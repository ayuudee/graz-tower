# G2 — Cross-aerodrome VFR transit (LOWG → LJMB)

## Conversation Evidence

> "Continue G2 (cross-aerodrome VFR transit, LOWG → LJMB) implementation in
> the TWR2 ATC simulator at [legacy absolute path elided; repo now lives at
> /Users/andrew/dev/projects/graz-tower]." [user, opening turn]
>
> "G0 (sim/src/jvmTest/.../LowgGoldenTest.kt) is the existing single-aerodrome
> VFR circuit-training golden test — alive, passes, rich assertions. G2 is its
> cross-aerodrome equivalent: aircraft files VFR plan LOWG→LJMB, taxis at LOWG,
> takes off, cruises, contacts LJMB Tower at the destination's first arrival-
> procedure REP, gets joining instructions, lands, taxis to stand." [user]
>
> "Architectural anchors (non-negotiable, from the prior session's conversation):
> 1. Pilot is a reasoning agent, reads world.aerodromes[…] for chart-database-
>    equivalent reference data. ...
> 2. No airspace-polygon containment test, no new PilotEvent leaf, no WorldIndex
>    extensions. ...
> 3. No HandoffTarget.Foreign(aerodromeId). Cross-aerodrome flow is release +
>    procedure-following + initial contact at the procedure's contact REP. ...
> 4. Pilot plan is a snapshot of 'current best plan' — D-G2.4 documents
>    fluid-replanning as future scope; static decomposition for now." [user]
>
> "It's important that the commandments (from agents.md) and in particular the
> architectural requirements for the various firewalls are [upheld]. It's simply
> not a win if we end up cheating. If in doubt, model it like what that object
> (controller, pilot, etc) would have access to in real life." [user, capture
> read-back]

## Goal & Context

Land a runnable G2 golden test of the same shape as G0 (`LowgGoldenTest`),
exercising a cross-aerodrome VFR transit from LOWG to LJMB end-to-end: pilot
files plan, taxis at LOWG, takes off, cruises ~32 NM (~60 km) to the
destination's published contact REP, autonomously contacts LJMB Tower, gets
joining instructions, lands, taxis to stand. [paraphrase]

Cross-aerodrome traffic is modeled as **release + procedure-following +
initial contact at the procedure's contact REP** — not a peer handoff. LOWG
controllers terminate radar service / approve frequency change at the CTR
boundary; the pilot proceeds through Class G uncontrolled airspace (SERA.6001
permits no-comms transit in Class G) without an assigned ATC unit; on
reaching the destination's procedure-published contact REP, the pilot
autonomously initiates contact (CAP 413 §3.29–3.31 initial-call format). The
destination controller already has the strip from filing distribution
(ICAO Doc 4444 §11.4.2.2.2 — FPL goes to the destination aerodrome control
tower); the InitialContact transitions it from `knownStrips` to `Owned`
(already-landed `applyTwoWayCommsEstablished` arm). [user, docs-scout]

**Phase C is committed as `29e064a`** (Transit-mode route planning +
cross-aerodrome contact REP resolution). Phases A, F, G are the remaining-work
surface this epic carries forward. [paraphrase]

## Quick commands

```bash
# Build pilot + sim test source (catches type errors fast)
nix --extra-experimental-features 'nix-command flakes' develop path:. --command \
  ./gradlew :pilot:compileKotlinJvm :sim:compileTestKotlinJvm

# Run the new G2 golden test in isolation
nix --extra-experimental-features 'nix-command flakes' develop path:. --command \
  ./gradlew :sim:jvmTest --tests 'xyz.easiersaid.twr.sim.G2CrossAerodromeVfrTest*'

# Verify G0 unchanged (Commandment 9: G0 remains green throughout)
nix --extra-experimental-features 'nix-command flakes' develop path:. --command \
  ./gradlew :sim:jvmTest --tests 'xyz.easiersaid.twr.sim.LowgGoldenTest*'

# Full pre-commit smoke (all-tests across affected modules)
nix --extra-experimental-features 'nix-command flakes' develop path:. --command \
  ./gradlew :protocol:allTests :pilot:allTests :controller:allTests :sim:allTests
```

