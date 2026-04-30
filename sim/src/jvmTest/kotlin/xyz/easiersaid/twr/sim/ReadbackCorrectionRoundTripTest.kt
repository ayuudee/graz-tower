package xyz.easiersaid.twr.sim

import arrow.core.NonEmptyList
import arrow.core.getOrElse
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.WeatherObservation
import xyz.easiersaid.twr.controller.WindReport
import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AerodromeAip
import xyz.easiersaid.twr.core.world.AerodromeRole
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.AuthorityGrant
import xyz.easiersaid.twr.core.world.Degrees
import xyz.easiersaid.twr.core.world.Feet
import xyz.easiersaid.twr.core.world.PhysicalGeometry
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.buildWorldIndex
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.HighLevelGoal
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.pilot.createMission
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtomDefect
import xyz.easiersaid.twr.protocol.AuthorityEntityType
import xyz.easiersaid.twr.protocol.AuthorityOperation
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedToLandReadback
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.controller.DecisionTrace
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.ReadbackCorrection
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.SimpleElement
import xyz.easiersaid.twr.protocol.Wind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Pass 3 — round-trip integration test (R6).
 *
 * Verifies the controller→sim→pilot→controller flow for `ReadbackCorrection`:
 *
 *  1. Controller emits `ControllerOutput.Respond(ReadbackCorrection(correct=ClearedToLand))`.
 *  2. Sim wiring delivers the correction utterance, schedules `PilotProcessingComplete`.
 *  3. Pilot's `processControllerResponse` produces a corrected `Readback` transmission.
 *  4. Sim schedules a `TransmissionStart` for the corrected readback.
 *  5. The transmission's content matches `requiredReadbackAtoms(correct)`.
 *
 * G0 (`LowgGoldenTest`) does not exercise this path because pilots in G0
 * always read back correctly. This test isolates the correction flow.
 */
class ReadbackCorrectionRoundTripTest {

    @Test
    fun `pilot retransmits a corrected readback after ReadbackCorrection from controller`() {
        // ── Minimal world: one aerodrome with a stand point ─────────────
        val lowg = AerodromeId("LOWG")
        val standId = PointId("STAND_1")
        val frequency = Frequency.unsafe("118.200")
        val rwy = RunwayId("16C")
        val controllerId = ControllerId("LOWG_TWR")
        val aircraftId = AircraftId("OE-ABC")

        val authorities = setOf(
            AuthorityGrant(
                entityType = AuthorityEntityType.RADIO_ROLE,
                operations = setOf(AuthorityOperation.CONTACT),
            ),
        )
        val ad = Aerodrome(
            icao = lowg,
            elevation = Feet(1115),
            magneticVariation = Degrees(0.0),
            transitionAltitude = Level.AltitudeFeet.unsafe(10000),
            aip = AerodromeAip(),
            roles = mapOf(
                RoleName.TOWER to AerodromeRole(RoleName.TOWER, authorities, frequency),
            ),
        )
        val world = AviationWorld(
            geometry = PhysicalGeometry(points = mapOf(standId to Position(0.0, 0.0))),
            aerodromes = mapOf(lowg to ad),
        )
        val worldIndex = world.buildWorldIndex()

        // ── Controller + aircraft fixture ───────────────────────────────
        val tower = ControllerSpec(
            id = controllerId,
            role = RoleName.TOWER,
            aerodromeId = lowg,
            frequency = frequency,
            responsibilities = setOf(aircraftId),
        )

        val now = SimTime.ZERO
        val mission = createMission(
            goal = HighLevelGoal.CircuitTraining(circuits = 1, fullStopOnLast = true),
            startPhase = PilotPhase.AtStand,
            time = now,
        )
        val aircraft = AircraftState(
            id = aircraftId,
            callsign = Callsign("OEABC"),
            position = world.geometry.points.getValue(standId),
            positionPoint = standId,
            phase = PilotPhase.AtStand,
            pilotMission = mission,
        )

        val state = SimState.initial(
            seed = 42L,
            world = world,
            worldIndex = worldIndex,
            aircraft = listOf(aircraft),
            controllers = listOf(tower),
            weatherByAerodrome = mapOf(
                lowg to WeatherObservation(
                    wind = WindReport.Available(Wind.unsafe(directionDegrees = 160, speedKnots = 8)),
                    qnh = null,
                    visibility = null,
                ),
            ),
        ).getOrElse { error("SimState.initial rejected fixture: $it") }

        // ── Inject a ReadbackCorrection utterance ──────────────────────
        val correctedInstruction = ClearedToLand(target = aircraftId, runway = rwy)
        val correction = ReadbackCorrection(
            target = aircraftId,
            correct = correctedInstruction,
            defects = NonEmptyList(AtomDefect.MissingAtom(ClearedToLandReadback(rwy)), emptyList()),
        )
        val respondOutput = ControllerOutput.Respond(
            target = aircraftId,
            response = correction,
            trace = DecisionTrace(
                ruleId = "TEST-ReadbackCorrection",
                description = "Test injection",
                regulations = emptyList(),
            ),
        )
        val utterance = Utterance.FromController(respondOutput)
        val event = SimEvent.PilotProcessingComplete(
            time = now,
            aircraftId = aircraftId,
            utterance = utterance,
        )

        // ── Step the sim: pilot processes the correction ───────────────
        val (afterStep, emitted) = step(state, event)

        // ── Assert: pilot emitted a TransmissionStart with corrected Readback ──
        val transmissionStarts = emitted.filterIsInstance<SimEvent.TransmissionStart>()
        assertEquals(1, transmissionStarts.size,
            "Expected one corrected-readback transmission scheduled, got ${transmissionStarts.size}")

        val tx = transmissionStarts.single().transmission
        assertEquals(aircraftId, (tx.speaker as SpeakerRef.Pilot).aircraftId,
            "Corrected readback must be from the pilot of the aircraft under correction")
        assertEquals(controllerId, (tx.receiver as ReceiverRef.Controller).id,
            "Corrected readback must address the responsible controller")
        assertEquals(frequency, tx.frequency,
            "Corrected readback must use the controller's current frequency (pilot has not switched)")

        val pilotTx = (tx.utterance as Utterance.FromPilot).transmission
        val readback = pilotTx as? xyz.easiersaid.twr.protocol.Readback
            ?: fail("Expected Readback transmission, got ${pilotTx::class.simpleName}")

        // The readback must carry the safety-critical atom for the corrected
        // ClearedToLand (the runway identifier).
        val carriesRunwayAtom = readback.elements.any {
            it is SimpleElement && it.value is ClearedToLandReadback
        }
        assertTrue(carriesRunwayAtom,
            "Corrected readback must carry ClearedToLandReadback atom; got ${readback.elements}")

        // ── Mission is unchanged (correction is verification, not re-execution) ──
        val updatedAc = afterStep.aircraft.getValue(aircraftId)
        assertEquals(aircraft.pilotMission, updatedAc.pilotMission,
            "Mission must be unchanged for ReadbackCorrection — original instruction was already processed")
    }
}
