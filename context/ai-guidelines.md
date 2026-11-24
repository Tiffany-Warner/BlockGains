# AI Guidelines

## Style
- Concise. Briefly explain non-obvious decisions.
- Ask before large refactors/architecture changes.
- Minimal changes. No unspecced or nice-to-have features, no unrelated refactors. Follow existing patterns.
- Never delete files without asking.

## Local only (until release)
**Nothing leaves this machine.** No `git push`, no PRs, no remote branches, no GitHub Actions. `origin` is public right now (v1 on `main`) but is untouched until I say we're releasing.

## Workflow (every feature/fix)
1. Document in @context/current-feature.md
2. Branch from local `dev`: `feature/x`, `fix/x`, etc. Exception: a fix to a feature branch that isn't merged into `dev` yet branches from that feature branch and merges back into it.
3. Implement
4. Test. Manual checks happen on the emulator only. Never install to, uninstall from, clear data on, or run adb commands against a physical device unless I ask in that prompt: my phone holds my real data. With more than one device attached, set `ANDROID_SERIAL` to the emulator. `./gradlew build` (incl. unit tests + lint) must pass before every commit/merge. Instrumented tests run on the Gradle Managed Device (`./gradlew pixel6Api36DebugAndroidTest`, a Gradle-created ATD emulator) and must pass before every merge into `dev` or a feature branch, and in autonomous runs at the end of each milestone. Never run `connectedAndroidTest` on a physical device, and on my everyday emulator only if I ask. Never filter test output; save it in full.
   - A managed-device run that executes 0 tests, or fewer than the last run without a known reason (tests added or removed, a filter you passed), **counts as failed** even if Gradle says BUILD SUCCESSFUL. Check the test count in the results, not just the exit code. Gradle can also mark the task UP-TO-DATE and reuse an empty result; use `--rerun`.
   - A test that fails and then passes on a rerun **isn't a pass** until the cause is known. Report it as flaky, find the cause, and fix it (or ask) before relying on that suite.
5. Iterate
6. Commit: ask first (unless pre-approved, see Autonomous runs). Only after a `./gradlew build` that passed and started after your last edit. Never commit first and amend later to fix the build. Staging: `git add -A` is fine after checking `git status`; if it shows any file you didn't create or change, stop and ask.
7. Merge with `--no-ff`. Don't push. `fix/` → `dev` and feature → `dev` need my OK (or a pre-approval in my prompt). `fix/` → its unmerged feature branch is pre-approved once `./gradlew build` and the managed-device tests pass.
8. After an approved `--no-ff` merge, delete the merged branch with `git branch -d` without asking (`-d` refuses unmerged branches). Still ask before deleting anything unmerged.
9. Mark complete in current-feature.md, add to history
10. History entries: 3–5 lines max. What changed, cause/key decisions, gotchas, what's unverified, version. At most one short "Verified:" clause per entry; no other verification logs or test counts.

## Versioning
- Each milestone bumps the minor version (Phase 1: M1 = `1.1.0` … M6 = `1.6.0`). Each fix bumps the patch version. Docs-only and test-only changes don't bump.
- When my real-data check of Phase 1 passes, bump to `2.0.0` (even if nothing is pushed yet).
- Phase 2 milestones are `2.1.0`, `2.2.0`, …; fixes bump the patch.

## Autonomous runs
A run is autonomous when a goal prompt in `context/specs/` or my prompt says so. For that run only, these are **pre-approved**:
- Commits on the run's feature branch (one or more per milestone, build passing)
- Refactors and replacing/deleting v1 files that the spec explicitly replaces (list every deleted file in the spec's Handoff notes)
- Fix branches: `fix/x` from the run's feature branch, merged back into it with `--no-ff`, then deleted with `git branch -d` (never `-D`)
- Doc corrections: making context files match what was actually built or decided (wrong versions, stale status, wrong paths, two files disagreeing). Each correction gets a row in `project-schema.md` → Decisions and a line in the history entry
- Adding entries to `tax-rules.md` → Open questions (see Tax rules)

Still **never** during an autonomous run: push or touch `origin`; merge into `dev`; delete `dev`, `main` or the run's feature branch (it isn't merged until I review; step 8 applies only after an approved merge); rewrite history (rebase, `reset --hard`, or amending anything but the commit you just made) unless I asked; change a tax rule (R-sections, worked cases or their expected results) or decide an Open question; change the rules in this file or `CLAUDE.md`; delete anything the spec doesn't replace.

When the run ends, stop and report, starting with: (1) anything done against these guidelines, (2) Open questions added, saying which option is conservative and which one you implemented, (3) toolchain changes and the Android Studio check, (4) what wasn't verified. I review, then we do steps 7–9 together.

## Branches
- `dev`: local working branch. Create it from `main` if it doesn't exist. `dev` is reset to `main` only during a release, after the history tag and bundle exist (Release step 6).
- `main`: last published state. Never commit to it directly. Only updated at release.

