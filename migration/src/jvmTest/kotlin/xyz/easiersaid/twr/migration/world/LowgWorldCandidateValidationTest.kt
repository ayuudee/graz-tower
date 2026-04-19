package xyz.easiersaid.twr.migration.world

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AirspaceClass
import xyz.easiersaid.twr.core.world.AirspaceBoundary
import xyz.easiersaid.twr.core.world.AirspaceVolume
import xyz.easiersaid.twr.core.world.AirspaceVolumeType
import xyz.easiersaid.twr.core.world.AltitudeBand
import xyz.easiersaid.twr.core.world.AltitudeBoundary
import xyz.easiersaid.twr.core.world.Apron
import xyz.easiersaid.twr.core.world.AerodromeAip
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.BoundaryRing
import xyz.easiersaid.twr.core.world.CircuitJoin
import xyz.easiersaid.twr.core.world.CircuitLeg
import xyz.easiersaid.twr.core.world.CircuitProcedure
import xyz.easiersaid.twr.core.world.ContactRequirement
import xyz.easiersaid.twr.core.world.ContactTiming
import xyz.easiersaid.twr.core.world.DeclaredDistances
import xyz.easiersaid.twr.core.world.Degrees
import xyz.easiersaid.twr.core.world.Feet
import xyz.easiersaid.twr.core.world.Fix
import xyz.easiersaid.twr.core.world.FixType
import xyz.easiersaid.twr.core.world.FlightInformationRegion
import xyz.easiersaid.twr.core.world.GeometrySegmentId
import xyz.easiersaid.twr.core.world.HoldingPoint
import xyz.easiersaid.twr.core.world.HoldingPointType
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.Meters
import xyz.easiersaid.twr.core.world.OperationalSector
import xyz.easiersaid.twr.core.world.OperationalSectorAnchor
import xyz.easiersaid.twr.core.world.OperationalSectorCtrRelation
import xyz.easiersaid.twr.core.world.OperationalSectorKind
import xyz.easiersaid.twr.core.world.Path as WorldPath
import xyz.easiersaid.twr.core.world.PhysicalGeometry
import xyz.easiersaid.twr.core.world.PlateId
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.PublishedMapLabel
import xyz.easiersaid.twr.core.world.PublishedPointReference
import xyz.easiersaid.twr.core.world.PublishedProcedureAdvisories
import xyz.easiersaid.twr.core.world.PublishedProcedureCommunicationFailure
import xyz.easiersaid.twr.core.world.PublishedVfrProcedure
import xyz.easiersaid.twr.core.world.PublishedVfrProcedureKind
import xyz.easiersaid.twr.core.world.Runway
import xyz.easiersaid.twr.core.world.SegmentGeometry
import xyz.easiersaid.twr.core.world.Stand
import xyz.easiersaid.twr.core.world.SurfaceType
import xyz.easiersaid.twr.core.world.Taxiway
import xyz.easiersaid.twr.core.world.VfrRoute
import xyz.easiersaid.twr.core.world.VfrRouteAirspaceProfile
import xyz.easiersaid.twr.core.world.VfrRouteAirspaceSegment
import xyz.easiersaid.twr.core.world.Waypoint
import xyz.easiersaid.twr.core.world.WorldValidationCode
import xyz.easiersaid.twr.core.world.asBoundaryRing
import xyz.easiersaid.twr.core.world.validate
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.ApronId
import xyz.easiersaid.twr.protocol.AirspaceVolumeId
import xyz.easiersaid.twr.protocol.CircuitDirection
import xyz.easiersaid.twr.protocol.CircuitProcedureId
import xyz.easiersaid.twr.protocol.DmeDistanceNm
import xyz.easiersaid.twr.protocol.FirId
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.OperationalSectorId
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.PublishedVfrProcedureId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.StandId
import xyz.easiersaid.twr.protocol.TaxiwayId
import xyz.easiersaid.twr.protocol.JoinType
import xyz.easiersaid.twr.protocol.VfrRouteId

class LowgWorldCandidateValidationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    @Test
    fun writesLowgCurrentCoreValidationReport() {
        val projectRoot = resolveProjectRoot()
        val candidatePath = projectRoot.resolve("cad/airports/rendered/lowg/world-candidate.json")
        assertTrue(Files.exists(candidatePath), "Missing LOWG world candidate at $candidatePath")

        val document = json.decodeFromString<WorldCandidateDocument>(candidatePath.readText())
        val world = document.toWorld()
        val report = world.validate()
        val structuralIssues = report.issues.filter { it.code in structuralCodes }

        val reportDocument = ValidationReportDocument(
            airportCode = document.airportCode,
            airportName = document.airportName,
            projectionStatus = document.projectionStatus,
            sourceManifest = document.sourceManifest,
            sourceEntityBundle = document.sourceEntityBundle,
            forcedAssumptions = document.forcedAssumptions,
            omittedFeatures = document.omittedFeatures,
            projectionGaps = document.projectionGaps,
            issueCount = report.issues.size,
            issueCountsByCode = report.issues.groupingBy { issue -> issue.code.name }.eachCount().toSortedMap(),
            structuralIssueCount = structuralIssues.size,
            structuralIssueCodes = structuralIssues.map { issue -> issue.code.name }.distinct().sorted(),
            issues = report.issues.map { issue ->
                ValidationIssueRecord(
                    code = issue.code.name,
                    message = issue.message,
                )
            },
        )

        val outputPath = projectRoot.resolve("cad/airports/rendered/lowg/world-validation-report.json")
        Files.createDirectories(outputPath.parent)
        outputPath.writeText(json.encodeToString(reportDocument))

        val summary = buildString {
            appendLine("LOWG world candidate validation issues: ${report.issues.size}")
            reportDocument.issueCountsByCode.forEach { (code, count) ->
                appendLine("  $code: $count")
            }
        }
        println(summary.trim())

        assertTrue(
            structuralIssues.isEmpty(),
            buildString {
                appendLine("Unexpected structural projection issues in LOWG world candidate:")
                structuralIssues.forEach { issue -> appendLine("- ${issue.code}: ${issue.message}") }
            }.trim(),
        )
    }

    private fun resolveProjectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val direct = cwd.resolve("settings.gradle.kts")
        return if (Files.exists(direct)) {
            cwd
        } else {
            cwd.parent ?: cwd
        }
    }

    private fun WorldCandidateDocument.toWorld(): AviationWorld {
        val positions = world.geometry.points.mapValues { (_, point) ->
            Position(
                xMeters = point.xMeters,
                yMeters = point.yMeters,
            )
        }.mapKeys { (id, _) -> PointId(id) }

        val paths = world.geometry.paths.mapValues { (_, path) ->
            WorldPath(path.pointIds.map(::PointId))
        }

        val segments = world.geometry.paths.values
            .flatMap { path ->
                val pointIds = path.pointIds.map(::PointId)
                pointIds.zipWithNext().map { (from, to) ->
                    val fromPosition = positions.getValue(from)
                    val toPosition = positions.getValue(to)
                    GeometrySegmentId.between(from, to) to SegmentGeometry(
                        length = Meters(hypot(toPosition.xMeters - fromPosition.xMeters, toPosition.yMeters - fromPosition.yMeters)),
                        width = Meters(path.widthMeters),
                        surface = path.surface.toSurfaceType(),
                    )
                }
            }
            .groupBy(
                keySelector = { (segmentId, _) -> segmentId },
                valueTransform = { (_, geometry) -> geometry },
            )
            .mapValues { (segmentId, geometries) ->
                val first = geometries.first()
                require(geometries.all { geometry -> geometry == first }) {
                    "Conflicting segment geometry projection for ${segmentId.describe()}: $geometries"
                }
                first
            }

        val runways = world.aerodrome.runways.mapValues { (_, runway) ->
            Runway(
                id = RunwayId(runway.id),
                path = paths.getValue(runway.pathId),
                threshold = PointId(runway.thresholdPointId),
                declaredDistances = DeclaredDistances(
                    tora = Meters(runway.declaredDistances.toraMeters.toDouble()),
                    toda = Meters(runway.declaredDistances.todaMeters.toDouble()),
                    asda = Meters(runway.declaredDistances.asdaMeters.toDouble()),
                    lda = Meters(runway.declaredDistances.ldaMeters.toDouble()),
                ),
            )
        }.mapKeys { (id, _) -> RunwayId(id) }

        val taxiways = world.aerodrome.taxiways.mapValues { (_, taxiway) ->
            Taxiway(
                id = TaxiwayId(taxiway.id),
                name = taxiway.name,
                path = paths.getValue(taxiway.pathId),
                holdingPoints = taxiway.holdingPoints.map { holdingPoint ->
                    HoldingPoint(
                        point = PointId(holdingPoint.pointId),
                        name = holdingPoint.name,
                        type = holdingPoint.type.toHoldingPointType(),
                        runway = holdingPoint.runwayId?.let(::RunwayId),
                    )
                },
                bidirectional = taxiway.bidirectional,
            )
        }.mapKeys { (id, _) -> TaxiwayId(id) }

        val stands = world.aerodrome.stands.mapValues { (_, stand) ->
            Stand(
                id = StandId(stand.id),
                name = stand.name,
                point = PointId(stand.pointId),
            )
        }.mapKeys { (id, _) -> StandId(id) }

        val aprons = world.aerodrome.aprons.mapValues { (_, apron) ->
            Apron(
                id = ApronId(apron.id),
                name = apron.name,
                paths = apron.pathIds.map(paths::getValue),
                stands = apron.standIds.map(::StandId).toSet(),
            )
        }.mapKeys { (id, _) -> ApronId(id) }

        val fixes = world.fixes.mapValues { (_, fix) ->
            Fix(
                id = FixId(fix.id),
                point = PointId(fix.pointId),
                name = fix.name,
                type = fix.type.toFixType(),
            )
        }.mapKeys { (id, _) -> FixId(id) }

        val vfrRoutes = world.vfrRoutes.mapValues { (_, route) ->
            VfrRoute(
                id = VfrRouteId(route.id),
                name = route.name,
                waypoints = route.pointIds.map { pointId ->
                    Waypoint(PointId(pointId))
                },
                airspaceProfile = route.airspaceProfile.toVfrRouteAirspaceProfile(),
            )
        }.mapKeys { (id, _) -> VfrRouteId(id) }

        val operationalSectors = world.aerodrome.aip.operationalSectors.mapValues { (_, sector) ->
            OperationalSector(
                id = OperationalSectorId(sector.id),
                name = sector.name,
                kind = sector.kind.toOperationalSectorKind(),
                boundary = AirspaceBoundary(
                    sector.boundaryPathIds.map { pathId ->
                        paths.getValue(pathId).asBoundaryRing()
                    },
                ),
                anchor = sector.anchor?.toOperationalSectorAnchor(),
                entryExitPoints = sector.entryExitPointIds.map(::PointId).toSet(),
                altitudeBand = sector.altitudeBand?.toAltitudeBand(),
                contactRequirement = sector.contactRequirement?.toContactRequirement(),
                relationToCtr = sector.relationToCtr?.toOperationalSectorCtrRelation(),
                associatedProcedures = sector.associatedProcedureIds.map(::PublishedVfrProcedureId).toSet(),
                note = sector.note,
                specialProcedureNote = sector.specialProcedureNote,
            )
        }.mapKeys { (id, _) -> OperationalSectorId(id) }
        val publishedVfrProcedures = world.aerodrome.aip.publishedVfrProcedures.mapValues { (_, procedure) ->
            procedure.toPublishedVfrProcedure()
        }.mapKeys { (id, _) -> PublishedVfrProcedureId(id) }
        val circuits = world.aerodrome.circuits.mapValues { (_, circuit) ->
            CircuitProcedure(
                id = CircuitProcedureId(circuit.id),
                runway = RunwayId(circuit.runwayId),
                direction = circuit.direction.toCircuitDirection(),
                legs = circuit.legs.map { leg ->
                    CircuitLeg(
                        name = leg.name.toLegName(),
                        path = paths.getValue(leg.pathId),
                    )
                },
                altitude = Level.AltitudeFeet.unsafe(circuit.altitudeFeet),
                reportingPoints = circuit.reportingPoints.mapKeys { (name, _) -> name.toLegName() }
                    .mapValues { (_, pointId) -> PointId(pointId) },
                joinProcedures = circuit.joinProcedures.map { join ->
                    CircuitJoin(
                        type = join.type.toJoinType(),
                        entryPoint = PointId(join.entryPointId),
                        entryPath = join.entryPathId?.let(paths::getValue),
                    )
                },
                goAroundPath = paths.getValue(circuit.goAroundPathId),
            )
        }.mapKeys { (id, _) -> CircuitProcedureId(id) }

        val syntheticAirspace = world.syntheticAirspace
        val airspaceVolumeId = AirspaceVolumeId(syntheticAirspace.volumeId)
        val firId = FirId(syntheticAirspace.firId)
        val airspace = AirspaceVolume(
            id = airspaceVolumeId,
            name = syntheticAirspace.volumeName,
            type = AirspaceVolumeType.valueOf(syntheticAirspace.type),
            airspaceClass = AirspaceClass.valueOf(syntheticAirspace.airspaceClass),
            altitudeBand = AltitudeBand(
                lower = AltitudeBoundary.Surface,
                upper = AltitudeBoundary.AtLevel(Level.AltitudeFeet.unsafe(syntheticAirspace.upperAltitudeFeet)),
            ),
            memberPoints = syntheticAirspace.memberPointIds.map(::PointId).toSet(),
            fir = firId,
            boundary = syntheticAirspace.boundaryPathIds
                .takeIf { it.isNotEmpty() }
                ?.map { pathId -> paths.getValue(pathId).asBoundaryRing() }
                ?.let(::AirspaceBoundary),
        )
        val fir = FlightInformationRegion(
            id = firId,
            name = syntheticAirspace.firName,
            volumes = setOf(airspaceVolumeId),
        )

        val aerodrome = Aerodrome(
            icao = AerodromeId(world.aerodrome.icao),
            elevation = Feet(world.aerodrome.elevationFeet),
            magneticVariation = Degrees(world.aerodrome.magneticVariationDegrees.toDouble()),
            transitionAltitude = Level.AltitudeFeet.unsafe(world.aerodrome.transitionAltitudeFeet),
            aip = AerodromeAip(
                operationalSectors = operationalSectors,
                publishedVfrProcedures = publishedVfrProcedures,
            ),
            runways = runways,
            circuits = circuits,
            taxiways = taxiways,
            stands = stands,
            aprons = aprons,
        )

        return AviationWorld(
            geometry = PhysicalGeometry(
                points = positions,
                segments = segments,
            ),
            fixes = fixes,
            vfrRoutes = vfrRoutes,
            aerodromes = mapOf(AerodromeId(world.aerodrome.icao) to aerodrome),
            airspace = mapOf(airspaceVolumeId to airspace),
            firs = mapOf(firId to fir),
        )
    }

    private fun String.toSurfaceType(): SurfaceType =
        when (this) {
            "GROUND" -> SurfaceType.GROUND
            "RUNWAY" -> SurfaceType.RUNWAY
            "SKY" -> SurfaceType.SKY
            else -> error("Unsupported surface type: $this")
        }

    private fun CandidateVfrRouteAirspaceProfile?.toVfrRouteAirspaceProfile(): VfrRouteAirspaceProfile? =
        when (this?.kind) {
            "IN_VOLUME" -> airspaceVolumeId?.let(::AirspaceVolumeId)?.let(VfrRouteAirspaceProfile::InVolume)
            "IN_CLASS" -> airspaceClass?.let(AirspaceClass::valueOf)?.let(VfrRouteAirspaceProfile::InClass)
            "SEGMENTED" -> VfrRouteAirspaceProfile.Segmented(
                segments.map { segment ->
                    VfrRouteAirspaceSegment(
                        from = PointId(segment.fromPointId),
                        to = PointId(segment.toPointId),
                        airspaceVolume = AirspaceVolumeId(segment.airspaceVolumeId),
                    )
                },
            )
            else -> null
        }

    private fun CandidatePublishedVfrProcedure.toPublishedVfrProcedure(): PublishedVfrProcedure =
        PublishedVfrProcedure(
            id = PublishedVfrProcedureId(id),
            plateId = PlateId(plateId),
            kind = kind.toPublishedVfrProcedureKind(),
            publishedSequence = publishedSequence.map { reference -> reference.toPublishedPointReference() },
            associatedVfrRoutes = associatedVfrRouteIds.map(::VfrRouteId).toSet(),
            associatedOperationalSectors = associatedOperationalSectorIds.map(::OperationalSectorId).toSet(),
            associatedCircuits = associatedCircuitIds.map(::CircuitProcedureId).toSet(),
            contactRequirement = contactRequirement?.toContactRequirement(),
            advisories = advisories?.toPublishedProcedureAdvisories(),
            mapLabels = mapLabels.map { label ->
                PublishedMapLabel(
                    label = label.label,
                    location = label.location.toPublishedPointReference(),
                )
            },
            terminatesAt = terminatesAt?.toPublishedPointReference(),
            holdAt = holdAt?.toPublishedPointReference(),
            communicationFailure = communicationFailure?.toPublishedProcedureCommunicationFailure(),
            departureRunways = departureRunwayIds.map(::RunwayId).toSet(),
            applicableRunways = applicableRunwayIds.map(::RunwayId).toSet(),
        )

    private fun String.toPublishedVfrProcedureKind(): PublishedVfrProcedureKind =
        when (this) {
            "ARRIVAL" -> PublishedVfrProcedureKind.ARRIVAL
            "DEPARTURE" -> PublishedVfrProcedureKind.DEPARTURE
            "TRANSIT" -> PublishedVfrProcedureKind.TRANSIT
            "CIRCUIT_PUBLICATION" -> PublishedVfrProcedureKind.CIRCUIT_PUBLICATION
            "CIRCUIT_ATTACHED_HOLD" -> PublishedVfrProcedureKind.CIRCUIT_ATTACHED_HOLD
            else -> error("Unsupported published VFR procedure kind: $this")
        }

    private fun String.toCircuitDirection(): CircuitDirection =
        when (this) {
            "LEFT_HAND" -> CircuitDirection.LEFT_HAND
            "RIGHT_HAND" -> CircuitDirection.RIGHT_HAND
            else -> error("Unsupported circuit direction: $this")
        }

    private fun String.toLegName(): LegName =
        when (this) {
            "UPWIND" -> LegName.UPWIND
            "CROSSWIND" -> LegName.CROSSWIND
            "DOWNWIND" -> LegName.DOWNWIND
            "BASE" -> LegName.BASE
            "FINAL" -> LegName.FINAL
            else -> error("Unsupported circuit leg name: $this")
        }

    private fun String.toJoinType(): JoinType =
        when (this) {
            "STRAIGHT_IN" -> JoinType.STRAIGHT_IN
            "BASE" -> JoinType.BASE
            "DOWNWIND" -> JoinType.DOWNWIND
            "CROSSWIND" -> JoinType.CROSSWIND
            "MID_DOWNWIND" -> JoinType.MID_DOWNWIND
            "OVERHEAD" -> JoinType.OVERHEAD
            "LONG_FINAL" -> JoinType.LONG_FINAL
            else -> error("Unsupported join type: $this")
        }

    private fun CandidatePublishedPointReference.toPublishedPointReference(): PublishedPointReference =
        when (kind) {
            "FIX" -> PublishedPointReference.Fix(reference = reference, point = PointId(requireNotNull(pointId)))
            "NAMED_POINT" -> PublishedPointReference.NamedPoint(reference = reference, point = PointId(requireNotNull(pointId)))
            "OPERATIONAL_SECTOR_ANCHOR" -> PublishedPointReference.SectorAnchor(reference = reference, point = PointId(requireNotNull(pointId)))
            "LITERAL" -> PublishedPointReference.Literal(reference = reference)
            else -> error("Unsupported published point reference type: $kind")
        }

    private fun String.toOperationalSectorKind(): OperationalSectorKind =
        when (this) {
            "VFR_OPERATIONAL" -> OperationalSectorKind.VFR_OPERATIONAL
            "IFR_HOLDING_SECTOR" -> OperationalSectorKind.IFR_HOLDING_SECTOR
            "VFR_TRAINING_AREA" -> OperationalSectorKind.VFR_TRAINING_AREA
            "GLIDER_SECTOR" -> OperationalSectorKind.GLIDER_SECTOR
            "NIGHT_VFR_SECTOR" -> OperationalSectorKind.NIGHT_VFR_SECTOR
            "HELICOPTER_OPERATIONAL" -> OperationalSectorKind.HELICOPTER_OPERATIONAL
            else -> error("Unsupported operational sector kind: $this")
        }

    private fun CandidateOperationalSectorAnchor.toOperationalSectorAnchor(): OperationalSectorAnchor =
        when (kind) {
            "CTR_BOUNDARY_REPORTING_POINT" -> OperationalSectorAnchor.CtrBoundaryReportingPoint(PointId(requireNotNull(pointId)))
            "REPORTING_POINT" -> OperationalSectorAnchor.ReportingPoint(PointId(requireNotNull(pointId)))
            "NAVAID" -> OperationalSectorAnchor.Navaid(PointId(requireNotNull(pointId)))
            else -> error("Unsupported operational sector anchor role: $kind")
        }

    private fun String.toOperationalSectorCtrRelation(): OperationalSectorCtrRelation =
        when (this) {
            "WITHIN_CTR" -> OperationalSectorCtrRelation.WITHIN_CTR
            "OVERLAPS_CTR" -> OperationalSectorCtrRelation.OVERLAPS_CTR
            "BOUNDARY_OR_OVERLAP" -> OperationalSectorCtrRelation.BOUNDARY_OR_OVERLAP
            "ADJACENT_TO_CTR" -> OperationalSectorCtrRelation.ADJACENT_TO_CTR
            "INSIDE_TMA_OUTSIDE_CTR" -> OperationalSectorCtrRelation.INSIDE_TMA_OUTSIDE_CTR
            else -> error("Unsupported operational sector CTR relation: $this")
        }

    private fun CandidateAltitudeBand.toAltitudeBand(): AltitudeBand =
        AltitudeBand(
            lower = lower.toAltitudeBoundary(),
            upper = upper?.toAltitudeBoundary() ?: AltitudeBoundary.Unlimited,
        )

    private fun CandidateAltitudeBoundary.toAltitudeBoundary(): AltitudeBoundary =
        when (kind) {
            "SURFACE" -> AltitudeBoundary.Surface
            "AT_LEVEL" -> AltitudeBoundary.AtLevel(
                when (levelType) {
                    "ALTITUDE_FEET" -> Level.AltitudeFeet.unsafe(value ?: 0)
                    "FLIGHT_LEVEL" -> Level.FlightLevel.unsafe(value ?: 0)
                    "HEIGHT_FEET" -> Level.HeightFeet.unsafe(value ?: 0)
                    else -> error("Unsupported altitude boundary level type: $levelType")
                },
            )
            "UNLIMITED" -> AltitudeBoundary.Unlimited
            else -> error("Unsupported altitude boundary kind: $kind")
        }

    private fun CandidateContactRequirement.toContactRequirement(): ContactRequirement =
        ContactRequirement(
            role = RoleName.valueOf(role),
            timing = timing.toContactTiming(),
        )

    private fun CandidateContactTiming.toContactTiming(): ContactTiming =
        when (kind) {
            "BEFORE_ENTRY" -> ContactTiming.BeforeEntry
            "BEFORE_POINT" -> ContactTiming.BeforePoint(PointId(requireNotNull(pointId)))
            "AT_POINT" -> ContactTiming.AtPoint(PointId(requireNotNull(pointId)))
            "DISTANCE_BEFORE" -> ContactTiming.DistanceBefore(
                point = PointId(requireNotNull(pointId)),
                distance = DmeDistanceNm.unsafe(requireNotNull(distanceNm)),
            )
            "BY_ALTITUDE" -> ContactTiming.ByAltitude(
                when (levelType) {
                    "ALTITUDE_FEET" -> Level.AltitudeFeet.unsafe(requireNotNull(value))
                    "FLIGHT_LEVEL" -> Level.FlightLevel.unsafe(requireNotNull(value))
                    "HEIGHT_FEET" -> Level.HeightFeet.unsafe(requireNotNull(value))
                    else -> error("Unsupported contact timing altitude level type: $levelType")
                },
            )
            else -> error("Unsupported contact timing kind: $kind")
        }

    private fun CandidatePublishedProcedureAdvisories.toPublishedProcedureAdvisories(): PublishedProcedureAdvisories =
        PublishedProcedureAdvisories(
            contact = contact,
            altitude = altitude,
            route = route,
            reporting = reporting,
            availability = availability,
            specialProcedure = specialProcedure,
            noiseAbatement = noiseAbatement,
            speedCap = speedCap,
            squawkConvention = squawkConvention,
            activationHours = activationHours,
            equipmentMinimum = equipmentMinimum,
            language = language,
            general = general,
        )

    private fun CandidatePublishedProcedureCommunicationFailure.toPublishedProcedureCommunicationFailure():
        PublishedProcedureCommunicationFailure =
        PublishedProcedureCommunicationFailure(
            beforeContactEstablished = beforeContactEstablished,
            afterContactEstablishedExitSequence = afterContactEstablishedExitSequence.map { reference ->
                reference.toPublishedPointReference()
            },
            note = note,
        )

    private fun String.toFixType(): FixType =
        when (this) {
            "WAYPOINT" -> FixType.WAYPOINT
            "VOR" -> FixType.VOR
            "NDB" -> FixType.NDB
            "MARKER" -> FixType.MARKER
            else -> error("Unsupported fix type: $this")
        }

    private fun String?.toHoldingPointType(): HoldingPointType =
        when (this) {
            null -> HoldingPointType.CAT_A
            "CAT_A" -> HoldingPointType.CAT_A
            "CAT_B" -> HoldingPointType.CAT_B
            "INTERMEDIATE" -> HoldingPointType.INTERMEDIATE
            else -> error("Unsupported holding point type: $this")
        }

    private companion object {
        val structuralCodes = setOf(
            WorldValidationCode.ORPHAN_GEOMETRY_POINT,
            WorldValidationCode.ORPHAN_GEOMETRY_SEGMENT,
            WorldValidationCode.GEOMETRY_SEGMENT_UNKNOWN_ENDPOINT,
            WorldValidationCode.UNKNOWN_GEOMETRY_POINT_REFERENCE,
            WorldValidationCode.UNKNOWN_GEOMETRY_SEGMENT_REFERENCE,
            WorldValidationCode.POINT_OUTSIDE_AIRSPACE,
            WorldValidationCode.UNKNOWN_FIR,
            WorldValidationCode.FIR_VOLUME_MISMATCH,
            WorldValidationCode.UNKNOWN_AIRSPACE_VOLUME,
            WorldValidationCode.RECIPROCAL_RUNWAYS_DO_NOT_SHARE_SEGMENT,
        )
    }
}

@Serializable
private data class ValidationReportDocument(
    val airportCode: String,
    val airportName: String,
    val projectionStatus: String,
    val sourceManifest: String,
    val sourceEntityBundle: String,
    val forcedAssumptions: List<String>,
    val omittedFeatures: List<String>,
    val projectionGaps: List<String>,
    val issueCount: Int,
    val issueCountsByCode: Map<String, Int>,
    val structuralIssueCount: Int,
    val structuralIssueCodes: List<String>,
    val issues: List<ValidationIssueRecord>,
)

@Serializable
private data class ValidationIssueRecord(
    val code: String,
    val message: String,
)
