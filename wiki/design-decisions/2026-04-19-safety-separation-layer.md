# 2026-04-19: Safety and Separation Layer (Phase 6)

## Context

Phase 5 landed proactive approach sequencing: ArrivalSequence with distance ordering, spacing-adequacy guards, supersession chain, essential traffic obligations, feasibility gates. Phase 6 adds the reactive safety layer underneath — the last line of defence when sequencing fails or when separation minima require enforcement beyond what proactive spacing provides.

### Key insight from ATC operational research

A tower controller does not switch between "sequencing mode" and "safety mode." They hold three concurrent mental layers: flow picture (background — who follows whom), separation picture (middle — pair-wise awareness with trend), and conflict picture (foreground, rare — something is wrong). The reactive layer does not replace the proactive layer; it *interrupts* it when a pair-wise threshold is breached. The biggest sim failure: making separation binary (trigger only at the hard limit). Real controllers have a comfort gradient and begin gentle corrections well before minimum is violated.

### Key insight from twr1

TWR1's oracle (forward-simulation for 30 ticks) is pragmatic for bounded conflict prediction but cannot prove whole-system safety. Its invariant checker (15 checks per tick) catches violations post-hoc. Wake turbulence is entirely absent. The segment-based conflict model (discrete occupancy, not continuous distance) scales well and is formally enumerable.

### Regulatory kernel (ICAO Doc 4444 17th ed.)

- §5.8: Wake turbulence categories J/H/M/L + separation minima (distance and time)
- §5.11: Visual separation conditions and limitations
- §7.9/§7.10: Runway departure/arrival separation (2-minute/3-minute rules)
- §7.10.2: Go-around obligation when separation cannot be maintained
- §7.14: LVP — one aircraft at a time, no conditional clearances, time-based wake replaces distance-based
- SERA.8005(c): General separation obligation for controlled flights

## Decision Summary

Build a pair-wise separation engine that runs **early** in the pipeline — alongside arrival sequence update — writing separation assessments into BeliefState as first-class beliefs. Procedure guards use these assessments naturally (replacing the Phase 5 `SpacingNotAdequate` placeholder). A **final safety-net step** after procedure execution catches cases no procedure rule handles (separation-driven go-arounds with clear runway) and can inject reactive interventions that no rule proposed.

The engine is both a **belief source** (early, feeding procedure guards) and a **reactive rule source** (late, catching what proactive rules missed).

---

## 1. Aircraft Type and Wake Category — 6a

### 1.1 Wake category as first-class data

```kotlin
enum class WakeCategory {
    J,  // Super (A380, An-225) — Doc 4444 §5.8
    H,  // Heavy (>136,000 kg MTOW)
    M,  // Medium (7,000–136,000 kg)
    L,  // Light (<7,000 kg)
}
```

Added to `AircraftObservation` as `wakeCategory: WakeCategory? = null`. Default null for unknown; **unknown defaults to H (Heavy)** for worst-case conservative separation. M would under-separate if the aircraft is actually Heavy or Super.

### 1.2 Separation minima tables

Pure data: leader-follower → required distance (NM) and time (minutes).

```kotlin
data class WakeSeparationMinima(
    val leader: WakeCategory,
    val follower: WakeCategory,
    val distanceNm: Double,    // for radar/distance-based (approach)
    val timeMinutes: Double,   // for time-based (departure, LVP)
)
```

ICAO baseline table (Doc 4444 §5.8):

| Leader | Follower | Distance (NM) | Time (min) |
|--------|----------|---------------|------------|
| J | J | 6 | 2 |
| J | H | 6 | 2 |
| J | M | 7 | 3 |
| J | L | 8 | 3 |
| H | H | 4 | 2 |
| H | M | 5 | 2 |
| H | L | 6 | 3 |
| M | L | 5 | 3 |

Same-category (non-J) or lighter-behind-heavier: radar minimum only (3 NM), no additional wake minimum. Time = 2 minutes.

RECAT-EU as a future switchable overlay (separate table, same shape). Phase 7+.

### 1.3 Observation history buffer

`BeliefState` gains `previousPositions: Map<AircraftId, List<ObservationSnapshot>>` — a bounded ring buffer (last 5 observations) per aircraft. Populated in `updateBeliefs` by snapshotting the outgoing observation before overwriting. Provides the history needed for closure-rate and vertical-rate derivation (§4).

### 1.4 Wire into ControllerView and sim

Sim populates `wakeCategory` from `AircraftState` (requires aircraft type → wake category lookup, initially hardcoded for LOWG fleet). `aircraftAt` test fixture gains `wakeCategory` parameter defaulting to `WakeCategory.L` (light — common in VFR circuit training).

