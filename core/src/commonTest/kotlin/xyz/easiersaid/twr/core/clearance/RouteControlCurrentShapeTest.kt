package xyz.easiersaid.twr.core.clearance

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import xyz.easiersaid.twr.core.world.FixtureIds
import xyz.easiersaid.twr.core.world.sampleWorld
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ClearanceContent
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.ClearanceId
import xyz.easiersaid.twr.protocol.ClearanceStatus
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.Heading
import xyz.easiersaid.twr.protocol.ProceedDirect
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.TickNumber
import xyz.easiersaid.twr.protocol.TurnByDegrees
import xyz.easiersaid.twr.protocol.TurnDirection

class RouteControlCurrentShapeTest {

    @Test
    fun turnByDegreesCompoundResolvesVectorPrimaryAndImmediateFrequencyTail() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(
                FixtureIds.aerodrome,
                currentHeading = Heading.unsafe(135)
            ),
            clearance = structuredClearance(
                id = "CLR-TURN-DEG-CONTACT",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Compound(
                    steps = arrow.core.nonEmptyListOf(
                        TurnByDegrees.unsafe(TEST_AIRCRAFT, TurnDirection.LEFT, 90),
                        ContactFrequency(TEST_AIRCRAFT, RoleName.APPROACH)
                    )
                )
            )
        ).requireResolved()

        val turnStep = assertIs<ResolvedStep.Vector>(resolved.steps[0])
        val frequencyStep = assertIs<ResolvedStep.FrequencyChange>(resolved.steps[1])

        assertEquals(Heading.unsafe(135), turnStep.vector.capturedHeading)
        assertEquals(Heading.unsafe(45), turnStep.vector.targetHeading)
        assertEquals(90, turnStep.vector.turnDegrees)
        assertEquals(RoleName.APPROACH, frequencyStep.frequency.roleName)

        val tailOnlyEvaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.sidExit,
                entities = emptySet(),
                onGround = false,
                radioState = RadioState(
                    currentRole = RoleName.APPROACH,
                    lastContactRole = RoleName.APPROACH
                )
            )
        )

        assertEquals(
            listOf(CompletionResult.NOT_COMPLETE, CompletionResult.COMPLETE),
            tailOnlyEvaluation.stepResults.map { result -> result.result }
        )
        assertEquals(setOf(1), tailOnlyEvaluation.newlyCompletedSteps)
        assertEquals(ClearanceStatus.ACTIVE, tailOnlyEvaluation.updated.source.status)

        val completeEvaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.sidExit,
                entities = emptySet(),
                onGround = false,
                observedTurnDirection = TurnDirection.LEFT,
                observedTurnDegrees = 90,
                radioState = RadioState(
                    currentRole = RoleName.APPROACH,
                    lastContactRole = RoleName.APPROACH
                )
            )
        )

        assertEquals(
            listOf(CompletionResult.COMPLETE, CompletionResult.COMPLETE),
            completeEvaluation.stepResults.map { result -> result.result }
        )
        assertEquals(setOf(0, 1), completeEvaluation.newlyCompletedSteps)
        assertEquals(ClearanceStatus.COMPLETED, completeEvaluation.updated.source.status)
    }

    @Test
    fun routeControlCompoundFrequencySupersessionPreservesPrimaryStep() {
        val world = sampleWorld()
        val existing = world.resolveClearance(
            context = ClearanceResolutionContext(
                FixtureIds.aerodrome,
                currentHeading = Heading.unsafe(135)
            ),
            clearance = structuredClearance(
                id = "CLR-TURN-DEG-CONTACT",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Compound(
                    steps = arrow.core.nonEmptyListOf(
                        TurnByDegrees.unsafe(TEST_AIRCRAFT, TurnDirection.LEFT, 90),
                        ContactFrequency(TEST_AIRCRAFT, RoleName.APPROACH)
                    )
                )
            )
        ).requireResolved()
        val incoming = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-TURN-DEG-FREQ",
                domain = ClearanceDomain.FREQUENCY,
                content = ClearanceContent.Single(
                    ContactFrequency(TEST_AIRCRAFT, RoleName.TOWER)
                )
            )
        ).requireResolved()

        val decision = determineSupersession(incoming, listOf(existing))

        assertEquals(emptyList(), decision.fullySuperseded)
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
    fun proceedDirectCompoundCompletesAtFixAfterImmediateFrequencyTail() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-PD-CONTACT",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Compound(
                    steps = arrow.core.nonEmptyListOf(
                        ProceedDirect(TEST_AIRCRAFT, FixId("HOLD")),
                        ContactFrequency(TEST_AIRCRAFT, RoleName.TOWER)
                    )
                )
            )
        ).requireResolved()

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.holdFixPoint,
                entities = emptySet(),
                onGround = false,
                radioState = RadioState(
                    currentRole = RoleName.TOWER,
                    lastContactRole = RoleName.TOWER
                )
            )
        )

        assertEquals(
            listOf(CompletionResult.COMPLETE, CompletionResult.COMPLETE),
            evaluation.stepResults.map { result -> result.result }
        )
        assertEquals(setOf(0, 1), evaluation.newlyCompletedSteps)
        assertEquals(ClearanceStatus.COMPLETED, evaluation.updated.source.status)
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
