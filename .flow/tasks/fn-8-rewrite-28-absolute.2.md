---
satisfies: [R1, R2]
---

## Description

The 3 code files referencing `/home/andrew/dev/projects/twr2/` use the absolute path **load-bearingly** — Python `sys.path.insert(0, "/home/andrew/...")` and bash `ROOT=/home/andrew/...`. Strip-the-prefix breaks them unless we also remove the cwd-dependence. Rewrite each to use a cwd-independent form so the script works from any invocation directory.

**Size:** S (5 lines across 3 files; bespoke per-file)

**Files:**
- `research/tools/requirements-spike/RUNBOOK.md` — bash block at line 90 (`ROOT=/home/andrew/dev/projects/twr2`)
- `research/tools/requirements-spike/build_icao4444_seeded_promotions.py` — `sys.path.insert(0, "/home/andrew/...")` at lines 1413, 1603
- `research/tools/requirements-spike/test_quality_gates.py` — `sys.path.insert(...)` at 2 lines (mirror of build script; locate via `grep -n "/home/andrew" <file>`)

## Approach

- **Python (both files):** replace `sys.path.insert(0, "research/tools/requirements-spike")` with `sys.path.insert(0, str(Path(__file__).resolve().parent))` (assumes the script lives at the path it inserts; if it inserts a *different* directory, use `Path(__file__).resolve().parent / "<rel>"` instead — verify per occurrence). Add `from pathlib import Path` if not already imported.
- **Bash (RUNBOOK.md):** replace `ROOT=/home/andrew/dev/projects/twr2` with `ROOT="$(git rev-parse --show-toplevel)"`. The runbook is read by humans following commands manually, so this keeps the same `${ROOT}/...` substitution pattern downstream in the same fenced block.
- **Verify** by smoke-running each script from a non-repo cwd:
  - `cd /tmp && python3 /Users/andrew/dev/projects/graz-tower/research/tools/requirements-spike/build_icao4444_seeded_promotions.py --help` (or the equivalent invocation that exercises the import).
  - `cd /tmp && python3 /Users/andrew/dev/projects/graz-tower/research/tools/requirements-spike/test_quality_gates.py --help` (or similar).
  - `cd /tmp && bash -c 'ROOT="$(git -C /Users/andrew/dev/projects/graz-tower rev-parse --show-toplevel)"; echo "$ROOT"'` — confirm the bash form resolves correctly.

## Investigation targets

**Required**:
- `research/tools/requirements-spike/build_icao4444_seeded_promotions.py:1413,1603` — confirm both lines insert the same path or distinct subpaths (the rewrite differs).
- `research/tools/requirements-spike/test_quality_gates.py` — `grep -n "/home/andrew" <file>` to find the exact lines and check whether they're identical to the build-script pattern.
- `research/tools/requirements-spike/RUNBOOK.md:90` — read the surrounding bash block to confirm the `ROOT` substitution pattern is the only place that uses the var.

**Optional**:
- Other `sys.path.insert` calls elsewhere in `research/tools/requirements-spike/` (`grep -rn "sys.path.insert"`) — if any others exist with relative or `__file__`-based forms, mirror that style.

## Acceptance

- [ ] **R1** — `grep -n "/home/andrew/dev/projects/twr2" RUNBOOK.md build_icao4444_seeded_promotions.py test_quality_gates.py` (in their directory) returns empty.
- [ ] **R2** — Smoke-run each script / bash form from `/tmp` (non-repo cwd) succeeds: Python imports resolve, bash `ROOT` expands to the repo root.
- [ ] No new occurrences of `/home/andrew` introduced anywhere; no other code paths broken (`grep -n "Path(__file__)" <each python file>` confirms the substitute is in place).

## Done summary

_(filled in by `flowctl done` after the work lands)_

## Evidence

- Commits:
- Tests:
- PRs:
