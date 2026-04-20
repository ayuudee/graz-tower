package xyz.easiersaid.twr.sim

import arrow.core.NonEmptyList
import kotlinx.serialization.json.Json
import xyz.easiersaid.twr.controller.PilotGoal
import xyz.easiersaid.twr.core.world.*
import xyz.easiersaid.twr.migration.world.CandidateCircuitProcedure
import xyz.easiersaid.twr.migration.world.WorldCandidateDocument
import xyz.easiersaid.twr.protocol.*
import java.io.File
import kotlin.test.Test

/**
 * Throwaway spike: load LOWG world, spawn two arrivals on the circuit,
 * run the sim, print what happens.
 */
class LowgSpike {

    private val json = Json { ignoreUnknownKeys = true }

    private val candidate: WorldCandidateDocument by lazy {
        val file = File("../cad/airports/rendered/lowg/world-candidate.json")
        json.decodeFromString(file.readText())
    }

    private fun loadLowgWorld(): Pair<AviationWorld, CandidateCircuitProcedure> {
        val cw = candidate.world
        val ad = cw.aerodrome
        val positions = cw.geometry.points.mapValues { (_, p) -> Position(p.xMeters, p.yMeters) }
            .mapKeys { (id, _) -> PointId(id) }

        val pathMap = cw.geometry.paths.mapValues { (_, p) -> Path(p.pointIds.map(::PointId)) }

        // Pick a circuit — 34C center west (left-hand, most standard)
        val circuitData = ad.circuits.values.firstOrNull { it.id.contains("34C") && it.id.contains("WEST") }
            ?: ad.circuits.values.first()
        val circuitId = CircuitProcedureId(circuitData.id)
        val runwayId = RunwayId(circuitData.runwayId)

        val circuit = CircuitProcedure(
            id = circuitId,
            runway = runwayId,
            direction = if (circuitData.direction == "LEFT_HAND") CircuitDirection.LEFT_HAND else CircuitDirection.RIGHT_HAND,
            legs = circuitData.legs.map { leg ->
                CircuitLeg(LegName.valueOf(leg.name), pathMap[leg.pathId] ?: Path(emptyList()))
            },
            altitude = Level.AltitudeFeet.unsafe(circuitData.altitudeFeet),
            goAroundPath = pathMap[circuitData.goAroundPathId] ?: Path(emptyList()),
        )

        val rwyData = ad.runways.values.first { it.id == circuitData.runwayId }
        val runway = Runway(
            id = runwayId,
            path = pathMap[rwyData.pathId] ?: Path(emptyList()),
            threshold = PointId(rwyData.thresholdPointId),
        )

        val aerodromeId = AerodromeId(ad.icao)
        val world = AviationWorld(
            geometry = PhysicalGeometry(points = positions, segments = emptyMap()),
            aerodromes = mapOf(aerodromeId to Aerodrome(
                icao = aerodromeId,
                elevation = Feet(ad.elevationFeet),
                magneticVariation = Degrees(ad.magneticVariationDegrees.toDouble()),
                transitionAltitude = Level.AltitudeFeet.unsafe(ad.transitionAltitudeFeet),
                runways = mapOf(runwayId to runway),
                stands = emptyMap(),
                taxiways = emptyMap(),
                circuits = mapOf(circuitId to circuit),
                roles = mapOf(
                    RoleName.TOWER to AerodromeRole(
                        name = RoleName.TOWER,
                        authorities = setOf(AuthorityGrant(AuthorityEntityType.RUNWAY, setOf(
                            AuthorityOperation.LINE_UP, AuthorityOperation.TAKEOFF, AuthorityOperation.LAND,
                        ))),
                        frequency = Frequency.unsafe("118.200"),
                    ),
                    RoleName.APPROACH to AerodromeRole(
                        name = RoleName.APPROACH,
                        authorities = setOf(AuthorityGrant(AuthorityEntityType.AIRSPACE_VOLUME, setOf(
                            AuthorityOperation.AIRSPACE_TRANSIT,
                        ))),
                        frequency = Frequency.unsafe("120.350"),
                    ),
                ),
            )),
        )
        return world to circuitData
    }

    private fun buildWorldIndex(world: AviationWorld): WorldIndex {
        val aero = world.aerodromes.values.first()
        val adjacency = mutableMapOf<PointId, MutableSet<PointId>>()
        val entities = mutableMapOf<PointId, MutableSet<EntityRef>>()
        val circuitLegs = mutableMapOf<PointId, MutableSet<LegName>>()

        aero.runways.values.forEach { rwy ->
            rwy.path.points.zipWithNext().forEach { (a, b) ->
                adjacency.getOrPut(a) { mutableSetOf() }.add(b)
                adjacency.getOrPut(b) { mutableSetOf() }.add(a)
            }
            rwy.path.points.forEach { pt -> entities.getOrPut(pt) { mutableSetOf() }.add(EntityRef.RunwayRef(rwy.id)) }
        }
        aero.circuits.values.forEach { cct ->
            cct.legs.forEach { leg ->
                leg.path.points.zipWithNext().forEach { (a, b) ->
                    adjacency.getOrPut(a) { mutableSetOf() }.add(b)
                    adjacency.getOrPut(b) { mutableSetOf() }.add(a)
                }
                leg.path.points.forEach { pt ->
                    entities.getOrPut(pt) { mutableSetOf() }.add(EntityRef.CircuitProcedureRef(cct.id))
                    circuitLegs.getOrPut(pt) { mutableSetOf() }.add(leg.name)
                }
            }
        }

        return WorldIndex(
            positions = world.geometry.points,
            adjacency = adjacency.mapValues { it.value.toSet() },
            entitiesByPoint = entities.mapValues { it.value.toSet() },
            circuitLegsByPoint = circuitLegs.mapValues { it.value.toSet() },
            thresholdByRunway = aero.runways.mapValues { (_, rwy) -> rwy.threshold },
        )
    }

