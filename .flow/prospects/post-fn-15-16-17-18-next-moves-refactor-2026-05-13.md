---
title: Post fn-15/16/17/18 next moves — refactor / new territory / .plan
date: "2026-05-13"
focus_hint: "refactor for obvious gain / new territory (IFR, multi-aircraft, stress test) / working through .plan"
volume: "25"
survivor_count: "15"
rejected_count: "10"
rejection_rate: "0.4"
artifact_id: post-fn-15-16-17-18-next-moves-refactor-2026-05-13
promoted_ideas: [1]
promoted_to: {"1": [fn-19-fix-d-world2-pre-existing]}
status: active
---

## Focus

User-supplied direction: "Rooted in either refactoring something materially for a very obvious gain, moving into new territory wrt to supporting more scenarios (IFR, more aircraft; potentially a stress test), or working through the plan."

Three concept-aligned buckets ground the generation pass:
1. **Material refactoring for obvious gain** — pick up the structural debt fn-15/16/17/18 exposed but didn't close.
2. **New territory** — IFR wiring (IFR-1..6), multi-aircraft cross-aerodrome, approach sequencing, additional pilot-reactive triggers.
3. **`.plan` items** — operational backlog that's been parked but real (M8 launcher, A9 manual routes, OR-3 adversarial loop, RR-* registry work).

## Grounding snapshot

focus_hint: post-fn-15/16/17/18 next moves
focus_kind: concept (three buckets above)

git_log_30d: top 15 touched files (out of ~150)
  - sim/.../Step.kt (39 commits) — DES core
  - research/fm/PROJECT_STATUS.md (31), research/fm/README.md (29), lean/README.md (27), AGENT_GUIDE.md (25) — Lean program docs
  - controller/.../Controller.kt (30), bdi/Guard.kt (27), procedure/TowerArrival.kt (26), TowerDeparture.kt (18) — controller pipeline hot
  - AGENTS.md (26), .plan (25)
  - sim/.../LowgGoldenTest.kt (23) — foundational golden
  - research/fm/lean/CertifiedAtc.lean (23) — Lean entry
  - pilot/.../PilotCognitive.kt (19), controller/.../ControllerTypes.kt (19)

open_epics: 1
  - fn-10-complete-remaining-source-window — Registry hardening (4 tasks all `todo`; predates this session)

strategy: graz-tower, last_updated 2026-05-08 (5 days stale; 4 epics shipped since)
  target_problem: regulation-grounded ATC sim — every controller behaviour cites a regulation, every safety-critical decision Lean-certified or fail-loud, every state transition has reversal tested before forward
  tracks:
    FM/Lean proof program — split certifiers (runway/surface/air-path/separation), Safety-complete (N₀) + Full-brief complete closed; next: polygonal airspace, deeper route-bearing, multi-unit comms, richer heading
    Runtime simulator — pure-fold DES, BDI controller, G3a trilogy + react crosswind + react tailwind closed; **strategy explicitly: "IFR wiring (IFR-1..6) and approach sequencing are the next live verticals"**
    Requirements registry — Ollama-first, 46-window slice landed
    Reviewer / agent infrastructure — multi-agent review; **OR-3 (autonomous adversarial loop) flagged not-started**

.plan operational backlog (named items):
  - IFR-1..6: ClearedTo-from-Uncleaned fails, arrivalJoinTask missing, createMission ifr=true never passed, applyFplAmendment silently discards AmendmentError, STAR-to-approach gap, altitude-constraint resolution
  - M5: LJMB runtime SID candidate/test mismatch keeps `./gradlew build` red — pre-existing D-WORLD.2 cross-epic carve-out
  - M8: no main()/runnable launcher
  - A9: manual routes in LowgSpike + ArrivalVerticalTest (blocked on IFR wiring)
  - B7: APP→TWR handoff at downwind phraseologically wrong (blocked on APP sequencing)
  - F1: holding-pattern semantics provisional (blocked on geometry)
  - CB-1..6: future-scope controller backlog (approach sequencing, wake turbulence, LVPs, intersection departures, runway inspection, IFR ground flow)
  - OR-3: autonomous adversarial loop not started

