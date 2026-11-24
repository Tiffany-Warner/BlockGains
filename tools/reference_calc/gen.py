#!/usr/bin/env python3
"""Random dataset generator for BlockGains differential tests.

Writes made-up transactions in the unified CSV format, the reference
calculator's expected results, and (for the koinly profile) a Koinly
universal-format CSV of the same transactions.

    python gen.py --seed 1                     # full profile: wallets, swaps, transfers, openings
    python gen.py --seed 7 --profile koinly    # one wallet, Koinly-friendly, + .koinly.csv
    python gen.py --seed 1 --count 20          # seeds 1..20

All numbers are synthetic. Every transaction gets its own date, so same-day
ordering never matters (worked cases T1-T12 cover that separately).
"""
from __future__ import annotations

import argparse
import csv
import io
import json
import math
import random
from datetime import date, timedelta
from decimal import Decimal, ROUND_DOWN
from pathlib import Path

import refcalc
from koinly import to_koinly_csv

QTY = Decimal("0.00000001")
USD = Decimal("0.01")
BASIS_DP = Decimal("0.00000001")

ASSETS = {  # symbol: (starting price, typical buy size)
    "FAKEA": (9000, Decimal("0.05")),
    "FAKEB": (300, Decimal("1.5")),
    "FAKEC": (20, Decimal("25")),
    "FAKED": (0.40, Decimal("1500")),
}
WALLETS = ["FAKE-EXCHANGE-1", "FAKE-WALLET-1", "FAKE-WALLET-2"]
PROFILES = {
    "full": dict(wallets=WALLETS, features={"buy", "sell", "swap", "income", "transfer", "adjust", "fee", "zero"},
                 opening="split"),
    "koinly": dict(wallets=["FAKE-WALLET-1"], features={"buy", "sell", "swap", "income", "fee"}, opening="none"),
}
WEIGHTS = {"buy": 34, "sell": 26, "swap": 10, "income": 12, "transfer": 10, "adjust": 3}


def q(value: Decimal, step: Decimal = QTY) -> Decimal:
    return value.quantize(step, rounding=ROUND_DOWN)


class Market:
    """Deterministic daily prices: a seeded random walk per asset."""

    def __init__(self, rng: random.Random, start: date, end: date, assets: list[str]):
        self.prices: dict[str, dict[date, Decimal]] = {}
        days = (end - start).days + 1
        for asset in assets:
            price, series = float(ASSETS[asset][0]), {}
            for offset in range(days):
                price *= math.exp(rng.gauss(0.0006, 0.035))
                series[start + timedelta(days=offset)] = Decimal(str(round(price, 2 if price >= 1 else 4)))
            self.prices[asset] = series

    def price(self, asset: str, on: date) -> Decimal:
        return self.prices[asset][on]


