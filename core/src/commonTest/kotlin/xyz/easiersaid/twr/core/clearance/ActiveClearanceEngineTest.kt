package xyz.easiersaid.twr.core.clearance

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import xyz.easiersaid.twr.core.resolution.ResolutionResult
import xyz.easiersaid.twr.core.world.FixtureIds
import xyz.easiersaid.twr.core.world.sampleWorld
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ApproachType
import xyz.easiersaid.twr.protocol.ClearanceContent
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.ClearanceId
import xyz.easiersaid.twr.protocol.ClearanceStatus
import xyz.easiersaid.twr.protocol.ClearedApproach
import xyz.easiersaid.twr.protocol.ClearedTo
import xyz.easiersaid.twr.protocol.ClearedToEnterControlZone
import xyz.easiersaid.twr.protocol.ConditionalClearance
import xyz.easiersaid.twr.protocol.ConditionalPredicate
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.CrossRunway
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.RemainOutsideControlledAirspace
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

    @Test
    fun supersededPendingClearanceIsNotReactivatedLaterInSamePass() {
        val world = sampleWorld()

        val firstPending = admitClearance(
            existing = emptyList(),
            incoming = world.resolveClearance(
                context = ClearanceResolutionContext(
                    aerodromeId = FixtureIds.aerodrome,
                    currentPoint = FixtureIds.holdShort09
                ),
                clearance = structuredClearance(
                    id = "COND-CROSS",
                    domain = ClearanceDomain.GROUND,
                    issuedAt = 1,
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
        )

        val secondPending = admitClearance(
            existing = firstPending.clearances,
            incoming = world.resolveClearance(
                context = ClearanceResolutionContext(
                    aerodromeId = FixtureIds.aerodrome,
                    currentPoint = FixtureIds.holdShort09
                ),
                clearance = structuredClearance(
                    id = "COND-HOLD",
                    domain = ClearanceDomain.GROUND,
                    issuedAt = 2,
                    content = ClearanceContent.Single(
                        ConditionalClearance(
                            target = TEST_AIRCRAFT,
                            condition = ConditionalPredicate.AfterTraffic(
                                traffic = TrafficRef.ByDescription("landing 737"),
                                action = TrafficAction.LANDING
                            ),
                            instruction = HoldShortOf(
                                target = TEST_AIRCRAFT,
                                runway = FixtureIds.runway27
                            )
                        )
                    )
                )
            ).requireResolved()
        )

        val reconciled = reconcileClearances(
            existing = secondPending.clearances,
            completionViews = emptyMap(),
            conditionEvaluator = { _, _ -> true }
        )

        assertEquals(1, reconciled.activatedClearances.size)
        assertEquals("COND-CROSS", reconciled.activatedClearances.single().after.source.id.value)
        assertTrue(
            reconciled.terminalClearances.any { managed ->
                managed.source.id.value == "COND-HOLD" && managed.status == ClearanceStatus.SUPERSEDED
            }
        )
        assertTrue(
            reconciled.clearances.none { managed ->
                managed.source.id.value == "COND-HOLD" && managed.status == ClearanceStatus.ACTIVE
            }
        )
    }

    @Test
    fun activatedConditionalClearancesRespectIssuedOrder() {
        val world = sampleWorld()

        val firstPending = admitClearance(
            existing = emptyList(),
            incoming = world.resolveClearance(
                context = ClearanceResolutionContext(
                    aerodromeId = FixtureIds.aerodrome,
                    currentPoint = FixtureIds.holdShort09
                ),
                clearance = structuredClearance(
                    id = "COND-CROSS",
                    domain = ClearanceDomain.GROUND,
                    issuedAt = 1,
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
        )

        val secondPending = admitClearance(
            existing = firstPending.clearances,
            incoming = world.resolveClearance(
                context = ClearanceResolutionContext(
                    aerodromeId = FixtureIds.aerodrome,
                    currentPoint = FixtureIds.holdShort09
                ),
                clearance = structuredClearance(
                    id = "COND-LUP",
                    domain = ClearanceDomain.RUNWAY,
                    issuedAt = 2,
                    content = ClearanceContent.Single(
                        ConditionalClearance(
                            target = TEST_AIRCRAFT,
                            condition = ConditionalPredicate.AfterTraffic(
                                traffic = TrafficRef.ByDescription("landing 737"),
                                action = TrafficAction.LANDING
                            ),
                            instruction = LineUpAndWait(
                                target = TEST_AIRCRAFT,
                                runway = FixtureIds.runway09
                            )
                        )
                    )
                )
            ).requireResolved()
        )

        val reconciled = reconcileClearances(
            existing = secondPending.clearances,
            completionViews = emptyMap(),
            conditionEvaluator = { _, _ -> true }
        )

        assertEquals(
            listOf("COND-CROSS", "COND-LUP"),
            reconciled.activatedClearances.map { activation -> activation.after.source.id.value }
        )
        assertTrue(
            reconciled.clearances.any { managed ->
                managed.source.id.value == "COND-CROSS" && managed.status == ClearanceStatus.ACTIVE
            }
        )
        assertTrue(
            reconciled.clearances.any { managed ->
                managed.source.id.value == "COND-LUP" && managed.status == ClearanceStatus.ACTIVE
            }
        )
    }

    @Test
    fun frequencySupersessionLeavesWorldBackedRemainOutsideCompoundActive() {
        val world = sampleWorld()
        val existing = stageIncomingClearance(
            world.resolveClearance(
                context = ClearanceResolutionContext(FixtureIds.aerodrome),
                clearance = structuredClearance(
                    id = "ROCA-FREQ",
                    domain = ClearanceDomain.ROUTE,
                    content = ClearanceContent.Compound(
                        steps = arrow.core.nonEmptyListOf(
                            RemainOutsideControlledAirspace(
                                target = TEST_AIRCRAFT,
                                airspace = FixtureIds.airspace
                            ),
                            ContactFrequency(
                                target = TEST_AIRCRAFT,
                                role = RoleName.TOWER
                            )
                        )
                    )
                )
            ).requireResolved()
        )
        val incoming = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "NEW-FREQ-ROCA",
                domain = ClearanceDomain.FREQUENCY,
                content = ClearanceContent.Single(
                    ContactFrequency(
                        target = TEST_AIRCRAFT,
                        role = RoleName.APPROACH
                    )
                )
            )
        ).requireResolved()

        val admission = admitClearance(listOf(existing), incoming)

        val reconciled = reconcileClearances(
            existing = admission.clearances,
            completionViews = mapOf(
                TEST_AIRCRAFT to CompletionView(
                    position = xyz.easiersaid.twr.protocol.PointId("OUTSIDE-CTR"),
                    entities = emptySet(),
                    onGround = false
                )
            )
        )

        assertTrue(
            reconciled.clearances.any { managed ->
                managed.source.id.value == "ROCA-FREQ" &&
                    managed.status == ClearanceStatus.ACTIVE &&
                    managed.suppressedDomains == setOf(ClearanceDomain.FREQUENCY)
            }
        )
        assertTrue(
            reconciled.terminalClearances.none { managed ->
                managed.source.id.value == "ROCA-FREQ"
            }
        )
    }

    @Test
    fun frequencySupersessionCompletesWorldBackedControlZoneCompound() {
        val world = sampleWorld()
        val existing = stageIncomingClearance(
            world.resolveClearance(
                context = ClearanceResolutionContext(FixtureIds.aerodrome),
                clearance = structuredClearance(
                    id = "ENTER-CTR-FREQ",
                    domain = ClearanceDomain.ROUTE,
                    content = ClearanceContent.Compound(
                        steps = arrow.core.nonEmptyListOf(
                            ClearedToEnterControlZone(
                                target = TEST_AIRCRAFT,
                                airspace = FixtureIds.airspace
                            ),
                            ContactFrequency(
                                target = TEST_AIRCRAFT,
                                role = RoleName.TOWER
                            )
                        )
                    )
                )
            ).requireResolved()
        )
        val incoming = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "NEW-FREQ-ENTER",
                domain = ClearanceDomain.FREQUENCY,
                content = ClearanceContent.Single(
                    ContactFrequency(
                        target = TEST_AIRCRAFT,
                        role = RoleName.APPROACH
                    )
                )
            )
        ).requireResolved()

        val admission = admitClearance(listOf(existing), incoming)
        val reconciled = reconcileClearances(
            existing = admission.clearances,
            completionViews = mapOf(
                TEST_AIRCRAFT to CompletionView(
                    position = FixtureIds.runway09Threshold,
                    entities = emptySet(),
                    onGround = false
                )
            )
        )

        assertTrue(
            reconciled.terminalClearances.any { managed ->
                managed.source.id.value == "ENTER-CTR-FREQ" &&
                    managed.status == ClearanceStatus.COMPLETED
            }
        )
        assertTrue(
            reconciled.clearances.any { managed ->
                managed.source.id.value == "NEW-FREQ-ENTER" && managed.status == ClearanceStatus.ACTIVE
            }
        )
    }

    @Test
    fun worldBackedControlZonePermissionTerminalsAfterExitTransition() {
        val world = sampleWorld()
        val existing = stageIncomingClearance(
            world.resolveClearance(
                context = ClearanceResolutionContext(FixtureIds.aerodrome),
                clearance = structuredClearance(
                    id = "ENTER-CTR-SINGLE",
                    domain = ClearanceDomain.ROUTE,
                    content = ClearanceContent.Single(
                        ClearedToEnterControlZone(
                            target = TEST_AIRCRAFT,
                            airspace = FixtureIds.airspace
                        )
                    )
                )
            ).requireResolved()
        )

        val reconciled = reconcileClearances(
            existing = listOf(existing),
            completionViews = mapOf(
                TEST_AIRCRAFT to CompletionView(
                    position = xyz.easiersaid.twr.protocol.PointId("OUTSIDE-CTR"),
                    entities = emptySet(),
                    onGround = false,
                    transitionHistory = setOf(xyz.easiersaid.twr.core.world.EntityRef.AirspaceVolumeRef(FixtureIds.airspace))
                )
            )
        )

        assertTrue(
            reconciled.terminalClearances.any { managed ->
                managed.source.id.value == "ENTER-CTR-SINGLE" &&
                    managed.status == ClearanceStatus.COMPLETED
            }
        )
    }

    @Test
    fun worldBackedApproachTerminalsOnMissedApproachHold() {
        val world = sampleWorld()
        val existing = stageIncomingClearance(
            world.resolveClearance(
                context = ClearanceResolutionContext(FixtureIds.aerodrome),
                clearance = structuredClearance(
                    id = "APPROACH-WORLDBACKED",
                    domain = ClearanceDomain.ROUTE,
                    content = ClearanceContent.Single(
                        ClearedApproach(
                            target = TEST_AIRCRAFT,
                            approachType = ApproachType.ILS,
                            runway = FixtureIds.runway09
                        )
                    )
                )
            ).requireResolved()
        )

        val reconciled = reconcileClearances(
            existing = listOf(existing),
            completionViews = mapOf(
                TEST_AIRCRAFT to CompletionView(
                    position = FixtureIds.holdFixPoint,
                    entities = setOf(xyz.easiersaid.twr.core.world.EntityRef.HoldingPatternRef(FixtureIds.hold)),
                    onGround = false
                )
            )
        )

        assertTrue(
            reconciled.terminalClearances.any { managed ->
                managed.source.id.value == "APPROACH-WORLDBACKED" &&
                    managed.status == ClearanceStatus.COMPLETED
            }
        )
    }
}

private val TEST_AIRCRAFT = AircraftId("TEST123")

private fun structuredClearance(
    id: String,
    domain: ClearanceDomain,
    content: ClearanceContent,
    status: ClearanceStatus = ClearanceStatus.ACTIVE,
    issuedAt: Long = 1
): StructuredClearance =
    StructuredClearance(
        id = ClearanceId(id),
        aircraft = TEST_AIRCRAFT,
        content = content,
        domain = domain,
        issuedBy = ControllerId("CTRL-1"),
        issuedAt = TickNumber(issuedAt),
        status = status
    )

private fun <T> ResolutionResult<T>.requireResolved(): T =
    getOrElse { failure -> error("Expected resolved, got ${failure.code}: ${failure.message}") }
