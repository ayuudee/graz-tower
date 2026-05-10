---
title: Research/FM next steps after fn-9
date: "2026-05-09"
focus_hint: for research/FM next steps.
volume: 22
survivor_count: 13
rejected_count: 9
rejection_rate: 0.41
artifact_id: for-research-fm-next-steps-2026-05-09
promoted_ideas: []
status: active
---

## Focus

**Original hint** (verbatim):

> for research/FM next steps.

**Resolved focus**: research/FM next moves now that fn-9 (`VfrRoute.airspaceProfile` proof-visibility) has landed and unblocked the polygonal-airspace + predicate-strengthening successor branches.
**Focus kind**: concept (senior-maintainer + first-time-user personas).

## Grounding snapshot

focus_hint: research/FM next steps
focus_kind: concept
date: 2026-05-09

git_log_30d: top: sim/Step.kt (33), research/fm/PROJECT_STATUS.md (31), research/fm/lean/README.md (30),
  research/fm/README.md (29), research/fm/lean/CertifiedAtc.lean (26), research/fm/AGENT_GUIDE.md (25),
  controller/Controller.kt (19), sim/PilotCognitive.kt (17), requirements-spike/run_icao4444_*.py (17),
  core/CompletionEvaluation.kt (17), core/InstructionResolution.kt (16), .plan (16),
  sim/PilotMission.kt (15), protocol/Instruction.kt (15), controller/bdi/Guard.kt (15)

open_epics: 2
  - fn-5-g2-cross-aerodrome-vfr-transit-lowg-ljmb (G2 LOWG → LJMB cross-aerodrome VFR transit)
  - fn-6-kinematic-position-on (kinematic position on AircraftObservation)

done_epics_recent:
  - fn-9-lift-fm-extraction-to-consume-runtime (closed today: airspaceProfile proof-visible at extraction)
  - fn-8-rewrite-28-absolute (legacy /home/andrew/dev/projects/twr2/ path-ref sweep)
  - fn-7-author-strategymd-anchoring-target (STRATEGY.md authored)
  - fn-4-richer-airspace-geometry-widening (predecessor to fn-9)
  - fn-3-complete-lean-formalization-campaign, fn-2-resume-lean, fn-1-review-research

prior_prospect: lean-fm-next-steps-2026-05-08.md (1d ago, ideas 1-3 promoted as fn-7/8/9; ideas 4-12 unpromoted)

today_surfaced_signals (from fn-9 epic-review + sandbox unblock):
  - detekt-baseline.xml is empty (0 IDs); 11 pre-existing violations break ./gradlew build
  - LjmbWorldCandidateValidationTest.writesLjmbCurrentCoreValidationReport fails (last touched 369ead7, 2026-04-30)
  - Sandbox bootstrap requires sandbox.network.allowLocalBinding + allowUnixSockets +
    gradle.properties systemProp.https.proxy* + GRADLE_USER_HOME redirect — undocumented today

changelog_recent: scanned: none (no CHANGELOG.md)

memory_matches: scanned: none (memory enabled but uninitialised)

memory_audit_stale: scanned: none (audit not run)

strategy: name=graz-tower, last_updated=2026-05-08, sections_filled=6/6
  active_tracks:
    - FM / Lean proof program (research/fm) — INTENTIONALLY_OPEN: polygonal airspace,
      deeper route-bearing (UNBLOCKED by fn-9), multi-unit comms, richer heading,
      richer operational modes; phases 1-4 frozen; route-airspace profile now proof-visible
    - Runtime simulator (protocol / core / sim / controller / pilot / migration) —
      G0/G2 golden anchors; IFR wiring (IFR-1..6) + approach sequencing live verticals;
      Special VFR (CB-8) FM-leads-runtime parity inversion still open
    - Requirements registry (research/tools/requirements-spike) — 46-window slice landed;
      16 remaining clearance/comms manifest-only items not yet promoted; ICAO Doc 8168 cited
      in strategy but not in the ingested set
    - Reviewer / agent infrastructure (agents/, AGENTS.md) — OR-3 autonomous reviewer flagged
      not-started in .plan; non-negotiable commandments in place

## Survivors

### High leverage (1-3)

