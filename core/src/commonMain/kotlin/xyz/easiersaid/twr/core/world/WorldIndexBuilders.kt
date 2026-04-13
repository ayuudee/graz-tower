package xyz.easiersaid.twr.core.world

import xyz.easiersaid.twr.protocol.PointId

fun Path.segmentIds(): List<SegmentId> =
    points.zipWithNext(::SegmentId)

fun AviationWorld.deriveAdjacency(): Map<PointId, Set<PointId>> {
    val adjacency = linkedMapOf<PointId, MutableSet<PointId>>()

    fun addEdge(from: PointId, to: PointId) {
        adjacency.getOrPut(from) { linkedSetOf() }.add(to)
        adjacency.getOrPut(to) { linkedSetOf() }.add(from)
    }

    fun addPath(path: Path) {
        path.segmentIds().forEach { segment ->
            addEdge(segment.from, segment.to)
        }
    }

    allPaths().forEach(::addPath)

    return adjacency.mapValues { (_, points) -> points.toSet() }
}

fun AviationWorld.deriveEntitiesByPoint(): Map<PointId, Set<EntityRef>> {
    val entities = linkedMapOf<PointId, MutableSet<EntityRef>>()

    fun addPoint(point: PointId, ref: EntityRef) {
        entities.getOrPut(point) { linkedSetOf() }.add(ref)
    }

    fun addPath(path: Path, ref: EntityRef) {
        path.points.forEach { point -> addPoint(point, ref) }
    }

    fixes.values.forEach { fix ->
        addPoint(fix.point, EntityRef.FixRef(fix.id))
    }

    aerodromes.values.forEach { aerodrome ->
        aerodrome.runways.values.forEach { runway ->
            addPath(runway.path, EntityRef.RunwayRef(runway.id))
        }
        aerodrome.taxiways.values.forEach { taxiway ->
            addPath(taxiway.path, EntityRef.TaxiwayRef(taxiway.id))
            taxiway.holdingPoints.forEach { holdingPoint ->
                addPoint(holdingPoint.point, EntityRef.TaxiwayRef(taxiway.id))
            }
        }
        aerodrome.stands.values.forEach { stand ->
            addPoint(stand.point, EntityRef.StandRef(stand.id))
        }
        aerodrome.aprons.values.forEach { apron ->
            apron.paths.forEach { path -> addPath(path, EntityRef.ApronRef(apron.id)) }
        }
        aerodrome.circuits.values.forEach { circuit ->
            val ref = EntityRef.CircuitProcedureRef(circuit.id)
            circuit.legs.forEach { leg -> addPath(leg.path, ref) }
            circuit.joinProcedures.forEach { join ->
                addPoint(join.entryPoint, ref)
                join.entryPath?.let { addPath(it, ref) }
            }
            circuit.extendedDownwind?.let { extension ->
                addPath(extension.extendedPath, ref)
                extension.offRamps.forEach { addPath(it.path, ref) }
            }
            circuit.orbitPoints.forEach { orbit ->
                addPoint(orbit.point, ref)
                addPath(orbit.loop, ref)
            }
            circuit.reportingPoints.values.forEach { point -> addPoint(point, ref) }
            addPath(circuit.goAroundPath, ref)
        }
        aerodrome.sids.values.forEach { sid ->
            val ref = EntityRef.SidRef(sid.id)
            sid.waypoints.forEach { waypoint -> addPoint(waypoint.point, ref) }
            sid.transitions.values.flatten().forEach { waypoint -> addPoint(waypoint.point, ref) }
        }
        aerodrome.stars.values.forEach { star ->
            val ref = EntityRef.StarRef(star.id)
            star.waypoints.forEach { waypoint -> addPoint(waypoint.point, ref) }
            star.transitions.values.flatten().forEach { waypoint -> addPoint(waypoint.point, ref) }
        }
        aerodrome.approaches.values.forEach { approach ->
            val ref = EntityRef.ApproachRef(approach.id)
            approach.waypoints.forEach { waypoint -> addPoint(waypoint.point, ref) }
            approach.missedApproach.waypoints.forEach { waypoint -> addPoint(waypoint.point, ref) }
        }
        aerodrome.holdingPatterns.values.forEach { holdingPattern ->
            val ref = EntityRef.HoldingPatternRef(holdingPattern.id)
            addPath(holdingPattern.loop, ref)
            fixes[holdingPattern.fix]?.let { fix -> addPoint(fix.point, ref) }
        }
    }

    airways.values.forEach { airway ->
        val ref = EntityRef.AirwayRef(airway.id)
        airway.waypoints.forEach { waypoint -> addPoint(waypoint.point, ref) }
    }

    vfrRoutes.values.forEach { route ->
        val ref = EntityRef.VfrRouteRef(route.id)
        route.waypoints.forEach { waypoint -> addPoint(waypoint.point, ref) }
    }

    airspace.values.forEach { airspaceVolume ->
        val ref = EntityRef.AirspaceVolumeRef(airspaceVolume.id)
        airspaceVolume.points.forEach { point -> addPoint(point, ref) }
    }

    return entities.mapValues { (_, refs) -> refs.toSet() }
}

private fun AviationWorld.allPaths(): Sequence<Path> =
    sequence {
        aerodromes.values.forEach { aerodrome ->
            aerodrome.runways.values.forEach { runway -> yield(runway.path) }
            aerodrome.taxiways.values.forEach { taxiway -> yield(taxiway.path) }
            aerodrome.aprons.values.forEach { apron ->
                apron.paths.forEach { path -> yield(path) }
            }
            aerodrome.circuits.values.forEach { circuit ->
                circuit.legs.forEach { leg -> yield(leg.path) }
                circuit.joinProcedures.mapNotNull { it.entryPath }.forEach { path -> yield(path) }
                circuit.extendedDownwind?.let { extension ->
                    yield(extension.extendedPath)
                    extension.offRamps.forEach { offRamp -> yield(offRamp.path) }
                }
                circuit.orbitPoints.forEach { orbit -> yield(orbit.loop) }
                yield(circuit.goAroundPath)
            }
            aerodrome.sids.values.forEach { sid ->
                sid.waypoints.asPathOrNull()?.let { path -> yield(path) }
                sid.transitions.values.forEach { transition ->
                    transition.asPathOrNull()?.let { path -> yield(path) }
                }
            }
            aerodrome.stars.values.forEach { star ->
                star.waypoints.asPathOrNull()?.let { path -> yield(path) }
                star.transitions.values.forEach { transition ->
                    transition.asPathOrNull()?.let { path -> yield(path) }
                }
            }
            aerodrome.approaches.values.forEach { approach ->
                approach.waypoints.asPathOrNull()?.let { path -> yield(path) }
                approach.missedApproach.waypoints.asPathOrNull()?.let { path -> yield(path) }
            }
            aerodrome.holdingPatterns.values.forEach { holdingPattern -> yield(holdingPattern.loop) }
        }
        airways.values.forEach { airway ->
            airway.waypoints.asPathOrNull()?.let { path -> yield(path) }
        }
        vfrRoutes.values.forEach { route ->
            route.waypoints.asPathOrNull()?.let { path -> yield(path) }
        }
    }

private fun List<Waypoint>.asPathOrNull(): Path? =
    takeIf { it.size >= 2 }?.let { waypoints ->
        Path(waypoints.map { waypoint -> waypoint.point })
    }