## Release (when asked)
`main` gets one squashed commit per release; `dev`'s individual commits never go public.
1. Release branch `release/X.Y.Z` from `dev`: bump the version, update `CHANGELOG.md`, tick the release in current-feature.md. Merge it into `dev` with `--no-ff`.
2. **Checks** on the release branch: `./gradlew build` and `./gradlew pixel6Api36DebugAndroidTest` pass (managed device, see Workflow step 4), the `tools/` Python tests pass. Manual checks on the emulator, not my daily phone. Multiple devices: `ANDROID_SERIAL=<serial>`.
3. **Author email check:** every commit uses my GitHub noreply address (`25518506+Tiffany-Warner@users.noreply.github.com`; GitHub refuses pushes that expose my private email), and this repo's `user.email` is set to it. If any commit has another email (except `noreply@github.com` for GitHub web commits), stop and tell me.
4. **Squash:** create the local tag `vN-dev-history` on `dev` and back it up with `git bundle create <Desktop>/blockgains-vN-dev-history.bundle vN-dev-history` plus `git bundle verify`. Never push that tag. Then on `main`: `git merge --squash dev` and one commit, `BlockGains X.Y.Z (...)`. `git log origin/main..main` must show exactly that commit. Tag it `vX.Y.Z` (annotated, local).
5. **Privacy check** (see Private data) of the tree and of the exact diff that goes public: `git show --stat <squash commit>` and a full grep over `git show <squash commit>` for real exchange names, wallet addresses, emails and real tickers outside `docs/media/`. Anything found → stop and tell me before pushing.
6. `git branch -f dev main` (while on `main`), so the next phase starts from the released commit. Without this, a later `dev` → `main` merge would publish the old commits.
7. Push only `main` and the version tag, **only after I confirm**: `git push origin main` and `git push origin vX.Y.Z`. Never `--tags` or `--all`, never `dev`.

## Private data (public repo)
`origin` is public right now, and everything committed will be pushed there at release. Nothing from my real finances goes in code, tests, fixtures, specs, history, commits or screenshots:
- No real transaction files, quantities, prices, dates or totals from my records
- No wallet addresses, exchange account IDs/emails, API keys, or anything that identifies my accounts
- Test, fixture, preview, screenshot and doc-example data uses only obviously fake names: asset symbols start with `FAKE` (`FAKEA`, `FAKEB`, `FAKEUSD` for a stablecoin) and wallet labels start with `FAKE-` (`FAKE-EXCHANGE-1`, `FAKE-WALLET-1`). No real tickers, not even common ones, and no real exchange or wallet-app names. Fixtures use made-up numbers
- WALLET-1, WALLET-2 and EXCHANGE-1 (the app's default label and older examples) are generic and not private, even if I also use them. Privacy sweeps don't flag them.
- Real tickers may appear only inside a quoted external source (the cited example in tax-rules R6). Privacy sweeps don't flag those.
- Exception: files under docs/media/ (README GIFs and screenshots and the CSV used to make them) may use common real tickers such as BTC, ETH and SOL. All amounts, prices and dates there are made up. Wallets use generic labels (WALLET-1, EXCHANGE-1). Never use real exchange names, wallet addresses or account numbers. Real tickers stay banned everywhere else in the repo.
- Nothing that resembles my real activity either: no dates, sequences of moves, holdings or swaps taken from `context/local/`. When in doubt, change the date, amount and symbol
- Before every commit, check the staged diff for any asset symbol or wallet label without the `FAKE` prefix (the fixture test in coding-standards → Testing covers CSVs; check Kotlin previews, test code and docs yourself)
- No details of my situation either: which exchanges/wallets I use, when I moved coins, what I hold. That background lives only in `context/local/` (gitignored). Read it for judgment calls, but committed docs, code and history stay generic
- Check the diff before every commit. Already committed (even locally)? Stop and tell me, because history must be rewritten before any push

## Tax rules
- `context/tax-rules.md` is the only source of tax behavior. Never guess or change a rule.
- Any change to `tax-rules.md` (a rule, a worked case, a decided Open question) also updates `tools/reference_calc/refcalc.py` and its tests, and regenerates the differential fixtures (`tools/reference_calc/README.md`), on the same branch.
- Not covered? Add an entry under its **Open questions** with at least two options and what each does to the affected worked case. Implement the conservative option, flag it in the history entry, and keep going (or ask if I'm around).
- **Conservative** = the option that never reports less tax than the correct answer could be: gain the same or higher, loss the same or smaller, income included rather than left out, and when the term is unclear, short-term for a gain and long-term for a loss. An option that leaves any proceeds, gain or income out of the totals, even "until the data is fixed", is not conservative. Label the conservative option in the entry. If I choose another one, the entry records that it was my choice (like the partial-shortfall entry).

## Commits
Conventional (`feat:`, `fix:`, `chore:`, `docs:`, `test:`). One feature/fix each. I'm the only author: no `Co-Authored-By`, `Generated with`, session links or any other AI attribution in commit messages, trailers or code comments. Check the message before every commit.

## Stuck
After 2–3 failed attempts, stop and explain. No random fixes. Ask if unclear.

## Code review (periodic + on demand)
- Correctness: every tax-rules case has a passing test; no `Double` near money; issues instead of crashes on bad data
- Privacy: no `INTERNET` permission, no financial values in logs at INFO+, nothing real in fixtures
- Data safety: Room migrations (never destructive), backup/restore round-trips
- Performance: recalculation off the main thread, no needless recompositions
- Matches existing patterns