class Generator:
    def __init__(self, seed: int, profile: str, n: int, start: date, end: date, assets: list[str],
                 opening: str | None, errors: int):
        self.rng = random.Random(seed)
        cfg = PROFILES[profile]
        self.profile, self.wallets, self.features = profile, cfg["wallets"], cfg["features"]
        self.opening = opening or cfg["opening"]
        self.n, self.start, self.end, self.assets, self.errors = n, start, end, assets, errors
        self.market = Market(self.rng, start, end + timedelta(days=60), assets)
        self.rows: list[dict] = []

    # ---- helpers

    def _row(self, **values) -> dict:
        row = {c: "" for c in refcalc.COLUMNS}
        row.update({k: _cell(v) for k, v in values.items()})
        row.setdefault("asset_class", "CRYPTO")
        row["asset_class"] = "CRYPTO"
        self.rows.append(row)
        return row

    def _value(self, price: Decimal, qty: Decimal) -> dict:
        """Full profile: per-unit price (exact price x qty, stresses precision).
        Koinly profile: cent-rounded total, so both tools see identical USD amounts."""
        if self.profile == "koinly":
            return {"total_usd": (price * qty).quantize(USD)}
        return {"price_usd": price}

    def _fee(self, total: Decimal) -> Decimal | None:
        if "fee" in self.features and self.rng.random() < 0.4:
            return max(USD, (total * Decimal("0.006")).quantize(USD))
        return None

    def _dates(self) -> list[date]:
        span = (self.end - self.start).days
        days = sorted(self.rng.sample(range(span + 1), min(self.n, span)))
        dates = [self.start + timedelta(days=d) for d in days]
        return [d for d in dates if d != refcalc.CUTOFF]  # keep 2025-01-01 for OPENING rows only

    # ---- generation

    def run(self) -> list[dict]:
        holdings: dict = {}  # pre-2025: asset -> qty (universal); 2025+: (asset, wallet) -> qty
        dates = self._dates()
        pre = [d for d in dates if d < refcalc.CUTOFF]
        post = [d for d in dates if d >= refcalc.CUTOFF]

        for d in pre:
            self._step(d, holdings, key=lambda asset, wallet: asset, pre2025=True)

        holdings = self._openings(pre_rows=list(self.rows))

        for d in post:
            self._step(d, holdings, key=lambda asset, wallet: (asset, wallet), pre2025=False)

        self._inject_errors()
        return self.rows

    def _held(self, holdings, key, pre2025):
        options = []
        for asset in self.assets:
            for wallet in self.wallets:
                k = key(asset, wallet)
                if holdings.get(k, 0) > 0 and (k, wallet) not in options:
                    options.append((asset, wallet))
                if pre2025:
                    break  # universal: one entry per asset, wallet chosen later
        return options

    def _step(self, d: date, holdings: dict, key, pre2025: bool) -> None:
        # Pre-2025 transfers are allowed: the move is ignored for matching (R4), but a fee still counts (R13).
        kinds = [k for k in WEIGHTS if k in self.features]
        held = self._held(holdings, key, pre2025)
        if not held:
            kinds = [k for k in kinds if k in {"buy", "income"}]
        if "transfer" in kinds and len(self.wallets) < 2:
            kinds.remove("transfer")
        kind = self.rng.choices(kinds, weights=[WEIGHTS[k] for k in kinds])[0]
        wallet_any = self.rng.choice(self.wallets)

        if kind in {"buy", "income"}:
            asset = self.rng.choice(self.assets)
            wallet = wallet_any
            price = self.market.price(asset, d)
            size = ASSETS[asset][1]
            qty = q(size * Decimal(str(self.rng.uniform(0.05, 2.0))) * (Decimal("0.05") if kind == "income" else 1))
            if qty <= 0:
                return
            if kind == "buy":
                zero = "zero" in self.features and self.rng.random() < 0.03
                unknown = zero and self.rng.random() < 0.7
                self._row(type="BUY", date=d, asset=asset, wallet=wallet, quantity=qty,
                          **self._value(Decimal("0.00") if zero else price, qty),
                          fee_usd=None if zero else self._fee(price * qty),
                          basis_unknown="true" if unknown else None,
                          note="unknown basis" if unknown else None)
            else:
                self._row(type="INCOME", date=d, asset=asset, wallet=wallet, quantity=qty,
                          **self._value(price, qty), note="staking")
            k = key(asset, wallet)
            holdings[k] = holdings.get(k, 0) + qty
            return

        asset, wallet = self.rng.choice(held)
        if pre2025:
            wallet = wallet_any  # wallet labels are cosmetic before 2025 (universal pool)
        k = key(asset, wallet)
        have = holdings[k]
        fraction = Decimal("1") if self.rng.random() < 0.1 else Decimal(str(self.rng.uniform(0.05, 0.8)))
        qty = q(have * fraction)
        if qty <= 0:
            return
        price = self.market.price(asset, d)

        if kind == "sell":
            self._row(type="SELL", date=d, asset=asset, wallet=wallet, quantity=qty,
                      **self._value(price, qty), fee_usd=self._fee(price * qty))
            holdings[k] = have - qty
        elif kind == "swap":
            targets = [a for a in self.assets if a != asset]
            to_asset = self.rng.choice(targets)
            value = (price * qty * Decimal(str(self.rng.uniform(0.98, 1.02)))).quantize(USD)
            to_qty = q(value / self.market.price(to_asset, d))
            if to_qty <= 0 or value <= 0:
                return
            fee = self._fee(value) if self.profile != "koinly" else None
            self._row(type="SWAP", date=d, asset=asset, wallet=wallet, quantity=qty, total_usd=value,
                      fee_usd=fee, to_asset=to_asset, to_quantity=to_qty, note="on-chain swap")
            holdings[k] = have - qty
            k2 = key(to_asset, wallet)
            holdings[k2] = holdings.get(k2, 0) + to_qty
        elif kind == "transfer":
            to_wallet = self.rng.choice([w for w in self.wallets if w != wallet])
            received, extra = qty, {}
            fee = self._transfer_fee(d, asset, wallet, qty, holdings, key)
            if fee is not None:  # R13: quantity = what arrived; same-coin fee comes out of what was sent
                fee_asset, fee_qty, fee_usd = fee
                extra = dict(fee_usd=fee_usd, fee_quantity=fee_qty)
                if fee_asset == asset:
                    received = qty - fee_qty
                else:
                    extra["fee_asset"] = fee_asset
                    fk = key(fee_asset, wallet)
                    holdings[fk] = holdings[fk] - fee_qty
            if received <= 0:
                return
            self._row(type="TRANSFER", date=d, asset=asset, wallet=wallet, quantity=received, to_wallet=to_wallet,
                      **extra)
            holdings[k] = holdings[k] - qty
            k2 = key(asset, to_wallet)
            holdings[k2] = holdings.get(k2, 0) + received
        elif kind == "adjust":
            dust = q(have * Decimal("0.0005"))
            if dust <= 0:
                return
            self._row(type="ADJUST", date=d, asset=asset, wallet=wallet, quantity=-dust, note="fee dust")
            holdings[k] = have - dust

    def _transfer_fee(self, d: date, asset: str, wallet: str, sent: Decimal, holdings: dict, key):
        """About half of transfers pay a network fee (R13), some in another coin held in the same wallet.
        Returns (fee_asset, fee_quantity, fee_usd) or None."""
        if "fee" not in self.features or self.rng.random() >= 0.5:
            return None
        others = [a for a in self.assets if a != asset and holdings.get(key(a, wallet), 0) > 0]
        if others and self.rng.random() < 0.3:
            fee_asset = self.rng.choice(others)
            fee_qty = q(holdings[key(fee_asset, wallet)] * Decimal(str(self.rng.uniform(0.001, 0.02))))
        else:
            fee_asset = asset
            fee_qty = q(sent * Decimal(str(self.rng.uniform(0.0005, 0.01))))
        if fee_qty <= 0:
            return None
        return fee_asset, fee_qty, (fee_qty * self.market.price(fee_asset, d)).quantize(USD)

    def _openings(self, pre_rows: list[dict]) -> dict:
        """Builds 2025+ per-wallet holdings, writing OPENING rows if the mode asks for them."""
        engine_report = refcalc.calculate(_to_csv(pre_rows))
        lots = engine_report["remaining_lots"]
        holdings: dict = {}
        if self.opening == "none":
            default = self.wallets[0] if len(self.wallets) == 1 else "FAKE-WALLET-1"
            for lot in lots:
                k = (lot["asset"], default)
                holdings[k] = holdings.get(k, 0) + Decimal(lot["quantity"])
            return holdings

        # Exact basis per lot (the report rounds to cents), recomputed from the engine.
        exact = _exact_remaining(pre_rows)
        mismatch_done = False
        for lot in exact:
            parts = [lot["quantity"]]
            if len(self.wallets) > 1 and self.rng.random() < 0.5 and lot["quantity"] > Decimal("0.0001"):
                first = q(lot["quantity"] * Decimal(str(self.rng.uniform(0.2, 0.8))))
                parts = [first, lot["quantity"] - first]
            for part in parts:
                wallet = self.rng.choice(self.wallets)
                qty = part
                if self.opening == "mismatch" and not mismatch_done and qty > QTY * 1000:
                    qty = qty - QTY * 100  # leave dust behind -> TransitionMismatch
                    mismatch_done = True
                basis = q(lot["basis"] * part / lot["quantity"], BASIS_DP)
                self._row(type="OPENING", date=refcalc.CUTOFF, asset=lot["asset"], wallet=wallet, quantity=qty,
                          total_usd=basis, acquired_date=lot["acquired"],
                          basis_unknown="true" if lot["basis_unknown"] else None,
                          note="opening position")
                k = (lot["asset"], wallet)
                holdings[k] = holdings.get(k, 0) + qty
        return holdings

    def _inject_errors(self) -> None:
        base = self.end + timedelta(days=5)
        for i in range(self.errors):
            d = base + timedelta(days=i * 3)
            asset = self.rng.choice(self.assets)
            wallet = self.rng.choice(self.wallets)
            choice = i % 3
            if choice == 0:  # oversell -> InsufficientLots
                self._row(type="SELL", date=d, asset=asset, wallet=wallet, quantity=ASSETS[asset][1] * 40,
                          price_usd=self.market.price(asset, d), note="injected oversell")
            elif choice == 1:  # missing price -> InvalidRow
                self._row(type="BUY", date=d, asset=asset, wallet=wallet, quantity=Decimal("1"),
                          note="injected missing price")
            else:  # bad date -> InvalidRow
                row = self._row(type="BUY", date=d, asset=asset, wallet=wallet, quantity=Decimal("1"),
                                price_usd=Decimal("1.00"), note="injected bad date")
                row["date"] = "2025-02-30"


