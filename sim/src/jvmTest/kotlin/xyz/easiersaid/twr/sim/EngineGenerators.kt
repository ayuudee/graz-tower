package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.pilot.HighLevelGoal
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.pilot.createMission
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Atis
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayConfiguration
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.sim.HeapEventQueue
import xyz.easiersaid.twr.sim.testing.Fixtures
import xyz.easiersaid.twr.sim.testing.LoadedFixture
import xyz.easiersaid.twr.sim.testing.controllerByRole
import xyz.easiersaid.twr.sim.testing.load

/**
 * fn-26 (R2): generators for `step()` property tests. Seeded from
 * `Fixtures.LOWG` so worlds are pre-validated. Generators vary only the
 * RNG seed and event timing/class, keeping `SimState.initial` smart-
 * constructor invariants and the engine's `require(event.time >= state.now)`
 * boundary satisfied by construction.
 *
 * Two stages serve different property classes (per epic R6 vacuity finding):
 *  - [arbInitialState] — fresh `SimState.initial` per iteration, varying
 *    seed. Used by R3/R4/R5. Empty controller ledger → no runway
 *    instructions emitted; R6 against this would be vacuous.
 *  - [arbPostFilingState] — cached snapshot driven through fixture
 *    `FlightPlanFiled` events + ticks **up to right before** the first
 *    runway-instruction emission. A `ControllerCycle` re-applied here
 *    re-fires the same rule → R6 reaches the kernel-call branch.
 *
 * Load-once: the fixture loader (JSON parse + WorldIndex build +
 * validation) and the warmup drive both run once per JVM via `by lazy`.
 */
internal object EngineGenerators {

    private const val LOWG_ID = "LOWG"
    private val LOWG_AERODROME = AerodromeId(LOWG_ID)
    private val LOWG_AIRCRAFT_ID = AircraftId("OE-ABC")
    private val LOWG_RUNWAY_16C = RunwayId("16C")

    /**
     * Post-filing warmup cap. The warmup drives until the controller
     * emits its first runway instruction; cap is the upper bound after
     * which we fail-loud (planning defect). 25 sim-min comfortably
     * covers a full G0 circuit (~10-20 min per `LowgGoldenTest` band);
     * LineUpAndWait emits well inside that window.
     */
    private val POST_FILING_WARMUP_CAP: SimDuration = SimDuration.ofMillis(25 * 60 * 1000L)

    /**
     * Maximum event-time delta from `state.now`. Bounded so generators
     * produce events that fire within the modelled cadences (1 Hz
     * physics; ~1 s pilot tick; ~250 ms controller cycle floor) rather
     * than scheduling far-future events that no engine path would emit
     * naturally.
     */
    private const val MAX_EVENT_DELTA_MS = 30_000L

    /** Loaded LOWG fixture — fetched once per JVM. */
    private val loadedLowg: LoadedFixture by lazy {
        Fixtures.LOWG.load().getOrElse { error("LOWG fixture failed to load: $it") }
    }

    /**
     * Cached post-filing SimState (seed=0L, deterministic). Driven until
     * **just before** the first runway-instruction emission so a fresh
     * `ControllerCycle` re-fires the rule. Iteration variance comes from
     * the event arb composed on top — not the state.
     */
    private val postFilingLowg: SimState by lazy {
        val initial = buildInitialLowgState(seed = 0L)
        val now = SimTime.ZERO
        val atis = lowgAtis(now)
        val tower = checkNotNull(loadedLowg.controllerByRole(RoleName.TOWER)) {
            "LOWG fixture should stage a TOWER controller for the post-filing warm-up"
        }
        val ground = checkNotNull(loadedLowg.controllerByRole(RoleName.GROUND)) {
            "LOWG fixture should stage a GROUND controller for the post-filing warm-up"
        }
        val initialEvents = loadedLowg.initialEvents + listOf(
            SimEvent.AtisIssued(time = now, aerodrome = LOWG_AERODROME, atis = atis),
            SimEvent.PilotDecisionTick(time = now, aircraftId = LOWG_AIRCRAFT_ID),
            SimEvent.PhysicsTick(time = now),
            SimEvent.ControllerCycle(time = now, controllerId = ground.id),
            SimEvent.ControllerCycle(time = now, controllerId = tower.id),
        )
        runUntilPreRunwayInstruction(initial, initialEvents, now + POST_FILING_WARMUP_CAP)
            ?: error(
                "post-filing warm-up failed: 25 sim-min of LOWG-circuit drive produced " +
                    "no runway-instruction emission. R6 can't be exercised against the " +
                    "current fixture/world setup — surface as a planning defect.",
            )
    }

    /**
     * Drive from [initial] / [events] and return the pre-state of the
     * step that emits the first runway-class controller instruction.
     * A `ControllerCycle` re-applied against the returned state re-fires
     * the rule (the post-state would have the commitment advanced).
     * Returns null if [untilTime] elapses without an emission.
     */
    private fun runUntilPreRunwayInstruction(
        initial: SimState,
        events: List<SimEvent>,
        untilTime: SimTime,
    ): SimState? {
        var state = initial
        val (stamped, stampedEvents) = state.emit(events)
        state = stamped
        val queue = HeapEventQueue()
        stampedEvents.forEach(queue::enqueue)
        var next = queue.dequeueMin()
        while (next != null && next.time <= untilTime) {
            val (newState, emitted) = step(state, next)
            if (emitted.any { ev -> ev.carriesRunwayInstruction() }) {
                // Return the PRE-state with `now` bumped to the event
                // time (matches `step()`'s internal time bump) — so
                // event generators producing `state.now + delta` start
                // at a realistic cursor.
                return state.copy(now = next.time)
            }
            state = newState
            emitted.forEach(queue::enqueue)
            next = queue.dequeueMin()
        }
        return null
    }

