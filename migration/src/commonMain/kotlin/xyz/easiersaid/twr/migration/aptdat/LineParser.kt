package xyz.easiersaid.twr.migration.aptdat

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import xyz.easiersaid.twr.migration.common.GeoCoordinate
import xyz.easiersaid.twr.migration.common.Latitude
import xyz.easiersaid.twr.migration.common.Longitude
import xyz.easiersaid.twr.migration.common.ParseError

internal fun parseGeo(lat: String, lon: String, line: Int): Either<ParseError, GeoCoordinate> {
    val latD = lat.toDoubleOrNull()
        ?: return ParseError(line, "Invalid latitude: $lat").left()
    val lonD = lon.toDoubleOrNull()
        ?: return ParseError(line, "Invalid longitude: $lon").left()
    val latV = Latitude(latD).fold(
        { ParseError(line, it).left() },
        { it.right() },
    )
    val lonV = Longitude(lonD).fold(
        { return ParseError(line, it).left() },
        { it },
    )
    return latV.map { GeoCoordinate(it, lonV) }
}

internal data class AirportHeader(
    val icao: String,
    val name: String,
    val elevationFeet: Int,
    val hasTower: Boolean,
)

internal fun parseAirportHeader(parts: List<String>, line: Int): Either<ParseError, AirportHeader> {
    if (parts.size < 5) return ParseError(line, "Airport header too short", "1").left()
    val elevation = parts[1].toIntOrNull()
        ?: return ParseError(line, "Invalid elevation: ${parts[1]}", "1").left()
    val hasTower = parts[2] != "0"
    return AirportHeader(
        icao = parts[4],
        name = parts.drop(5).joinToString(" "),
        elevationFeet = elevation,
        hasTower = hasTower,
    ).right()
}

internal fun parseLandRunway(parts: List<String>, line: Int): Either<ParseError, LandRunway> {
    if (parts.size < 26) return ParseError(line, "Land runway record too short", "100").left()
    val width = parts[1].toDoubleOrNull()
        ?: return ParseError(line, "Invalid runway width", "100").left()
    val surfaceCode = parts[2].toIntOrNull() ?: 0
    val end1Pos = parseGeo(parts[9], parts[10], line).fold({ return it.left() }, { it })
    val end2Pos = parseGeo(parts[18], parts[19], line).fold({ return it.left() }, { it })
    return LandRunway(
        width = width,
        surface = SurfaceType.fromCode(surfaceCode) ?: SurfaceType.ASPHALT,
        shoulderSurface = parts[3].toIntOrNull() ?: 0,
        smoothness = parts[4].toDoubleOrNull() ?: 0.0,
        centreLights = parts[5].toIntOrNull() ?: 0,
        edgeLights = parts[6].toIntOrNull() ?: 0,
        distanceRemainingSigns = parts[7].toIntOrNull() ?: 0,
        end1 = RunwayEnd(
            designator = parts[8],
            position = end1Pos,
            displacedThresholdMeters = parts[11].toDoubleOrNull() ?: 0.0,
            overrunMeters = parts[12].toDoubleOrNull() ?: 0.0,
            markings = parts[13].toIntOrNull() ?: 0,
            approachLighting = parts[14].toIntOrNull() ?: 0,
            touchdownZoneLighting = parts[15].toIntOrNull() ?: 0,
            reilLighting = parts[16].toIntOrNull() ?: 0,
        ),
        end2 = RunwayEnd(
            designator = parts[17],
            position = end2Pos,
            displacedThresholdMeters = parts[20].toDoubleOrNull() ?: 0.0,
            overrunMeters = parts[21].toDoubleOrNull() ?: 0.0,
            markings = parts[22].toIntOrNull() ?: 0,
            approachLighting = parts[23].toIntOrNull() ?: 0,
            touchdownZoneLighting = parts[24].toIntOrNull() ?: 0,
            reilLighting = parts[25].toIntOrNull() ?: 0,
        ),
    ).right()
}

internal fun parseWaterRunway(parts: List<String>, line: Int): Either<ParseError, WaterRunway> {
    if (parts.size < 9) return ParseError(line, "Water runway record too short", "101").left()
    val end1Pos = parseGeo(parts[3], parts[4], line).fold({ return it.left() }, { it })
    val end2Pos = parseGeo(parts[6], parts[7], line).fold({ return it.left() }, { it })
    return WaterRunway(
        width = parts[1].toDoubleOrNull() ?: 0.0,
        end1Designator = parts[2],
        end1Position = end1Pos,
        end2Designator = parts[5],
        end2Position = end2Pos,
    ).right()
}

internal fun parseHelipad(parts: List<String>, line: Int): Either<ParseError, Helipad> {
    if (parts.size < 12) return ParseError(line, "Helipad record too short", "102").left()
    val pos = parseGeo(parts[2], parts[3], line).fold({ return it.left() }, { it })
    return Helipad(
        designator = parts[1],
        position = pos,
        heading = parts[4].toDoubleOrNull() ?: 0.0,
        length = parts[5].toDoubleOrNull() ?: 0.0,
        width = parts[6].toDoubleOrNull() ?: 0.0,
        surface = SurfaceType.fromCode(parts[7].toIntOrNull() ?: 0),
        markings = parts[8].toIntOrNull() ?: 0,
        shoulderSurface = parts[9].toIntOrNull() ?: 0,
        smoothness = parts[10].toDoubleOrNull() ?: 0.0,
        edgeLights = parts[11].toIntOrNull() ?: 0,
    ).right()
}

internal fun parseBezierNode(code: String, parts: List<String>, line: Int): Either<ParseError, BezierNode> {
    if (parts.size < 3) return ParseError(line, "Bezier node too short", code).left()
    val pos = parseGeo(parts[1], parts[2], line).fold({ return it.left() }, { it })
    return when (code) {
        "111" -> BezierNode.Plain(pos).right()
        "113" -> BezierNode.ClosePlain(pos).right()
        "115" -> BezierNode.EndPlain(pos).right()
        "112", "114", "116" -> {
            if (parts.size < 5) return ParseError(line, "Bezier curve node needs control point", code).left()
            val cp = parseGeo(parts[3], parts[4], line).fold({ return it.left() }, { it })
            when (code) {
                "112" -> BezierNode.Bezier(pos, cp).right()
                "114" -> BezierNode.CloseBezier(pos, cp).right()
                "116" -> BezierNode.EndBezier(pos, cp).right()
                else -> ParseError(line, "Unexpected bezier code: $code", code).left()
            }
        }
        else -> ParseError(line, "Unknown bezier node code: $code", code).left()
    }
}

internal fun parseTaxiNode(parts: List<String>, line: Int): Either<ParseError, TaxiNode> {
    if (parts.size < 5) return ParseError(line, "Taxi node record too short", "1201").left()
    val pos = parseGeo(parts[1], parts[2], line).fold({ return it.left() }, { it })
    val usage = TaxiNodeUsage.fromCode(parts[3])
        ?: return ParseError(line, "Unknown taxi node usage: ${parts[3]}", "1201").left()
    return TaxiNode(
        id = parts[4].toIntOrNull() ?: return ParseError(line, "Invalid node id: ${parts[4]}", "1201").left(),
        position = pos,
        usage = usage,
        name = parts.drop(5).joinToString(" "),
    ).right()
}

internal fun parseTaxiEdge(parts: List<String>, line: Int): Either<ParseError, TaxiEdge> {
    if (parts.size < 5) return ParseError(line, "Taxi edge record too short", "1202").left()
    val direction = EdgeDirection.fromCode(parts[3])
        ?: return ParseError(line, "Unknown edge direction: ${parts[3]}", "1202").left()
    val edgeType = EdgeType.fromCode(parts[4])
        ?: return ParseError(line, "Unknown edge type: ${parts[4]}", "1202").left()
    return TaxiEdge(
        node1Id = parts[1].toIntOrNull()
            ?: return ParseError(line, "Invalid node1 id", "1202").left(),
        node2Id = parts[2].toIntOrNull()
            ?: return ParseError(line, "Invalid node2 id", "1202").left(),
        direction = direction,
        type = edgeType,
        name = parts.drop(5).joinToString(" "),
        activeZones = emptyList(),
    ).right()
}

