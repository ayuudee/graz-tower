package xyz.easiersaid.twr.core.clearance

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.FixtureIds
import xyz.easiersaid.twr.core.world.sampleWorld
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ClearanceContent
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.ClearanceId
import xyz.easiersaid.twr.protocol.ClearanceStatus
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedLowApproach
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.GoAround
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.TickNumber

class RunwayWorldBackedCurrentShapeTest {

    @Test
    fun lineUpAndWaitResolvesAsWorldBackedRunwayStep() {
        val resolved = sampleWorld().resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-LUP-WB",
                domain = ClearanceDomain.RUNWAY,
                content = ClearanceContent.Single(
                    LineUpAndWait(TEST_AIRCRAFT, FixtureIds.runway09)
                )
            )
        ).requireResolved()

        val step = assertIs<ResolvedStep.RunwayOperation>(resolved.steps.single())
        assertEquals(FixtureIds.runway09, step.operation.runway.id)
        assertEquals(FixtureIds.runway09Threshold, step.operation.thresholdPoint)
        assertEquals(
            listOf(
                FixtureIds.runway09Threshold,
                FixtureIds.runwayMid,
                FixtureIds.runway27Threshold
            ),
            step.operation.pathPoints
        )
    }

    @Test
    fun takeoffCompletesOnAirborneRunwayTransition() {
        val resolved = sampleWorld().resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-TO-WB",
                domain = ClearanceDomain.RUNWAY,
                content = ClearanceContent.Single(
                    ClearedForTakeoff(TEST_AIRCRAFT, FixtureIds.runway09)
                )
            )
        ).requireResolved()

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.goAroundClimb,
                entities = emptySet(),
                onGround = false,
                transitionHistory = setOf(EntityRef.RunwayRef(FixtureIds.runway09))
            )
        )

        assertEquals(CompletionResult.COMPLETE, evaluation.stepResults.single().result)
        assertEquals(ClearanceStatus.COMPLETED, evaluation.updated.source.status)
    }

    @Test
    fun landingTouchAndGoAndLowApproachUseResolvedRunwayLifecycle() {
        val world = sampleWorld()
        val landing = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-LAND-WB",
                domain = ClearanceDomain.RUNWAY,
                content = ClearanceContent.Single(
                    ClearedToLand(TEST_AIRCRAFT, FixtureIds.runway09)
                )
            )
        ).requireResolved()
        val touchAndGo = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-TG-WB",
                domain = ClearanceDomain.RUNWAY,
                content = ClearanceContent.Single(
                    ClearedTouchAndGo(TEST_AIRCRAFT, FixtureIds.runway09)
                )
            )
        ).requireResolved()
        val lowApproach = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-LA-WB",
                domain = ClearanceDomain.RUNWAY,
                content = ClearanceContent.Single(
                    ClearedLowApproach(TEST_AIRCRAFT, FixtureIds.runway09)
                )
            )
        ).requireResolved()

        val vacatedView = CompletionView(
            position = FixtureIds.holdShort09,
            entities = setOf(EntityRef.TaxiwayRef(FixtureIds.taxiwayAlpha)),
            onGround = true,
            transitionHistory = setOf(EntityRef.RunwayRef(FixtureIds.runway09))
        )
        val airborneAfterTouchView = CompletionView(
            position = FixtureIds.goAroundClimb,
            entities = emptySet(),
            onGround = false,
            transitionHistory = setOf(EntityRef.RunwayRef(FixtureIds.runway09))
        )

        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(landing, vacatedView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(touchAndGo, airborneAfterTouchView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(lowApproach, airborneAfterTouchView).stepResults.single().result
        )
    }

    @Test
    fun goAroundResolvesWorldBackedFromCurrentRunwayAndRemainsActive() {
        val resolved = sampleWorld().resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentRunway = FixtureIds.runway09
            ),
            clearance = structuredClearance(
                id = "CLR-GA-WB",
                domain = ClearanceDomain.RUNWAY,
                content = ClearanceContent.Single(
                    GoAround(TEST_AIRCRAFT)
                )
            )
        ).requireResolved()

        val step = assertIs<ResolvedStep.RunwayOperation>(resolved.steps.single())
        assertEquals(FixtureIds.runway09, step.operation.runway.id)

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.goAroundClimb,
                entities = emptySet(),
                onGround = false,
                transitionHistory = setOf(EntityRef.RunwayRef(FixtureIds.runway09))
            )
        )

        assertEquals(CompletionResult.NOT_COMPLETE, evaluation.stepResults.single().result)
        assertEquals(ClearanceStatus.ACTIVE, evaluation.updated.source.status)
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

private fun <T> arrow.core.Either<*, T>.requireResolved(): T =
    getOrElse { failure -> error("Expected resolved, got $failure") }