## Commandments compliance (AGENTS.md, non-negotiable)

This epic is bound by the project-wide commandments at `AGENTS.md`. The
following are explicitly load-bearing for G2: [user, paraphrase]

- **Commandment 1 — No corners cut.** No skip lists, no `@Suppress`, no silent
  workarounds. If a behavior cannot be implemented correctly, fail loudly.
- **Commandment 2 — No half-baked work.** Every commit leaves the suite green;
  no test exclusions to mask Phase A/F/G integration gaps.
- **Commandment 4 — Tests prove the real job.** Phase F integration assertions
  exercise real cross-aerodrome behavior; structural-property tests that the
  type system already enforces don't earn their keep.
- **Commandment 5 — The pilot owns the plan.** The G2 test harness MUST NOT
  decompose the goal, stitch phases together, or swap goals mid-flight. If the
  harness needs to, the pilot's planning capability is incomplete — fix the
  pilot, don't work around it.
- **Commandment 6 — Protocol is source of truth.** Cross-aerodrome release
  pattern (ICAO Doc 4444 §10.1.4), initial-contact phraseology (CAP 413
  §3.29–3.31), ATIS letter on first contact (CAP 413 §4.195), filing
  distribution (ICAO Doc 4444 §11.4.2.2.2), Class G no-comms transit
  permission (SERA.6001).
- **Commandment 7 — Cite your sources.** Repo paths for in-tree citations:
  `research/txt/icao4444-extracted.txt`, `research/txt/cap413-extracted.txt`,
  `research/txt/sera-923-2012-extracted.txt`,
  `research/txt/slovenia-vfr-extracted.txt`. ICAO Annex 11 is NOT in the repo;
  use CAP 413 §4.195 as the in-repo proxy for the ATIS-on-first-contact rule.

**Firewall integrity is non-negotiable.** [user, capture read-back]

- The pilot reads only what a real pilot would have access to: chart-database-
  equivalent reference data (`AviationWorld` — geometry, fixes, aerodromes'
  published procedures, airspace volumes, FIRs), own kinematic state, own
  filed plan, own visual observation, ATIS frequency. **No live state, no
  controller-state mirror, no peer-aircraft positions, no controller-decision
  peeks.** `FirewallPilotInputTest` and the Phase C
  `FirewallAviationWorldFieldsTest` (with type-argument walking) enforce this;
  D-G2.5 records the future NavComputer-façade defense.
- The controller reads only what a real controller would have access to: radio
  transmissions on the controlled frequency, own sensor readings, own visual
  observation, FlightStrip back-channel from filing distribution. **No pilot
  intent reads, no peer-controller state mirror, no goal-tree peeks.**
  `FirewallObservationTest`, `FirewallNoStaleAircraftIntentTest`,
  `FirewallNoWatchingReadInControllerTest`, `FirewallControllerNoPilotDepTest`,
  `FirewallBeliefWriteTest` enforce this.
- **No `HandoffTarget.Foreign(aerodromeId)`** — cross-aerodrome handoff stays
  syntactically impossible. Phase G's `FirewallNoCrossAerodromeHandoffTest`
  reflection enforces this on `HandoffTarget::class.sealedSubclasses`.
- **Reality-anchored modelling rule:** when in doubt, model what the real-world
  entity would actually have access to. Convenience is not justification for
  widening a firewall surface.

**Test-side firewall anti-patterns to forbid** (call out in Phase F):
[practice-scout]

- **Shared `SimulationState` observed by both controllers** — controllers
  observe their own filtered streams, not a global world.
- **Global event bus the test asserts against** — encourages controllers to
  read from the bus, breaking the firewall.
