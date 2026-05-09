---
title: Lean/FM next-step ideas (with adjacent runtime + DX context)
date: "2026-05-08"
focus_hint: "Read ./research for the lean/fm work. There are various docs, plans, etc, plus also perhaps plans in .plan and design docs, wiki, etc. Read it all and get up to speed. There should be a very clear idea of what we're trying to do."
volume: "21"
survivor_count: "12"
rejected_count: "9"
rejection_rate: "0.43"
artifact_id: lean-fm-next-steps-2026-05-08
promoted_ideas: [1, 2, 3]
promoted_to: {"1": [fn-7-author-strategymd-anchoring-target], "2": [fn-8-rewrite-28-absolute], "3": [fn-9-lift-fm-extraction-to-consume-runtime]}
status: active
---

## Focus

**Original hint** (verbatim):

> Read ./research for the lean/fm work. There are various docs, plans, etc, plus also perhaps plans in .plan and design docs, wiki, etc. Read it all and get up to speed. There should be a very clear idea of what we're trying to do.

**Resolved focus**: lean/fm next-step ideas, with adjacent .plan / docs / wiki context.
**Focus kind**: concept (not risk-flavored — senior-maintainer + first-time-user personas).

## Grounding snapshot

focus_hint: lean/fm work + .plan + design docs + wiki (full repo context)
focus_kind: concept
date: 2026-05-08

git_log_30d: ~50 commits across FM docs, runtime sim, requirements ingestion
top:
  - sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/Step.kt (33 commits)
  - research/fm/lean/README.md (29)
  - research/fm/README.md (28)
  - research/fm/PROJECT_STATUS.md (28)
  - research/fm/lean/CertifiedAtc.lean (26)
  - research/fm/AGENT_GUIDE.md (24)
  - controller/Controller.kt (19)
  - sim/PilotCognitive.kt (17)
  - core/clearance/CompletionEvaluation.kt (17)
  - protocol/Instruction.kt (15)

open_epics: 2
  - fn-5-g2-cross-aerodrome-vfr-transit-lowg-ljmb (G2 LOWG → LJMB cross-aerodrome VFR transit)
  - fn-6-kinematic-position-on (kinematic position on AircraftObservation)
done_epics: fn-1 (review research), fn-2 (resume Lean), fn-3 (FM campaign), fn-4 (richer airspace geometry)

changelog_recent: scanned: none (no CHANGELOG.md)

memory_matches: scanned: none (memory enabled but not initialised — `flowctl memory init` not yet run)

memory_audit_stale: scanned: none (audit not run)

strategy: scanned: none (no STRATEGY.md signal)

fm_state_summary:
  Safety-complete (N₀): closed for scoped surface
  Full-brief complete: closed for scoped surface
  98 Lean modules; observation-regression queue exhausted
  Phase 1-4 widening closures frozen (do not reopen)
  parity_inventory + refinement_inventory + GreenfieldDeliveredRefinement.lean current
  INTENTIONALLY_OPEN branches:
    - polygonal/continuous airspace beyond graph-backed point-set+transition
    - deeper route-bearing beyond graph-backed published-procedure
    - multi-unit comms beyond immediate radio + published-handoff
    - heading-hold + issue-time heading beyond observed turn-progress
    - richer operational mode semantics
  runtime ahead of FM: VfrRoute.airspaceProfile, AirspaceVolume.memberPoints+boundaries,
    AerodromeAip operational sectors, published VFR procedures — not yet proof-visible

plan_state:
  .plan: active backlog tracker; IFR-1..6 (IFR wiring), CB-1..10 (controller backlog),
    P-1..4 (protocol parked), OR-3 (adversarial reviewer not started)
  plan.md: 2-line stub from 11 Apr ending mid-sentence
  README.md: 12 chars ("# graz-tower")
  AGENTS.md: 265 lines of non-negotiable commandments

