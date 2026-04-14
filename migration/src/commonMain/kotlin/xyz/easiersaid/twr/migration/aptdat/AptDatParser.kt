package xyz.easiersaid.twr.migration.aptdat

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import arrow.core.toNonEmptyListOrNull
import xyz.easiersaid.twr.migration.common.ParseError
import xyz.easiersaid.twr.migration.common.ParseResult

/**
 * Parse an apt.dat file that may contain multiple airports.
 * Pure, total: no IO, no exceptions.
 */
fun parseAptDat(
    content: String,
): Either<NonEmptyList<ParseError>, ParseResult<List<AptDatAirport>>> {
    val finalState = content.lineSequence()
        .foldIndexed(ParserState.initial()) { index, state, line ->
            processLine(state, index + 1, line)
        }
        .finalizeCurrentAirport()
        .finalizeCurrentFlow()

    val errors = finalState.errors
    return if (errors.isNotEmpty()) {
        (errors.toNonEmptyListOrNull() ?: nonEmptyListOf(errors.first())).left()
    } else {
        ParseResult(finalState.completedAirports, finalState.warnings).right()
    }
}

private enum class PolygonGroupType { PAVEMENT, LINEAR_FEATURE, BOUNDARY }

private data class PolygonHeader(
    val type: PolygonGroupType,
    val surface: SurfaceType?,
    val smoothness: Double,
    val heading: Double,
    val name: String,
)

private data class FlowAccumulator(
    val name: String,
    val windRule: WindRule? = null,
    val ceilingRule: CeilingRule? = null,
    val visibilityRule: VisibilityRule? = null,
    val patternRunway: PatternRunway? = null,
    val runwayAssignments: List<RunwayAssignment> = emptyList(),
) {
    fun toAtcFlow(): AtcFlow = AtcFlow(
        name = name,
        windRule = windRule,
        ceilingRule = ceilingRule,
        visibilityRule = visibilityRule,
        patternRunway = patternRunway,
        runwayAssignments = runwayAssignments,
    )
}

private data class AirportAccumulator(
    val header: AirportHeader,
    val metadata: Map<String, String> = emptyMap(),
    val landRunways: List<LandRunway> = emptyList(),
    val waterRunways: List<WaterRunway> = emptyList(),
    val helipads: List<Helipad> = emptyList(),
    val pavements: List<Pavement> = emptyList(),
    val linearFeatures: List<LinearFeature> = emptyList(),
    val boundary: Boundary? = null,
    val taxiNodes: List<TaxiNode> = emptyList(),
    val taxiEdges: List<TaxiEdge> = emptyList(),
    val vehicleEdges: List<VehicleEdge> = emptyList(),
    val stands: List<Stand> = emptyList(),
    val startupLocations: List<StartupLocation> = emptyList(),
    val taxiSigns: List<TaxiSign> = emptyList(),
    val lightingObjects: List<LightingObject> = emptyList(),
    val towerViewpoint: TowerViewpoint? = null,
    val atcFlows: List<AtcFlow> = emptyList(),
    val frequencies: List<AtcFrequency> = emptyList(),
    val serviceVehicleLocations: List<ServiceVehicleLocation> = emptyList(),
    val serviceVehicleDestinations: List<ServiceVehicleDestination> = emptyList(),
) {
    fun toAirport(): AptDatAirport = AptDatAirport(
        icao = header.icao,
        name = header.name,
        elevationFeet = header.elevationFeet,
        hasTower = header.hasTower,
        metadata = metadata,
        landRunways = landRunways,
        waterRunways = waterRunways,
        helipads = helipads,
        pavements = pavements,
        linearFeatures = linearFeatures,
        boundary = boundary,
        taxiNetwork = TaxiNetwork(taxiNodes, taxiEdges, vehicleEdges),
        stands = stands,
        startupLocations = startupLocations,
        taxiSigns = taxiSigns,
        lightingObjects = lightingObjects,
        towerViewpoint = towerViewpoint,
        atcFlows = atcFlows,
        frequencies = frequencies,
        serviceVehicleLocations = serviceVehicleLocations,
        serviceVehicleDestinations = serviceVehicleDestinations,
    )
}

