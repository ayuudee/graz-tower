package xyz.easiersaid.twr.core.clearance

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import xyz.easiersaid.twr.core.resolution.ResolutionResult
import xyz.easiersaid.twr.core.world.FixtureIds
import xyz.easiersaid.twr.core.world.sampleWorld
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ClearanceContent
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.ClearanceId
import xyz.easiersaid.twr.protocol.ClearanceStatus
import xyz.easiersaid.twr.protocol.ClearedApproach
import xyz.easiersaid.twr.protocol.ClearedTo
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClimbTo
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.GoAround
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RouteSpec
import xyz.easiersaid.twr.protocol.TickNumber
import xyz.easiersaid.twr.protocol.ApproachType

class SupersessionEngineTest {

    @Test
    fun standaloneFrequencyPartiallySupersedesCompoundRouteClearance() {
        val world = sampleWorld()
        val existing = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = supersessionClearance(
                id = "EXISTING-COMPOUND",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Compound(
                    steps = arrow.core.nonEmptyListOf(
                        ClearedTo(
                            target = TEST_AIRCRAFT,
                            clearanceLimit = FixId("HOLD"),
                            route = RouteSpec.ViaSid(FixtureIds.sid)
                        ),
                        ContactFrequency(
                            target = TEST_AIRCRAFT,
                            role = RoleName.APPROACH
                        )
                    )
                )
            )
        ).requireResolved()
        val incoming = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = supersessionClearance(
                id = "INCOMING-FREQ",
                domain = ClearanceDomain.FREQUENCY,
                content = ClearanceContent.Single(
                    ContactFrequency(
                        target = TEST_AIRCRAFT,
                        role = RoleName.TOWER
                    )
                )
            )
        ).requireResolved()

        val decision = determineSupersession(incoming, listOf(existing))

        assertTrue(decision.fullySuperseded.isEmpty())
        assertEquals(1, decision.partiallySuperseded.size)
        assertEquals(
            setOf(ClearanceDomain.FREQUENCY),
            decision.partiallySuperseded.single().suppressedDomains
        )
        assertEquals(
            listOf(ClearanceDomain.ROUTE),
            decision.partiallySuperseded.single().remainingSteps.map { step -> step.domain }
        )
    }

    @Test
    fun goAroundFullySupersedesRunwayRouteAndLevelClearances() {
        val world = sampleWorld()
        val existing = listOf(
            world.resolveClearance(
                context = ClearanceResolutionContext(FixtureIds.aerodrome),
                clearance = supersessionClearance(
                    id = "LAND",
                    domain = ClearanceDomain.RUNWAY,
                    content = ClearanceContent.Single(
                        ClearedToLand(
                            target = TEST_AIRCRAFT,
                            runway = FixtureIds.runway09
                        )
                    )
                )
            ).requireResolved(),
            world.resolveClearance(
                context = ClearanceResolutionContext(FixtureIds.aerodrome),
                clearance = supersessionClearance(
                    id = "APP",
                    domain = ClearanceDomain.ROUTE,
                    content = ClearanceContent.Single(
                        ClearedApproach(
                            target = TEST_AIRCRAFT,
                            approachType = ApproachType.ILS,
                            runway = FixtureIds.runway09
                        )
                    )
                )
            ).requireResolved(),
            world.resolveClearance(
                context = ClearanceResolutionContext(FixtureIds.aerodrome),
                clearance = supersessionClearance(
                    id = "CLIMB",
                    domain = ClearanceDomain.LEVEL,
                    content = ClearanceContent.Single(
                        ClimbTo(
                            target = TEST_AIRCRAFT,
                            level = Level.AltitudeFeet(5000)
                        )
                    )
                )
            ).requireResolved()
        )
        val incoming = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = supersessionClearance(
                id = "GA",
                domain = ClearanceDomain.RUNWAY,
                content = ClearanceContent.Single(
                    GoAround(target = TEST_AIRCRAFT)
                )
            )
        ).requireResolved()

        val decision = determineSupersession(incoming, existing)

        assertEquals(
            setOf("LAND", "APP", "CLIMB"),
            decision.fullySuperseded.map { clearance -> clearance.source.id.value }.toSet()
        )
        assertTrue(decision.partiallySuperseded.isEmpty())
        assertTrue(decision.unaffected.isEmpty())
    }
}

private val TEST_AIRCRAFT = AircraftId("TEST123")

private fun supersessionClearance(
    id: String,
    domain: ClearanceDomain,
    content: ClearanceContent,
    status: ClearanceStatus = ClearanceStatus.ACTIVE
): StructuredClearance =
    StructuredClearance(
        id = ClearanceId(id),
        aircraft = TEST_AIRCRAFT,
        content = content,
        domain = domain,
        issuedBy = ControllerId("CTRL-1"),
        issuedAt = TickNumber(1),
        status = status
    )

private fun <T> ResolutionResult<T>.requireResolved(): T =
    getOrElse { failure -> error("Expected resolved, got ${failure.code}: ${failure.message}") }
