package xyz.easiersaid.twr.core.clearance

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import xyz.easiersaid.twr.core.resolution.ResolutionFailureCode
import xyz.easiersaid.twr.core.resolution.ResolutionResult
import xyz.easiersaid.twr.core.world.FixtureIds
import xyz.easiersaid.twr.core.world.sampleWorld
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ClearedToEnterControlZone
import xyz.easiersaid.twr.protocol.ClearanceContent
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.ClearanceId
import xyz.easiersaid.twr.protocol.ClearanceStatus
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ConditionalClearance
import xyz.easiersaid.twr.protocol.ConditionalPredicate
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.BacktrackRunway
import xyz.easiersaid.twr.protocol.ClearedTo
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClimbTo
import xyz.easiersaid.twr.protocol.CrossRunway
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.RemainOutsideControlledAirspace
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.JoinAirway
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RouteSpec
import xyz.easiersaid.twr.protocol.SpecialVfrClearance
import xyz.easiersaid.twr.protocol.TaxiTo
import xyz.easiersaid.twr.protocol.TrafficAction
import xyz.easiersaid.twr.protocol.TrafficRef
import xyz.easiersaid.twr.protocol.TickNumber

class ResolvedClearanceTest {

    @Test
    fun resolvesSingleFrequencyClearance() {
        val world = sampleWorld()
        val clearance = StructuredClearance(
            id = ClearanceId("CLR-FREQ"),
            aircraft = AircraftId("TEST123"),
            content = ClearanceContent.Single(
                ContactFrequency(
                    target = AircraftId("TEST123"),
                    role = RoleName.TOWER
                )
            ),
            domain = ClearanceDomain.FREQUENCY,
            issuedBy = ControllerId("CTRL-1"),
            issuedAt = TickNumber(1),
            status = ClearanceStatus.ACTIVE
        )

        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = clearance
        ).requireResolved()

