package xyz.easiersaid.twr.core.world

import xyz.easiersaid.twr.protocol.OrbitDirection
import xyz.easiersaid.twr.protocol.PointId

internal object RouteAdjacentFixtureIds {
    val extendedDownwindEnd = PointId("CIRCUIT_EXTENDED_DOWNWIND_END")
    val orbitNorth = PointId("CIRCUIT_ORBIT_NORTH")
    val orbitSouth = PointId("CIRCUIT_ORBIT_SOUTH")
}

internal fun routeAdjacentWorld(): AviationWorld {
    val world = sampleWorld()
    val aerodrome = world.aerodromes.getValue(FixtureIds.aerodrome)
    val circuit = aerodrome.circuits.getValue(FixtureIds.circuit09)

    val widenedCircuit = circuit.copy(
        extendedDownwind = ExtendedDownwind(
            extendedPath = Path(
                listOf(
                    FixtureIds.downwindEnd,
                    RouteAdjacentFixtureIds.extendedDownwindEnd
                )
            ),
            offRamps = listOf(
                OffRamp(
                    path = Path(
                        listOf(
                            RouteAdjacentFixtureIds.extendedDownwindEnd,
                            FixtureIds.baseTurn
                        )
                    )
                )
            )
        ),
        orbitPoints = listOf(
            OrbitPoint(
                point = FixtureIds.downwindEnd,
                loop = Path(
                    listOf(
                        FixtureIds.downwindEnd,
                        RouteAdjacentFixtureIds.orbitNorth,
                        RouteAdjacentFixtureIds.orbitSouth,
                        FixtureIds.downwindEnd
                    )
                ),
                direction = OrbitDirection.LEFT
            ),
            OrbitPoint(
                point = FixtureIds.downwindEnd,
                loop = Path(
                    listOf(
                        FixtureIds.downwindEnd,
                        RouteAdjacentFixtureIds.orbitSouth,
                        RouteAdjacentFixtureIds.orbitNorth,
                        FixtureIds.downwindEnd
                    )
                ),
                direction = OrbitDirection.RIGHT
            )
        )
    )

    return world.copy(
        geometry = routeAdjacentGeometry(world.geometry),
        aerodromes = world.aerodromes + (
            FixtureIds.aerodrome to aerodrome.copy(
                circuits = aerodrome.circuits + (FixtureIds.circuit09 to widenedCircuit)
            )
        )
    )
}

private fun routeAdjacentGeometry(base: PhysicalGeometry): PhysicalGeometry {
    val points = base.points + mapOf(
        RouteAdjacentFixtureIds.extendedDownwindEnd to Position(1850.0, 700.0),
        RouteAdjacentFixtureIds.orbitNorth to Position(1550.0, 950.0),
        RouteAdjacentFixtureIds.orbitSouth to Position(1250.0, 950.0)
    )

    val addedSegments = buildMap {
        addSegment(
            points,
            FixtureIds.downwindEnd,
            RouteAdjacentFixtureIds.extendedDownwindEnd,
            400.0,
            SurfaceType.SKY
        )
        addSegment(
            points,
            RouteAdjacentFixtureIds.extendedDownwindEnd,
            FixtureIds.baseTurn,
            400.0,
            SurfaceType.SKY
        )
        addSegment(
            points,
            FixtureIds.downwindEnd,
            RouteAdjacentFixtureIds.orbitNorth,
            400.0,
            SurfaceType.SKY
        )
        addSegment(
            points,
            RouteAdjacentFixtureIds.orbitNorth,
            RouteAdjacentFixtureIds.orbitSouth,
            400.0,
            SurfaceType.SKY
        )
        addSegment(
            points,
            RouteAdjacentFixtureIds.orbitSouth,
            FixtureIds.downwindEnd,
            400.0,
            SurfaceType.SKY
        )
    }

    return base.copy(
        points = points,
        segments = base.segments + addedSegments
    )
}