- **Reading `world.aircraft.position` in test setup to compute the handoff
  tick** — the test author should derive timing from controller-visible state
  (radio call, strip event), not the world's omniscient view. Otherwise a
  firewall-tightening refactor breaks the test.
- **Pilot reading destination runway-in-use via simulator state** — pilot
  reads runway only via filing or ATIS broadcast.

## Architecture & Anchors

Non-negotiable architectural anchors derived from the firewall principle: [user]

- **Pilot reads `AviationWorld` as chart data.** No new `PilotEvent` leaf for
  cross-aerodrome triggers, no airspace-polygon containment test, no
  `WorldIndex` extensions. Procedure designers place contact REPs outside
  controlled airspace by construction — the pilot trusts the procedure's REP
  placement and does NOT perform per-tick polygon-containment tests.
- **Pilot plan is a static snapshot** ("current best plan, based on what I
  know right now"). D-G2.4 records fluid replanning as future scope.
- **LJMB is controlled (TOWER + APP)** during published hours per Slovenia
  AIP. Per `migration/.../LJMB.dat:1054-1055` (Maribor Tower 119.205, Maribor
  Approach 134.305) and `research/txt/slovenia-vfr-extracted.txt:860+`. Phase
  A's resolution is to publish the TOWER role on the LJMB authoring (the
  world-candidate currently has NO `roles` block at all), NOT to rename TOWER
  to AFIS. AFIS modelling is deferred for non-controlled fields. [docs-scout,
  practice-scout]
- **Existing infrastructure relied on** (already landed):
  `applyTwoWayCommsEstablished` knownStrips arm
  (`Step.kt:1545-1553`), `atisLetterForCallInbound` multi-aerodrome lookup,
  Pass 14 cross-aerodrome strip distribution (`AftnRouting.kt:43-89`),
  Pass 7 `ResponsibilityState` typed transitions, FiledPlan with
  `destinationRunway` (Phase B). [paraphrase]

## Boundaries / non-goals

In scope:
- Phase A: `Fixtures.LOWG_LJMB_VFR` multi-aerodrome fixture loader +
  LJMB authoring fix (publish TOWER role).
- Phase F: `G2CrossAerodromeVfrTest` integration test mirroring G0; +
  `firstPilotInitialContactTo(controllerId)` test extension.
- Phase G: `FirewallNoCrossAerodromeHandoffTest` +
  `FixtureAerodromeStaffingDoctrineSpec` + closing doc updates
  (AGENTS.md Testing section, ljmb.md authoring-decisions note,
  plan-file completion header).

Out of scope (deferred):
- LJMB_APPROACH staffing + multi-destination AFTN routing.
- D-G2.4 (fluid replanning + clear-on-destination-change for
  `transitContactRep`).
- D-G2.6 (procedure selection by approach direction; G2 picks
  deterministically by id-sort).
- D-G2.7 (destination tower role lookup beyond `RoleName.TOWER` fallback —
  applies to non-controlled fields like Lesce, not LJMB).
- D-G2.8 (`atisLetterForCallInbound` typed-split — currently `error()` on
  multi-entry non-Transit).
- AFIS-as-RoleName-leaf modelling (relevant only for non-controlled fields;
  LJMB is controlled per AIP).
- D-AUDIT.6.C-FOLLOWUP (route amendment / strip update on amendment).
- D-PASS-17.2 (`firstNotNullOfOrNull` IFR helper sweep — VFR-only path here).
- ICAO Annex 11 PDF acquisition (currently external-citation-only;
  CAP 413 §4.195 is the in-repo proxy).

## Decision context

- **Plan file:** `/home/andrew/.claude/plans/g2-cross-aerodrome-vfr-transit.md`
  — full multi-phase plan with concrete code-level specs per phase, deferments
  register, three-agent review folds, amendments table.
