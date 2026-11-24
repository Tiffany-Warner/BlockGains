"""One-off converter: v1's legacy two-file CSV (buys and/or sells, one asset) -> BlockGains unified CSV.

The legacy importer was removed from the app in 1.8.0. This writes the same rows it would have stored:
BUY/SELL with price_usd, the asset, asset class and wallet given on the command line, and no fee.
Numbers stay exact strings (never float). A $0 buy stays price 0 so the app's import preview asks
whether it was an unknown-basis lot or income.

    python tools/legacy_to_unified.py --buys buys.csv --sells sells.csv --asset FAKEB --wallet FAKE-WALLET-1 -o out.csv

Standard library only (Python 3.10+). Bad rows are skipped with a warning on stderr (file and line).
"""
from __future__ import annotations

import argparse
import csv
import re
import sys
from dataclasses import dataclass
from datetime import date, datetime
from typing import Callable

UNIFIED_HEADER = [
    "type", "date", "asset", "asset_class", "wallet", "quantity", "price_usd", "total_usd", "fee_usd",
    "fee_asset", "fee_quantity", "to_asset", "to_quantity", "to_wallet", "acquired_date", "basis_unknown", "note",
]
ASSET_CLASSES = ("CRYPTO", "STOCK")

# Same number rules as the app's ValueParsers.decimal: optional $, thousands separators only in proper
# groups, scientific notation never grouped.
PLAIN = re.compile(r"\d+\.?\d*|\.\d+")
SCIENTIFIC = re.compile(r"(\d+\.?\d*|\.\d+)[eE][+-]?\d{1,3}")
GROUPED = re.compile(r"\d{1,3}(,\d{3})+(\.\d*)?")
# Same date formats as the app: M/d/yyyy (and MM/dd/yyyy), yyyy-M-d, yyyy/M/d.
DATE_FORMATS = ("%m/%d/%Y", "%Y-%m-%d", "%Y/%m/%d")


class RowError(Exception):
    pass


@dataclass(frozen=True)
class Layout:
    """Column detection identical to the old LegacyImporter (and v1)."""
    type: str
    names: tuple[str, str, str]
    is_date: Callable[[str], bool]
    is_price: Callable[[str], bool]


BUYS = Layout("BUY", ("Date bought", "Quantity", "Purchase price per unit"),
              lambda h: "date" in h and "bought" in h,
              lambda h: "purchase" in h and "price" in h and "unit" in h)
SELLS = Layout("SELL", ("Date sold", "Quantity Sold", "Sale price per unit"),
               lambda h: "date" in h and "sold" in h,
               lambda h: "sale" in h and "price" in h and "unit" in h)


def parse_number(raw: str, column: str) -> str:
    """The number as an exact plain string: `$` and valid thousands separators removed. Never negative."""
    text = raw.strip()
    if not text:
        raise RowError(f"{column} is blank")
    unsigned = text.replace("−", "-")
    if unsigned.startswith("-") or unsigned.lstrip("$").strip().startswith("-"):
        raise RowError(f"{column} is negative: '{raw}'")
    unsigned = unsigned.removeprefix("$").strip()
    if PLAIN.fullmatch(unsigned) or SCIENTIFIC.fullmatch(unsigned):
        return unsigned
    if GROUPED.fullmatch(unsigned):
        return unsigned.replace(",", "")
    raise RowError(f"bad number in {column}: '{raw}'")


def parse_date(raw: str, column: str) -> date:
    text = raw.strip()
    for fmt in DATE_FORMATS:
        try:
            return datetime.strptime(text, fmt).date()
        except ValueError:
            continue
    raise RowError(f"bad date in {column}: '{raw}'")


def is_zero(number: str) -> bool:
    return not any(c in "123456789" for c in number.split("e")[0].split("E")[0])


def read_records(path: str) -> list[tuple[int, list[str]]]:
    """(physical start line, fields) per record; blank lines skipped, like the app's CsvReader."""
    with open(path, newline="", encoding="utf-8-sig") as handle:
        reader = csv.reader(handle)
        records, last = [], 0
        for fields in reader:
            start, last = last + 1, reader.line_num
            if not fields or (len(fields) == 1 and not fields[0].strip()):
                continue
            records.append((start, fields))
    return records


def convert_file(path: str, layout: Layout, asset: str, asset_class: str, wallet: str,
                 warn: Callable[[str], None]) -> list[list[str]]:
    records = read_records(path)
    if not records:
        raise SystemExit(f"{path}: empty file; expected columns {', '.join(layout.names)}")
    header = [name.strip().lower() for name in records[0][1]]
    date_col = next((i for i, h in enumerate(header) if layout.is_date(h)), -1)
    quantity_col = next((i for i, h in enumerate(header) if "quantity" in h), -1)
    price_col = next((i for i, h in enumerate(header) if layout.is_price(h)), -1)
    if min(date_col, quantity_col, price_col) < 0:
        raise SystemExit(f"{path}: missing columns; expected {', '.join(layout.names)}")

    rows = []
    for line, fields in records[1:]:
        field = lambda i: fields[i].strip() if i < len(fields) else ""
        try:
            day = parse_date(field(date_col), layout.names[0])
            quantity = parse_number(field(quantity_col), layout.names[1])
            price = parse_number(field(price_col), layout.names[2])
            if is_zero(quantity):
                raise RowError(f"{layout.names[1]} must be positive")
        except RowError as err:
            warn(f"{path} line {line}: skipped ({err})")
            continue
        row = dict.fromkeys(UNIFIED_HEADER, "")
        row.update(type=layout.type, date=day.isoformat(), asset=asset, asset_class=asset_class,
                   wallet=wallet, quantity=quantity, price_usd=price)
        rows.append([row[name] for name in UNIFIED_HEADER])
    return rows


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Convert legacy buys/sells CSVs to the BlockGains unified CSV.")
    parser.add_argument("--buys", help="legacy buys file (Date bought, Quantity, Purchase price per unit)")
    parser.add_argument("--sells", help="legacy sells file (Date sold, Quantity Sold, Sale price per unit)")
    parser.add_argument("--asset", required=True, help="asset symbol for every row")
    parser.add_argument("--wallet", required=True, help="wallet label for every row")
    parser.add_argument("--asset-class", default="CRYPTO", type=str.upper, choices=ASSET_CLASSES)
    parser.add_argument("-o", "--output", required=True, help="unified CSV to write")
    args = parser.parse_args(argv)
    if not args.buys and not args.sells:
        parser.error("give --buys, --sells or both")
    asset, wallet = args.asset.strip().upper(), args.wallet.strip()
    if not asset or not wallet:
        parser.error("--asset and --wallet can't be blank")

    warnings: list[str] = []
    rows: list[list[str]] = []
    for path, layout in ((args.buys, BUYS), (args.sells, SELLS)):
        if path:
            rows += convert_file(path, layout, asset, args.asset_class, wallet, warnings.append)
    with open(args.output, "w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        writer.writerow(UNIFIED_HEADER)
        writer.writerows(rows)
    for message in warnings:
        print(message, file=sys.stderr)
    zero = sum(1 for r in rows if r[0] == "BUY" and is_zero(r[6]))
    print(f"Wrote {len(rows)} rows to {args.output}; skipped {len(warnings)}."
          + (f" {zero} $0 buy(s): the import preview will ask about each." if zero else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
