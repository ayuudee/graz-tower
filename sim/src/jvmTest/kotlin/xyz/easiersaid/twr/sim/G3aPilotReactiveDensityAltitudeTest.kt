package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.fail
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.pilot.CompletionMode
import xyz.easiersaid.twr.pilot.CompoundTask
import xyz.easiersaid.twr.pilot.DensityAltitudeInput
import xyz.easiersaid.twr.pilot.HighLevelGoal
import xyz.easiersaid.twr.pilot.MissionStep
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.pilot.PilotRoute
import xyz.easiersaid.twr.pilot.PrimitiveTask
import xyz.easiersaid.twr.pilot.TaskNode
import xyz.easiersaid.twr.pilot.computeDensityAltitudeFeet
import xyz.easiersaid.twr.pilot.createMission
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.Request
import xyz.easiersaid.twr.protocol.RequestTaxi
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.sim.testing.Fixtures
import xyz.easiersaid.twr.sim.testing.controllerByRole
import xyz.easiersaid.twr.sim.testing.formatJourney
import xyz.easiersaid.twr.sim.testing.load
import xyz.easiersaid.twr.sim.testing.missionStepTransitions
import xyz.easiersaid.twr.sim.testing.positionPointTransitions
import xyz.easiersaid.twr.sim.testing.runUntilWithStateTrace