private data class ParserState(
    val completedAirports: List<AptDatAirport>,
    val currentAirport: AirportAccumulator?,
    val currentPolygonHeader: PolygonHeader?,
    val currentPolygonNodes: List<BezierNode>,
    val currentFlow: FlowAccumulator?,
    val warnings: List<ParseError>,
    val errors: List<ParseError>,
) {
    companion object {
        fun initial(): ParserState = ParserState(
            completedAirports = emptyList(),
            currentAirport = null,
            currentPolygonHeader = null,
            currentPolygonNodes = emptyList(),
            currentFlow = null,
            warnings = emptyList(),
            errors = emptyList(),
        )
    }

    fun finalizeCurrentPolygon(): ParserState {
        val header = currentPolygonHeader ?: return this
        val nodes = currentPolygonNodes
        val airport = currentAirport ?: return this
        val updated = when (header.type) {
            PolygonGroupType.PAVEMENT -> airport.copy(
                pavements = airport.pavements + Pavement(
                    header.surface, header.smoothness, header.heading, header.name, nodes,
                ),
            )
            PolygonGroupType.LINEAR_FEATURE -> airport.copy(
                linearFeatures = airport.linearFeatures + LinearFeature(header.name, nodes),
            )
            PolygonGroupType.BOUNDARY -> airport.copy(
                boundary = Boundary(header.name, nodes),
            )
        }
        return copy(
            currentAirport = updated,
            currentPolygonHeader = null,
            currentPolygonNodes = emptyList(),
        )
    }

    fun finalizeCurrentFlow(): ParserState {
        val flow = currentFlow ?: return this
        val airport = currentAirport ?: return this
        return copy(
            currentAirport = airport.copy(atcFlows = airport.atcFlows + flow.toAtcFlow()),
            currentFlow = null,
        )
    }

    fun finalizeCurrentAirport(): ParserState {
        val airport = currentAirport ?: return this
        val finalized = finalizeCurrentPolygon().finalizeCurrentFlow()
        val updatedAirport = finalized.currentAirport ?: airport
        return finalized.copy(
            completedAirports = finalized.completedAirports + updatedAirport.toAirport(),
            currentAirport = null,
        )
    }
}

private val FREQUENCY_CODES = setOf("1050", "1051", "1052", "1053", "1054", "1055", "1056")
private val BEZIER_CODES = setOf("111", "112", "113", "114", "115", "116")

private fun processLine(state: ParserState, lineNumber: Int, line: String): ParserState {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || trimmed == "I") return state
    val parts = trimmed.split(Regex("\\s+"))
    if (parts.isEmpty()) return state
    val code = parts[0]

    if (code in SKIP_CODES) return state
    if (code in BEZIER_CODES) return processBezierNode(state, code, parts, lineNumber)

    val s = state.finalizeCurrentPolygon()
    return dispatchRecord(s, code, parts, lineNumber)
}

private val SKIP_CODES = setOf("1200", "1130")

private fun dispatchRecord(
    s: ParserState,
    code: String,
    parts: List<String>,
    lineNumber: Int,
): ParserState = dispatchStructural(s, code, parts, lineNumber)
    ?: dispatchTaxiNetwork(s, code, parts, lineNumber)
    ?: dispatchInfrastructure(s, code, parts, lineNumber)
    ?: dispatchAtcFlow(s, code, parts, lineNumber)
    ?: s.copy(warnings = s.warnings + ParseError(lineNumber, "Unknown record code: $code", code))

private fun dispatchStructural(
    s: ParserState,
    code: String,
    parts: List<String>,
    lineNumber: Int,
): ParserState? = when (code) {
    "1" -> processAirportHeader(s, parts, lineNumber)
    "1302" -> processMetadata(s, parts)
    "110" -> startPavementPolygon(s, parts)
    "120" -> startLinearFeature(s, parts)
    "130" -> startBoundary(s, parts)
    "100" -> processRecord(s, lineNumber, code) { parseLandRunway(parts, lineNumber) }
    "101" -> processRecord(s, lineNumber, code) { parseWaterRunway(parts, lineNumber) }
    "102" -> processRecord(s, lineNumber, code) { parseHelipad(parts, lineNumber) }
    else -> null
}

private fun dispatchTaxiNetwork(
    s: ParserState,
    code: String,
    parts: List<String>,
    lineNumber: Int,
): ParserState? = when (code) {
    "1201" -> processRecord(s, lineNumber, code) { parseTaxiNode(parts, lineNumber) }
    "1202" -> processTaxiEdge(s, parts, lineNumber)
    "1204" -> processActiveZone(s, parts, lineNumber)
    "1206" -> processRecord(s, lineNumber, code) { parseVehicleEdge(parts, lineNumber) }
    "1300" -> processRecord(s, lineNumber, code) { parseStand(parts, lineNumber) }
    "1301" -> processRecord(s, lineNumber, code) { parseStartupLocation(parts, lineNumber) }
    else -> null
}

