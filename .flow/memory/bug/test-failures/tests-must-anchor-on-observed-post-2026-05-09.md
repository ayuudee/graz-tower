---
title: "Tests must anchor on observed post-state, not controller-emitted instruction tim"
date: "2026-05-09"
track: bug
category: test-failures
module: sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G1TwoAircraftCircuitsTest.kt
tags: [test-discipline, observation-vs-intent, sim-pin, fn-8]
problem_type: test-failure
symptoms: Test pin uses firstControllerInstructionOf<X> as proxy for aircraft state transition
root_cause: Anchored on controller intent (instruction emission) rather than post-state observation (pilot report / sensor derivation)
resolution_type: fix
---

## Problem

Tests pinned to controller-emitted INSTRUCTION timestamps (e.g. "controller told A to vacate") look like they verify behaviour but actually only verify intent. A regression where the controller emits the vacate instruction but the runway-duty machine fails to release the runway, or where the pilot fails to actually vacate, would still satisfy such a pin while violating the property the test claims to enforce.

The fn-8.3 Phase 4 G1 re-baseline introduced a pin like:

```kotlin
val aFirstVacateTime = records.firstControllerInstructionOf<AfterLandingVacateVia>(...)
check(aFirstVacateTime < bFirstDownwindTime) { "A vacates before B enters pattern" }
```

Codex iteration 1 caught this immediately: the pin proves the controller TOLD A to vacate, not that A actually did so.

## What Didn't Work

Anchoring serialization invariants on controller-emitted instructions
(`firstControllerInstructionOf<AfterLandingVacateVia>`,
`firstControllerInstructionOf<BacktrackRunway>`). These records show the
controller's intent at the time of emission, not the aircraft's actual
state transition. A regression in the runway-duty machine, the pilot's
mission-tree advance, or the kinematic-vacate path would still satisfy
the instruction-time pin while breaking the property the test was
authored to defend.

## Solution

Anchor on the pilot's *report* transmission instead — the pilot's mission
tree only advances to `REPORT_RUNWAY_VACATED` after the aircraft is
physically off the runway entity, so the `Report(RunwayVacated)` pilot
transmission is a real post-state observation:

```kotlin
val aRunwayVacatedTime = records.firstPilotReportOf<ReportEvent.RunwayVacated>(aircraftAId)
    .map { it.time.millis }
    .getOrElse { fail("Expected RunwayVacated report from A...") }
check(aRunwayVacatedTime < bFirstDownwindTime) { ... }
```

Codex iteration 2 SHIPped on this anchor. The KDoc + inline comment
called out the instruction-vs-observation distinction so future
implementers understand the regression risk.

## Prevention

When authoring a test pin that asserts "X happened before Y":
1. **Identify the property being defended** — is it that the controller
   issued an instruction, or that the aircraft transitioned state?
2. **Anchor on the post-state observation, not the upstream intent.**
   Controller-emitted instruction records are appropriate when the test
   is about controller-side decision logic; pilot reports / sensor-
   derived state transitions are appropriate when the test is about
   aircraft-side compliance or sim integration.
3. **Trace from the test message back to the regression**: if the
   message says "B's first Downwind report came before A vacated", the
   anchor must witness "A is actually no longer using the runway" —
   not "controller asked A to vacate."

This is a sibling of the broader "test-time observation source
discipline" — the same trap fires whenever a test pins on something
upstream of the property under test (e.g. pilot intent on `pilotMission`
to verify physics, controller belief to verify sim state, etc.).
