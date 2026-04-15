package xyz.easiersaid.twr.core.clearance

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import xyz.easiersaid.twr.core.resolution.ResolutionResult
import xyz.easiersaid.twr.core.world.FixtureIds
import xyz.easiersaid.twr.core.world.sampleWorld
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ClearanceContent
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.ClearanceId
import xyz.easiersaid.twr.protocol.ClearanceStatus
import xyz.easiersaid.twr.protocol.ContinueApproach
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ExtendDownwind
import xyz.easiersaid.twr.protocol.GoAround
import xyz.easiersaid.twr.protocol.Orbit
import xyz.easiersaid.twr.protocol.OrbitDirection
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.TickNumber

class RouteAdjacentCurrentShapeTest {

    @Test
    fun continueApproachResolvesAsPlainAndRemainsActiveUnderCurrentEngine() {
        val world = sampleWorld()
        val clearance = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-CONT-APP",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(ContinueApproach(TEST_AIRCRAFT))
            )
        ).requireResolved()

        val step = assertIs<ResolvedStep.Plain>(clearance.steps.single())
        assertEquals(ClearanceDomain.ROUTE, step.domain)

        val evaluation = evaluateCompletion(
            clearance = clearance,
            view = CompletionView(
                position = FixtureIds.fafPoint,
                entities = emptySet(),
                onGround = false
            )
        )

        assertEquals(CompletionResult.NOT_COMPLETE, evaluation.stepResults.single().result)
        assertEquals(ClearanceStatus.ACTIVE, evaluation.updated.source.status)
    }

    @Test
    fun extendDownwindAndOrbitUseSourceDomainConventionAndRemainActive() {
        val world = sampleWorld()
        val extendDownwind = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-EXT-DW",
                domain = ClearanceDomain.RUNWAY,
                content = ClearanceContent.Single(ExtendDownwind(TEST_AIRCRAFT))
            )
        ).requireResolved()
        val orbit = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-ORBIT",
                domain = ClearanceDomain.RUNWAY,
                content = ClearanceContent.Single(Orbit(TEST_AIRCRAFT, OrbitDirection.LEFT))
            )
        ).requireResolved()

        val extendStep = assertIs<ResolvedStep.Plain>(extendDownwind.steps.single())
        val orbitStep = assertIs<ResolvedStep.Plain>(orbit.steps.single())
        assertEquals(ClearanceDomain.RUNWAY, extendStep.domain)
        assertEquals(ClearanceDomain.RUNWAY, orbitStep.domain)

        val view = CompletionView(
            position = FixtureIds.downwindEnd,
            entities = emptySet(),
            onGround = false
        )

        assertEquals(
            CompletionResult.NOT_APPLICABLE,
            evaluateCompletion(extendDownwind, view).stepResults.single().result
        )
        assertEquals(
            CompletionResult.NOT_APPLICABLE,
            evaluateCompletion(orbit, view).stepResults.single().result
        )
        assertEquals(ClearanceStatus.ACTIVE, evaluateCompletion(extendDownwind, view).updated.source.status)
        assertEquals(ClearanceStatus.ACTIVE, evaluateCompletion(orbit, view).updated.source.status)
    }

    @Test
    fun routeAdjacentSupersessionMatchesCurrentEngine() {
        val world = sampleWorld()
        val continueApproach = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-CONT-APP",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(ContinueApproach(TEST_AIRCRAFT))
            )
        ).requireResolved()
        val extendDownwind = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-EXT-DW",
                domain = ClearanceDomain.RUNWAY,
                content = ClearanceContent.Single(ExtendDownwind(TEST_AIRCRAFT))
            )
        ).requireResolved()
        val incomingGoAround = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentRunway = FixtureIds.runway09
            ),
            clearance = structuredClearance(
                id = "CLR-GA",
                domain = ClearanceDomain.RUNWAY,
                content = ClearanceContent.Single(GoAround(TEST_AIRCRAFT))
            )
        ).requireResolved()
        val incomingFrequency = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-FREQ",
                domain = ClearanceDomain.FREQUENCY,
                content = ClearanceContent.Single(ContactFrequency(TEST_AIRCRAFT, RoleName.TOWER))
            )
        ).requireResolved()

        val routeDecision = determineSupersession(incomingGoAround, listOf(continueApproach))
        val circuitDecision = determineSupersession(incomingFrequency, listOf(extendDownwind))

        assertEquals(listOf(continueApproach), routeDecision.fullySuperseded)
        assertEquals(listOf(extendDownwind), circuitDecision.unaffected)
    }
}

private val TEST_AIRCRAFT = AircraftId("TEST123")

private fun structuredClearance(
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
