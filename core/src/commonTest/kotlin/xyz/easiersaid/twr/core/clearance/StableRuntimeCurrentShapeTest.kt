package xyz.easiersaid.twr.core.clearance

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import xyz.easiersaid.twr.core.resolution.ResolutionResult
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.FixtureIds
import xyz.easiersaid.twr.core.world.sampleWorld
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ApproachComponent
import xyz.easiersaid.twr.protocol.AvoidLevel
import xyz.easiersaid.twr.protocol.CancelClearance
import xyz.easiersaid.twr.protocol.ClearanceContent
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.ClearanceId
import xyz.easiersaid.twr.protocol.ClearanceStatus
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.ContinuePresentHeading
import xyz.easiersaid.twr.protocol.DescendWhenReady
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.FlyHeading
import xyz.easiersaid.twr.protocol.Heading
import xyz.easiersaid.twr.protocol.InterceptLocaliser
import xyz.easiersaid.twr.protocol.JoinAirway
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.MinimumCleanSpeed
import xyz.easiersaid.twr.protocol.PressureSetting
import xyz.easiersaid.twr.protocol.ProceedDirect
import xyz.easiersaid.twr.protocol.ResumeNormalSpeed
import xyz.easiersaid.twr.protocol.ResumeOwnNavigation
import xyz.easiersaid.twr.protocol.RouteAsFiled
import xyz.easiersaid.twr.protocol.SetPressure
import xyz.easiersaid.twr.protocol.StopTurn
import xyz.easiersaid.twr.protocol.TickNumber
import xyz.easiersaid.twr.protocol.TurnDirection
import xyz.easiersaid.twr.protocol.TurnHeading

class StableRuntimeCurrentShapeTest {