/**
 * G3a-react-density-altitude — single-aerodrome single-aircraft VFR
 * **pilot-reactive** apron-stay decline triggered by a fixture-authored
 * hot-day OAT whose density altitude on the aerodrome elevation exceeds
 * the aircraft type's `maxDensityAltitudeFt` advisory (FAA AC 61-107B
 * §3-1 — light-GA training-aircraft DA threshold).
 *
 * Sibling-by-shape of [G3aPilotReactiveCrosswindTest] (fn-14.2 — crosswind
 * axis) and [G3aPilotReactiveTailwindTest] (fn-15.2 — tailwind axis),
 * **distinctively distinguished** as the **first apron-side reactive
 * recognition** in the codebase. The wind-axis siblings recognise an
 * exceedance on `phase = Final` and respond with a go-around (in-flight
 * recovery via subtree-rewrite); this test recognises an exceedance at
 * `phase = AtStand` (still on the apron, REQUEST_TAXI not yet emitted)
 * and responds with a `NON_COMPLETING` `DECLINE_DEPARTURE` primitive
 * (apron-terminal — no recovery, no go-around envelope, the mission
 * never advances past the decline).
 *
 * Single AI aircraft at LOWG (C172, `maxDensityAltitudeFt = Feet.unsafe(5000)`
 * — FAA AC 61-107B §3-1 light-GA training threshold; the C172 leaf models
 * the light-GA regime — B738's leaf is `null` per the per-type
 * doctrinal-applicability asymmetry locked at fn-28 round-5 Major 2:
 * jet-class types have flat-rated thrust and high-altitude design, so
 * the light-GA DA decline concept does not apply). Mission
 * `HighLevelGoal.CircuitTraining(outcomes = [FullStop])` — one planned
 * circuit; in the absence of a DA-decline regression, this would taxi
 * out, depart, fly a single full-stop circuit, and return to the stand
 * (the [LowgGoldenTest] G0 baseline). With the high-DA fixture in play,
 * the pilot's `derivePilotEvent` DA-decline branch fires
 * `PilotEvent.DensityAltitudeDecline` on the very first decision tick
 * (BEFORE the cognitive layer emits `Request(RequestTaxi)`),
 * `applyDensityAltitudeDecline` rewrites the GroundDeparture compound's
 * suffix via [CompoundTask.replaceFromActivePrimitive] to
 * `[PrimitiveTask(DECLINE_DEPARTURE, NON_COMPLETING)]` and returns
 * `suppressSameTickCognitive = true`; the same-tick `Request(RequestTaxi)`
 * is zeroed by `pilotDecide`'s suppression mechanism (round-13 Major 1 —
 * applied BEFORE every `PilotOutput` construction site, both
 * `PlanRouteOutcome.Plan` and `PlanRouteOutcome.Skip`).
 *
 * **DA computation is numerical, not prose** (R17 — round-4 Major 4):
 * the test reads OAT + QNH from the [Fixtures.LOWG_HIGH_DA] fixture +
 * field elevation from the LOWG world data, constructs a
 * [DensityAltitudeInput], calls [computeDensityAltitudeFeet], and
 * asserts the result is strictly above C172's 5000 ft advisory. NO
 * "DA ≈ 5500" prose approximations — the named pure function is the
 * single source of truth (its KDoc is the doctrinal anchor; the
 * fixture chooses OAT so the formula's output sits comfortably above
 * the threshold; the test's failure message includes the computed
 * value AND the threshold so any drift in either is immediately
 * legible).
 *
 * **Doctrinal anchors:**
 *  - **FAA AC 61-107B §3-1** — *"Density Altitude and Aircraft
 *    Performance"*. Light-GA training-aircraft DA decline threshold;
 *    captured at the formula-residency site (KDoc on
 *    [computeDensityAltitudeFeet]) and at the per-type threshold
 *    (KDoc on `AircraftType.maxDensityAltitudeFt`).
 *  - **FAA Pilot's Handbook of Aeronautical Knowledge (FAA-H-8083-25C)
 *    Chapter 4** — atmospheric ISA constants and lapse rates; the
 *    formula's constants are doctrine-anchored in [computeDensityAltitudeFeet]'s
 *    `internal const val` declarations.
 *  - **FAA AFH (FAA-H-8083-3C) Chapter 11** — high-DA takeoff
 *    performance and pilot decision-making framing. The pilot's
 *    decision shape ("if computed DA exceeds the type's advisory
 *    threshold, decline departure rather than depart with degraded
 *    performance margin") matches AFH Ch 11's narrative.
 *  - **ICAO Annex 6 Part II §2.4** — PIC final authority (no clearance
 *    or ATC instruction is required to decline departure; the PIC's
 *    decision is the load-bearing observable).
 *
 * **Sibling tests** (reactive-GA + reactive-decline taxonomy):
 *  - G0 ([LowgGoldenTest]) — single-aircraft single-aerodrome circuit
 *    training (full-stop only; no GA, no decline). Structural template
 *    AND the baseline this test diverges from at decision tick 1
 *    (under the high-DA fixture, the apron-stay branch fires; under
 *    [Fixtures.LOWG]'s `oat = 12.79°C` the branch is silent and the
 *    G0 trace plays out unmodified).
 *  - G3a-react-crosswind ([G3aPilotReactiveCrosswindTest]) — single-
 *    aircraft pilot-reactive go-around triggered by a world-authored
 *    wind shift past C172's `maxCrosswindKnots`; on-final recognition,
 *    in-flight subtree-rewrite via `applyCrosswindGoAround`.
 *  - G3a-react-tailwind ([G3aPilotReactiveTailwindTest]) — sibling of
 *    crosswind on the tailwind axis (POH/AFH-advisory `maxTailwindKnots`).
 *  - G3a-trained ([G3aPilotTrainedGoAroundTest]) — instructor-authored
 *    `CircuitOutcome.GoAround`; mission-driven, not weather-driven.
 *  - G3a-obstruction ([G3aRunwayObstructionTest]) — controller-side
 *    reactive GA off a world-authored runway obstruction.
 *
 * **What G3a-react-density-altitude distinctively pins** (the three-layer
 * pin per fn-11.2 / fn-12.3 / fn-14.2 / fn-15.2 discipline):
 *
 *  - **Layer 1 (causal partial-order, R17 numerical)** — the
 *    `computeDensityAltitudeFeet` pure function consumes the fixture's
 *    OAT + QNH + LOWG's elevation and returns a value strictly above
 *    C172's `maxDensityAltitudeFt = 5000 ft` advisory. The test
 *    asserts on the function's output, NOT on a hand-computed prose
 *    value (round-3 Major 4 / R17). A regression that changed any
 *    formula constant, OAT slot, QNH slot, or elevation source would
 *    surface here with the exact computed-vs-threshold delta in the
 *    failure message.
 *
 *  - **Layer 2 (sticky-witness regression — mission tree rewrite)** —
 *    the final mission's task-tree contains `MissionStep.DECLINE_DEPARTURE`
 *    AND the active primitive (`mission.currentTask`) is
 *    `(DECLINE_DEPARTURE, NON_COMPLETING)`. The pre-decline GroundDeparture
 *    children (`REQUEST_TAXI`, `TAXI_TO_HOLDING`, `RUN_UP_CHECKS`, ...)
 *    have been replaced by the suffix-rewrite primitive
 *    [CompoundTask.replaceFromActivePrimitive] called from
 *    `applyDensityAltitudeDecline`. Outer-level later siblings (e.g.
 *    `FLY_DEPARTURE`, `SHUTDOWN`) are preserved per R13's "leave outer
 *    parents intact" contract — but the mission's `currentTask` never
 *    advances past the `NON_COMPLETING` `DECLINE_DEPARTURE`, so those
 *    outer-level siblings are operationally unreachable.
 *
 *  - **Layer 3 (kinematic non-event, R14 transmission-suppression)** —
 *    the aircraft NEVER moves: `positionPoint` is constant (no
 *    transition from `LOWG_STAND_1_POINT`), `altitudeM == 0.0`,
 *    `targetSpeedMps == 0.0`, `route is PilotRoute.None`. The decision
 *    cycle that fires `DensityAltitudeDecline` emits ZERO
 *    `Request(RequestTaxi)` transmissions across the entire run (R14 —
 *    the per-step cognitive transmission is zeroed by
 *    `pilotDecide`'s suppression mechanism). Without the suppression,
 *    `pilotDecide`'s cognitive layer would still advance `REQUEST_TAXI`'s
 *    first-tick transmission slot AND the apply path would rewrite
 *    the tree — the two would race, with the radio fire visible BEFORE
 *    the next decision tick's tree-walk. The ZERO `Request(RequestTaxi)`
 *    assertion is the load-bearing R14 pin.
 *
 * **Distinctive shape vs the wind-axis siblings** (load-bearing for
 * cognitive-clarity at maintenance time):
 *  - **Static fixture, NO mutation hook**: the crosswind + tailwind
 *    tests author a two-transition wind shift via
 *    `runUntilWithStateTrace`'s `onAfterEvent` (one-shot at a gate,
 *    one-shot at recovery). DA decline is recognised on the FIRST
 *    pilot decision tick (BEFORE any taxi request) — the hot-day OAT
 *    is in the fixture's static `weather.oat` slot, no mid-run
 *    mutation is required. This is a deliberate shape difference, NOT
 *    a missed opportunity to mirror the wind axes (round-10 Minor 1).
 *  - **Apron-terminal, NO recovery / NO go-around envelope**: the
 *    wind-axis siblings emit `Report(GoingAround)` + recover via a new
 *    circuit. DA decline does NOT emit a radio transmission (R14
 *    cognitive-suppression — no `Request(RequestTaxi)`, no
 *    `Report(DecliningDeparture)` event today; future doctrinal
 *    additions are out of scope per fn-28 boundaries) and the mission
 *    never advances past `DECLINE_DEPARTURE`.
 *  - **Mission never completes**: the wind-axis siblings end with
 *    `mission.isComplete == true` (recovery circuit lands, aircraft
 *    parked). DA decline ends with the mission's `currentTask` pinned
 *    at the `NON_COMPLETING` `DECLINE_DEPARTURE` primitive — the
 *    `mission.isComplete` query is `false` forever. The test runs the
 *    sim long enough for the decision tick to fire + a few subsequent
 *    ticks to verify no spurious advancement (5 sim minutes wall —
 *    far longer than the recognition + apply latency, far shorter
 *    than any plausible recovery if the decline regressed to a
 *    completing primitive).
 *
 * **Closes the third pilot-reactive POH/AFH recognition axis** as the
 * sixth reactive-GA path (after crosswind, tailwind, and the three
 * controller-side reactive paths). Closes
 * `D-PASS-g3a-react-other-poh-triggers` per the deferment register's
 * §8 archive policy — see fn-28's epic spec acceptance + R17 doctrine
 * anchor.
 *
 * @see G3aPilotReactiveCrosswindTest the crosswind sibling — same
 *      fixture-shape ([Fixtures.LOWG] vs [Fixtures.LOWG_HIGH_DA] differ
 *      only on the OAT slot), same three-layer pin shape; distinguishing
 *      surfaces are the recognition axis (wind on final vs DA on
 *      apron), the mission shape at recognition (circuit-like vs
 *      pre-taxi), and the response shape (GA + recovery vs apron-
 *      terminal `NON_COMPLETING` decline).
 * @see G3aPilotReactiveTailwindTest the tailwind sibling — same shape
 *      considerations as the crosswind sibling.
 * @see Fixtures.LOWG_HIGH_DA the high-DA fixture variant — full numeric
 *      provenance in its KDoc; sole distinguishing surface vs
 *      [Fixtures.LOWG] is the OAT slot (`50.0°C` vs `12.79°C`).
 * @see computeDensityAltitudeFeet the named pure function R17 anchors
 *      against — the formula, constants, and rounding live there; this
 *      test asserts on the function's output, not on hand-computed
 *      prose.
 */