    @Test
    fun `LOWG spike — two arrivals on circuit`() {
        val (world, circuitData) = loadLowgWorld()
        val worldIndex = buildWorldIndex(world)
        val aero = world.aerodromes.values.first()
        val circuit = aero.circuits.values.first()
        val runwayId = circuit.runway
        val thresholdPt = aero.runways[runwayId]!!.threshold

        val downwindLeg = circuit.legs.first { it.name == LegName.DOWNWIND }
        val baseLeg = circuit.legs.first { it.name == LegName.BASE }
        val finalLeg = circuit.legs.first { it.name == LegName.FINAL }
        val downwindPt = downwindLeg.path.points.first()
        val basePt = baseLeg.path.points.first()
        val finalPt = finalLeg.path.points.first()

        println("=== LOWG Spike ===")
        println("Circuit: ${circuit.id} (${circuit.direction})")
        println("Runway: $runwayId → threshold: $thresholdPt")
        println("Downwind: $downwindPt @ ${worldIndex.positions[downwindPt]}")
        println("Base: $basePt @ ${worldIndex.positions[basePt]}")
        println("Final: $finalPt @ ${worldIndex.positions[finalPt]}")
        println("Positions: ${worldIndex.positions.size}, adjacency: ${worldIndex.adjacency.size}, circuit-leg points: ${worldIndex.circuitLegsByPoint.size}")

        val arr1 = AircraftState(
            id = AircraftId("OE-ARR1"), callsign = Callsign("OE-ARR1"),
            position = worldIndex.positions[finalPt]!!,
            positionPoint = finalPt,
            altitudeM = 500.0, targetAltitudeM = 500.0,
            speedMps = PilotConstants.APPROACH_SPEED_MPS,
            targetSpeedMps = PilotConstants.APPROACH_SPEED_MPS,
            phase = PilotPhase.Final,
            route = PilotRoute.Airborne(
                waypoints = NonEmptyList(thresholdPt, emptyList()),
                targetAltitudeM = 500.0, arrivalPhase = PilotPhase.LandingRoll,
            ),
            pilotGoal = PilotGoal.ARRIVE, humanPiloted = false,
            pilotMission = createMission(PilotGoal.ARRIVE, PilotPhase.Final, SimTime.ZERO),
        )

        val arr2 = AircraftState(
            id = AircraftId("OE-ARR2"), callsign = Callsign("OE-ARR2"),
            position = worldIndex.positions[downwindPt]!!,
            positionPoint = downwindPt,
            altitudeM = 600.0, targetAltitudeM = 600.0,
            speedMps = PilotConstants.CLIMB_SPEED_MPS,
            targetSpeedMps = PilotConstants.CLIMB_SPEED_MPS,
            phase = PilotPhase.Downwind,
            route = PilotRoute.Airborne(
                waypoints = NonEmptyList(basePt, finalLeg.path.points + listOf(thresholdPt)),
                targetAltitudeM = 600.0, arrivalPhase = PilotPhase.LandingRoll,
            ),
            pilotGoal = PilotGoal.ARRIVE, humanPiloted = false,
            pilotMission = createMission(PilotGoal.ARRIVE, PilotPhase.Downwind, SimTime.ZERO),
        )

        val twr = ControllerSpec(
            id = ControllerId("LOWG-TWR"), role = RoleName.TOWER,
            aerodromeId = AerodromeId("LOWG"), frequency = Frequency.unsafe("118.200"),
            responsibilities = setOf(arr1.id, arr2.id),
        )

        println("\nSpawning: ${arr1.id} on FINAL, ${arr2.id} on DOWNWIND")
        println("Running 120s...\n")

        val result = runUntil(
            initial = SimState.initial(seed = 42L, world = world, worldIndex = worldIndex, controllers = listOf(twr)),
            initialEvents = listOf(
                SimEvent.PhysicsTick(SimTime.ZERO),
                SimEvent.Spawn(SimTime.ZERO, arr1),
                SimEvent.Spawn(SimTime.ZERO, arr2),
                SimEvent.ControllerCycle(SimTime.ZERO, twr.id),
            ),
            until = SimTime.ofSeconds(300), // 5 minutes — enough for radio pipeline delays
        )

        println("=== t=${result.now.millis / 1000}s ===")
        result.aircraft.forEach { (id, ac) ->
            val mStep = ac.pilotMission?.currentTask?.step?.name ?: "no-mission"
            val mComplete = ac.pilotMission?.isComplete ?: false
            val lastReport = ac.pilotMission?.lastReportedLeg
            println("  $id: phase=${ac.phase} point=${ac.positionPoint} alt=${"%.0f".format(ac.altitudeM)}m spd=${"%.1f".format(ac.speedMps)}m/s mission=$mStep(done=$mComplete) lastReport=$lastReport")
        }
        val beliefs = result.beliefs.values.firstOrNull()
        if (beliefs != null) {
            println("\nBeliefs:")
            println("  Commitments: ${beliefs.commitments.map { (id, c) -> "$id=${c.kind.trafficType}/${c.stage}" }}")
            println("  Sequence: ${beliefs.arrivalSequence?.slots?.map { "#${it.stableNumber} ${it.aircraft} gate=${it.gate}" }}")
            println("  Separation: ${beliefs.separationAssessments.map { "${it.aircraft}↔${it.other}: ${it.concern}" }}")
            println("  Duty: holder=${beliefs.runwayDuty?.holder} queue=${beliefs.runwayDuty?.queue?.map { it.aircraft }}")
        }
        println("\n=== Spike done ===")
    }
}
