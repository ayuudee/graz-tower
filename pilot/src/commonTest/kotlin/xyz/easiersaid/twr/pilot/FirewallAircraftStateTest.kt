package xyz.easiersaid.twr.pilot

import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.PointId
import kotlin.test.Test

/**
 * Architectural enforcement test (E5) — load-bearing same-treatment for AI/human pilots.
 *
 * The named-argument constructor below is the FIREWALL ALLOWLIST for
 * [AircraftState]. The pilot's behaviour does not branch on whether the
 * cockpit is crewed by a human or AI. This is enforced **structurally**:
 * no field on [AircraftState] may name or imply cockpit-crewing variety.
 *
 * If a future change re-introduces such a field (whether named
 * `humanPiloted`, `isAi`, `pilotKind`, `crewType`, `isAutomated`, or
 * anything analogous), this test fails to compile because the constructor
 * signature changes and a required argument is missing. The compile error
 * directs the reviewer here.
 *
 * This is paired with [FirewallSameTreatmentTest] (in jvmTest), which
 * catches *specific historical names* (`humanPiloted`, `pilotGoal`) by
 * source-text scan. **E5 is the load-bearing structural test**; the
 * source-text test is a tripwire for the historical names only.
 *
 * **No-suppression rule:** an architectural test failure is never resolved
 * by `@Disabled`, `@Suppress`, or test removal. Resolve by removing the
 * offending field. Real-world differences in crewing (AI vs human) are
 * observed at the radio level (the controller cannot tell who is flying;
 * neither can the test fixture); they are never assumed via a field on
 * [AircraftState].
 */
class FirewallAircraftStateTest {

    @Test
    fun `AircraftState has only kinematic and mission fields`() {
        // FIREWALL ALLOWLIST. Every field below must be either kinematic
        // ground truth (sim physics) or pilot mission state. No cockpit
        // crewing discriminator; no controller-derived field; no observation
        // accessor. See `pilot/AircraftState.kt` and the pilot-firewall plan.
        val canonical = AircraftState(
            id = AircraftId("X"),
            callsign = Callsign("X"),
            position = Position(xMeters = 0.0, yMeters = 0.0),
            positionPoint = PointId("P"),
            speedMps = 0.0,
            targetSpeedMps = 0.0,
            altitudeM = 0.0,
            targetAltitudeM = 0.0,
            phase = PilotPhase.AtStand,
            route = PilotRoute.None,
            type = xyz.easiersaid.twr.protocol.AircraftType.Default,
            pilotMission = null,
        )
        @Suppress("UNUSED_VARIABLE")
        val _check = canonical.id
    }
}