doc_drift:
  research/fm/*.md last_updated: April 13-21 (3+ weeks behind today)
  ~78 files (research/fm, docs/, wiki/, cad/airports, requirements-spike) reference legacy absolute path /home/andrew/dev/projects/twr2/
    (repo lives at /Users/andrew/dev/projects/graz-tower; original prospect-time count was 28 — corrected post-scout)

## Survivors

### High leverage (1-3)

#### 1. Author STRATEGY.md anchoring target problem + tracks
**Summary:** No STRATEGY.md exists; flow-next prospect/plan/capture all benefit. Project has at least four parallel tracks worth naming: FM/Lean, runtime sim, requirements registry, reviewer infra.
**Leverage:** Small-diff lever because one new ≤200-line file at the repo root anchors four explicit tracks already named across docs; impact lands on every future /flow-next:plan, prospect, capture, and interview run.
**Size:** S
**Affected areas:** STRATEGY.md, .flow/
**Risk notes:** Strategy docs rot fast if not maintained; keep tracks specific (active vs done) so it's not aspirational.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

#### 2. Rewrite legacy absolute path refs across ~78 docs/JSON + 3 code files (originally counted as 28)
**Summary:** Repo lives at graz-tower; FM docs, design docs, wiki entries, CAD authoring boards, and requirements-spike registry/golden/downstream JSONs still point at the old twr2 absolute path. Cross-machine, cross-repo-name links are dead. Original prospect estimate of 28 files turned out to be ~78 after a fuller repo scout.
**Leverage:** Small-diff lever because the path string is unique and grep-replaceable across ~78 files (75 doc/JSON via sed strip-the-prefix; 3 code files needing cwd-independent rewrites — Python `Path(__file__).parent`, bash `git rev-parse --show-toplevel`); impact lands on every new agent or human reader of research/fm, docs/design, wiki/, cad/airports/, and the requirements-spike outputs.
**Size:** S
**Affected areas:** research/fm/*.md, docs/design/*.md, wiki/data-sources/*.md, wiki/design-decisions/*.md, cad/airports/*.md + *_underlay_placement.json, research/tools/requirements-spike/{golden,downstream,registry}/**/*.json + RUNBOOK.md + 2 *.py files
**Risk notes:** Mechanical but boring; risk is replacing the wrong substring (e.g. inside code blocks). Historical audit JSONs under requirements-spike/quality/curation/ and quality/adequacy/.../sample_manifest.json are excluded — rewriting them would alter an audit trail.
**Persona:** first-time-user
**Next step:** /flow-next:interview

#### 3. Lift FM extraction to consume runtime VfrRoute.airspaceProfile (InVolume / InClass / Segmented)
**Summary:** Close the documented runtime-vs-FM gap: extraction currently treats VFR routes as waypoint sequences only; bring airspaceProfile into proof-visible world. Enables polygonal airspace + richer route-bearing.
**Leverage:** Small-diff lever because the runtime types and AviationWorld extraction contract already exist and the proof-side widening reuses the existing GreenfieldAirspaceWorldBacked* shape; impact lands on unblocking polygonal airspace and richer route-bearing widening.
**Size:** M
**Affected areas:** research/fm/aviation_world_extraction_contract.md, research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean, research/fm/route_bearing_scope.md
**Risk notes:** Route-bearing extraction surface is delivered but narrow; widening is non-cosmetic — interacts with the airspace branch.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

### Worth considering (4-7)

#### 4. Drift CI metadata-parity gate against runtime mutation
**Summary:** Existing DeliveredMetadataParityTest is hand-tracked; add a CI assertion that fails when InstructionRules metadata diverges from GreenfieldModel without an inventory bump.
**Leverage:** Small-diff lever because the DeliveredMetadataParityTest harness is already in place and the change is one mutation-style assertion per metadata field; impact lands on every future change to InstructionRules / GreenfieldModel that would silently break the parity claim.
**Size:** M
**Affected areas:** core/src/commonTest/kotlin/xyz/easiersaid/twr/core/clearance/DeliveredMetadataParityTest.kt, research/fm/refinement_inventory.md, research/fm/lean/CertifiedAtc/GreenfieldDeliveredRefinement.lean
**Risk notes:** Drift gates are claimed but never tested-against-mutation; could ship a green-but-meaningless check if not done carefully.
**Persona:** adversarial-reviewer
**Next step:** /flow-next:interview

