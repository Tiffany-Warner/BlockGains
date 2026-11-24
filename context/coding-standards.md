# Coding Standards

## Toolchain
- **Android Studio is the ceiling.** Every version must open and sync in the owner's installed Android Studio. Before changing AGP or Gradle, check Help > About in the installed Android Studio and compare that version against developer.android.com/build/releases/about-agp. Use the newest stable AGP that Studio supports, never newer. If a library needs a newer AGP or compileSdk than that, keep the older library version and note it in the Handoff notes.
- Within that limit: current stable Kotlin, KSP, Compose BOM, Hilt, Room, Navigation. Check versions when upgrading, don't trust memory. Versions live only in `gradle/libs.versions.toml` (don't repeat them here or in specs).
- Owner's Android Studio: `2026.2.2` (supports AGP up to `9.5`), checked `2026-11-21`. Update this line whenever you check.
- Built-in Kotlin (no `kotlin-android` plugin). JBR is JDK 25; Java/Kotlin target 17 via `compileOptions` (no `jvmToolchain`, there's no JDK 17 installed).
- minSdk 26, targetSdk 36, compileSdk 37.2 (current AndroidX releases require 37.1+; AGP installs the platform on first build).

## Kotlin
- Idiomatic: `val`, data/sealed classes, extension functions, `object` singletons, `when` over if/else chains
- Explicit nullability, no platform types, no `!!` in production code
- Named args when params share a type (very common here: quantity/price/fee are all `BigDecimal`)
- No `@Suppress` without a comment why

## Money and quantities
- **`BigDecimal` only.** Never `Double`/`Float` in models, math, Room, JSON, or parsing (parse strings straight to `BigDecimal`).
- Full precision everywhere. Round (HALF_UP, cents) only in formatting/export, per `tax-rules.md` → Rounding.
- Compare with `compareTo`, never `equals` (scale-sensitive).
- One `MoneyFormatter` / `QuantityFormatter` in `ui/format/`. No ad-hoc formatting in composables.

## Compose
- Compose only, no XML
- One job per composable. Extract reusable UI.
- Hoist state. Keep composables stateless. No business logic (use the ViewModel).
- Use `remember`/`derivedStateOf` to avoid recomputation. Use effects correctly, not for logic.
- Preview every composable, in light + dark, with made-up data that follows ai-guidelines → Private data (FAKE symbols and labels) (multi-lot events, long asset names, negative values)

## Architecture
- MVVM + clean layers: `ui/` (composables, ViewModels), `domain/` (models, use cases, repo interfaces, **calculator**), `data/` (Room, DataStore, repo impls, importers, exporters, mappers)
- **`domain/` is pure Kotlin.** No `android.*` imports, so all tax logic runs in JVM unit tests.
- Calculator: `calculate(transactions, settings): CalcResult` (taxable events, income events, remaining lots, issues). Pure and deterministic. Lot matching behind a strategy interface (FIFO now, others later).
- Lots and taxable events are **derived**, never stored. Recompute on data change, on `Dispatchers.Default`.
- If a result depends on anything outside a transaction's own fields (e.g. insertion order as a same-day tiebreak), `tax-rules.md` must say so (or it's an Open question), the order must be stored, and JSON backup and unified CSV round trips must keep it, with a test.
- One ViewModel per screen. Exposes `StateFlow<UiState>` (sealed/data class), never mutable state. No `Context` in ViewModels.
- Use cases: single responsibility, testable. Only when they earn their place.

## DI
- Hilt only. No service locators or manual DI.
- Scope correctly: `@Singleton`, `@ViewModelScoped`.

## Navigation
- Navigation Compose; routes as sealed class/`object` with `const val` strings (or type-safe routes)
- Pass only primitive IDs. The destination ViewModel fetches data.
- No composable-to-composable navigation
- Shared selection (tax year, asset-class filter) lives in a `@Singleton` holder or the nav-graph-scoped ViewModel, not passed around

## Data
- **Room:** one `transactions` table mirroring the unified CSV (`type` + nullable type-specific columns). `BigDecimal` ↔ `String`, `LocalDate` ↔ ISO `String` TypeConverters. DAOs return `Flow`. `exportSchema = true`, schemas committed. Always migrate, never `fallbackToDestructiveMigration`.
- **DataStore (Preferences)** for settings (default wallet, wallet labels, theme)
- One repository per feature domain. Map to domain models. No entities in UI.
- Files in/out only through the Storage Access Framework (`OpenDocument`/`CreateDocument`) behind an injected interface. No storage permissions.
- CSV: RFC 4180 reader (quoted fields). Never `split(",")`.
- PDF: `android.graphics.pdf.PdfDocument`. JSON backup: Kotlin Serialization.

