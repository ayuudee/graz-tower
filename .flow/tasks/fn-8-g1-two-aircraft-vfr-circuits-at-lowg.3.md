# Description

Closure pass for G1. fn-8.2 shipped `G1TwoAircraftCircuitsTest` loudly
failing per the project's no-corners-cut convention; the test author
isolated a multi-aircraft circuit-pattern sequencing defect (touch-and-go
→ full-stop intent flip leaves the prior `ClearedTouchAndGo`
coordination alive, A reaches the runway threshold but never receives
`ClearedToLand` / `AfterLandingVacateVia`, B is queued for departure
forever — observed sim deadlock). G0 (single-aircraft) stays green;
multi-aircraft `circuits=1` exhibits the same deadlock — narrowing the
failure to the multi-aircraft coordination / commitment-stage path.

Mirrors the G2 closure precedent (commits `f273d53` round 1, `5af3f24`
round 2 — diagnostic harness first, then iterative fixes). fn-8.3 is a
**single task** that runs a diagnostic dive on G1 using the **existing**
trace harness (no new infra), then lands the fix(es).

## Decisions captured (interview, 2026-05-09 — corrected after pass-1 plan-review)

1. **Package**: single sub-task under fn-8 (not a multi-task split, not
   a new epic). Closes fn-8.2 indirectly when G1 goes green.
2. **Priority**: blocks fn-9 (in-flight research work paused until G1
   closes — the loudly-failing test on master is a real signal-deficit).
3. **Investigation order**: diagnostic dive runs **before** any fix
   attempt. Mirrors G2's commit `f273d53` (SimTrace harness landed first).
