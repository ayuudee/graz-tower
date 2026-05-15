# fn-21 — Refresh STRATEGY.md after this session's five-epic run (Runtime + Reviewer-infrastructure tracks)

## Overview

STRATEGY.md's `last_updated: 2026-05-08` is 7+ days stale. Since then this session shipped five epics that materially advanced two of the four strategy tracks:

- **Runtime simulator track**: fn-15 (G3a-react-tailwind, ninth golden), fn-16 (`SimState.weatherByAerodrome` → `Aerodrome.weather` atomic migration — the "rich-world-domain" principle now applies to weather, sibling of fn-12's `Runway.obstruction`), fn-17 (CAP 413 Edition 24 numbering reconciliation + `CAP_413_EDITION` constant), fn-19 (LJMB SID test reconciliation — `./gradlew build` no longer red on D-WORLD.2).
- **Reviewer / agent infrastructure track**: fn-18 (four-bucket deferment register reorganization — `docs/deferments.md` is now the in-repo CI-visible canonical surface for D-* deferments, with `docs/deferments-CONVENTION.md` as the decision-tree doc; closes the long-standing `~/.claude/plans/pilot-firewall.md` external-only register pattern).

Refresh the two affected tracks' prose to reflect these landings, and bump `last_updated`. **No structural changes** — the four tracks, target-problem, and approach all stay the same. This is editing one paragraph per affected track plus the metadata.

## Boundaries / non-goals

- **Out: scope re-framing.** The four tracks and the target-problem / approach text are not re-litigated; fn-21 only updates "what has shipped" prose.
- **Out: FM/Lean and Requirements registry track edits.** This session shipped no Lean work and no registry-track work (fn-10 + fn-20 are open but pre-date this session). Those track sections stay as-is.
- **Out: anything outside `STRATEGY.md`.** No AGENTS.md / `.plan` / docs edits. The strategy refresh is a single-file task.

## Strategy Alignment

Active tracks served by this plan:
- **Reviewer / agent infrastructure** — this IS a strategy doc maintenance task; the track itself flags "plans review-aware by construction" as a commandment. Updating the strategy doc keeps it review-aware by reflecting actual shipped state.

## Decision context

**Why now**: per the prospect artifact's high-leverage analysis, the strategy doc is the input every downstream `/flow-next:prospect` / `/flow-next:plan` / `/flow-next:interview` reads for grounding. 5 epics shipped since the last refresh means downstream planning is grounded on a 7-day-stale snapshot. Small edit; large amortised payoff.

**Edit shape**: paragraph-level updates to the two affected track sections. The Runtime simulator track's golden-anchors list adds nothing (G3a-react-tailwind is already there from fn-15) but the body should mention the post-G3a closures (fn-16's weather migration, fn-17's CAP 413 Ed 24, fn-19's LJMB SID reconciliation). The Reviewer track mentions the deferment-register reorganization. `last_updated` bumps to 2026-05-15.

