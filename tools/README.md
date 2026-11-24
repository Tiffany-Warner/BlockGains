# Tools

Standard library only (Python 3.10+). Nothing here is part of the app build.

| Folder / file | What it is |
|---|---|
| `reference_calc/` | Independent reference calculator for differential tests (`reference_calc/README.md`) |
| `legacy_to_unified.py` | One-off converter for v1's legacy buys/sells CSVs (below) |

## Converting legacy buys/sells files

The app imports only the unified CSV since `1.8.0`. To bring in v1's two-file format (one asset, no wallet or fee columns):

```bash
python tools/legacy_to_unified.py --buys buys.csv --sells sells.csv --asset FAKEB --wallet FAKE-WALLET-1 -o fakeb-unified.csv
```

- `--buys` and `--sells`: either or both. Columns are found the way the old importer found them: buys need headers containing "date"+"bought", "quantity" and "purchase"+"price"+"unit"; sells need "date"+"sold", "quantity" and "sale"+"price"+"unit". A file without them stops the run and nothing is written.
- `--asset` (uppercased), `--wallet`, `--asset-class` (`CRYPTO` default, or `STOCK`) apply to every row, like the old import screen.
- Output: the full unified header, `BUY`/`SELL` rows with `price_usd`, dates as `yyyy-MM-dd`, numbers as exact strings (`$` and valid thousands separators removed, never rounded). `fee_usd` stays blank, which is what the old importer stored.
- A $0 buy stays at price 0, so the import preview asks whether it was an unknown-basis lot or income.
- Bad rows (bad date, blank or badly grouped number, quantity ≤ 0, negative price) are skipped and listed on stderr with file and line. Fix them in the source file and convert again.

**Files you already imported with the old importer:** re-importing the converted file flags every row as a duplicate except $0 buys. When you imported them, the preview turned each $0 buy into an unknown-basis lot or an INCOME record, so it no longer matches the unresolved row. Delete $0-buy rows from the output before re-importing those files.

Tests: `cd tools/reference_calc && python -m unittest test_legacy_to_unified -v`. The converter's output for `reference_calc/testdata/legacy-*.csv` must equal `app/src/test/resources/fixtures/converted-from-legacy.csv`, which the app's `BuysSellsFixtureTest` imports with no issues and compares with `buys-sells.csv`.

Never run it on real files inside the repo, and never commit its output from real records.
