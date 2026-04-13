package xyz.easiersaid.twr.core.clearance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import xyz.easiersaid.twr.core.resolution.ResolutionFailureCode
import xyz.easiersaid.twr.core.resolution.ResolutionResult
import xyz.easiersaid.twr.core.world.FixtureIds
import xyz.easiersaid.twr.core.world.sampleWorld
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ClearanceContent
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.ClearanceId
import xyz.easiersaid.twr.protocol.ClearanceStatus
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.ClearedTo
import xyz.easiersaid.twr.protocol.CrossRunway
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.JoinAirway
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RouteSpec
import xyz.easiersaid.twr.protocol.TaxiTo
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
    fun resolvesCompoundTaxiClearanceAgainstTaxiRoute() {
        val world = sampleWorld()
        val clearance = StructuredClearance(
            id = ClearanceId("CLR-TAXI"),
            aircraft = AircraftId("TEST123"),
            content = ClearanceContent.Compound(
                steps = listOf(
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
                steps = listOf(
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

        val unresolved = assertIs<ResolutionResult.Unresolved>(result)
        assertEquals(ResolutionFailureCode.MISSING_CURRENT_POINT, unresolved.failure.code)
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

        val unresolved = assertIs<ResolutionResult.Unresolved>(result)
        assertEquals(ResolutionFailureCode.AIRWAY_JOIN_FIX_NOT_ON_AIRWAY, unresolved.failure.code)
    }
}

private fun ResolutionResult<ResolvedClearance>.requireResolved(): ResolvedClearance =
    when (this) {
        is ResolutionResult.Resolved -> value
        is ResolutionResult.Unresolved -> error("Expected resolved clearance, got ${failure.code}: ${failure.message}")
    }
