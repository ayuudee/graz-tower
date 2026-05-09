# Rewrite legacy `/home/andrew/dev/projects/twr2/` path refs across docs + code

## Overview

Repo lives at `/Users/andrew/dev/projects/graz-tower`; the prospect counted **28** files referencing the legacy absolute `/home/andrew/dev/projects/twr2/` path, but the actual sweep is **~78 files** with hundreds of occurrences. The single biggest hot spot is `research/fm/README.md` (158 hits) — every cross-reference inside the FM proof program is currently a dead cross-machine link.

Two distinct rewrite shapes:

1. **75 doc / JSON files** — markdown inline links (`[text](/home/andrew/...)`) and JSON `sourceRef` strings. Strip-the-prefix is canonical and safe; result is a repo-relative path that resolves naturally in markdown viewers and remains semantically equivalent in audit-grade `sourceRef` strings.
2. **3 code files (5 occurrences)** — `RUNBOOK.md` bash block (`ROOT=/home/andrew/...`) and Python `sys.path.insert(0, "/home/andrew/...")` in `build_icao4444_seeded_promotions.py` (×2) and `test_quality_gates.py` (×2). Strip-the-prefix **breaks these** unless we also fix the cwd assumption — they need `Path(__file__).parent` (Python) or `git rev-parse --show-toplevel` (bash).

## Boundaries / non-goals

- Bare `twr2` project-name token (`settings.gradle.kts:1` `rootProject.name`, `flake.nix:2` description, `bin/export_osm_geojson_dxf.py:395` User-Agent, prose mentions in `research/fm/lean/README.md:3`) — project rename is a separate decision.
- `/home/andrew/.claude/plans/...` references in 7 source files (pilot/sim/controller test files + `.flow/specs/fn-5...md`) — point at private notes outside the repo; separate decision.
- `~/.claude/projects/-home-andrew-dev-projects-twr2/...` (3 hits in `.flow/tasks/fn-6-kinematic-position-on.3.md`) — Claude Code session-scoped store paths; rewriting them touches a different sweep.
- `/tmp/twr2-...` (3 hits in `research/tools/requirements-spike/quality/...` outputs) — captured tool-run artifacts recording former tmp registry; not rewritten.
- Historical tool-output JSON under `research/tools/requirements-spike/quality/curation/**/*.error.json` and `quality/adequacy/**/sample_manifest.json` — audit artifacts of past runs; rewriting changes the audit trail. **Left untouched.**

## Strategy Alignment

Active tracks served:
- **Reviewer / agent infrastructure** — 78 dead cross-machine links are exactly the kind of first-time-user friction the project wants to remove; every new agent (human or AI) reading `research/fm` currently lands on broken refs.
- **FM / Lean proof program** — the highest-impact files (`research/fm/README.md`: 158 hits, `research/fm/lean/README.md`: 93, `research/fm/PROJECT_STATUS.md`: 56) are the FM project's onboarding surface. Fixing them lifts the proof program's readability without touching theorems.

## Decision context

Strip-prefix is the right canonical form for markdown links and JSON `sourceRef` strings — both resolve correctly as repo-relative paths and survive future repo-rename / cross-machine moves. For the 3 code files we use cwd-independent forms so the scripts work from any invocation directory. We deliberately skip historical audit JSONs because they record what past runs produced — rewriting them retroactively edits an audit trail.

## Acceptance

- **R1:** `grep -rln "/home/andrew/dev/projects/twr2" research/ docs/ wiki/ AGENTS.md README.md cad/ .flow/ *.md 2>/dev/null` returns empty across the in-scope tree (excluding `.git/` and the historical-audit JSONs called out in non-goals).
- **R2:** The 3 code files (`research/tools/requirements-spike/RUNBOOK.md` bash block, `build_icao4444_seeded_promotions.py` ×2 `sys.path.insert`, `test_quality_gates.py` ×2 `sys.path.insert`) resolve paths from any cwd — verified by running the relevant Python import / bash expansion from a non-repo-root cwd.
- **R3:** No semantic regressions in markdown rendering — spot-check 5 markdown links across the highest-hit files (`research/fm/README.md`, `research/fm/lean/README.md`, `research/fm/PROJECT_STATUS.md`) render as valid repo-relative links; `git diff --stat` shows only path-string deletions, no other changes.
- **R4:** Historical audit JSONs (`research/tools/requirements-spike/quality/curation/**/*.error.json`, `quality/adequacy/**/sample_manifest.json`) explicitly **not** touched; the exclusion is documented in this epic spec so future readers don't re-litigate.
- **R5:** Epic title + the source prospect's `## Survivors > #2` description updated to reflect actual scope ("~78 files", not "28") so downstream readers see the corrected count.

## Early proof point

Task `fn-8-rewrite-28-absolute.1` strips the prefix across `research/fm/README.md` (158 occurrences — the largest single file) and spot-checks the result. If the strip produces broken markdown anywhere — e.g., a link that relied on absolute disambiguation — re-evaluate the canonical form before sweeping the remaining 74 files. The 158-hit file is the hardest case; if it works there, the rest follow.

## Requirement coverage

| Req | Description | Task(s) | Gap justification |
|-----|-------------|---------|-------------------|
| R1  | Zero hits in in-scope tree post-sweep | fn-8-rewrite-28-absolute.1, fn-8-rewrite-28-absolute.2 | — |
| R2  | 3 code files cwd-independent | fn-8-rewrite-28-absolute.2 | — |
| R3  | Markdown spot-check + diff-stat sanity | fn-8-rewrite-28-absolute.1 | — |
| R4  | Historical audit JSONs left untouched | fn-8-rewrite-28-absolute.1 | — |
| R5  | Epic title + prospect description updated | fn-8-rewrite-28-absolute.1 | — |

## Quick commands

```bash
# Pre-sweep: list in-scope occurrences (excludes audit JSONs + .git)
grep -rln "/home/andrew/dev/projects/twr2" research/ docs/ wiki/ AGENTS.md README.md cad/ .flow/ *.md 2>/dev/null \
  | grep -v "^research/tools/requirements-spike/quality/curation/" \
  | grep -v "^research/tools/requirements-spike/quality/adequacy/.*sample_manifest"

# Post-sweep: should return empty
grep -rl "/home/andrew/dev/projects/twr2" . 2>/dev/null \
  | grep -v "^\./\.git/" \
  | grep -v "^\./research/tools/requirements-spike/quality/"

# Smoke: ensure Python script imports work from a non-repo cwd
( cd /tmp && python3 -c "import sys; sys.path.insert(0, '/Users/andrew/dev/projects/graz-tower/research/tools/requirements-spike'); import build_icao4444_seeded_promotions" 2>&1 | head -5 )
```

## References

- Source prospect: `.flow/prospects/lean-fm-next-steps-2026-05-08.md#idea-2`
- Repo-scout findings (2026-05-08): authoritative file list, occurrence counts, and code-vs-doc shape split
- Highest-impact files: `research/fm/README.md` (158 hits), `research/fm/lean/README.md` (93), `research/fm/runtime_model_change_impact.md` (61), `research/fm/PROJECT_STATUS.md` (56)