#### 1. Capture sandbox bootstrap config so future Claude Code agents don't relive today's discovery
**Summary:** Document the gradle.properties proxy + tmpdir + SSL_CERT_FILE + sandbox.network settings into AGENTS.md or a project-local script.
**Leverage:** Small-diff lever because today's bootstrap was a one-off discovery sequence (gradle.properties, sandbox.network.allowLocalBinding, allowUnixSockets, GRADLE_USER_HOME redirect, JAVA_TOOL_OPTIONS, SSL_CERT_FILE pin); impact lands on every future Claude Code session against this repo skipping ~30 min of trial-and-error.
**Size:** S
**Affected areas:** AGENTS.md, scripts/, ~/.claude/settings.json (suggested update)
**Risk notes:** Settings.json is per-user not per-repo; project doc must distinguish 'user must add' from 'project provides'.
**Persona:** first-time-user
**Next step:** /flow-next:interview

#### 2. Regenerate detekt-baseline.xml to absorb 11 pre-existing violations
**Summary:** detekt-baseline.xml is empty (0 IDs); 11 violations across Step.kt / Guard.kt / PilotCognitive.kt break ./gradlew build for everyone.
**Leverage:** Small-diff lever because gradle's `detekt --baseline` regenerates the file in one command; impact lands on `./gradlew build` going green so the strategy's 'multi-module Gradle build + detekt clean' metric becomes enforceable in CI without first refactoring three unrelated files.
**Size:** S
**Affected areas:** detekt-baseline.xml
**Risk notes:** Regenerating freezes existing violations as 'known bad'; consider fixing the 3 MaxLineLength + ReturnCount items inline first, then baselining only the structural ones (NestedBlockDepth, ComplexCondition, LoopWithTooManyJumpStatements).
**Persona:** first-time-user
**Next step:** /flow-next:interview

#### 3. Predicate strengthening — consume airspaceProfile in ClearedToEnterControlZone / SpecialVfrClearance / RemainOutsideControlledAirspace
**Summary:** Make the world-backed airspace family Ready/Issuable predicates use the new fn-9 profile data instead of staying on point-membership.
**Leverage:** Small-diff lever because fn-9 already lands the data and the Ready/Issuable predicates are localised in GreenfieldAirspaceWorldBackedCurrentShape.lean (43, 115); impact lands on the airspace family theorems graduating from 'point-membership-only' to 'profile-aware', which is the strategic payoff of fn-9.
**Size:** M
**Affected areas:** research/fm/lean/CertifiedAtc/GreenfieldAirspaceWorldBackedCurrentShape.lean, research/fm/lean/CertifiedAtc/GreenfieldAirspaceWorldBackedCompound.lean, research/fm/parity_inventory.md
**Risk notes:** Touches the WORLD_BACKED_COMPLETE phase-4 closure; must add alongside (additive Prop conjuncts), never reopen existing theorems.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

### Worth considering (4-7)

