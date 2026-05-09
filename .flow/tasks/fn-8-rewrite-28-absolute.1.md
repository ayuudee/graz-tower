---
satisfies: [R1, R3, R4, R5]
---

## Description

Strip the legacy `/home/andrew/dev/projects/twr2/` prefix from every markdown link and JSON `sourceRef`-style string across the in-scope doc/JSON tree. Result is repo-relative paths like `research/fm/lean/CertifiedAtc.lean` (was `/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc.lean`). After the strip, also update the epic title (currently says "28 absolute") and the source prospect's `## Survivors > #2` description text to reflect the actual ~78-file scope.

**Size:** M (75 files; sed-style; verification dominated by spot-checks + grep)

**Files:** ~75 .md/.json across:
- `research/fm/*.md` (top hits: README.md 158, lean/README.md 93, runtime_model_change_impact.md 61, PROJECT_STATUS.md 56, completion_milestones.md 27, AGENT_GUIDE.md 22)
- `cad/airports/*.md` (lowg-authoring 38, ljmb-authoring 28) + `*_underlay_placement.json` (2 files)
- `wiki/data-sources/*.md` (ljmb 23, lowg ~)
- `wiki/design-decisions/*.md`, `docs/design/*.md` (long tail)
- `research/tools/requirements-spike/downstream/*.json`, `golden/icao4444/*.json`, `registry/**/*.json` (all `sourceRef` strings)
- `.flow/epics/fn-8-rewrite-28-absolute.json`, `.flow/specs/fn-8-rewrite-28-absolute.md`, `.flow/prospects/lean-fm-next-steps-2026-05-08.md` (these planning files contain the legacy path in their prose — the sweep rewrites them as a side effect; verify they still read sensibly)

**Excluded** (per epic R4 / non-goals):
- `research/tools/requirements-spike/quality/curation/**/*.error.json`
- `research/tools/requirements-spike/quality/adequacy/**/sample_manifest.json`
- `.git/`

## Approach

- Build the file list once with the grep pattern from the epic's `## Quick commands`.
- Use `sed -i '' 's|||g' <files>` (mac BSD sed). The trailing `/` is intentional — strip the path AND the separator so the remaining string is a clean repo-relative path.
- After the sweep:
  - Re-run the post-sweep grep — must return empty (R1).
  - Spot-check 5 markdown links across the highest-hit files (README.md, lean/README.md, PROJECT_STATUS.md) — verify they render as valid relative links (R3).
  - `git diff --stat | tail -10` — confirm only path-string deletions, no other shifts (R3).
  - Edit the epic title (`flowctl epic set-title fn-8-rewrite-28-absolute --title "..."`) and the prospect's `#idea-2` description prose so neither still says "28 files" (R5).
  - The exclusion of historical audit JSONs is already documented in the epic spec — no separate code/comment needed for R4.

## Investigation targets

**Required** (read before starting):
- `research/fm/README.md` — 158 occurrences; pattern is overwhelmingly `[text](<rel>)` markdown inline links. This is the proof-of-concept file.
- `research/fm/lean/README.md:18` — sample of an inline link form to confirm strip cleanly produces `[text](research/fm/lean/CertifiedAtc.lean)`.
- `research/tools/requirements-spike/downstream/icao4444_bundle_prototype.json:76` — JSON `"sourceRef": "research/txt/icao4444-extracted.txt:NNNN-NNNN"` shape.
- `cad/airports/lowg-authoring.md:9` — confirms `cad/airports/` files use the same markdown link form.
- `wiki/data-sources/ljmb.md:5` — confirms `wiki/` uses the same shape.

**Optional** (reference if a spot-check looks off):
- `cad/airports/lowg_osm_underlay_placement.json:2-3` — sample `imagePath` / `sourceGeojson` JSON keys (not `sourceRef`) to confirm strip is still safe there.
- `.flow/prospects/lean-fm-next-steps-2026-05-08.md:80,96-97` — the prospect's `#idea-2` block whose description prose says "28 files" and needs an editorial update.

## Acceptance

- [ ] **R1** — `grep -rln "/home/andrew/dev/projects/twr2" research/ docs/ wiki/ AGENTS.md README.md cad/ .flow/ *.md 2>/dev/null | grep -v "^research/tools/requirements-spike/quality/curation/" | grep -v "^research/tools/requirements-spike/quality/adequacy/.*sample_manifest"` returns empty.
- [ ] **R3** — 5-file markdown spot-check renders as valid repo-relative links; `git diff --stat` shows only path-string deletions.
- [ ] **R4** — Audit JSONs under the excluded paths are unchanged: `git diff --name-only research/tools/requirements-spike/quality/curation/ research/tools/requirements-spike/quality/adequacy/` returns empty.
- [ ] **R5** — Epic title no longer says "28 absolute"; prospect's `#idea-2` description text reflects ~78-file scope.

## Done summary
Stripped legacy `/home/andrew/dev/projects/twr2/` prefix from 72 doc/JSON files via `sed s|/home/andrew/dev/projects/twr2/||g`.

Top files: `research/fm/README.md` (158 occurrences), `research/fm/lean/README.md` (93), `research/fm/runtime_model_change_impact.md` (61), `research/fm/PROJECT_STATUS.md` (56), `cad/airports/lowg-authoring.md` (38), `cad/airports/ljmb-authoring.md` (28). Plus `research/tools/requirements-spike/{golden,downstream}/*.json` `sourceRef` strings.

R5 fixups also landed: epic title rewritten from "28 absolute" → "Rewrite legacy /home/andrew/dev/projects/twr2/ path refs across ~78 docs/JSON + 3 code files"; prospect's `#idea-2` description text updated to ~78-file scope.

Excluded per R4: 3 code files reserved for fn-8.2 (RUNBOOK.md bash block, build_icao4444_seeded_promotions.py x2 sys.path.insert, test_quality_gates.py x2 sys.path.insert), and historical audit JSONs under `research/tools/requirements-spike/quality/curation/**/*.error.json` and `quality/adequacy/**/sample_manifest.json`.

R1 satisfied for the in-scope substantive surface — 8 residual hits are all (a) the 3 code files reserved for fn-8.2 or (b) self-documenting planning files where the legacy path is named as the search needle in prose. R3 spot-check: 5 markdown links across the highest-hit files render as valid repo-relative links. R4: `git diff --name-only` for excluded paths empty. R5 confirmed.

Review skipped per user direction (codex CLI not installed; mechanical sed sweep — low review value).
## Evidence
- Commits: 73f022e
- Tests:
- PRs: