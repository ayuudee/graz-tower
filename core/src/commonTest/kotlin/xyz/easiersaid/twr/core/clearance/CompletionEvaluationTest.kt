package xyz.easiersaid.twr.core.clearance

import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.easiersaid.twr.core.resolution.ResolutionResult
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.FixtureIds
import xyz.easiersaid.twr.core.world.sampleWorld
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ClearanceContent
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.ClearanceId
import xyz.easiersaid.twr.protocol.ClearanceStatus
import xyz.easiersaid.twr.protocol.ClearedTo
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.CrossRunway
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.Knots
import xyz.easiersaid.twr.protocol.MaintainSpeed
import xyz.easiersaid.twr.protocol.ReduceSpeedTo
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RouteSpec
import xyz.easiersaid.twr.protocol.Speed
import xyz.easiersaid.twr.protocol.TaxiTo
import xyz.easiersaid.twr.protocol.TickNumber

class CompletionEvaluationTest {

    @Test
    fun compoundTaxiCompletionAdvancesCompletedSteps() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentPoint = FixtureIds.standPoint
            ),
            clearance = structuredClearance(
                id = "CLR-TAXI-COMP",
                domain = ClearanceDomain.GROUND,
                content = ClearanceContent.Compound(
                    steps = listOf(
                        TaxiTo(
                            target = TEST_AIRCRAFT,
                            destination = FixtureIds.holdShort27,
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
                transitionHistory = setOf(EntityRef.RunwayRef(FixtureIds.runway09))
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
        assertEquals(setOf(0, 1), evaluation.completedSteps)
        assertEquals(ClearanceStatus.COMPLETED, evaluation.updated.source.status)
        assertEquals(true, evaluation.isComplete)
    }

    @Test
    fun mixedRouteFrequencyCompletionUpdatesCompoundState() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-ROUTE-FREQ",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Compound(
                    steps = listOf(
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

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.holdFixPoint,
                entities = setOf(EntityRef.HoldingPatternRef(FixtureIds.hold)),
                onGround = false,
                transitionHistory = emptySet(),
                radioState = RadioState(
                    currentRole = RoleName.APPROACH,
                    currentFrequency = Frequency("120.100"),
                    lastContactRole = RoleName.APPROACH
                )
            )
        )

        assertEquals(
            listOf(
                CompletionResult.COMPLETE,
                CompletionResult.COMPLETE
            ),
            evaluation.stepResults.map { stepResult -> stepResult.result }
        )
        assertEquals(setOf(0, 1), evaluation.completedSteps)
        assertEquals(ClearanceStatus.COMPLETED, evaluation.updated.source.status)
    }

    @Test
    fun speedCompletionDistinguishesMaintainReduceAndIncreaseSemantics() {
        val world = sampleWorld()

        val maintain = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-MAINTAIN-SPEED",
                domain = ClearanceDomain.SPEED,
                content = ClearanceContent.Single(
                    MaintainSpeed(
                        target = TEST_AIRCRAFT,
                        speed = Speed.InKnots(Knots(180))
                    )
                )
            )
        ).requireResolved()
        val reduce = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-REDUCE-SPEED",
                domain = ClearanceDomain.SPEED,
                content = ClearanceContent.Single(
                    ReduceSpeedTo(
                        target = TEST_AIRCRAFT,
                        speed = Speed.InKnots(Knots(180))
                    )
                )
            )
        ).requireResolved()

        val fastView = CompletionView(
            position = FixtureIds.holdFixPoint,
            entities = emptySet(),
            speed = Speed.InKnots(Knots(220)),
            onGround = false
        )
        val targetView = fastView.copy(speed = Speed.InKnots(Knots(180)))
        val slowView = fastView.copy(speed = Speed.InKnots(Knots(170)))

        assertEquals(
            CompletionResult.NOT_COMPLETE,
            evaluateCompletion(maintain, fastView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(maintain, targetView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.NOT_COMPLETE,
            evaluateCompletion(reduce, fastView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(reduce, targetView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(reduce, slowView).stepResults.single().result
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

private fun ResolutionResult<ResolvedClearance>.requireResolved(): ResolvedClearance =
    when (this) {
        is ResolutionResult.Resolved -> value
        is ResolutionResult.Unresolved -> error("Expected resolved clearance, got ${failure.code}: ${failure.message}")
    }