private fun dispatchInfrastructure(
    s: ParserState,
    code: String,
    parts: List<String>,
    lineNumber: Int,
): ParserState? = when (code) {
    "20" -> processRecord(s, lineNumber, code) { parseTaxiSign(parts, lineNumber) }
    "21" -> processRecord(s, lineNumber, code) { parseLightingObject(parts, lineNumber) }
    "14" -> processRecord(s, lineNumber, code) { parseTowerViewpoint(parts, lineNumber) }
    "1400" -> processRecord(s, lineNumber, code) { parseServiceVehicleLocation(parts, lineNumber) }
    "1401" -> processRecord(s, lineNumber, code) { parseServiceVehicleDestination(parts, lineNumber) }
    in FREQUENCY_CODES -> processRecord(s, lineNumber, code) { parseAtcFrequency(code, parts, lineNumber) }
    else -> null
}

private fun dispatchAtcFlow(
    s: ParserState,
    code: String,
    parts: List<String>,
    lineNumber: Int,
): ParserState? = when (code) {
    "1000" -> startAtcFlow(s, parts)
    "1001" -> processFlowRule(s, lineNumber) { parseWindRule(parts, lineNumber) }
    "1002" -> processFlowRule(s, lineNumber) { parseCeilingRule(parts, lineNumber) }
    "1003" -> processFlowRule(s, lineNumber) { parseVisibilityRule(parts, lineNumber) }
    "1101" -> processFlowRule(s, lineNumber) { parsePatternRunway(parts, lineNumber) }
    "1110" -> processFlowRule(s, lineNumber) { parseRunwayAssignment(parts, lineNumber) }
    else -> null
}

private fun processBezierNode(
    state: ParserState,
    code: String,
    parts: List<String>,
    lineNumber: Int,
): ParserState = parseBezierNode(code, parts, lineNumber).fold(
    { err -> state.copy(warnings = state.warnings + err) },
    { node -> state.copy(currentPolygonNodes = state.currentPolygonNodes + node) },
)

private fun processAirportHeader(
    state: ParserState,
    parts: List<String>,
    lineNumber: Int,
): ParserState {
    val finalized = state.finalizeCurrentAirport()
    return parseAirportHeader(parts, lineNumber).fold(
        { err -> finalized.copy(errors = finalized.errors + err) },
        { header -> finalized.copy(currentAirport = AirportAccumulator(header)) },
    )
}

private fun processMetadata(state: ParserState, parts: List<String>): ParserState {
    val airport = state.currentAirport ?: return state
    if (parts.size < 3) return state
    val key = parts[1]
    val value = parts.drop(2).joinToString(" ")
    return state.copy(
        currentAirport = airport.copy(metadata = airport.metadata + (key to value)),
    )
}

@Suppress("UNCHECKED_CAST")
private fun <T> processRecord(
    state: ParserState,
    lineNumber: Int,
    code: String,
    parse: () -> Either<ParseError, T>,
): ParserState {
    val airport = state.currentAirport
        ?: return state.copy(
            warnings = state.warnings + ParseError(lineNumber, "Record $code outside airport", code),
        )
    return parse().fold(
        { err -> state.copy(warnings = state.warnings + err) },
        { value -> state.copy(currentAirport = addToAirport(airport, value, code)) },
    )
}

private fun <T> addToAirport(airport: AirportAccumulator, value: T, code: String): AirportAccumulator =
    when (code) {
        "100" -> airport.copy(landRunways = airport.landRunways + (value as LandRunway))
        "101" -> airport.copy(waterRunways = airport.waterRunways + (value as WaterRunway))
        "102" -> airport.copy(helipads = airport.helipads + (value as Helipad))
        "1201" -> airport.copy(taxiNodes = airport.taxiNodes + (value as TaxiNode))
        "1206" -> airport.copy(vehicleEdges = airport.vehicleEdges + (value as VehicleEdge))
        "1300" -> airport.copy(stands = airport.stands + (value as Stand))
        "1301" -> airport.copy(startupLocations = airport.startupLocations + (value as StartupLocation))
        "20" -> airport.copy(taxiSigns = airport.taxiSigns + (value as TaxiSign))
        "21" -> airport.copy(lightingObjects = airport.lightingObjects + (value as LightingObject))
        "14" -> airport.copy(towerViewpoint = value as TowerViewpoint)
        "1400" -> airport.copy(
            serviceVehicleLocations = airport.serviceVehicleLocations + (value as ServiceVehicleLocation),
        )
        "1401" -> airport.copy(
            serviceVehicleDestinations = airport.serviceVehicleDestinations + (value as ServiceVehicleDestination),
        )
        else -> {
            if (code in FREQUENCY_CODES) {
                airport.copy(frequencies = airport.frequencies + (value as AtcFrequency))
            } else {
                airport
            }
        }
    }