- **Citation accuracy correction (docs-scout):** the plan and earlier capture
  cited CAP 413 §2.5 / Annex 11 §4.3.6 / Annex 2 — these are inaccurate. The
  precise in-repo cites are CAP 413 §3.29–3.31 (initial call) + §4.195 (ATIS
  letter on first contact); ICAO Doc 4444 §11.4.2.2.2 (FPL recipients
  including destination tower); ICAO Doc 4444 §10.1.4 (cross-aerodrome
  release, accurate as cited). The epic spec is updated to use the precise
  cites.
- **Time-band correction (practice-scout):** the plan's "25–45 min" band is
  too tight. ~32 NM (~60 km) LOWG→LJMB cruise at C172 ~110 KTAS = 15–20 min
  cruise alone; stand-to-stand block budget is 50–75 min including taxi out,
  run-up, climb to cruise, descent + TMA entry, pattern + landing, taxi in.
  Phase F's R4 time-band updates to **stand-to-stand wall-clock 50–75 min**
  with margin breakdown documented in code.
- **Phase A authoring-fix shape (repo-scout):** LJMB world-candidate.json has
  NO `roles` block whatsoever; the fixture's `Fixture.validate()` raises
  `RoleNotPublished(TOWER, LJMB)`. The fix is to extend LJMB authoring to
  publish the TOWER role with frequency 119.205 (and optionally APPROACH at
  134.305 if Phase A scope grows; G2 plan defers APPROACH staffing). The
  existing `Fixture` data class is single-aerodrome-shaped (one `aerodromeId`,
  one `frequency`, single-set `controllerRoles`); multi-aerodrome needs a
  parallel `MultiAerodromeFixture` or extension supporting per-aerodrome
  frequency + per-aerodrome role set.
- **Conventions:** Build via the `nix develop` invocation in Quick commands.
  Commit format `G2 Phase X: scope` with `Co-Authored-By: Claude Opus 4.7
  (1M context) <noreply@anthropic.com>` tail. Use `kotlin.test` (not JUnit
  beyond `@Test`); `check(...) { msg }` / `fail(msg)` for assertions; tests
  in `:sim/jvmTest` for integration, `:pilot/jvmTest` for reflection-based
  firewall tests.
- **Memory references** (load-bearing): `feedback_agent_review_process`,
  `feedback_no_corners`, `feedback_firewall_principle`,
  `feedback_review_discipline`, `feedback_pass_scope`,
  `feedback_no_permission_asking`, `feedback_testing_philosophy`,
  `feedback_draft_revisions`, `feedback_plans_review_aware`,
  `feedback_impact_assessment`, `feedback_reality_anchored`.
- **Phase C commit (already landed):** `29e064a` — "G2 Phase C: Transit-mode
  route planning + cross-aerodrome contact REP resolution". 10 files,
  +1202/-77.
- **Predecessor scope (already landed in earlier passes):** `60e7669` (LJMB
  authoring), `b02d191` (Phase B: FiledPlan.destinationRunway,
  RunwayAssignmentSource Option B), `1f8073b` (Phases D + E: multi-aerodrome
  ATIS lookup, applyTwoWayCommsEstablished knownStrips arm), `e38c079`
  (Phase C prep: PilotMission scaffolding cleanup).

## Acceptance

- **R1:** [committed `29e064a`] Pilot's `HighLevelGoal.Transit(destination)`
  mission can plan a route from origin to destination's first published
  contact REP via `world.aerodromes[destination].aip.publishedVfrProcedures`
  (kind priority `ARRIVAL > TRANSIT`, lex id-sort within kind,
  `publishedSequence` falling back to `mapLabels`). [user, paraphrase]
- **R2:** [committed `29e064a`] `PilotMission.transitContactRep:
  Option<PointId>` slice exists, set once by the planner on the first
  `Transit + FLY_DEPARTURE` tick; `isPhysicallyComplete` FLY_DEPARTURE
  Transit arm fires when `mission.transitContactRep ==
  Some(aircraft.positionPoint)`; mission then advances autonomously to
  `arrivalJoinTask` → `CALL_INBOUND` → `InitialContact` to destination
  tower. [user]