internal fun parseActiveZone(parts: List<String>, line: Int): Either<ParseError, ActiveZone> {
    if (parts.size < 3) return ParseError(line, "Active zone record too short", "1204").left()
    val type = ActiveZoneType.fromCode(parts[1])
        ?: return ParseError(line, "Unknown active zone type: ${parts[1]}", "1204").left()
    val designators = parts[2].split(",").map { it.trim() }.filter { it.isNotEmpty() }
    return ActiveZone(type, designators).right()
}

internal fun parseVehicleEdge(parts: List<String>, line: Int): Either<ParseError, VehicleEdge> {
    if (parts.size < 4) return ParseError(line, "Vehicle edge record too short", "1206").left()
    val direction = EdgeDirection.fromCode(parts[3])
        ?: return ParseError(line, "Unknown direction: ${parts[3]}", "1206").left()
    return VehicleEdge(
        node1Id = parts[1].toIntOrNull()
            ?: return ParseError(line, "Invalid node1 id", "1206").left(),
        node2Id = parts[2].toIntOrNull()
            ?: return ParseError(line, "Invalid node2 id", "1206").left(),
        direction = direction,
        name = parts.drop(4).joinToString(" "),
    ).right()
}

internal fun parseStand(parts: List<String>, line: Int): Either<ParseError, Stand> {
    if (parts.size < 6) return ParseError(line, "Stand record too short", "1300").left()
    val pos = parseGeo(parts[1], parts[2], line).fold({ return it.left() }, { it })
    val type = StandType.fromCode(parts[4])
        ?: return ParseError(line, "Unknown stand type: ${parts[4]}", "1300").left()
    return Stand(
        position = pos,
        heading = parts[3].toDoubleOrNull() ?: 0.0,
        type = type,
        aircraftTypes = parts[5],
        name = parts.drop(6).joinToString(" "),
    ).right()
}

internal fun parseStartupLocation(parts: List<String>, line: Int): Either<ParseError, StartupLocation> {
    if (parts.size < 3) return ParseError(line, "Startup location too short", "1301").left()
    return StartupLocation(
        name = parts[1],
        operationType = parts[2],
    ).right()
}

internal fun parseTaxiSign(parts: List<String>, line: Int): Either<ParseError, TaxiSign> {
    if (parts.size < 6) return ParseError(line, "Taxi sign record too short", "20").left()
    val pos = parseGeo(parts[1], parts[2], line).fold({ return it.left() }, { it })
    return TaxiSign(
        position = pos,
        heading = parts[3].toDoubleOrNull() ?: 0.0,
        reserved = parts[4].toIntOrNull() ?: 0,
        size = parts[5].toIntOrNull() ?: 0,
        text = parts.drop(6).joinToString(" "),
    ).right()
}

internal fun parseLightingObject(parts: List<String>, line: Int): Either<ParseError, LightingObject> {
    if (parts.size < 6) return ParseError(line, "Lighting object record too short", "21").left()
    val pos = parseGeo(parts[1], parts[2], line).fold({ return it.left() }, { it })
    return LightingObject(
        position = pos,
        lightType = parts[3].toIntOrNull() ?: 0,
        heading = parts[4].toDoubleOrNull() ?: 0.0,
        angle = parts[5].toDoubleOrNull() ?: 0.0,
        associatedRunway = parts.getOrElse(6) { "" },
        description = parts.drop(7).joinToString(" "),
    ).right()
}

internal fun parseTowerViewpoint(parts: List<String>, line: Int): Either<ParseError, TowerViewpoint> {
    if (parts.size < 5) return ParseError(line, "Tower viewpoint record too short", "14").left()
    val pos = parseGeo(parts[1], parts[2], line).fold({ return it.left() }, { it })
    return TowerViewpoint(
        position = pos,
        heightFeetAgl = parts[3].toDoubleOrNull() ?: 0.0,
        drawBuilding = parts[4].toIntOrNull() ?: 0,
        name = parts.drop(5).joinToString(" "),
    ).right()
}