private fun processTaxiEdge(state: ParserState, parts: List<String>, lineNumber: Int): ParserState {
    val airport = state.currentAirport ?: return state
    return parseTaxiEdge(parts, lineNumber).fold(
        { err -> state.copy(warnings = state.warnings + err) },
        { edge ->
            state.copy(
                currentAirport = airport.copy(taxiEdges = airport.taxiEdges + edge),
            )
        },
    )
}

private fun processActiveZone(state: ParserState, parts: List<String>, lineNumber: Int): ParserState {
    val airport = state.currentAirport ?: return state
    if (airport.taxiEdges.isEmpty()) {
        return state.copy(
            warnings = state.warnings + ParseError(lineNumber, "Active zone without preceding edge", "1204"),
        )
    }
    return parseActiveZone(parts, lineNumber).fold(
        { err -> state.copy(warnings = state.warnings + err) },
        { zone ->
            val lastEdge = airport.taxiEdges.last()
            val updatedEdge = lastEdge.copy(activeZones = lastEdge.activeZones + zone)
            val updatedEdges = airport.taxiEdges.dropLast(1) + updatedEdge
            state.copy(currentAirport = airport.copy(taxiEdges = updatedEdges))
        },
    )
}

private fun startPavementPolygon(state: ParserState, parts: List<String>): ParserState {
    val surfaceCode = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return state.copy(
        currentPolygonHeader = PolygonHeader(
            type = PolygonGroupType.PAVEMENT,
            surface = SurfaceType.fromCode(surfaceCode),
            smoothness = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0,
            heading = parts.getOrNull(3)?.toDoubleOrNull() ?: 0.0,
            name = parts.drop(4).joinToString(" "),
        ),
        currentPolygonNodes = emptyList(),
    )
}

private fun startLinearFeature(state: ParserState, parts: List<String>): ParserState =
    state.copy(
        currentPolygonHeader = PolygonHeader(
            type = PolygonGroupType.LINEAR_FEATURE,
            surface = null,
            smoothness = 0.0,
            heading = 0.0,
            name = parts.drop(1).joinToString(" "),
        ),
        currentPolygonNodes = emptyList(),
    )

private fun startBoundary(state: ParserState, parts: List<String>): ParserState =
    state.copy(
        currentPolygonHeader = PolygonHeader(
            type = PolygonGroupType.BOUNDARY,
            surface = null,
            smoothness = 0.0,
            heading = 0.0,
            name = parts.drop(1).joinToString(" "),
        ),
        currentPolygonNodes = emptyList(),
    )

private fun startAtcFlow(state: ParserState, parts: List<String>): ParserState {
    val finalized = state.finalizeCurrentFlow()
    val name = parts.drop(1).joinToString(" ")
    return finalized.copy(currentFlow = FlowAccumulator(name))
}

@Suppress("UNCHECKED_CAST")
private fun <T> processFlowRule(
    state: ParserState,
    lineNumber: Int,
    parse: () -> Either<ParseError, T>,
): ParserState {
    val flow = state.currentFlow
        ?: return state.copy(
            warnings = state.warnings + ParseError(lineNumber, "Flow rule outside flow block"),
        )
    return parse().fold(
        { err -> state.copy(warnings = state.warnings + err) },
        { value ->
            val updated = when (value) {
                is WindRule -> flow.copy(windRule = value)
                is CeilingRule -> flow.copy(ceilingRule = value)
                is VisibilityRule -> flow.copy(visibilityRule = value)
                is PatternRunway -> flow.copy(patternRunway = value)
                is RunwayAssignment -> flow.copy(runwayAssignments = flow.runwayAssignments + value)
                else -> flow
            }
            state.copy(currentFlow = updated)
        },
    )
}
