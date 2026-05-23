---
satisfies: [R6, R8, R9, R10, R11]
---

## Description

Close chunk 01 of the ICAO 9432 programme: integrate the four primitive landings (.2/.3/.4) into the chunk artifacts, write the AGENT_DIALOGUE closure entry, run the principal-agent self-assessment before commit, and (if file convention permits) record the chunk-01 closure sentence in STRATEGY.md.

This task does NOT produce new evidence DSL or production code. It is the integration + handoff step.

**Pre-conditions** (checked at task start):
- Tasks .2, .3, .4 are complete.
- All chunk-01 evidence tests live on disk.
- Every covered-red landing has a named spawned production-repair epic spec AND the `.plan` blocker for that unit has been REPLACED (not deleted) with a one-line pointer to the repair epic.

**Deliverables**:

1. **`chunk_01_coverage_report.md` update**:
   - Re-render the coverage table reflecting the four new terminal states (covered-green or covered-red per task outcome).
   - Update the summary tally (was: 6 covered-green / 4 expected-gap / 7 phraseology-later / 2 policy-blocked / 1 not-applicable).
   - Replace each chunk-specific blocker row in the Repair / Follow-Up Handoff section: COMMS-1, FN33-MODEL-1, FN44-GAP-1, FN44-GAP-2 are CLOSED; record spawned production-repair epic IDs (if any) inline.
   - Update the "Focused verification run" command block.
   - Record actual `./gradlew-nix` invocation result (green/red breakdown).

2. **`STRATEGY.md` Requirements-registry track update — soft contract**:
   - If `STRATEGY.md` exists and follows a track-with-prose-narrative shape, add a sentence noting chunk-01 closure (e.g., "ICAO 9432 chunk 01 (communications, transfer, readback) closed at fn-48: COMMS-1 / FN33-MODEL-1 / FN44-GAP-1 / FN44-GAP-2 resolved; 7 phraseology-later units remain pending PHRASE-1, 2 policy-blocked pending POLICY-1.").
   - If the strategy file structure has changed such that the Requirements-registry track is unrecognizable: skip the strategy update entirely and note in the AGENT_DIALOGUE closure entry that strategy update was skipped. Honest-close-out > forced edit.
   - Bump `last_updated` only if a sentence was added.

3. **`AGENT_DIALOGUE.md` closure entry** under the Dialogue Log:
   - New dated section.
   - Verification command + actual result (green/red split).
   - New coverage tally.
   - Spawned repair-epic IDs (if any).
   - Explicit "Chunk 02 (ground movement / pushback / taxi) is ready for the Test Writer" signal.
   - Reference to the AGENTS.md self-assessment outcome (sub-block).

4. **Principal-agent self-assessment** (AGENTS.md §Self-assessment before review):
   - Walk the 8 criteria (Totality, Reversal completeness, Interaction coverage, Test coverage, New-field audit, Operational correctness, Error-handling honesty, Deferment honesty).
   - Record findings in AGENT_DIALOGUE.md alongside the closure entry as a sub-block. Anything not fixed in this epic goes to `.plan` (and `docs/deferments.md` if a named contract). Empty assessment is unacceptable — every criterion gets at least a one-line check-result.

**Size:** M
**Files:**
- `research/tools/requirements-spike/quality/icao9432_programme/chunk_01_comms_readback_transfer/coverage_report.md`
- `STRATEGY.md` (only if file convention permits — soft contract per above)
- `AGENT_DIALOGUE.md` (closure entry + self-assessment sub-block)
- `.plan` (only if self-assessment surfaces a deferred follow-up — otherwise unchanged)
- `docs/deferments.md` (only if a named-contract deferment surfaces)

## Approach

