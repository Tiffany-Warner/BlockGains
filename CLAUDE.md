# BlockGains

Personal-use, fully offline Android app that calculates US capital gains and losses (crypto first, stocks later) from the owner's own transaction records.

Read the context files before working. Each fact lives in exactly one file: link to it, never copy it. If two files disagree, fix the one that isn't the source of truth.

| File | Purpose |
|---|---|
| `context/ai-guidelines.md` | How Claude Code should behave: workflow, branches (**local only, no pushing**), commits, private data |
| `context/current-feature.md` | Active feature, its checklist, and history |
| `context/coding-standards.md` | Kotlin/Compose/architecture/data rules |
| `context/ui-guidelines.md` | Visual style, navigation, screens |
| `context/project-schema.md` | Phases, roadmap, decisions |
| `context/tax-rules.md` | **Source of truth for all tax math**, with sources and worked test cases |
| `context/specs/` | Feature specs, one per feature |
| `tools/reference_calc/README.md` | Independent reference calculator for differential tests; must follow `tax-rules.md` |
| `context/local/` | **Private, gitignored, may not exist.** Owner notes for background only. Never copy anything from it into committed files, commits or history entries |

Read only what the task needs: `tax-rules.md` for calculator work, `ui-guidelines.md` for UI, and so on.
