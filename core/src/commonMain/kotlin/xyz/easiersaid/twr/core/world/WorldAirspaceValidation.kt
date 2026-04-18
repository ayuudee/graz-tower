package xyz.easiersaid.twr.core.world

import xyz.easiersaid.twr.protocol.PointId

internal fun AviationWorld.validateAirspaceCoverage(): List<WorldValidationIssue> {
    val coveredPoints = airspace.values.flatMap { volume -> volume.memberPoints }.toSet()

    val coverageIssues = geometry.points.keys
        .filter { point -> point !in coveredPoints }
        .sortedBy(PointId::value)
        .map { point ->
            WorldValidationIssue(
                WorldValidationCode.POINT_OUTSIDE_AIRSPACE,
                "Point ${point.value} is not contained in any airspace volume"
            )
        }

    val boundaryMembershipIssues = airspace.values.flatMap { volume ->
        // This is intentionally only a syntactic coupling check: boundary vertices
        // must be explicitly listed in memberPoints. It is not a geometric
        // point-in-polygon completeness check over the world geometry.
        volume.boundary?.rings.orEmpty().flatMap { ring ->
            ring.points
                .filter { point -> point !in volume.memberPoints }
                .sortedBy(PointId::value)
                .map { point ->
                    WorldValidationIssue(
                        WorldValidationCode.AIRSPACE_BOUNDARY_VERTEX_NOT_IN_MEMBERSHIP,
                        "Airspace volume ${volume.id.value} boundary vertex ${point.value} " +
                            "is not listed in explicit memberPoints"
                    )
                }
        }
    }

    return coverageIssues + boundaryMembershipIssues
}

internal fun AviationWorld.validateVfrRouteAirspaceProfiles(): List<WorldValidationIssue> =
    vfrRoutes.values.flatMap { route ->
        when (val profile = route.airspaceProfile) {
            null -> emptyList()
            is VfrRouteAirspaceProfile.InClass -> validateInClassRouteAirspace(route, profile)
            is VfrRouteAirspaceProfile.InVolume -> validateInVolumeRouteAirspace(route, profile)
            is VfrRouteAirspaceProfile.Segmented -> validateSegmentedRouteAirspace(route, profile)
        }
    }

private fun validateInClassRouteAirspace(
    route: VfrRoute,
    profile: VfrRouteAirspaceProfile.InClass
): List<WorldValidationIssue> =
    when (profile.airspaceClass) {
        AirspaceClass.A -> listOf(
            WorldValidationIssue(
                WorldValidationCode.UNIFORM_VFR_ROUTE_CONTROLLED_CLASS_WITHOUT_VOLUME,
                "VFR route ${route.id.value} uses IFR-only class A without an authoritative " +
                    "airspace volume reference"
            )
        )

        AirspaceClass.B, AirspaceClass.C, AirspaceClass.D -> listOf(
            WorldValidationIssue(
                WorldValidationCode.UNIFORM_VFR_ROUTE_CONTROLLED_CLASS_WITHOUT_VOLUME,
                "VFR route ${route.id.value} uses controlled class ${profile.airspaceClass.name} " +
                    "without an authoritative airspace volume reference"
            )
        )

        else -> emptyList()
    }

private fun AviationWorld.validateInVolumeRouteAirspace(
    route: VfrRoute,
    profile: VfrRouteAirspaceProfile.InVolume
): List<WorldValidationIssue> {
    val volume = airspace[profile.airspaceVolume]
        ?: return listOf(
            WorldValidationIssue(
                WorldValidationCode.VFR_ROUTE_UNKNOWN_VOLUME,
                "VFR route ${route.id.value} references unknown airspace volume ${profile.airspaceVolume.value}"
            )
        )

    return route.waypoints
        .map { waypoint -> waypoint.point }
        .filter { point -> point !in volume.memberPoints }
        .sortedBy(PointId::value)
        .map { point ->
            WorldValidationIssue(
                WorldValidationCode.VFR_ROUTE_POINT_NOT_IN_VOLUME,
                "VFR route ${route.id.value} point ${point.value} is not contained in airspace volume ${volume.id.value}"
            )
        }
}

private fun AviationWorld.validateSegmentedRouteAirspace(
    route: VfrRoute,
    profile: VfrRouteAirspaceProfile.Segmented
): List<WorldValidationIssue> {
    val expectedSegments = route.waypoints.zipWithNext { from, to -> from.point to to.point }
    val alignmentIssues = if (
        profile.segments.size != expectedSegments.size ||
        profile.segments.zip(expectedSegments).any { (segment, expected) ->
            segment.from != expected.first || segment.to != expected.second
        }
    ) {
        listOf(
            WorldValidationIssue(
                WorldValidationCode.VFR_ROUTE_SEGMENT_SEQUENCE_MISMATCH,
                "Segmented VFR route ${route.id.value} does not align with the route waypoint sequence"
            )
        )
    } else {
        emptyList()
    }

    val segmentIssues = profile.segments.flatMap { segment ->
        val volume = airspace[segment.airspaceVolume]
            ?: return@flatMap listOf(
                WorldValidationIssue(
                    WorldValidationCode.VFR_ROUTE_SEGMENT_UNKNOWN_VOLUME,
                    "VFR route ${route.id.value} segment ${segment.from.value}->${segment.to.value} " +
                        "references unknown airspace volume ${segment.airspaceVolume.value}"
                )
            )

        listOf(segment.from, segment.to)
            .filter { point -> point !in volume.memberPoints }
            .sortedBy(PointId::value)
            .map { point ->
                WorldValidationIssue(
                    WorldValidationCode.VFR_ROUTE_SEGMENT_ENDPOINT_NOT_IN_VOLUME,
                    "VFR route ${route.id.value} segment endpoint ${point.value} is not contained in " +
                        "airspace volume ${volume.id.value}"
                )
            }
    }

    return alignmentIssues + segmentIssues
}