def _cell(value) -> str:
    """Plain decimal notation: str(Decimal) can give '0E-8', which the app's importer rejects."""
    if value is None:
        return ""
    if isinstance(value, Decimal):
        return format(value, "f")
    return str(value)


def _to_csv(rows: list[dict]) -> str:
    out = io.StringIO()
    writer = csv.DictWriter(out, fieldnames=refcalc.COLUMNS, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return out.getvalue()


def _exact_remaining(rows: list[dict]) -> list[dict]:
    txs, _ = refcalc.read_transactions(_to_csv(rows))
    engine = refcalc.Engine("FAKE-WALLET-1")
    for tx in sorted(txs, key=lambda t: (t.date, refcalc.PRIORITY[t.type], t.line)):
        engine.apply(tx)
    return [{"asset": l.asset, "quantity": l.qty, "basis": l.basis, "acquired": l.acquired.isoformat(),
             "basis_unknown": l.basis_unknown} for l in engine.remaining_lots()]


def generate(seed: int, args) -> tuple[str, dict, str | None]:
    gen = Generator(seed, args.profile, args.transactions, args.start, args.end,
                    args.assets, args.opening, args.errors)
    rows = gen.run()
    rows.sort(key=lambda r: (r["date"] if r["date"] != "2025-02-30" else "9999", r["type"] != "OPENING"))
    text = _to_csv(rows)
    default_wallet = gen.wallets[0] if len(gen.wallets) == 1 else "FAKE-WALLET-1"
    report = refcalc.calculate(text, default_wallet)
    report["generator"] = {"seed": seed, "profile": args.profile, "transactions": len(rows),
                           "opening": gen.opening, "errors": args.errors,
                           "start": args.start.isoformat(), "end": args.end.isoformat()}
    koinly = to_koinly_csv(text) if args.profile == "koinly" else None
    return text, report, koinly


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--seed", type=int, default=1)
    parser.add_argument("--count", type=int, default=1, help="number of datasets (seed, seed+1, ...)")
    parser.add_argument("--profile", choices=sorted(PROFILES), default="full")
    parser.add_argument("--transactions", type=int, default=150, help="approximate rows per dataset")
    parser.add_argument("--start", type=date.fromisoformat, default=date(2019, 1, 1))
    parser.add_argument("--end", type=date.fromisoformat, default=date(2026, 9, 30))
    parser.add_argument("--assets", type=lambda s: s.upper().split(","), default=list(ASSETS))
    parser.add_argument("--opening", choices=["split", "none", "mismatch"],
                        help="2025 transition: OPENING rows split across wallets, none (default wallet), "
                             "or split with a deliberate mismatch. Default depends on profile")
    parser.add_argument("--errors", type=int, default=0, help="append N bad rows (oversells, invalid rows)")
    parser.add_argument("--out", type=Path, default=Path("out"))
    args = parser.parse_args()
    unknown = [a for a in args.assets if a not in ASSETS]
    if unknown:
        parser.error(f"unknown assets {unknown}; choose from {list(ASSETS)}")

    args.out.mkdir(parents=True, exist_ok=True)
    for seed in range(args.seed, args.seed + args.count):
        stem = args.out / f"{args.profile}-seed{seed}"
        text, report, koinly = generate(seed, args)
        stem.with_suffix(".csv").write_text(text, encoding="utf-8")
        stem.with_suffix(".expected.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        if koinly:
            (args.out / f"{stem.name}.koinly.csv").write_text(koinly, encoding="utf-8")
        print(f"\n{stem.name}: {report['generator']['transactions']} rows")
        refcalc.print_summary(report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