- **R3:** [Phase A] `Fixtures.LOWG_LJMB_VFR` multi-aerodrome loader returns
  a merged `AviationWorld` containing both LOWG and LJMB aerodromes;
  controllers staffed: `LOWG_GROUND`, `LOWG_TOWER`, `LOWG_APPROACH`,
  `LJMB_TOWER` (LJMB_APPROACH explicitly out of scope per plan); single
  filing event distributes via `AftnRouting.routeFiledPlan` to LOWG_GROUND
  (Owned) + LJMB_TOWER (knownStrips). Pre-existing `FixtureLoadSpec` /
  `FixtureSanityTest` failures resolved by publishing TOWER role on LJMB
  world-candidate. [user, paraphrase]
- **R4:** [Phase F] `G2CrossAerodromeVfrTest` integration test mirrors
  `LowgGoldenTest`'s depth (~500 lines). Outcome assertions:
  `mission.isComplete`, aircraft phase ∈ {Parked, AtStand}, altitude 0,
  `positionPoint` ∈ LJMB stand points (or stand-equivalent taxiway points).
  Plus: filing-cardinality pin (== 2 events to 2 distinct sides), pre-radio
  `activeRunway = Some(RunwayAssignment(RWY_14, Filing))` pin,
  cross-aerodrome handoff window with `midGapStates.isNotEmpty()` +
  `tContact - tRelease >= 30_000ms` + no-LOWG-Owned-anywhere-in-window pin
  + `∃ snapshot` with LJMB knownStrips, post-contact
  `responsibilities[LJMB_TWR][ac] is Owned`, multi-aerodrome ATIS pins
  (`firstInitialContactToLowg.atisCode == 'A'`,
  `firstInitialContactToLjmb.atisCode == 'B'`), autonomous-contact
  provenance pin (no `ContactFrequency` directing to LJMB anywhere in
  records), **stand-to-stand wall-clock time band 50–75 min** (~32 NM /
  ~60 km cruise; margin breakdown in code comment per practice-scout).
  Snapshot indexing anchors on semantic events, not absolute ticks. [user,
  practice-scout]
- **R5:** [Phase G] `FirewallNoCrossAerodromeHandoffTest` reflection asserts
  `HandoffTarget::class.sealedSubclasses.mapNotNull { it.simpleName }.toSet()
  == setOf("Peer", "Released")`. [user]
- **R6:** [Phase G] `FixtureAerodromeStaffingDoctrineSpec` cardinal-claim
  assertion: exact-set match on staffed `(role, aerodrome)` pairs =
  `{GROUND/LOWG, TOWER/LOWG, APPROACH/LOWG, TOWER/LJMB}`. [user]