class G3aPilotReactiveDensityAltitudeTest {

    @Test
    fun `high-DA fixture OAT triggers pilot DA decline at LOWG apron and no taxi request fires`() {
        // ── World + controllers via the high-DA fixture ─────────────────────
        // [Fixtures.LOWG_HIGH_DA] overrides only the OAT slot vs the baseline
        // [Fixtures.LOWG]; every other surface (stand, controllers, flight
        // plan, runway selection, ATIS path) is identical so any deviation
        // from the baseline G0 ([LowgGoldenTest]) flow is caused by the DA
        // recognition firing on the first decision tick.
        val fixture = Fixtures.LOWG_HIGH_DA
        val loaded = fixture.load().getOrElse {
            fail("LOWG_HIGH_DA fixture failed to load: $it")
        }
        val lowg = AerodromeId("LOWG")
        val ground = checkNotNull(loaded.controllerByRole(RoleName.GROUND)) {
            "GROUND missing from LOWG_HIGH_DA fixture"
        }
        val tower = checkNotNull(loaded.controllerByRole(RoleName.TOWER)) {
            "TOWER missing from LOWG_HIGH_DA fixture"
        }

        // ── One AI aircraft (C172) at the stand, mission = one full-stop circuit ──
        // Identical to G0's setup; the DA decline branch fires regardless of
        // the goal because the recognition gate is mission-shape (pre-taxi)
        // not goal-shape (CircuitTraining vs Departure). Single full-stop
        // circuit is the smallest viable goal — any baseline that would have
        // taxied + departed in absence of the decline.
        val aircraftId = AircraftId("OE-ABC")
        val now = SimTime.ZERO
        val singleFullStop = HighLevelGoal.CircuitTraining(
            outcomes = listOf(CircuitOutcome.FullStop),
        )
        val mission = createMission(
            goal = singleFullStop,
            startPhase = PilotPhase.AtStand,
            time = now,
            filedPlan = fixture.flightPlans[aircraftId]
                ?: fail("LOWG_HIGH_DA fixture missing flight plan for $aircraftId"),
        )
        val aircraft = AircraftState(
            id = aircraftId,
            callsign = Callsign("OEABC"),
            position = loaded.world.geometry.points.getValue(fixture.standPointId),
            positionPoint = fixture.standPointId,
            phase = PilotPhase.AtStand,
            type = AircraftType.C172,
            pilotMission = mission,
        )

        // ── Build SimState through the smart constructor ────────────────────
        val initialState = SimState.initial(
            seed = 42L,
            world = loaded.world,
            worldIndex = loaded.worldIndex,
            aircraft = listOf(aircraft),
            controllers = listOf(ground, tower),
            weatherByAerodrome = mapOf(lowg to fixture.weather),
        ).getOrElse { error("SimState.initial rejected the LOWG_HIGH_DA fixture: $it") }

        // ── Drive ───────────────────────────────────────────────────────────
        // 5 sim minutes — far longer than the recognition + apply latency
        // (which fires on tick 1 within milliseconds of sim start), far
        // shorter than any plausible recovery if the decline regressed to a
        // completing primitive (G0's baseline lands in 10-22 sim minutes; if
        // the decline ever advanced past `DECLINE_DEPARTURE`, this run would
        // either advance into REQUEST_TAXI / TAXI_TO_HOLDING within seconds
        // or remain at-rest for the full 5 minutes — either way the assertion
        // set below catches it).
        val until = SimTime.ZERO + SimDuration.ofMillis(5 * 60 * 1000L)

        // ATIS publication kept symmetric with G0 ([LowgGoldenTest]) — ensures
        // the controller's BeliefState.expectedAtisLetter folds and the
        // controller-side runtime is exercised identically up to the moment
        // the pilot diverges (which is decision tick 1). Without ATIS, a
        // future controller-side defect masked by the absence of an ATIS
        // path could mistakenly "look right" in this test's trace.
        val lowgAtis = xyz.easiersaid.twr.protocol.Atis(
            letter = 'A',
            aerodrome = lowg,
            configuration = xyz.easiersaid.twr.protocol.RunwayConfiguration(
                arrivals = listOf(xyz.easiersaid.twr.protocol.RunwayId("16C")),
                departures = listOf(xyz.easiersaid.twr.protocol.RunwayId("16C")),
            ),
            wind = xyz.easiersaid.twr.protocol.Wind.unsafe(160, 8),
            qnh = null,
            visibility = null,
            generatedAt = now,
        )
        val initialEvents = loaded.initialEvents + listOf(
            SimEvent.AtisIssued(time = now, aerodrome = lowg, atis = lowgAtis),
            SimEvent.PilotDecisionTick(time = now, aircraftId = aircraftId),
            SimEvent.PhysicsTick(time = now),
            SimEvent.ControllerCycle(time = now, controllerId = ground.id),
            SimEvent.ControllerCycle(time = now, controllerId = tower.id),
        )

        // Deliberate no-op hook: DA decline is recognised on the static
        // fixture's OAT — there's no mid-run world mutation, unlike the
        // crosswind/tailwind siblings. The `onAfterEvent` default is
        // identity; passing it explicitly here pins the contract for the
        // reader ("we do NOT mutate the world during this run").
        val (finalState, records, trace) = runUntilWithStateTrace(
            initialState = initialState,
            initialEvents = initialEvents,
            untilTime = until,
        )

        // ── Diagnostic preamble ─────────────────────────────────────────────
        val journey = finalState.formatJourney(aircraftId, records)
        println(journey)

        println()
        println("─── G3a-react-density-altitude per-aircraft trace summary ───")
        println("Aerodrome elevation:        ${loaded.world.aerodromes.getValue(lowg).elevation.value} ft")
        println("Fixture OAT:                ${fixture.weather.oat?.celsius ?: "<NULL>"} °C")
        println("Fixture QNH:                ${fixture.weather.qnh}")
        println("Mission step transitions:")
        for (t in trace.missionStepTransitions(aircraftId)) {
            val fromStr = t.from.fold({ "absent" }, { it.name })
            val toStr = t.to.fold({ "absent" }, { it.name })
            println("  [${t.after.time.millis}ms] $fromStr → $toStr")
        }
        println("positionPoint transitions:")
        for (t in trace.positionPointTransitions(aircraftId)) {
            val fromStr = t.from.fold({ "absent" }, { it.value })
            val toStr = t.to.fold({ "absent" }, { it.value })
            println("  [${t.after.time.millis}ms] $fromStr → $toStr")
        }
        println("─── end G3a-react-density-altitude per-aircraft trace summary ───")
        println()

        // ── Layer 1 — Numerical DA via computeDensityAltitudeFeet (R17) ─────
        //
        // The named pure function is the single source of truth for the DA
        // computation. Build a [DensityAltitudeInput] from the fixture's
        // OAT/QNH + LOWG's elevation and assert the function's output is
        // strictly above C172's 5000 ft `maxDensityAltitudeFt` advisory
        // (FAA AC 61-107B §3-1).
        //
        // NO prose approximations (round-3 Major 4 / R17). The failure
        // message includes the doctrinal anchor + threshold + computed
        // value so any drift in either is immediately legible — a
        // regression that altered the formula constants, the OAT slot,
        // the QNH slot, or the elevation source surfaces here with the
        // exact delta.
        val aerodromeElevation = loaded.world.aerodromes.getValue(lowg).elevation
        val fixtureOat = fixture.weather.oat
            ?: fail("LOWG_HIGH_DA fixture's WeatherObservation.oat is null — fixture regression")
        val fixtureQnh = fixture.weather.qnh
            ?: fail("LOWG_HIGH_DA fixture's WeatherObservation.qnh is null — fixture regression")
        val daInput = DensityAltitudeInput(
            oat = fixtureOat,
            qnh = fixtureQnh,
            fieldElevation = aerodromeElevation,
        )
        val computedDa = computeDensityAltitudeFeet(daInput)
        val c172Threshold = checkNotNull(AircraftType.C172.maxDensityAltitudeFt) {
            "C172.maxDensityAltitudeFt must be non-null (FAA AC 61-107B §3-1 light-GA " +
                "training advisory = 5000 ft); null would mean the per-type doctrinal " +
                "applicability inverted between fn-28.2 and fn-28.3."
        }
        check(computedDa.value > c172Threshold.value) {
            "R17 numerical DA pin (FAA AC 61-107B §3-1): computeDensityAltitudeFeet(" +
                "oat=${fixtureOat.celsius}°C, qnh=$fixtureQnh, fieldElevation=" +
                "${aerodromeElevation.value} ft) returned ${computedDa.value} ft; expected " +
                "strictly > C172.maxDensityAltitudeFt = ${c172Threshold.value} ft. A regression " +
                "below the threshold means either the fixture's OAT/QNH no longer drives the " +
                "computed DA above 5000 ft, the elevation source mis-resolves, the formula " +
                "constants drifted, or the C172 per-type threshold changed. Numerical pin " +
                "directly anchored on the named pure function's output — no prose " +
                "approximation.\n$journey"
        }

        // ── Layer 2 — Sticky-witness regression (mission tree rewrite) ──────
        //
        // After the DA decline fires, the GroundDeparture compound's
        // children-from-active are replaced by `[PrimitiveTask(DECLINE_DEPARTURE,
        // NON_COMPLETING)]` via [CompoundTask.replaceFromActivePrimitive].
        // The active primitive (`mission.currentTask`) is the
        // `DECLINE_DEPARTURE` primitive; outer-level later siblings
        // (FLY_DEPARTURE, etc.) are preserved per R13's "leave outer parents
        // intact" contract but are operationally unreachable through the
        // `NON_COMPLETING` terminal.
        //
        // Direct absence + presence assertions (NOT compound predicates per
        // `bug/test-failures/compound-predicate-test-assertions-2026-05-11` —
        // independent positive + negative pins on each half of the contract).
        val finalAircraft = finalState.aircraft.getValue(aircraftId)
        val finalMission = checkNotNull(finalAircraft.pilotMission) {
            "Aircraft lost its mission during the run.\n$journey"
        }
        val finalSteps = collectSteps(finalMission.root)
        check(MissionStep.DECLINE_DEPARTURE in finalSteps) {
            "Layer 2 (R13 mission-tree rewrite): expected the final mission tree to contain " +
                "DECLINE_DEPARTURE primitive after `applyDensityAltitudeDecline` ran. Got " +
                "steps=$finalSteps. Either the recognition didn't fire (covered by Layer 1 " +
                "above) or `applyDensityAltitudeDecline` didn't write the primitive via " +
                "`replaceFromActivePrimitive`.\n$journey"
        }
        check(MissionStep.REQUEST_TAXI !in finalSteps) {
            "Layer 2 (R13 suffix-replace pin): expected the final mission tree NOT to contain " +
                "REQUEST_TAXI after the decline rewrite — `replaceFromActivePrimitive` drops " +
                "the active primitive (REQUEST_TAXI was at the start of GroundDeparture) AND " +
                "every following same-level sibling. Got steps=$finalSteps. A REQUEST_TAXI " +
                "remnant would indicate the rewrite ran on the wrong compound or the active-" +
                "primitive walk regressed.\n$journey"
        }
        val activeStep = finalMission.currentTask?.step
            ?: fail("Mission.currentTask is null after run — every leaf complete? Impossible " +
                "with a NON_COMPLETING terminal; check for a tree regression.\n$journey")
        check(activeStep == MissionStep.DECLINE_DEPARTURE) {
            "Layer 2 (terminal-state pin): expected mission.currentTask.step = " +
                "DECLINE_DEPARTURE after the apron-terminal rewrite, got $activeStep. A " +
                "currentTask other than DECLINE_DEPARTURE means either the decline regressed " +
                "to a completing primitive (the cognitive layer advanced past it) or " +
                "`replaceFromActivePrimitive`'s suffix-tail walk missed the active " +
                "position.\n$journey"
        }
        val declinePrimitive = findDeclinePrimitive(finalMission.root)
            ?: fail("Layer 2 (NON_COMPLETING invariant): expected a DECLINE_DEPARTURE " +
                "PrimitiveTask in the rewritten tree; couldn't find one — `findDeclinePrimitive` " +
                "is total over the tree shape, so this means the rewrite produced an empty " +
                "or shape-incompatible tree.\n$journey")
        check(declinePrimitive.completionMode == CompletionMode.NON_COMPLETING) {
            "Layer 2 (R20 NON_COMPLETING invariant): DECLINE_DEPARTURE primitive must pair " +
                "with NON_COMPLETING (R20 — the terminal-state contract; fn-28.2 audit at " +
                "PilotCognitive.isStepComplete / stepTransmission / skipCompletedSteps / " +
                "planRoute relies on this pairing). Got completionMode=" +
                "${declinePrimitive.completionMode}. A regression to PHYSICAL / REPORTED / " +
                "INSTRUCTION_GATED / TIMED / INSTANT would let the mission advance past " +
                "DECLINE_DEPARTURE, breaking the apron-terminal contract.\n$journey"
        }
        check(!declinePrimitive.completed) {
            "Layer 2 (NON_COMPLETING invariant): DECLINE_DEPARTURE primitive must remain " +
                "uncompleted forever (NON_COMPLETING means `isStepComplete` returns false; no " +
                "completion event ever flips its status). Got completed=true — a regression " +
                "wired the primitive to a completion event.\n$journey"
        }

        // ── Layer 3 — Kinematic non-event (positionPoint + at-rest physics) ─
        //
        // The decline keeps the aircraft on the apron — `positionPoint`
        // never transitions away from the stand, `altitudeM == 0.0`,
        // `targetSpeedMps == 0.0`, `route is PilotRoute.None`. Any
        // transition indicates the cognitive layer advanced past the
        // DA decline OR the apply path didn't zero the kinematic intent.
        val positionTransitions = trace.positionPointTransitions(aircraftId)
        check(positionTransitions.isEmpty()) {
            "Layer 3 (kinematic non-event): aircraft moved away from the stand — " +
                "`positionPoint` transitioned ${positionTransitions.size} times. The apron-" +
                "terminal contract requires zero transitions: the pilot decided NOT to taxi, " +
                "so physics must hold at-rest at LOWG_STAND_1_POINT. Transitions: " +
                positionTransitions.joinToString { t ->
                    val fromStr = t.from.fold({ "absent" }, { it.value })
                    val toStr = t.to.fold({ "absent" }, { it.value })
                    "[${t.after.time.millis}ms] $fromStr → $toStr"
                } + "\n$journey"
        }
        check(finalAircraft.altitudeM == 0.0) {
            "Layer 3 (kinematic non-event): final altitudeM = ${finalAircraft.altitudeM} m " +
                "(expected 0.0 — the aircraft is on the apron and the decline never " +
                "transitions to a climb).\n$journey"
        }
        check(finalAircraft.targetSpeedMps == 0.0) {
            "Layer 3 (kinematic non-event): final targetSpeedMps = " +
                "${finalAircraft.targetSpeedMps} m/s (expected 0.0 — " +
                "`applyDensityAltitudeDecline` sets intent.targetSpeedMps = 0; a non-zero " +
                "value indicates the decline's at-rest intent didn't propagate or a later " +
                "tick's planner overrode it).\n$journey"
        }
        check(finalAircraft.route is PilotRoute.None) {
            "Layer 3 (kinematic non-event): final route = ${finalAircraft.route} (expected " +
                "PilotRoute.None — the decline has no planned route; a Ground or Airborne " +
                "route indicates the planner ran despite the NON_COMPLETING terminal).\n$journey"
        }

        // ── R14 — ZERO Request(RequestTaxi) transmissions across the run ────
        //
        // Round-3 Critical 1: the test asserts ZERO `Request(RequestTaxi)`
        // transmissions emitted across the decision tick. Without the
        // cognitive-suppression mechanism (fn-28.2 R14), `pilotDecide`'s
        // cognitive layer would still advance REQUEST_TAXI's first-tick
        // transmission slot AND the apply path would rewrite the tree —
        // the two would race, with the radio fire visible BEFORE the next
        // decision tick's tree-walk. The ZERO count proves the suppression
        // mechanism zeroed the same-tick transmission on every PilotOutput
        // return path (round-13 Major 1 — applied BEFORE every PilotOutput
        // construction site, both PlanRouteOutcome.Plan and Skip branches).
        val requestTaxiRecords = records.filter { rec ->
            val speakerAc = (rec.speaker as? SpeakerRef.Pilot)?.aircraftId
            if (speakerAc != aircraftId) return@filter false
            val pilotTransmission = (rec.utterance as? Utterance.FromPilot)?.transmission
                ?: return@filter false
            pilotTransmission is Request && pilotTransmission.type is RequestTaxi
        }
        check(requestTaxiRecords.isEmpty()) {
            "R14 cognitive-suppression pin: expected ZERO Request(RequestTaxi) transmissions " +
                "from $aircraftId across the entire run; got ${requestTaxiRecords.size}. The " +
                "DA decline fires on the FIRST decision tick (before the cognitive layer " +
                "would emit REQUEST_TAXI's per-step transmission); the suppression mechanism " +
                "(applyCognitiveSuppression on suppressSameTickCognitive=true) zeroes the " +
                "same-tick cognitive transmission. A non-zero count indicates either (a) the " +
                "suppression wasn't applied to one of pilotDecide's return paths (round-13 " +
                "Major 1 regression), (b) the decline didn't fire on tick 1 and the cognitive " +
                "layer emitted REQUEST_TAXI in an earlier tick, or (c) the suppression " +
                "flag wasn't set on the apply path's return. Transmissions: " +
                requestTaxiRecords.joinToString { rec -> "[${rec.time.millis}ms]" } +
                "\n$journey"
        }

        // ── Mission-completion sanity (NEGATIVE pin) ────────────────────────
        //
        // The NON_COMPLETING DECLINE_DEPARTURE means the mission never
        // reaches `isComplete`. A `true` here would mean the decline
        // regressed to a completing primitive — the apron-terminal
        // contract broke. Defense-in-depth pin paired with the Layer 2
        // assertions above.
        check(!finalMission.isComplete) {
            "Negative-completion pin: NON_COMPLETING DECLINE_DEPARTURE primitive means the " +
                "mission must NEVER reach isComplete. Got isComplete=true — the decline " +
                "regressed to a completing primitive (R20 invariant violated).\n$journey"
        }
    }

