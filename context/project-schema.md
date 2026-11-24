# BlockGains: Project Schema

> Your crypto (and later stock) capital gains, calculated on your phone from your own records. Offline, private, FIFO, wallet-aware.

---

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM + Clean Architecture (pure-Kotlin domain)
- **DI**: Hilt
- **Local DB**: Room (transactions)
- **Settings**: DataStore (Preferences)
- **Navigation**: Jetpack Navigation Compose
- **Serialization**: Kotlin Serialization (JSON backup)
- **Files**: Storage Access Framework; PDF via `PdfDocument`

## How It Fits Together

- **Phone only.** No server, no network, no price APIs, no account linking. The app never has `INTERNET`.
- **In:** the unified CSV (`specs/import-format-spec.md`); old buys/sells files go through `tools/legacy_to_unified.py`
- **Middle:** transactions stored in Room → pure calculator → taxable events, income, remaining lots, issues (`tax-rules.md`)
- **Out:** Summary + Transactions screens, CSV/Form 8949/PDF exports, JSON backup

## Owner's Situation

Private. Kept in `context/local/owner-notes.md` (gitignored, local only). The design above and in the specs is written to be generic; the notes only explain *why* certain features are in Phase 1.

## Built Toward Later Phases

Every phase keeps to these so later phases add to the app instead of undoing it:

- **Lot matching is a strategy** (FIFO now; HIFO/specific ID later)
- **Asset class exists from day one** (Crypto/Stock) even though stocks come in Phase 3
- **The unified CSV is the model.** Room mirrors it, the "All transactions" export is it, and new importers (exchange exports, 1099-B) map into it
- **Derived, not stored.** Lots and events are always recomputed, so rule fixes apply to all history instantly
- **Every tax rule has a source and a test** (`tax-rules.md`)

---

## Phase 1: v2 Core (spec: `specs/v2-phase1-spec.md`)

> Goal: Turn the v1 prototype into a persistent, trustworthy app for real records: multi-asset, multi-wallet, 2025 per-wallet rules, swaps, transfers, income, exports, backup.

- [x] M1 Foundation: toolchain upgrade, Hilt/Room/Navigation, manifest cleanup
- [x] M2 Domain + calculator: all transaction types, tax-rules R1–R11, worked cases T1–T12
- [x] M3 Persistence + import: Room, RFC 4180 reader, unified importer with preview (the legacy buys/sells importer was removed in `1.8.0`)
- [x] M4 Core UI: Summary, Transactions, Import, Opening positions, Settings
- [x] M5 Exports + backup: tax-year CSV, all CSV, Form 8949 CSV, income CSV, PDF, JSON backup/restore
- [x] M6 Polish + docs: accessibility, empty/error states, README storage disclaimer
- [x] M7 Network fees on transfers: tax-rules R13, `fee_asset` / `fee_quantity` columns (`1.7.0`)

## Phase 2: Manual Entry & Exchange Import

