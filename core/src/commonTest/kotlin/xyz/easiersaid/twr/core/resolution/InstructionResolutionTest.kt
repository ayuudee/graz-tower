package xyz.easiersaid.twr.core.resolution

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import xyz.easiersaid.twr.core.world.FixtureIds
import xyz.easiersaid.twr.core.world.WorldValidationCode
import xyz.easiersaid.twr.core.world.sampleWorld
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ApproachType
import xyz.easiersaid.twr.protocol.ClearedApproach
import xyz.easiersaid.twr.protocol.ClearedTo
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.CrossRunway
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.HoldAt
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.HoldSpec
import xyz.easiersaid.twr.protocol.RouteSpec
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint

class InstructionResolutionTest {

    @Test
    fun taxiToResolvesGroundPathThroughViaPoints() {
        val result = sampleWorld().resolveTaxiTo(
            context = GroundResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentPoint = FixtureIds.standPoint
            ),
            instruction = TaxiToHoldingPoint(
                target = AircraftId("TEST123"),
                destination = FixtureIds.holdShort09,
                runway = FixtureIds.runway09,
                via = listOf(FixtureIds.apronJunction)
            )
        )

        val resolved = result.requireResolved()
        assertEquals(
            listOf(
                FixtureIds.standPoint,
                FixtureIds.apronJunction,
                FixtureIds.holdShort09
            ),
            resolved.points
        )
    }

    @Test
    fun holdShortResolvesHoldingPointOnCurrentTaxiway() {
        val result = sampleWorld().resolveHoldShortOf(
            context = GroundResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentPoint = FixtureIds.apronJunction
            ),
            instruction = HoldShortOf(
                target = AircraftId("TEST123"),
                runway = FixtureIds.runway09
            )
        )

        val resolved = result.requireResolved()
        assertEquals(FixtureIds.taxiwayAlpha, resolved.taxiway.id)
        assertEquals(FixtureIds.holdShort09, resolved.holdingPoint.point)
    }

    @Test
    fun crossRunwayResolvesSharedTaxiwayPoint() {
        val result = sampleWorld().resolveCrossRunway(
            context = GroundResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentPoint = FixtureIds.holdShort09
            ),
            instruction = CrossRunway(
                target = AircraftId("TEST123"),
                runway = FixtureIds.runway09
            )
        )

        val resolved = result.requireResolved()
        assertEquals(FixtureIds.taxiwayAlpha, resolved.taxiway.id)
        assertEquals(FixtureIds.runway09, resolved.runway.id)
        assertEquals(FixtureIds.runwayMid, resolved.crossingPoint)
    }

    @Test
    fun clearedApproachResolvesPublishedApproach() {
        val result = sampleWorld().resolveClearedApproach(
            context = AerodromeResolutionContext(FixtureIds.aerodrome),
            instruction = ClearedApproach(
                target = AircraftId("TEST123"),
                approachType = ApproachType.ILS,
                runway = FixtureIds.runway09
            )
        )

        val resolved = result.requireResolved()
        assertEquals(FixtureIds.approach, resolved.approach.id)
        assertEquals(FixtureIds.runway09, resolved.approach.runway)
    }

    @Test
    fun clearedToResolvesSidRouteSpec() {
        val result = sampleWorld().resolveClearedTo(
            context = AerodromeResolutionContext(FixtureIds.aerodrome),
            instruction = ClearedTo(
                target = AircraftId("TEST123"),
                clearanceLimit = FixId("HOLD"),
                route = RouteSpec.ViaSid(FixtureIds.sid)
            )
        )

        val resolved = result.requireResolved()
        assertEquals("HOLD", resolved.clearanceLimit.id.value)
        val route = assertIs<ResolvedRouteSpec.SidProcedure>(resolved.route)
        assertEquals(FixtureIds.sid, route.sid.id)
    }

    @Test
    fun holdAtResolvesPublishedHoldingPattern() {
        val result = sampleWorld().resolveHoldAt(
            context = AerodromeResolutionContext(FixtureIds.aerodrome),
            instruction = HoldAt(
                target = AircraftId("TEST123"),
                hold = HoldSpec.Published(FixId("HOLD"))
            )
        )

        val resolved = result.requireResolved()
        assertEquals(FixtureIds.hold, resolved.holdingPattern.id)
        assertEquals("HOLD", resolved.fix.id.value)
    }

    @Test
    fun contactFrequencyResolvesRoleFrequency() {
        val result = sampleWorld().resolveContactFrequency(
            context = AerodromeResolutionContext(FixtureIds.aerodrome),
            instruction = ContactFrequency(
                target = AircraftId("TEST123"),
                role = RoleName.TOWER
            )
        )

        val resolved = result.requireResolved()
        assertEquals(RoleName.TOWER, resolved.roleName)
        assertEquals("118.500", resolved.publishedFrequency.mhz)
        assertEquals("118.500", resolved.instructedFrequency.mhz)
    }
}

private fun <T> ResolutionResult<T>.requireResolved(): T =
    getOrElse { failure -> error("Expected resolved, got ${failure.code}: ${failure.message}") }