- **R7:** Firewall integrity preserved. Across all phases, no widening of
  `PilotInput`, `AviationWorld`, controller `BeliefState`, or any other
  allowlisted surface that would let a pilot or controller read state they
  wouldn't have access to in real life. The existing firewall test suite
  (`FirewallPilotInputTest`, `FirewallAviationWorldFieldsTest`,
  `FirewallObservationTest`, `FirewallNoStaleAircraftIntentTest`,
  `FirewallNoWatchingReadInControllerTest`,
  `FirewallControllerNoPilotDepTest`, `FirewallBeliefWriteTest`, plus
  Phase G's `FirewallNoCrossAerodromeHandoffTest`) must remain green. The
  test-side anti-patterns (shared SimulationState, global event bus,
  world.aircraft.position in test setup, pilot reading destination
  runway-in-use via simulator state) are explicitly forbidden. **Convenience
  is not justification for widening.** When in doubt, model what the entity
  would actually have access to in real life. [user, capture read-back]
- **R8:** Per-phase discipline every phase: PLAN → REVIEW (3 agents in
  parallel: impact, fp-review, test-review via Agent tool with
  `subagent_type`; clean contexts, no priming, no pre-justification per
  `feedback_agent_review_process`) → ADAPT (fold every must-fix and
  should-fix unless absolutely defensible; document the reasoning) →
  IMPLEMENT → POST-IMPL REVIEW (3 agents again) → ADDRESS findings →
  COMMIT → NEXT PHASE. [user]
- **R9:** Commandments compliance (AGENTS.md). No corners cut; no half-baked
  work; tests prove the real job; the pilot owns the plan; protocol is
  source of truth (citations: ICAO Doc 4444 §10.1.4 cross-aerodrome release;
  ICAO Doc 4444 §11.4.2.2.2 filing distribution; CAP 413 §3.29–3.31
  initial-call format; CAP 413 §4.195 ATIS letter on first contact;
  SERA.6001 Class G no-comms permission). G0 (`LowgGoldenTest`) remains
  green throughout. [user, AGENTS.md, docs-scout]
- **R10:** Pre-existing failures resolved. `FixtureLoadSpec` and
  `FixtureSanityTest` fail on master with `RoleNotPublished(role=TOWER,
  aerodrome=LJMB)`. Phase A's LJMB authoring fix (publish TOWER role with
  frequency 119.205 in `cad/airports/rendered/ljmb/world-candidate.json`)
  unblocks both. Closing doc updates: AGENTS.md Testing section gains a
  G0/G2 golden-test paragraph; `wiki/data-sources/ljmb.md` gains a TOWER-
  vs-AFIS resolution entry in the authoring-decisions section; the
  `~/.claude/plans/g2-cross-aerodrome-vfr-transit.md` file gains a
  `Status: COMPLETE` header on epic completion. [docs-gap-scout]

## Early proof point

Task `fn-5-g2-cross-aerodrome-vfr-transit-lowg-ljmb.1` (Phase A) validates
the core approach: a multi-aerodrome merged `AviationWorld` with both LOWG
and LJMB authored, all four controllers staffed, single FiledPlan
distributing through `AftnRouting.routeFiledPlan` to both sides. If this
fails, the cross-aerodrome modelling shape is wrong (LJMB authoring
incomplete, `Fixture` data class too narrow, or `mergeAviationWorlds`
preconditions unmet) — re-evaluate before continuing to Phase F's
integration test.

## Requirement coverage

| Req | Description | Task(s) | Gap justification |
|-----|-------------|---------|-------------------|
| R1  | Transit route planning to destination procedure REP | — | Committed `29e064a` |
| R2  | `transitContactRep` slice + Transit-aware physical-completion | — | Committed `29e064a` |
| R3  | `Fixtures.LOWG_LJMB_VFR` multi-aerodrome loader | fn-5-g2-cross-aerodrome-vfr-transit-lowg-ljmb.1 | — |
| R4  | `G2CrossAerodromeVfrTest` integration test | fn-5-g2-cross-aerodrome-vfr-transit-lowg-ljmb.2 | — |
| R5  | `FirewallNoCrossAerodromeHandoffTest` reflection | fn-5-g2-cross-aerodrome-vfr-transit-lowg-ljmb.3 | — |
| R6  | `FixtureAerodromeStaffingDoctrineSpec` cardinal-claim | fn-5-g2-cross-aerodrome-vfr-transit-lowg-ljmb.3 | — |
| R7  | Firewall integrity preserved across all phases | fn-5-g2-cross-aerodrome-vfr-transit-lowg-ljmb.{1,2,3} | Cross-cutting; each task verifies its own firewall surface |
| R8  | Per-phase 3-agent review discipline | fn-5-g2-cross-aerodrome-vfr-transit-lowg-ljmb.{1,2,3} | Process, not output — applied per task |
| R9  | Commandments compliance + G0 unbroken | fn-5-g2-cross-aerodrome-vfr-transit-lowg-ljmb.{1,2,3} | Cross-cutting; each task runs G0 + commandments check |
| R10 | Pre-existing failures resolved + closing doc updates | fn-5-g2-cross-aerodrome-vfr-transit-lowg-ljmb.{1,3} | Authoring fix in .1 unblocks fixture tests; doc updates land in .3 |
