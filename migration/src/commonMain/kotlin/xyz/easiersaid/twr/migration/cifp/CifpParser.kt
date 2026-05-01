package xyz.easiersaid.twr.migration.cifp

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import arrow.core.toNonEmptyListOrNull
import xyz.easiersaid.twr.migration.common.ParseError
import xyz.easiersaid.twr.migration.common.ParseResult

/**
 * Parse an X-Plane CIFP file for a single airport.
 * Pure, total: no IO, no exceptions.
 */
fun parseCifp(content: String): Either<NonEmptyList<ParseError>, ParseResult<CifpAirport>> {
    val lines = content.lines()
    val sids = mutableListOf<CifpLeg>()
    val stars = mutableListOf<CifpLeg>()
    val approaches = mutableListOf<CifpLeg>()
    val runways = mutableListOf<CifpRunway>()
    val precisionData = mutableListOf<CifpPrecisionData>()
    val warnings = mutableListOf<ParseError>()
    val errors = mutableListOf<ParseError>()

    lines.forEachIndexed { index, line ->
        processLine(
            line.trim(), index + 1, sids, stars, approaches, runways, precisionData, warnings,
        )
    }

    if (errors.isNotEmpty()) {
        return (errors.toNonEmptyListOrNull() ?: nonEmptyListOf(errors.first())).left()
    }

    val airport = CifpAirport(
        sids = groupLegsIntoProcedures(SectionType.SID, sids),
        stars = groupLegsIntoProcedures(SectionType.STAR, stars),
        approaches = groupLegsIntoProcedures(SectionType.APPROACH, approaches),
        runways = runways.toList(),
        precisionApproachData = precisionData.toList(),
    )
    return ParseResult(airport, warnings.toList()).right()
}

private fun groupLegsIntoProcedures(
    sectionType: SectionType,
    legs: List<CifpLeg>,
): List<CifpProcedure> =
    legs.groupBy { Triple(it.routeType, it.procedureName, it.transition) }
        .map { (key, groupLegs) ->
            CifpProcedure(
                sectionType = sectionType,
                routeType = key.first,
                name = key.second,
                legs = groupLegs.sortedBy { it.sequenceNumber },
            )
        }

@Suppress("LongParameterList")
private fun processLine(
    trimmed: String,
    lineNumber: Int,
    sids: MutableList<CifpLeg>,
    stars: MutableList<CifpLeg>,
    approaches: MutableList<CifpLeg>,
    runways: MutableList<CifpRunway>,
    precisionData: MutableList<CifpPrecisionData>,
    warnings: MutableList<ParseError>,
) {
    if (trimmed.isEmpty()) return
    val colonIdx = trimmed.indexOf(':')
    if (colonIdx < 0) {
        warnings.add(ParseError(lineNumber, "No section prefix found", null))
        return
    }
    val prefix = trimmed.substring(0, colonIdx)
    val rest = trimmed.substring(colonIdx + 1).trimEnd(';')
    when (prefix) {
        "SID" -> parseProcedureLeg(rest, lineNumber).fold({ warnings.add(it) }, { sids.add(it) })
        "STAR" -> parseProcedureLeg(rest, lineNumber).fold({ warnings.add(it) }, { stars.add(it) })
        "APPCH" -> parseProcedureLeg(rest, lineNumber).fold({ warnings.add(it) }, { approaches.add(it) })
        "RWY" -> parseRunway(rest, lineNumber).fold({ warnings.add(it) }, { runways.add(it) })
        "PRDAT" -> parsePrecisionData(rest, lineNumber).fold({ warnings.add(it) }, { precisionData.add(it) })
        else -> warnings.add(ParseError(lineNumber, "Unknown section: $prefix", prefix))
    }
}

