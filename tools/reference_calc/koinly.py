"""Converts unified-format transactions to Koinly's universal CSV format.

Only the types that map cleanly are supported: BUY, SELL, SWAP, INCOME.
Use with single-wallet datasets (gen.py --profile koinly) so Koinly's
wallet handling can't differ from BlockGains'.

Koinly columns: Date, Sent Amount, Sent Currency, Received Amount,
Received Currency, Fee Amount, Fee Currency, Net Worth Amount,
Net Worth Currency, Label, Description, TxHash
"""
from __future__ import annotations

import csv
import io

import refcalc

KOINLY_COLUMNS = ["Date", "Sent Amount", "Sent Currency", "Received Amount", "Received Currency",
                  "Fee Amount", "Fee Currency", "Net Worth Amount", "Net Worth Currency",
                  "Label", "Description", "TxHash"]


def _num(value) -> str:
    return "" if value is None else refcalc.fmt_qty(value)


def to_koinly_csv(unified_csv: str) -> str:
    txs, issues = refcalc.read_transactions(unified_csv)
    unsupported = sorted({t.type for t in txs if t.type not in {"BUY", "SELL", "SWAP", "INCOME"}})
    if unsupported or issues:
        raise ValueError(f"Koinly export supports BUY/SELL/SWAP/INCOME only and no invalid rows "
                         f"(found types {unsupported}, {len(issues)} invalid rows)")
    if any(t.type == "BUY" and t.total == 0 for t in txs):
        raise ValueError("Koinly export doesn't support $0 buys")

    out = io.StringIO()
    writer = csv.DictWriter(out, fieldnames=KOINLY_COLUMNS, lineterminator="\n")
    writer.writeheader()
    for t in txs:
        # Noon UTC keeps the calendar date the same in US time zones.
        row = {c: "" for c in KOINLY_COLUMNS}
        row["Date"] = f"{t.date.isoformat()} 12:00 UTC"
        row["Description"] = f"blockgains line {t.line}"
        if t.fee:
            row["Fee Amount"], row["Fee Currency"] = _num(t.fee), "USD"
        if t.type == "BUY":
            row.update({"Sent Amount": _num(t.total), "Sent Currency": "USD",
                        "Received Amount": _num(t.quantity), "Received Currency": t.asset})
        elif t.type == "SELL":
            row.update({"Sent Amount": _num(t.quantity), "Sent Currency": t.asset,
                        "Received Amount": _num(t.total), "Received Currency": "USD"})
        elif t.type == "SWAP":
            row.update({"Sent Amount": _num(t.quantity), "Sent Currency": t.asset,
                        "Received Amount": _num(t.to_quantity), "Received Currency": t.to_asset,
                        "Net Worth Amount": _num(t.total), "Net Worth Currency": "USD", "Label": "swap"})
        elif t.type == "INCOME":
            row.update({"Received Amount": _num(t.quantity), "Received Currency": t.asset,
                        "Net Worth Amount": _num(t.total), "Net Worth Currency": "USD", "Label": "staking"})
        writer.writerow(row)
    return out.getvalue()
