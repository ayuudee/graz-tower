# ATC Agent Review — Issue Tracker

Consolidated feedback from law, general ops, and phraseology agents (2026-04-13).

## HIGH

| # | Issue | Status |
|---|-------|--------|
| 1 | Visual approach conflated with instrument approach — resolution fails for VISUAL | DONE — added `ClearedVisualApproach`, removed VISUAL from `ApproachType` |
| 2 | MaintainLevel/MaintainSpeed SELF_COMPLETING should be PERSISTENT | DONE — changed to PERSISTENT; added `evaluatePersistentConstraint` for monitoring |
| 3 | LineUpAndWait missing Clearance marker | DONE — now implements `Clearance` |
| 4 | DivertTo has no domain / no supersession | DONE — ROUTE domain, supersedes ROUTE + LEVEL |
| 5 | Vector instructions null timing and null completion — should be IMMEDIATE/PERSISTENT | DONE — IMMEDIATE timing, PERSISTENT completion |

## MEDIUM

| # | Issue | Status |
|---|-------|--------|
| 6 | GoAround should supersede SPEED domain | DONE — added SPEED to supersedesOverride |
| 7 | Circuit/sequencing instructions misplaced in RUNWAY domain | DONE — moved to null domain (independent of landing/takeoff) |
| 8 | No Special VFR clearance instruction | DONE — added `SpecialVfrClearance` |
| 9 | PushbackApproved marked conditional — apron, not manoeuvring area | DONE — `mayBeConditional = false` |
| 10 | TaxiViaRunway has null metadata | DONE — SEQUENTIAL timing, SELF_COMPLETING |
| 11 | No altitude/position-based conditional predicates | DONE — added `AtLevel`, `AtDistance`, `AfterPassing` to `ConditionalPredicate` |
| 12 | Hold-short completion conflates arrival and constraint | Documented — PERSISTENT/NOT_APPLICABLE is correct for the constraint aspect; arrival detection would need position tracking against resolved holding point, deferred |

## LOW

| # | Issue | Status |
|---|-------|--------|
| 13 | Frequency upper bound mismatch + no 8.33 kHz validation | DONE — upper bound fixed to 136.975; 8.33 kHz validation deferred |
| 14 | FL vs altitude comparison ignores pressure | DONE — documented assumption in `comparableFeet` comment |
| 15 | Missing readback types + BehindTrafficCondition | DONE — added GoAround/Vacate/Orbit/ExtendDownwind/VisualApproach/SpecialVfr readbacks, `BehindTrafficCondition`, `AtDistanceCondition` |
| 16 | Missing instruction types (ReportIntentions, CancelClearance, etc.) | DONE — added `ReportIntentions`, `CancelClearance`, `DescendWhenReady`, `AvoidArea`, `AvoidLevel` |
| 17 | Missing authority operations (LOW_APPROACH, GO_AROUND) | DONE — added to `AuthorityOperation` enum |
| 18 | PressureSetting QNH/QFE lack validation | DONE — smart constructors with 900-1100 hPa range |
| 19 | Runway clearances null timing but SELF_COMPLETING — document intent | DONE — comment in InstructionRules.kt |
| 20 | Backtrack always completes at far end | DONE — `BacktrackRunway` now has optional `vacateAt: PointId?`; resolution uses it when present |

---

# Phase 4 sim-engine review (2026-04-17)

Four-agent pass after the sim engine + 4e-B handoff vertical landed: FP review,
ATC general operations, ATC law / regulatory, ATC phraseology. Items below are
*deferred* — they came out of the review but are explicitly outside Phase 4's
"first vertical slice" scope and will land in the Phase 5+ slices they belong
to. Fixes that *did* land in Phase 4 pre-commit are in the git history.

## Deferred — Phase 5 Approach-sequencing slice

| # | Issue | Source |
|---|-------|--------|
| P4-D1 | APP→TWR handoff on downwind is wrong for most real fields; should be ILS intercept / 8 nm final | ATC general |
| P4-D2 | No sequencing logic in approach (no "number N", no speed control, no vectoring) | ATC general |
| P4-D3 | `ARR-LAND` allows clearance from `OnApproach` without a distance gate (ICAO 4444 §7.10 runway-available requirement is weak) | ATC general |
| P4-D4 | `ARR-TURN-BASE` fires when the aircraft is already on base — phraseologically wrong, should only fire from downwind | ATC general |
| P4-D5 | No essential traffic info on circuit instructions (CAP 413 §4.49) | phraseology |
| P4-D6 | No "report final" instruction / readback | phraseology |

## Deferred — Phase 6 Safety / separation layer

| # | Issue | Source |
|---|-------|--------|
| P4-D7 | No wake turbulence separation (ICAO 4444 §5.4.2 / §5.8) | ATC general + law |
| P4-D8 | No arrival→departure runway buffer — back-to-back clearances possible | ATC general |
| P4-D9 | Successive-departure time separation (ICAO 4444 §5.8) not modelled | law |
| P4-D10 | No missed-approach climb/heading carried into go-around | ATC general |
| P4-D11 | LVPs (low-visibility procedures) / CAT II-III regime absent | ATC general |
| P4-D12 | No runway-inspection state (inhibits takeoff/landing clearances) | ATC general |
| P4-D13 | No intersection-departure handling | ATC general |

## Deferred — Emergency / cancel-takeoff family

| # | Issue | Source |
|---|-------|--------|
| P4-D14 | `DEP-CANCEL-TAKEOFF` has no `NoPendingReadback` guard — can fire repeatedly while the cancel is still in flight | ATC general |
| P4-D15 | `StopImmediately` / `TakeoffImmediatelyOrVacateRunway` / `TakeoffImmediatelyOrHoldShort` still have empty readback atom sets (the hold-position pair was fixed in Phase 4) | phraseology |

## Deferred — Protocol / phraseology v2

| # | Issue | Source |
|---|-------|--------|
| P4-D16 | `InitialContact.atisCode: Char?` — silently nullable; contact at controlled aerodromes requires ATIS letter per CAP 413 §2.3 | phraseology |
| P4-D17 | `ReadbackCorrection` carries only `ReadbackCorrectionKind`, loses the specific `AtomDefect` list that `classifyReadback` produces | phraseology |
| P4-D18 | `AfterLandingVacateVia` cannot express "vacate left/right when able" (needs optional direction + `WhenAble` modifier) | phraseology |
| P4-D19 | No "stand by" / workload-deferral response from controller | phraseology |

## Deferred — FP polish

| # | Issue | Source |
|---|-------|--------|
| P4-D20 | `SimState.aircraft: LinkedHashMap` leaks a mutable type in a supposed value | FP |
| P4-D21 | `SimRandom` uses `tag.hashCode()` — JVM-specific, not MPP-stable; fragile under replay across targets | FP |
| P4-D22 | `PilotAgent` uses `error()` in a pure function for structurally unreachable branches; should lift into `Raise` or prove unreachability at the type level | FP |
| P4-D23 | `applyPilotHeardInstruction` / `instructionDuration` / `pilotUtteranceDuration` still have `else ->` fallbacks — documented in Phase 4 but a structural solution (e.g. an `InstructionEffect` opt-in annotation per sealed leaf) would be cleaner long-term | FP |

## Deferred — Regulation database

| # | Issue |
|---|-------|
| P4-D24 | Add `ICAO4444_5_8` (successive-departure separation) when the matching rule lands |
| P4-D25 | Pin ICAO Doc 4444 edition number (the same todo is already in `controller-issue-tracker.md` Tier 6 #35) |