---

## 2. Pair-wise Separation Engine — 6b

### 2.1 Two-phase architecture

The separation engine runs in **two phases** in the pipeline:

**Phase A — Early assessment (belief source).** Runs after `updateArrivalSequence`, before `executeProcedures`. Computes `SeparationAssessment` for every relevant pair and writes them into `BeliefState.separationAssessments`. Procedure guards (like `SpacingNotAdequate`) read these assessments instead of computing their own distances. The existing `SpacingNotAdequate` guard is replaced by `SeparationConcernAbove(threshold: SeparationConcern)`.

**Phase B — Reactive safety net (rule source).** Runs after `arbitrate`, before readback processing. Inspects the committed outputs and the current separation picture. If any pair is at INTERVENTION or VIOLATION level and no committed output addresses it, the engine injects a reactive intervention. This catches the case where two arrivals converge on final with clear runway and no procedure rule fires — the ARR-GO-AROUND guard requires `NOT RunwayPhysicallyClear`, but an in-trail separation violation with a clear runway has no procedure coverage.

### 2.2 Separation assessment

```kotlin
data class SeparationAssessment(
    val aircraft: AircraftId,
    val other: AircraftId,
    val currentSeparationNm: Double?,
    val requiredSeparationNm: Double,
    val closureRateKt: Double?,
    val timeToMinimumSeconds: Double?,
    val concern: SeparationConcern,
)

enum class SeparationConcern {
    COMFORTABLE,
    MONITORING,
    INTERVENTION,
    VIOLATION,
    /** Visual separation applied — controller has delegated; thresholds shift, speed control not available. */
    DELEGATED,
}
```

`DELEGATED` concern level added per ATC review: when visual separation is applied (FollowTarget at VISUAL_SEPARATION_APPLIED), the controller is still monitoring but cannot issue speed control — can only cancel visual separation or issue go-around.

### 2.3 Comfort computation — absolute margin with closure adjustment

**Not a ratio.** Absolute margin in NM with explicit closure-rate adjustment:

```
margin = currentSeparationNm - requiredSeparationNm
closureAdjustment = max(0, closureRateKt * CLOSURE_FACTOR)  // NM penalty for convergence
effectiveMargin = margin - closureAdjustment
```

Where `CLOSURE_FACTOR` converts closure rate to an additional margin requirement (e.g., 0.02 NM per knot of closure → 40kt closure demands 0.8 NM additional buffer).

Thresholds (in NM of effective margin):
- effectiveMargin > 2.0: COMFORTABLE
- 1.0 < effectiveMargin ≤ 2.0: MONITORING
- 0.0 < effectiveMargin ≤ 1.0: INTERVENTION
- effectiveMargin ≤ 0.0: VIOLATION

These are initial values — parameterised for tuning. For time-based separation (departures, LVP), a parallel computation in the time domain (seconds of margin) produces the concern level independently; the worse of distance and time concern wins.

### 2.4 Runway separation checks

Specific checks for the runway environment (Doc 4444 §7.9/§7.10):

- **Departure with arrival on approach**: departure may be cleared if estimated departure roll + climb clears the runway before arrival crosses threshold.
- **Land-after**: preceding aircraft vacated or at safe distance.
- **Wake on departure**: 2 minutes same-category or heavier-behind-lighter; 3 minutes lighter-behind-heavier.
- **Departure-departure**: successive departures on same runway; wake timer applies.

Timer infrastructure: `RunwayDutyState` gains `lastOperationCompletedAt: SimTime? = null` — set when holder is released. `grantPhase` checks wake timer before granting: `timeSinceLastOperation >= requiredWakeMinutes`.

### 2.5 SAFETY action deduplication

At most one SAFETY action per aircraft per cycle. If both a procedure interrupt (ARR-GO-AROUND) and the separation engine fire go-around for the same aircraft, the separation engine's version takes precedence (it has more context — separation assessment data for the trace). Dedup in Phase B by checking committed SAFETY outputs before injecting.

---

## 3. Intervention Hierarchy — 6c

### 3.1 Selection with explicit skip predicates

Selection is a pure function: `(SeparationAssessment, AircraftObservation, BeliefState) → Intervention?`

Default preference order: speed control → path extension → orbit → go-around.

**Skip predicates** (go directly to go-around):
- Aircraft inside 2 NM final (no speed/extend option exists at this range)
- Preceding aircraft has NOT vacated runway and arriving aircraft is at or past FAF
- Follower is faster than leader with < 1 NM excess margin (can't decelerate in time)
- VIOLATION level assessment (below minimum — no choice)

**Skip to orbit** (skip speed/extend):
- Aircraft on downwind but extending would conflict with adjacent airspace boundary
- Speed differential alone cannot resolve within the available track length

The intervention function checks skip predicates first, then tries each level in order with a feasibility check. Cascading is bounded to 4 steps maximum.

### 3.2 Go-around consequence management

A go-around creates a new separation problem: the climbing aircraft may conflict with downwind or hold traffic. The separation engine must:
1. Immediately assess the going-around aircraft against all other tracked aircraft.
2. If a new INTERVENTION/VIOLATION pair is created, issue traffic information to the affected aircraft.
3. Update the ArrivalSequence: remove the going-around aircraft, cascade renumber (Phase 5 `resequencedAircraft` handles this), update FollowTarget references.

This is not a new pipeline step — it falls out of the normal separation assessment on the next cycle after the go-around is committed.

---

## 4. Belief-Delta Detection — 6d

Derive state changes from observation history without pilot reports:

```kotlin
data class ObservationDelta(
    val aircraft: AircraftId,
    val closureRateKt: Double?,      // relative to preceding aircraft in sequence
    val verticalRateFpm: Double?,    // from altitude change / time between snapshots
    val groundTrackDeg: Double?,     // heading change
    val groundSpeedTrend: SpeedTrend?,
)

enum class SpeedTrend { ACCELERATING, DECELERATING, STABLE }
```

Computed in `deriveObservationDeltas` from the observation history buffer (§1.3). Runs between `updateBeliefs` and `updateArrivalSequence`. Feeds into separation assessment as input signals — closure rate is the critical one.

---

## 5. LVP Predicate Modifier — 6e (infrastructure only)

`lvpMode: Boolean` flag on `ControllerView` (derived from visibility < 550m or RVR < 550m).

When LVP is active:
- Conditional clearances rejected by feasibility
- Only one aircraft on the runway at a time (`grantPhase` refuses while occupied)
- Time-based wake separation replaces distance-based (use `timeMinutes` column, not `distanceNm`)
- Visual separation not applicable (`DELEGATED` concern level cannot be reached)

No new procedures. Existing rules' guards gain LVP awareness.

---

## 6. Visual Separation Decision — 6f

Uses Phase 5's FollowTarget lifecycle. The TRAFFIC_IN_SIGHT → VISUAL_SEPARATION_APPLIED transition requires:

- Both aircraft visible (or one reports other in sight)
- Geometry suitable: not converging, adequate lateral offset, same direction of flight
- Not during LVP
- Below FL100 (SERA.8005(b))

When visual separation is applied, the separation assessment for that pair shifts to `DELEGATED` concern. Wake minima are reduced but not eliminated for J/H leaders (Doc 4444 §5.11). Intervention options narrow: no speed control available, only cancel visual separation or go-around.

---

## 7. Verification — 6g

### 7.1 Wake minima table

Exhaustive test: every Doc 4444 §5.8 entry cross-checked against the lookup function. Property-based:
- All minima ≥ 0
- Directional: `required(H, L) != required(L, H)`
- Monotonic: for fixed leader, lighter follower requires ≥ heavier follower separation
- Unknown category (null → H) produces worst-case for light followers

### 7.2 Comfort gradient boundary cases

Unit tests on the pure function at each threshold transition:
- COMFORTABLE → MONITORING at exactly effectiveMargin = 2.0
- MONITORING → INTERVENTION at exactly 1.0
- INTERVENTION → VIOLATION at exactly 0.0
- Closure rate shifts thresholds: 40kt closure at 3nm required with 4nm current = INTERVENTION (not COMFORTABLE)
- Time-domain computation for departure wake timers

### 7.3 Intervention selection

- Determinism: identical inputs → identical output
- Skip predicates: inside 2nm final → go-around, no cascade
- Feasibility gating: speed control rejected when outside speed band → falls through to extend
- Go-around is never suppressed: any VIOLATION must produce a go-around

### 7.4 Harness scenarios (4-aircraft)

| Scenario | What it tests |
|----------|---------------|
| Converging pair on final (M behind H, closing 40kt) | Wake minima lookup, comfort with closure, intervention selection (speed control) |
| Same-category pair (M-M) stable spacing | COMFORTABLE assessment, no intervention |
| Light behind Heavy on departure | 3-minute wake timer in grantPhase |
| Go-around mid-sequence with traffic on downwind | Go-around consequence: new pair assessment, resequencing |
| VIOLATION with clear runway (no procedure rule fires) | Reactive rule-source: engine injects go-around |
| LVP mode: time-based wake, no conditional clearances | LVP predicate modifier |

### 7.5 Test infrastructure

- `aircraftAt` gains `wakeCategory: WakeCategory? = WakeCategory.L` parameter
- NM ↔ metres conversion helper (1 NM = 1852m)
- Multi-tick observation history builder: `observationHistory(aircraft, vararg snapshots: Pair<SimTime, PointId>)`
- `testAssessment(...)` factory for unit-testing comfort/intervention functions
- `towerView` gains `lvpMode: Boolean = false`

---

## Dependency graph

```
6a (wake categories + minima + observation history buffer)
 |
6b (pair-wise separation engine: Phase A beliefs + Phase B reactive)
 |
6c (intervention hierarchy with skip predicates)
 |
6d (belief-delta — feeds closure rate into 6b; can start parallel)
 |
6e (LVP predicate modifier — rides on 6b)
 |
6f (visual separation decision — rides on 6b + Phase 5 FollowTarget)
 |
6g (verification — continuous)
```

---

## Explicitly excluded (Phase 7+)

| Item | Why deferred |
|------|-------------|
| RECAT-EU table | Need ICAO baseline first |
| Parallel/dependent runway separation | Single-runway first |
| Holding/stack vertical separation | Distinct domain |
| STAR compliance checking | Need APP fully operational |
| CAT II/III approach minima + procedures | Need LVP infrastructure first |
| Workload model (cognitive-cost budgeting) | Needs all Phase 6 interventions costed |
| IFR ground flow | Independent of safety layer |
| Crosswind wake displacement | Not essential for Phase 6 |

---

## Risk register

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Comfort gradient thresholds feel robotic | HIGH | HIGH | Absolute margin + closure adjustment is explicitly parameterised; test with real scenarios; ATC agent review |
| Wake category data absent for most aircraft | MEDIUM | MEDIUM | Default to H (worst-case); LOWG fleet hardcoded initially |
| Closure rate noisy from discrete position updates | MEDIUM | MEDIUM | Smooth over 3+ observations in history buffer; degrade to distance-only when insufficient data |
| Reactive engine fires alongside procedure rules (dedup) | MEDIUM | HIGH | At most one SAFETY per aircraft; separation engine takes precedence; checked in Phase B |
| Go-around creates new separation problem | MEDIUM | MEDIUM | Falls out of normal next-cycle assessment; no special handling needed beyond what the engine already does |

---

## Review log

| Pass | Reviewer | Key findings | Resolution |
|------|----------|-------------|------------|
| 1 | Staff engineer | Pipeline placement wrong (must be early + late, not just late); filter-only insufficient (must be rule source); comfort formula div-by-zero; SAFETY dedup; no timer infra for departure wake; no observation history buffer | All addressed in v1→v2: two-phase engine, reactive rule source, absolute margin formula, dedup rule, lastOperationCompletedAt, previousPositions buffer |
| 1 | ATC general | Comfort formula fragile at low minima; skip-logic missing; default H not M; go-around consequence; delegated concern; departure-departure wake; closure adjustment not formalised | All addressed: absolute margin, explicit skip predicates, H default, consequence management, DELEGATED concern, dep-dep in §2.4, closure formula specified |
| 1 | Test review | No verification section; need wake table tests, boundary tests, intervention determinism, go-around-never-suppressed; need test infra (wake on fixtures, NM helper, history builder, assessment factory, lvpMode) | Verification section added (§7); infrastructure enumerated (§7.5) |
| 2 | FP | DELEGATED ordinal poisons SeparationConcernAbove; estimateGroundSpeedKt stale fallback; reactiveInterventions dead code | Fixed: isSeverityAtLeast excludes DELEGATED; stale fallback removed (return null); Phase B wired into pipeline |
| 2 | Test review | Comfort tests tautological (helper duplicates formula); intervention tests exercise only degenerate paths; no per-row wake verification for 6 entries | Tests rewritten against production functions; DELEGATED/VIOLATION severity tests added; production assessSeparation integration test added |
| 2 | ATC law | Wake table correct; FL100 handling correct; closure 20kt is sim heuristic not regulatory; lastOperationCompletedAt measures wrong event (should be roll commencement) | 20kt documented as heuristic; takeoffRollStartedAt field added to RunwayDutyState |
| 2 | ATC general | Thresholds operationally realistic; closure factor sound; 70kt minimum reasonable; reactiveInterventions should return interventions not assessments | Return type changed to List<Pair<Assessment, Intervention>> — detection + action atomic |
| 2 | ATC phraseology | Phase B should emit BreakOff (not GoAround) when runway clear; traffic info must accompany every separation intervention | Tracked for Phase 7 emission wiring; noted in pipeline comment |