    private fun SimEvent.carriesRunwayInstruction(): Boolean {
        val tx = (this as? SimEvent.TransmissionStart)?.transmission ?: return false
        if (tx.speaker !is SpeakerRef.Controller) return false
        val utterance = tx.utterance as? Utterance.FromController ?: return false
        val output = utterance.output as? ControllerOutput.Instruct ?: return false
        val instr: AtcInstruction = when (val d = output.dispatch) {
            is Dispatch.Direct -> d.instruction
            is Dispatch.Conditional -> d.instruction
        }
        return instr is LineUpAndWait || instr is ClearedForTakeoff || instr is ClearedToLand
    }

    /**
     * Build a fresh `SimState.initial` from the LOWG fixture with the given
     * RNG seed. Smart-constructor validations all hold by construction
     * (the fixture is pre-validated).
     */
    private fun buildInitialLowgState(seed: Long): SimState {
        val aircraftId = LOWG_AIRCRAFT_ID
        val now = SimTime.ZERO
        val mission = createMission(
            goal = HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop)),
            startPhase = PilotPhase.AtStand,
            time = now,
        )
        val aircraft = AircraftState(
            id = aircraftId,
            callsign = Callsign("OEABC"),
            position = loadedLowg.world.geometry.points.getValue(Fixtures.LOWG.standPointId),
            positionPoint = Fixtures.LOWG.standPointId,
            phase = PilotPhase.AtStand,
            pilotMission = mission,
        )
        val ground = checkNotNull(loadedLowg.controllerByRole(RoleName.GROUND)) {
            "LOWG fixture should stage a GROUND controller"
        }
        val tower = checkNotNull(loadedLowg.controllerByRole(RoleName.TOWER)) {
            "LOWG fixture should stage a TOWER controller"
        }
        return SimState.initial(
            seed = seed,
            world = loadedLowg.world,
            worldIndex = loadedLowg.worldIndex,
            aircraft = listOf(aircraft),
            controllers = listOf(ground, tower),
            weatherByAerodrome = mapOf(LOWG_AERODROME to Fixtures.LOWG.weather),
        ).getOrElse { error("SimState.initial rejected the LOWG fixture: $it") }
    }

    private fun lowgAtis(at: SimTime): Atis = Atis(
        letter = 'A',
        aerodrome = LOWG_AERODROME,
        configuration = RunwayConfiguration(
            arrivals = listOf(LOWG_RUNWAY_16C),
            departures = listOf(LOWG_RUNWAY_16C),
        ),
        wind = Wind.unsafe(160, 8),
        qnh = null,
        visibility = null,
        generatedAt = at,
    )

    /** R3/R4/R5 generator: fresh `SimState.initial` with the seed varied. */
    fun arbInitialState(): Arb<SimState> = arbitrary { rs ->
        buildInitialLowgState(seed = rs.random.nextLong())
    }

    /** R6 generator: cached post-filing snapshot (controller ledgers populated). */
    fun arbPostFilingState(): Arb<SimState> = arbitrary { postFilingLowg }

    /**
     * Generate a `SimEvent` compatible with [state]. Event time satisfies
     * `time >= state.now` by construction (R3 generator-bug discipline).
     * Restricted to self-scheduled primitive events (PhysicsTick /
     * ControllerCycle / PilotDecisionTick) — these are real inputs the
     * engine sees every cycle. Payload events (TransmissionStart,
     * FlightPlanFiled, AtisIssued, etc) need rich state-bound payloads
     * the property generator doesn't synthesise; the post-filing warmup
     * exercises those paths via real flows.
     */
    fun arbEventFor(state: SimState): Arb<SimEvent> {
        val deltaArb: Arb<SimDuration> = Arb.long(0L..MAX_EVENT_DELTA_MS).map { SimDuration.ofMillis(it) }
        val aircraftIds = state.aircraft.keys.toList()
        val controllerIds = state.controllers.keys.toList()
        return deltaArb.flatMap { delta ->
            val at = state.now + delta
            val choices: List<Arb<SimEvent>> = buildList {
                add(Arb.of(SimEvent.PhysicsTick(time = at)))
                if (controllerIds.isNotEmpty()) {
                    add(
                        Arb.of(controllerIds).map { id ->
                            SimEvent.ControllerCycle(time = at, controllerId = id)
                        },
                    )
                }
                if (aircraftIds.isNotEmpty()) {
                    add(
                        Arb.of(aircraftIds).map { id ->
                            SimEvent.PilotDecisionTick(time = at, aircraftId = id)
                        },
                    )
                }
            }
            check(choices.isNotEmpty()) { "arbEventFor: state must have ≥1 aircraft or controller" }
            Arb.of(choices).flatMap { it }
        }
    }

    /** R3/R4/R5 pair generator (fresh initial state + event). */
    fun arbInitialStatePlusEvent(): Arb<Pair<SimState, SimEvent>> =
        arbInitialState().flatMap { state -> arbEventFor(state).map { state to it } }

    /** R6 pair generator (cached post-filing state + event). */
    fun arbPostFilingStatePlusEvent(): Arb<Pair<SimState, SimEvent>> =
        arbPostFilingState().flatMap { state -> arbEventFor(state).map { state to it } }
}
