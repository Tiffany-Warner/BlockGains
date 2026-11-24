# Reference calculator (differential testing)

An independent, deliberately simple Python implementation of `context/tax-rules.md`, used to check the app's Kotlin calculator on large made-up datasets. If two separately written implementations agree on hundreds of random scenarios, the chance they're wrong in the same way is small.

Standard library only (Python 3.10+). All data it generates is synthetic and safe to commit.

| File | What it does |
|---|---|
| `refcalc.py` | Reads a unified-format CSV and outputs expected results (per-year ST/LT/net/income, every taxable event, remaining lots, issues) |
| `gen.py` | Generates random datasets plus their `.expected.json`, and a Koinly CSV for the `koinly` profile |
| `koinly.py` | Converts a unified CSV to Koinly's universal import format |
| `test_refcalc.py` | Checks the reference against worked cases T1–T14 |
| `test_legacy_to_unified.py`, `testdata/` | Tests for `../legacy_to_unified.py` (not part of the reference; see `../README.md`) |

## Quick start

```bash
cd tools/reference_calc
python -m unittest test_refcalc -v          # reference matches T1–T14, R3 same-day order
                                            # (or, from the repo root: python tools/reference_calc/test_refcalc.py)
python gen.py --seed 1 --count 10           # out/full-seed1..10 (.csv + .expected.json)
python gen.py --seed 1 --profile koinly     # + out/koinly-seed1.koinly.csv
python refcalc.py some.csv --summary        # expected results for any unified CSV (`#` comment lines are skipped)
```

### Generator options
- `--profile full` (default): 3 wallets, buys, sells, swaps, income, transfers (before and after 2025; about half pay a network fee, some in another coin), dust adjustments, fees, occasional $0/unknown-basis buys, OPENING rows on 2025-01-01 split across wallets
- `--profile koinly`: one wallet, only BUY/SELL/SWAP/INCOME, cent-rounded USD amounts. One wallet means "universal before 2025, per-wallet after" can't differ between tools
- `--opening split|none|mismatch`: how the 2025 transition is represented (`mismatch` leaves dust behind to trigger `TransitionMismatch`)
- `--errors N`: append N bad rows (oversells → `InsufficientLots`, missing price / bad date → `InvalidRow`)
- `--transactions`, `--start`, `--end`, `--assets FAKEA,FAKEB,FAKEC,FAKED`

Every transaction gets its own date, so same-day ordering never comes into play. `test_refcalc.py` covers it (`SameDayOrder`). Decimals are written in plain notation (never `0E-8`).

## Using it in BlockGains tests

1. The app's fixtures in `app/src/test/resources/fixtures/differential` are 30 generated pairs from these commands, plus hand-written `case-*.csv` files for rules the generator doesn't reach. Rerun all three commands after changing the reference or the generator; never edit generated files or any `.expected.json` by hand:
   ```bash
   OUT=../../app/src/test/resources/fixtures/differential
   python gen.py --seed 64 --count 20 --out $OUT                                # OPENING rows split across wallets
   python gen.py --seed 21 --count 5 --opening none --out $OUT                  # default-wallet transition
   python gen.py --seed 100 --count 5 --opening mismatch --errors 3 --out $OUT  # mismatch, oversells, bad rows
   ```
   For each `case-*.csv`: `python refcalc.py $OUT/<case>.csv -o $OUT/<case>.expected.json --default-wallet FAKE-WALLET-1`
2. `DifferentialFixturesTest` (JVM, `domain/calc`): for each `*.csv`, import it with the unified importer, run the calculator with `default_wallet` from the JSON's `settings`, and compare with `*.expected.json`. All differences across all fixtures are reported in one failure:
   - per year: `short_term.gain`, `long_term.gain`, `net_gain`, `ordinary_income` (cents, HALF_UP, totals from unrounded values)
   - per year: lot-match counts by term (`short_term.count`, `long_term.count`; the reference has one event per matched lot)
   - issue kinds and counts (import `InvalidRow`s plus calculator issues)
3. On a mismatch the test also prints the first disagreeing lot-level event (disposal line, quantity, acquired date, term, proceeds, basis) from `events`.

When the two disagree, don't assume the app is wrong. Find which rule the difference comes from and check it against `tax-rules.md`. If the rule is ambiguous, add an Open question there and fix whichever side is wrong.

## Comparing with Koinly

1. `python gen.py --seed 7 --profile koinly` → `out/koinly-seed7.koinly.csv` and `out/koinly-seed7.expected.json`
2. In Koinly: create a test account (or a separate portfolio) with base currency **USD**, US tax rules, and cost basis method **FIFO**. Add one wallet and import the `.koinly.csv` with Koinly's **universal / custom CSV** option.
3. Compare Koinly's per-year capital gains (short-term, long-term) and income against the `years` section of the expected JSON. As far as I know the free plan shows these totals but charges to download reports.
4. Small differences in the cents can come from Koinly's own rounding. Bigger differences point to a real disagreement, so go to the events. Each Koinly row's Description says `blockgains line N`, which matches `line` in the expected JSON.

Dates are written as noon UTC so they keep the same calendar date in US time zones. Transfers aren't converted yet; when they are, the matching Koinly settings for network fees are listed in tax-rules R13. Swaps carry their USD value in Net Worth Amount, and income uses the `staking` label.

Only ever import **generated** data into Koinly or any other outside tool. Never your real records.

## Rules the reference implements (keep in sync with `tax-rules.md`)

The rules themselves live in `context/tax-rules.md` and `context/specs/import-format-spec.md`; don't restate them here. Where to look in the code:

1. **R3, unmatched units (Open questions: partial shortfall, B):** `Engine._dispose`. Output events carry `"unmatched"`, `"basis_unknown"` and `"acquired": null`.
2. **R4, mismatch only with pre-2025 history:** `Engine.transition` and `pre2025_assets`. Fixture `case-swap-only-history`.
3. **R3, same-day order:** `PRIORITY`.
4. **import-format-spec, OPENING validation:** `parse_row`. Fixture `case-opening-wrong-date`.
5. **R13, network fees on transfers:** `Engine.apply` (TRANSFER disposes the fee first via `_dispose`, before 2025 too) and the fee checks in `parse_row`. Fee events carry `"kind": "transfer_fee"`. Fixtures `case-transfer-fee-same-asset`, `case-transfer-fee-other-asset`, `case-transfer-fee-pre2025`, `case-transfer-fee-shortfall`.

## Formerly unwritten choices

These used to be listed here as choices `tax-rules.md` didn't spell out. They are now rules there; compare against those, not this file:

1. **Lot order after a transfer:** tax-rules R3 (transferred-in lots sort by original acquired date). Code: `Engine` pool order; app `LotPools.add`.
2. **Pre-2025 `TRANSFER` and `ADJUST` rows:** tax-rules R4 (pre-2025 moves ignored for matching), R13 (a pre-2025 network fee is still disposed of from the universal pool) and R3/R9 (a pre-2025 ADJUST removes units from the universal pool).