docs/deferments.md: 98 active + 37 archive (135 total post-fn-18)
  - 25× D-AUDIT.* "blocked on prerequisite" (ATIS multi-aerodrome / voice-style / separate-frequency cluster; mixed-mode parallel runways; per-step TIMED durations; etc.)
  - D-PASS-pilot-world-strip-dynamic-state (filed by fn-16.1 as long-term structural enforcement of pilot firewall)
  - D-PASS-g3a-react-* siblings: gust, multi-aircraft-crosswind, combined-wind-vector, ATIS-cadence, condition-corrections, personal-minimums
  - D-PASS-g3b-react-cross-aerodrome-{crosswind,tailwind}
  - D-WORLD.2 — LjmbWorldCandidateValidationTest pre-existing failure (cross-epic carve-out from fn-5/6.2/9.2/11.2/16.1/16.2)

recent memory captures (this session, 6× test-discipline + 2 build/integration):
  - tx-start-vs-mint-id discipline (sim-test-pins-must-compare-against)
  - inherited-sim-test-gate-semantics (gate carried from crosswind didn't transfer to tailwind axis)
  - r9-style-allowlist-guards-must-key-by (test-discipline)
  - pre-existing-test-failures-need-named-register-entries
  - compound-predicate-test-assertions (TouchAndGo absence assertion failure mode)
  - tests-must-anchor-on-observed-post-state
  - rich-world-domain-entity-field-needs (fn-16.1 lesson)
  - renumbering-grep-walk-must-span-full (fn-17 lesson)
  - long-spec-flow-next-tasks-review-loop (fn-18.3 lesson at asymptote)
  - recognition/apply-pipelines-need-mission-shape-decoupling
  These are scattered under bug/test-failures and bug/build-errors; not yet promoted to feedback_* durable conventions.

## Survivors

### High leverage (1-3)

#### 1. Fix D-WORLD.2 pre-existing :migration:jvmTest failure (LjmbWorldCandidateValidationTest 9-vs-5 SIDs)
**Summary:** Reconcile the LJMB candidate to the 9-SID expectation OR document the 5-SID acceptance and update the test; unblocks `./gradlew build`.
**Leverage:** Small-diff lever because the failure is one test's assertion against one regenerated artifact; impact lands on every commit's broad build (currently red on a side path that 5+ epics have carved out).
**Size:** S
**Affected areas:** migration/, .plan (D-WORLD.2 archive flip), docs/deferments.md
**Risk notes:** If the 5-SID acceptance is the right answer, the test+report must agree; if the 9-SID restoration is right, the LJMB candidate generator may need a bug fix.
**Persona:** first-time-user
**Next step:** /flow-next:interview

#### 2. Refresh STRATEGY.md (5 days stale; 4 epics shipped since 2026-05-08)
**Summary:** Update the Runtime simulator track to mention the four-bucket deferment register, Aerodrome.weather entity-field migration, and the G3a-react axis closure; refresh last_updated.
**Leverage:** Small-diff lever because the track sections take ≤2 paragraphs of edit; impact lands on every downstream /flow-next:prospect, /flow-next:plan, and /flow-next:interview that reads strategy for grounding (the husk-vs-presence gate at sections_filled ≥ 1 already passes).
**Size:** S
**Affected areas:** STRATEGY.md
**Risk notes:** Drift in the wrong direction if the user disagrees on which surfaces are 'the next live verticals' now — confirm before editing.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

#### 3. Add `main()` runnable launcher (close .plan M8)
**Summary:** Single-aircraft sim main entrypoint so anyone — agent or human — can run the sim without a test harness; pins the G0 / G1 shape.
**Leverage:** Small-diff lever because it's one Kotlin main() + Gradle application-plugin wiring; impact lands on every onboarding session, every demo, every adversarial-loop iteration that wants to drive the sim end-to-end.
**Size:** S
**Affected areas:** sim/src/jvmMain/ (new), sim/build.gradle.kts, AGENTS.md (quick-start mention)
**Risk notes:** Choice of seed / aerodrome / scenario fixture has to be deterministic for the launcher to be debuggable; pick one rather than 'configurable' on v1.
**Persona:** first-time-user
**Next step:** /flow-next:interview

### Worth considering (4-7)

#### 4. Promote 6+ session memory captures into durable `feedback_*` conventions
**Summary:** The session shipped 10+ bug/test-failures and bug/build-errors entries (mint-id discipline, inherited-gate semantics, compound-predicate assertions, etc.). Consolidate into feedback files so reviewer subagents key off them.
**Leverage:** Small-diff lever because each consolidation is a 1-2 paragraph promotion of an existing memory file; impact lands on every future impl-review (codex backend already sources feedback context for review-discipline).
**Size:** S
**Affected areas:** .flow/memory/feedback_*.md (new entries), .flow/memory/bug/test-failures/* (referenced), MEMORY.md (index)
**Risk notes:** Risk of over-promoting one-off lessons — pick only the ones with a clear forward-applicable rule.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

#### 5. Tighten R15 deferment-register regex tooling (eliminate class-iii prefix-of-canonical workarounds)
**Summary:** Replace the greedy `[A-Za-z0-9_.-]*[A-Za-z0-9_]` regex with a word-boundary-aware Python script that consults `docs/deferments.md` directly; removes the 8 documented exclusions fn-18.3 had to enumerate.
**Leverage:** Small-diff lever because the script is ~50 lines of stdlib Python plus a `.flow/bin/`-style wrapper; impact lands on every future deferment audit (including fn-26 / fn-34 audit refreshes and the fn-18.3 R15 gate itself for re-runs).
**Size:** S
**Affected areas:** .flow/bin/ (new audit script), docs/deferments-CONVENTION.md (note the new tooling), fn-18.3 R15 acceptance language (reference the script)
**Risk notes:** Risk of over-engineering — if the tooling needs more than a Python script, defer; the prose-level documented-exclusion approach is acceptable per spec.
**Persona:** adversarial-reviewer
**Next step:** /flow-next:interview

#### 6. Land `:pilot/PilotAviationWorld` projection (close D-PASS-pilot-world-strip-dynamic-state)
**Summary:** Typed projection in `:pilot` that hides `Aerodrome.weather` and other entity-level dynamic state from pilot reads; structurally enforces the pilot firewall fn-16.1 strengthened via KDoc.
**Leverage:** Small-diff lever because the projection is one new data class + one builder + reader migration in `PilotWiring.kt` (well-isolated boundary); impact lands on the entire pilot firewall — codifies in the build graph what fn-16.1 only documented in KDoc.
**Size:** M
**Affected areas:** pilot/src/commonMain/.../PilotAviationWorld.kt (new), sim/.../PilotWiring.kt (reads change), PilotInput.kt (signature update), docs/deferments.md (close D-PASS-pilot-world-strip-dynamic-state)
**Risk notes:** Adding a new type that mirrors `AviationWorld` risks divergence over time; the projection must be co-located with `AviationWorld` so a field addition there forces a decision here.
**Persona:** adversarial-reviewer
**Next step:** /flow-next:interview

#### 7. IFR-1: Fix `ClearedTo` from `Uncleaned` (.plan IFR-1)
**Summary:** Smallest of the IFR-1..6 unblocking items per .plan; lays the first IFR wiring foundation the strategy explicitly names as the next live vertical.
**Leverage:** Small-diff lever because IFR-1 is the simplest of the six (state-machine guard fix); impact lands on IFR-2/3/4/5 unblock cascade (createMission ifr=true, arrivalJoinTask, AmendmentError, STAR-to-approach).
**Size:** M
**Affected areas:** controller/.../ControllerTypes.kt, controller/.../Controller.kt, .plan (IFR-1 → DONE)
**Risk notes:** May expose deeper issues with the clearance lifecycle that .plan IFR-2..6 implicitly assume away; budget for scope creep.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

### If you have the time (8+)

#### 8. IFR-3: `createMission` passes `ifr=true` (.plan IFR-3)
**Summary:** Sibling to IFR-1; unblocks IFR mission generation. Smallest of the IFR-1..6 set per .plan.
**Leverage:** Small-diff lever because the flag-plumbing is one parameter through the mission factory; impact lands on every IFR mission downstream of `createMission`.
**Size:** S
**Affected areas:** pilot/.../PilotMission.kt, .plan (IFR-3 → DONE)
**Risk notes:** Behaviour changes underneath existing pilot-side tests; needs an audit of which tests assume `ifr=false`.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

#### 9. IFR-2: IFR arrival HTN gains `arrivalJoinTask()` (.plan IFR-2)
**Summary:** Adds the missing IFR arrival HTN task; precondition for a G2-IFR golden.
**Leverage:** Small-diff lever because the HTN extension follows the existing VFR-arrival shape (one new task constructor + wire-up); impact lands on every IFR-arrival mission tree.
**Size:** M
**Affected areas:** pilot/.../PilotMission.kt, pilot/.../mission/* (HTN tree)
**Risk notes:** Premature without IFR-1 + IFR-3 landing first; treat as the third IFR step, not the second.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

#### 10. Pilot-reactive trigger for density altitude (sibling to G3a-react crosswind/tailwind axis)
**Summary:** Third pilot-reactive POH/AFH recognition axis after crosswind (fn-14) and tailwind (fn-15); closes D-PASS-g3a-react-other-poh-triggers.
**Leverage:** Small-diff lever because the recognition+applier shape is directly mirroring fn-14/15 (new event leaf, new branch in derivePilotEvent, new applyDensityAltitudeGoAround); impact lands on a new G3a-react golden plus the third doctrinal-severity-asymmetry axis (per-type DA limits).
**Size:** M
**Affected areas:** protocol/.../AircraftType.kt (maxDensityAltitude), pilot/.../PilotEvent.kt, pilot/.../Pilot.kt, sim/jvmTest/.../G3aPilotReactiveDensityAltitudeTest.kt (new — 10th golden)
**Risk notes:** DA derivation needs altitude+temp+pressure inputs the pilot side may not currently sense; check the cockpit-input boundary first.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

#### 11. G1 multi-aircraft cross-aerodrome golden (revive abandoned G1, precursor to G3b)
**Summary:** First cross-aerodrome flying since the abandoned earlier G1; uses the retained `MultiAerodromeWorldTest.kt` scaffold (real LOWG + LJMB merge, two controllers, no flying today).
**Leverage:** Small-diff lever because the scaffold exists and the controller-handoff machinery from fn-8 is shipped; impact lands on the entire approach-sequencing surface (preconditions for CB-1) plus D-PASS-g3b-react-cross-aerodrome-{crosswind,tailwind} closures.
**Size:** L
**Affected areas:** sim/jvmTest/.../MultiAerodromeWorldTest.kt (real flying), sim/.../testing/Fixtures.kt, controller/.../ (cross-aerodrome handoff)
**Risk notes:** Cross-aerodrome flying is genuinely complex — pilot-driven frequency changes, FIS segment, TMA/CTR entry per Jepp 19-2; budget for scope creep beyond the scaffold's current shape.
**Persona:** first-time-user
**Next step:** /flow-next:interview

#### 12. Drive down the ATIS D-AUDIT cluster (4 entries: separate-frequency / voice-style / multi-aerodrome resolution / ATIS-cadence)
**Summary:** Meta-epic that closes 4 blocked deferments by landing the prerequisite (proper ATIS modelling); pattern-mirror of fn-18's clustered closure.
**Leverage:** Small-diff lever because the four deferments share a common precondition (typed ATIS surface that supports per-aerodrome resolution); impact lands on every multi-aerodrome scenario that today silently uses the LOWG-default ATIS.
**Size:** L
**Affected areas:** protocol/.../Atis.kt, controller/.../ (ATIS issuance), pilot/.../ (ATIS reception), docs/deferments.md (4 entries → Archive)
**Risk notes:** Voice-style rendering may be premature; pick a focal subset (separate-frequency + multi-aerodrome-resolution) and defer voice rendering as its own follow-up.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

#### 13. APP role wiring (strategy: 'approach sequencing are the next live verticals')
**Summary:** Add APP role at LOWG (today only TWR exists); foundation for CB-1 approach-sequencing work (number N, speed control, vectoring, essential traffic).
**Leverage:** Small-diff lever because APP follows the existing TWR/GND role shape (new controller spec + BDI guards + handoff machinery); impact lands on every IFR arrival, plus B7 APP→TWR handoff unblock, plus CB-1 enablement.
**Size:** XL
**Affected areas:** controller/.../ControllerSpec.kt, controller/.../bdi/ (APP procedures), sim/.../testing/Fixtures.kt (LOWG manifest update), wiki/domain/aviation-world.md
**Risk notes:** Genuinely XL — surfaces multi-controller responsibility-state interactions, frequency-change machinery, sequencing semantics; split into 3-4 tasks before starting.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

#### 14. A9: Migrate `LowgSpike` + `ArrivalVerticalTest` to planner-driven routes (depends on IFR-1..3 landing)
**Summary:** Removes the manual-route bypass per .plan A9; the conformance gap closes once IFR wiring is in place.
**Leverage:** Small-diff lever because the routes are already authored — just need to walk through the planner instead of hand-listing waypoints; impact lands on the two named tests plus any future IFR test that copy-pastes from them.
**Size:** M
**Affected areas:** sim/jvmTest/.../LowgSpike.kt, sim/jvmTest/.../ArrivalVerticalTest.kt, .plan (A9 → DONE)
**Risk notes:** Blocked on IFR-1..3 from positions 7/8/9; sequence accordingly.
**Persona:** senior-maintainer
**Next step:** /flow-next:interview

#### 15. OR-3 phase 1: bootstrap autonomous adversarial loop (.plan + strategy 'Reviewer / agent infrastructure')
**Summary:** Start the autonomous-adversarial-loop scaffolding the strategy track flags as not-started; pattern-mirror of /flow-next:plan-review's codex backend, applied to running implementations.
**Leverage:** Small-diff lever because the codex impl-review backend already exists and the loop is just a scheduler around it; impact lands on every epic's review surface (could surface defects no human caught — meta-defence per fn-18's review-discipline).
**Size:** L
**Affected areas:** agents/ (new OR-3 agent), AGENTS.md (process update), scripts/ (loop runner)
**Risk notes:** Premature if the codex review backend isn't stable enough for unattended runs; gate behind a manual triage phase before going fully autonomous.
**Persona:** adversarial-reviewer
**Next step:** /flow-next:interview

## Rejected

- Audit other SimState.*-by-aerodrome flat maps and migrate to entity fields — insufficient-signal: No specific map named beyond weather (which already landed); speculative without a concrete grep first.
- Refactor controller/Controller.kt 15-stage pipeline (30-commit hot zone) — insufficient-signal: 30 commits is hot but doesn't mean rotten; no specific architectural complaint cited.
- Multi-aircraft stress test golden (N≥3 aircraft on a single runway) — too-large: No concrete spec; the test shape needs definition first; treat as future scope after C13 APP wiring lands.
- Wake-turbulence separation (CB-2, ICAO 4444 §5.4.2/§5.8) — out-of-scope: .plan classifies CB-* as 'future scope, no imminent ETA' — parked for a reason.
- Runway-inspection state (CB-5; inhibits takeoff/landing clearances) — out-of-scope: Same CB-* future-scope parking; small but premature.
- B7: APP→TWR handoff at downwind phraseology fix — other: Sub-item of approach sequencing — depends on position-13 APP role wiring landing first; not a standalone candidate.
- F1: holding-pattern semantics — insufficient-signal: .plan classifies as 'blocked on geometry'; geometry work isn't in scope this iteration.
- RR-9: Wrong-value and cross-kind tests for §4.5.7.5.1(c) standalone advisories — duplicates-open-epic: Within fn-10's surface (Complete remaining source-window hardening — registry track).
- Decision-cycle mint-id pinning made structural (sim-test helper) — insufficient-signal: fn-15.2 SHIP'd on tx-start with documented justification; no concrete pain from the non-structural approach yet.
- STRATEGY-update automation (auto-append on epic close) — insufficient-signal: 5-day staleness isn't enough to justify tooling; manual refresh (position 2) is fine.