#### 4. Polygonal airspace FM branch — bring AirspaceVolume.boundary into proof-visible world
**Summary:** Widen FM extraction past the graph-backed point-set + transition model; close inside-polygon predicate + boundary-crossing semantics. One of five INTENTIONALLY_OPEN strategic branches; depends on fn-9.
**Leverage:** Small-diff lever because the runtime AirspaceVolume.boundary field is already populated and fn-9 just landed the source-extraction prep; impact lands on richer route-vs-airspace interaction theorems and product-realistic CTR/CTA semantics. Carried over from prior prospect (idea #7) and now unblocked.
**Size:** XL
**Affected areas:** research/fm/lean/CertifiedAtc/GreenfieldAirspaceWorldBacked*.lean, research/fm/aviation_world_extraction_contract.md, core/.../ProcedureAndAirspaceModel.kt
**Risk notes:** New world-resolution theory; XL by nature; must be a deliberate semantic widening choice rather than reflex. Pairs naturally with #3 predicate strengthening.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

#### 5. Profile-aware worldBackedAirspaceRouteInteraction? — multi-volume API
**Summary:** Widen the single-volume airspace/route interaction extractor at GreenfieldAirspaceWorldBackedCurrentShape.lean:27 to consume the new Segmented profile natively.
**Leverage:** Small-diff lever because the call site is one function and the Segmented variant already carries its own list of volumes (volume-authoritative); impact lands on segmented-route reasoning becoming first-class instead of degenerating to single-volume in proofs that consume profile data.
**Size:** M
**Affected areas:** research/fm/lean/CertifiedAtc/GreenfieldAirspaceWorldBackedCurrentShape.lean, research/fm/lean/CertifiedAtc/GreenfieldAirspaceWorldBackedCompound.lean
**Risk notes:** Tightly coupled with #3 predicate strengthening — likely the same epic; codex impl-review on fn-9 specifically named this as the next deferred branch.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

#### 6. Investigate + fix LjmbWorldCandidateValidationTest.writesLjmbCurrentCoreValidationReport
**Summary:** Test failure surfaced by today's gradle gate; last touched 369ead7 on 2026-04-30. Root-cause and either fix or document the expected-output drift.
**Leverage:** Small-diff lever because the failure is a single test in `migration/` against a generated report; impact lands on `./gradlew build` going green and the strategy's 'golden tests continuously green' metric extending to migration validation reports.
**Size:** S
**Affected areas:** migration/src/jvmTest/kotlin/.../LjmbWorldCandidateValidationTest.kt, migration/build/reports/tests/jvmTest/...
**Risk notes:** Could be a stale snapshot expectation OR a real validation regression; investigate before either updating the expected output or reverting the upstream change.
**Persona:** first-time-user
**Next step:** /flow-next:interview

#### 7. Drift CI metadata-parity gate against runtime mutation
**Summary:** DeliveredMetadataParityTest is hand-tracked; add a CI assertion that fails when InstructionRules metadata diverges from GreenfieldModel without an inventory bump (carried from prior prospect idea #4).
**Leverage:** Small-diff lever because the DeliveredMetadataParityTest harness is already in place and the change is one mutation-style assertion per metadata field; impact lands on every future change to InstructionRules / GreenfieldModel that would silently break the parity claim being caught at CI time.
**Size:** M
**Affected areas:** core/src/commonTest/kotlin/.../DeliveredMetadataParityTest.kt, research/fm/refinement_inventory.md, research/fm/lean/CertifiedAtc/GreenfieldDeliveredRefinement.lean
**Risk notes:** Drift gates are claimed but never tested-against-mutation; could ship a green-but-meaningless check if not done carefully. Carry-over candidate from prior prospect.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

### If you have the time (8+)

#### 8. Close IFR-1..6 wiring gaps as a coordinated epic
**Summary:** .plan tracks 6 IFR runtime gaps: ClearedTo from Uncleaned, missing arrivalJoinTask, ifr=true plumbing, applyFplAmendment silent error swallow, STAR-to-approach gap, transition-altitude awareness.
**Leverage:** Small-diff lever because each gap is a single seam with a tracked .plan ID and shared IFR mode plumbing; impact lands on running an IFR golden test the same way LowgGoldenTest runs the VFR one — a third golden anchor alongside G0 and G2.
**Size:** L
**Affected areas:** controller/, sim/, protocol/
**Risk notes:** Six items; some may have unstable runtime semantics that block FM closure. Sequence carefully or split into per-gap epics. Carried from prior prospect idea #10.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

#### 9. Special VFR controller flow (CB-8) — runtime issuance + lifecycle
**Summary:** Special VFR clearance is FM-closed (current-shape compound); runtime controller can't issue it yet. Bridge the parity inversion.
**Leverage:** Small-diff lever because FM has already closed Special VFR semantics and the runtime gap is one InstructionRules issuance path plus controller wiring; impact lands on closing the FM-leads-runtime parity inversion (one of the few places where the proof boundary is ahead of the runtime).
**Size:** M
**Affected areas:** controller/src/commonMain/kotlin/.../, protocol/.../InstructionRules.kt
**Risk notes:** FM has it; runtime doesn't issue it. Must keep parity_inventory + drift tests in sync. Carried from prior prospect idea #8.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

#### 10. Stub README.md fleshed into a one-screen project landing page
**Summary:** README.md is 12 chars; new agents and humans land here first and bounce. Add ATC-sim+FM mission, links to AGENTS.md, AGENT_GUIDE, .plan, design docs, run.sh.
**Leverage:** Small-diff lever because one ≤80-line file rewrite covers a single discoverability surface; impact lands on every first-impression visit by humans, agents, and reviewers — a complement to fn-7's STRATEGY.md.
**Size:** S
**Affected areas:** README.md
**Risk notes:** Risk of becoming generic boilerplate; keep it specific to this repo (Graz tower simulator + FM proof program). Carried from prior prospect idea #5 (not promoted).
**Persona:** first-time-user
**Next step:** /flow-next:interview

#### 11. Published VFR procedures into proof-visible world
**Summary:** Currently runtime-only per runtime_model_change_impact.md. Bring published VFR procedures (in AerodromeAip) into the FM extraction so route-bearing theorems can refer to them by id.
**Leverage:** Small-diff lever because the runtime AerodromeAip already carries the procedures and the FM extraction has the eight-family pattern to copy; impact lands on procedure-aware Phase A theorems (currently abstract over arbitrary published-procedure facts).
**Size:** L
**Affected areas:** research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean, research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean, research/fm/aviation_world_extraction_contract.md
**Risk notes:** Strategic widening — must be a deliberate choice over polygonal airspace / multi-unit comms / heading-progress; not picked by the strategy as 'next' yet.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

#### 12. ICAO Doc 8168 (PANS-OPS) ingestion into the requirements registry
**Summary:** Strategy lists Doc 8168 as a citable source for AtcRule / Regulation triples but the 46-window slice does not include it. Run the four-stage adjudication pipeline for the relevant procedure-design sections.
**Leverage:** Small-diff lever because the requirements-spike four-stage pipeline (challenger → defender → bundle gate → judge) generalises across source families with mechanical budget bumps; impact lands on the citation-discipline metric extending to procedure-design rules (approach minima, missed approach geometry).
**Size:** M
**Affected areas:** research/tools/requirements-spike/, research/tools/requirements-spike/registry/, research/tools/requirements-spike/sections/
**Risk notes:** Source documents are dense and visually-formatted (tables, diagrams); Ollama-first ingestion may need extra OCR / table-extraction passes versus the prose-heavy ICAO 4444.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

#### 13. Promote the 16 remaining clearance/communications manifest-only items through ingest → promote → curate → audit → adequacy
**Summary:** RRD-5 from .plan: 16 items in the live manifest are not yet accepted coverage. Run the standard pipeline so the 46-window slice closes from 'declared' to 'landed' across the full manifest.
**Leverage:** Small-diff lever because each item is one manifest section with established four-stage pipeline; impact lands on the RR-17 adequacy evidence catching up to the 46-window manifest declaration so 'requirements-registry mechanical integrity' (strategy metric) becomes truly green.
**Size:** M
**Affected areas:** research/tools/requirements-spike/registry/ollama_first/, research/tools/requirements-spike/quality/curation/, research/tools/requirements-spike/quality/adequacy/
**Risk notes:** Adequacy review is the slowest stage; budget for re-runs if early adjudication produces weak quotes.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

## Rejected

- Multi-unit comms / surveillance FM branch beyond published-handoff — insufficient-signal: Strategy lists five INTENTIONALLY_OPEN active-surface options (polygonal airspace, deeper route-bearing, multi-unit comms, richer heading, richer operational modes) without picking one as next; fn-9 just unblocked deeper-route-bearing successors, which have a stronger 'next-move' signal than multi-unit comms today.
- Richer heading-progress FM branch — heading-hold + issue-time heading — insufficient-signal: Same as multi-unit comms: one of five INTENTIONALLY_OPEN options with no specific signal that this is next over the deeper-route-bearing branch fn-9 just unblocked.
- Theorem-name → Lean-module link checker — insufficient-signal: Low-priority hygiene; same idea was in the prior prospect (#6) and was not promoted by the user. No new signal that it is now urgent.
- Replace plan.md stub or merge it into .plan — insufficient-signal: Duplicates prior prospect idea #9 not promoted; .plan currently functions correctly as the canonical backlog and plan.md stub does not block any new work.
- Operational sectors into proof-visible world — insufficient-signal: Sectors are runtime-only and there is no runtime driver currently consuming sector facts; bringing them into the proof world before any caller is ready is speculative.
- Add a 'reversal invariants' review checklist to AGENTS.md — insufficient-signal: Strategy emphasizes reversal-aware testing but a checklist is process not engineering; concrete reversal tests (in code, in goldens) are the load-bearing artifact, not a bullet list in AGENTS.md.
- Pilot/ATC firewall pass 6+ — insufficient-signal: Without a clear scope of what passes 1-5 (commit 369ead7) covered, this is opaque; needs interview-time clarification before it can be planned.
- Document the post-fn-9 successor-branch dependency graph — insufficient-signal: fn-9.2 already updated six FM docs to name the successor branches (predicate strengthening, profile-aware interaction, polygonal boundary, sectors); a new dependency-graph doc adds another layer above an already-dense FM doc tree without clear payoff.
- Refresh memory store with fn-9-era learnings — other: Memory population belongs in /flow-next:capture or memory-migrate, not as a planning epic; treating 'capture lessons learned' as a planned task confuses two distinct workflows.
