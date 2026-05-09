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
