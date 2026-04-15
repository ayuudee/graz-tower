package xyz.easiersaid.twr.core.clearance

import arrow.core.Either
import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import xyz.easiersaid.twr.core.resolution.ResolutionFailureCode
import xyz.easiersaid.twr.core.world.FixtureIds
import xyz.easiersaid.twr.core.world.routeAdjacentWorld
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
    fun continueApproachResolvesAgainstCurrentApproachAndRemainsActive() {
        val world = routeAdjacentWorld()
        val clearance = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentApproach = FixtureIds.approach
            ),
            clearance = structuredClearance(
                id = "CLR-CONT-APP",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(ContinueApproach(TEST_AIRCRAFT))
            )
        ).requireResolved()

        val step = assertIs<ResolvedStep.ContinueApproachStep>(clearance.steps.single())
        assertEquals(FixtureIds.approach, step.continuation.approach.id)
        assertEquals(listOf(FixtureIds.iafPoint, FixtureIds.fafPoint, FixtureIds.runway09Threshold), step.continuation.waypointPoints)

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
    fun extendDownwindAndOrbitResolveAgainstCurrentCircuitContext() {
        val world = routeAdjacentWorld()
        val extendDownwind = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentCircuit = FixtureIds.circuit09
            ),
            clearance = structuredClearance(
                id = "CLR-EXT-DW",
                domain = ClearanceDomain.RUNWAY,
                content = ClearanceContent.Single(ExtendDownwind(TEST_AIRCRAFT))
            )
        ).requireResolved()
        val orbit = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentPoint = FixtureIds.downwindEnd,
                currentCircuit = FixtureIds.circuit09
            ),
            clearance = structuredClearance(
                id = "CLR-ORBIT",
                domain = ClearanceDomain.RUNWAY,
                content = ClearanceContent.Single(Orbit(TEST_AIRCRAFT, OrbitDirection.LEFT))
            )
        ).requireResolved()

        val extendStep = assertIs<ResolvedStep.ExtendDownwindStep>(extendDownwind.steps.single())
        val orbitStep = assertIs<ResolvedStep.OrbitStep>(orbit.steps.single())

        assertEquals(FixtureIds.circuit09, extendStep.extension.circuit.id)
        assertEquals(
            listOf(FixtureIds.downwindEnd, xyz.easiersaid.twr.core.world.RouteAdjacentFixtureIds.extendedDownwindEnd),
            extendStep.extension.extendedPathPoints
        )
        assertEquals(FixtureIds.circuit09, orbitStep.orbit.circuit.id)
        assertEquals(FixtureIds.downwindEnd, orbitStep.orbit.orbitPoint)
        assertEquals(OrbitDirection.LEFT, orbitStep.orbit.direction)

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
    }

    @Test
    fun orbitRequiresCurrentPointMatchingPublishedOrbitPoint() {
        val world = routeAdjacentWorld()
        val result = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentPoint = FixtureIds.crosswindEnd,
                currentCircuit = FixtureIds.circuit09
            ),
            clearance = structuredClearance(
                id = "CLR-ORBIT",
                domain = ClearanceDomain.RUNWAY,
                content = ClearanceContent.Single(Orbit(TEST_AIRCRAFT, OrbitDirection.LEFT))
            )
        )

        when (result) {
            is Either.Left -> assertEquals(ResolutionFailureCode.NO_ORBIT_POINT, result.value.code)
            is Either.Right -> error("Expected orbit resolution to fail when no published orbit point matches the current point")
        }
    }

    @Test
    fun routeAdjacentSupersessionMatchesCurrentEngine() {
        val world = routeAdjacentWorld()
        val continueApproach = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentApproach = FixtureIds.approach
            ),
            clearance = structuredClearance(
                id = "CLR-CONT-APP",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(ContinueApproach(TEST_AIRCRAFT))
            )
        ).requireResolved()
        val extendDownwind = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentCircuit = FixtureIds.circuit09
            ),
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

private fun <T> arrow.core.Either<xyz.easiersaid.twr.core.resolution.ResolutionFailure, T>.requireResolved(): T =
    getOrElse { failure -> error("Expected resolved, got ${failure.code}: ${failure.message}") }
