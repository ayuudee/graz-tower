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
   - Re-render the coverage table reflecting the four new terminal states. **Actual distribution from .2/.3/.4**: 2 covered-green (FN44-GAP-1 via task .2; FN33-MODEL-1 via task .4) + 2 covered-red (FN44-GAP-2 via task .2 → fn-49; COMMS-1 via task .3 → fn-50). <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.4 confirmed the as-built tally 2-green/2-red, not the originally-anticipated 1-green/3-red -->
   - Update the summary tally (was: 6 covered-green / 4 expected-gap / 7 phraseology-later / 2 policy-blocked / 1 not-applicable). New tally: **8 covered-green / 2 covered-red / 0 expected-gap / 7 phraseology-later / 2 policy-blocked / 1 not-applicable**. <!-- Updated by plan-sync: derived from .2/.3/.4 done summaries -->
   - Replace each chunk-specific blocker row in the Repair / Follow-Up Handoff section: FN44-GAP-1 and FN33-MODEL-1 are CLOSED (covered-green, paragraph deleted from `.plan` for FN44-GAP-1; FN33-MODEL-1 paragraph PARTIALLY rewritten because the other two §2.8.3 source units it references remain `blocked_by_model_gap`); FN44-GAP-2 → tracked by `fn-49-sim-emits-pilot-notified-frequency`; COMMS-1 → tracked by `fn-50-sim-models-reception-quality-comms-1`. Record spawned production-repair epic IDs inline on the two covered-red rows. <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.4 found the FN33-MODEL-1 paragraph survives as a partial-closure (route-clearance timing ::36e6ad16cffe8726 and TAKE OFF phraseology ::f06dfa1cefd2d649 still blocked_by_model_gap), not fully deleted as the green rule prescribes for a clean covered-green -->
   - Update the "Focused verification run" command block: replace the legacy `nix-shell --run './gradlew ...'` form with the wrapper form `./gradlew-nix :sim:jvmTest :controller:jvmTest --tests "*Icao9432*"` per memory `gradlew-nix-wrapper-2026-05-18`. The `*Icao9432*` wildcard covers `Icao9432Chunk01ReadbackEvidenceTest`, `Icao9432Chunk01FrequencyTransferEvidenceTest`, `Icao9432Chunk01ReceptionDoubtEvidenceTest`, `Icao9432Chunk01ClearancePacingEvidenceTest` (sim side) + `Icao9432ReadbackConformanceSpec` (controller side). <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.4 evidence rows use ./gradlew-nix directly; coverage_report.md still carries the legacy form -->
   - Record actual `./gradlew-nix` invocation result (green/red breakdown). The 4 chunk-01 evidence tests + `Icao9432ReadbackConformanceSpec` are all green per task .4 evidence row. Note the pre-existing sandbox failure: `EvidenceReportWriterTest` fails on `Files.createTempDirectory` due to macOS TMPDIR sandbox restriction; reproduces on master baseline; NOT a chunk-01 closure blocker — record it in the closure entry as a known pre-existing finding, not as a chunk-01 regression. <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.4 surfaced the pre-existing sandbox failure on EvidenceReportWriterTest -->

2. **`STRATEGY.md` Requirements-registry track update — soft contract**:
   - If `STRATEGY.md` exists and follows a track-with-prose-narrative shape, add a sentence noting chunk-01 closure. **Honest as-built phrasing** (the prior example overstated all four units as "resolved" — only FN44-GAP-1 and FN33-MODEL-1 are fully covered-green; FN44-GAP-2 and COMMS-1 land covered-red with named repair-epic pointers, which is a different terminal state): e.g., "ICAO 9432 chunk 01 (communications, transfer, readback) closed at fn-48: FN44-GAP-1 and FN33-MODEL-1 landed covered-green (the latter via the new `EvidenceAuditOutcome.Advisory` audit-outcome leaf for §2.8.3.2's "should" semantics); FN44-GAP-2 and COMMS-1 landed covered-red and are tracked by `fn-49-sim-emits-pilot-notified-frequency` and `fn-50-sim-models-reception-quality-comms-1` respectively; 7 phraseology-later units remain pending PHRASE-1, 2 policy-blocked pending POLICY-1." <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.4 confirmed the actual 2-green/2-red distribution + the EvidenceAuditOutcome.Advisory leaf is the load-bearing soft-contract anchor -->
   - If the strategy file structure has changed such that the Requirements-registry track is unrecognizable: skip the strategy update entirely and note in the AGENT_DIALOGUE closure entry that strategy update was skipped. Honest-close-out > forced edit.
   - Bump `last_updated` only if a sentence was added.

