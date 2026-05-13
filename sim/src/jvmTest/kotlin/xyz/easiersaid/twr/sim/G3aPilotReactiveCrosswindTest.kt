package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.fail
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.core.world.WeatherObservation
import xyz.easiersaid.twr.core.world.updateAerodrome
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.controller.bdi.TowerArrivalStage
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.pilot.HighLevelGoal
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.pilot.createMission
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.Atis
import xyz.easiersaid.twr.protocol.BacktrackRunway
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayConfiguration
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.protocol.WindReport
import xyz.easiersaid.twr.protocol.headingDegreesMagnetic
import xyz.easiersaid.twr.sim.testing.Fixtures
import xyz.easiersaid.twr.sim.testing.commitmentStageTransitions
import xyz.easiersaid.twr.sim.testing.controllerByRole
import xyz.easiersaid.twr.sim.testing.firstPilotReportOf
import xyz.easiersaid.twr.sim.testing.firstWhere
import xyz.easiersaid.twr.sim.testing.formatJourney
import xyz.easiersaid.twr.sim.testing.load
import xyz.easiersaid.twr.sim.testing.missionStepTransitions
import xyz.easiersaid.twr.sim.testing.positionPointTransitions
import xyz.easiersaid.twr.sim.testing.responsibilityTransitions
import xyz.easiersaid.twr.sim.testing.runUntilWithStateTrace
import xyz.easiersaid.twr.sim.testing.transitionsOf
import xyz.easiersaid.twr.sim.testing.weatherTransitions

