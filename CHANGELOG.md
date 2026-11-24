# Changelog

## 2.0.0 (2026-11-24)

A rebuild of BlockGains. Version 1 read one asset's buys and sells from two files and kept nothing once you closed it. Version 2 keeps all your records on your device and covers the 2025 crypto rules.

> BlockGains is an estimator for personal records, not tax advice. Check the results against your broker forms (1099-DA / 1099-B) and a qualified tax professional before filing.

### New

- **One CSV for everything.** A single unified format holds any mix of assets and wallets, with buys, sells, swaps, income, transfers, opening positions and adjustments. Download a blank template from the app. Every import shows a preview first: rows to add, duplicates that will be skipped, problems with their line numbers, and any $0 buys to resolve.
- **Your records are kept.** Everything is stored on the device, so you import once and come back to it.
- **2025 per-wallet rules.** Disposals before 2025 use one FIFO pool per asset; from January 1, 2025, each wallet has its own pool. The new *Opening positions* helper assigns what you held on that date to your wallets and keeps the original dates and basis.
- **Fees.** Buy fees add to basis and sale fees reduce proceeds.
- **Transfer network fees.** Moving coins between your own wallets isn't taxable, but the coins paid as the network fee count as sold, so they can have a small gain or loss.
- **Swaps** are treated as disposals of the coin you gave up, and the coin you received starts a new lot.
- **Income.** Staking and reward income is reported as ordinary income for the year.
- **Summary by tax year** with short-term, long-term, net and ordinary income, filtered by asset class. Tap any taxable event to see the exact lots it used and the rule that applied.
- **Problems are shown, not hidden.** Missing lots or mismatched opening positions appear as plain-language issues instead of crashes or silently wrong totals.
- **Exports:** taxable events CSV, Form 8949-style CSV (Part I and Part II with totals), income CSV, a printable PDF report, and all transactions in the unified format.
- **Full backup and restore** (JSON), including settings.
- **Delete data** from Settings: all transactions, or everything including settings. Both ask twice and offer a backup first.
- **Release signing.** You can sign release builds with your own key (see the README, Building).
- New look with light and dark themes, optional wallpaper colors on Android 12+, and layouts that work at large font sizes.

### Upgrading from 1.x

- Version 1 stored nothing, so there is no data to move. Convert old two-file buys/sells CSVs to the unified format with `tools/legacy_to_unified.py` (see `tools/README.md`), then import the result.

### Known limitations

**Tax rules**

- **FIFO only.** No HIFO or specific identification yet. If you gave a broker a standing order (such as HIFO) or identified specific units, your real numbers won't match.
- **No loss carryovers.** A net loss only shows a note about the $3,000 yearly limit ($1,500 if married filing separately).
- **Income is ordinary income only.** No self-employment tax for mining or business activity.
- **Stocks: no wash-sale rule yet.** That comes with the stocks phase. Wash sales don't apply to crypto under current law (last checked November 2026).
- **US federal only.** No state taxes.
- **2025 safe harbor.** The app doesn't decide whether a Rev. Proc. 2024-28 allocation was made, or whether your opening positions count as one. Ask a tax preparer.

**Your records**

- **No price lookups.** You enter the USD values for swaps, income and fees.
- **No time of day.** On the same date: buys, income and opening positions first, then transfers and swaps, then sales and adjustments, each group in file order.
- **Missing records.** Units sold, swapped or spent on a transfer fee without a matching purchase count as $0 basis and short-term (conservative), and are flagged. A transfer or adjustment with too few units is flagged only.
- **Swap fee paid in a third coin** (for example gas paid in another token) isn't recorded as a sale of that coin yet.

**Not covered yet:** liquidity pools, lending, wrapping or bridging, NFTs, margin and derivatives, exchange-specific importers, and 1099-DA reconciliation.
