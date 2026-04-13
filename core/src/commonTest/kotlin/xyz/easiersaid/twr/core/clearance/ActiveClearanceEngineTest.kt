package xyz.easiersaid.twr.core.clearance

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
import xyz.easiersaid.twr.protocol.ClearedTo
import xyz.easiersaid.twr.protocol.ConditionalClearance
import xyz.easiersaid.twr.protocol.ConditionalPredicate
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.CrossRunway
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RouteSpec
import xyz.easiersaid.twr.protocol.TickNumber
import xyz.easiersaid.twr.protocol.TrafficAction
import xyz.easiersaid.twr.protocol.TrafficRef

class ActiveClearanceEngineTest {

    @Test
    fun conditionalClearanceDoesNotSupersedeUntilActivated() {
        val world = sampleWorld()
        val existing = stageIncomingClearance(
            world.resolveClearance(
                context = ClearanceResolutionContext(
                    aerodromeId = FixtureIds.aerodrome,
                    currentPoint = FixtureIds.holdShort09
                ),
                clearance = structuredClearance(
                    id = "HOLD-SHORT",
                    domain = ClearanceDomain.GROUND,
                    content = ClearanceContent.Single(
                        HoldShortOf(
                            target = TEST_AIRCRAFT,
                            runway = FixtureIds.runway27
                        )
                    )
                )
            ).requireResolved()
        )
        val incoming = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentPoint = FixtureIds.holdShort09
            ),
            clearance = structuredClearance(
                id = "COND-CROSS",
                domain = ClearanceDomain.GROUND,
                content = ClearanceContent.Single(
                    ConditionalClearance(
                        target = TEST_AIRCRAFT,
                        condition = ConditionalPredicate.AfterTraffic(
                            traffic = TrafficRef.ByDescription("landing 737"),
                            action = TrafficAction.LANDING
                        ),
                        instruction = CrossRunway(
                            target = TEST_AIRCRAFT,
                            runway = FixtureIds.runway09
                        )
                    )
                )
            )
        ).requireResolved()

        val admission = admitClearance(listOf(existing), incoming)

        assertEquals(ClearanceStatus.CONDITION_PENDING, admission.incoming.status)
        assertTrue(admission.fullySuperseded.isEmpty())
        assertTrue(
            admission.clearances.any { managed ->
                managed.source.id.value == "HOLD-SHORT" && managed.status == ClearanceStatus.ACTIVE
            }
        )

        val pending = reconcileClearances(
            existing = admission.clearances,
            completionViews = emptyMap(),
            conditionEvaluator = { _, _ -> false }
        )

        assertTrue(
            pending.clearances.any { managed ->
                managed.source.id.value == "COND-CROSS" && managed.status == ClearanceStatus.CONDITION_PENDING
            }
        )

        val activated = reconcileClearances(
            existing = admission.clearances,
            completionViews = emptyMap(),
            conditionEvaluator = { _, _ -> true }
        )

        assertTrue(
            activated.clearances.any { managed ->
                managed.source.id.value == "COND-CROSS" && managed.status == ClearanceStatus.ACTIVE
            }
        )
        assertTrue(
            activated.terminalClearances.any { managed ->
                managed.source.id.value == "HOLD-SHORT" && managed.status == ClearanceStatus.SUPERSEDED
            }
        )
    }

    @Test
    fun suppressedCompoundDomainDoesNotBlockCompletion() {
        val world = sampleWorld()
        val existing = stageIncomingClearance(
            world.resolveClearance(
                context = ClearanceResolutionContext(FixtureIds.aerodrome),
                clearance = structuredClearance(
                    id = "DEP-COMPOUND",
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
        )
        val incoming = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "NEW-FREQ",
                domain = ClearanceDomain.FREQUENCY,
                content = ClearanceContent.Single(
                    ContactFrequency(
                        target = TEST_AIRCRAFT,
                        role = RoleName.TOWER
                    )
                )
            )
        ).requireResolved()

        val admission = admitClearance(listOf(existing), incoming)
        val suppressed = admission.clearances.single { managed -> managed.source.id.value == "DEP-COMPOUND" }

        assertEquals(setOf(ClearanceDomain.FREQUENCY), suppressed.suppressedDomains)

        val reconciled = reconcileClearances(
            existing = admission.clearances,
            completionViews = mapOf(
                TEST_AIRCRAFT to CompletionView(
                    position = FixtureIds.holdFixPoint,
                    entities = emptySet(),
                    onGround = false
                )
            )
        )

        assertTrue(
            reconciled.terminalClearances.any { managed ->
                managed.source.id.value == "DEP-COMPOUND" && managed.status == ClearanceStatus.COMPLETED
            }
        )
        assertTrue(
            reconciled.clearances.any { managed ->
                managed.source.id.value == "NEW-FREQ" && managed.status == ClearanceStatus.ACTIVE
            }
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
