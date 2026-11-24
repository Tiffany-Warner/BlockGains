# Current Feature

## Done: import flash and plurals (`1.8.5`)

Found while recording the README GIFs. No tax rule changes; stored data and backups unchanged.

- [x] Import preview: no "Nothing to review" flash after Confirm or Cancel; the screen keeps the last preview while it closes
- [x] Count strings audited; plurals for the Opening positions lot line ("1 unit"), the preview's duplicate and problem tiles, and "Matched lot (1)"
- [x] Build, managed-device tests
- [x] Merge into `dev` (pre-approved)

## Done: README layout (docs only, no bump)

- [x] README replaced with the owner's new layout: disclaimer up front, contents links, feature groups, getting started, privacy, limitations tables, release signing in a collapsible block
- [x] Facts checked against the app and docs; section links and the `<details>` block checked; privacy scan
- [x] Merge into `dev`

## Done: manual test round 1 fixes (`1.8.4`)

From the owner's first manual test pass on the emulator. No tax rule changes; stored data and backups unchanged.

- [x] Import preview: income value error only after typing
- [x] Cancel gets the first keyboard focus in both delete dialogs
- [x] Export empty state pointing to Import (Restore stays)
- [x] Pre-2025 shortfall issues name the record's wallet and say lots are pooled (app and PDF banner)
- [x] A wallet rename updates an active Wallet filter; long filter chips end with "…"
- [x] PDF: "+" on gains, true minus on losses; shortfall banner wraps
- [x] Transactions header scrolls with the list (landscape)
- [x] Under a term filter a Mixed sale shows only that term's proceeds, basis and gain
- [x] Bottom bar labels wrap to two lines (owner kept it)
- [x] Decisions in ui-guidelines and project-schema: one row per sale, filters not persisted, opening helper Save guard
- [x] Settings disclaimer card at the top; About no longer repeats it
- [x] Build, managed-device tests, re-run the failed manual checks on the emulator
- [x] Owner review, then merge into `dev`

## Done: release signing (`1.8.3`)

So the owner can install a non-debuggable release build before real data goes in. No app behavior change.

- [x] `signingConfigs.release` from the user-level `~/.gradle/gradle.properties` only (four `BLOCKGAINS_RELEASE_*` values); unsigned release when they're missing. Release: not debuggable, minify off
- [x] README → Building: keytool command, the four properties, what to back up
- [x] Build, managed-device tests
- [x] Owner review, then merge into `dev`

## Done: flaky license-back test (test-only, no bump)

- [x] Emulator check: is it an app bug? (not from `1.8.2`; see history)
- [x] `SettingsAboutTest` sends Back to the dialog's window once it has focus
- [x] Test alone 10× on the commit before `1.8.2` and 10× on the fix; full suite 4× in a row
- [x] ai-guidelines: 0-test or short runs fail; fail-then-pass isn't a pass
- [x] Merge into `dev`

## Done: import preview exit + delete data (`1.8.2`)

Before the owner's real-data check. No tax rule changes; stored data and backups unchanged.

- [x] Import preview: back, Cancel and Confirm return to Import / Export (no tab switch); back writes nothing, the picked file stays; snackbar "N transactions imported"
- [x] Other screens checked for tab jumps on back
- [x] Settings → Data: Delete all transactions / Delete all data, two-step confirmation with count, Back up first, type DELETE
- [x] ViewModel and Compose tests; TopLevelNavigationTest updated
- [x] Docs: ui-guidelines, README, project-schema Decisions + Phase 2 item
- [x] Build, managed-device tests, emulator check
- [x] Owner review, then merge into `dev`

## Done: Phase 1 final gate (docs only, no bump)

Before the owner's real-data check. The third-coin swap fee is deferred to Phase 2 (owner, 2026-11-22): Open question stays undecided, option B implemented.