4. **Diagnostics scope (CORRECTED per pass-1 plan-review findings #1+#4):**
   the existing trace harness in `SimTraceQueries.kt` already provides
   `commitmentStageTransitions(aircraft, controller)` derived from
   `BeliefState` snapshots (line 124). The first-pass interview
   misjudged this as missing infra requiring a new build-out; pass-1
   plan-review surfaced the existing query. fn-8.3 therefore:
   - **Does NOT** add new typed observation events on `:common`.
   - **Does NOT** change `ControllerDecisionResult` or the
     `controllerDecide` API. Pure-decide contract preserved untouched.
   - **DOES** add small correlation / formatting helpers in
     `:sim:jvmTest` if the dive needs them (e.g. cross-aircraft
     timeline merge, transmission-record correlation against
     commitment-stage transitions).
   - **DOES** carry a fallback escalation path: if the existing query
     proves insufficient to root-cause the failure, escalate to the
     "build typed events" route as a separate task — file under
     `D-PASS-g1-diagnostics-typed-events`. fn-8.3 does not pre-commit
     to that route.
5. **Diagnostics shape (NULLIFIED per #4 above):** the writer-style /
   suspend-emitter / hybrid choice from the interview is moot if no
   new infra ships. Decision retained for future reference (if
   `D-PASS-g1-diagnostics-typed-events` is ever activated, writer-style
   + hybrid surface is the user-decided default).
6. **Diagnostics surface (NULLIFIED per #4 above):** same as #5.
7. **Test scope (closure proof)**: G1 green AND a smaller multi-aircraft
   `circuits=1` minimal pin green. The minimal pin's assertions are
   specified in §Acceptance below (per pass-1 plan-review finding #7).
8. **Start zone**: not pre-decided — diagnostic dive picks based on
   evidence, not agent guess. Three named suspects from the G1 KDoc
   remain the candidate list.
9. **G0 / G2 stability**: re-baseline pinned values if the fix is
   doctrinally correct (per `feedback_no_corners.md`; matches the
   discipline fn-8.1 followed for per-aircraft RNG threading). Roll back
   only if a shift cannot be defended doctrinally.
10. **Abort criterion**: STOP fn-8.3 and split if the dive reveals the
    existing trace harness CANNOT identify the transition cause and a
    typed-events build-out is necessary. That work is its own scope
    (`D-PASS-g1-diagnostics-typed-events`); fn-8.3 doesn't bundle it.
    The interview-time abort ("> ~10 controller call-site refactors")
    is now nullified by #4 — no controller API change in fn-8.3.
11. **G1 time-band acceptance (per pass-1 plan-review finding #6):**
    fn-8.2's spec required tightening the 90-min generous wall to a
    ±15% band post-first-green. fn-8.2 never reached first-green, so
    that tightening was deferred. **fn-8.3 inherits:** when G1 goes
    green here, capture observed wall in evidence and tighten to a
    ±15% band before fn-8.3 closes.

## Failure-mode summary (from G1 test KDoc, commit c543139)

Aircraft A executes the first circuit's touch-and-go correctly. On
the second circuit she reports Downwind FULL_STOP at ~T+20:48; the
controller responds with repeated `ClearedTouchAndGo` re-issues
(`ARR-LAND-TNG-REISSUE`) on the CAP 413 §2.7 cadence. A physically
reaches `LOWG_RWY_16C_THR` and her mission step transitions to
`REPORT_RUNWAY_VACATED`, but **no `ClearedToLand` and no
`AfterLandingVacateVia`** is ever transmitted; she stays on the
runway in `LandingRoll` phase indefinitely. The tower's commitment
for A remains at `AwaitLandedObserved` with the runway-duty `holder`
still A. B is queued for departure (`RunwayQueueEntry(B, DEPARTURE)`)
and never receives a takeoff slot. Final transmission count saturates
at ~418 — effective deadlock despite event-queue activity continuing.

Single-aircraft `circuits=1` stays green; multi-aircraft `circuits=1`
deadlocks with `BacktrackRunway` issued (rule fires) but pilot never
advances. **Multi-aircraft coordination / commitment-stage path is
the failure region**, NOT the FULL_STOP intent flip itself or the
runway-exit selection.

**Note (per pass-1 plan-review finding #7):** the G1 (`circuits=2`)
and minimal (`circuits=1`) failures are related but not identical
surfaces. G1 manifests as missing `ClearedToLand`/`AfterLandingVacateVia`;
the minimal pin manifests as `BacktrackRunway` issued but pilot stuck.
Both originate in the multi-aircraft coordination / commitment-stage
path; the dive establishes the shared root cause (or proves they're
distinct).

## Three suspect zones (from G1 KDoc — candidate list, dive picks)

1. **Coordination ledger × two-aircraft pendingReadback matching** —
   first-pass evidence shows the T&G coordination on A keeps escalating
   `Issued → Querying` even after A reads back. Consistent with a same-
   aircraft / cross-aircraft readback-match misattribution under
   multi-aircraft load. Investigation site:
   `controller/Controller.kt:736 acceptReadback`.
2. **Commitment-stage advancement on TnG → fullstop transition while the
   previous-circuit T&G coordination is still live.** G2's `acceptReadback`
   "close every Correct coord" fix may have a sibling on the arrival-
   side T&G commitment lifecycle. Investigation site:
   `controller/observe/Coordination.kt` + arrival commitment-stage
   transitions.
3. **Runway-duty `lastOperationCompletedAt`** doesn't appear to advance
   past A's first-circuit T&G touchdown — duty-state machine may still
   hold A as the in-flight ARRIVAL holder when the second circuit's
   FULL_STOP path tries to fire. Investigation site:
   `controller/assess/RunwayAssessment.kt:34 lastOperationCompletedAt`.

## Approach (high-level shape — implementation specifics for plan-review / work)

### Phase 1 — Diagnostic dive using existing trace harness

Run G1 + the multi-aircraft `circuits=1` minimal scenario. For each
aircraft, walk:
- `SimTrace.commitmentStageTransitions(aircraft, controller)` (existing
  query at `SimTraceQueries.kt:124`).
- `SimTrace.missionStepTransitions(aircraft)` + `positionPointTransitions`.
- Per-tick coordination-ledger snapshots from `st.beliefs[controller]
  ?.coordinations` (or whatever path-of-record the existing harness
  uses; audit at task time).
- Per-tick `RunwayDutyState` snapshots from `st.beliefs[controller]
  ?.runwayDuty` (singular per `BeliefState`; per pass-2 plan-review
  finding #1).
- Transmission-record correlation: cross-reference the `journey` of
  records with the commitment-stage transitions to identify the tick
  where the chain should have advanced but didn't.

If correlation / formatting helpers are missing, ADD them in
`:sim:jvmTest` (no `:common` changes). Examples of helpers that may
be needed:
- `SimTrace.coordinationStateAt(t: SimTime, aircraft: AircraftId): List<Coordination>`
- A printable per-aircraft timeline merging records + commitment-stage
  + mission-step transitions.

**Escalation gate (per decision #10):** if the existing trace harness
cannot identify the transition cause for the failure (e.g. the
relevant state isn't captured in `BeliefState` because it's
mid-decide-cycle and not surfaced to the trace), STOP and file
`D-PASS-g1-diagnostics-typed-events` as a separate task. fn-8.3 does
not pre-commit to that route.

### Phase 2 — Root-cause + fix(es)

Apply fix(es) targeting the identified zone(s). Iterative — multiple
commits expected, each closing one observable misbehavior. G2's
closure precedent shipped one fix in round 1, three more in round 2;
fn-8.3 may follow the same shape but stays in one task.

For each fix, the plan-review-aware discipline (per
`feedback_review_discipline.md`):
- Diagnose first (use the dive output).
- Plan inline in commit message + spec evidence.
- Land minimal change.
- Verify against G1 + minimal pin + G0 + G2.
- If the fix shifts G0/G2 pinned values, re-baseline per decision #9
  with explicit per-pin doctrinal rationale.

### Phase 3 — Re-pin

- G1 green.
- Multi-aircraft `circuits=1` minimal pin green (new test
  `G1TwoAircraftMinimalSpec.kt` or sibling — implementer picks file/name
  at task time). Specific assertions per pass-1 plan-review finding #7:
  - Both aircraft complete their missions.
  - Each aircraft's vacate / `BacktrackRunway` readback closes its
    coordination (the coordination-ledger entry transitions to
    `Closed`/equivalent terminal stage; no Querying-stuck cycles).
  - No runway holder is wedged after the last aircraft vacates
    (`RunwayDutyState.holder` is null OR matches the next queued
    aircraft, not the just-vacated one).
  - B receives a runway slot — i.e. an instruction targeting B is
    transmitted that consumes the runway (e.g. `LineUpAndWait`,
    `ClearedForTakeoff`, `BacktrackRunway`, etc.).
- G0 (`LowgGoldenTest`), G2 (`G2CrossAerodromeVfrTest`) stay green OR
  pinned values re-baselined with explicit per-pin rationale.
- detekt baseline unchanged.
- Time band tightened to ±15% per decision #11; observed wall recorded
  in `## Evidence`.

## Review considerations (per pass-1 plan-review finding #3 + `feedback_plans_review_aware.md`)

### FP / type-safety

- **No controller API change.** `controllerDecide` returns
  `ControllerDecisionResult(outputs, updatedBeliefs, trace)` unchanged.
  Pure-decide contract preserved (per `feedback_architecture.md`'s
  pure-IO principle). No `(newState, traces)` tuple addition.
- The existing `commitmentStageTransitions` query at
  `SimTraceQueries.kt:124` returns `List<CommitmentStageTransition>`
  with typed `Option<Stage>` `from`/`to` accessors — fully typed,
  no stringly-encoded transitions.
- The fix(es) target the controller's pure-decide path. Any new
  function added at the fix sites must preserve the existing
  `(view, beliefs, world) → ControllerDecisionResult` shape — no
  side effects, no IO leakage.
- Sealed `Stage` / `RunwayDutyState` / coordination-ledger types
  remain authoritative; the fix doesn't introduce stringly typed
  branching.

### Test architecture

- G1 stays as the integration-level closure proof.
- The minimal pin (`circuits=1` two-aircraft) is a **scope-narrower**
  regression test, not a duplicate. Catches the failure at a smaller
  scenario shape so future single-circuit refactors fail loudly
  before reaching G1.
- Per `feedback_testing_philosophy.md`, no scaffold tests added.
  Diagnostic helpers in `:sim:jvmTest` are not tests; they're
  test-time tooling.
- G0 / G2 byte-stability is the load-bearing regression risk; the
  re-baseline-if-doctrinally-correct policy (decision #9) handles
  shifts explicitly, never silently.

### Impact

- **No production code blast radius for the diagnostic dive.** Helpers
  live in `:sim:jvmTest`. The fix(es) DO touch production code in the
  controller (one or more of the three suspect zones).
- Fix-time blast radius depends on which zone the dive identifies.
  Pre-write audit at fix time per `feedback_pass_scope`'s discipline.
- New minimal-pin test file (~150 lines, mirrors G0's shape).
- Doc updates (if needed): `AGENTS.md` § Golden tests may move G1's
  status from "FAILING — closure pending" to "green" once closed.

### Operational correctness

- The fix targets a real-ATC sequencing defect: an aircraft that has
  reported FULL_STOP must receive `ClearedToLand` /
  `AfterLandingVacateVia`, not a re-issued `ClearedTouchAndGo`. CAP
  413 / ICAO Doc 4444 are the doctrine sources for the FULL_STOP
  flip path. **Citation discipline (per pass-2 plan-review nitpick
  #4):** verify the exact CAP 413 / ICAO Doc 4444 section numbers
  against the local research text or canonical PDF during the
  diagnostic dive if the fix changes ATC phraseology or behavior;
  do not commit speculative section numbers.
- Multi-aircraft pattern sequencing is real-ATC-ubiquitous. Closing
  G1 unblocks all future multi-aircraft scenarios (3+ aircraft per
  D-PASS-three-or-more-aircraft, mixed VFR+IFR, etc.).
- The diagnostic dive output is also a doctrine artifact: it
  documents the controller's coordination-ledger / commitment-stage
  / runway-duty state transitions under multi-aircraft load. Future
  ATC-correctness work (e.g. RECAT-EU adoption, additional separation
  rules) reads cleaner against a recorded baseline.

# Acceptance

- [ ] Diagnostic dive evidence captured in `## Evidence`: per-aircraft
      timeline merging commitment-stage transitions + mission-step
      transitions + transmission records across the FULL_STOP flip.
      Identified root-cause zone (one of the three suspects, or a new
      zone surfaced by the dive).
- [ ] Diagnostic helpers (if added) live in `:sim:jvmTest` only. No
      `:common` / production controller changes for the diagnostics
      themselves. (Fix-time controller changes are separate and
      expected.)
- [ ] **Existing-query-first audit captured in `## Evidence`:**
      confirms `SimTrace.commitmentStageTransitions(aircraft,
      controller)` (and any sibling existing queries) were sufficient
      for the dive. If the escalation gate fired (existing query
      insufficient), `D-PASS-g1-diagnostics-typed-events` is filed
      and fn-8.3 is split before fix work begins.
- [ ] G1 (`G1TwoAircraftCircuitsTest`) green.
- [ ] New multi-aircraft `circuits=1` minimal pin green
      (`G1TwoAircraftMinimalSpec.kt` or sibling — implementer picks
      file/name; documents in evidence). Specific assertions:
      - Both aircraft complete their missions.
      - Each aircraft's vacate / `BacktrackRunway` readback closes
        its coordination — the coordination entry is **absent** from
        `BeliefState.coordinations` after correct readback /
        supersession (per pass-2 plan-review finding #2 — the model
        encodes closure as absence, not a `Closed` state). No matching
        entry remains in `Issued`, `Querying`, `Reissued`, or
        `LostCommsDeclared`.
      - `RunwayDutyState.holder` is null OR the next queued aircraft
        after the last aircraft vacates (NOT the just-vacated one).
      - B receives a runway slot (instruction targeting B that
        consumes the runway is transmitted).
- [ ] G0 (`LowgGoldenTest`) green OR re-baselined with explicit
      per-pin doctrinal rationale documented in `## Evidence`.
- [ ] G2 (`G2CrossAerodromeVfrTest`) green OR re-baselined with
      explicit per-pin doctrinal rationale documented in `## Evidence`.
- [ ] G1's time band tightened to ±15% around observed wall (per
      decision #11 — inheriting fn-8.2's deferred post-first-green
      tightening). Observed wall captured in `## Evidence`.
- [ ] Targeted test commands green: `:sim:jvmTest --tests
      G1TwoAircraftCircuitsTest --tests G1TwoAircraftMinimalSpec
      --tests LowgGoldenTest --tests G2CrossAerodromeVfrTest`.
- [ ] Controller / sim / pilot / core / protocol suites green:
      `:sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:jvmTest
      :protocol:jvmTest`.
- [ ] `./gradlew detekt` baseline unchanged.
- [ ] **`:migration:jvmTest --continue` shape (FYI evidence, NOT
      blocking acceptance per pass-1 plan-review finding #5):** if run,
      output is captured in `## Evidence` for completeness. The known
      pre-fn-7 flake (`LjmbWorldCandidateValidationTest.writesLjmbCurrentCoreValidationReport`)
      may still be present; fn-8.3 doesn't gate on it. fn-8.3 is a
      controller / sim closure task — migration verification isn't
      in-scope unless migration code is touched.
- [ ] fn-8.2 marked done (G1 was its blocker) — handled by the user
      post-green; not part of fn-8.3's automated acceptance.
- [ ] Deferments register updated:
      - `D-PASS-g1-diagnostics` partial closure recorded: existing
        trace harness was sufficient for fn-8's purposes; broader
        per-callsign diagnostic infra (typed events on `:common`)
        stays under `D-PASS-g1-diagnostics-typed-events`, only
        activated if a future closure pass needs it.
      - `D-PASS-g1-diagnostics-broader` (named in the original
        interview output) is RENAMED to
        `D-PASS-g1-diagnostics-typed-events` to clarify it's the
        typed-events build-out specifically.

## Done summary

## Evidence

### Phase 1 — Diagnostic dive (2026-05-09, session 1)

**Existing-query-first audit (decision #4 / acceptance bullet 3): PASS.**
The existing `SimTraceQueries.kt` harness — `transitionsOf`,
`commitmentStageTransitions`, `missionStepTransitions`,
`positionPointTransitions` — was sufficient to root-cause the deadlock.
No typed-events build-out needed; `D-PASS-g1-diagnostics-typed-events`
escalation gate did not fire.

**Helper added (sim/jvmTest only, per acceptance bullet 2):**
`G1ClosureDiveTest.kt` — diagnostic dive driver that reuses the existing
trace queries to print:
- `circuitIntent[OE-ABC]` transitions at LOWG_TOWER over the run.
- Commitment stage transitions for OE-ABC at LOWG_TOWER.
- Per-`ARR-LAND*` transmission, the controller's intent + stage at the
  cursor immediately before that record.
- Per-pilot-Downwind report, the tower's `circuitIntent[A]` before and
  20 s after.

The helper is **diagnostic-only**, not part of the closure proof. May
be deleted or refactored once root-cause fixes land.

**Root-cause zone identified: stale `circuitIntent` belief at TOWER +
runaway TnG re-issue loop (zone #1: coordination ledger × multi-aircraft
under load — but the actual mechanism is single-aircraft single-circuit,
manifesting only under load).**

Specifically:
1. **`circuitIntent[OE-ABC]` is set ONCE at [569000ms] on the first
   downwind report (`absent → TOUCH_AND_GO`) and never updates again
   for the rest of the run** — even though the pilot transmits a
   second `Downwind(circuitIntent=FULL_STOP)` at [1248000ms]. The
   controller's belief stays `TOUCH_AND_GO` 20 s after the FULL_STOP
   downwind.
2. The most likely mechanism: the controller is firing `ARR-LAND-TNG`
   re-issuance every ~10 s during the entire first circuit
   (569ms-1155ms — see commitment ping-pong below), saturating the
   frequency. The pilot's FULL_STOP downwind transmission at [1248000ms]
   is followed 440 ms later by an `ARR-LAND-TNG` ClearedTouchAndGo
   transmission at [1248440ms] — they overlap on-air, both get marked
   stepped-on at `handleTransmissionEnd`, neither is delivered. The
   controller never sees the FULL_STOP. The pilot has no further
   opportunity to declare intent (downwind happens once per circuit).
3. **Compounding bug — runaway commitment-form-and-issue loop.** During
   the first circuit (569ms-1155ms), the commitment ping-pongs every
   ~0.5-10 s through `Complete → AwaitApproach → LandingClearanceIssued
   → AwaitLandedObserved → Complete`. ~80 `ARR-LAND-TNG` /
   `ARR-LAND-TNG-REISSUE` firings in this window, all addressed to A.
   `ARR-TNG-AIRBORNE` (gate: `IsCircuitTraffic && !FULL_STOP &&
   Airborne`) at `AwaitLandedObserved` is too eager — it completes the
   arrival without checking that the aircraft was actually observed
   on the runway during the current commitment. Fresh `AwaitDownwind`
   commitment forms next cycle, position-reconciles to
   `LandingClearanceIssued` (A is on Final), `ARR-LAND-TNG` fires —
   loop repeats.
4. **Deadlock at `AwaitLandedObserved` after second touchdown.** A
   stays on the runway (mission tree's second circuit is `Circuit`,
   not `TouchAndGo` — full-stop). Controller's commitment is at
   `AwaitLandedObserved`. `circuitIntent[A]` is stale `TOUCH_AND_GO`.
   `ARR-VACATE`'s guard: `OnRunway, OnGround, AnyOf(CircuitIntent==
   FULL_STOP, Not(IsCircuitTraffic))` — first OR-arm false (stale
   intent), second false (A is circuit traffic), so rule doesn't fire.
   `ARR-TNG-AIRBORNE`'s guard requires `Airborne` — A is on the ground.
   No rule advances → A wedged in `LandingRoll` at runway threshold,
   B wedged in `AwaitReady` waiting for the runway slot.

**Concrete observation chain (key timestamps):**
- [569000ms] `circuitIntent[A]: absent → TOUCH_AND_GO` (first downwind).
- [569000-1155000ms] commitment.stage[A] ping-pongs ~80 cycles.
- [1248000ms] PILOT transmits `Downwind(circuitIntent=FULL_STOP)`.
- [1248440ms] CONTROLLER transmits `ARR-LAND-TNG → ClearedTouchAndGo`
  (intent=TOUCH_AND_GO at this cursor, i.e. stale).
- Both transmissions overlap in flight; both stepped-on; neither
  delivered.
- [1248440-5400000ms] `circuitIntent[A]` stays at `TOUCH_AND_GO`
  permanently. ~120 further `ARR-LAND-TNG*` firings, all stale.
- [1461000ms] A reaches RWY_16C_THR for second touchdown.
- [1545000ms] mission step `LAND → REPORT_RUNWAY_VACATED`.
- [1545000-5400000ms] A stuck in `LandingRoll`, no `BacktrackRunway`
  or `AfterLandingVacateVia` ever issued. Wedged.

**Suspect-zone disposition:**
- Zone #1 (coordination ledger × pendingReadback matching under
  multi-aircraft load) — partially implicated. The runaway TnG re-issue
  loop fires every ~10 s because each freshly-formed commitment
  spawns a fresh ClearedTouchAndGo coordination, and every new TnG
  collides with prior coordinations. But the mechanism isn't a
  cross-aircraft readback misattribution; it's the same-aircraft
  commitment-form-and-issue loop. This suspect zone fired but for a
  different reason than first-pass evidence suggested.
- Zone #2 (commitment-stage advancement on TnG → fullstop transition
  while the previous-circuit T&G coordination is still live) — the
  ROOT zone. `ARR-TNG-AIRBORNE` is too eager (no "actually touched
  the runway" pre-condition), causing the commitment to ping-pong
  whenever the aircraft is briefly classified `OnRunway` (e.g.
  airborne over the runway threshold on short final, before actual
  touchdown). Each cycle issues a fresh TnG. The sustained on-air
  TnG congestion is what blocks the pilot's FULL_STOP downwind from
  being delivered.
- Zone #3 (runway-duty `lastOperationCompletedAt`) — secondary. The
  duty state at end-of-run shows `holder=OE-ABC, operation=ARRIVAL,
  lastOperationCompletedAt=1025000ms` (i.e. the *first* touch-and-go
  release time). Because A never vacates after the second touchdown,
  the duty state never releases A — but this is a downstream symptom
  of Zone #2, not an independent defect.

**Fix direction (Phase 2, next session):** target Zone #2 first.
Either (a) gate `ARR-TNG-AIRBORNE` on "aircraft has been observed
on a runway entity at least once during the current commitment"
(analogous to `RunwayDutyState.holderReachedRunway`), or (b) restrict
the rule to fire only when the aircraft transitioned from OnRunway
to Airborne within the current commitment lifetime. The fix should
collapse the runaway loop, freeing the radio so the pilot's FULL_STOP
downwind can deliver, and `circuitIntent[A]` can flip to FULL_STOP,
unblocking `ARR-VACATE` after the second touchdown.

**Citation discipline note (decision #11 / Operational correctness
review note):** the fix direction does not change ATC phraseology or
introduce new regulation citations — it tightens an internal
commitment-stage gate. CAP 413 / ICAO Doc 4444 section numbers on
existing rules are unchanged. No speculative citations to verify.

**Test command run (Phase 1 evidence-only):**
```
nix develop --command ./gradlew :sim:jvmTest \
  --tests xyz.easiersaid.twr.sim.G1ClosureDiveTest \
  --tests xyz.easiersaid.twr.sim.LowgGoldenTest \
  --tests xyz.easiersaid.twr.sim.G2CrossAerodromeVfrTest \
  --console=plain
```
Result: G1ClosureDiveTest GREEN (diagnostic prints captured),
LowgGoldenTest GREEN, G2CrossAerodromeVfrTest GREEN.
G1TwoAircraftCircuitsTest stays FAILING per spec — closure proof is
Phase 3.

**Detekt baseline state (Phase 1):** 10 weighted issues — same
count as the c543139 baseline (fn-8.2 ship). No new issues introduced
by `G1ClosureDiveTest.kt` (jvmTest scope, default detekt config does
not flag the diagnostic shape).

### Phase 2 round 1 — Root-cause fix B2 + auxiliary B3 (2026-05-09, session 2)

**OnRunway flicker investigation (Phase 2 thread (a)) — disposition.**
The Phase 1 prior-worker hypothesis ("the `OnRunway` position
classification fires repeatedly during the airborne portion of circuit
1 — that 'flicker' is the actual cause") **did not hold under
empirical investigation**. The dive's new event-tagged stage trace +
worldIndex entity audit proved:

- The runaway commitment ping-pong fires at points that are **NOT**
  on `RunwayRef` entities — primarily at circuit anchors like
  `LOWG_ANCHOR_CIRCUIT_SE_ENTRY` (phase=Base, alt=305m, NOT runway-
  adjacent). There is no per-cycle `OnRunway` (entity) flicker for
  the runway during the airborne portion of circuit 1.
- The actual proximate cause is **multi-leg circuit-leg classification
  in `worldIndex.circuitLegsByPoint`**: certain shared graph points
  belong to multiple circuit legs simultaneously. Audit output:
  ```
  LOWG_ANCHOR_CIRCUIT_SE_ENTRY → FINAL BASE DOWNWIND
  LOWG_CIRCUIT_MAIN_SHARED_GRAPH_07 → FINAL BASE
  LOWG_CIRCUIT_CENTER_EAST_AXIS_02 → FINAL
  LOWG_CIRCUIT_CENTER_EAST_AXIS_03 → FINAL
  LOWG_RWY_16C_THR → Approach Runway
  LOWG_CIRCUIT_MAIN_SHARED_GRAPH_08 → BASE
  ```
  `OnCircuitLeg(LegName.FINAL)` therefore evaluates **TRUE** at
  base-leg / circuit-anchor positions, allowing `LandingConditions`
  (`AnyOf(OnApproach, OnCircuitLeg(FINAL))`) to fire `ARR-LAND-TNG`
  prematurely on the base leg, far from short final.

- The actual **load-bearing** root cause of the loop is
  `ARR-TNG-AIRBORNE`'s gate being too eager: `Airborne &&
  IsCircuitTraffic && !FULL_STOP`. After `ARR-LAND-TNG` fires
  (premature or not) and the pilot reads back, stage advances to
  `AwaitLandedObserved` via `readbackAdvancesToStage`, where
  `ARR-TNG-AIRBORNE` fires immediately because the aircraft is
  airborne (even though it never touched the runway during this
  commitment) → completes the commitment → fresh one re-forms each
  cycle.

The two bugs interact: the multi-leg classification shortens the
ping-pong cycle (fires earlier), but the over-eager `ARR-TNG-AIRBORNE`
is what makes the cycle **runaway**.

**Fix shape: per spec direction Option A — gate `ARR-TNG-AIRBORNE`
on observed-runway-touchdown during current commitment lifetime.**
The fix is doctrinally analogous to `RunwayDutyState.holderReachedRunway`
(already in the runway-duty machine).

**B2 implementation (3 surgical changes):**
1. `Commitment.touchedDownDuringCommitment: Boolean = false` — sticky
   witness, default false on commitment formation
   (`controller/bdi/Commitment.kt`).
2. `reconcileObservedStages` for `TOWER_ARRIVAL` sets the flag when
   observation has `RunwayRef` membership AND `onGround = true`
   (`controller/Controller.kt`).
3. New `TouchedDownDuringCommitment` BDI guard added to `ARR-TNG-AIRBORNE`'s
   `AllOf` gate (`controller/bdi/Guard.kt`,
   `controller/procedure/TowerArrival.kt`).

**B3 implementation — exposed by B2's success.** With B2 collapsing
the runaway loop, A's full circuit completes cleanly (2 circuits +
park). Result: aircraft B was wedged at TOWER_DEPARTURE@AwaitReady
with the runway granted but `DEP-LUAW` never firing. Root cause:
`DEP-LUAW`'s `DepartureTrigger = PilotReady` is a single-cycle event
guard. Pilots report Ready ONCE; for sequential departures behind a
circuit-traffic arrival, the runway is granted to the second
departure many cycles after the pilot's one-shot Ready event has
aged out of `ctx.events`. **Real ATC retains "Ready" on the strip**
— this fix models that. Three surgical changes:

1. `Commitment.pilotReadyDuringCommitment: Boolean = false` — sticky
   witness for `ReadyForDepartureReceived` event during commitment
   lifetime (`controller/bdi/Commitment.kt`).
2. `reconcileObservedStages` for `TOWER_DEPARTURE` sets the flag from
   the `events` parameter (`controller/Controller.kt`; signature
   gains `events: List<ControllerEvent>`).
3. New `PilotReadyDuringCommitment` BDI guard replaces `PilotReady`
   in `DepartureTrigger` (`controller/bdi/Guard.kt`,
   `controller/procedure/TowerDeparture.kt`).

**Test results post-B2+B3:**
- `G1ClosureDiveTest` — green (diagnostic, kept for Phase 2+ work).
- `LowgGoldenTest` — green (G0 unchanged).
- `G2CrossAerodromeVfrTest` — green (G2 unchanged).
- `G1TwoAircraftCircuitsTest` — **still failing**, but at a new
  later wedge (see B4 below).
- Full `:sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:jvmTest
  :protocol:jvmTest` — 105 of 106 sim tests + all other suites pass.
  Only G1TwoAircraftCircuitsTest fails. **No regressions from B2+B3.**
- Detekt — 10 weighted issues, baseline unchanged.

**Downstream wedge (B4) — surfaced by B2+B3 closure, deferred to
Phase 2 round 2.** With A and B both flying, A completes cleanly. B
takes off ([1171440ms]) but its commitment stays at TOWER_DEPARTURE
@AwaitTakeoffObserved instead of transitioning to TOWER_ARRIVAL after
B reports Downwind. `DEP-CIRCUIT-COMPLETE` (gate: `Airborne &&
OnCircuitLeg(DOWNWIND) && IsCircuitTraffic`, advances to Complete →
re-forms as TOWER_ARRIVAL) does not fire because B's pilot reports
Downwind, Base, Final at the same SimTime tick [1560940ms] (likely a
pilot-AI timing compression after the long ground delay), and the
controller cycle doesn't see B simultaneously on Downwind leg AND
with `IsCircuitTraffic = true` set.

This is a **distinct, third bug** beyond Phase 2's two original
threads, surfaced because B2+B3 freed the rest of B's path. Per the
Phase 1→Phase 2 handoff ("STOP and report when Phase 2's threads
are resolved or if a third phase is needed"), B4 is filed for Phase
2 round 2 / Phase 3. Candidate fixes (no implementation in this
round):
- (B4-a) Relax `DEP-CIRCUIT-COMPLETE`'s leg gate to also fire on
  Base or Final, not just Downwind, provided IsCircuitTraffic is
  true and aircraft is airborne. Doctrine: at any leg of the
  circuit, if the aircraft is established as circuit traffic, it's
  "circuit complete" from a TOWER_DEPARTURE perspective.
- (B4-b) Investigate the pilot AI's Downwind+Base+Final compression
  when departure is delayed — may be a pilot-side timing model issue.
- (B4-c) Auto-complete TOWER_DEPARTURE when the controller observes
  the aircraft on any circuit leg (entity-derived) with
  IsCircuitTraffic = true, regardless of the specific leg.

**Citation discipline note (decision #11 / Operational correctness):**
neither B2 nor B3 changes ATC phraseology or introduces new
regulation citations. B2 tightens an internal commitment-stage
gate; B3 changes the controller's "ready" witness from event-only to
strip-state-style persistent. CAP 413 / ICAO Doc 4444 section
numbers on existing rules are unchanged. No speculative citations.

**Test commands run (Phase 2 round 1 evidence):**
```
nix develop --command ./gradlew :sim:jvmTest \
  --tests xyz.easiersaid.twr.sim.G1ClosureDiveTest \
  --tests xyz.easiersaid.twr.sim.LowgGoldenTest \
  --tests xyz.easiersaid.twr.sim.G2CrossAerodromeVfrTest \
  --tests xyz.easiersaid.twr.sim.G1TwoAircraftCircuitsTest \
  --console=plain --rerun-tasks

nix develop --command ./gradlew :sim:jvmTest :pilot:jvmTest \
  :controller:jvmTest :core:jvmTest :protocol:jvmTest \
  --console=plain

nix develop --command ./gradlew detekt --console=plain
```

### Phase 3 round 1 — B4 dive + same-instant frequency-collision fix + strip-based circuit recognition (2026-05-09, session 3)

**B4 dive findings (G1ClosureDiveTest 3rd `@Test` method
`dive — B4 B downwind compression vs DEP-CIRCUIT-COMPLETE`):**

The Phase 2 round 1 hypothesis ("B reports Downwind+Base+Final at the
same SimTime tick — pilot-AI timing compression") **partially held but
the actual mechanism is different**. The dive's event-tagged trace shows:

1. **B's pilot tick at `1559000ms` produced ONE Downwind transmission
   (intent=TOUCH_AND_GO).** Not two. `pilotCognitiveDecide` returns at
   most one tx per call by construction.
2. **B's NEXT pilot tick at `1560000ms` produced ONE Base transmission.**
   These are two consecutive ticks 1s apart — not a single-tick burst.
3. **Both transmissions ended up scheduled at exactly `1560940ms`** —
   the tick at `1559000ms` saw A's `RunwayVacated` (1558940..1560940) in
   `inFlightTransmissions` and computed `proposedStart = 1560940ms`.
   The tick at `1560000ms` saw the **same** stale view (the prior
   tick's emitted `TransmissionStart` event was queued at `1560940ms`
   and not yet processed) and ALSO computed `proposedStart = 1560940ms`.
4. **A's Readback (handleInstructFromController emission) also got
   `startedAt=1560940ms`** for the same reason: the tick at `1559000ms`
   processed A's `ARR-VACATE-HANDOFF` reception, generating a Readback
   delayed to the freq-free moment.
5. **Three transmissions on the same frequency at the exact same instant**
   (B's Downwind, B's Base, A's Readback). All three got marked
   `steppedOn=true` by `handleTransmissionStart`. None delivered.
6. **`circuitIntent[B]` was never set** — Downwind was the only path the
   controller has to learn circuit-end intent (CAP 413 §4.45-4.49).
   Without it, `IsCircuitTraffic` was false for B for the entire run.
7. **`DEP-CIRCUIT-COMPLETE` never fired** because its gate
   `IsCircuitTraffic` was false. B's commitment stayed at
   `TOWER_DEPARTURE@AwaitTakeoffObserved` from `1177000ms` until run end.

**The bug surfaces as multi-aircraft frequency contention**, not pilot-AI
compression as initially hypothesised. The proximate cause is
`handlePilotTick`'s frequency-busy check reading `state.inFlightTransmissions`
which doesn't yet include freshly-emitted-but-pending-at-future-time
transmissions queued by prior pilot ticks (or by other aircraft's
simultaneous ticks). Reality-anchored model: a real pilot whose own PTT
is still active will not begin a new transmission; the audio panel
signals "transmitting." The current sim layer doesn't honour this.

**Fix shape — three coordinated changes:**

1. **C1 — same-aircraft pilot radio-busy tracking (`SimState.pilotRadioFreeAt` +
   `Step.handlePilotTick`).** Per-aircraft `Map<AircraftId, SimTime>`
   eagerly tracks the `endsAt` of the aircraft's most recently emitted
   pilot transmission. Subsequent pilot ticks for the same aircraft
   honour this floor when computing `proposedStart`. Also updated in
   `handleInstructFromController` (readback + InitialContact paths) and
   `handleRespondFromController` (corrected-readback path) for
   completeness. Doctrine: a pilot doesn't talk over their own
   transmission. After C1, B's same-tick Downwind+Base collision
   resolves: Downwind at `1560940ms`, Base at `1562940ms` (after
   Downwind ends).
2. **C2 — strip-based circuit-traffic recognition (new `IsCircuitTrafficByStrip`
   guard in `controller/bdi/Guard.kt`).** Reads
   `ControllerView.flightStripDestinations` — absent ⇔ filed plan has
   no onward destination ⇔ VFR LCL local flight. The strip carries this
   *before any radio contact*. Doctrine: ICAO Annex 11 §4.3,
   AIP/AIC kind-of-flight markings (VFR LCL); real ATC strips for
   circuit-training flights are tagged "VFR LCL" / "LOCAL" so the
   controller knows from the AFTN-distributed strip alone. The guard
   tightens "no destination" to "has a strip AND no destination" to
   fail closed for unknown aircraft.
3. **C3 — `DEP-CIRCUIT-COMPLETE` accepts strip-based signal alongside
   radio-derived `IsCircuitTraffic`** (`controller/procedure/TowerDeparture.kt`).
   Gate becomes `Airborne && OnCircuitLeg(DOWNWIND) &&
   AnyOf(IsCircuitTraffic, IsCircuitTrafficByStrip)`. Robust to lost
   radio reports — a single Downwind step-on no longer wedges
   commitment-stage advancement.
4. **C4 — `ARR-LAND` / `ARR-LAND-TNG` default flip** (`controller/procedure/TowerArrival.kt`).
   Pre-fix, `ARR-LAND-TNG`'s gate was `Not(CircuitIntentIs(FULL_STOP))`
   — i.e., default to T&G when intent is empty. Combined with the C3
   strip-based commitment advancement, this caused G0 (single-aircraft
   circuit, full-stop) to receive a T&G clearance before its Downwind
   was processed. Doctrinally, a controller hearing no Downwind call
   should clear the aircraft to land (safe default), not offer T&G.
   `ARR-LAND` now fires on `CircuitIntentIs(FULL_STOP) || Not(IsCircuitTraffic)`;
   `ARR-LAND-TNG` requires explicit `CircuitIntentIs(TOUCH_AND_GO)`.
   Symmetric change to `ARR-LAND-REISSUE` and `ARR-LAND-TNG-REISSUE`
   gates.

**Test results post-Phase-3-round-1:**
- `G1ClosureDiveTest` — green (diagnostic, kept).
- `LowgGoldenTest` (G0) — green (G0 unchanged byte-stable on outcome
  assertions; the C4 default-flip avoided a regression that would have
  fired when C2 broadened DEP-CIRCUIT-COMPLETE).
- `G2CrossAerodromeVfrTest` (G2) — green (G2 unchanged).
- `G1TwoAircraftCircuitsTest` (G1) — **still failing**, but at a
  different downstream wedge surfaced by Phase 3's success (see B5
  below). Aircraft A now completes both circuits + parks cleanly. B
  takes off, has its first-circuit Downwind stepped on, the controller
  defaults to ARR-LAND on observation alone (because of `Not(IsCircuitTraffic)`
  via the strip-only signal — wait, the strip says local but radio
  intent is empty, so `Not(IsCircuitTraffic)` is true — that fires
  ARR-LAND).
- Full `:sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:jvmTest
  :protocol:jvmTest` — 106 of 107 sim tests + all pilot/controller/
  core/protocol tests pass. Only G1TwoAircraftCircuitsTest fails. **No
  regressions from C1-C4.**
- Detekt — 10 weighted issues, baseline unchanged.

**B5 — downstream wedge surfaced by C1-C4 closure (Phase 3 round 2 / next session).**

With C1-C4 unblocking B's first circuit, B reaches Downwind→Base→Final,
gets `ClearedToLand` (full-stop default — pilot's intent is empty
because Downwind was stepped on, so `Not(IsCircuitTraffic)` fires
ARR-LAND), touches down, receives `AfterLandingVacateVia` (ARR-VACATE
fires correctly). Pilot reads back the vacate. **But B's pilot mission
tree was configured for T&G on first circuit** (`circuits=2,
fullStopOnLast=true` → first circuit is `touchAndGoCircuitTask` which
has a trailing `FLY_DEPARTURE` after `LAND` for the climb-out).

After LAND completes, the pilot's mission step advances to the trailing
`FLY_DEPARTURE`. `processInstruction(AfterLandingVacateVia)` only
matches `step == AWAIT_VACATE_INSTRUCTION` and silently drops the
instruction. The sim layer's `applyAfterLandingVacateVia` writes a
ground route to the exit point, but the pilot's mission tree doesn't
advance. **B sits in `LandingRoll` at `LOWG_RWY_16C_THR` indefinitely
with `active task = REPORT_RUNWAY_VACATED`** (the next mission step
that was reachable via mission-tree advance).

**Empirically tested but reverted Phase 3 round 1 fix.** A pilot-side
HTN-replan (`collapseToGroundArrival` helper in `PilotCognitive.kt`)
that replaces the active TouchAndGo task + subsequent circuit siblings
with a fall-through to the existing `groundArrivalTask` was implemented
and tested. It changed B's wedge from "LandingRoll + REPORT_RUNWAY_VACATED"
to "Climbing + REPORT_RUNWAY_VACATED" (B's kinematic layer took off
again from the runway threshold despite the mission tree being in a
ground-arrival shape). The collapse left `applyAfterLandingVacateVia`'s
ground route in place, but the pilot's `Pilot.kt` decide loop must
have over-ruled it from the mission-tree's pilot-phase-derivation.

The fix needs a **deeper pilot-side replan** that also resets the
kinematic intent (analogous to `applySelfInitiatedGoAround`'s
`PilotIntent` reset to `phase = PilotPhase.Climbing`). Filed as
**B5** for Phase 3 round 2 / next session. The pilot-side fix is its
own scope and risk surface — it touches kinematic-layer interaction,
mission-tree replan discipline, and may have impact on go-around/
join-circuit flows. Best handled with a fresh dive + plan-review
cycle rather than bundling into Phase 3 round 1.

**Filed deferments (Phase 3 round 1):**
- **B5** (this round's surfacing): pilot-side mid-T&G→full-stop
  recovery on `AfterLandingVacateVia` / `BacktrackRunway` receipt when
  step is the trailing `FLY_DEPARTURE` of a `TouchAndGoCircuitTask`.
  The pilot must replan the mission tree (collapse remaining
  circuit-pattern siblings under `CircuitTraining`, advance to
  `groundArrivalTask`) AND reset kinematic intent to a ground-vacate
  shape. Real ATC parallel: "OE-DEF, vacate at <exit> via <route>" —
  the cockpit acknowledges and complies regardless of whether the
  pilot's plan was T&G; the controller's instruction supersedes.
  Single-aircraft cases (G0) don't surface this because the radio is
  uncongested and `circuitIntent` is delivered cleanly, so the
  controller correctly issues `ClearedTouchAndGo` per pilot intent.
  **Note (Phase 3 round 2 supersession):** this Phase 3 round 1
  draft conflated mission-tree replan with kinematic-intent reset
  into a single transition. Phase 3 round 2's dive identified that
  the kinematic reset must defer to post-touchdown — the
  ClearedToLand receipt shouldn't touch `PilotIntent.phase` because
  the aircraft is still airborne on final. The corrected two-stage
  contract lives in `D-PASS-pilot-mid-tng-fullstop-recovery` (Phase
  3 round 2 deferments list + sister register); future
  implementers MUST follow that contract, not this round 1 draft.
- **D-PASS-cross-aircraft-step-on** (broader sim radio infra):
  simultaneous transmissions from different aircraft on the same
  frequency at the same instant collide because each emission site
  reads `inFlightTransmissions` against a stale view (the prior
  emission's `TransmissionStart` event is queued but not yet
  processed). C1's `pilotRadioFreeAt` only addresses the same-aircraft
  case. A broader fix (per-frequency `frequencyBusyUntil` tracker
  updated eagerly across all emission sites) was attempted in this
  session but broke G2's handoff timing (G2 went `LostCommsDeclared`
  on a JoinCircuit coordination because the pre-applied
  `inFlightTransmissions` shifted controller-cycle output ordering in
  a way that disrupted readback delivery). The narrow fix is filed for
  a future pass with a dedicated impact-aware design.

**Citation discipline note:** the C1-C4 fixes change ATC default
behaviour (clear-to-land vs T&G when intent unknown) and add a
strip-based signal recognition path. Both are reality-anchored:
- C4's full-stop default for unknown intent: real ATC's safe default
  is to clear the aircraft to land; offering T&G to a pilot who hasn't
  declared T&G is doctrinally backwards. CAP 413 §4.45-4.49 (downwind
  intent reporting), ICAO Doc 4444 §7.10 (landing clearance procedure)
  — the CAP 413 phraseology "[callsign], cleared touch and go" is the
  correct response WHEN the pilot has reported "downwind, touch and
  go". Without that report, "cleared to land" is the default.
- C2's strip-based circuit recognition: ICAO Annex 11 §4.3 (flight
  rules), AIP / AIC kind-of-flight markings (VFR LCL). No new
  regulation citations — the strip's `destinationAerodrome=null`
  encoding is the existing protocol-layer signal for "local flight."

**Test commands run (Phase 3 round 1 evidence):**
```
nix develop --command ./gradlew :sim:jvmTest \
  --tests xyz.easiersaid.twr.sim.G1ClosureDiveTest \
  --tests xyz.easiersaid.twr.sim.LowgGoldenTest \
  --tests xyz.easiersaid.twr.sim.G2CrossAerodromeVfrTest \
  --tests xyz.easiersaid.twr.sim.G1TwoAircraftCircuitsTest \
  --console=plain --rerun-tasks

nix develop --command ./gradlew :sim:jvmTest :pilot:jvmTest \
  :controller:jvmTest :core:jvmTest :protocol:jvmTest \
  --console=plain

nix develop --command ./gradlew detekt --console=plain
```

**Scope note for fn-8.3 closure:** Phase 3 round 1 closes the original
B4 mechanism (DEP-CIRCUIT-COMPLETE no longer wedges on a stepped-on
Downwind transmission). G1 is not yet green — the B5 follow-on wedge
prevents B from completing. Per the spec's abort criterion #10, B5 is
filed as a separate task scope rather than bundled into fn-8.3's
acceptance, mirroring how fn-8.3 itself was filed as a separate task
beyond fn-8.2's scope. fn-8.3 stays in_progress until B5 closure or
user direction to re-pin G1's expectation.

### Phase 3 round 2 — B5 confirmation dive + STOP-and-report (2026-05-09, session 4)

**Re-anchored on master tip `8e0a3ec` (Phase 3 round 1 ship).** Re-ran
the existing G1 + dive harness to confirm Phase 3 round 1's closure
of B4 holds and to characterise B5 precisely. Per the prompt's
explicit STOP-and-report contract (worker.md "third phase needed"
pattern), this round does NOT attempt a B5 fix — it confirms the
wedge, sharpens the candidate-fix space with one new finding, and
files deferments.

**G1 baseline (post-`8e0a3ec`):**
- `LowgGoldenTest` (G0) — green.
- `G2CrossAerodromeVfrTest` (G2) — green.
- `G1ClosureDiveTest` — green (diagnostic).
- `G1TwoAircraftCircuitsTest` (G1) — failing at the B-mission-incomplete
  check (line 321), wall-time exhausted at 5 400 000 ms.

**B's full mission step trace (from per-aircraft trace summary,
captured 2026-05-09):**
```
[126440ms]  REQUEST_TAXI         → TAXI_TO_HOLDING
[222000ms]  TAXI_TO_HOLDING      → RUN_UP_CHECKS
[283000ms]  RUN_UP_CHECKS        → AWAIT_LINE_UP
[1156440ms] AWAIT_LINE_UP        → AWAIT_TAKEOFF_CLEARANCE
[1176440ms] AWAIT_TAKEOFF_CLEARANCE → FLY_DEPARTURE
[1559000ms] FLY_DEPARTURE        → REPORT_DOWNWIND       (1st circuit downwind)
[1560000ms] REPORT_DOWNWIND      → REPORT_BASE
[1561000ms] REPORT_BASE          → FLY_FINAL
[1586440ms] FLY_FINAL            → REPORT_FINAL
[1588000ms] REPORT_FINAL         → LAND
[1856000ms] LAND                 → FLY_DEPARTURE         (T&G post-LAND)
[2244000ms] FLY_DEPARTURE        → REPORT_DOWNWIND       (2nd circuit downwind)
[2245000ms] REPORT_DOWNWIND      → REPORT_BASE
[2246000ms] REPORT_BASE          → REPORT_FINAL
[2247000ms] REPORT_FINAL         → LAND
[2541000ms] LAND                 → REPORT_RUNWAY_VACATED (groundArrivalTask)
                                                          (B wedged here through wall)
```

**Final controller-side state at wall (5 400 000 ms):**
- Tower commitment for B: `kind=ARRIVAL stage=AwaitVacating
  contacted=true runway=16C`. Frozen at AwaitVacating since the
  first circuit's BacktrackRunway readback (1862 440 ms).
- Tower coordinations for B: `{}` (empty — no outstanding
  coordination ledger entries).
- `RunwayDutyState`: `holder=null, lastOperationCompletedAt=
  2 020 000 ms, lastOperationWakeCategory=L`.
- B's pilot active task: `REPORT_RUNWAY_VACATED`, mission incomplete,
  `phase=LandingRoll, positionPoint=LOWG_RWY_16C_THR`.

**The four interlocking mechanisms behind B5 (sharpened from
Phase 3 round 1's filing):**

**M1 — Same-tick race between controller's ARR-LAND emission and
pilot's Downwind report.** At [1560940ms] three transmissions are
on-air simultaneously: ARR-REPORT-FINAL (controller),
ARR-LAND ClearedToLand (controller, full-stop default per C4), and
B's `Report(Downwind, intent=TOUCH_AND_GO)` (pilot). The controller
fires ARR-LAND BEFORE B's downwind transmission has been
delivered/processed. Doctrinal divergence: per CAP 413 §4.45-4.49,
the pilot calls the position first; the controller responds with a
landing clearance. The current sim fires ARR-LAND on observation
+ strip-derived `IsCircuitTrafficByStrip` (C2/C3) without waiting
for the pilot's report.

**M2 — Pilot mission tree obediently complies with the wrong-intent
clearance.** B reads back ClearedToLand at 1589 440 ms.
`handleLandingClearance` (`PilotCognitive.kt:835`) marks
AWAIT_SEQUENCING / FLY_BASE / FLY_FINAL / AWAIT_LANDING_CLEARANCE
all complete and sets `hasClearance = true`. The mission's T&G
shape is preserved (LAND→FLY_DEPARTURE remains queued); the pilot
will lift off again post-LAND because the next mission step is
FLY_DEPARTURE, not REPORT_RUNWAY_VACATED.

**M3 — BacktrackRunway is silently dropped because the pilot's step
is wrong.** At 1855 440 ms the controller (per C4 / ARR-VACATE)
issues `BacktrackRunway`. The pilot's step is `LAND`
(CompletionMode.PHYSICAL — incomplete because the aircraft is
still on the runway). `processInstruction` for BacktrackRunway
(`PilotCognitive.kt:645`) requires `step ==
MissionStep.AWAIT_VACATE_INSTRUCTION`. Step is `LAND`, so the
match fails and the instruction is silently dropped. Sim layer's
`applyAfterLandingVacateVia` writes the ground route, but the
mission tree doesn't advance.

**M4 — B physically lifts off again and flies a second
(unauthorised, unobserved-by-tower) circuit.** Mission step
advances `LAND → FLY_DEPARTURE` at 1856 000 ms; kinematic intent
is climb-out. B physically takes off, flies the full second
circuit (Downwind/Base/Final reports at 2244-2248k ms — these
sequence cleanly because A has parked and the radio is quiet),
lands again at 2541 000 ms. The tower's commitment never re-formed
for this second circuit (it stayed `AwaitVacating` from the first
BacktrackRunway). B's second-circuit reports go to a controller
that doesn't process them at this commitment stage. After the
second LAND, mission step advances to REPORT_RUNWAY_VACATED (the
groundArrivalTask first step, because circuitTask is the last
sibling under CircuitTraining for the full-stop circuit). Pilot
then sits in REPORT_RUNWAY_VACATED on the runway threshold. No
controller-side rule fires (commitment is `AwaitVacating`, not
`AwaitLandedObserved`).

**Existing-query-first audit (acceptance bullet 3): PASS again.**
The dive used `formatJourney` + the trace summary (`responsibilityTransitions`,
`missionStepTransitions`, `positionPointTransitions`) and direct
`finalState.beliefs[tower.id].commitments[bId]` reads — all
existing surfaces. No new typed events needed for this round; the
escalation gate does not fire.

**Reality-anchored fix-direction space (no implementation in this
round — the user picks):**

- **B5-α — controller-side: tighten ARR-LAND / ARR-LAND-TNG gate
  on observed circuit-position report.** Add a `HasReportedCircuitPosition`
  guard (or extend `LandingConditions`) so the rule does not fire
  until the controller has *observed* the pilot's Downwind/Base/Final
  report. CAP 413 §4.45-4.49 doctrinal: clearance follows the
  position call, not observation alone. C2/C3's strip-based
  `IsCircuitTrafficByStrip` would still drive commitment formation
  / DEP-CIRCUIT-COMPLETE advancement; only the actual `ClearLandAction`
  / `ClearTouchAndGoAction` emission would be gated. Risk surface:
  the report-gating must compose with `ARR-CONTINUE` (when runway
  not clear — shouldn't matter, ARR-CONTINUE doesn't issue land
  clearance) and with `ARR-LAND-REISSUE` (re-issue path also gates
  on the observed-report so a stepped-on first-issue doesn't
  re-fire prematurely). Plan-review focus: does the
  observed-report gate break `LowgGoldenTest`'s G0 timing? G0's
  pilot reports Downwind cleanly; the gate should be transparent
  for G0.

- **B5-β — pilot-side: two-stage transition on
  ClearedToLand-when-T&G-mission-shape mismatch + on post-touchdown
  vacate instruction.** When the pilot receives `ClearedToLand` and
  the active circuit task is `TouchAndGo`, the fix splits across
  two transitions to respect kinematic timing (the aircraft is
  airborne on final at receipt; ground-vacate kinematics only
  apply post-touchdown):
  - **Stage 1 — on `ClearedToLand` receipt**: replan the mission
    tree only. Collapse the active TouchAndGo + remaining circuit-
    pattern siblings under `CircuitTraining` to a fall-through
    `groundArrivalTask`. Mark `hasClearance = true` via the
    existing `handleLandingClearance` semantic. Do **NOT** touch
    `PilotIntent.phase` — the kinematic layer correctly maintains
    the descent profile on final approach. Mission step advances
    `LAND` next (CompletionMode.PHYSICAL — completes on
    touchdown), then `groundArrivalTask`'s steps post-touchdown.
  - **Stage 2 — on `BacktrackRunway` / `AfterLandingVacateVia`
    receipt while on the runway post-touchdown**: the kinematic
    ground-vacate transition. Extend `processInstruction` so
    these instructions match at any post-touchdown on-runway step
    (not only `AWAIT_VACATE_INSTRUCTION` — handles the M3 case
    where the pilot's step is `LAND` complete or
    `REPORT_RUNWAY_VACATED`). Advance the step + set pilot intent
    to taxi the vacate/backtrack route. `phase = LandingRoll` is
    the correct kinematic state here (post-touchdown,
    decelerating on the runway pre-vacate); `route =
    vacateRoute, targetSpeedMps = taxiSpeed` set when the route
    is built.
  Phase 3 round 1's earlier `collapseToGroundArrival` attempt
  conflated both stages into a single ClearedToLand-receipt-time
  transition, forcing kinematic ground-vacate while the aircraft
  was still airborne — the kinematic layer correctly overrode it
  and the pilot lifted off again. The two-stage contract avoids
  this. Risk surface: touches go-around / join-circuit / cross-
  aerodrome flows that share mission-tree replan discipline; the
  Stage 2 `processInstruction` widening must compose with the
  existing AWAIT_VACATE_INSTRUCTION handling for normal full-stop
  arrivals (idempotent — same step-mark-complete + intent-update
  shape).

- **B5-γ — broader sim-radio fix: per-frequency `frequencyBusyUntil`
  tracker.** Already documented in Phase 3 round 1 as
  `D-PASS-cross-aircraft-step-on`. Phase 3 round 1 attempted this and
  broke G2's handoff timing. Filed for a future dedicated pass with
  impact-aware design. Not part of B5's immediate fix space.

**Recommendation (informational; user picks):** B5-α has the smaller
blast radius (controller-only, 1-2 guard files + targeted rule
gates) and aligns most cleanly with CAP 413's doctrine. B5-β is the
deeper architectural fix but touches pilot-side replan discipline
that the project is still evolving (go-around / replan flows in
flight). The cleanest sequencing is α first — confirm the
controller-side gating restores G1's first-circuit T&G path. If a
residual case still surfaces from intent ambiguity, β follows in a
later pass with proper plan-review.

#### Phase 3 round 2 — Review considerations (per `feedback_plans_review_aware.md`)

This subsection covers the new direction recommendations introduced
in Phase 3 round 2 (B5-α / B5-β / B5-γ sequencing). A future plan-
review pass before the B5 fix lands will iterate on these, but the
load-bearing concerns are captured inline here so silence isn't a
review-finding gap.

**FP / type-safety (B5-α path — controller-side):**
- New BDI guard (e.g. `HasReportedCircuitPosition(legs:
  Set<LegName>?)`). The witness MUST be **commitment-scoped**, not
  a flat `Map<AircraftId, Set<ReportEvent>>` on `BeliefState`.
  Earlier draft of this subsection sketched a flat surface; codex
  review iteration 2 caught the stale-belief class — a flat map
  would let A's first-circuit Downwind report unlock A's second-
  circuit landing clearance, recreating the same stale-witness
  failure mode that Phase 2's `circuitIntent` work surfaced. The
  correct shape mirrors Phase 2's `Commitment.touchedDownDuring
  Commitment` / `pilotReadyDuringCommitment` discipline:
  - `Commitment.observedReportsDuringCommitment:
    Set<ReportEvent>` (or `Set<LegName>` if only legs matter) —
    sticky witness, default empty on commitment formation, set in
    `reconcileObservedStages` for `TOWER_ARRIVAL` when an observed
    `ReportEvent` matches the aircraft. Reset implicitly on
    commitment lifecycle: a fresh commitment formation (e.g. fresh
    `AwaitDownwind` after T&G `ARR-TNG-AIRBORNE` completes the
    prior arrival) gets the default-empty value.
  - The guard reads the field on the aircraft's CURRENT
    commitment. Stale reports from prior commitments (e.g. first-
    circuit Downwind for a now-active second-circuit commitment)
    are structurally invisible.
- Reset/reversal points to verify in the implementation pass:
  - **Commitment formation** (fresh `AwaitDownwind` for
    `TOWER_ARRIVAL`): default empty.
  - **T&G commitment completion** (`ARR-TNG-AIRBORNE` fires →
    next commitment forms): default empty.
  - **Go-around** (commitment regresses to `AwaitDownwind` via
    `ARR-GO-AROUND-CLEARANCE-ISSUED`): RESET to empty (the
    pilot's pre-go-around reports don't satisfy the post-go-
    around landing clearance gate).
  - **Handoff / responsibility transitions**: per the existing
    BeliefState commitment-on-aircraft scoping, a commitment
    moves with its aircraft within a controller's purview;
    cross-controller transfer doesn't preserve the witness
    (typed: separate Commitment instance, separate field).
  - **Circuit re-entry** (e.g. extended downwind / orbit): no
    reset — the aircraft is still on the same commitment,
    pre-existing reports remain valid.
- Targeted regression test in the implementation pass: "first-
  circuit Downwind report must NOT satisfy
  `HasReportedCircuitPosition` for the second-circuit commitment"
  — a `BeliefState` fixture with two sequential commitments and
  the witness scoped to the active one only.
- No new `ControllerDecisionResult` shape change. Pure-decide
  contract preserved (per `feedback_architecture.md`).
- `BeliefState` already carries `circuitIntent: Map<AircraftId,
  CircuitIntent>` (M2's mechanism path) AT THE TOP LEVEL — note
  this is the PRE-Phase-2 shape that still has the stale-belief
  class for circuitIntent itself. Phase 2 closed circuitIntent's
  staleness via the `touchedDownDuringCommitment` witness sticky
  on `Commitment`, not by re-scoping circuitIntent. The B5-α
  guard MUST be on `Commitment`, not at `BeliefState` top level,
  to avoid recreating a stale-belief class for reports that the
  field itself was supposed to fix.
- Failure-closed default: if the witness is empty for an
  aircraft's current commitment, the rule does NOT fire. A future
  scenario where the witness is mis-populated surfaces as
  "ARR-LAND never fires" (live wedge — surfaces in tests) rather
  than "ARR-LAND fires too eagerly" (dangerous silent regression).

**FP / type-safety (B5-β path — pilot-side):**
- Mission-tree replan is a `PilotMission → PilotMission` transform.
  Existing `replaceChild` infrastructure (`PilotMission.kt`)
  handles the structural work. The new function (e.g.
  `collapseToGroundArrival`) takes `(mission: PilotMission, now:
  SimTime): PilotMission` and is total — non-circuit-task missions
  flow through unchanged.
- **Two-stage timing — load-bearing constraint** (codex review
  iteration 2 finding): at `ClearedToLand` receipt the aircraft is
  still AIRBORNE on final approach. Resetting kinematic intent to
  `LandingRoll` + vacate route at that moment would force the
  pilot into ground-vacate kinematics before they've physically
  landed — breaks the kinematic chain. The fix splits into two
  transitions:
  1. **Stage 1 — on `ClearedToLand` receipt** (when active
     circuit task is `TouchAndGo`): replan the mission tree only.
     Collapse the active TouchAndGo + remaining circuit-pattern
     siblings under `CircuitTraining` to a fall-through
     `groundArrivalTask`. Mark `hasClearance = true` (existing
     `handleLandingClearance` semantic). Do NOT touch
     `PilotIntent.phase`. The pilot continues flying the final
     approach kinematically — same as a plain `circuitTask()`
     full-stop arrival. Mission step advances `LAND` next, then
     the `groundArrivalTask`'s steps after touchdown.
  2. **Stage 2 — on `BacktrackRunway` / `AfterLandingVacateVia`
     receipt** (when aircraft is on the runway post-touchdown,
     mission step is `LAND` complete or `REPORT_RUNWAY_VACATED`):
     this is the kinematic ground-vacate transition. Update
     `processInstruction` so BacktrackRunway / AfterLandingVacateVia
     match at any step where the pilot is on the runway post-
     touchdown (not only `AWAIT_VACATE_INSTRUCTION`), advancing
     the step + setting the pilot's intent to taxi the
     vacate/backtrack route. `PilotPhase.LandingRoll` is the
     correct phase here (kinematically post-landing, decelerating
     on the runway pre-vacate); `route = vacateRoute,
     targetSpeedMps = taxiSpeed` only after the route is set up.
- The Phase 3 round 1 attempt's hidden bug: it tried to do BOTH
  stages on ClearedToLand receipt, which forced kinematic ground-
  vacate while the aircraft was still airborne. Pilot's kinematic
  layer (`Pilot.kt:planRoute`) re-derived an airborne route on
  the next tick because the mission step was still `LAND`
  (CompletionMode.PHYSICAL — incomplete because aircraft was
  airborne) AND the existing kinematic logic correctly maintained
  the airborne descent profile on final approach. The fix is to
  defer kinematic ground-vacate to Stage 2 (post-touchdown),
  which is when real pilots receive vacate instructions.
- Sealed `PilotPhase` discrimination ensures both transitions
  remain typed: Stage 1 leaves `phase` untouched (still
  `Final` / `LandingRoll` per kinematic layer); Stage 2 sets
  `phase = LandingRoll` explicitly (idempotent if already
  `LandingRoll` post-touchdown).

**Test architecture:**
- B5-α tests: a controller-only spec exercising the new guard
  against `BeliefState` fixtures with / without observed reports.
  Existing `controller/jvmTest` style (e.g. similar to
  `RunwayLengthGatingSpec` or the `Tower*Spec` files) is the
  precedent.
- G1 closure proof: G1 + the new minimal `circuits=1` two-aircraft
  pin (per fn-8.3 acceptance bullet 5). Both must go green for
  fn-8.3 closure.
- G0 / G2 byte-stability: the load-bearing regression risk. G0's
  pilot reports Downwind cleanly so the new guard fires
  transparently; G2's pilot also reports cleanly on the LJMB
  pattern. Re-baseline policy (decision #9): if a fix shifts pinned
  values doctrinally-correctly, re-baseline with explicit
  rationale; otherwise fix the regression.
- No scaffold tests (per `feedback_testing_philosophy.md`). The
  guard's first real-job test IS the G1 minimal pin; targeted
  controller spec is "the rule + this guard fires under these
  beliefs" — that's a real-job assertion.

**Impact:**
- B5-α surface: 1-3 production files in `:controller`
  (`bdi/Guard.kt`, `procedure/TowerArrival.kt`, possibly
  `observe/BeliefState.kt` for the new field). New BDI atom in
  `Guard.kt` (sealed leaf addition — exhaustiveness compiles
  loud across all guard consumers).
- B5-β surface: 2-4 production files in `:pilot`
  (`PilotCognitive.kt`, `Pilot.kt`, `PilotMission.kt` for the
  helper, `processInstruction` arms for BacktrackRunway /
  AfterLandingVacateVia at non-AVI step). Touches go-around /
  join-circuit replan flows by proximity.
- B5-γ (deferred): broader sim-radio infra; out of fn-8.3 scope
  per `D-PASS-cross-aircraft-step-on`.
- Pre-write audit at fix time per `feedback_pass_scope`
  discipline. If B5-α's guard introduction touches > 10 BDI rule
  call sites or B5-β's pilot replan touches > 10 mission-tree
  call sites, STOP and re-plan-review before landing.

**Operational correctness:**
- B5-α citation discipline (per `feedback_reality_anchored.md`):
  CAP 413 §4.45-4.49 is the doctrinal source for "pilot calls
  Downwind first; controller responds with landing clearance."
  ICAO Doc 4444 §7.10 (landing clearance procedure) corroborates:
  clearance is issued "when the pilot reports on final" or after
  the pilot's position call. Verify these section numbers
  against local research text or canonical PDF before commit; do
  not commit speculative section numbers (per pass-2 plan-review
  nitpick #4 that fn-8.3 inherited).
- B5-α reality check: the change makes the controller WAIT for
  the pilot's report before clearing. Real ATC behaviour aligns
  — at controlled aerodromes the pilot's downwind/base/final call
  drives clearance issuance; observation alone (e.g. radar)
  triggers traffic-info calls, not landing clearances. The
  current code's "fire on observation alone" is the doctrinally-
  wrong shape that B5 surfaces.
- B5-β reality check: pilots who receive "cleared to land" when
  they expected "cleared touch and go" comply with the cleared-
  to-land instruction. They do NOT lift off again unbidden. The
  current sim's "pilot lifts off because mission tree says
  FLY_DEPARTURE next" is the doctrinally-wrong shape that M4
  surfaces. The replan must resolve the intent mismatch in favour
  of the controller's most recent clearance.
- B5-γ (filed): cross-aircraft step-on remains a real-radio
  phenomenon (real radios ARE half-duplex on a single frequency).
  The current sim model approximates this but with a known
  staleness gap; the deferment captures the contract.

**Sequencing rationale:**
- α first: smallest blast radius, doctrinally cleanest, controller-
  only. Verifies the M1 mechanism is the load-bearing root cause.
- β follows if needed: handles pilot-side residual cases the
  controller-only fix can't cover (e.g. delayed delivery where
  pilot has already advanced past the divergent step before the
  controller's intent-aligned clearance arrives). Plan-review
  cycle when β's scope is concrete.
- γ remains deferred: independent infra problem with its own
  impact-aware design.

**STOP-and-report disposition (per spec abort criterion #10 +
worker.md "third phase needed" pattern):** fn-8.3 stays
in_progress. G1 closure work continues in a follow-on session
under one of the directions above. The Phase 3 round 2 deliverable
is this confirmation evidence + the deferment filings below.

**Deferments filed (per acceptance bullet 12 — captured in
`~/.claude/plans/pilot-firewall.md`):**

- `D-PASS-g1-diagnostics` — partial closure recorded: the existing
  trace harness was sufficient for the entire fn-8.3 dive cycle
  (Phase 1, Phase 2 round 1, Phase 3 round 1, Phase 3 round 2). No
  typed events on `:common` were needed.
- `D-PASS-g1-diagnostics-typed-events` — placeholder retained,
  unfired. Trigger: a future closure pass where `BeliefState`
  snapshots are insufficient because the relevant state is mid-
  decide-cycle and not surfaced.
- `D-PASS-cross-aircraft-step-on` — broader sim-radio infra
  (per-frequency `frequencyBusyUntil` tracker). Tried in Phase 3
  round 1, reverted (broke G2). Filed for a dedicated pass with
  impact-aware design. Trigger: future multi-aircraft scenario
  where same-frequency same-instant cross-aircraft transmissions
  collide and C1's same-aircraft `pilotRadioFreeAt` doesn't help.
- `D-PASS-pilot-mid-tng-fullstop-recovery` — the B5 wedge as
  refined here. Two real-fix paths (full contracts in
  `~/.claude/plans/pilot-firewall.md § fn-8 deferments` and the
  Phase 3 round 2 Review considerations subsection above):
  - α (controller-side, recommended first): `ARR-LAND` /
    `ARR-LAND-TNG` gate on a commitment-scoped
    `Commitment.observedReportsDuringCommitment` sticky witness
    (mirroring Phase 2's `touchedDownDuringCommitment` discipline
    so first-circuit reports cannot satisfy second-circuit
    landing clearance).
  - β (pilot-side, two-stage timing): Stage 1 on `ClearedToLand`
    receipt replans mission tree only and leaves kinematics
    untouched while airborne; Stage 2 on post-touchdown
    `BacktrackRunway` / `AfterLandingVacateVia` receipt performs
    the kinematic ground-vacate (extends `processInstruction`
    matching to any post-touchdown on-runway step).
  Trigger: this entry IS the trigger — fn-8.3's next session
  opens it.

**Test commands run (Phase 3 round 2 evidence):**
```
nix develop --command ./gradlew :sim:jvmTest \
  --tests xyz.easiersaid.twr.sim.G1ClosureDiveTest \
  --tests xyz.easiersaid.twr.sim.LowgGoldenTest \
  --tests xyz.easiersaid.twr.sim.G2CrossAerodromeVfrTest \
  --tests xyz.easiersaid.twr.sim.G1TwoAircraftCircuitsTest \
  --console=plain --rerun-tasks
```
Result: G0 + G2 + G1ClosureDive green; G1 fails at B-mission-incomplete
check (B5 wedge confirmed).