        assertEquals(1, resolved.steps.size)
        val step = assertIs<ResolvedStep.FrequencyChange>(resolved.steps.single())
        assertEquals(RoleName.TOWER, step.frequency.roleName)
        assertEquals("118.500", step.frequency.instructedFrequency.mhz)
        assertEquals(1, resolved.immediateSteps.size)
        assertEquals(setOf(ClearanceDomain.FREQUENCY), resolved.supersedesDomains)
    }

    @Test
    fun resolvesConditionalTaxiClearanceIntoEnvelopeCondition() {
        val world = sampleWorld()
        val condition = ConditionalPredicate.AfterTraffic(
            traffic = TrafficRef.ByDescription("landing 737"),
            action = TrafficAction.LANDING
        )
        val clearance = StructuredClearance(
            id = ClearanceId("CLR-CONDITIONAL-TAXI"),
            aircraft = AircraftId("TEST123"),
            content = ClearanceContent.Single(
                ConditionalClearance(
                    target = AircraftId("TEST123"),
                    condition = condition,
                    instruction = TaxiTo(
                        target = AircraftId("TEST123"),
                        destination = FixtureIds.holdShort09,
                        via = listOf(FixtureIds.apronJunction)
                    )
                )
            ),
            domain = ClearanceDomain.GROUND,
            issuedBy = ControllerId("CTRL-1"),
            issuedAt = TickNumber(1),
            status = ClearanceStatus.ACTIVE
        )

        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentPoint = FixtureIds.standPoint
            ),
            clearance = clearance
        ).requireResolved()

        assertEquals(condition, resolved.source.condition)
        assertIs<TaxiTo>((resolved.source.content as ClearanceContent.Single).instruction)
        assertIs<ResolvedStep.Taxi>(resolved.steps.single())
    }

    @Test
    fun rejectsConditionalCompoundSteps() {
        val world = sampleWorld()
        val clearance = StructuredClearance(
            id = ClearanceId("CLR-CONDITIONAL-COMPOUND"),
            aircraft = AircraftId("TEST123"),
            content = ClearanceContent.Compound(
                steps = arrow.core.nonEmptyListOf(
                    ConditionalClearance(
                        target = AircraftId("TEST123"),
                        condition = ConditionalPredicate.BehindTraffic(
                            TrafficRef.ByDescription("departing A320")
                        ),
                        instruction = CrossRunway(
                            target = AircraftId("TEST123"),
                            runway = FixtureIds.runway09
                        )
                    ),
                    HoldShortOf(
                        target = AircraftId("TEST123"),
                        runway = FixtureIds.runway27
                    )
                )
            ),
            domain = ClearanceDomain.GROUND,
            issuedBy = ControllerId("CTRL-1"),
            issuedAt = TickNumber(1),
            status = ClearanceStatus.ACTIVE
        )

        val result = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentPoint = FixtureIds.holdShort09
            ),
            clearance = clearance
        )

        assertTrue(result.isLeft())
        val unresolved = (result as arrow.core.Either.Left).value
        assertEquals(ResolutionFailureCode.CONDITIONAL_STEP_NOT_SUPPORTED, unresolved.code)
    }

    @Test
    fun rejectsConditionalNonSurfaceInstructions() {
        val world = sampleWorld()
        val clearance = StructuredClearance(
            id = ClearanceId("CLR-CONDITIONAL-TAKEOFF"),
            aircraft = AircraftId("TEST123"),
            content = ClearanceContent.Single(
                ConditionalClearance(
                    target = AircraftId("TEST123"),
                    condition = ConditionalPredicate.AfterTraffic(
                        traffic = TrafficRef.ByDescription("landing 737"),
                        action = TrafficAction.LANDING
                    ),
                    instruction = ClimbTo(
                        target = AircraftId("TEST123"),
                        level = Level.FlightLevel.unsafe(350)
                    )
                )
            ),
            domain = ClearanceDomain.LEVEL,
            issuedBy = ControllerId("CTRL-1"),
            issuedAt = TickNumber(1),
            status = ClearanceStatus.ACTIVE
        )

        val result = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = clearance
        )

        assertTrue(result.isLeft())
        val unresolved = (result as arrow.core.Either.Left).value
        assertEquals(ResolutionFailureCode.CONDITIONAL_INSTRUCTION_NOT_ALLOWED, unresolved.code)
    }

    @Test
    fun resolvesCompoundTaxiClearanceAgainstTaxiRoute() {
        val world = sampleWorld()
        val clearance = StructuredClearance(
            id = ClearanceId("CLR-TAXI"),
            aircraft = AircraftId("TEST123"),
            content = ClearanceContent.Compound(
                steps = arrow.core.nonEmptyListOf(
                    TaxiTo(
                        target = AircraftId("TEST123"),
                        destination = FixtureIds.holdShort27,
                        via = listOf(FixtureIds.apronJunction)
                    ),
                    CrossRunway(
                        target = AircraftId("TEST123"),
                        runway = FixtureIds.runway09
                    ),
                    HoldShortOf(
                        target = AircraftId("TEST123"),
                        runway = FixtureIds.runway27
                    )
                ),
                completedSteps = setOf(0)
            ),
            domain = ClearanceDomain.GROUND,
            issuedBy = ControllerId("CTRL-1"),
            issuedAt = TickNumber(2),
            status = ClearanceStatus.ACTIVE
        )

        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentPoint = FixtureIds.standPoint
            ),
            clearance = clearance
        ).requireResolved()

        val taxi = assertIs<ResolvedStep.Taxi>(resolved.steps[0])
        assertEquals(
            listOf(
                FixtureIds.standPoint,
                FixtureIds.apronJunction,
                FixtureIds.holdShort09,
                FixtureIds.runwayMid,
                FixtureIds.holdShort27
            ),
            taxi.route.points
        )

        val crossing = assertIs<ResolvedStep.Crossing>(resolved.steps[1])
        assertEquals(FixtureIds.runwayMid, crossing.crossing.crossingPoint)
        assertEquals(FixtureIds.runway09, crossing.crossing.runway.id)

        val holdShort = assertIs<ResolvedStep.HoldShort>(resolved.steps[2])
        assertEquals(FixtureIds.holdShort27, holdShort.holdingPoint.holdingPoint.point)
        assertEquals(FixtureIds.runway27, holdShort.holdingPoint.runway.id)

        assertEquals(2, resolved.sequentialSteps.size)
        assertEquals(1, resolved.persistentSteps.size)
        assertEquals(0, resolved.completedSteps.single())
        assertEquals(1, resolved.nextSequentialStep?.index)
    }

    @Test
    fun resolvesCompoundDepartureClearanceAcrossDomains() {
        val world = sampleWorld()
        val clearance = StructuredClearance(
            id = ClearanceId("CLR-DEP"),
            aircraft = AircraftId("TEST123"),
            content = ClearanceContent.Compound(
                steps = arrow.core.nonEmptyListOf(
                    ClearedTo(
                        target = AircraftId("TEST123"),
                        clearanceLimit = FixId("HOLD"),
                        route = RouteSpec.ViaSid(FixtureIds.sid)
                    ),
                    ContactFrequency(
                        target = AircraftId("TEST123"),
                        role = RoleName.APPROACH
                    )
                )
            ),
            domain = ClearanceDomain.ROUTE,
            issuedBy = ControllerId("CTRL-1"),
            issuedAt = TickNumber(3),
            status = ClearanceStatus.ACTIVE
        )

        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = clearance
        ).requireResolved()

        val route = assertIs<ResolvedStep.Route>(resolved.steps[0])
        assertEquals(FixtureIds.sid, assertIs<xyz.easiersaid.twr.core.resolution.ResolvedRouteSpec.SidProcedure>(route.clearance.route).sid.id)

        val frequency = assertIs<ResolvedStep.FrequencyChange>(resolved.steps[1])
        assertEquals(RoleName.APPROACH, frequency.frequency.roleName)
        assertEquals(listOf(1), resolved.immediateSteps.map { step -> step.index })
        assertEquals(setOf(ClearanceDomain.ROUTE, ClearanceDomain.FREQUENCY), resolved.supersedesDomains)
    }

    @Test
    fun unresolvedGroundCompilationRequiresCurrentPoint() {
        val world = sampleWorld()
        val clearance = StructuredClearance(
            id = ClearanceId("CLR-MISSING-POINT"),
            aircraft = AircraftId("TEST123"),
            content = ClearanceContent.Single(
                TaxiTo(
                    target = AircraftId("TEST123"),
                    destination = FixtureIds.holdShort09
                )
            ),
            domain = ClearanceDomain.GROUND,
            issuedBy = ControllerId("CTRL-1"),
            issuedAt = TickNumber(4),
            status = ClearanceStatus.ACTIVE
        )

        val result = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = clearance
        )

        assertTrue(result.isLeft())
        val unresolved = (result as arrow.core.Either.Left).value
        assertEquals(ResolutionFailureCode.MISSING_CURRENT_POINT, unresolved.code)
    }

    @Test
    fun unresolvedJoinAirwayRequiresJoinFixOnAirway() {
        val world = sampleWorld()
        val clearance = StructuredClearance(
            id = ClearanceId("CLR-JOIN-AIRWAY"),
            aircraft = AircraftId("TEST123"),
            content = ClearanceContent.Single(
                JoinAirway(
                    target = AircraftId("TEST123"),
                    airway = FixtureIds.airway,
                    joinFix = FixId("IAF")
                )
            ),
            domain = ClearanceDomain.ROUTE,
            issuedBy = ControllerId("CTRL-1"),
            issuedAt = TickNumber(5),
            status = ClearanceStatus.ACTIVE
        )

        val result = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = clearance
        )

        assertTrue(result.isLeft())
        val unresolved = (result as arrow.core.Either.Left).value
        assertEquals(ResolutionFailureCode.AIRWAY_JOIN_FIX_NOT_ON_AIRWAY, unresolved.code)
    }

    @Test
    fun resolvesBacktrackIntoFarEndRunwayStep() {
        val world = sampleWorld()
        val clearance = StructuredClearance(
            id = ClearanceId("CLR-BACKTRACK"),
            aircraft = AircraftId("TEST123"),
            content = ClearanceContent.Single(
                BacktrackRunway(
                    target = AircraftId("TEST123"),
                    runway = FixtureIds.runway09
                )
            ),
            domain = ClearanceDomain.GROUND,
            issuedBy = ControllerId("CTRL-1"),
            issuedAt = TickNumber(6),
            status = ClearanceStatus.ACTIVE
        )

        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = clearance
        ).requireResolved()

        val step = assertIs<ResolvedStep.Backtrack>(resolved.steps.single())
        assertEquals(FixtureIds.runway27Threshold, step.farEndPoint)
    }

    @Test
    fun resolvesControlZoneClearanceAgainstAirspaceVolume() {
        val world = sampleWorld()
        val clearance = StructuredClearance(
            id = ClearanceId("CLR-ENTER-CTR"),
            aircraft = AircraftId("TEST123"),
            content = ClearanceContent.Single(
                ClearedToEnterControlZone(
                    target = AircraftId("TEST123"),
                    airspace = FixtureIds.airspace,
                    route = RouteSpec.ViaRoute(FixtureIds.vfrRoute),
                    levelRestriction = Level.AltitudeFeet.unsafe(1500)
                )
            ),
            domain = ClearanceDomain.ROUTE,
            issuedBy = ControllerId("CTRL-1"),
            issuedAt = TickNumber(7),
            status = ClearanceStatus.ACTIVE
        )

        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = clearance
        ).requireResolved()

        val step = assertIs<ResolvedStep.Airspace>(resolved.steps.single())
        assertEquals(FixtureIds.airspace, step.airspace.airspace.id)
        assertEquals(
            FixtureIds.vfrRoute,
            assertIs<xyz.easiersaid.twr.core.resolution.ResolvedRouteSpec.VfrRouteProcedure>(step.airspace.route).route.id
        )
        assertEquals(Level.AltitudeFeet.unsafe(1500), step.airspace.levelRestriction)
    }

    @Test
    fun resolvesCompoundControlZoneClearanceAgainstAirspaceVolumeAndTail() {
        val world = sampleWorld()
        val clearance = StructuredClearance(
            id = ClearanceId("CLR-ENTER-CTR-COMPOUND"),
            aircraft = AircraftId("TEST123"),
            content = ClearanceContent.Compound(
                steps = arrow.core.nonEmptyListOf(
                    ClearedToEnterControlZone(
                        target = AircraftId("TEST123"),
                        airspace = FixtureIds.airspace
                    ),
                    ContactFrequency(
                        target = AircraftId("TEST123"),
                        role = RoleName.TOWER
                    )
                )
            ),
            domain = ClearanceDomain.ROUTE,
            issuedBy = ControllerId("CTRL-1"),
            issuedAt = TickNumber(8),
            status = ClearanceStatus.ACTIVE
        )

        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = clearance
        ).requireResolved()

        val airspace = assertIs<ResolvedStep.Airspace>(resolved.steps[0])
        assertEquals(FixtureIds.airspace, airspace.airspace.airspace.id)

        val frequency = assertIs<ResolvedStep.FrequencyChange>(resolved.steps[1])
        assertEquals(RoleName.TOWER, frequency.frequency.roleName)
        assertEquals(setOf(ClearanceDomain.ROUTE, ClearanceDomain.FREQUENCY), resolved.supersedesDomains)
    }

    @Test
    fun resolvesRemainOutsideControlledAirspaceAgainstAirspaceVolume() {
        val world = sampleWorld()
        val clearance = StructuredClearance(
            id = ClearanceId("CLR-ROCA"),
            aircraft = AircraftId("TEST123"),
            content = ClearanceContent.Single(
                RemainOutsideControlledAirspace(
                    target = AircraftId("TEST123"),
                    airspace = FixtureIds.airspace
                )
            ),
            domain = ClearanceDomain.ROUTE,
            issuedBy = ControllerId("CTRL-1"),
            issuedAt = TickNumber(9),
            status = ClearanceStatus.ACTIVE
        )

        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = clearance
        ).requireResolved()

        val step = assertIs<ResolvedStep.Airspace>(resolved.steps.single())
        assertEquals(FixtureIds.airspace, step.airspace.airspace.id)
        assertTrue(FixtureIds.runway09Threshold in step.airspace.airspace.points)
    }

    @Test
    fun unresolvedAirspaceInstructionRequiresKnownAirspaceVolume() {
        val world = sampleWorld()
        val clearance = StructuredClearance(
            id = ClearanceId("CLR-SVFR-UNKNOWN"),
            aircraft = AircraftId("TEST123"),
            content = ClearanceContent.Single(
                SpecialVfrClearance(
                    target = AircraftId("TEST123"),
                    airspace = xyz.easiersaid.twr.protocol.AirspaceVolumeId("UNKNOWN-CTR")
                )
            ),
            domain = ClearanceDomain.ROUTE,
            issuedBy = ControllerId("CTRL-1"),
            issuedAt = TickNumber(10),
            status = ClearanceStatus.ACTIVE
        )

        val result = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = clearance
        )

        assertTrue(result.isLeft())
        val unresolved = (result as arrow.core.Either.Left).value
        assertEquals(ResolutionFailureCode.UNKNOWN_AIRSPACE_VOLUME, unresolved.code)
    }
}

private fun <T> ResolutionResult<T>.requireResolved(): T =
    getOrElse { failure -> error("Expected resolved, got ${failure.code}: ${failure.message}") }