    /**
     * Walk a [TaskNode] tree (depth-first, leaves only) and return every
     * [MissionStep] encountered. Used by Layer 2's mission-tree assertions
     * (DECLINE_DEPARTURE present, REQUEST_TAXI absent). Mirrors the
     * `collectSteps` helpers in `PilotDensityAltitudeDeclineTest`,
     * `PilotCrosswindGoAroundTest`, `PilotTailwindGoAroundTest`, and
     * `ReplaceFromActivePrimitiveSpec` (same shape, total over the
     * sealed `TaskNode` hierarchy).
     */
    private fun collectSteps(task: TaskNode): List<MissionStep> = when (task) {
        is PrimitiveTask -> listOf(task.step)
        is CompoundTask -> task.children.flatMap { collectSteps(it) }
    }

    /**
     * Walk a [TaskNode] tree and return the first [PrimitiveTask] whose
     * step is `DECLINE_DEPARTURE`, or null if none. Used by Layer 2's
     * NON_COMPLETING invariant pin (verify the primitive carries
     * `CompletionMode.NON_COMPLETING`). Mirrors `findDecline` in
     * `PilotDensityAltitudeDeclineTest`.
     */
    private fun findDeclinePrimitive(task: TaskNode): PrimitiveTask? = when (task) {
        is PrimitiveTask -> if (task.step == MissionStep.DECLINE_DEPARTURE) task else null
        is CompoundTask -> task.children.firstNotNullOfOrNull { findDeclinePrimitive(it) }
    }
}