- [x] Privacy: no real exchange name or ticker in docs, template comments, tests or tools (outside R6's quoted source); history checked read-only
- [x] Doc drift: reference README pointers to R3/R4/R9/R13, no mockk in coding-standards, import-format-spec sections numbered
- [x] Phase 2 item to decide the third-coin swap fee; real-data checklist under Order step 5
- [x] Gate checks: build, managed-device tests, reference + converter tests
- [x] IRS FAQ citations in R6, R13 and the spec (owner checked the page)
- [x] Owner review, then merge into `dev`

## Done: import-rule follow-ups (`1.8.1`)

From the step-1 parsing check of the legacy removal (owner's choice A: fixed after it merged). No tax rule changes; stored data and backups unchanged.

- [x] `TaxRules.assetsWithHistory`: `to_asset` only from SWAP rows (calculator and Opening positions), as R4 already says; regression test
- [x] import-format-spec section 2: strict parsing written down (malformed value in any column, `asset_class`, `basis_unknown` values, SWAP to its own asset, grouping, U+2212)
- [x] `refcalc.py` `parse_row` matches, reference tests, 7 hand-written differential cases; existing fixtures unchanged
- [x] Owner review, then merge into `dev`

## Done: remove the legacy two-file import (`1.8.0`)

Owner decision (2026-11-22): the unified CSV becomes the only import format. Old buys/sells files go through a one-off converter, `tools/legacy_to_unified.py`. No change to tax behavior, stored data or backups.

- [x] Step 1: unified parsing rules checked against import-format-spec and `refcalc.py` `parse_row` (differences: follow-ups above)
- [x] Delete `LegacyImporter` and its UI (format choice, asset/class/wallet, buys/sells pickers), strings and ViewModel inputs; Import = pick unified file → preview
- [x] $0-buy prompt kept (format-agnostic in `BuildImportPreviewUseCase`), unified test added; default-wallet setting text no longer mentions legacy
- [x] Stored data checked: nothing records the importer; no migration, backup unchanged
- [x] Unified fixture `buys-sells.csv` keeps the legacy fixture's hand-computed year totals
- [x] Converter + tests (golden output imported by `BuysSellsFixtureTest`, duplicate check), `tools/README.md`
- [x] Docs: import-format-spec, ui-guidelines, README, project-schema, v2-phase1-spec notes, coding-standards file list
- [x] Owner review, then merge into `dev`

## Done: network fees on transfers (M7, `1.7.0`)

Owner decision (2026-11-22, option A): coins spent on a transfer's network fee are a disposal at FMV from the source wallet; nothing is added to basis (IRS FAQ 53, 81, 97). New rule tax-rules R13.

- [x] tax-rules R13 + T13/T14, R5 pointer, project-schema Decisions
- [x] import-format-spec: `fee_asset`, `fee_quantity`, validation, duplicate key
- [x] Domain + calculator (fee disposed before the moved units; pre-2025 fees reported)
- [x] Room migration 1 → 2, JSON backup v2, CSV export/template, round-trip tests
- [x] Reference calculator, its tests, generator, hand-written fixtures, regenerated fixtures
- [x] UI: transaction list/detail (sent, fee, received), fee events labeled in events, Form 8949 CSV, PDF
- [x] `./gradlew build`, managed-device tests, emulator check, `1.7.0`
- [x] Review follow-ups: R3/R4 wording, fee-shortfall warning test, "totals are before fees" in spec/template/import card, fee labels on event detail
- [x] Owner review, then merge into `dev` together

## Phase 1 status

**Phase 1 is released as `2.0.0`** (squashed onto `main`, tag `v2.0.0`). M1–M7 done, plus review fixes; the owner's real-data check passed.

Versioning: see ai-guidelines → Versioning.

## Order

1. [x] Owner reviews `tax-rules.md` worked cases and the spec; local `dev` created from `main`
2. [x] Autonomous run on `feature/v2-phase1` (from `dev`): M1–M6 per the spec, one history entry per milestone, Handoff notes filled in
3. [x] Owner reviews the build and the Handoff notes; UI nitpick rounds as `fix/` branches from `feature/v2-phase1` (not `dev`)
4. [x] Merge into local `dev`, delete the branch
5. [x] Owner imports real files on device and checks the numbers
   - [x] Decide the signing key first (debug keystore or a release keystore): an app signed with another key can't update it, only replace it after an uninstall
   - [x] Convert legacy buys/sells files with `tools/legacy_to_unified.py` in a folder outside the repo; never save real files inside it
   - [x] After the first import: Full backup (JSON), then copy that file off the phone to storage you trust
   - [x] Opening positions for 2025-01-01: each wallet's quantity matches what it really held; no unexpected `TransitionMismatch`
   - [x] Summary totals per year against your own records and any 1099-DA / 1099-B
   - [x] Export the PDF and Form 8949 CSV for a year with a transfer network fee: "(transfer fee)" rows, totals equal Summary
   - [x] TalkBack spot check: Summary, an event detail, Import preview
   - [x] `2.0.0` only after the owner says the check passed
6. [x] Release `2.0.0`: squash `dev` onto `main`, tag `v2.0.0`, then `dev` restarts from `main` (ai-guidelines → Release)

When the Phase 2 spec starts, move the Phase 1 history to `context/history/v2-phase1.md` and keep this file to the active phase.

## History

<!-- Newest first. 3–5 lines each. -->

### 2026-11-24: Release `2.0.0` (v2 Phase 1)
- Owner's real-data check passed (Order step 5). `CHANGELOG.md` added with the 2.0.0 notes; versionCode 124
- `dev` was squashed into one commit on `main`; the old history is kept only in the local tag `v2-dev-history` and an offline bundle, never pushed. `dev` restarts from `main`
- ai-guidelines → Release now describes this flow (tree + squash-diff privacy check, history tag + bundle, push only `main` and the version tag)

### 2026-11-23: Fix: import flash and plurals: `1.8.5`
- Confirm/Cancel cleared the pending preview before the screen closed, so it showed "Nothing to review" for a frame; the ViewModel now keeps the last preview once it's closing (test fails on the old code)
- Plurals: `opening_lot_body` (decimal quantity: "unit" only for exactly 1, `QuantityFormatter.pluralCount`), `preview_duplicates_tile`, `preview_problems_tile`, `matched_lots`. Other counts already used plurals
- Verified: unit and managed-device tests; seen on the emulator in the GIF re-takes that follow

### 2026-11-23: Docs: README layout
- README rewritten in the owner's layout (disclaimer at the top, contents links, limitations as tables, release signing under `<details>`). Wording kept; no fact needed changing against tax-rules, the manifest's backup rules, the toolchain or the signing setup
- Unverified: not viewed on github.com itself (nothing is pushed); anchors and the `<details>` structure were checked against GitHub's rules locally

### 2026-11-23: Fix: manual test round 1: `1.8.4`
- Import preview income error waits for typing; delete dialogs send the first key press to Cancel (`onEnter` on a focus group: buttons can't take focus in touch mode, so focusing on open did nothing); Export empty state keeps Restore; pre-2025 shortfalls name the record's wallet and say lots are pooled (app, PDF)
- Wallet rename follows into an active wallet filter; long chips ellipsize; PDF `+`/true minus and a wrapping banner; Transactions header and empty states live in one list (landscape, search keeps focus); a Mixed row under a term filter shows that term's share; bottom bar labels wrap and hyphenate
- Owner decisions: one row per disposal, filters reset on restart, opening helper Save guard; Settings opens with a not-tax-advice card (About no longer repeats it)
- Unverified: TalkBack speech for the card and "Short-term part", card contrast with wallpaper colors. Verified: the 9 failed manual checks pass on the emulator

### 2026-11-22: Fix: release signing from user-level properties: `1.8.3`
- `app/build.gradle.kts` reads `BLOCKGAINS_RELEASE_STORE_FILE`, `_STORE_PASSWORD`, `_KEY_ALIAS`, `_KEY_PASSWORD` straight from `gradle.gradleUserHomeDir/gradle.properties` (not `providers.gradleProperty`, which would also see the project's `gradle.properties` and `-P`). All four set → release is signed; otherwise unsigned with a build log note
- Release explicitly `isDebuggable = false`, minify still off. Debug builds keep the shared Android Studio debug key
- Gotcha: an app signed with another key can't update the installed one; going from debug to release on a device means uninstalling first

### 2026-11-22: Test: flaky license-back test (no bump)
- `SettingsAboutTest` failed 3 of 6 full managed-device runs: `Espresso.pressBack()` goes to whichever window has focus, which could still be the activity while the license dialog's window was opening. It now uses `onView(isRoot()).inRoot(isDialog()).perform(pressBack())`, which waits for the dialog's window to have focus (no sleeps)
- Not an app bug from `1.8.2`: on the emulator, back pressed 200 ms or more after tapping View license always closed it (30/30). Back within ~50–100 ms of the tap is dropped while the dialog window is created, on the pre-`1.8.2` build too (7/10 stayed open, 3/10 closed Settings instead). No person presses that fast; left as is
- ai-guidelines → Workflow 4: a managed-device run with 0 tests (or fewer without a reason) fails; fail-then-pass isn't a pass until the cause is known
- Verified: test alone 10/10 on the commit before `1.8.2` (it only flaked in the full suite) and 10/10 on the fix; full suite 4/4 in a row (21 tests each)

### 2026-11-22: Fix: import preview exit, Settings → Data deletes: `1.8.2`
- Import preview back/Cancel/Confirm now pop to Import / Export (`closeImportPreview`, a no-op if the preview is already gone) instead of jumping to Summary. Back never wrote anything; it cleared the in-memory preview only. Snackbar reads "N transactions imported"
- Settings → Data: "Delete all transactions" (table only) and "Delete all data" (table + DataStore cleared). Step 1 shows the count, "can't be undone" and Back up first (Full backup JSON); step 2 needs `DELETE` typed. After deleting: pending preview cleared, year/filter reset, snackbar, Summary
- Back on a bottom-nav tab still goes to Summary (platform default), so Transactions' empty-state "Import transactions" → back lands on Summary, not Transactions. Every other screen pops to where it was opened
- Verified: build, 21 managed-device tests, emulator (import → back adds nothing, file kept; confirm → snackbar; both deletes keep/reset settings as specified). Not seen: Cancel's initial focus (touch mode shows no focus); TalkBack itself not run, labels checked in the accessibility tree

### 2026-11-22: Phase 1 final gate (docs only, no bump)
- Privacy: the import spec and template comment named a real exchange's "Subtotal" label; now "the subtotal before fees, not the total including fees". A history line no longer lists real tickers. Earlier commits still contain such names (left for the owner to decide at release)
- Third-coin swap fee deferred to Phase 2 (owner): new Phase 2 item to decide it; the Open question stays undecided with B implemented
- Doc drift: reference README's "Choices not spelled out" now points to R3/R4/R9/R13; coding-standards drops mockk (fakes, per Decisions); import-format-spec sections numbered 1–4
- IRS FAQ citations checked by the owner on the page (updated 2026-06-29): R6 keeps 97/99 for a fee in the swapped coin and adds 98/72 for a fee withheld from the coin received (also in the spec's SWAP row); R13 cites 53, 81, 97 and 62/63, with 102 only for valuing the coins when the service's value can't be determined. coding-standards says Truth only

### 2026-11-22: Import-rule follow-ups from the legacy removal: `1.8.1`
- Bug: `TaxRules.assetsWithHistory` counted a `to_asset` on any pre-2025 row, so a stray one on a BUY could raise a `TransitionMismatch` for that asset (calculator and Opening positions). Now SWAP-only, as R4 says
- The app's strict parsing is now the written rule (import-format-spec section 2) and `refcalc.py` matches it: malformed values in any column, unknown `asset_class`, unrecognized `basis_unknown`, SWAP to its own asset and bad grouping are `InvalidRow`s; `yes`/`y`/`1` are true; U+2212 is a minus. Before, the reference ignored unused columns, accepted any class, read only `true`, and stripped every comma (`1,5` → 15)
- New differential cases: `case-malformed-unused-column`, `-asset-class-values`, `-basis-unknown-values`, `-swap-same-asset`, `-number-grouping`, `-unicode-minus`, `-stray-to-asset`. All 36 existing expected files recompute identically; nothing regenerated
- Also: the converter test compared line endings and failed once git checked the golden file out with CRLF; it now compares text. `.gitattributes` keeps test fixtures LF on every OS, and the fake-data guard also checks `tools/reference_calc/testdata`
- Verified: build, managed-device tests, reference + converter tests; the stray-`to_asset` case fails without the fix. emulator launch only (no UI change)

### 2026-11-22: Remove the legacy two-file import (owner decision): `1.8.0`
- Import is now one unified CSV → preview: no format choice, asset/class/wallet or buys/sells inputs. The $0-buy prompt was already format-agnostic (`BuildImportPreviewUseCase`) and now has a unified test; the default-wallet text only mentions R4. Nothing stored records the importer: no migration, backups unchanged
- Deleted: `data/importer/LegacyImporter.kt`, `test/.../LegacyImporterTest.kt`, the `import_format_*` / `legacy_*` strings. Moved: `fixtures/legacy-buys.csv`, `legacy-sells.csv` → `tools/reference_calc/testdata/`. New: `fixtures/buys-sells.csv` (same rows, same hand-computed year totals), `tools/legacy_to_unified.py` + tests, golden `fixtures/converted-from-legacy.csv`, `tools/README.md`
- Gotcha: rows from the old importer re-imported from the converter are duplicates (blank `fee_usd` = what it stored), except $0 buys: the preview stored them resolved, so they don't match (documented in `tools/README.md`). Step-1 parsing differences are follow-ups above
- Verified: build, managed-device tests, reference + converter tests, emulator fresh install of `buys-sells.csv` (2021/2023/2024 totals match, no legacy UI)

### 2026-11-22: M7 Network fees on transfers (owner decision, option A): `1.7.0`
- New tax-rules R13 + T13/T14: a TRANSFER's `fee_quantity` of `fee_asset` (blank = the moved coin), worth `fee_usd`, is disposed of from the source pool before the moved units, before 2025 too; nothing added to basis; fee shortfall gets the $0-basis slice. `quantity` = what arrived. R5's stopgap note replaced with a pointer
- Before: `fee_usd` > 0 on TRANSFER/INCOME/OPENING was already an `InvalidRow` (never ignored); TRANSFER `quantity` left the source and arrived in full. A $0 `fee_usd` alone stays valid (no fee) so stored rows keep working
- Room v2 (nullable `fee_asset`/`fee_quantity`, migration test), backup v2 (v1 still restores), duplicate key, template, exports (`transfer_fee` column, "(transfer fee)" in 8949/PDF). `InsufficientLots.networkFee` tells a fee shortfall from the moved units' one
- Reference: same rule and validation (it used to drop transfer fees silently), generator writes fees on ~half of transfers and pre-2025 transfers again, 4 hand-written fee fixtures, all fixtures regenerated. Not built: add/edit TRANSFER form (manual entry is Phase 2)
- Verified: build, 15 managed-device tests, emulator upgrade from 1.6.5 kept data; imported fees show correctly. Not seen on device: PDF and 8949 output with fees (unit-tested only)
- Review follow-ups: spec, template comments and import card say `price_usd`/`total_usd` are before fees (fees only in `fee_usd`, R7); event detail shows "Selling fee" ("Subtracted from proceeds", hidden on transfer-fee events), "Includes buy fee" under Basis for lots from a BUY with a fee, and titles fee events "Network fee for transfer on <date>"

### 2026-11-22: Fix: duplicate key uses every column except note: `1.6.10`
- Import duplicates now match the import-format-spec key: every unified column except `note` (adds `asset_class`, `fee_usd`, `to_asset`, `to_quantity`, `to_wallet`, `acquired_date`, `basis_unknown`; price and total compared separately). Still only against stored rows, not within one file
- Before, a same-day TRANSFER to a different wallet or a SWAP into a different asset matching a stored row was skipped by default
- Legacy import asset hint no longer shows a real ticker; a code comment uses `FAKEA`

### 2026-11-22: Fix: scientific notation in CSV numbers: `1.6.9`
- Every numeric CSV field accepts scientific notation (`0E-8`, `1.2E-05`, `1.5e3`), parsed exactly with `BigDecimal`; 1–3 exponent digits, never combined with thousands separators. Other number rules are unchanged
- The import preview shows a non-blocking note with how many imported values used it, since spreadsheet apps may already have rounded them. Counted per row while parsing (`RowReader`), only for rows that will be added
- The reference generator still writes plain decimals (its `0E-8` fix stays)

### 2026-11-22: Fix: R4 history includes swap-received assets (owner-approved): `1.6.8`
- An asset counts as having pre-2025 records if it's a record's asset **or** a SWAP's received asset (`TaxRules.assetsWithHistory`, used by the calculator and the Opening positions helper). Before, an asset only ever received through pre-2025 swaps never got a `TransitionMismatch`. R4 wording updated
- The reference calculator now rejects OPENING rows not dated 2025-01-01 (or acquired after it), like the app. Hand-written fixtures `case-swap-only-history` and `case-opening-wrong-date` cover both former disagreements

### 2026-11-22: Differential tests against a Python reference (no version bump, test-only)
- `tools/reference_calc/`: independent implementation of tax-rules (option-B shortfall with acquired "Unknown", R4 mismatch only with pre-2025 records, R3 same-day order), its own tests, and a seeded generator using only `FAKE` symbols and `FAKE-` wallets
- 30 fixture pairs in `fixtures/differential`; `DifferentialFixturesTest` compares year totals, lot counts by term and issue counts. All 30 match. The fake-data guard now checks fixture subfolders
- Gotcha: the generator wrote a $0 basis as `0E-8`, which the importer rejects; fixed in `gen.py`, not the app
- Not exercised by the fixtures: OPENING rows dated other than 2025-01-01, and R4 history via a swap's `to_asset` (the two implementations differ there; see the tool's README) (fixed in 1.6.8)

### 2026-11-21: Fix: "Unknown" acquired date for shortfall units: `1.6.7`
- The $0-basis slice for units sold without a matching lot now shows "Unknown" as its acquired date in event detail, the taxable-events CSV (`date_acquired`) and Form 8949 column (b). `LotMatch.acquiredDate` is null for it; the calculator still dates it the sale date internally, so it stays short-term
- The PDF has one row per event and no acquired-date column, so it needed no change

### 2026-11-21: Fix: tax rule updates (owner-approved): `1.6.6`
- Partial shortfall switched from A to B: unmatched units of a SELL/SWAP become a slice with $0 basis, `basisUnknown`, short-term, their share of proceeds and fee; acquired date = sale date, source = the sale itself. `InsufficientLots` and export warnings stay; their wording now says gains may be overstated. T4 has a sale price (ST +$150.00)
- R4: `TransitionMismatch` only for assets with records before 2025-01-01. The Opening positions helper's "Remove these records" card follows the same rule
- Gotcha: TRANSFER/ADJUST shortfalls still only report the issue (not disposals, no event)

### 2026-11-21: Merged `feature/v2-phase1` into `dev`: `1.6.5`
- Owner review and UI fix rounds done; both Open questions from the run decided. Merged with `--no-ff`; nothing pushed
- Privacy sweep of the branch found no private items. The generic labels `WALLET-1`, `WALLET-2`, `EXCHANGE-1` are not private (ai-guidelines → Private data)
- Next: owner imports real files on device and checks the numbers; release (`2.0.0`) only when asked

### 2026-11-21: Fix: warmer cream light surfaces: `1.6.5`
- Light background/surface `#FCF2E3` (target `#FDF3E4`, ΔE 0.3): neutral palette at that hue/chroma, all surface roles 2 tones lower than M3 (surface 96), surfaceDim kept at 87. Containers follow, so cards stay distinct. Launch window background matches
- Dark mode, primary, gain/loss unchanged. All 82 checked text/background pairs still pass AA (lowest 4.62)

### 2026-11-21: Fix: cozy coffee theme: `1.6.4`
- New brand palette: full M3 light + dark schemes generated from seed `#C8925A` (TonalSpot, 2021 role tones, primary chroma 28 instead of 36 so the browns match the owner's target roles more closely). Target `background`/`surface` pairs map to M3 `surfaceContainerLow`/`surfaceContainer` (M3 keeps background = surface)
- Gains/losses moved off `primary`/`error` to extended colors (sage / terracotta, with containers). Light gain/loss darkened to tone 40 (`#2F6A3C`, `#A73826`): the given `#3F7A4A` / `#B3412E` fell below 4.5:1 on cards and the hero card. All 82 checked text/background pairs pass AA in both modes
- Wallpaper colors off by default; the Settings switch is now "Use wallpaper colors" and only shows on Android 12+. Launcher icon: `#F5E1C4` background, cozy foreground; monochrome layer unchanged. Launch window background follows the new surfaces
- Checked on emulator: Summary light/dark, 200% font, Settings, launcher icon. Not seen on device: a loss amount (fixture has none; `MoneyTextPreview` shows gain/zero/loss)

### 2026-11-21: Fix: instrumented tests on a Gradle Managed Device
- New policy (owner): instrumented tests run before every merge (`fix/` → feature, feature → `dev`) and at the end of each autonomous milestone, not only at release. Never on a physical device; the everyday emulator only if asked
- Managed device `pixel6Api36` in `app/build.gradle.kts`: Pixel 6, API 36 (= targetSdk), `aosp-atd` image, `testedAbi = "x86_64"` (AGP 10 changes the default to arm64, which the image can't run). Run with `./gradlew pixel6Api36DebugAndroidTest`
- First run: all 8 existing androidTest cases pass (they had only been compiled before). ~2 min including the image download; docs updated in ai-guidelines, coding-standards, README, project-schema. No version bump (test setup only)

### 2026-11-21: Fix: Summary tab did nothing: `1.6.3`
- After Transactions was opened from a Summary card (`restore = false`), tapping Summary did nothing, or brought back another tab. Cause: a non-inclusive `popUpTo(start) { saveState = true }` also files the popped tab's back stack under the start destination's id, and `restoreState` on Summary restored it. Plain tab switching hid it (the first plain hop maps Summary to an empty save)
- `navigateTopLevel` (now in `ui/navigation/TopLevelNavigation.kt`) never restores the start destination; other tabs still restore. The import preview's "pop, then Summary" path uses it too
- `TopLevelNavigationTest` (androidTest, minimal NavHost with the real routes): 3 of its 5 tests fail without the fix. Checked on emulator with the legacy fixture: every tab → every tab, and the card paths

### 2026-11-21: Fix: adaptive app icon: `1.6.2`
- New launcher icon: vector foreground (stepped blocks, from the owner's SVG on a 108dp canvas) on solid `#0E4F4F`, plus a monochrome layer for themed icons (Android 13+). The monochrome keeps the blocks' alpha steps so the climb survives tinting
- Unused density `mipmap-*dpi` webp icons removed (minSdk 26 always uses `mipmap-anydpi`); the source SVGs stay outside the repo
- Checked on emulator (API 37): light, dark, and themed icons in light and dark (Pixel launcher themes home-screen icons only)

### 2026-11-21: Docs: tighter context files for Phase 2
- ai-guidelines: fix branches of an unmerged feature branch merge back into it; commit only after a build that started after the last edit; wider autonomous-run permissions, hard limits and a report checklist; "conservative" defined as never understating tax; one no-attribution rule, also set in `.claude/settings.json` (`attribution`)
- Fake-only data: symbols renamed to `FAKE…` (tax-rules examples and T1–T12: real tickers → FAKEA, FAKEB; the cited source's example keeps its ticker) and wallet labels to `FAKE-…` in tax-rules, import-format-spec, tests, fixtures, previews, README and the import template, plus a fake-data guard test. No rule, quantity, date, price or expected result changed. App default wallet stays `WALLET-1`
- coding-standards: the owner's Android Studio caps the toolchain (2026.2.2, AGP up to 9.5, so the branch's AGP fits); versions live only in `libs.versions.toml`; stored-order rule. project-schema: five Decisions rows, Context Files table points to CLAUDE.md. No version bump

### 2026-11-21: Fix: export shortfall warning: `1.6.1`
- Partial shortfall stays option A (owner decision); exports now warn instead. Export tab: red card with Review → Issues. PDF: red banner on page 1 listing up to 5 shortfalls. CSV contents unchanged. Wording covers sales, swaps, transfers and adjustments (ADJUST shortfalls count too)
- Counts unresolved `InsufficientLots` dated on or before Dec 31 of the selected year for the current filter, so earlier-year (incl. transfer) shortfalls warn too. Asset class comes from the issue's row; an unknown row warns under every filter
- Summary's issues banner became the shared `WarningCard`. Page 1 gives up table rows to the banner
- Checked on emulator: card follows year/filter, Review opens Issues, PDF page-1 banner, 200% font. Not run: the new PDF instrumented test (release-only). No `ExportViewModel` unit test (JVM tests can't build `Paint`); its count is the tested `shortfallsThrough`

### 2026-11-21: M6 Polish + docs: `1.6.0`
- 200% font pass on emulator: single-line nav labels, radio list for import format, `LabeledValue` flows the value under its label instead of squeezing it
- System bar icons follow the app's theme choice (were white on light when the system was dark); zero amounts no longer colored as gains; issue quantities at full precision
- README: features, local storage disclaimer (on-device only, unencrypted, no network, use JSON backup), build steps, tax disclaimer
- Not verified: TalkBack walkthrough, `connectedDebugAndroidTest` (release-only per ai-guidelines)

### 2026-11-21: M5 Exports + backup: `1.5.0`
- Export tab: taxable events (per lot), Form 8949 (Part I/II with totals), income, PDF report (paginated, `PdfDocument`), all transactions (unified), JSON backup + restore with preview/confirm
- Year exports use the shared year and asset-class filter; 8949 part totals come from unrounded values, so they equal the Summary (rows may differ by a cent)
- Gotcha: kotlinx.serialization skips default values, so `format`/`version` needed `encodeDefaults = true`
- Checked on emulator: 8949 totals match Summary, 120-event PDF renders on 3 pages, backup → restore replaces data

### 2026-11-21: M4 Core UI: `1.4.0`
- Bottom-nav shell, Summary (year picker, filter, hero + breakdown cards, issues banner, loss-limit note), Transactions (events/all tabs, filters, event + record detail), Issues, Import with preview and $0-buy resolution, Opening positions helper, Settings
- One `LedgerRepository` runs the calculator on `Default` for all screens; year/filter shared through `SharedSelection`
- Checked on emulator: legacy fixture on a fresh install gives the hand-computed year totals; event detail shows matched lots
- Gotcha: the finished import preview must be popped before switching tabs, or the Import tab restores it

### 2026-11-21: M3 Persistence + import: `1.3.0`
- Room `transactions` table mirroring the unified CSV (decimals/dates as text, schema v1 exported to `app/schemas/`), DataStore settings, SAF `DocumentFiles`, Hilt modules
- RFC 4180 `CsvReader`, strict value parsers, legacy + unified importers sharing one row pipeline; `BuildImportPreviewUseCase` (duplicates, date range, $0-buy resolution)
- Legacy fixture's year totals checked end to end against hand-computed numbers
- import-format-spec: `#` comment lines (template) and strict thousands grouping documented

### 2026-11-21: M2 Domain + calculator: `1.2.0`
- Pure-Kotlin `domain/`: flat `Transaction` mirroring the unified CSV, shared `TransactionValidator`, `TaxCalculator` (R1–R11) with a pluggable `LotMatchingStrategy` (FIFO), `SummarizeYearUseCase` (R11)
- 2025 transition replaces the universal remainder with OPENING records or moves it to the default wallet; mismatches, shortfalls, $0 buys and bad rows come back as issues
- Pro-rata splits give the last lot the remainder, so proceeds/fees/basis sums stay exact
- Two Open questions added to tax-rules (partial shortfall, same-day TRANSFER/SWAP order); both implemented as proposed there, but the shortfall choice isn't the conservative option (see tax-rules)

### 2026-11-21: M1 Foundation: `1.1.0`
- Toolchain on current stable: AGP 9.5.2 (built-in Kotlin), Kotlin 2.4.21 + Compose compiler plugin, KSP 2.3.14, Hilt, Room, Navigation, Serialization, DataStore. v1 sources and example tests removed
- compileSdk 37.2 is required by current AndroidX (targetSdk stays 36); `OldTargetApi` lint disabled for that reason
- Manifest: storage permissions gone, no `INTERNET` (merged-manifest unit test), `allowBackup=false`, DB/files excluded from backup and transfer
- Launches to an empty-state Summary (checked on emulator, API 37)

### 2026-11-21: Docs: context files and v2 plan
- `CLAUDE.md` index + `context/` (ai-guidelines, coding-standards, ui-guidelines, project-schema, tax-rules, specs), same layout as TravelingTavern
- Key decisions: per-wallet FIFO from 2025 with OPENING records, swaps/transfers in Phase 1, asset class as a filter, no network, local-only until release
- v1 bugs found: no asset column, `daysHeld > 365`, sells matching future buys, `split(",")` CSV parsing, fees ignored, crash on insufficient lots
- No code, no bump

### v1: Prototype: `1.0`
- Two CSVs (buys/sells, one asset) → FIFO → year dashboard, in memory. No DI, no persistence, no real tests
