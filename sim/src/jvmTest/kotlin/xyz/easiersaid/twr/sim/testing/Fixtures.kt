package xyz.easiersaid.twr.sim.testing

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import xyz.easiersaid.twr.controller.WeatherObservation
import xyz.easiersaid.twr.controller.WindReport
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftIntent
import xyz.easiersaid.twr.protocol.FiledPlan
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.IcaoTypeDesignator
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.Wind

/**
 * Pre-built per-aerodrome [Fixture]s for sim integration tests. Constants
 * carry only [Path]s and primitives — no I/O happens at class load. I/O
 * fires when [Fixture.load] is called.
 *
 * Pass 10 evolution: per Pass 4 plan R2, the manifest-driven loader will
 * remove the need for `weather` and `controllerRoles` injection; those
 * fields disappear from [Fixture]. The `Fixtures` constants survive with
 * shrunk shapes.
 */
object Fixtures {

    val LOWG: Fixture = Fixture(
        aerodromeId = AerodromeId("LOWG"),
        candidatePath = projectRoot().resolve("cad/airports/rendered/lowg/world-candidate.json"),
        standPointId = PointId("LOWG_STAND_1_POINT"),
        frequency = Frequency.unsafe("118.200"),
        weather = WeatherObservation(
            wind = WindReport.Available(Wind.unsafe(directionDegrees = 160, speedKnots = 8)),
            qnh = null,
            visibility = null,
        ),
        // LOWG (per manifest): tower handles ground duties on the same RT;
        // we model the operational reality with both roles on the same freq.
        controllerRoles = setOf(RoleName.GROUND, RoleName.TOWER),
        // Pass 11 (D-AUDIT.6 / D-AUDIT.10): file the plan via AFTN-style
        // event distribution. Pre-Pass-11 this was a `groundResponsibilities`
        // direct-injection cheat; the strip now arrives via
        // `SimEvent.FlightPlanFiled` at sim-start.
        flightPlans = mapOf(
            AircraftId("OE-ABC") to FiledPlanForFixture(
                plan = FiledPlan.Vfr(
                    departureAerodrome = AerodromeId("LOWG"),
                    aircraftType = IcaoTypeDesignator.unsafe("C172"),
                    destinationAerodrome = null, // local circuit training
                    intent = AircraftIntent.Departing,
                ),
                recipient = RoleName.GROUND,
            ),
        ),
    )

    val LJMB: Fixture = Fixture(
        aerodromeId = AerodromeId("LJMB"),
        candidatePath = projectRoot().resolve("cad/airports/rendered/ljmb/world-candidate.json"),
        // LJMB candidate stands reference taxiway points; the GA-1 start 1
        // stand's pointId is LJMB_TWY_A_17_02. Future tests may copy this
        // Fixture and override standPointId for their stand of choice.
        standPointId = PointId("LJMB_TWY_A_17_02"),
        frequency = Frequency.unsafe("119.205"),
        weather = WeatherObservation(
            wind = WindReport.Available(Wind.unsafe(directionDegrees = 140, speedKnots = 6)),
            qnh = null,
            visibility = null,
        ),
        // LJMB AFIS only — but until D-PF.1 lands we model TOWER for the
        // simple G2-precursor sanity check.
        controllerRoles = setOf(RoleName.TOWER),
    )

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