private fun parseProcedureLeg(fields: String, lineNumber: Int): Either<ParseError, CifpLeg> {
    // Field indices after splitting on comma (0-based, after stripping "SECTION:" prefix):
    // 0=seq 1=routeType 2=procName 3=transition
    // 4=fixId 5=fixRegion 6=fixSection 7=fixSubsection
    // 8=waypointDesc 9=turnDir 10=rnp 11=pathTerminator
    // 12=overfly? 13=recNavId 14=recNavRegion 15=recNavSection 16=recNavSubsection
    // 17=arcRadius 18=theta 19=rho 20=outboundCourse 21=routeDistance
    // 22=altConstraintType 23=alt1 24=alt2 25=transitionAlt
    // 26=speedConstraintType 27=speedLimit 28=verticalAngle 29=???
    // 30=centerFixId 31=centerFixRegion 32=centerFixSection 33=centerFixSubsection
    val parts = fields.split(",")
    if (parts.size < 30) {
        return ParseError(lineNumber, "Procedure leg too short: ${parts.size} fields").left()
    }

    val pathTermCode = parts[11].trim()
    val pathTerm = PathTerminator.fromCode(pathTermCode)
        ?: return ParseError(lineNumber, "Unknown path terminator: '$pathTermCode'").left()

    val fix = buildFix(parts[4], parts[5], parts[6], parts[7])
    val navaid = buildFix(parts[13], parts[14], parts[15], parts[16])
    val centerFix = if (parts.size > 33) buildFix(parts[30], parts[31], parts[32], parts[33]) else null

    return CifpLeg(
        sequenceNumber = parts[0].trim().toIntOrNull() ?: 0,
        routeType = parts[1].trim(),
        procedureName = parts[2].trim(),
        transition = parts[3].trim(),
        fix = fix,
        waypointDescription = parts[8].trim(),
        turnDirection = TurnDirection.fromCode(parts[9]),
        rnp = parts[10].trim(),
        pathTerminator = pathTerm,
        recommendedNavaid = navaid,
        arcRadius = parts[17].trim(),
        theta = parts[18].trim(),
        rho = parts[19].trim(),
        outboundMagneticCourse = parts[20].trim(),
        routeDistance = parts[21].trim(),
        altitudeConstraint = parseAltitudeConstraint(parts, 22),
        speedLimit = parts[27].trim(),
        verticalAngle = parts[28].trim(),
        centerFix = centerFix,
        gnssFmsIndicator = if (parts.size > 36) parts[36].trim() else "",
        speedConstraintType = parts[26].trim(),
    ).right()
}

private fun buildFix(id: String, region: String, section: String, subsection: String): CifpFix? {
    val trimId = id.trim()
    return if (trimId.isEmpty()) null
    else CifpFix(trimId, region.trim(), section.trim(), subsection.trim())
}

private fun parseAltitudeConstraint(parts: List<String>, offset: Int): AltitudeConstraint? {
    val typeCode = parts[offset].trim()
    val alt1Str = parts[offset + 1].trim()
    val alt2Str = parts[offset + 2].trim()
    val transStr = parts[offset + 3].trim()

    val alt1 = alt1Str.toIntOrNull()
    val alt2 = alt2Str.toIntOrNull()
    val trans = transStr.toIntOrNull()

    val hasNoValues = listOf(alt1, alt2, trans).all { it == null }
    if (hasNoValues && typeCode.isEmpty()) return null

    val constraintType = AltitudeConstraintType.fromCode(typeCode)
        ?: AltitudeConstraintType.AT

    return AltitudeConstraint(constraintType, alt1, alt2, trans)
}

private fun parseRunway(fields: String, lineNumber: Int): Either<ParseError, CifpRunway> {
    // RWY format: designator,ilsId,ilsCategory,elevation,field5,ilsNavaid,ilsCategory2,rnp;thresholdCoords;
    val semicolonParts = fields.split(";")
    val mainParts = semicolonParts[0].split(",")
    if (mainParts.isEmpty()) return ParseError(lineNumber, "Empty RWY record").left()

    val thresholdParts = if (semicolonParts.size > 1) semicolonParts[1] else ""
    val latLon = parseThresholdCoords(thresholdParts)

    return CifpRunway(
        designator = mainParts[0].trim(),
        thresholdLatitude = latLon?.first,
        thresholdLongitude = latLon?.second,
        elevation = mainParts.getOrNull(3)?.trim()?.toIntOrNull(),
        landingThresholdElevation = if (semicolonParts.size > 2) {
            semicolonParts[2].trim().toIntOrNull()
        } else null,
        ilsIdentifier = mainParts.getOrNull(5)?.trim()?.takeIf { it.isNotEmpty() },
        ilsCategory = mainParts.getOrNull(6)?.trim()?.toIntOrNull(),
    ).right()
}

private fun parseThresholdCoords(coordStr: String): Pair<String, String>? {
    val trimmed = coordStr.trim()
    if (trimmed.isEmpty()) return null
    // Format: N47000722,E015261181
    val parts = trimmed.split(",")
    return if (parts.size >= 2) Pair(parts[0].trim(), parts[1].trim()) else null
}

private fun parsePrecisionData(
    fields: String,
    @Suppress("UNUSED_PARAMETER") lineNumber: Int,
): Either<ParseError, CifpPrecisionData> {
    val parts = fields.split(",")
    val minimums = mutableListOf<CifpMinimum>()
    var i = 0
    while (i + 1 < parts.size) {
        val type = parts[i].trim()
        val label = parts[i + 1].trim()
        if (type.isNotEmpty() && label.isNotEmpty()) {
            minimums.add(CifpMinimum(type, label))
        }
        i += 2
    }
    return CifpPrecisionData(minimums).right()
}