/**
 * G3a-react — single-aerodrome single-aircraft VFR **pilot-reactive**
 * go-around triggered by a world-authored wind shift whose crosswind
 * component on the active runway exceeds the aircraft type's POH-derived
 * `maxCrosswindKnots`.
 *
 * Single AI aircraft at LOWG (C172, POH max demonstrated crosswind =
 * 15 kt) flies a single planned circuit
 * (`HighLevelGoal.CircuitTraining(outcomes = [FullStop])`). Initial wind
 * is 10 kt headwind from runway heading (zero crosswind). After the
 * tower issues `ClearedToLand`, the test's per-tick world hook authors
 * `weatherByAerodrome[LOWG] = WeatherObservation(wind = Available(Wind(
 * directionDegrees = runwayHeading + 90°, speedKnots = 20)))` one-shot —
 * pure direct crosswind, 20 kt > C172's 15 kt limit. The pilot reads the
 * new wind via `PilotInput.weatherByAerodrome` on the next decision
 * cycle (fn-14.1 wiring), `derivePilotEvent`'s crosswind branch fires
 * `PilotEvent.CrosswindLimitExceeded`, `applyCrosswindGoAround` rewrites
 * the mission tree + transmits `Report(GoingAround)`, the controller's
 * existing `GA-POST-CLEAR` interrupt fires off the received GoingAround
 * report regressing the commitment from `{LandingClearanceIssued,
 * AwaitLandedObserved}` to `AwaitDownwind`, the aircraft GAs and
 * re-enters circuit. The hook then authors a second one-shot returning
 * wind to the initial 10 kt headwind once Report(GoingAround) has been
 * transmitted; the recovery circuit's final is therefore within the
 * POH limit and the aircraft lands.
 *
 * **Closes the G3a trilogy** (4th reactive-GA path; epic R12 + R13):
 * G3a-trained (fn-11 — pilot-trained mission), G3a-obstruction (fn-12
 * — ATC-mandated obstruction), G3a-continue (fn-13 — ATC CONTINUE
 * APPROACH non-GA), and now G3a-react (fn-14 — pilot-side reactive
 * recognition off world weather). The first path where pilot
 * recognition is driven by **world state directly observed via a new
 * pilot sensing channel** (vs mission-tree authorship or instruction
 * receipt).
 *
 * **Sibling tests:**
 *  - G0 ([LowgGoldenTest]) — single-aircraft single-aerodrome circuit
 *    training (full-stop only; no GA). Structural template.
 *  - G1 ([G1TwoAircraftCircuitsTest]) — single-aerodrome two-aircraft
 *    circuits=2 (T&G + full-stop). Vacate-coordination closure
 *    invariants this test's R7 pin reuses landed in G1.
 *  - G1 minimal ([G1TwoAircraftMinimalSpec]) — two-aircraft circuits=1
 *    multi-aircraft commitment-stage closure pin.
 *  - G2 ([G2CrossAerodromeVfrTest]) — single-aircraft cross-aerodrome
 *    VFR transit (LOWG → LJMB).
 *  - G3a-trained ([G3aPilotTrainedGoAroundTest]) — single-aerodrome
 *    pilot-trained GA via instructor-authored `CircuitOutcome.GoAround`.
 *    Same fixture; distinguishing surface is the mission goal, not the
 *    world weather.
 *  - G3a-obstruction ([G3aRunwayObstructionTest]) — single-aerodrome
 *    ATC-instructed reactive GA off a world-authored runway
 *    obstruction. Same fixture / same world-only-trigger discipline as
 *    G3a-react; distinguishing surface is the controller-side rule
 *    fire vs pilot-side recognition.
 *  - G3a-continue ([G3aRunwayObstructionContinueApproachTest]) — the
 *    non-GA sibling of G3a-obstruction. Pre-clearance CONTINUE
 *    APPROACH on a short-TTL runway obstruction.
 *
 * **What G3a-react distinctively pins:**
 *  - **World-only test trigger via wind:** the test authors
 *    `state.world.aerodromes[LOWG].weather` directly through
 *    `runUntilWithStateTrace`'s `onAfterEvent` hook (via
 *    `updateAerodrome` lens — fn-16). The pilot reads the new wind on
 *    the next `PilotDecisionTick` via `buildPilotInput`'s
 *    `mapNotNull { (id, a) -> a.weather?.wind?.let { id to it } }`
 *    projection (fn-14.1's `PilotWiring` wiring, fn-16 R7a source). No
 *    `PilotEvent.CrosswindLimitExceeded` injection, no direct
 *    `PilotInput.weatherByAerodrome` mutation outside the sim wiring,
 *    no `mission` mutation bypassing the recognition→apply pipeline.
 *    Per `feedback_world_only_test_triggers.md`.
 *  - **Two-transition world-weather authorship pattern:** one-shot wind
 *    shift past limit (triggers GA), then one-shot wind return within
 *    limits (enables recovery landing). Guards `var crosswindAuthored
 *    = false` and `var crosswindClearedToLimit = false` ensure each
 *    transition fires exactly once.
 *  - **End-to-end pilot-side reactive stack:** world weather author →
 *    `world.aerodromes[id].weather` mutation → `PilotWiring.buildPilotInput`
 *    projects to `WindReport` → `pilotDecide`'s `windForMission`
 *    resolves aerodrome key → `derivePilotEvent`'s crosswind branch
 *    fires `CrosswindLimitExceeded` → `applyCrosswindGoAround` Tick A
 *    (route=None, phase=Final retained, mission tree rewritten to
 *    CircuitAfterGoAround, `Report(GoingAround)` emitted) → controller
 *    `GA-POST-CLEAR` interrupt fires off `GoAroundEvent` → commitment
 *    regression → recovery clearance → landing. Pilot-side unit tests
 *    ([xyz.easiersaid.twr.pilot.observe.CrosswindLimitExceededSpec],
 *    [xyz.easiersaid.twr.pilot.PilotCrosswindHysteresisTest]) pin
 *    recognition discriminator + Tick A intent + hysteresis; this test
 *    pins the composition.
 *  - **Three-layer pin pattern** (per fn-11.2 / fn-12.3 discipline):
 *    - Layer 1 (causal partial-order) — exactly one
 *      `Report(GoingAround)` transmitted between the wind-shift cycle
 *      and the wind-recovery cycle. Decision-cycle timestamps via
 *      `findEmittingCycleMs` mint-id walk; same-cycle ordering uses
 *      `<=` on `SimTime.millis` plus mint-id sequence tiebreak per
 *      `sim-test-pins-must-compare-against-2026-05-10`. Strict `<`
 *      only across cycles.
 *    - Layer 2 (sticky-witness regression) — commitment regresses from
 *      one of `{LandingClearanceIssued, AwaitLandedObserved}` (the
 *      hook's post-clearance window) to `TowerArrivalStage
 *      .AwaitDownwind` via `GA-POST-CLEAR` (NOT `Immediate` advancement
 *      — unlike G3a-obstruction, the trigger here is a pilot-emitted
 *      `Report(GoingAround)` which the tower receives via
 *      `GoAroundEvent` and the `GA-POST-CLEAR` interrupt consumes;
 *      regression therefore fires strictly AFTER the GoingAround
 *      transmission). Post-regression sticky witnesses
 *      (`touchedDownDuringCommitment`, `pilotReadyDuringCommitment`,
 *      `observedReportsDuringCommitment`) reset per fn-8.3.
 *    - Layer 3 (kinematic non-event) — no `LandingRoll` or `Vacating`
 *      phase between the wind-shift cycle and the wind-recovery cycle;
 *      the aircraft does NOT touch down on the GA'd approach.
 *  - **World-weather transition pin:** exactly two transitions in the
 *    aerodrome-keyed `world.aerodromes[LOWG].weather` slice — wind
 *    crosses past limit (triggers GA), wind returns within limits
 *    (enables recovery). Via [weatherTransitions]; **aerodrome-keyed
 *    only — NO controller belief slice** (weather is world-state, not
 *    a controller belief projection; the GA is pilot-side and does
 *    not need controller observability expansion).
 *  - **Recovery + R7 vacate-coordination closure:** exactly one
 *    `TouchdownDetected` after wind returns within limit; recovery
 *    vacate transmission (`Report(RunwayVacated)`) present; tower's
 *    coordination ledger holds no leftover `AfterLandingVacateVia` /
 *    `BacktrackRunway` entries after vacate per fn-8.3 R7-style.
 *  - **No event-count pin on `CrosswindLimitExceeded` in this sim
 *    test** (per codex review issue #8 — that pin lives in fn-14.1's
 *    pilot-side `PilotCrosswindHysteresisTest`). Sim asserts only
 *    externally observable behavior.
 *
 * **Time band** (R12 — ±15% per fn-8.3 decision #11 inheritance): the
 * first-GREEN observed wall on the LOWG single-aircraft single-planned-
 * circuit + reactive-GA + recovery-circuit scenario is ~1333 sim seconds
 * (comparable to G3a-trained's 1393 s and G3a-obstruction's 1399 s —
 * same structural shape: one planned circuit, one GA detour, one
 * recovery circuit). The ±15% band catches doctrine timing regressions
 * while absorbing run-to-run jitter.
 *
 *   - observed completion wall: 1_333_000 ms (~22.2 sim minutes)
 *   - lower bound (×0.85): 1_133_050 ms (~18.9 min)
 *   - upper bound (×1.15): 1_532_950 ms (~25.5 min)
 *
 * **Doctrinal anchors:**
 *  - **FAA AFH (FAA-H-8083-3C) Chapter 9**: Common Error #1 —
 *    attempting a landing in crosswinds that exceed the airplane's
 *    maximum demonstrated crosswind component.
 *  - **14 CFR §23.233(a)** (pre-Amendment 64) + **FAA AC 23-8B**: POH
 *    "demonstrated crosswind" is performance information (0.2 V_SO
 *    floor), not a limitation. Modelling choice: a competent VFR pilot
 *    in the sim does GA when the demonstrated value is exceeded.
 *  - **ICAO Annex 6 Part II §2.4**: PIC final authority (GA without
 *    ATC permission per CAP 413 §4.66 (Ed 24 — formerly §4.67 in Ed 23,
 *    renumbered per fn-17.1) / ICAO Doc 4444 §12.3.4.18).
 *  - **FAA AIM §7-1-12.d.3**: ATC-voice winds in Magnetic degrees;
 *    runway designators are Magnetic; same reference frame.
 *
 * @see G3aPilotTrainedGoAroundTest the pilot-trained GA sibling
 *      (mission-authored, not world-weather-driven).
 * @see G3aRunwayObstructionTest the ATC-instructed reactive-GA sibling
 *      (controller-side rule fire off `RunwayObstructionDetected`).
 * @see G3aRunwayObstructionContinueApproachTest the non-GA pre-clearance
 *      sibling (controller-side CONTINUE APPROACH on short-TTL
 *      obstruction).
 * @see G3aPilotReactiveTailwindTest the immediate sibling on the second
 *      pilot-reactive POH/AFH recognition axis (fn-15 — closes the
 *      second pilot-reactive recognition axis as the fifth reactive-GA
 *      path). Same fixture / same two-transition
 *      `world.aerodromes[LOWG].weather` pattern / same three-layer pin
 *      shape; distinguishing surface is the recognition axis (tailwind
 *      component vs crosswind component) and the doctrinal regime
 *      (C172 AFH-advisory 10 kt tailwind vs C172 POH-demonstrated 15 kt
 *      crosswind — the per-type doctrinal severity asymmetry, C172 AFH
 *      advisory vs B738 FCOM hard limit, surfaces only on the tailwind
 *      axis since crosswind has POH-demonstrated values on both leaves).
 */
