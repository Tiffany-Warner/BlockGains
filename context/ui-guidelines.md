# UI Guidelines

Source of truth for screens, navigation, and visual rules. The owner reviews UI by nitpicking a working build, so make sensible choices, keep them consistent, and list them in the spec's Handoff notes.

## Principles
- **Material 3, stable components.** The cozy coffee brand scheme, with optional wallpaper colors on Android 12+ (see Color). Light + dark. M3 type scale and shape scale. No hard-coded colors or sizes outside the theme.
- Mobile best practices: 48dp touch targets, edge-to-edge with correct insets, works at 200% font scale, TalkBack labels on every icon button and on chart elements, no information carried by color alone (gains/losses use a sign and/or icon, not just green/red).
- Numbers: tabular figures for money columns, `$1,234.56` / `−$1,234.56` (true minus sign), quantities trimmed to significant decimals (max 8 shown, full precision in detail view).
- Every list has an empty state that tells you what to do next (usually "Import transactions").
- Calm, utilitarian, trustworthy. This is a records tool, not a trading app.

## Color
- **Brand palette ("cozy coffee"):** full Material 3 light + dark schemes generated from seed `#C8925A` with Material color utilities (TonalSpot, 2021 role tones, primary palette chroma 28 so the browns stay muted). Light surfaces are a warmer cream: neutral palette at the hue/chroma of `#FDF3E4`, surface roles 2 tones lower than M3 (surface 96). Values live only in `ui/theme/Color.kt`. Change the seed or palette parameters and regenerate; don't hand-edit single roles.
- **Wallpaper colors** (dynamic color) are off by default. Settings → "Use wallpaper colors" turns them on; the switch only exists on Android 12+.
- **Gains and losses** use the extended colors `MaterialTheme.gainLoss`, never `primary`/`error` (a loss is not an app error): gain = sage (light `#2F6A3C`, dark `#8FC79A`), loss = terracotta (light `#A73826`, dark `#F2A08E`), each with a container from its own tonal palette. They stay the same with wallpaper colors. Always with a `+` / true-minus sign; zero is neutral (`onSurface`).
- **Contrast:** every text/background pair meets WCAG AA (4.5:1), including gain/loss on cards and on the hero card's `primaryContainer`. Recheck whenever the palette changes.

## Navigation
**Bottom navigation bar** (M3 `NavigationBar`) with three destinations:
1. **Summary**
2. **Transactions**
3. **Import / Export** (one destination with two tabs or sections)

**Settings** opens from a top-app-bar action on Summary.

**Back returns to the screen you came from and never switches tabs.** The one exception is the platform default on a bottom-nav tab: back from Transactions or Import / Export goes to Summary (the start destination).

Asset class is a **filter, not a destination**: an M3 segmented button `All · Crypto · Stocks` at the top of Summary and Transactions, defaulting to **All**. The filter and the selected year are shared across screens (hoisted state). Filters (asset class, year, Transactions filters) last for the session only: after the app is closed or killed they start at their defaults. Records and settings persist.

## Screens

### Summary
- Top app bar: title, **tax-year dropdown** (exposed dropdown menu listing every year with activity, defaulting to the current year, or the latest year with data if the current one has none), Settings action.
- Asset-class segmented button.
- **Hero card:** net capital gain/loss for the year.
- **Breakdown cards:** Short-term, Long-term, Ordinary income (staking/rewards, R8). Each one is tappable and opens Transactions pre-filtered.
- **Issues banner** if the calculator returned issues for this year (count + "Review"). Tapping it opens an issues list with plain-language explanations and a suggested fix (e.g. "Add an ADJUST record" for dust).
- Loss-limit note when net < 0 (`tax-rules.md` R11).
- Small footer disclaimer: "Estimate for personal records, not tax advice."
- Optional, if it fits cleanly: a simple bar chart of ST vs LT by month. Skip it if it adds clutter.

### Transactions
- Two views via tabs: **Taxable events** (matched disposals for the selected year, which is what v1 shows) and **All transactions** (raw records).
- Taxable event row: date sold, asset, wallet, quantity, proceeds, basis, gain/loss, ST/LT chip. One row per disposal (not per lot); a disposal with both short- and long-term lots gets a "Mixed" chip. With the Short-term or Long-term filter, a Mixed row stays one row with its chip but shows only that term's proceeds, basis and gain ("Short-term part: Proceeds … · Basis …"), so the rows add up to the Summary's term total. Tapping it shows **detail** (always the whole disposal): the matched lot(s) with acquired dates, per-lot basis, fees, and the rule that applied (FIFO universal vs per-wallet).
- All-transactions row: type icon + label, date, asset, wallet, quantity, USD value. Filters: type, asset, wallet. Search by note.
- **Transfer network fees (tax-rules R13):** a TRANSFER row adds "Sent 1 · fee 0.01 · received 0.99" (or "Received 100 · fee 0.005 FAKEB" for a fee in another coin). Its detail labels `quantity` "Amount received", shows the fee in coins and its USD value with the helper "The coins spent on the fee count as sold, so they can have a small gain or loss.", and links to the fee's taxable event. Fee events read "0.01 FAKEB (transfer fee)" in lists and Form 8949 column (a); their detail is titled "Network fee for transfer on <date>". No setting.
- **Fee labels on event detail:** the SELL/SWAP fee share (R7) is "Selling fee" with "Subtracted from proceeds" under it, on the event and each lot; transfer-fee events don't show it. When a matched lot came from a BUY with a fee, its basis gets "Includes buy fee". Form 8949 CSV and the PDF table have no fee column (proceeds are already net of fees, basis already includes buy fees).
- Phase 2: add/edit/delete a single transaction from here (FAB + detail screen).