**Tooling**: `flowctl strategy` has `read`/`status` subcommands but writes are intentionally manual (the skill's "read-only plumbing — the skill writes the file" rule). Edit the file directly.

## Acceptance

- **R1:** `STRATEGY.md` `last_updated:` metadata bumped to `2026-05-15`. The format is a single date on a metadata line; do not introduce ISO datetime, just the date.
- **R2:** Runtime simulator track's body paragraph updated to mention (a) the `Aerodrome.weather` entity-field migration (fn-16) as a sibling of `Runway.obstruction` (fn-12) under the rich-world-domain principle; (b) CAP 413 Edition 24 numbering reconciliation (fn-17) with the new `CAP_413_EDITION` constant; (c) the LJMB-test reconciliation (fn-19) that flipped `./gradlew build` green on D-WORLD.2. The existing "IFR wiring (IFR-1..6) and approach sequencing are the next live verticals" sentence stays — those remain the next-vertical claim.
- **R3:** Reviewer / agent infrastructure track's body paragraph updated to mention the four-bucket deferment register reorganization (fn-18): `docs/deferments.md` is the in-repo CI-visible canonical surface; `docs/deferments-CONVENTION.md` is the decision-tree doc; the four buckets are (1) test contract / API exists, (2) API gap / commented-out future, (3) multi-task / flow-next epic, (4) narrative / cross-cutting. The OR-3 (autonomous adversarial loop) "flagged not-started" mention stays — OR-3 didn't move this session.
- **R4:** FM/Lean track + Requirements registry track + target_problem + approach + name sections are **unchanged**. Acceptance verifies the diff touches only the three things above (R1/R2/R3).
- **R5:** `flowctl strategy status --json` continues to report `sections_filled: 6` (no schema break). `flowctl strategy read --json` parses cleanly — the metadata-line parser doesn't fall over on the new date.
- **R6:** Diff stays small — single file, ≤30 line-changes. Larger diff signals scope creep; flag if it grows.

## Early proof point

Task `fn-21-refresh-strategymd-5-days-stale-4-epics.1` is the only task. If the strategy parser fails (`flowctl strategy read --json` errors) after the edit, the edit broke something structurally — revert and re-evaluate before re-trying.

## Quick commands

```bash
# Pre-edit baseline
.flow/bin/flowctl strategy status --json | jq '{last_updated, sections_filled, total_sections}'
.flow/bin/flowctl strategy read --json | jq -r '.tracks' | head -50  # current track text

# Post-edit verification
.flow/bin/flowctl strategy status --json | jq '{last_updated, sections_filled, total_sections}'
.flow/bin/flowctl strategy read --json | jq -r 'keys' # confirm all 6 sections still parse

# Diff sanity
git diff STRATEGY.md | wc -l  # should be small (≤30 lines)
```

## Requirement coverage

| Req | Description | Task(s) | Gap justification |
|-----|-------------|---------|-------------------|
| R1  | last_updated → 2026-05-15 | fn-21-refresh-strategymd-5-days-stale-4-epics.1 | — |
| R2  | Runtime track body refresh (fn-16/17/19 mentions) | fn-21-refresh-strategymd-5-days-stale-4-epics.1 | — |
| R3  | Reviewer track body refresh (fn-18 deferment-register reorganization) | fn-21-refresh-strategymd-5-days-stale-4-epics.1 | — |
| R4  | Other sections unchanged | fn-21-refresh-strategymd-5-days-stale-4-epics.1 | Verified via diff inspection |
| R5  | flowctl strategy parser still parses cleanly | fn-21-refresh-strategymd-5-days-stale-4-epics.1 | — |
| R6  | Diff ≤30 lines | fn-21-refresh-strategymd-5-days-stale-4-epics.1 | Scope-creep guard |

## Review considerations

- **FP / type safety**: not applicable — doc-only edit, no Kotlin code touched.
- **Test architecture**: not applicable — no tests added or modified.
- **Impact**: scoped to one file (`STRATEGY.md`). The downstream impact is every future `/flow-next:prospect` / `/flow-next:plan` / `/flow-next:interview` reads more accurate strategy grounding. **Reviewer focus**: confirm the diff is single-file and the parser still passes.
- **Operational ATC correctness / applicability**: not applicable — no controller / pilot / world-model surface changes. The strategy doc describes what has shipped, not how the runtime behaves.

## References

- Repo files:
  - `STRATEGY.md` — the file being edited (Runtime simulator + Reviewer / agent infrastructure tracks; `last_updated` metadata)
- Recent epics referenced in the refresh:
  - fn-15 (G3a-react-tailwind, ninth golden) — already credited in current text
  - fn-16 (`SimState.weatherByAerodrome` → `Aerodrome.weather`) — NEW mention
  - fn-17 (CAP 413 Edition 24 numbering reconciliation) — NEW mention
  - fn-18 (four-bucket deferment register, `docs/deferments.md` canonical) — NEW mention
  - fn-19 (LJMB SID test reconciliation, build green) — NEW mention
- Strategy tooling:
  - `.flow/bin/flowctl strategy status --json` — readiness/parse-status check
  - `.flow/bin/flowctl strategy read --json` — parsed snapshot read
  - `docs/deferments.md` / `docs/deferments-CONVENTION.md` — the deferment-register surface fn-18 established (cited from R3)
