# v2 Phase 1 Core: Spec

Scope, order and acceptance for Phase 1 (`project-schema.md`). Tax behavior: `tax-rules.md`. Formats: `import-format-spec.md`. Code rules: `coding-standards.md`. Screens: `ui-guidelines.md`. This spec points to them and doesn't repeat them.

## Goal
Turn the v1 prototype into a persistent, offline app the owner can trust for real crypto records, with exports to hand to a preparer or check against 1099-DA forms.

## v1 bugs (each fixed and covered by a test)
1. No asset column, so every buy/sell shares one FIFO pool (tax-rules R3)
2. `daysHeld > 365`, which is wrong on anniversaries/leap years (R2, T1, T2)
3. A sell can match a buy dated after it (R3, T4)
4. `split(",")` CSV parsing breaks on `"$1,234.56"` (import-format-spec → General parsing rules)
5. Fees ignored (R7, T3)
6. Insufficient lots throws and kills the whole calculation (R10)
7. `Context` in the ViewModel, no DI, nothing persists (coding-standards)

## v1 files this spec replaces
Everything under `app/src/main/java/com/tiffles/blockgains/` (MainActivity may be kept and rewritten), plus `ExampleUnitTest.kt` and `ExampleInstrumentedTest.kt`. Deleting these during the run is pre-approved (ai-guidelines → Autonomous runs). List each one in Handoff notes.

## Milestones
Each one leaves `./gradlew build` green, ends with a commit, a version bump (M1 = `1.1.0` … M6 = `1.6.0`) and a history entry in `current-feature.md`.

**M1. Foundation**: `1.1.0`
- Toolchain per coding-standards → Toolchain. Add Hilt, Room/KSP, Navigation, Kotlin Serialization, DataStore.
- Manifest: remove `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES`, no `INTERNET` (+ the manifest test), `allowBackup="false"` and DB excluded in extraction rules.
- Package layout per coding-standards → Files.
- ✅ App launches to an empty-state Summary.

**M2. Domain + calculator**: `1.2.0`
- Models for all 7 transaction types, lots, taxable events, income events, issues, asset class.
- Calculator implementing R1–R11, including the 2025 transition (R4), transfers, swaps, income, adjustments, and issues instead of exceptions.
- ✅ Tests T1–T12 pass, plus edge cases (empty input, no sells, many partial lots, 9-decimal quantities) and the calculator invariant tests (coding-standards → Testing).

**M3. Persistence + import**: `1.3.0`
- Room schema, repositories, DataStore settings (default wallet, wallet labels).
- RFC 4180 reader, legacy importer, unified importer → import preview with issues + duplicates.
- ✅ Importer tests with made-up fixtures. A legacy-format fixture (mixed `M/d/yyyy` / `MM/dd/yyyy`, `$` on buys only, unsorted rows, a $0 buy) imports correctly. _(`1.8.0`: replaced by its unified equivalent, `fixtures/buys-sells.csv`, when the legacy importer was removed.)_

**M4. Core UI**: `1.4.0`
- Navigation shell, Summary, Transactions (both tabs + event detail), Import flow with preview and $0-buy resolution, Opening positions helper, Settings. All per ui-guidelines.
- ✅ Importing the legacy fixture on a fresh install shows correct year totals _(`1.8.0`: now its unified equivalent, `fixtures/buys-sells.csv`)_. Tapping an event shows its matched lots.

**M5. Exports + backup**: `1.5.0`
- All six exports in ui-guidelines → Export.
- ✅ Round-trip tests pass (unified CSV, JSON). PDF renders a multi-page events table. Form 8949 CSV totals equal the Summary totals.

**M6. Polish + docs**: `1.6.0`
- Accessibility (content descriptions, 200% font, contrast), empty and error states.
- README: update features, add a local storage disclaimer (transactions stored only on this device in an unencrypted app database, never transmitted; no account numbers or wallet addresses; use the in-app backup). Keep the tax disclaimer.
- ✅ `./gradlew build` green. Handoff notes complete.

## Out of scope for Phase 1
Manual add/edit UI, exchange importers, stocks logic beyond the asset-class field, other lot methods (see project-schema Phases 2–3).

## Handoff notes
_(Filled in by the autonomous run, 2026-11-21. Branch `feature/v2-phase1`. Frozen at the merge into `dev` (`1.6.5`); lines tagged `1.6.6`+ were added after the merge. Later fixes are in current-feature history.)_