### Import / Export
**Import**
- One format, the unified CSV: pick the file ("Unified CSV file"), then **Preview import**. No format choice and no asset/wallet inputs (each row has its own).
- **Preview** screen: rows to add, duplicates skipped, issues (with line numbers), date range, and a non-blocking note when values were written in scientific notation ("N values were written in scientific notation; spreadsheet apps can round these, so check them.", import-format-spec → General parsing rules). Resolve $0 buys here (unknown basis vs income). Confirm → write.
- **Leaving the preview:** back (top-left or system), Cancel and Confirm all return to Import / Export, never another tab. Back and Cancel write nothing and keep the picked file selected; Confirm writes, then shows "N transactions imported".
- "Download blank template" (unified CSV with header and commented example rows). Its card adds "Totals are before fees; put fees in fee_usd.", and the template's comment lines say the same (import-format-spec).
- **Opening positions (2025-01-01) helper:** shows what the universal pool holds per asset on 2025-01-01 and lets the user assign lots to wallets (split a lot across wallets). Saves OPENING records. Save stays off until every unit of the asset is assigned ("0.5 not assigned"), so the helper can't create a mismatch. A `TransitionMismatch` still shows if totals don't match for an asset with records before 2025 (tax-rules R4); that comes from imported OPENING rows.

**Export** (all through SAF `CreateDocument`; file names include the year, e.g. `blockgains-2025-taxable-events.csv`)
1. Tax-year taxable events CSV (selected year)
2. All transactions CSV (unified format, round-trippable)
3. **Form 8949-style CSV** for the year: Part I (short-term) and Part II (long-term) with columns (a) description, (b) date acquired, (c) date sold, (d) proceeds, (e) cost basis, (h) gain/loss. Multi-lot disposals become one row per lot.
4. Ordinary income CSV for the year (R8)
5. **PDF report** for the year: title + year + generation date, ST / LT / net / income totals, a taxable-events table (paginated), the disclaimer. Plain and printable.
6. **Full backup (JSON)** and **Restore from backup** (restore shows a preview + confirm and warns that it replaces current data).

**No records:** Export shows an empty state ("Nothing to export yet") with an Import transactions button that opens the Import tab, and only Restore from backup below it.

**Shortfall warning:** unresolved `InsufficientLots` issues dated on or before the end of the selected year, for assets in the current filter, show a red warning card above the year exports (Review → Issues) and a red banner on PDF page 1. CSV contents don't change.

### Settings
- **Disclaimer card** first, above everything else: info icon, "Estimate for personal records, not tax advice." (titleMedium) and "Check results against your 1099-DA or 1099-B and a tax professional before filing." Tertiary container colors (not the error red), read by TalkBack as one item. The Summary footer keeps its one-line disclaimer.
- Default wallet (R4: gets anything held on 2025-01-01 without opening records).
- Manage wallets: add/rename labels (rename updates records). Hint text: "Use labels, not real addresses."
- Theme: system / light / dark. "Use wallpaper colors" switch (Android 12+ only, off by default; see Color).
- About: version, "Data is stored only on this device.", "License: GPL-3.0-or-later" with a "View license" button (full-screen, offline view of the bundled text; no web link). No disclaimer line here (it's the card at the top).
- **Data** (last section): two outlined buttons in the error color.
  - "Delete all transactions": empties the transactions table; default wallet, wallet labels, theme and wallpaper colors stay.
  - "Delete all data": empties the table and resets every setting to a fresh install.
  - Both confirm twice. Step 1 dialog: what goes, with the record count ("This deletes 1,234 transactions…" / "…and resets all settings"), "This can't be undone.", a "Back up first" button that saves the Full backup (JSON), then Cancel and Continue. Cancel gets the first keyboard focus in both steps (buttons can't hold focus in touch mode, so it applies once a key is pressed). Step 2: type `DELETE` (case-sensitive) to enable the red Delete button. Cancel or dismiss at either step changes nothing.
  - After deleting: snackbar, then the (empty) Summary. The year and asset filter go back to their defaults.