    @Test
    fun proceedDirectResolvesToDirectFixAndCompletesAtFix() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-PD",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(
                    ProceedDirect(TEST_AIRCRAFT, FixId("HOLD"))
                )
            )
        ).requireResolved()

        val step = assertIs<ResolvedStep.DirectFix>(resolved.steps.single())
        assertEquals(FixId("HOLD"), step.fix.id)
        assertEquals(FixtureIds.holdFixPoint, step.fix.point)

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.holdFixPoint,
                entities = emptySet(),
                onGround = false
            )
        )

        assertEquals(CompletionResult.COMPLETE, evaluation.stepResults.single().result)
        assertEquals(ClearanceStatus.COMPLETED, evaluation.updated.source.status)
    }

    @Test
    fun joinAirwayResolvesToAirwayJoinAndCompletesAtJoinFix() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-JOIN-AIRWAY",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(
                    JoinAirway(TEST_AIRCRAFT, FixtureIds.airway, FixId("SIDEXIT"))
                )
            )
        ).requireResolved()

        val step = assertIs<ResolvedStep.AirwayJoin>(resolved.steps.single())
        assertEquals(FixtureIds.airway, step.airway.id)
        assertEquals(FixId("SIDEXIT"), step.joinFix.id)
        assertEquals(FixtureIds.sidExit, step.joinFix.point)

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.sidExit,
                entities = emptySet(),
                onGround = false
            )
        )

        assertEquals(CompletionResult.COMPLETE, evaluation.stepResults.single().result)
        assertEquals(ClearanceStatus.COMPLETED, evaluation.updated.source.status)
    }

    @Test
    fun plainRouteControlLifecycleMatchesCurrentEngine() {
        val world = sampleWorld()

        val resumeOwnNavigation = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-RON",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(ResumeOwnNavigation(TEST_AIRCRAFT))
            )
        ).requireResolved()
        val routeAsFiled = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-RAF",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(RouteAsFiled(TEST_AIRCRAFT))
            )
        ).requireResolved()
        val flyHeading = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-FLY-HD",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(FlyHeading(TEST_AIRCRAFT, Heading.unsafe(270)))
            )
        ).requireResolved()
        val turnHeading = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-TURN-HD",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(TurnHeading(TEST_AIRCRAFT, TurnDirection.LEFT, Heading.unsafe(180)))
            )
        ).requireResolved()
        val continuePresentHeading = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-CPH",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(ContinuePresentHeading(TEST_AIRCRAFT))
            )
        ).requireResolved()
        val stopTurn = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-STOP-TURN",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(StopTurn(TEST_AIRCRAFT))
            )
        ).requireResolved()
        val interceptLocaliser = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-LOC",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(InterceptLocaliser(TEST_AIRCRAFT, FixtureIds.runway09))
            )
        ).requireResolved()

        assertIs<ResolvedStep.Plain>(resumeOwnNavigation.steps.single())
        assertIs<ResolvedStep.Plain>(routeAsFiled.steps.single())
        assertIs<ResolvedStep.Plain>(flyHeading.steps.single())
        assertIs<ResolvedStep.Plain>(turnHeading.steps.single())
        assertIs<ResolvedStep.Plain>(continuePresentHeading.steps.single())
        assertIs<ResolvedStep.Plain>(stopTurn.steps.single())
        assertIs<ResolvedStep.Plain>(interceptLocaliser.steps.single())

        val emptyView = CompletionView(position = FixtureIds.sidExit, entities = emptySet(), onGround = false)
        val localiserView = emptyView.copy(
            establishedApproachComponents = setOf(ApproachComponent.LOCALISER)
        )

        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(resumeOwnNavigation, emptyView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(routeAsFiled, emptyView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.NOT_APPLICABLE,
            evaluateCompletion(flyHeading, emptyView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.NOT_APPLICABLE,
            evaluateCompletion(turnHeading, emptyView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.NOT_APPLICABLE,
            evaluateCompletion(continuePresentHeading, emptyView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(stopTurn, emptyView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(interceptLocaliser, localiserView).stepResults.single().result
        )
    }

    @Test
    fun airModifierLifecycleMatchesCurrentEngine() {
        val world = sampleWorld()

        val descendWhenReady = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-DWR",
                domain = ClearanceDomain.LEVEL,
                content = ClearanceContent.Single(
                    DescendWhenReady(TEST_AIRCRAFT, Level.AltitudeFeet.unsafe(3000))
                )
            )
        ).requireResolved()
        val avoidLevel = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-AVOID",
                domain = ClearanceDomain.LEVEL,
                content = ClearanceContent.Single(
                    AvoidLevel(TEST_AIRCRAFT, Level.FlightLevel.unsafe(80))
                )
            )
        ).requireResolved()
        val minimumCleanSpeed = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-MIN-CLEAN",
                domain = ClearanceDomain.SPEED,
                content = ClearanceContent.Single(MinimumCleanSpeed(TEST_AIRCRAFT))
            )
        ).requireResolved()
        val resumeNormalSpeed = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-RNS",
                domain = ClearanceDomain.SPEED,
                content = ClearanceContent.Single(ResumeNormalSpeed(TEST_AIRCRAFT))
            )
        ).requireResolved()
        val setPressure = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-QNH",
                domain = ClearanceDomain.LEVEL,
                content = ClearanceContent.Single(
                    SetPressure(TEST_AIRCRAFT, PressureSetting.Standard)
                )
            )
        ).requireResolved()
        val cancelClearance = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-CANCEL",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(CancelClearance(TEST_AIRCRAFT))
            )
        ).requireResolved()

        assertIs<ResolvedStep.Plain>(descendWhenReady.steps.single())
        assertIs<ResolvedStep.Plain>(avoidLevel.steps.single())
        assertIs<ResolvedStep.Plain>(minimumCleanSpeed.steps.single())
        assertIs<ResolvedStep.Plain>(resumeNormalSpeed.steps.single())
        assertIs<ResolvedStep.Plain>(setPressure.steps.single())
        assertIs<ResolvedStep.Plain>(cancelClearance.steps.single())

        val satisfiedLevelView = CompletionView(
            position = FixtureIds.sidExit,
            entities = emptySet(),
            altitude = Level.AltitudeFeet.unsafe(2500),
            onGround = false
        )
        val avoidSatisfiedView = CompletionView(
            position = FixtureIds.sidExit,
            entities = emptySet(),
            altitude = Level.FlightLevel.unsafe(90),
            onGround = false
        )
        val emptyView = CompletionView(position = FixtureIds.sidExit, entities = emptySet(), onGround = false)

        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(descendWhenReady, satisfiedLevelView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(avoidLevel, avoidSatisfiedView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.NOT_APPLICABLE,
            evaluateCompletion(minimumCleanSpeed, emptyView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(resumeNormalSpeed, emptyView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(setPressure, emptyView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(cancelClearance, emptyView).stepResults.single().result
        )
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