### Per milestone
- **M1 `1.1.0`:** current stable toolchain (AGP 9.5.2 built-in Kotlin, Kotlin 2.4.21, KSP 2.3.14, Hilt 2.60.1, Compose BOM 2026.11.01, Gradle 9.9.2); Hilt/Room/Navigation/Serialization/DataStore; manifest without storage or network permissions, `allowBackup=false`, merged-manifest test. Needed compileSdk 37.2 (see Decisions).
- **M2 `1.2.0`:** pure-Kotlin domain and `TaxCalculator` (R1–R11), pluggable FIFO strategy, T1–T12 tests, v1-bug regressions, edge cases, 200-seed invariant tests (with a guard that they exercise every path).
- **M3 `1.3.0`:** Room table mirroring the unified CSV (schema v1 exported), DataStore settings, SAF wrapper, RFC 4180 reader, legacy + unified importers, import preview (duplicates, date range, $0-buy resolution). Legacy fixture totals checked end to end. Amended twice; the first commit went in before `./gradlew build` passed (against ai-guidelines → Workflow).
- **M4 `1.4.0`:** all screens in ui-guidelines: Summary, Transactions (+ event and record detail), Issues, Import + preview, Opening positions helper, Settings. One shared `LedgerRepository` computes on `Default`.
- **M5 `1.5.0`:** the six exports, JSON backup/restore. Round trips, 8949-vs-Summary and pagination tests; PDF render test in `androidTest`.
- **M6 `1.6.0`:** 200% font pass, theme/status-bar fix, README.
- **Left undone:** the optional Summary chart (skipped to avoid clutter, as the guidelines allow). No DAO/Room tests yet (the standards say "few"; there's only one schema version, and DAO calls are thin). `TransactionsViewModel` has no unit test (its route args need `SavedStateHandle.toRoute`, which needs Robolectric or an instrumented test).
- **Left undone (review, 1.6.1):** `ExportViewModel` and `TransactionsViewModel` have no unit tests. `ExportViewModel` can't be built in JVM tests because it reaches PDF/`Paint` code directly. Later fix: put the exporters behind an injected interface (coding-standards → Data).

### UI choices to nitpick
- Brand scheme: cozy coffee, generated from seed `#C8925A` (`ui/theme/Color.kt`, ui-guidelines → Color); wallpaper colors off by default (changed in `1.6.4`); warmer cream light surfaces since `1.6.5`.
- Gains use sage, losses terracotta (extended colors, since `1.6.4`), always with a `+` / true-minus sign; zero is neutral. Short/long term shown as text chips (long-term uses `tertiaryContainer`).
- Summary: year as a text-button dropdown in the top bar; breakdown cards stacked full width (better at 200% font than a row); issues banner sits above the hero card.
- Transactions: newest first; term filter chips on the events tab; type/asset/wallet as dropdown filter chips plus a note search on the all tab. The "All transactions" tab ignores the year (raw records); the year picker only shows on the events tab.
- Mixed-term disposals show a "Mixed" chip; the event detail lists each lot with its own term.
- Ordinary income card opens All transactions filtered to INCOME.
- Import format is a radio list (segmented buttons broke at 200% font). Wallet pickers are read-only dropdowns; new labels are added in Settings.
- $0 buys default to "Unknown basis" (the conservative choice); choosing Income asks for a per-unit value.
- Import preview's back button and system back both cancel the import.
- Opening positions: one card per lot with wallet rows, a "Split" button that pre-fills what's unassigned, and a Save button per asset. Assets with OPENING records whose pre-2025 records leave nothing held get a "Remove these records" card (since `1.6.6`, not for assets with no pre-2025 records at all; tax-rules R4).
- Export tab repeats the year picker and asset filter (shared with Summary); restore is a confirmation dialog, not a separate screen.
- Bottom-nav labels truncate to one line at large font sizes (TalkBack still reads the full label).
- PDF: US Letter, one row per event, English only like the CSV headers.

### Open questions added to tax-rules.md
- Partial shortfall: the owner first chose A (unmatched units left out) plus export warnings; since `1.6.6` it's B, the conservative option: unmatched units of a sale or swap count as $0 basis, short-term, marked unknown basis, with their share of proceeds and fee. The issue and export warnings stay. Since `1.6.7` such a slice shows "Unknown" as its acquired date in event detail, the taxable-events CSV and Form 8949 (the PDF has no acquired-date column).
- Same-day order of TRANSFER/SWAP: input order between acquisitions and disposals.

### Doc changes
- `coding-standards.md` → Toolchain: BlockGains' known-good versions, JDK note, compileSdk 37.2.
- `project-schema.md` → phase checklist ticked; Decisions rows: compileSdk 37.2 / targetSdk 36; year exports follow the shared year and asset filter; taxable-events CSV per lot; own CSV reader + Truth, fakes over mockk.
- `import-format-spec.md`: `#` comment lines (template) and strict thousands grouping.
- `tax-rules.md`: the two Open questions above. `1.6.6` (owner-approved): R3 shortfall → option B, R4 `TransitionMismatch` only for assets with records before 2025, T4 has a sale price and the new expected event.
- `README.md`: features, local storage and privacy disclaimer, build steps, tax disclaimer.

### Deleted v1 files
All replaced as the spec allows: `data/calculator/FifoCalculator.kt`, `data/model/Models.kt`, `data/parser/CsvParser.kt`, `ui/navigation/AppNavigation.kt`, `ui/screen/CsvUploadScreen.kt`, `ui/screen/DashboardScreen.kt`, `ui/viewmodel/DashboardViewModel.kt`, `ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`. `MainActivity.kt`, `ui/theme/Theme.kt` and `ui/theme/Type.kt` were rewritten in place. `res/mipmap-anydpi-v26` was renamed to `mipmap-anydpi` (lint: minSdk is 26).

### Not verified
- All androidTest cases pass on the managed device since 2026-11-21. The PDF page count was also checked by hand on the emulator (120 events → 3 pages).
- Only on the emulator (API 37, dynamic color on, dark system theme; light theme and 200% font spot-checked). Not on a real phone, not on Android 8–11 (brand scheme path), no TalkBack walkthrough.
- Restore was checked once (backup → import more → restore). Room migrations: none yet (schema v1).