class G3aPilotReactiveCrosswindTest {

    @Test
    fun `world-authored crosswind exceedance triggers pilot reactive GA and recovery landing at LOWG`() {
        // ── World + controllers via the shared fixture ──────────────────────
        val fixture = Fixtures.LOWG
        val loaded = fixture.load().getOrElse {
            fail("LOWG fixture failed to load: $it")
        }
        val lowg = AerodromeId("LOWG")
        val rwy = RunwayId("16C")
        val ground = checkNotNull(loaded.controllerByRole(RoleName.GROUND)) {
            "GROUND missing from LOWG fixture"
        }
        val tower = checkNotNull(loaded.controllerByRole(RoleName.TOWER)) {
            "TOWER missing from LOWG fixture"
        }

        // Resolve runway heading via the fail-closed typed helper (fn-14.1).
        // 16C → 160°M. Pure-crosswind direction is therefore (160 + 90) %
        // 360 = 250°M. `% 360` prevents overflow for runway headings ≥ 270°
        // (e.g. a runway 35C at 350°M + 90 would wrap to 80°M without the
        // mod). Map result `0` back to `360` per the Wind smart constructor's
        // `0..360` domain (the constructor accepts both endpoints; we use
        // 360 to preserve the aviation-display convention "360 = North").
        val runwayHeading = checkNotNull(rwy.headingDegreesMagnetic()) {
            "Runway $rwy did not parse to a magnetic heading — fixture/test mismatch"
        }
        val pureCrosswindDirection: Int = (((runwayHeading + 90) % 360))
            .let { if (it == 0) 360 else it }

        // ── One AI aircraft, mission = single full-stop circuit ─────────────
        // `CircuitTraining(outcomes = [FullStop])` — one planned circuit;
        // the recovery circuit (after the pilot-reactive GA) is provided
        // automatically by `applyCrosswindGoAround`'s `replaceChild { it
        // .isCircuitLike() }` rewrite, mirroring G3a-obstruction's shape
        // for the `CircuitAfterGoAround` subtree. NO `CircuitOutcome
        // .GoAround` in the list — this is pilot-reactive, not pilot-
        // trained.
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
                ?: fail("LOWG fixture missing flight plan for $aircraftId"),
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

        // ── Initial weather = 10 kt headwind from runway heading ─────────────
        // Zero crosswind component initially — the recognition predicate is
        // satisfied only after the world hook authors the shift. We
        // override the fixture's default 160°@8 to a precise 16C
        // headwind (160°@10) so the initial crosswind component is
        // exactly zero and the hook's transition is the sole driver of
        // recognition.
        val initialWeather = WeatherObservation(
            wind = WindReport.Available(
                Wind.unsafe(directionDegrees = runwayHeading, speedKnots = 10),
            ),
            qnh = null,
            visibility = null,
        )

        // ── Build SimState through the smart constructor ────────────────────
        val initialState = SimState.initial(
            seed = 42L,
            world = loaded.world,
            worldIndex = loaded.worldIndex,
            aircraft = listOf(aircraft),
            controllers = listOf(ground, tower),
            weatherByAerodrome = mapOf(lowg to initialWeather),
        ).getOrElse { error("SimState.initial rejected the LOWG fixture: $it") }

        // ── ATIS + drive ────────────────────────────────────────────────────
        val until = SimTime.ZERO + SimDuration.ofMillis(30 * 60 * 1000L)
        val lowgAtis = Atis(
            letter = 'A',
            aerodrome = lowg,
            configuration = RunwayConfiguration(
                arrivals = listOf(rwy),
                departures = listOf(rwy),
            ),
            wind = Wind.unsafe(runwayHeading, 10),
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

        // ── Two-transition world-state authorship via `onAfterEvent` ─────────
        //
        // Transition 1 — wind crosses past C172's 15 kt POH limit. Fires
        // when the aircraft is on `phase = Final` AND the tower's
        // commitment for the aircraft sits in a post-clearance stage
        // (`LandingClearanceIssued` or `AwaitLandedObserved`). This window
        // pins `T_obs > T_ClearedToLand` (the hook's "post-clearance"
        // constraint) and exercises the `GA-POST-CLEAR` interrupt path
        // (matching G3a-trained / G3a-obstruction shape — same
        // commitment-lifecycle interrupt fires off the pilot-emitted
        // `Report(GoingAround)`, NOT `Immediate` advancement which is the
        // G3a-obstruction controller-side reactive path).
        //
        // Transition 2 — wind returns within limits. Fires when
        // `Report(GoingAround)` has been observed in the transmission
        // record (the pilot has emitted the GA; the recovery circuit is
        // re-entering downwind) AND the aircraft is no longer on
        // `phase = Final` (i.e. has climbed out and is on a non-final
        // leg). Resetting BEFORE the recovery circuit reaches final
        // ensures the recovery final's crosswind is within the limit;
        // the recognition stays silent on circuit 2 → aircraft lands.
        //
        // Each transition is one-shot guarded; defense-in-depth against
        // multi-fire which would either retrigger recognition (transition
        // 1) or thrash the wind (transition 2).
        var crosswindAuthored = false
        var crosswindClearedToLimit = false
        val crosswindAuthoredAt = arrayOf<SimTime?>(null)
        val crosswindClearedAt = arrayOf<SimTime?>(null)
        val goingAroundTransmittedFlag = arrayOf(false)
        val onAfterEvent: (SimEvent, SimState) -> SimState = { ev, st ->
            // Track `Report(GoingAround)` emission via the event stream so
            // the recovery-wind transition's gate is consistent with the
            // sim's actual radio surface (not an indirect mission-step
            // proxy). We watch for `SimEvent.TransmissionStart` whose
            // speaker is the aircraft's pilot and whose utterance carries
            // a `Report` containing `ReportEvent.GoingAround`. `Report` is
            // itself a `PilotTransmission` leaf (NOT a
            // `PilotTransmissionElement`); the aircraft id lives on
            // `SpeakerRef.Pilot.aircraftId`, not the transmission.
            if (!goingAroundTransmittedFlag[0] && ev is SimEvent.TransmissionStart) {
                val tx = ev.transmission
                val speakerAc = (tx.speaker as? SpeakerRef.Pilot)?.aircraftId
                val pilotTransmission =
                    (tx.utterance as? Utterance.FromPilot)?.transmission
                val report = pilotTransmission as? xyz.easiersaid.twr.protocol.Report
                if (speakerAc == aircraftId &&
                    report != null &&
                    report.events.any { it is ReportEvent.GoingAround }
                ) {
                    goingAroundTransmittedFlag[0] = true
                }
            }

            when {
                // Transition 2 — wind returns within limit (one-shot).
                // Gated on `Report(GoingAround)` already transmitted AND
                // aircraft off final (back on a non-final leg / climbout).
                // Both gates required: the report alone would fire
                // immediately at the GA transmission instant, before the
                // aircraft has actually climbed and re-entered downwind;
                // the off-final gate gives the GA path room to execute.
                !crosswindClearedToLimit &&
                    goingAroundTransmittedFlag[0] &&
                    aircraftIsOffFinal(st, aircraftId) -> {
                    crosswindClearedToLimit = true
                    crosswindClearedAt[0] = st.now
                    authorWeather(st, lowg, initialWeather)
                }
                // Transition 1 — wind crosses past limit (one-shot).
                // Gated on post-clearance window per the spec.
                !crosswindAuthored &&
                    aircraftIsOnFinalWithLandingClearance(st, aircraftId, tower.id) -> {
                    crosswindAuthored = true
                    crosswindAuthoredAt[0] = st.now
                    val crosswindWeather = WeatherObservation(
                        wind = WindReport.Available(
                            Wind.unsafe(
                                directionDegrees = pureCrosswindDirection,
                                speedKnots = 20,
                            ),
                        ),
                        qnh = null,
                        visibility = null,
                    )
                    authorWeather(st, lowg, crosswindWeather)
                }
                else -> st
            }
        }

        val (finalState, records, trace) = runUntilWithStateTrace(
            initialState = initialState,
            initialEvents = initialEvents,
            untilTime = until,
            onAfterEvent = onAfterEvent,
        )

        // ── Diagnostic preamble ─────────────────────────────────────────────
        val journey = finalState.formatJourney(aircraftId, records)
        println(journey)

        println()
        println("─── G3a-react per-aircraft trace summary ───")
        println("Runway heading (16C):       ${runwayHeading}°M")
        println("Pure-crosswind direction:   ${pureCrosswindDirection}°M @ 20 kt")
        println("Crosswind authored at:      ${crosswindAuthoredAt[0]?.millis ?: "<NEVER>"}ms")
        println("Crosswind cleared at:       ${crosswindClearedAt[0]?.millis ?: "<NEVER>"}ms")
        println("Responsibility transitions:")
        for (t in trace.responsibilityTransitions(aircraftId)) {
            val fromStr = t.from.fold({ "absent" }, { it::class.simpleName ?: "?" })
            val toStr = t.to.fold({ "absent" }, { it::class.simpleName ?: "?" })
            println("  [${t.after.time.millis}ms] ${t.controller}: $fromStr → $toStr")
        }
        println("Mission step transitions:")
        for (t in trace.missionStepTransitions(aircraftId)) {
            val fromStr = t.from.fold({ "absent" }, { it.name })
            val toStr = t.to.fold({ "absent" }, { it.name })
            println("  [${t.after.time.millis}ms] $fromStr → $toStr")
        }
        println("Commitment stage transitions (tower):")
        for (t in trace.commitmentStageTransitions(aircraftId, tower.id)) {
            val fromStr = t.from.fold({ "absent" }, { it.name })
            val toStr = t.to.fold({ "absent" }, { it.name })
            println("  [${t.after.time.millis}ms] $fromStr → $toStr")
        }
        println("Weather (LOWG) transitions:")
        for (t in trace.weatherTransitions(lowg)) {
            val fmt: (arrow.core.Option<WeatherObservation>) -> String = { opt ->
                opt.fold({ "absent" }) { obs ->
                    when (val w = obs.wind) {
                        is WindReport.Available ->
                            "wind=${w.wind.directionDegrees}°@${w.wind.speedKnots}kt"
                        WindReport.NotReported -> "NotReported"
                    }
                }
            }
            println("  [${t.after.time.millis}ms] ${fmt(t.from)} → ${fmt(t.to)}")
        }
        println("positionPoint transitions:")
        for (t in trace.positionPointTransitions(aircraftId)) {
            val fromStr = t.from.fold({ "absent" }, { it.value })
            val toStr = t.to.fold({ "absent" }, { it.value })
            println("  [${t.after.time.millis}ms] $fromStr → $toStr")
        }
        println("Phase transitions:")
        for (t in trace.transitionsOf { st -> st.aircraft[aircraftId]?.phase }) {
            println("  [${t.after.time.millis}ms] ${t.from} → ${t.to}")
        }
        println("─── end G3a-react per-aircraft trace summary ───")
        println()

        // ── One-shot authorship pins (defensive) ────────────────────────────
        check(crosswindAuthored) {
            "World-authorship hook never fired transition 1 — " +
                "`aircraftIsOnFinalWithLandingClearance` never returned true. Either the " +
                "aircraft never reached phase=Final, or ClearedToLand was never issued for " +
                "circuit 1. This is a pre-condition regression — the rest of the test's pins " +
                "assume the crosswind was authored.\n$journey"
        }
        check(crosswindClearedToLimit) {
            "World-authorship hook never fired transition 2 — " +
                "`Report(GoingAround)` was never transmitted OR aircraft never left final after " +
                "the GA. Either the recognition didn't fire, the applier didn't transmit, or " +
                "the GA path didn't climb out. Without transition 2, the recovery circuit's " +
                "final would still face the crosswind and the recognition would re-fire — " +
                "the test would never complete.\n$journey"
        }

        // ── Outcome pins: aircraft completes mission, parked at stand ──────
        val finalAircraft = finalState.aircraft.getValue(aircraftId)
        val finalMission = checkNotNull(finalAircraft.pilotMission) {
            "Aircraft lost its mission.\n$journey"
        }
        check(finalMission.isComplete) {
            "Mission did not complete within 30 sim minutes — wedge or under-budget.\n$journey"
        }
        check(finalAircraft.altitudeM == 0.0) {
            "Aircraft is not on the ground at end of run.\n$journey"
        }
        check(finalAircraft.phase == PilotPhase.Parked || finalAircraft.phase == PilotPhase.AtStand) {
            "Aircraft did not return to a stand. Final phase: ${finalAircraft.phase}.\n$journey"
        }
        val standPoints = loaded.world.aerodromes
            .getValue(lowg)
            .stands.values.map { it.point }.toSet()
        check(finalAircraft.positionPoint in standPoints) {
            "Aircraft did not end at a LOWG stand point. positionPoint=${finalAircraft.positionPoint}; " +
                "valid stand points: $standPoints.\n$journey"
        }

        // ── World-weather transition pin (exactly two transitions) ──────────
        //
        // The aerodrome-keyed `weatherByAerodrome[LOWG]` slice transitions
        // exactly twice during the run: (1) initial 160°@10 → 250°@20
        // (crosswind authored), (2) 250°@20 → 160°@10 (cleared). No
        // controller-belief slice expansion — weather is world-state per
        // [weatherTransitions]'s KDoc.
        val weatherTrans = trace.weatherTransitions(lowg)
        check(weatherTrans.size == 2) {
            "Expected exactly two transitions in world.aerodromes[$lowg].weather " +
                "(crosswind authored + cleared), observed ${weatherTrans.size}. " +
                "More than two would indicate the one-shot guards regressed; fewer than two " +
                "indicates either the authorship hook didn't fire (covered by the defensive " +
                "pins above) or the trace doesn't see the world-state mutation (sim-engine " +
                "invariant violation).\n$journey"
        }
        val weatherShiftMs = weatherTrans[0].after.time.millis
        val weatherClearMs = weatherTrans[1].after.time.millis
        check(weatherShiftMs < weatherClearMs) {
            "Weather-transition ordering pin: shift ($weatherShiftMs ms) must precede clear " +
                "($weatherClearMs ms). Equal/reversed indicates the one-shot guards fired in " +
                "the wrong order.\n$journey"
        }
        // Defense-in-depth: confirm the shift is the high-crosswind state
        // and the clear is the headwind state — pins the wind values
        // against the authorship parameters above.
        val shiftedWind = (weatherTrans[0].to.getOrElse {
            fail("Weather-shift transition has absent `to` — invariant violation.\n$journey")
        }.wind as? WindReport.Available)?.wind
            ?: fail("Weather-shift transition `to.wind` is not WindReport.Available.\n$journey")
        check(shiftedWind.directionDegrees == pureCrosswindDirection &&
            shiftedWind.speedKnots == 20) {
            "Weather-shift wind mismatch: got ${shiftedWind.directionDegrees}°@${shiftedWind.speedKnots} " +
                "expected ${pureCrosswindDirection}°@20.\n$journey"
        }
        val clearedWind = (weatherTrans[1].to.getOrElse {
            fail("Weather-clear transition has absent `to` — invariant violation.\n$journey")
        }.wind as? WindReport.Available)?.wind
            ?: fail("Weather-clear transition `to.wind` is not WindReport.Available.\n$journey")
        check(clearedWind.directionDegrees == runwayHeading && clearedWind.speedKnots == 10) {
            "Weather-clear wind mismatch: got ${clearedWind.directionDegrees}°@${clearedWind.speedKnots} " +
                "expected ${runwayHeading}°@10.\n$journey"
        }

        // ── Layer 1 — Causal partial-order pins (decision-cycle timestamps) ─
        //
        // Locate Report(GoingAround) records first. There must be EXACTLY
        // ONE Report(GoingAround) between the wind-shift cycle and the
        // wind-recovery cycle — this is the "causal partial-order"
        // observable. More than one would indicate the hysteresis regressed
        // (recognition re-fired on a subsequent tick); zero would indicate
        // the recognition didn't fire at all.
        val goingAroundRecords = records.filter { rec ->
            val speakerAc = (rec.speaker as? SpeakerRef.Pilot)?.aircraftId
            if (speakerAc != aircraftId) return@filter false
            val pilotTransmission = (rec.utterance as? Utterance.FromPilot)?.transmission
            val report = pilotTransmission as? xyz.easiersaid.twr.protocol.Report
                ?: return@filter false
            report.events.any { it is ReportEvent.GoingAround }
        }
        check(goingAroundRecords.size == 1) {
            "Expected exactly one Report(GoingAround) for $aircraftId between the wind-shift " +
                "(${weatherShiftMs}ms) and the wind-recovery (${weatherClearMs}ms) cycles, " +
                "observed ${goingAroundRecords.size}. More than one indicates hysteresis " +
                "regression (recognition re-fired); zero indicates the recognition didn't fire.\n" +
                "$journey"
        }
        val goingAroundRecord = goingAroundRecords.single()
        val goingAroundMs = goingAroundRecord.time.millis
        check(goingAroundMs in weatherShiftMs..weatherClearMs) {
            "Report(GoingAround) (${goingAroundMs}ms) must occur between the wind-shift " +
                "(${weatherShiftMs}ms) and the wind-recovery (${weatherClearMs}ms) cycles.\n" +
                "$journey"
        }

        // ── Layer 2 — Sticky-witness regression pin (post-clearance → GA) ──
        //
        // Same shape as G3a-trained's regression pin: the pilot transmits
        // `Report(GoingAround)`, the tower receives it via `GoAroundEvent`,
        // and the `GA-POST-CLEAR` interrupt regresses the commitment from
        // `{LandingClearanceIssued, AwaitLandedObserved}` to
        // `AwaitDownwind`. UNLIKE G3a-obstruction (which uses `Immediate`
        // advancement and equality with the GoAround decision cycle), this
        // path goes through the radio-delivery → interrupt machinery, so
        // the regression strictly POSTDATES the GoingAround transmission.
        val stageTransitions = trace.commitmentStageTransitions(aircraftId, tower.id)
        val postClearStages = setOf<TowerArrivalStage>(
            TowerArrivalStage.LandingClearanceIssued,
            TowerArrivalStage.AwaitLandedObserved,
        )
        val regressions = stageTransitions.filter { t ->
            val from = t.from.fold({ null }, { it as? TowerArrivalStage }) ?: return@filter false
            val to = t.to.fold({ null }, { it as? TowerArrivalStage }) ?: return@filter false
            from in postClearStages && to == TowerArrivalStage.AwaitDownwind
        }
        check(regressions.size == 1) {
            "Expected exactly one stage regression {LandingClearanceIssued | AwaitLandedObserved} → " +
                "AwaitDownwind on the pilot-reactive GA, observed ${regressions.size}. " +
                "More than one would indicate the `GA-POST-CLEAR` interrupt fired multiple " +
                "times (unexpected for a single reactive GA); zero would indicate the " +
                "GoAroundEvent was not delivered to the controller (radio-delivery failure) " +
                "or the interrupt's `fromStages` didn't match.\n$journey"
        }
        val regression = regressions.single()
        check(regression.after.time.millis > goingAroundMs) {
            "Radio-delivery prerequisite: stage regression at ${regression.after.time.millis}ms " +
                "must fire strictly AFTER Report(GoingAround) at ${goingAroundMs}ms. " +
                "`GA-POST-CLEAR` gates on `GoAroundEvent` delivered from the radio; a " +
                "regression AT-OR-BEFORE the GoingAround transmission would indicate the " +
                "regression fired off some other channel (e.g. an unrelated rule, or " +
                "`Immediate` advancement which is the G3a-obstruction path, not the " +
                "pilot-reactive path).\n$journey"
        }

        // Post-regression sticky witnesses are reset (fn-8.3 R7-style).
        val commitmentAfter = regression.after.state.beliefs[tower.id]
            ?.commitments?.get(aircraftId)
            ?: fail(
                "Tower commitment for $aircraftId missing AT regression cursor — the " +
                    "regression should preserve the commitment (stage drops, commitment lives), " +
                    "not delete it.\n$journey"
            )
        check(!commitmentAfter.touchedDownDuringCommitment) {
            "touchedDownDuringCommitment must be reset post-regression; got " +
                "${commitmentAfter.touchedDownDuringCommitment}.\n$journey"
        }
        check(!commitmentAfter.pilotReadyDuringCommitment) {
            "pilotReadyDuringCommitment must be reset post-regression; got " +
                "${commitmentAfter.pilotReadyDuringCommitment}.\n$journey"
        }
        check(commitmentAfter.observedReportsDuringCommitment.isEmpty()) {
            "observedReportsDuringCommitment must be reset post-regression; got " +
                "${commitmentAfter.observedReportsDuringCommitment.size} entries: " +
                "${commitmentAfter.observedReportsDuringCommitment}.\n$journey"
        }

        // ── Layer 3 — Kinematic non-event pin ───────────────────────────────
        //
        // The pilot-reactive GA prevents touchdown on circuit 1: no
        // `LandingRoll` or `Vacating` phase entered between the wind-shift
        // cycle and the wind-recovery cycle. Touchdown in that window
        // would indicate either (i) the recognition fired too late, (ii)
        // the applier's Tick A intent didn't propagate, or (iii) the
        // controller's interrupt didn't fire and the aircraft landed on
        // the obstructed-by-crosswind runway.
        val phaseTransitions = trace.transitionsOf { st ->
            st.aircraft[aircraftId]?.phase
        }
        val touchdownInWindow = phaseTransitions.any { t ->
            val landed = t.to == PilotPhase.LandingRoll || t.to == PilotPhase.Vacating
            val inWindow = t.after.time.millis in weatherShiftMs..weatherClearMs
            landed && inWindow
        }
        check(!touchdownInWindow) {
            "Aircraft entered LandingRoll or Vacating between the wind-shift " +
                "(${weatherShiftMs}ms) and the wind-recovery (${weatherClearMs}ms) cycles — " +
                "circuit 1 should NOT have touched down. The pilot-reactive GA must fire in " +
                "time to prevent landing in the exceedance window.\nPhase transitions: " +
                phaseTransitions.joinToString { "${it.to}@${it.after.time.millis}ms" } +
                "\n$journey"
        }

        // ── Recovery pin: recovery landing + vacate after wind cleared ──────
        //
        // The recovery clearance fires AFTER the wind has returned within
        // limits, the aircraft lands on the recovery circuit, vacates.
        // We pin the recovery `ClearedToLand` strictly after the wind-
        // recovery cycle (the controller's existing `ARR-LAND` re-clears
        // on the recovery circuit's downwind/base call; the wind is now
        // safe).
        val landRecords = records.filter { rec ->
            val out = (rec.utterance as? Utterance.FromController)?.output
                as? ControllerOutput.Instruct ?: return@filter false
            val instr = (out.dispatch as? Dispatch.Direct)?.instruction ?: return@filter false
            out.target == aircraftId && instr is ClearedToLand
        }
        val landRecoveryRecord = landRecords.firstOrNull { it.time.millis > goingAroundMs }
            ?: fail(
                "Expected at least one ClearedToLand for $aircraftId AFTER Report(GoingAround) — " +
                    "the recovery circuit re-enters the pattern and is re-cleared to land.\n" +
                    "$journey"
            )
        val landRecoveryMs = landRecoveryRecord.time.millis

        val vacatedMs = records.firstPilotReportOf<ReportEvent.RunwayVacated>(aircraftId)
            .map { it.time.millis }
            .getOrElse {
                fail(
                    "Expected at least one Report(RunwayVacated) from $aircraftId — without " +
                        "it, the aircraft never confirmed leaving the runway after the recovery " +
                        "landing.\n$journey"
                )
            }
        check(goingAroundMs < landRecoveryMs && landRecoveryMs < vacatedMs) {
            "Recovery chain pin: expected " +
                "Report(GoingAround) (${goingAroundMs}ms) < " +
                "ClearedToLand(recovery) (${landRecoveryMs}ms) < " +
                "Report(RunwayVacated) (${vacatedMs}ms).\n$journey"
        }

        // ── R7 — Vacate-coordination closure pin ────────────────────────────
        //
        // After the recovery circuit's full-stop landing, the tower's
        // coordination ledger contains NO pending `AfterLandingVacateVia`
        // / `BacktrackRunway` entries for the aircraft — vacate readback
        // closed the coordination per fn-8.3's discipline.
        val towerBeliefs = checkNotNull(finalState.beliefs[tower.id]) {
            "Tower beliefs missing at end of run — controller pipeline regression.\n$journey"
        }
        val acCoordinations = towerBeliefs.coordinations[aircraftId].orEmpty()
        val vacateCoords = acCoordinations.filter { coord ->
            coord.instruction is AfterLandingVacateVia ||
                coord.instruction is BacktrackRunway
        }
        check(vacateCoords.isEmpty()) {
            "R7 vacate-coordination closure: after the recovery vacate readback, the tower's " +
                "coordination ledger must contain no `AfterLandingVacateVia` / `BacktrackRunway` " +
                "entries for $aircraftId. Got ${vacateCoords.size} unclosed entries: " +
                "$vacateCoords.\n$journey"
        }

        // ── Time band (R12 — ±15% around observed wall) ──────────────────────
        //
        // First-green observed wall (fn-14.2): 1_333_000 ms (~22.2 min) on
        // the LOWG single-aircraft single-planned-circuit + pilot-reactive
        // GA + recovery circuit scenario. ±15% band catches doctrine
        // regressions while absorbing run-to-run jitter (mirrors
        // G3a-trained's 1393 s and G3a-obstruction's 1399 s pins —
        // same structural shape). Captured in fn-14.2 evidence;
        // rebaseline if doctrine shifts (per fn-8.3 decision #11
        // inheritance).
        val completionCursor = trace.firstWhere { st ->
            st.aircraft[aircraftId]?.pilotMission?.isComplete == true
        }.getOrElse {
            fail("Mission never reached isComplete during the trace.\n$journey")
        }
        val completionMs = completionCursor.time.millis
        val observedCompletionMs = 1_333_000L
        val band = (observedCompletionMs * 0.15).toLong()
        val minMs = observedCompletionMs - band
        val maxMs = observedCompletionMs + band
        check(completionMs in minMs..maxMs) {
            "Mission completion (${completionMs / 1000} s = ${completionMs}ms) outside the ±15% " +
                "band [${minMs / 1000} s, ${maxMs / 1000} s] centred on the observed wall " +
                "(${observedCompletionMs / 1000} s). Drift indicates a doctrine regression " +
                "affecting the pilot-reactive GA cadence — recognition latency, applier Tick A " +
                "shape, recovery-circuit re-entry geometry, or radio serialization. " +
                "actual=${completionMs}ms expected=${observedCompletionMs}ms band=±${band}ms.\n" +
                "$journey"
        }
    }

    /**
     * Predicate for transition-1 authorship: the aircraft is on
     * `phase=Final` AND the tower's commitment for the aircraft sits in
     * a **post-clearance** stage (`LandingClearanceIssued` or
     * `AwaitLandedObserved`). Same shape as G3a-obstruction's hook
     * predicate (post-clearance window per the task spec's
     * `T_obs > T_ClearedToLand` rule).
     */
    private fun aircraftIsOnFinalWithLandingClearance(
        st: SimState,
        aircraft: AircraftId,
        towerId: xyz.easiersaid.twr.protocol.ControllerId,
    ): Boolean {
        val ac = st.aircraft[aircraft] ?: return false
        if (ac.phase != PilotPhase.Final) return false
        val commitment = st.beliefs[towerId]?.commitments?.get(aircraft) ?: return false
        val stage = commitment.stage
        return stage == TowerArrivalStage.LandingClearanceIssued ||
            stage == TowerArrivalStage.AwaitLandedObserved
    }

    /**
     * Predicate for transition-2 authorship: the aircraft is NOT on
     * final (i.e. has climbed out / re-entered the circuit). Used
     * together with the `Report(GoingAround)` already-transmitted gate
     * to fire the wind-recovery transition only after the GA path has
     * actually started executing.
     */
    private fun aircraftIsOffFinal(st: SimState, aircraft: AircraftId): Boolean {
        val ac = st.aircraft[aircraft] ?: return false
        return ac.phase != PilotPhase.Final
    }

    /**
     * Pure world-state mutation: replace
     * `state.world.aerodromes[aerodromeId].weather` with [weather]. Per
     * fn-14.2 R12 the world-only test trigger discipline writes
     * directly to the world-state entity (NOT to controller beliefs
     * and NOT to `PilotInput`); the sim's per-cycle `buildPilotInput`
     * projection picks up the new wind on the next pilot decision
     * tick.
     *
     * fn-16 (R8): migrated from the deleted
     * `state.weatherByAerodrome` flat map to
     * [xyz.easiersaid.twr.core.world.Aerodrome.weather] via the new
     * [xyz.easiersaid.twr.core.world.updateAerodrome] lens helper.
     */
    private fun authorWeather(
        st: SimState,
        aerodromeId: AerodromeId,
        weather: WeatherObservation,
    ): SimState =
        st.copy(world = st.world.updateAerodrome(aerodromeId) { it.copy(weather = weather) })
}
