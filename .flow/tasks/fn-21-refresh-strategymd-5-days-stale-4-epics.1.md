---
satisfies: [R1, R2, R3, R4, R5, R6]
---

## Description

Single doc-only task. Edit `STRATEGY.md` to refresh the two tracks this session advanced (Runtime simulator + Reviewer / agent infrastructure), and bump `last_updated`. Other tracks + target_problem + approach + name sections are unchanged.

**Size:** S (1 file, ≤30 line-changes).

**Files** (read or modify):
- **MODIFY**: `./STRATEGY.md` — only file edited.
- **READ ONLY** (verify only): `./docs/deferments.md`, `./docs/deferments-CONVENTION.md`, recent epic specs in `.flow/specs/fn-1{5,6,7,8,9}-*.md` (to ensure refresh prose is accurate).

## Approach (numbered Steps)

### Step 0 — Baseline capture

```bash
git rev-parse HEAD > $TMPDIR/fn-21-1-base-sha.txt
.flow/bin/flowctl strategy status --json | jq '{last_updated, sections_filled}' | tee $TMPDIR/fn-21-1-base-status.txt
.flow/bin/flowctl strategy read --json | jq '. | keys' | tee $TMPDIR/fn-21-1-base-keys.txt
```
Confirm: `last_updated: "2026-05-08"`, `sections_filled: 6`, all 6 keys present (name, target_problem, approach, tracks, last_updated, plus the implicit generator field).

### Step 1 — Edit Runtime simulator track body (R2)

In `STRATEGY.md`, find the `### Runtime simulator (...)` section. The current body ends with "IFR wiring (IFR-1..6) and approach sequencing are the next live verticals." Edit the body to add — before that closing sentence — a sentence (or sentences) that mention:

- **fn-16's `Aerodrome.weather` migration**: e.g. "The rich-world-domain principle that landed `Runway.obstruction` in fn-12 now applies to weather: fn-16 migrated `SimState.weatherByAerodrome` to `Aerodrome.weather` atomically (hard cutover, no shim) — time-varying state lives on the entity it concerns."
- **fn-17's CAP 413 Ed 24 reconciliation**: e.g. "fn-17 reconciled CAP 413 numbering to Edition 24 (effective 1 July 2026) — `RegulationRef.CAP_413_EDITION` constant added; all `CAP413_*` entries cite Ed 24 numbering except two retained Ed 23 inline literals gated by open principle-cite-audit deferments."
- **fn-19's LJMB SID test reconciliation**: e.g. "fn-19 closed the long-standing D-WORLD.2 pre-existing failure by reshaping `LjmbWorldCandidateValidationTest` to a coverage + structural-validity decomposition against the current 5-SID CIFP cycle — `./gradlew build` is green on this path."

The "G3a-react-tailwind" mention in the existing prose stays (fn-15 already credited).

### Step 2 — Edit Reviewer / agent infrastructure track body (R3)

In `STRATEGY.md`, find the `### Reviewer / agent infrastructure (...)` section. Edit the body to add a sentence about the four-bucket deferment register reorganization (fn-18):

e.g. "fn-18 reorganized the deferment register into a four-bucket model: `docs/deferments.md` is the in-repo CI-visible canonical surface (replacing the external `~/.claude/plans/pilot-firewall.md` register); `docs/deferments-CONVENTION.md` is the decision-tree doc; entries land in one of four buckets — (1) test contract / API exists today, (2) API gap / commented-out future, (3) multi-task / flow-next epic, (4) narrative / cross-cutting / blocked-on-real-world. 98 active + 37 archive entries at fn-18 close."

The "Autonomous adversarial loop (OR-3) flagged in `.plan` as not-started" sentence stays — OR-3 didn't move.

### Step 3 — Bump `last_updated` (R1)

Find the `last_updated:` line in the STRATEGY.md frontmatter / metadata block. Change `2026-05-08` to `2026-05-15`. **Single date, no ISO datetime.**

### Step 4 — Verify (R4, R5, R6)

```bash
# R5 — parser still works
.flow/bin/flowctl strategy status --json | jq '{last_updated, sections_filled, total_sections}'
# Expect: last_updated: "2026-05-15", sections_filled: 6, total_sections: 6

.flow/bin/flowctl strategy read --json | jq '. | keys'
# Expect: all 6 keys present (no schema break)

# R6 — diff scope sanity
git diff STRATEGY.md | wc -l
# Expect: small, ≤30 lines (per R6 scope-creep guard)

# R4 — verify only Runtime + Reviewer track bodies + last_updated changed
git diff STRATEGY.md | grep -E '^[+-]' | grep -v '^[+-]{3}' | head -40
# Inspect: should show only the three edit zones; FM/Lean / Requirements registry / target_problem / approach / name unchanged
```

