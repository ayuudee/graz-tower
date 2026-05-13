package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.serialization.json.Json
import xyz.easiersaid.twr.controller.ControllerView
import xyz.easiersaid.twr.core.world.WeatherObservation
import xyz.easiersaid.twr.protocol.WindReport
import xyz.easiersaid.twr.controller.controllerDecide
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.migration.world.WorldCandidateDocument
import xyz.easiersaid.twr.migration.world.WorldCandidateLoader
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AftnAddress
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftIntent
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.FiledPlan
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.ResponsibilityState
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Pass 14 (D-AUDIT.6.A-FOLLOWUP / .6.B-FOLLOWUP / .13) — cross-aerodrome
 * filing end-to-end.
 *
 * Loads the rendered LOWG and LJMB world candidates, merges them via the
 * production [WorldCandidateLoader], registers controllers at both
 * aerodromes, and files a single VFR plan from LOWG to LJMB. Asserts
 * that the AFTN routing fans out to two recipients and that the
 * post-filing state has the right shape on both sides.
 *
 * This spec subsumes the Pass-13-era `MultiAerodromeWorldTest` (which
 * was scaffold-level: loaded both worlds, no flight choreography). The
 * Pass-14 version exercises real filing flow.
 *
 * Doctrine: ICAO Doc 4444 §11 (FPL filing), Annex 10 Vol II (AFTN).
 *
 * One comprehensive row, five projection assertions on a single
 * post-filing state vector. Single failure message lists which
 * projection failed.
 */
class CrossAerodromeFilingSpec {

    private val json = Json { ignoreUnknownKeys = true }
    private val LOWG = AerodromeId("LOWG")
    private val LJMB = AerodromeId("LJMB")
    private val ac = AircraftId("OE-LJB")
    private val now0 = SimTime.ZERO