- [ ] Add / edit / delete individual transactions (on-chain trades, fixes)
- [ ] Exchange transaction-history CSV importer(s)
- [ ] Optional time column in the unified format (and exchange importers); same-day order falls back to input order when blank
- [ ] Decide: swap fee paid in a third coin (tax-rules Open questions)
- [ ] Issues screen: one-tap fixes (e.g. create ADJUST for dust)
- [ ] Undo a single import (needs an import id per row; Room migration)
- [ ] Form 8949 CSV: box column (G/H/I short-term, J/K/L long-term; C/F aren't allowed for digital assets) and column (a) = units + symbol. **Open question, not decided:** (A) a per-wallet "issues 1099-DA" setting picks the box (2025 broker sales H/K, self-custody I/L, 2026 covered units G/J); (B) leave the box blank for the preparer and only add units + symbol to (a); (C) a single box chosen at export time. Column (d) shows net proceeds today, which may not tie to the 1099-DA (Form 8949 instructions want the 1099-DA proceeds in (d) and costs as an adjustment with code E)

## Phase 3: Stocks & Methods

- [ ] Stocks: wash-sale rule (needs tax-rules R12 + tests first), 1099-B import for reconciliation
- [ ] Other lot methods (HIFO, specific ID), including standing orders recorded in the owner's books (Notice 2025-07, extended through 2026 by Notice 2026-20; tax-rules R3)
- [ ] Capital-loss carryover tracking across years
- [ ] Estimated tax owed (filing status, income, brackets: its own tax-rules section)

---

## Out of Scope

- Network access of any kind (price lookups, sync, account linking)
- State taxes, non-US rules
- Multiple users / devices
- NFTs / collectibles (28% rate, Notice 2023-27), gifts received (carryover basis; tax-rules Open questions), theft/casualty losses

---

## Decisions

| Date | Decision | Why |
|---|---|---|
| 2026-11-21 | FIFO only, behind a strategy interface | Owner's chosen method |
| 2026-11-21 | Universal FIFO before 2025-01-01, per-wallet after; OPENING records bridge them | Rev. Proc. 2024-28 / Reg. 1.1012-1(j). See tax-rules R3–R4 |
| 2026-11-21 | Transfers + swaps in Phase 1 (via unified CSV); manual entry UI in Phase 2 | Moving coins between own wallets and swapping before selling are common in 2025+ activity |
| 2026-11-21 | Asset class is a filter (default All), not a bottom-nav tab | Crypto and stocks net together on Schedule D |
| 2026-11-21 | No price API; user supplies USD values | Offline/private |
| 2026-11-21 | ~~Network fees not modeled; ADJUST handles dust~~ **Superseded by tax-rules R13 (`1.7.0`)** | Never tracked, trivial amounts |
| 2026-11-21 | `allowBackup=false`; JSON export is the backup path | Keep financial records off automatic cloud sync. **Owner may reverse** |
| 2026-11-21 | Synthetic fixtures only | Repo will be public again |
| 2026-11-21 | All work local until release | Owner's call: nothing pushed until close to release |
| 2026-11-21 | Tax-year exports follow the shared year and asset-class filter | So Form 8949/PDF totals always equal what Summary shows; default All is the real number |
| 2026-11-21 | Taxable-events CSV has one row per matched lot | A disposal can mix short- and long-term lots; per-lot rows keep the term and basis exact |
| 2026-11-21 | Own RFC 4180 reader; Truth for assertions; fakes instead of mockk | The reader is ~80 lines with full tests and no dependency; fakes keep ViewModel tests readable. No new runtime libraries |
| 2026-11-21 | compileSdk 37.2, targetSdk stays 36 | Latest stable Compose/core/lifecycle/navigation need compileSdk 37.1+. Raising targetSdk is a separate, deliberate change |
| 2026-11-21 | Test, preview and example data use only `FAKE` symbols and `FAKE-` wallet labels | Repo goes public; real tickers can hint at what the owner holds |
| 2026-11-21 | "Conservative" means never understating tax (ai-guidelines → Tax rules) | Phase 1 called an option that leaves gain out "conservative" |
| 2026-11-21 | Toolchain is capped by the owner's installed Android Studio | Phase 1's AGP didn't open in the owner's Studio |
| 2026-11-21 | Autonomous runs may merge their own fix branches back with `--no-ff` and correct docs | Fewer stops for routine follow-ups |
| 2026-11-21 | No AI attribution in commits, enforced in `.claude/settings.json` | Owner is the only author |
| 2026-11-21 | Instrumented tests run on a Gradle Managed Device (ATD, API 36) before every merge, not only at release | Catch UI regressions early without touching the owner's phone or everyday emulator |
| 2026-11-21 | No Material 3 Expressive for now | Not yet stable enough on Android. Revisit later |
| 2026-11-21 | Partial shortfall: unmatched units count as $0 basis, short-term (option B, was A) | Full proceeds are reportable, unsubstantiated basis is $0, unknown holding period is short-term. See tax-rules R3 |
| 2026-11-21 | `TransitionMismatch` only for assets with records before 2025 | Opening positions with no earlier history have nothing to disagree with. See tax-rules R4 |
| 2026-11-22 | Differential tests: the calculator is compared with an independent Python reference (`tools/reference_calc/`) on 30 seeded, synthetic fixtures | Two separately written implementations agreeing on many random datasets makes a shared mistake unlikely; T1–T12 cover few combinations. Fixtures are regenerated with `gen.py`, never hand-edited |
| 2026-11-22 | CSV numbers accept scientific notation, parsed exactly; the import preview notes how many | Spreadsheet apps write small and large numbers that way; they may already be rounded. See import-format-spec |
| 2026-11-22 | R4 "records before 2025" includes an asset received in a pre-2025 SWAP | An asset only ever swapped into before 2025 still has history to compare. Owner-approved |
| 2026-11-22 | `git add -A` is fine after checking `git status`; stop on any file Claude Code didn't create or change | A stray untracked folder was once swept into a commit |
| 2026-11-22 | After an approved `--no-ff` merge, delete the merged branch with `git branch -d` without asking | `-d` refuses unmerged branches, so it can't lose work |
| 2026-11-22 | Autonomous runs keep their unmerged feature branch | It isn't merged until the owner reviews |
| 2026-11-22 | Managed-device tests before every merge into `dev` or a feature branch, including `fix/` → `dev` | `fix/` → `dev` merges weren't covered by the old wording |
| 2026-11-22 | `2.0.0` when the owner's real-data check passes (even unpushed); Phase 2 milestones `2.1.0`, `2.2.0`…, fixes bump the patch | Owner's call. See ai-guidelines → Versioning |
| 2026-11-22 | License: GPL-3.0-or-later | Forks that handle people's financial records must stay open; owner is sole author and can relicense |
| 2026-11-22 | All history rewritten to the GitHub noreply email (owner-approved, `main` force-pushed); repo-local `user.email` uses it, and Release checks `origin/main..dev` for any other email | GitHub's email privacy setting refuses pushes that expose the private email |
| 2026-11-22 | Network fees on transfers (option A, owner's choice): fee coins are disposed at FMV from the source wallet, nothing added to basis; TRANSFER gains `fee_asset` / `fee_quantity`, `quantity` = what arrived | IRS FAQ 53, 81, 97. Options B (also add the fee to basis) and C (remove like ADJUST, no gain) contradict FAQ 53/81. See tax-rules R13 |
| 2026-11-22 | Legacy two-file (buys/sells) import removed in `1.8.0` (owner decision); the unified CSV is the only import format. Old files: `tools/legacy_to_unified.py` | The unified format covers everything legacy did, plus wallets, fees and every type; two importers meant two code paths to test and document |
| 2026-11-22 | Settings → Data: "Delete all transactions" (table only) and "Delete all data" (table + settings reset), each behind a count-and-backup dialog, then typing `DELETE`. The import preview always returns to Import / Export (`1.8.2`) | A clean slate before importing real data without uninstalling; two steps and a backup offer because nothing can be undone. Undoing one import needs per-row import ids (Phase 2) |
| 2026-11-22 | Release builds are signed with the owner's own key, configured only in the user-level `~/.gradle/gradle.properties`; the keystore lives outside the repo (`1.8.3`) | Debug builds are debuggable (`run-as` reads the database over USB) and share Android Studio's debug key; the real data should live in a release build. Secrets never enter the public repo |
| 2026-11-22 | A managed-device run with 0 tests, or fewer than the last without a reason, is a failure; a fail-then-pass isn't a pass until its cause is known (ai-guidelines → Workflow) | A run that never reached the device reported BUILD SUCCESSFUL with 0 tests, and a flaky test was once merged on a passing rerun |
| 2026-11-23 | From the first manual-test pass (`1.8.4`): taxable events stay one row per disposal (a Mixed chip, lots in the detail); filters aren't saved across restarts; the opening-positions helper keeps Save off until every unit is assigned | Owner's choices; the manual tests had expected lot rows, saved filters and a helper-made mismatch. Under a term filter a Mixed row shows only that term's share, so rows add up to the Summary |
| 2026-11-22 | The app's strict CSV parsing is the written rule (import-format-spec section 2); the reference calculator matches it. Only a SWAP's `to_asset` counts as R4 history, as tax-rules R4 already says | The reference was more lenient on malformed or unused columns and the app counted a stray `to_asset` on any type; found while removing the legacy import. Owner's choice |

---

## Context Files

See the table in CLAUDE.md.