## Privacy and security
- **No `INTERNET` permission**, ever. A unit test asserts the merged manifest doesn't contain it.
- `allowBackup="false"` and the database excluded in data extraction rules (see project-schema → Decisions)
- Never log quantities, prices, totals or file contents at INFO+
- No wallet addresses or account identifiers in the data model. Wallets are free-text labels.

## Coroutines
- `viewModelScope`; never `GlobalScope`
- `IO` for disk/files, `Default` for the calculator
- `StateFlow` for UI state, `SharedFlow` for one-shot events (snackbar, export done); collect with `collectAsStateWithLifecycle()`

## Files
```
app/src/main/java/com/tiffles/blockgains/
├── ui/
│   ├── [feature]/   [Feature]Screen.kt, [Feature]ViewModel.kt, components/
│   │                (summary/, transactions/, importexport/, openingpositions/, settings/)
│   ├── components/  # shared
│   ├── format/      # MoneyFormatter, QuantityFormatter
│   ├── navigation/Routes.kt
│   └── theme/
├── domain/  model/, calc/, repository/, usecase/
├── data/
│   ├── db/          # Room entities, DAOs, converters
│   ├── settings/    # DataStore
│   ├── importer/    # CsvReader, UnifiedImporter
│   ├── export/      # CSV, Form 8949, income, PDF, backup
│   ├── files/       # SAF wrapper
│   └── repository/
├── di/
└── util/
app/src/test/resources/fixtures/   # made-up CSVs only
```

## Naming
- `PascalCase`: classes, interfaces, composables. `camelCase`: functions, vars. `SCREAMING_SNAKE_CASE`: constants. `snake_case`: resource IDs
- Files match primary class
- Tests for tax-rules worked cases are named after them: `T3_fifoAcrossLotsWithFees`

## Theming
- Material 3 (stable APIs); dynamic color on 12+, brand palette fallback
- All colors/type/shapes via `MaterialTheme`. No hardcoded colors or px (use `dp`/`sp`).
- Light + dark from the start
- Edge-to-edge with correct insets on every screen

## Errors
- Data problems are `CalcIssue`s / import issues, not exceptions (`tax-rules.md` R10)
- `Result<T>` or sealed `Resource<T>` for async (file I/O, import, export)
- Surface via UiState, never crash silently. Friendly messages, log details (without amounts).
- Every data screen handles: loading, empty (→ Import), content, issues

## Testing
- Unit (JUnit, coroutines-test, Truth; hand-written fakes, no mocking library):
    - **Every `tax-rules.md` worked case**, plus edge cases (empty input, 9-decimal quantities, many partial lots)
    - **Calculator invariants** over randomly generated transaction sets (fixed seeds, so failures reproduce): per asset, units acquired = units matched from lots + adjusted away + remaining; basis in = basis used + basis remaining (minus ADJUST-dropped basis). Unmatched shortfall slices (tax-rules R3, option B) aren't counted as matched: each disposal's unmatched units equal its `InsufficientLots` shortfall and have $0 basis, unknown basis, short-term and no acquired date. TRANSFER/ADJUST shortfalls only move or remove units that exist, so they drop out of the count; no matched lot's acquired date is after its disposal date; per-wallet matching from 2025-01-01 never takes a lot from another wallet; random input never throws (problems come back as issues)
    - Plain JUnit loops with `kotlin.random.Random(seed)` are enough. A property-testing library is optional (record it in project-schema → Decisions if added)
    - Importers with fixtures: quoted money, mixed date formats, unsorted rows, $0 buys, bad rows
    - Fake-data guard: a test reads every CSV under `app/src/test/resources/fixtures/` (and the bundled import template, if any) and fails if an asset doesn't start with `FAKE` or a wallet doesn't start with `FAKE-`
    - Round trips: unified CSV export → import, JSON backup → restore
    - ViewModels, use cases, manifest has no `INTERNET`
- Room: migration tests once there's a second schema version. DAO tests few (Robolectric or instrumented).
- Compose UI tests (`androidTest/`, `createComposeRule`) for screens with behavior worth guarding:
    - Test stateless `…Content` with fixed `UiState` + fake callbacks (no Hilt/ViewModel)
    - Cover each state (loading/empty/content/issues), taps → right callback
    - Find by text/content description; `testTag` only if neither works
    - Run on the Gradle Managed Device `pixel6Api36` (ATD image, API 36 = targetSdk, defined in `app/build.gradle.kts`) before every merge; when and where: ai-guidelines → Workflow step 4. Unit tests run on every `./gradlew build`.

## Quality
- No unused imports/vars; no unexplained commented-out code
- Functions ≲40 lines; extract well-named helpers freely
- Self-documenting; KDoc for public APIs and non-obvious logic. Reference tax rule IDs where applied (`// R3`).
- `./gradlew build` (tests + lint) clean before commit
