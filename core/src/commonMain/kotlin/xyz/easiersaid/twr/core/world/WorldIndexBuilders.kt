package xyz.easiersaid.twr.core.world

import xyz.easiersaid.twr.protocol.PointId

fun Path.segmentIds(): List<SegmentId> =
    points.zipWithNext(::SegmentId)

fun Path.geometrySegmentIds(): List<GeometrySegmentId> =
    segmentIds().map { segment ->
        GeometrySegmentId.between(segment.from, segment.to)
    }

fun AviationWorld.buildWorldIndex(): WorldIndex =
    WorldIndex(
        positions = geometry.points,
        adjacency = deriveAdjacency(),
        surfaceBySegment = geometry.expandSegmentValues { segment -> segment.surface },
        lengthBySegment = geometry.expandSegmentValues { segment -> segment.length },
        widthBySegment = geometry.expandSegmentValues { segment -> segment.width },
        entitiesByPoint = deriveEntitiesByPoint(),
        holdingPointsByRunway = deriveHoldingPointsByRunway(),
        circuitLegsByPoint = deriveCircuitLegsByPoint(),
    )

fun AviationWorld.deriveHoldingPointsByRunway(): Map<xyz.easiersaid.twr.protocol.RunwayId, Set<PointId>> {
    val entries = aerodromes.values.flatMap { aerodrome ->
        aerodrome.taxiways.values.flatMap { taxiway ->
            taxiway.holdingPoints.mapNotNull { hp ->
                hp.runway?.let { runway -> runway to hp.point }
            }
        }
    }
    return entries.groupBy(
        keySelector = { (runway, _) -> runway },
        valueTransform = { (_, point) -> point }
    ).mapValues { (_, points) -> points.toSet() }
}

fun AviationWorld.deriveAdjacency(): Map<PointId, Set<PointId>> {
    val edges = geometry.segments.keys.flatMap { segment ->
        listOf(segment.first to segment.second, segment.second to segment.first)
    }
    return edges.groupBy(
        keySelector = { (from, _) -> from },
        valueTransform = { (_, to) -> to }
    ).mapValues { (_, neighbors) -> neighbors.toSet() }
}

fun AviationWorld.deriveEntitiesByPoint(): Map<PointId, Set<EntityRef>> {
    val entries = collectEntityPointEntries()
    return entries.groupBy(
        keySelector = { (point, _) -> point },
        valueTransform = { (_, ref) -> ref }
    ).mapValues { (_, refs) -> refs.toSet() }
}

private fun AviationWorld.collectEntityPointEntries(): List<Pair<PointId, EntityRef>> =
    collectFixEntries() +
        aerodromes.values.flatMap(::collectAerodromeEntries) +
        collectAirwayEntries() +
        collectVfrRouteEntries() +
        collectAirspaceEntries()

private fun AviationWorld.collectFixEntries(): List<Pair<PointId, EntityRef>> =
    fixes.values.map { fix -> fix.point to EntityRef.FixRef(fix.id) }

private fun AviationWorld.collectAerodromeEntries(
    aerodrome: Aerodrome
): List<Pair<PointId, EntityRef>> =
    collectRunwayEntries(aerodrome) +
        collectTaxiwayEntries(aerodrome) +
        collectStandEntries(aerodrome) +
        collectApronEntries(aerodrome) +
        collectCircuitEntries(aerodrome) +
        collectSidEntries(aerodrome) +
        collectStarEntries(aerodrome) +
        collectApproachEntries(aerodrome) +
        collectHoldingPatternEntries(aerodrome) +
        collectAerodromeAipEntries(aerodrome)

private fun collectRunwayEntries(aerodrome: Aerodrome): List<Pair<PointId, EntityRef>> =
    aerodrome.runways.values.flatMap { runway ->
        runway.path.points.map { point -> point to EntityRef.RunwayRef(runway.id) }
    }

private fun collectTaxiwayEntries(aerodrome: Aerodrome): List<Pair<PointId, EntityRef>> =
    aerodrome.taxiways.values.flatMap { taxiway ->
        val ref = EntityRef.TaxiwayRef(taxiway.id)
        taxiway.path.points.map { point -> point to ref } +
            taxiway.holdingPoints.map { holdingPoint -> holdingPoint.point to ref }
    }

private fun collectStandEntries(aerodrome: Aerodrome): List<Pair<PointId, EntityRef>> =
    aerodrome.stands.values.map { stand -> stand.point to EntityRef.StandRef(stand.id) }

private fun collectApronEntries(aerodrome: Aerodrome): List<Pair<PointId, EntityRef>> =
    aerodrome.aprons.values.flatMap { apron ->
        val ref = EntityRef.ApronRef(apron.id)
        apron.paths.flatMap { path -> path.points.map { point -> point to ref } }
    }