#### 5. Stub README.md fleshed into a one-screen project landing page
**Summary:** README.md is 12 chars; new agents and humans land here first and bounce. Add ATC-sim+FM mission, links to AGENTS.md, AGENT_GUIDE, .plan, design docs, run.sh.
**Leverage:** Small-diff lever because one ≤80-line file rewrite covers a single discoverability surface; impact lands on every first-impression visit by humans, agents, and reviewers.
**Size:** S
**Affected areas:** README.md
**Risk notes:** Risk of becoming generic boilerplate; keep it specific to this repo (Graz tower simulator + FM proof program).
**Persona:** first-time-user
**Next step:** /flow-next:interview

#### 6. Theorem-name → Lean-module link checker
**Summary:** refinement_inventory.md and the Lean registry hand-reference theorem names; add a small Lean elaborator test (or grep guard) that fails when a referenced theorem disappears from CertifiedAtc/*.lean.
**Leverage:** Small-diff lever because Lean's elaborator already resolves theorem names so a single CertifiedAtc/RefinementChecks.lean import block can fail loudly on missing aliases; impact lands on refinement_inventory.md and GreenfieldDeliveredRefinement.lean staying honest.
**Size:** S
**Affected areas:** research/fm/refinement_inventory.md, research/fm/lean/CertifiedAtc/GreenfieldDeliveredRefinement.lean, scripts/
**Risk notes:** Easy to ship a passes-locally-fails-on-CI check if hostile environment differences leak in.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

#### 7. Polygonal airspace FM branch — bring AirspaceVolume.boundaries into proof-visible world
**Summary:** Widen FM extraction past the graph-backed point-set+transition model; close inside-polygon predicate + boundary-crossing semantics. One of five INTENTIONALLY_OPEN branches.
**Leverage:** Small-diff lever because the runtime AirspaceVolume.boundaries field is already populated and the FM widening reuses the existing GreenfieldAirspaceWorldBacked* shape; impact lands on richer route-vs-airspace interaction theorems and product-realistic CTR/CTA semantics.
**Size:** XL
**Affected areas:** research/fm/lean/CertifiedAtc/GreenfieldAirspaceWorldBacked*.lean, research/fm/aviation_world_extraction_contract.md, core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/ProcedureAndAirspaceModel.kt
**Risk notes:** New world-resolution theory; one of five INTENTIONALLY_OPEN branches — pick deliberately, not by reflex. Depends on #3 extraction widening.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

### If you have the time (8+)

#### 8. Special VFR controller flow (CB-8) — runtime issuance + lifecycle
**Summary:** Special VFR clearance is FM-closed (current-shape compound); runtime controller can't issue it yet. Bridge the parity inversion.
**Leverage:** Small-diff lever because FM has already closed Special VFR semantics and the runtime gap is one InstructionRules issuance path plus controller wiring; impact lands on closing the FM-leads-runtime parity inversion.
**Size:** M
**Affected areas:** controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/, protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/InstructionRules.kt
**Risk notes:** FM has it; runtime doesn't issue it. Must keep parity_inventory + drift tests in sync.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

#### 9. Replace plan.md stub or merge it into .plan
**Summary:** plan.md is 2 lines, ending mid-sentence dated 11 Apr — confusing as a top-level file when .plan is the canonical backlog.
**Leverage:** Small-diff lever because plan.md is two lines and the canonical backlog already lives in .plan; impact lands on a confusing mixed-message file at the repo root.
**Size:** S
**Affected areas:** plan.md, .plan
**Risk notes:** May contain a half-formed thought worth recovering; ask the user before deleting outright.
**Persona:** first-time-user
**Next step:** /flow-next:interview

#### 10. Close IFR-1..6 wiring gaps as a coordinated epic
**Summary:** .plan tracks 6 IFR runtime gaps: ClearedTo from Uncleaned, missing arrivalJoinTask, ifr=true plumbing, applyFplAmendment silent error swallow, STAR-to-approach gap, transition-altitude awareness.
**Leverage:** Small-diff lever because each gap is a single seam with a tracked .plan ID and shared IFR mode plumbing; impact lands on running an IFR golden test the same way LowgGoldenTest runs the VFR one.
**Size:** L
**Affected areas:** controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/, sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/, protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/
**Risk notes:** Six items; some may have unstable runtime semantics that block FM closure. Sequence carefully or split into per-gap epics.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

#### 11. Richer heading-progress FM branch — heading-hold + issue-time heading
**Summary:** Close the INTENTIONALLY_OPEN heading branch beyond observed turn-progress: heading-hold persistence, issue-time capture, broader vector manoeuvres.
**Leverage:** Small-diff lever because TurnByDegrees is already closed on the observed-turn-progress model and heading-hold reuses the existing vector-state machinery; impact lands on closing one of the five INTENTIONALLY_OPEN branches.
**Size:** L
**Affected areas:** research/fm/lean/CertifiedAtc/GreenfieldRouteControl*.lean, core/src/commonMain/kotlin/xyz/easiersaid/twr/core/clearance/CompletionEvaluation.kt, core/src/commonMain/kotlin/xyz/easiersaid/twr/core/resolution/InstructionResolution.kt
**Risk notes:** Vector-state semantics already partially world-backed; widening must avoid reopening the closed TurnByDegrees branch.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

#### 12. Multi-unit comms / surveillance FM branch beyond published-handoff
**Summary:** Close coordination/jurisdiction/surveillance theorems beyond the current immediate radio/transponder + published-handoff model.
**Leverage:** Small-diff lever because the published-handoff jurisdiction layer is already closed and multi-unit coordination reuses the same world-backed extraction shape; impact lands on richer surveillance/jurisdiction theorems.
**Size:** L
**Affected areas:** research/fm/lean/CertifiedAtc/GreenfieldCommunications*.lean, research/fm/lean/CertifiedAtc/GreenfieldRadioJurisdictionWorldBacked.lean
**Risk notes:** Multi-unit coordination is one of the harder semantic widenings; needs a stable runtime story first or the proof will lead the runtime.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

## Rejected

- Refresh April-21 FM status doc dates for May-08 reality — insufficient-signal: Cosmetic doc-date refresh; should land alongside real proof or runtime work, not as a standalone next move.
- Onboarding shortcut: 'first 30 minutes in research/fm' digest — other: Adds another doc layer above AGENT_GUIDE.md instead of slimming AGENT_GUIDE itself; duplicates the front-of-line problem.
- Slim AGENTS.md commandments digest at top — other: Same shape as the rejected onboarding digest; commandments are non-negotiable and abridging risks softening the bar.
- Wake turbulence separation (CB-2): ICAO 4444 §5.4.2 / §5.8 — out-of-scope: Controller backlog item; user's focus hint pointed at lean/fm not at runtime separation widening.
- Start OR-3 autonomous adversarial reviewer loop — out-of-scope: Multi-week reviewer infrastructure; orthogonal to lean/fm focus and not the right next move from this prospect.
- Plan-maintenance rule self-enforcement: drop DONE entries past 7-day window — insufficient-signal: One-line cleanup of .plan; not a 'next move' worth a candidate slot.
- Operational sectors + published VFR procedures into FM extraction — other: Overlaps the surviving #3 extraction-widening track on a sibling runtime object; bundle at promote time rather than ranking separately.
- Mach + ClimbTo / DescendTo into the scoped Viable_sep continuation set — too-large: XL widening of the load-bearing Viable_sep theorem; safety_complete_scope.md explicitly excludes Mach + altitude modifiers because the local viability story can't carry them yet — keeping in scope would risk regressing the closed claim.
- Capture user's incomplete plan.md thought via /flow-next:interview — other: Duplicates the surviving plan.md cleanup candidate with a thinner angle; user can run /flow-next:interview directly on plan.md as a follow-up.