    @Test
    fun `LOWG-to-LJMB VFR filing distributes Owned to LOWG_GROUND and knownStrips to LJMB_TOWER`() {
        val world = mergedWorld()

        val lowgGround = ControllerSpec(
            id = ControllerId("LOWG_GROUND"),
            role = RoleName.GROUND,
            aerodromeId = LOWG,
            frequency = Frequency.unsafe("118.200"),
            responsibilities = emptyMap(),
        )
        val lowgTower = ControllerSpec(
            id = ControllerId("LOWG_TOWER"),
            role = RoleName.TOWER,
            aerodromeId = LOWG,
            frequency = Frequency.unsafe("118.200"),
            responsibilities = emptyMap(),
        )
        val ljmbTower = ControllerSpec(
            id = ControllerId("LJMB_TOWER"),
            role = RoleName.TOWER,
            aerodromeId = LJMB,
            frequency = Frequency.unsafe("119.205"),
            responsibilities = emptyMap(),
        )

        val state = SimState.initial(
            seed = 0L,
            world = world,
            worldIndex = WorldIndex(positions = world.geometry.points),
            aircraft = emptyList(),
            controllers = listOf(lowgGround, lowgTower, ljmbTower),
            weatherByAerodrome = world.aerodromes.keys.associateWith {
                WeatherObservation(wind = WindReport.NotReported, qnh = null, visibility = null)
            },
        ).getOrElse { error("multi-aerodrome state setup invalid: $it") }

        val plan = FiledPlan.Vfr(
            departureAerodrome = LOWG,
            destinationAerodrome = LJMB,
            intent = AircraftIntent.Departing,
        )

        // Step the FlightPlanFiled events that the loader/producer
        // would emit — fanned out via routeFiledPlan to LOWG_GROUND
        // (departure) and LJMB_TOWER (destination).
        val recipients = AftnRouting.routeFiledPlan(plan) { aerodromeId ->
            world.aerodromes[aerodromeId]?.roles?.keys.orEmpty()
        }
            .fold({ fail("routeFiledPlan failed: $it") }, { it.toList() })
        assertEquals(
            listOf(
                AftnAddress(LOWG, RoleName.GROUND),
                AftnAddress(LJMB, RoleName.TOWER),
            ),
            recipients,
            "ICAO Doc 4444 §11: cross-aerodrome plan fans out to departure + destination bays",
        )

        var current = state
        for (recipient in recipients) {
            val (next, _) = step(current, SimEvent.FlightPlanFiled(
                time = now0,
                aircraft = ac,
                plan = plan,
                recipient = recipient,
            ))
            current = next
        }

        val gnd = current.controllers.getValue(lowgGround.id)
        val ljmb = current.controllers.getValue(ljmbTower.id)

        // Single comprehensive assertion: the cross-aerodrome filing's
        // post-state must satisfy ALL five projections simultaneously.
        // Build a list of projection-failures so the diagnostic names
        // every aspect that's wrong.
        val failures = buildList {
            // (a) LOWG_GROUND owns the aircraft (departure side).
            val gndState = gnd.responsibilities[ac]
            if (gndState !is ResponsibilityState.Owned) {
                add("LOWG_GROUND.responsibilities[$ac] = $gndState (expected Owned per Doc 4444 §11 departure side)")
            }
            // (b) LJMB_TOWER does NOT have the aircraft in responsibilities.
            if (ac in ljmb.responsibilities) {
                add("LJMB_TOWER.responsibilities[$ac] = ${ljmb.responsibilities[ac]} " +
                    "(expected absent — destination side has no responsibility before handoff)")
            }
            // (c) LJMB_TOWER has the strip in knownStrips.
            val ljmbStrip = ljmb.knownStrips[ac]
            if (ljmbStrip != plan) {
                add("LJMB_TOWER.knownStrips[$ac] = $ljmbStrip (expected $plan)")
            }
            // (d) LJMB_TOWER's view shows Arriving intent in flightStripIntents.
            val ljmbView = buildControllerView(current, ljmbTower.id)
            val ljmbIntent = ljmbView.flightStripIntents[ac]
            if (ljmbIntent != AircraftIntent.Arriving) {
                add("LJMB_TOWER ControllerView.flightStripIntents[$ac] = $ljmbIntent " +
                    "(expected Arriving per Annex 10 Vol II destination strip)")
            }
            // (e) LJMB_TOWER's view does NOT contain the aircraft observation
            //     (no sensor contact yet — strip ≠ observation).
            if (ac in ljmbView.aircraft) {
                add("LJMB_TOWER ControllerView.aircraft[$ac] is present " +
                    "(expected absent — destination tower has the strip but no radar contact)")
            }
            // (f) Pass 14 post-impl test review N1: LOWG_TWR (peer at the
            //     departure aerodrome, not the AFTN recipient) must NOT
            //     have the strip leak into knownStrips OR responsibilities.
            //     The router emitted only LOWG_GROUND for the departure side.
            val lowgTwr = current.controllers.getValue(lowgTower.id)
            if (ac in lowgTwr.knownStrips) {
                add("LOWG_TOWER.knownStrips[$ac] = ${lowgTwr.knownStrips[ac]} " +
                    "(expected absent — only LOWG_GROUND should receive the departure-side strip)")
            }
            if (ac in lowgTwr.responsibilities) {
                add("LOWG_TOWER.responsibilities[$ac] = ${lowgTwr.responsibilities[ac]} " +
                    "(expected absent — peer-at-departure must not gain responsibility)")
            }
        }
        check(failures.isEmpty()) {
            "Cross-aerodrome filing post-state assertions failed:\n  ${failures.joinToString("\n  ")}"
        }
    }

    private fun mergedWorld(): AviationWorld {
        val projectRoot = resolveProjectRoot()
        val lowgPath = projectRoot.resolve("cad/airports/rendered/lowg/world-candidate.json")
        val ljmbPath = projectRoot.resolve("cad/airports/rendered/ljmb/world-candidate.json")
        assertTrue(Files.exists(lowgPath), "Missing LOWG world candidate at $lowgPath")
        assertTrue(Files.exists(ljmbPath), "Missing LJMB world candidate at $ljmbPath")
        val lowgWorld = WorldCandidateLoader.toWorld(json.decodeFromString<WorldCandidateDocument>(Files.readString(lowgPath)))
        val ljmbWorld = WorldCandidateLoader.toWorld(json.decodeFromString<WorldCandidateDocument>(Files.readString(ljmbPath)))
        return WorldCandidateLoader.mergeAviationWorlds(listOf(lowgWorld, ljmbWorld))
    }

    private fun resolveProjectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val direct = cwd.resolve("settings.gradle.kts")
        return if (Files.exists(direct)) cwd else cwd.parent ?: cwd
    }
}