private fun collectCircuitEntries(aerodrome: Aerodrome): List<Pair<PointId, EntityRef>> =
    aerodrome.circuits.values.flatMap { circuit ->
        val ref = EntityRef.CircuitProcedureRef(circuit.id)
        val legEntries = circuit.legs.flatMap { leg ->
            leg.path.points.map { point -> point to ref }
        }
        val joinEntries = circuit.joinProcedures.flatMap { join ->
            listOf(join.entryPoint to ref) +
                (join.entryPath?.points?.map { point -> point to ref } ?: emptyList())
        }
        val downwindEntries = circuit.extendedDownwind?.let { extension ->
            extension.extendedPath.points.map { point -> point to ref } +
                extension.offRamps.flatMap { ramp -> ramp.path.points.map { point -> point to ref } }
        } ?: emptyList()
        val orbitEntries = circuit.orbitPoints.flatMap { orbit ->
            listOf(orbit.point to ref) + orbit.loop.points.map { point -> point to ref }
        }
        val reportingEntries = circuit.reportingPoints.values.map { point -> point to ref }
        val goAroundEntries = circuit.goAroundPath.points.map { point -> point to ref }
        legEntries + joinEntries + downwindEntries + orbitEntries + reportingEntries + goAroundEntries
    }

private fun collectSidEntries(aerodrome: Aerodrome): List<Pair<PointId, EntityRef>> =
    aerodrome.sids.values.flatMap { sid ->
        val ref = EntityRef.SidRef(sid.id)
        sid.waypoints.map { waypoint -> waypoint.point to ref } +
            sid.transitions.values.flatten().map { waypoint -> waypoint.point to ref }
    }

private fun collectStarEntries(aerodrome: Aerodrome): List<Pair<PointId, EntityRef>> =
    aerodrome.stars.values.flatMap { star ->
        val ref = EntityRef.StarRef(star.id)
        star.waypoints.map { waypoint -> waypoint.point to ref } +
            star.transitions.values.flatten().map { waypoint -> waypoint.point to ref }
    }

private fun collectApproachEntries(aerodrome: Aerodrome): List<Pair<PointId, EntityRef>> =
    aerodrome.approaches.values.flatMap { approach ->
        val ref = EntityRef.ApproachRef(approach.id)
        approach.waypoints.map { waypoint -> waypoint.point to ref } +
            approach.missedApproach.waypoints.map { waypoint -> waypoint.point to ref }
    }

private fun AviationWorld.collectHoldingPatternEntries(
    aerodrome: Aerodrome
): List<Pair<PointId, EntityRef>> =
    aerodrome.holdingPatterns.values.flatMap { holdingPattern ->
        val ref = EntityRef.HoldingPatternRef(holdingPattern.id)
        holdingPattern.loop.points.map { point -> point to ref } +
            listOfNotNull(fixes[holdingPattern.fix]?.let { fix -> fix.point to ref })
    }

private fun AviationWorld.collectAirwayEntries(): List<Pair<PointId, EntityRef>> =
    airways.values.flatMap { airway ->
        val ref = EntityRef.AirwayRef(airway.id)
        airway.waypoints.map { waypoint -> waypoint.point to ref }
    }

private fun AviationWorld.collectVfrRouteEntries(): List<Pair<PointId, EntityRef>> =
    vfrRoutes.values.flatMap { route ->
        val ref = EntityRef.VfrRouteRef(route.id)
        route.waypoints.map { waypoint -> waypoint.point to ref }
    }

private fun AviationWorld.collectAirspaceEntries(): List<Pair<PointId, EntityRef>> =
    airspace.values.flatMap { airspaceVolume ->
        val ref = EntityRef.AirspaceVolumeRef(airspaceVolume.id)
        airspaceVolume.memberPoints.map { point -> point to ref } +
            // Boundary vertices are intentionally registered as well as memberPoints.
            // deriveEntitiesByPoint() deduplicates by Set<EntityRef>, so keeping both
            // sources makes the boundary claim explicit without double-counting.
            airspaceVolume.boundary.orEmpty().flatMap { ring ->
                ring.points.map { point -> point to ref }
            }
    }

private fun AirspaceBoundary?.orEmpty(): List<BoundaryRing> =
    this?.rings.orEmpty()

fun AviationWorld.deriveCircuitLegsByPoint(): Map<xyz.easiersaid.twr.protocol.PointId, Set<LegName>> {
    val entries = aerodromes.values.flatMap { aerodrome ->
        aerodrome.circuits.values.flatMap { circuit ->
            circuit.legs.flatMap { leg ->
                leg.path.points.map { point -> point to leg.name }
            }
        }
    }
    return entries.groupBy(
        keySelector = { (point, _) -> point },
        valueTransform = { (_, legName) -> legName }
    ).mapValues { (_, names) -> names.toSet() }
}

private fun <T> PhysicalGeometry.expandSegmentValues(
    valueSelector: (SegmentGeometry) -> T
): Map<SegmentId, T> =
    segments.flatMap { (segmentId, geometry) ->
        val value = valueSelector(geometry)
        segmentId.directedIds().map { directed -> directed to value }
    }.toMap()