3. **`AGENT_DIALOGUE.md` closure entry** under the Dialogue Log:
   - New dated section.
   - Verification command + actual result (green/red split). **Pre-existing sandbox failure to call out explicitly**: `EvidenceReportWriterTest` fails on `Files.createTempDirectory` (macOS TMPDIR sandbox restriction); reproduces on master baseline — record this as a known pre-existing finding, not as a chunk-01 regression. <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.4 surfaced the pre-existing sandbox failure -->
   - New coverage tally: **8 covered-green / 2 covered-red / 0 expected-gap / 7 phraseology-later / 2 policy-blocked / 1 not-applicable**. <!-- Updated by plan-sync: derived from .2/.3/.4 done summaries -->
   - Spawned repair-epic IDs: `fn-49-sim-emits-pilot-notified-frequency` (FN44-GAP-2 closure) and `fn-50-sim-models-reception-quality-comms-1` (COMMS-1 closure). <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.2 spawned fn-49; fn-48-icao-9432-chunk-01-drive-expected-gap.3 spawned fn-50 -->
   - Explicit "Chunk 02 (ground movement / pushback / taxi) is ready for the Test Writer" signal.
   - Reference to the AGENTS.md self-assessment outcome (sub-block).

4. **Principal-agent self-assessment** (AGENTS.md §Self-assessment before review):
   - Walk the 8 criteria (Totality, Reversal completeness, Interaction coverage, Test coverage, New-field audit, Operational correctness, Error-handling honesty, Deferment honesty).
   - Record findings in AGENT_DIALOGUE.md alongside the closure entry as a sub-block. Anything not fixed in this epic goes to `.plan` (and `docs/deferments.md` if a named contract). Empty assessment is unacceptable — every criterion gets at least a one-line check-result.
   - **Captured lesson from chunk** (reference under Test coverage / Error-handling honesty as applicable): memory entry `bug/test-failures/audit-selectors-must-activate-examined-2026-05-26` (landed during .3's Codex impl-review fix-loop) — audit selectors must call `activate(fact.id)` for every consulted fact on ALL non-empty paths before branching outcomes, or `AuditEvidenceCaseBuilder.toCase` silently overrides the selector's specific outcome with a generic "did not activate any evidence facts" `Fail`. Task .4 applied this discipline pre-emptively on the `AuditClearancePacingSubject` `Advisory` and `Pass` paths and landed Codex impl-review SHIP on first pass — concrete evidence the captured lesson works as a forward guard. <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.3 surfaced the activation-discipline lesson; .4 successfully applied it pre-emptively -->

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
- **`.plan` verification**: confirm the four blocker paragraphs are closed correctly per the rule. Expected state from .2/.3/.4:
  - FN44-GAP-1 paragraph: deleted (covered-green via task .2). <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.2 done summary records "GAP-1 paragraph deleted" -->
  - FN44-GAP-2 paragraph: REPLACED with pointer to `fn-49-sim-emits-pilot-notified-frequency` (covered-red via task .2). <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.2 spawned fn-49 -->
  - COMMS-1 paragraph: REPLACED with pointer to `fn-50-sim-models-reception-quality-comms-1` (covered-red via task .3). <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.3 spawned fn-50 -->
  - FN33-MODEL-1 paragraph: **PARTIALLY REWRITTEN, NOT FULLY DELETED** (task .4 closed only the §2.8.3.2 source unit `...::ac9111d240cfd2c2` covered-green; the two other source units the paragraph references — route-clearance timing `...::36e6ad16cffe8726` and TAKE OFF phraseology `...::f06dfa1cefd2d649` — remain `blocked_by_model_gap`). Verify the rewritten paragraph documents that exact partial-closure state and does not claim full closure. <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.4 closed §2.8.3.2 covered-green via the EvidenceAuditOutcome.Advisory leaf; the FN33-MODEL-1 paragraph stays in .plan as partial-closure -->
  This is a safety net — .2/.3/.4 already do the work; .5 verifies.

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

- [ ] `coverage_report.md` reflects four new terminal states (2 covered-green: FN44-GAP-1, FN33-MODEL-1; 2 covered-red: FN44-GAP-2 → fn-49, COMMS-1 → fn-50); zero remaining `expected-gap`; phraseology-later (7) and policy-blocked (2) untouched; not-applicable (1) untouched. New summary tally: 8 covered-green / 2 covered-red / 0 expected-gap / 7 phraseology-later / 2 policy-blocked / 1 not-applicable. <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.4 confirmed the as-built tally -->
- [ ] Each `covered-red` row carries the spawned production-repair epic ID inline (FN44-GAP-2 → `fn-49-sim-emits-pilot-notified-frequency`; COMMS-1 → `fn-50-sim-models-reception-quality-comms-1`). <!-- Updated by plan-sync: spawned epics named explicitly -->
- [ ] `STRATEGY.md` Requirements-registry track has a chunk-01 closure sentence AND `last_updated` bumped — OR closure entry documents an explicit skip with one-line reason.
- [ ] `AGENT_DIALOGUE.md` closure entry recorded with: dated section, verification command + actual result, new coverage tally, spawned repair-epic IDs (if any), "Chunk 02 ready for Test Writer" signal.
- [ ] Principal-agent self-assessment recorded — 8 criteria, each with a check-result, anything not fixed filed in `.plan` (and `docs/deferments.md` if named-contract).
- [ ] `./gradlew-nix :sim:jvmTest :controller:jvmTest --tests "*Icao9432*"` executed end-to-end; result recorded verbatim in the closure entry. The wildcard covers `Icao9432Chunk01ReadbackEvidenceTest`, `Icao9432Chunk01FrequencyTransferEvidenceTest`, `Icao9432Chunk01ReceptionDoubtEvidenceTest`, `Icao9432Chunk01ClearancePacingEvidenceTest`, and `Icao9432ReadbackConformanceSpec`. Result is GREEN modulo any spawned-repair-epic-scoped reds (those reds remain red inside the audit reports but the JUnit tests pass per the covered-red `report.results` assertion contract). The pre-existing `EvidenceReportWriterTest` macOS-TMPDIR sandbox failure reproduces on master and is NOT a chunk-01 closure blocker — record it as a known pre-existing finding. <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.4 verified the chunk-01 test inventory and surfaced the pre-existing sandbox failure -->
- [ ] `./gradlew-nix detekt` and `./gradlew-nix build` pass (closure gate, run as separate commands).
- [ ] `SourceUnitCitationValidationTest` and `EvidenceSourceCatalog` validation green.
- [ ] `.plan` items confirmed closed per the rule with the verified as-built state: FN44-GAP-1 deleted (covered-green); FN44-GAP-2 replaced with pointer to `fn-49-sim-emits-pilot-notified-frequency` (covered-red); COMMS-1 replaced with pointer to `fn-50-sim-models-reception-quality-comms-1` (covered-red); FN33-MODEL-1 **partially rewritten, not deleted** because the §2.8.3.2 source unit is covered-green via the Advisory leaf but the two other source units the paragraph references (route-clearance timing `::36e6ad16cffe8726` and TAKE OFF phraseology `::f06dfa1cefd2d649`) remain `blocked_by_model_gap`. This task is the safety net. <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.4 confirmed FN33-MODEL-1 is a partial-closure paragraph rewrite, not a full delete -->
- [ ] PHRASE-1 and POLICY-1 entries in `.plan` UNCHANGED — verified by diff scan against pre-epic baseline.
- [ ] No new programme-level deferments introduced (verified by `docs/deferments.md` diff scan).
- [ ] Honest close-out: if any criterion fails honest review (per memory `honest-close-out-dont-assert-green-on-2026-05-17`), chunk closure is HALTED until the failure is resolved or filed with a named follow-up; closure entry records the halt, not a false-green.

## Done summary
_(filled at task close)_
## Evidence
- Commits:
- Tests:
- PRs:
