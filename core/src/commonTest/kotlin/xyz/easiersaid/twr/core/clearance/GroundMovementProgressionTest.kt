package xyz.easiersaid.twr.core.clearance

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.FixtureIds
import xyz.easiersaid.twr.core.world.sampleWorld
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.BacktrackRunway
import xyz.easiersaid.twr.protocol.ClearanceContent
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.ClearanceId
import xyz.easiersaid.twr.protocol.ClearanceStatus
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.CrossRunway
import xyz.easiersaid.twr.protocol.HoldPosition
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.protocol.TickNumber

class GroundMovementProgressionTest {

    @Test
    fun taxiCompletesFromTraversedGroundProgress() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentPoint = FixtureIds.standPoint
            ),
            clearance = structuredClearance(
                id = "CLR-TAXI-PROGRESS",
                domain = ClearanceDomain.GROUND,
                content = ClearanceContent.Single(
                    TaxiToHoldingPoint(
                        target = TEST_AIRCRAFT,
                        destination = FixtureIds.holdShort27,
                        runway = FixtureIds.runway27,
                        via = listOf(FixtureIds.apronJunction)
                    )
                )
            )
        ).requireResolved()

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.runwayMid,
                entities = setOf(EntityRef.RunwayRef(FixtureIds.runway09)),
                onGround = true,
                groundProgress = GroundProgressState(
                    traversedPoints = setOf(FixtureIds.holdShort27)
                )
            )
        )

        assertEquals(CompletionResult.COMPLETE, evaluation.stepResults.single().result)
        assertEquals(ClearanceStatus.COMPLETED, evaluation.updated.source.status)
    }

    @Test
    fun crossingCompletesFromExplicitGroundCrossingProgress() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentPoint = FixtureIds.apronJunction
            ),
            clearance = structuredClearance(
                id = "CLR-CROSS-PROGRESS",
                domain = ClearanceDomain.GROUND,
                content = ClearanceContent.Single(
                    CrossRunway(
                        target = TEST_AIRCRAFT,
                        runway = FixtureIds.runway09
                    )
                )
            )
        ).requireResolved()

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.holdShort27,
                entities = setOf(EntityRef.TaxiwayRef(FixtureIds.taxiwayAlpha)),
                onGround = true,
                groundProgress = GroundProgressState(
                    crossedRunways = setOf(FixtureIds.runway09)
                )
            )
        )

        assertEquals(CompletionResult.COMPLETE, evaluation.stepResults.single().result)
        assertEquals(ClearanceStatus.COMPLETED, evaluation.updated.source.status)
    }

    @Test
    fun holdShortRemainsActiveWhenHoldingPointReachedAndStopped() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentPoint = FixtureIds.apronJunction
            ),
            clearance = structuredClearance(
                id = "CLR-HOLD-SHORT-PROGRESS",
                domain = ClearanceDomain.GROUND,
                content = ClearanceContent.Single(
                    HoldShortOf(
                        target = TEST_AIRCRAFT,
                        runway = FixtureIds.runway27
                    )
                )
            )
        ).requireResolved()

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.holdShort27,
                entities = setOf(EntityRef.TaxiwayRef(FixtureIds.taxiwayAlpha)),
                onGround = true,
                groundProgress = GroundProgressState(
                    reachedHoldingPoints = setOf(FixtureIds.holdShort27),
                    stoppedOnGround = true
                )
            )
        )

        assertEquals(CompletionResult.NOT_APPLICABLE, evaluation.stepResults.single().result)
        assertEquals(ClearanceStatus.ACTIVE, evaluation.updated.source.status)
    }

    @Test
    fun holdPositionRemainsActiveWhenStoppedOnGround() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-HOLD-POS-PROGRESS",
                domain = ClearanceDomain.GROUND,
                content = ClearanceContent.Single(
                    HoldPosition(TEST_AIRCRAFT)
                )
            )
        ).requireResolved()

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.apronJunction,
                entities = setOf(EntityRef.TaxiwayRef(FixtureIds.taxiwayAlpha)),
                onGround = true,
                groundProgress = GroundProgressState(
                    stoppedOnGround = true
                )
            )
        )

        assertEquals(CompletionResult.NOT_APPLICABLE, evaluation.stepResults.single().result)
        assertEquals(ClearanceStatus.ACTIVE, evaluation.updated.source.status)
    }

    @Test
    fun backtrackCompletesFromTraversedFarEndPoint() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-BACKTRACK-PROGRESS",
                domain = ClearanceDomain.GROUND,
                content = ClearanceContent.Single(
                    BacktrackRunway(
                        target = TEST_AIRCRAFT,
                        runway = FixtureIds.runway09
                    )
                )
            )
        ).requireResolved()

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.runwayMid,
                entities = setOf(EntityRef.RunwayRef(FixtureIds.runway09)),
                onGround = true,
                groundProgress = GroundProgressState(
                    traversedPoints = setOf(FixtureIds.runway27Threshold)
                )
            )
        )

        assertEquals(CompletionResult.COMPLETE, evaluation.stepResults.single().result)
        assertEquals(ClearanceStatus.COMPLETED, evaluation.updated.source.status)
    }

    @Test
    fun taxiCrossHoldShortCompoundCompletesFromGroundProgress() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentPoint = FixtureIds.standPoint
            ),
            clearance = structuredClearance(
                id = "CLR-GROUND-PROGRESS-COMP",
                domain = ClearanceDomain.GROUND,
                content = ClearanceContent.Compound(
                    steps = arrow.core.nonEmptyListOf(
                        TaxiToHoldingPoint(
                            target = TEST_AIRCRAFT,
                            destination = FixtureIds.holdShort27,
                            runway = FixtureIds.runway27,
                            via = listOf(FixtureIds.apronJunction)
                        ),
                        CrossRunway(
                            target = TEST_AIRCRAFT,
                            runway = FixtureIds.runway09
                        ),
                        HoldShortOf(
                            target = TEST_AIRCRAFT,
                            runway = FixtureIds.runway27
                        )
                    )
                )
            )
        ).requireResolved()

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.holdShort27,
                entities = setOf(EntityRef.TaxiwayRef(FixtureIds.taxiwayAlpha)),
                onGround = true,
                groundProgress = GroundProgressState(
                    traversedPoints = setOf(FixtureIds.holdShort27),
                    reachedHoldingPoints = setOf(FixtureIds.holdShort27),
                    crossedRunways = setOf(FixtureIds.runway09),
                    stoppedOnGround = true
                )
            )
        )

        assertEquals(
            listOf(
                CompletionResult.COMPLETE,
                CompletionResult.COMPLETE,
                CompletionResult.NOT_APPLICABLE
            ),
            evaluation.stepResults.map { stepResult -> stepResult.result }
        )
        assertEquals(setOf(0, 1), evaluation.newlyCompletedSteps)
        assertEquals(ClearanceStatus.COMPLETED, evaluation.updated.source.status)
    }
}

private val TEST_AIRCRAFT = AircraftId("TEST123")

private fun structuredClearance(
    id: String,
    domain: ClearanceDomain,
    content: ClearanceContent
) = StructuredClearance(
    id = ClearanceId(id),
    aircraft = TEST_AIRCRAFT,
    content = content,
    domain = domain,
    issuedBy = ControllerId("CTRL-1"),
    issuedAt = TickNumber(1),
    status = ClearanceStatus.ACTIVE
)

private fun <T> arrow.core.Either<*, T>.requireResolved(): T =
    getOrElse { failure -> error("Expected resolved, got $failure") }
