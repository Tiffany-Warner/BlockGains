# Import format spec

Source of truth for every CSV the app reads: the unified format is the only import format. The app also ships a downloadable blank template for the unified format (see `ui-guidelines.md` → Import).

## 1. General parsing rules (all formats)
- **RFC 4180 CSV:** quoted fields, commas inside quotes (`"$1,234.56"`), escaped quotes. Don't use `String.split(",")` (the v1 bug).
- Header matching is case-insensitive and ignores surrounding whitespace.
- Dates accepted: `M/d/yyyy`, `MM/dd/yyyy`, `yyyy-MM-dd`, `yyyy/MM/dd`. Rows can be in any order. Sort after parsing.
- Money: optional `$`, optional thousands separators, optional surrounding quotes. Quantities can have 9+ decimal places. Parse straight to `BigDecimal` (never through `Double`).
- Scientific notation is accepted in every numeric field (`0E-8`, `1.2E-05`, `1.5e3`, `-1.2E-3` for an ADJUST), parsed exactly with `BigDecimal`: a plain number, `e` or `E`, an optional sign and 1–3 exponent digits. It can't be combined with thousands separators. Spreadsheet apps write small and large numbers this way and may already have rounded them, so the preview shows a non-blocking note with how many values to check.
- Blank lines are skipped. In the unified format, lines whose first field starts with `#` are comments (the template's example rows use this).
- Thousands separators must be in proper groups (`1,234.56`). Anything else with a comma (`1,5`) is an `InvalidRow`, never guessed. A bad row becomes an `InvalidRow` issue with its line number and a reason, and the other rows still import (`tax-rules.md` R10).
- Import is a **preview → confirm** flow: show counts, issues, and the date range before writing to the database. Duplicate detection: a row identical to one already in the DB in every column except `note` (numbers compared ignoring trailing zeros; a blank `fee_asset` with a `fee_quantity` counts as the row's `asset`) is flagged and skipped by default.

The legacy two-file buys/sells format was removed in `1.8.0`; convert old files with `tools/legacy_to_unified.py` (`tools/README.md`).

## 2. Unified format
One file, any mix of assets, wallets, and types.

```
type,date,asset,asset_class,wallet,quantity,price_usd,total_usd,fee_usd,fee_asset,fee_quantity,to_asset,to_quantity,to_wallet,acquired_date,basis_unknown,note
```

Columns are matched by name, so files written before `1.7.0` (without `fee_asset` / `fee_quantity`) still import. The template, the "All transactions" export and the JSON backup include them. The template's `#` comment lines say that totals are before fees and how a TRANSFER's network fee is entered; the header itself is the same as the export's.

| Column | Required for | Meaning |
|---|---|---|
| `type` | all | `BUY`, `SELL`, `SWAP`, `INCOME`, `TRANSFER`, `OPENING`, `ADJUST` |
| `date` | all | Transaction date (for OPENING, use `2025-01-01`) |
| `asset` | all | Symbol, uppercase (`FAKEA`, `FAKEB`) |
| `asset_class` | optional | `CRYPTO` (default when blank) or `STOCK`, any case. Anything else is an `InvalidRow` |
| `wallet` | all | User label, e.g. `FAKE-EXCHANGE-1`. **Never a real address.** |
| `quantity` | all | Positive, except ADJUST, which must be negative. For TRANSFER: what **arrived** in `to_wallet` (`tax-rules.md` R13) |
| `price_usd` / `total_usd` | BUY, SELL, INCOME, OPENING | Per-unit price or total **before fees** (the subtotal before fees, not the total including fees). Put fees in `fee_usd`: a buy fee is added to basis, a sell/swap fee is subtracted from proceeds (R7). Including the fee in `total_usd` and `fee_usd` counts it twice. One of the two is enough; if both are given they must agree within $0.01. For OPENING, this is the **original** cost. |
| `fee_usd` | optional | BUY/SELL/SWAP: fee in USD (`tax-rules.md` R7). TRANSFER: USD value (FMV) of the fee coins, required with `fee_quantity` (R13). Not allowed on other types above $0 |
| `fee_asset` | optional | TRANSFER only: symbol of the coin the network fee was paid in; blank = `asset` (R13) |
| `fee_quantity` | TRANSFER with a network fee | Units spent on the fee, positive. With the fee in the same coin, the source wallet loses `quantity` + `fee_quantity` (R13) |
| `to_asset`, `to_quantity`, `total_usd` | SWAP | What was received and its USD value V (R6). A fee paid in the coin being swapped: include those units in `quantity`, leave `fee_usd` blank (IRS FAQ 97/99: the fee units are disposed of and their value is a cost of the swap, so they net out). A fee withheld from the coin received: enter what arrived and its value, `fee_usd` blank (IRS FAQ 98/72). `fee_usd` is for a fee paid separately, e.g. in USD. A fee in a third coin can't be entered yet (`tax-rules.md` Open questions) |
| `to_wallet` | TRANSFER | Destination wallet |
| `acquired_date` | OPENING | Original acquisition date (R4). An OPENING row must be dated `2025-01-01` with `acquired_date` on or before it; otherwise it's an `InvalidRow` |
| `basis_unknown` | optional | `true` for intentional $0-basis lots (R9). `true`/`yes`/`y`/`1` mean true and blank/`false`/`no`/`n`/`0` mean false, any case. Anything else is an `InvalidRow` |
| `note` | optional | Free text, stored and shown in the transaction detail |

**Strict parsing (written rule since `1.8.1`; the app always worked this way):** every non-blank value is read and must be valid, even in a column the row's type doesn't use, so a malformed value in any column makes the row an `InvalidRow`: a bad date (`acquired_date` on a BUY), a bad number (`to_quantity` on a SELL, badly grouped like `1,5` or `12,34`, grouping combined with scientific notation), an unknown `asset_class`, an unrecognized `basis_unknown`. A SWAP whose `to_asset` is its own `asset` (any case) is an `InvalidRow`. A leading minus can be ASCII `-` or U+2212 `−` (what the app displays), before or after `$`. **Valid** values in columns a type doesn't use are stored but ignored: `acquired_date` counts only on OPENING, `to_asset` / `to_quantity` only on SWAP (including R4's "records before 2025"), `to_wallet` only on TRANSFER.

**Network fee rows are `InvalidRow`s, never silently ignored, when:** `fee_quantity` has no `fee_usd`; a TRANSFER has a `fee_usd` above $0 or a `fee_asset` but no `fee_quantity`; `fee_quantity` ≤ 0; or `fee_asset` / `fee_quantity` is on any type other than TRANSFER. A TRANSFER with `fee_usd` of exactly 0 and no `fee_quantity` has no fee (valid, as before `1.7.0`). `fee_usd` above $0 on INCOME, OPENING or ADJUST was already an `InvalidRow` before `1.7.0` and still is.

Examples (made-up numbers):
```
type,date,asset,asset_class,wallet,quantity,price_usd,total_usd,fee_usd,fee_asset,fee_quantity,to_asset,to_quantity,to_wallet,acquired_date,basis_unknown,note
BUY,2023-01-05,FAKEB,CRYPTO,FAKE-WALLET-2,10,20.00,,,,,,,,,,
OPENING,2025-01-01,FAKEB,CRYPTO,FAKE-WALLET-2,10,20.00,,,,,,,,2023-01-05,,carried from universal pool
SWAP,2025-03-10,FAKEB,CRYPTO,FAKE-WALLET-2,9.9,,1500.00,,,,FAKEUSD,1500,,,,on-chain swap
TRANSFER,2025-03-12,FAKEUSD,CRYPTO,FAKE-WALLET-2,1500,,,,,,,,FAKE-EXCHANGE-1,,,
TRANSFER,2025-03-13,FAKEB,CRYPTO,FAKE-WALLET-2,0.09,,,3.00,,0.01,,,FAKE-EXCHANGE-1,,,quantity = received; network fee 0.01 FAKEB worth $3.00
SELL,2025-03-15,FAKEUSD,CRYPTO,FAKE-EXCHANGE-1,1500,1.00,,,,,,,,,,
INCOME,2025-02-01,FAKEB,CRYPTO,FAKE-EXCHANGE-1,0.5,100.00,,,,,,,,,,staking
ADJUST,2025-06-01,FAKEB,CRYPTO,FAKE-WALLET-1,-0.0012,,,,,,,,,,,fee dust
```

## 3. Later (not phase 1)
- Exchange transaction-history CSV importers (map to the unified model).
- Broker 1099-B / 1099-DA CSV for reconciliation (stocks phase).

## 4. Export ↔ import round trip
The "All transactions" CSV export **is** the unified format, so export → import into a fresh install reproduces the same data. The JSON backup is the full-fidelity version (it includes settings). Test both round trips.
