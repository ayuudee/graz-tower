package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Atis
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.CurrentInformationIs
import xyz.easiersaid.twr.protocol.InitialContact
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayConfiguration
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Wind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pass 15 (D-AUDIT.8 closure) — controller-side ATIS-letter mismatch
 * advisory emission. Per ICAO Annex 11 §4.3.6, when a pilot's
 * `InitialContact.atisCode` differs from the controller's
 * `expectedAtisLetter`, the controller issues a `CurrentInformationIs`
 * advisory naming the current letter (no readback obligation).
 *
 * Three rows pin the production code path through `controllerDecide`:
 *  1. Mismatch → advisory emitted with current letter.
 *  2. Match → silent (no advisory).
 *  3. Pilot omitted atisCode (legacy / pre-ATIS) → silent (not a mismatch).
 */
class AtisLetterMismatchAdvisorySpec {

    private val LOWG = AerodromeId("LOWG")
    private val ac = AircraftId("OE-ABC")
    private val ctrlId = ControllerId("LOWG_TWR")
    private val now0 = SimTime.ofMillis(0)

    private fun atis(letter: Char): Atis = Atis(
        letter = letter,
        aerodrome = LOWG,
        configuration = RunwayConfiguration(
            arrivals = listOf(RunwayId("16C")),
            departures = listOf(RunwayId("16C")),
        ),
        wind = Wind.unsafe(160, 8),
        qnh = null,
        visibility = null,
        generatedAt = now0,
    )

    // fn-6.1: seed WorldIndex with the test point so `fromTestPoint` derives
    // coords non-divergently. ATIS-letter advisory logic reads no geometric
    // field, so coords are not load-bearing here, but the helper enforces the
    // no-fixture-drift invariant.
    private val testWorldIndex = WorldIndex(
        positions = mapOf(PointId("P") to Position(xMeters = 0.0, yMeters = 0.0)),
    )

    private fun viewWithReceivedInitialContact(
        atisOnView: Char?,
        pilotAtisCode: Char?,
    ): ControllerView {
        val atisMap = if (atisOnView != null) mapOf(LOWG to atis(atisOnView)) else emptyMap()
        val initialContact = InitialContact(
            stationCalled = RoleName.TOWER,
            atisCode = pilotAtisCode,
        )
        return ControllerView(
            time = now0,
            controllerId = ctrlId,
            role = RoleName.TOWER,
            aerodromeId = LOWG,
            responsibilities = setOf(ac),
            aircraft = mapOf(
                ac to AircraftObservation.fromTestPoint(
                    point = PointId("P"),
                    worldIndex = testWorldIndex,
                    id = ac,
                    callsign = Callsign("OEABC"),
                    onGround = true,
                ),
            ),
            runways = emptyMap(),
            activeClearances = emptyMap(),
            receivedMessages = listOf(ReceivedMessage.Clear(ac, initialContact)),
            weather = null,
            worldIndex = testWorldIndex,
            atis = atisMap,
        )
    }

    @Test
    fun `pilot atisCode differs from expected emits CurrentInformationIs per Annex 11 sec4dot3dot6`() {
        // First decide cycle folds expectedAtisLetter from view.atis;
        // the same cycle scans receivedMessages and emits the advisory.
        val view = viewWithReceivedInitialContact(atisOnView = 'B', pilotAtisCode = 'A')
        val result = controllerDecide(view, BeliefState.EMPTY, AviationWorld())
        val advisories = result.outputs
            .filterIsInstance<ControllerOutput.Respond>()
            .map { it.response }
            .filterIsInstance<CurrentInformationIs>()
        assertEquals(1, advisories.size, "exactly one advisory expected; got: $advisories")
        assertEquals(
            'B',
            advisories.single().letter,
            "Annex 11 §4.3.6: advisory carries the controller's CURRENT letter, not the pilot's stale one",
        )
        assertEquals(ac, advisories.single().target, "advisory targets the aircraft that mis-acknowledged")
    }

    @Test
    fun `pilot atisCode matches expected emits no advisory`() {
        val view = viewWithReceivedInitialContact(atisOnView = 'A', pilotAtisCode = 'A')
        val result = controllerDecide(view, BeliefState.EMPTY, AviationWorld())
        val advisories = result.outputs
            .filterIsInstance<ControllerOutput.Respond>()
            .map { it.response }
            .filterIsInstance<CurrentInformationIs>()
        assertTrue(advisories.isEmpty(), "matching letters → no advisory; got: $advisories")
    }

    @Test
    fun `pilot omitted atisCode is silent — pre-ATIS legacy not a mismatch`() {
        // A null atisCode is the legacy shape (pre-Pass-15 InitialContact
        // construction). Treating it as a mismatch would spam advisories
        // at every legacy initial contact during migration. Per the
        // implementation: null atisCode short-circuits.
        val view = viewWithReceivedInitialContact(atisOnView = 'A', pilotAtisCode = null)
        val result = controllerDecide(view, BeliefState.EMPTY, AviationWorld())
        val advisories = result.outputs
            .filterIsInstance<ControllerOutput.Respond>()
            .map { it.response }
            .filterIsInstance<CurrentInformationIs>()
        assertTrue(advisories.isEmpty(), "null atisCode is not a mismatch; got: $advisories")
    }
}