internal fun parseAtcFrequency(
    recordCode: String,
    parts: List<String>,
    line: Int,
): Either<ParseError, AtcFrequency> {
    if (parts.size < 3) return ParseError(line, "Frequency record too short", recordCode).left()
    val type = AtcFrequencyType.fromRecordCode(recordCode)
        ?: return ParseError(line, "Unknown frequency record code: $recordCode", recordCode).left()
    return AtcFrequency(
        type = type,
        frequencyKhz = parts[1].toIntOrNull()
            ?: return ParseError(line, "Invalid frequency: ${parts[1]}", recordCode).left(),
        name = parts.drop(2).joinToString(" "),
    ).right()
}

internal fun parseWindRule(parts: List<String>, line: Int): Either<ParseError, WindRule> {
    if (parts.size < 5) return ParseError(line, "Wind rule too short", "1001").left()
    return WindRule(
        icao = parts[1],
        minHeading = parts[2].toIntOrNull()
            ?: return ParseError(line, "Invalid min heading", "1001").left(),
        maxHeading = parts[3].toIntOrNull()
            ?: return ParseError(line, "Invalid max heading", "1001").left(),
        maxSpeedKnots = parts[4].toIntOrNull()
            ?: return ParseError(line, "Invalid max speed", "1001").left(),
    ).right()
}

internal fun parseCeilingRule(parts: List<String>, line: Int): Either<ParseError, CeilingRule> {
    if (parts.size < 3) return ParseError(line, "Ceiling rule too short", "1002").left()
    return CeilingRule(
        icao = parts[1],
        ceilingFeetAgl = parts[2].toIntOrNull()
            ?: return ParseError(line, "Invalid ceiling", "1002").left(),
    ).right()
}

internal fun parseVisibilityRule(parts: List<String>, line: Int): Either<ParseError, VisibilityRule> {
    if (parts.size < 3) return ParseError(line, "Visibility rule too short", "1003").left()
    return VisibilityRule(
        icao = parts[1],
        visibilityStatuteMiles = parts[2].toDoubleOrNull()
            ?: return ParseError(line, "Invalid visibility", "1003").left(),
    ).right()
}

internal fun parsePatternRunway(parts: List<String>, line: Int): Either<ParseError, PatternRunway> {
    if (parts.size < 3) return ParseError(line, "Pattern runway too short", "1101").left()
    return PatternRunway(
        runwayDesignator = parts[1],
        direction = parts[2],
    ).right()
}

internal fun parseRunwayAssignment(parts: List<String>, line: Int): Either<ParseError, RunwayAssignment> {
    if (parts.size < 8) return ParseError(line, "Runway assignment too short", "1110").left()
    return RunwayAssignment(
        runwayDesignator = parts[1],
        frequencyKhz = parts[2].toIntOrNull()
            ?: return ParseError(line, "Invalid frequency", "1110").left(),
        operations = parts[3],
        aircraftTypes = parts[4],
        name = parts.drop(7).joinToString(" "),
    ).right()
}

internal fun parseServiceVehicleLocation(
    parts: List<String>,
    line: Int,
): Either<ParseError, ServiceVehicleLocation> {
    if (parts.size < 6) return ParseError(line, "Service vehicle location too short", "1400").left()
    val pos = parseGeo(parts[1], parts[2], line).fold({ return it.left() }, { it })
    return ServiceVehicleLocation(
        position = pos,
        heading = parts[3].toDoubleOrNull() ?: 0.0,
        vehicleType = parts[4],
        reserved = parts[5].toIntOrNull() ?: 0,
        name = parts.drop(6).joinToString(" "),
    ).right()
}

internal fun parseServiceVehicleDestination(
    parts: List<String>,
    line: Int,
): Either<ParseError, ServiceVehicleDestination> {
    if (parts.size < 5) return ParseError(line, "Service vehicle destination too short", "1401").left()
    val pos = parseGeo(parts[1], parts[2], line).fold({ return it.left() }, { it })
    return ServiceVehicleDestination(
        position = pos,
        heading = parts[3].toDoubleOrNull() ?: 0.0,
        vehicleType = parts[4],
        name = parts.drop(5).joinToString(" "),
    ).right()
}