### Step 5 — `flowctl done`

```bash
cat > $TMPDIR/fn-21-1-summary.md <<'EOF2'
fn-21.1 shipped: STRATEGY.md `last_updated` bumped 2026-05-08 → 2026-05-15. Runtime simulator track body refreshed to mention fn-16 (Aerodrome.weather migration), fn-17 (CAP 413 Ed 24 reconciliation), fn-19 (LJMB SID test reconciliation, build green). Reviewer / agent infrastructure track body refreshed to mention fn-18 (four-bucket deferment register reorganization, docs/deferments.md canonical). Other sections unchanged. `flowctl strategy status` and `flowctl strategy read` still parse cleanly; diff ≤30 lines.
EOF2

cat > $TMPDIR/fn-21-1-evidence.json <<'EOF2'
{
  "task": "fn-21-refresh-strategymd-5-days-stale-4-epics.1",
  "base_sha": "<from base-sha.txt>",
  "implementation_sha": "<SHA before flowctl done>",
  "diff_line_count": "<git diff STRATEGY.md | wc -l result>",
  "last_updated_change": "2026-05-08 → 2026-05-15",
  "tracks_updated": ["Runtime simulator", "Reviewer / agent infrastructure"],
  "tracks_unchanged": ["FM / Lean proof program", "Requirements registry"],
  "epics_credited": ["fn-16 (Aerodrome.weather)", "fn-17 (CAP 413 Ed 24)", "fn-18 (deferment register)", "fn-19 (LJMB SID test)"],
  "parser_check": "flowctl strategy status/read both green; sections_filled=6 unchanged",
  "verify_outcome": "STRATEGY.md edited; parser green; diff scope within ≤30 lines"
}
EOF2

.flow/bin/flowctl done fn-21-refresh-strategymd-5-days-stale-4-epics.1 \
  --summary-file $TMPDIR/fn-21-1-summary.md \
  --evidence-json $TMPDIR/fn-21-1-evidence.json --json
```

## Acceptance

- [ ] **R1** — `last_updated:` is `2026-05-15` after edit; format is single date (no ISO datetime).
- [ ] **R2** — Runtime simulator track body includes fn-16 / fn-17 / fn-19 mentions per Step 1.
- [ ] **R3** — Reviewer / agent infrastructure track body includes fn-18 four-bucket-deferment-register mention per Step 2.
- [ ] **R4** — Other sections (FM/Lean, Requirements registry, target_problem, approach, name) unchanged. Verified via `git diff` inspection — diff touches only the three intended zones.
- [ ] **R5** — `flowctl strategy status --json` reports `sections_filled: 6, total_sections: 6` post-edit. `flowctl strategy read --json` parses cleanly (all keys present).
- [ ] **R6** — `git diff STRATEGY.md | wc -l` ≤ 30 lines. If larger, surfaces scope creep — flag and re-evaluate.

## Key context

- Doc-only edit. No code, no tests, no production behavior.
- The two affected tracks' body paragraphs are prose; pick a natural insertion point in each (typically before the existing "_Why it serves the approach:_" line).
- `flowctl strategy` is read-only plumbing — write to `STRATEGY.md` directly.
- Pre-existing dirty state (research/tools/requirements-spike/) MUST NOT be staged.

## Done summary
fn-21.1 shipped: STRATEGY.md `last_updated` bumped 2026-05-08 → 2026-05-15. Five epics shipped this session (fn-15/16/17/18/19); four require new prose here (fn-15's G3a-react-tailwind is already credited in the existing track text). Runtime simulator track body refreshed to mention fn-16 (Aerodrome.weather migration), fn-17 (CAP 413 Ed 24 reconciliation, `CAP_413_EDITION` constant in `RegulationDatabase`), fn-19 (LJMB SID test reconciliation, build green). Reviewer / agent infrastructure track body refreshed to mention fn-18 (four-bucket deferment register reorganization, docs/deferments.md canonical, 98 active + 37 archive entries). Other sections (FM/Lean, Requirements registry, target_problem, approach, name) unchanged. `flowctl strategy status` and `flowctl strategy read` parse cleanly post-edit; diff = 30 lines (within ≤30 R6 scope-creep guard). Codex impl-review path: triage-skip fast-path verdict SHIP (docs-only diff).
## Evidence
- Commits: 8eb54662e02b08a8f78ece39d611bf69e44e6369
- Tests: flowctl strategy status --json, flowctl strategy read --json, git diff STRATEGY.md | wc -l
- PRs: