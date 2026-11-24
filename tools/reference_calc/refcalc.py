#!/usr/bin/env python3
"""BlockGains reference calculator.

An independent, deliberately simple implementation of context/tax-rules.md
(R1-R11, R13), used to produce expected results for differential tests against the
app's Kotlin calculator. Standard library only.

    python refcalc.py transactions.csv -o expected.json
    python refcalc.py transactions.csv --summary

Input is the unified CSV from context/specs/import-format-spec.md.
"""
from __future__ import annotations

import argparse
import csv
import io
import json
import re
import sys
from dataclasses import dataclass, field
from datetime import date, datetime
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP, getcontext

getcontext().prec = 60

CUTOFF = date(2025, 1, 1)  # R3/R4: per-wallet tracking starts here
ZERO = Decimal(0)
CENT = Decimal("0.01")
TOLERANCE = Decimal("0.01")

COLUMNS = [
    "type", "date", "asset", "asset_class", "wallet", "quantity", "price_usd",
    "total_usd", "fee_usd", "fee_asset", "fee_quantity", "to_asset", "to_quantity", "to_wallet",
    "acquired_date", "basis_unknown", "note",
]
TYPES = {"BUY", "SELL", "SWAP", "INCOME", "TRANSFER", "OPENING", "ADJUST"}
# R3 same-day ordering: acquisitions (BUY, INCOME; OPENING is applied at the
# transition) first, then TRANSFER and SWAP in input order, then ADJUST and SELL in
# input order. A SWAP is processed as one step. Ties keep file order (the line number).
PRIORITY = {"BUY": 0, "INCOME": 0, "TRANSFER": 1, "SWAP": 1, "ADJUST": 2, "SELL": 2}
DATE_FORMATS = ("%Y-%m-%d", "%Y/%m/%d", "%m/%d/%Y")
ASSET_CLASSES = {"CRYPTO", "STOCK"}
# import-format-spec: numbers are plain, scientific (never grouped, 1-3 exponent digits) or grouped in
# proper thousands (`1,234.56`; `1,5` is invalid). ASCII digits only.
PLAIN_NUMBER = re.compile(r"[0-9]+\.?[0-9]*|\.[0-9]+")
SCIENTIFIC_NUMBER = re.compile(r"([0-9]+\.?[0-9]*|\.[0-9]+)[eE][+-]?[0-9]{1,3}")
GROUPED_NUMBER = re.compile(r"[0-9]{1,3}(,[0-9]{3})+(\.[0-9]*)?")
TRUE_MINUS = "\u2212"  # U+2212, accepted as a minus sign
TRUE_VALUES, FALSE_VALUES = {"true", "yes", "y", "1"}, {"", "false", "no", "n", "0"}


# --------------------------------------------------------------------------- models

@dataclass
class Tx:
    line: int
    type: str
    date: date
    asset: str
    asset_class: str
    wallet: str
    quantity: Decimal
    total: Decimal | None        # USD value: price*qty, total_usd, or SWAP's V
    fee: Decimal                 # USD; for a TRANSFER, the FMV of the fee coins (R13)
    fee_asset: str | None = None     # R13: TRANSFER fee coin (None = the transferred asset)
    fee_quantity: Decimal | None = None
    to_asset: str | None = None
    to_quantity: Decimal | None = None
    to_wallet: str | None = None
    acquired_date: date | None = None
    basis_unknown: bool = False


@dataclass
class Lot:
    asset: str
    wallet: str
    qty: Decimal
    basis: Decimal
    acquired: date
    seq: int
    source_line: int
    basis_unknown: bool = False


@dataclass
class Event:
    asset: str
    wallet: str
    scope: str            # "universal" (before 2025) or "wallet" (2025+)
    quantity: Decimal
    acquired: date | None  # None for unmatched units (no lot)
    disposed: date
    proceeds: Decimal
    basis: Decimal
    term: str             # "ST" | "LT"
    source_line: int      # the disposal row
    lot_line: int | None  # the row that created the lot; None for unmatched units
    unmatched: bool = False
    basis_unknown: bool = False
    kind: str = "disposal"  # or "transfer_fee" (R13)

    @property
    def gain(self) -> Decimal:
        return self.proceeds - self.basis


@dataclass
class IncomeEvent:
    asset: str
    wallet: str
    quantity: Decimal
    date: date
    value: Decimal
    source_line: int


@dataclass
class Issue:
    kind: str
    line: int | None
    date: date | None
    asset: str | None
    wallet: str | None
    detail: dict = field(default_factory=dict)


# --------------------------------------------------------------------------- parsing

class RowError(Exception):
    pass


def parse_date(text: str) -> date:
    text = text.strip()
    for fmt in DATE_FORMATS:
        try:
            return datetime.strptime(text, fmt).date()
        except ValueError:
            continue
    raise RowError(f"unrecognized date '{text}'")


def parse_decimal(text: str | None, name: str) -> Decimal | None:
    """Blank -> None. Optional `$` and a leading minus (ASCII or U+2212) in either order; anything
    else that isn't a valid number is a RowError (import-format-spec -> General parsing rules)."""
    if text is None or not text.strip():
        return None
    rest = text.strip().replace(TRUE_MINUS, "-")
    negative = rest.startswith("-")
    if negative:
        rest = rest[1:].strip()
    if rest.startswith("$"):
        rest = rest[1:].strip()
    if not negative and rest.startswith("-"):  # "$-5.00"
        negative, rest = True, rest[1:].strip()
    if PLAIN_NUMBER.fullmatch(rest) or SCIENTIFIC_NUMBER.fullmatch(rest):
        digits = rest
    elif GROUPED_NUMBER.fullmatch(rest):
        digits = rest.replace(",", "")
    else:
        raise RowError(f"bad number in {name}: '{text}'")
    value = Decimal(digits)
    return -value if negative else value


def parse_optional_date(text: str) -> date | None:
    return parse_date(text) if text else None


def parse_boolean(text: str, name: str) -> bool:
    value = text.strip().lower()
    if value in TRUE_VALUES:
        return True
    if value in FALSE_VALUES:
        return False
    raise RowError(f"unrecognized {name} '{text}'")


def parse_row(line: int, row: dict[str, str]) -> Tx | None:
    """Returns None for blank rows, raises RowError for invalid ones."""
    get = lambda key: (row.get(key) or "").strip()
    if not any(v and v.strip() for v in row.values() if isinstance(v, str)):
        return None
    tx_type = get("type").upper()
    if tx_type not in TYPES:
        raise RowError(f"unknown type '{tx_type}'")
    asset, wallet = get("asset").upper(), get("wallet")
    if not asset or not wallet:
        raise RowError("asset and wallet are required")
    qty = parse_decimal(get("quantity"), "quantity")
    if qty is None:
        raise RowError("quantity is required")
    if tx_type == "ADJUST":
        if qty >= 0:
            raise RowError("ADJUST quantity must be negative")
    elif qty <= 0:
        raise RowError("quantity must be positive")

    # import-format-spec: a malformed value in any column rejects the row, even one this type doesn't use.
    asset_class = (get("asset_class") or "CRYPTO").upper()
    if asset_class not in ASSET_CLASSES:
        raise RowError(f"unknown asset_class '{get('asset_class')}'")
    basis_unknown = parse_boolean(get("basis_unknown"), "basis_unknown")
    to_quantity = parse_decimal(get("to_quantity"), "to_quantity")
    acquired_date = parse_optional_date(get("acquired_date"))
    price = parse_decimal(get("price_usd"), "price_usd")
    total = parse_decimal(get("total_usd"), "total_usd")
    raw_fee = parse_decimal(get("fee_usd"), "fee_usd")
    fee = raw_fee or ZERO
    if fee < 0:
        raise RowError("fee_usd can't be negative")
    fee_asset = get("fee_asset").upper() or None
    fee_quantity = parse_decimal(get("fee_quantity"), "fee_quantity")
    if tx_type == "TRANSFER":  # R13: a network fee is paid in coins, worth fee_usd
        if fee_quantity is None and (fee > 0 or fee_asset):
            raise RowError("a TRANSFER fee needs fee_quantity")
        if fee_quantity is not None and fee_quantity <= 0:
            raise RowError("fee_quantity must be positive")
        if fee_quantity is not None and raw_fee is None:
            raise RowError("fee_quantity needs fee_usd (the fee coins' USD value)")
    else:
        if fee_asset or fee_quantity is not None:
            raise RowError("fee_asset/fee_quantity apply only to TRANSFER")
        if fee > 0 and tx_type not in {"BUY", "SELL", "SWAP"}:
            raise RowError(f"fee_usd isn't allowed on {tx_type}")

    value: Decimal | None = None
    if tx_type in {"BUY", "SELL", "INCOME", "OPENING"}:
        if price is not None and total is not None and abs(price * qty - total) > TOLERANCE:
            raise RowError("price_usd x quantity and total_usd disagree by more than $0.01")
        value = total if total is not None else (price * qty if price is not None else None)
        if value is None:
            raise RowError(f"{tx_type} needs price_usd or total_usd")
        if value < 0:
            raise RowError("USD value can't be negative")

    tx = Tx(
        line=line, type=tx_type, date=parse_date(get("date")), asset=asset,
        asset_class=asset_class, wallet=wallet,
        quantity=qty, total=value, fee=fee,
        basis_unknown=basis_unknown,
    )
    if tx_type == "SWAP":
        tx.to_asset = get("to_asset").upper() or None
        tx.to_quantity = to_quantity
        if not tx.to_asset or tx.to_quantity is None or tx.to_quantity <= 0:
            raise RowError("SWAP needs to_asset and a positive to_quantity")
        if tx.to_asset == asset:
            raise RowError("a SWAP must receive a different asset")
        if total is None or total < 0:
            raise RowError("SWAP needs total_usd (its USD value V)")
        tx.total = total
    elif tx_type == "TRANSFER":
        if fee_quantity is not None:
            tx.fee_asset, tx.fee_quantity = fee_asset or asset, fee_quantity
        tx.to_wallet = get("to_wallet") or None
        if not tx.to_wallet or tx.to_wallet == wallet:
            raise RowError("TRANSFER needs a to_wallet different from wallet")
    elif tx_type == "OPENING":  # import-format-spec: OPENING rows are dated 2025-01-01 (R4)
        if tx.date != CUTOFF:
            raise RowError(f"OPENING must be dated {CUTOFF.isoformat()}")
        if acquired_date is None:
            raise RowError("OPENING needs acquired_date")
        tx.acquired_date = acquired_date
        if tx.acquired_date > CUTOFF:
            raise RowError("OPENING acquired_date can't be after the transition")
    return tx


COMMENT_PREFIX = "#"


def read_records(text: str) -> list[tuple[int, list[str]]]:
    """(physical start line, fields) per record. Blank lines and lines whose first field starts with
    `#` are skipped (import-format-spec: the template's comment lines), before the header too."""
    reader = csv.reader(io.StringIO(text))
    records, last = [], 0
    for fields in reader:
        start, last = last + 1, reader.line_num
        if not fields or fields[0].strip().startswith(COMMENT_PREFIX):
            continue
        records.append((start, fields))
    return records


def read_transactions(text: str) -> tuple[list[Tx], list[Issue]]:
    records = read_records(text)
    if not records:
        return [], []
    names = [name.strip().lower() for name in records[0][1]]
    txs, issues = [], []
    for index, fields in records[1:]:
        row = {name: fields[i] if i < len(fields) else None for i, name in enumerate(names)}
        try:
            tx = parse_row(index, row)
        except RowError as err:
            issues.append(Issue("InvalidRow", index, None, None, None, {"reason": str(err)}))
            continue
        if tx is not None:
            txs.append(tx)
    return txs, issues


# --------------------------------------------------------------------------- engine

def plus_one_year(d: date) -> date:
    """Same as java.time.LocalDate.plusYears(1): Feb 29 -> Feb 28."""
    try:
        return d.replace(year=d.year + 1)
    except ValueError:
        return d.replace(year=d.year + 1, day=28)


def is_long_term(acquired: date, disposed: date) -> bool:
    return disposed > plus_one_year(acquired)  # R2


class Engine:
    def __init__(self, default_wallet: str):
        self.default_wallet = default_wallet
        self.universal: dict[str, list[Lot]] = {}
        self.wallets: dict[tuple[str, str], list[Lot]] = {}
        self.transitioned = False
        self.seq = 0
        self.events: list[Event] = []
        self.income: list[IncomeEvent] = []
        self.issues: list[Issue] = []
        self.adjusted_basis = ZERO
        self.pre2025_assets: set[str] = set()

    # ---- pools

    def _next_seq(self) -> int:
        self.seq += 1
        return self.seq

    def _pool(self, asset: str, wallet: str, on: date) -> tuple[list[Lot], str]:
        if on < CUTOFF:
            return self.universal.setdefault(asset, []), "universal"
        return self.wallets.setdefault((asset, wallet), []), "wallet"

    @staticmethod
    def _insert(pool: list[Lot], lot: Lot) -> None:
        # FIFO order = original acquisition date, then insertion order.
        pool.append(lot)
        pool.sort(key=lambda l: (l.acquired, l.seq))

    def _acquire(self, asset, wallet, qty, basis, acquired, line, basis_unknown=False):
        pool, _ = self._pool(asset, wallet, acquired)
        self._insert(pool, Lot(asset, wallet, qty, basis, acquired, self._next_seq(), line, basis_unknown))

    def _take(self, pool: list[Lot], qty: Decimal, on: date):
        """Remove qty FIFO from pool (never lots acquired after `on`).
        Yields (lot_snapshot, matched_qty, basis_used); returns shortfall via StopIteration value."""
        remaining = qty
        for lot in pool:
            if remaining <= 0:
                break
            if lot.acquired > on or lot.qty <= 0:
                continue  # R3: never match a later lot
            matched = min(lot.qty, remaining)
            basis_used = lot.basis if matched == lot.qty else lot.basis * matched / lot.qty
            yield lot, matched, basis_used
            lot.qty -= matched
            lot.basis -= basis_used
            remaining -= matched
        pool[:] = [l for l in pool if l.qty > 0]
        self._shortfall = remaining

    # ---- transition (R4)

    def transition(self, openings: list[Tx]) -> None:
        if self.transitioned:
            return
        self.transitioned = True
        by_asset: dict[str, list[Tx]] = {}
        for o in openings:
            by_asset.setdefault(o.asset, []).append(o)
        for asset in sorted(set(self.universal) | set(by_asset)):
            remainder = [l for l in self.universal.get(asset, []) if l.qty > 0]
            if asset in by_asset:
                expected = sum((l.qty for l in remainder), ZERO)
                opened = sum((o.quantity for o in by_asset[asset]), ZERO)
                # R4: only warn when the asset has records from before 2025
                if asset in self.pre2025_assets and expected != opened:
                    self.issues.append(Issue("TransitionMismatch", None, CUTOFF, asset, None,
                                             {"expected": fmt_qty(expected), "opening": fmt_qty(opened)}))
                for o in by_asset[asset]:
                    lot = Lot(asset, o.wallet, o.quantity, o.total, o.acquired_date,
                              self._next_seq(), o.line, o.basis_unknown)
                    self._insert(self.wallets.setdefault((asset, o.wallet), []), lot)
            else:
                for lot in remainder:
                    lot.wallet = self.default_wallet
                    self._insert(self.wallets.setdefault((asset, self.default_wallet), []), lot)
        self.universal.clear()

    # ---- transaction handlers

    def _dispose(self, tx: Tx, asset: str, qty: Decimal, gross: Decimal, fee: Decimal, kind: str = "disposal"):
        pool, scope = self._pool(asset, tx.wallet, tx.date)
        net = gross - fee
        for lot, matched, basis_used in list(self._take(pool, qty, tx.date)):
            self.events.append(Event(
                asset=asset, wallet=tx.wallet, scope=scope, quantity=matched,
                acquired=lot.acquired, disposed=tx.date,
                proceeds=net * matched / qty,  # R7: fee allocated pro rata by quantity
                basis=basis_used,
                term="LT" if is_long_term(lot.acquired, tx.date) else "ST",
                source_line=tx.line, lot_line=lot.source_line, basis_unknown=lot.basis_unknown, kind=kind,
            ))
        if self._shortfall > 0:
            # R3 (Open questions: partial shortfall, B): the whole sale is taxable. Units with
            # no lot get $0 basis, unknown basis, short-term and acquired "Unknown" (None),
            # with their pro-rata share of proceeds and fee. R10: still an InsufficientLots issue.
            self.events.append(Event(
                asset=asset, wallet=tx.wallet, scope=scope, quantity=self._shortfall,
                acquired=None, disposed=tx.date, proceeds=net * self._shortfall / qty, basis=ZERO,
                term="ST", source_line=tx.line, lot_line=None, unmatched=True, basis_unknown=True, kind=kind,
            ))
            self.issues.append(Issue("InsufficientLots", tx.line, tx.date, asset, tx.wallet,
                                     {"short_by": fmt_qty(self._shortfall)}))

    def apply(self, tx: Tx) -> None:
        if tx.date >= CUTOFF and not self.transitioned:
            raise RuntimeError("transition() must run before 2025+ transactions")
        if tx.date < CUTOFF:
            self.pre2025_assets.add(tx.asset)
            if tx.to_asset:
                self.pre2025_assets.add(tx.to_asset)
        if tx.type == "BUY":
            if tx.total == 0 and not tx.basis_unknown:
                self.issues.append(Issue("ZeroPriceBuy", tx.line, tx.date, tx.asset, tx.wallet, {}))
            self._acquire(tx.asset, tx.wallet, tx.quantity, tx.total + tx.fee, tx.date, tx.line,
                          tx.basis_unknown)  # R7: buy fee adds to basis
        elif tx.type == "INCOME":  # R8
            self.income.append(IncomeEvent(tx.asset, tx.wallet, tx.quantity, tx.date, tx.total, tx.line))
            self._acquire(tx.asset, tx.wallet, tx.quantity, tx.total, tx.date, tx.line)
        elif tx.type == "SELL":
            self._dispose(tx, tx.asset, tx.quantity, tx.total, tx.fee)
        elif tx.type == "SWAP":  # R6
            self._dispose(tx, tx.asset, tx.quantity, tx.total, tx.fee)
            self._acquire(tx.to_asset, tx.wallet, tx.to_quantity, tx.total, tx.date, tx.line)
        elif tx.type == "TRANSFER":  # R5
            if tx.fee_quantity is not None:
                # R13: the fee coins are a disposal from the source pool first (before 2025 too):
                # amount realized = fee_usd, no transaction cost, nothing added to basis.
                self._dispose(tx, tx.fee_asset, tx.fee_quantity, tx.fee, ZERO, kind="transfer_fee")
            if tx.date < CUTOFF:
                return  # R4: pre-2025 transfers are ignored for matching
            source, _ = self._pool(tx.asset, tx.wallet, tx.date)
            moved = list(self._take(source, tx.quantity, tx.date))
            dest, _ = self._pool(tx.asset, tx.to_wallet, tx.date)
            for lot, matched, basis_used in moved:
                self._insert(dest, Lot(tx.asset, tx.to_wallet, matched, basis_used, lot.acquired,
                                       self._next_seq(), lot.source_line, lot.basis_unknown))
            if self._shortfall > 0:
                self.issues.append(Issue("InsufficientLots", tx.line, tx.date, tx.asset, tx.wallet,
                                         {"short_by": fmt_qty(self._shortfall)}))
        elif tx.type == "ADJUST":  # R9
            pool, _ = self._pool(tx.asset, tx.wallet, tx.date)
            for _, _, basis_used in list(self._take(pool, -tx.quantity, tx.date)):
                self.adjusted_basis += basis_used
            if self._shortfall > 0:
                self.issues.append(Issue("InsufficientLots", tx.line, tx.date, tx.asset, tx.wallet,
                                         {"short_by": fmt_qty(self._shortfall)}))

    def remaining_lots(self) -> list[Lot]:
        lots = [l for pool in self.universal.values() for l in pool]
        lots += [l for pool in self.wallets.values() for l in pool]
        return sorted(lots, key=lambda l: (l.asset, l.wallet, l.acquired, l.seq))


def calculate(text: str, default_wallet: str = "FAKE-WALLET-1") -> dict:
    txs, issues = read_transactions(text)
    openings = [t for t in txs if t.type == "OPENING"]
    regular = sorted((t for t in txs if t.type != "OPENING"),
                     key=lambda t: (t.date, PRIORITY[t.type], t.line))
    engine = Engine(default_wallet)
    engine.issues.extend(issues)
    for tx in regular:
        if tx.date >= CUTOFF:
            engine.transition(openings)
        engine.apply(tx)
    if openings:
        engine.transition(openings)
    return build_report(engine, default_wallet)


# --------------------------------------------------------------------------- output

def cents(value: Decimal) -> str:
    return str(value.quantize(CENT, rounding=ROUND_HALF_UP))


def fmt_qty(value: Decimal) -> str:
    text = format(value.normalize(), "f")
    return "0" if text in {"-0", ""} else text


def build_report(engine: Engine, default_wallet: str) -> dict:
    years: dict[int, dict] = {}

    def bucket(year: int) -> dict:
        return years.setdefault(year, {
            "ST": {"proceeds": ZERO, "basis": ZERO, "gain": ZERO, "count": 0},
            "LT": {"proceeds": ZERO, "basis": ZERO, "gain": ZERO, "count": 0},
            "income": ZERO, "income_count": 0,
        })

    for e in engine.events:
        b = bucket(e.disposed.year)[e.term]
        b["proceeds"] += e.proceeds
        b["basis"] += e.basis
        b["gain"] += e.gain
        b["count"] += 1
    for i in engine.income:
        b = bucket(i.date.year)
        b["income"] += i.value
        b["income_count"] += 1

    year_out = {}
    for year in sorted(years):
        y = years[year]
        net = y["ST"]["gain"] + y["LT"]["gain"]  # R11: totals from unrounded values
        year_out[str(year)] = {
            "short_term": {k: (cents(v) if k != "count" else v) for k, v in y["ST"].items()},
            "long_term": {k: (cents(v) if k != "count" else v) for k, v in y["LT"].items()},
            "net_gain": cents(net),
            "loss_limit_note": net < 0,
            "ordinary_income": cents(y["income"]),
            "income_count": y["income_count"],
        }

    return {
        "settings": {"method": "FIFO", "cutoff": CUTOFF.isoformat(), "default_wallet": default_wallet,
                     "rounding": "HALF_UP to cents, totals from unrounded values"},
        "years": year_out,
        "events": [{
            "line": e.source_line, "lot_line": e.lot_line, "kind": e.kind, "asset": e.asset, "wallet": e.wallet,
            "scope": e.scope, "quantity": fmt_qty(e.quantity),
            "acquired": e.acquired.isoformat() if e.acquired else None, "unmatched": e.unmatched,
            "basis_unknown": e.basis_unknown,
            "disposed": e.disposed.isoformat(), "term": e.term, "proceeds": cents(e.proceeds),
            "basis": cents(e.basis), "gain": cents(e.gain),
        } for e in engine.events],
        "income": [{
            "line": i.source_line, "asset": i.asset, "wallet": i.wallet, "quantity": fmt_qty(i.quantity),
            "date": i.date.isoformat(), "value": cents(i.value),
        } for i in engine.income],
        "remaining_lots": [{
            "asset": l.asset, "wallet": l.wallet if engine.transitioned else "*universal*",
            "quantity": fmt_qty(l.qty), "basis": cents(l.basis), "acquired": l.acquired.isoformat(),
            "basis_unknown": l.basis_unknown,
        } for l in engine.remaining_lots()],
        "adjusted_away_basis": cents(engine.adjusted_basis),
        "issues": [{
            "kind": i.kind, "line": i.line, "date": i.date.isoformat() if i.date else None,
            "asset": i.asset, "wallet": i.wallet, **i.detail,
        } for i in engine.issues],
    }


def print_summary(report: dict, out=sys.stdout) -> None:
    print(f"{'Year':<6}{'Short-term':>16}{'Long-term':>16}{'Net':>16}{'Income':>14}  Events", file=out)
    for year, y in report["years"].items():
        count = y["short_term"]["count"] + y["long_term"]["count"]
        print(f"{year:<6}{y['short_term']['gain']:>16}{y['long_term']['gain']:>16}"
              f"{y['net_gain']:>16}{y['ordinary_income']:>14}  {count}", file=out)
    if report["issues"]:
        kinds: dict[str, int] = {}
        for i in report["issues"]:
            kinds[i["kind"]] = kinds.get(i["kind"], 0) + 1
        print("Issues: " + ", ".join(f"{k} x{v}" for k, v in sorted(kinds.items())), file=out)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("csv", help="unified-format transactions CSV")
    parser.add_argument("-o", "--output", help="write expected results JSON here")
    parser.add_argument("--default-wallet", default="FAKE-WALLET-1",
                        help="wallet for pre-2025 lots with no OPENING records (R4)")
    parser.add_argument("--summary", action="store_true", help="print a per-year summary")
    args = parser.parse_args(argv)
    with open(args.csv, newline="", encoding="utf-8-sig") as handle:
        report = calculate(handle.read(), args.default_wallet)
    if args.output:
        with open(args.output, "w", encoding="utf-8") as handle:
            json.dump(report, handle, indent=2)
            handle.write("\n")
    if args.summary or not args.output:
        print_summary(report)
    return 0


if __name__ == "__main__":
    sys.exit(main())