- **Coverage report**: paragraph-level edits, not a rewrite. Structure stays; rows change. Cite each new test by its source file.
- **STRATEGY.md** (soft contract): single-sentence addition in Requirements-registry track if file convention permits. Do NOT restructure. If the file's shape doesn't accommodate a simple sentence addition (e.g., it's been restructured by another in-flight epic), skip and document.
- **AGENT_DIALOGUE.md closure entry**: append under Dialogue Log; do NOT edit earlier entries. Use existing date-section convention. Self-assessment sits as a sub-block inside the closure entry.
- **Verification run**: invoke `./gradlew-nix :sim:jvmTest :controller:jvmTest --tests "*Icao9432*"` then `./gradlew-nix detekt` then `./gradlew-nix build`. Record exact result. If any test class is unexpectedly red beyond spawned-repair-epic scope, halt and raise `QUESTION:` — do NOT silently mark chunk closed.
- **`.plan` verification**: confirm COMMS-1, FN33-MODEL-1, FN44-GAP-1, FN44-GAP-2 are closed correctly per the rule (deleted on green; replaced with repair-epic pointer on red). This is a safety net — .2/.3/.4 already do the work; .5 verifies.

## Investigation targets

**Required**:
- `research/tools/requirements-spike/quality/icao9432_programme/chunk_01_comms_readback_transfer/coverage_report.md` — current state
- `STRATEGY.md` — Requirements-registry track section, soft-contract check
- `AGENT_DIALOGUE.md` — Dialogue Log convention
- `AGENTS.md` §Self-assessment before review — the 8 criteria
- `docs/deferments-CONVENTION.md` — four-bucket decision tree
- All four new chunk-01 test files (from .2/.3/.4)
- Spawned repair-epic specs (if any)

**Optional**:
- Memory entry `honest-close-out-dont-assert-green-on-2026-05-17` — never claim GREEN on suites not actually run.

## Key context

This task is gating: a sloppy closure entry undermines the inaugural Test-Completer pass and the two-actor doctrine landed in .1. The self-assessment must be honest — if a criterion is genuinely not applicable, say so; if a finding is left for follow-up, file it in `.plan`/`docs/deferments.md` with a named contract.

Per memory `honest-close-out-dont-assert-green-on-2026-05-17`: never assert GREEN on a test suite not actually run end-to-end. Verification command must execute, and its real output recorded in the closure entry.

The STRATEGY.md update is **soft** — if it's hard to land cleanly, skipping is honest; the closure entry then explicitly records the skip with a one-line reason.

## Acceptance

- [ ] `coverage_report.md` reflects four new terminal states (covered-green or covered-red per .2/.3/.4 outcome); zero remaining `expected-gap`; phraseology-later (7) and policy-blocked (2) untouched; not-applicable (1) untouched.
- [ ] Each `covered-red` row carries the spawned production-repair epic ID inline.
- [ ] `STRATEGY.md` Requirements-registry track has a chunk-01 closure sentence AND `last_updated` bumped — OR closure entry documents an explicit skip with one-line reason.
- [ ] `AGENT_DIALOGUE.md` closure entry recorded with: dated section, verification command + actual result, new coverage tally, spawned repair-epic IDs (if any), "Chunk 02 ready for Test Writer" signal.
- [ ] Principal-agent self-assessment recorded — 8 criteria, each with a check-result, anything not fixed filed in `.plan` (and `docs/deferments.md` if named-contract).
- [ ] `./gradlew-nix :sim:jvmTest :controller:jvmTest --tests "*Icao9432*"` executed end-to-end; result recorded verbatim in the closure entry. Result is GREEN modulo any spawned-repair-epic-scoped reds (those reds remain red and are named).
- [ ] `./gradlew-nix detekt` and `./gradlew-nix build` pass (closure gate, run as separate commands).
- [ ] `SourceUnitCitationValidationTest` and `EvidenceSourceCatalog` validation green.
- [ ] `.plan` items COMMS-1, FN33-MODEL-1, FN44-GAP-1, FN44-GAP-2 confirmed closed per the rule (paragraph deleted on green; one-line pointer on red). This task is the safety net.
- [ ] PHRASE-1 and POLICY-1 entries in `.plan` UNCHANGED — verified by diff scan against pre-epic baseline.
- [ ] No new programme-level deferments introduced (verified by `docs/deferments.md` diff scan).
- [ ] Honest close-out: if any criterion fails honest review (per memory `honest-close-out-dont-assert-green-on-2026-05-17`), chunk closure is HALTED until the failure is resolved or filed with a named follow-up; closure entry records the halt, not a false-green.

## Done summary
_(filled at task close)_
## Evidence
- Commits:
- Tests:
- PRs:
