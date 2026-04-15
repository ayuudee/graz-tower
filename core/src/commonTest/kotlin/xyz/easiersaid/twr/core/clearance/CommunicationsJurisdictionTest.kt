package xyz.easiersaid.twr.core.clearance

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import xyz.easiersaid.twr.core.resolution.ResolutionFailureCode
import xyz.easiersaid.twr.core.resolution.ResolutionResult
import xyz.easiersaid.twr.core.world.AerodromeAip
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.FixtureIds
import xyz.easiersaid.twr.core.world.HandoffPoint
import xyz.easiersaid.twr.core.world.HandoffPointKind
import xyz.easiersaid.twr.core.world.HandoffStep
import xyz.easiersaid.twr.core.world.PilotHandoffAction
import xyz.easiersaid.twr.core.world.sampleWorld
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ClearanceContent
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.ClearanceId
import xyz.easiersaid.twr.protocol.ClearanceStatus
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.MonitorFrequency
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SetSquawk
import xyz.easiersaid.twr.protocol.Squawk
import xyz.easiersaid.twr.protocol.TickNumber

class CommunicationsJurisdictionTest {

    @Test
    fun contactFrequencyAttachesPublishedHoldingPointHandoff() {
        val world = worldWithPublishedHandoffs()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentRole = RoleName.GROUND,
                currentPoint = FixtureIds.holdShort09,
                onGround = true
            ),
            clearance = structuredClearance(
                id = "CLR-HANDOFF-CONTACT",
                domain = ClearanceDomain.FREQUENCY,
                content = ClearanceContent.Single(
                    ContactFrequency(
                        target = TEST_AIRCRAFT,
                        role = RoleName.TOWER
                    )
                )
            )
        ).requireResolved()

        val step = resolved.steps.single() as ResolvedStep.FrequencyChange
        val handoff = assertNotNull(step.frequency.publishedHandoff)

        assertEquals(RoleName.GROUND, handoff.from)
        assertEquals(RoleName.TOWER, handoff.to)
        assertEquals(PilotHandoffAction.CONTACT, handoff.pilotAction)
        assertEquals(HandoffPointKind.HOLDING_POINT, handoff.at.kind)
        assertEquals(FixtureIds.holdShort09, handoff.at.point)
    }

    @Test
    fun monitorFrequencyAttachesPublishedBoundaryFixHandoff() {
        val world = worldWithPublishedHandoffs()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentRole = RoleName.TOWER,
                currentFix = FixId("HOLD"),
                onGround = false
            ),
            clearance = structuredClearance(
                id = "CLR-HANDOFF-MONITOR",
                domain = ClearanceDomain.FREQUENCY,
                content = ClearanceContent.Single(
                    MonitorFrequency(
                        target = TEST_AIRCRAFT,
                        role = RoleName.APPROACH
                    )
                )
            )
        ).requireResolved()

        val step = resolved.steps.single() as ResolvedStep.FrequencyChange
        val handoff = assertNotNull(step.frequency.publishedHandoff)

        assertEquals(RoleName.TOWER, handoff.from)
        assertEquals(RoleName.APPROACH, handoff.to)
        assertEquals(PilotHandoffAction.MONITOR, handoff.pilotAction)
        assertEquals(HandoffPointKind.BOUNDARY_FIX, handoff.at.kind)
        assertEquals(FixId("HOLD"), handoff.at.fix)
    }

    @Test
    fun contactFrequencyAttachesPublishedAirborneHandoff() {
        val world = worldWithPublishedHandoffs()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentRole = RoleName.APPROACH,
                onGround = false
            ),
            clearance = structuredClearance(
                id = "CLR-HANDOFF-AIRBORNE",
                domain = ClearanceDomain.FREQUENCY,
                content = ClearanceContent.Single(
                    ContactFrequency(
                        target = TEST_AIRCRAFT,
                        role = RoleName.TOWER
                    )
                )
            )
        ).requireResolved()

        val step = resolved.steps.single() as ResolvedStep.FrequencyChange
        val handoff = assertNotNull(step.frequency.publishedHandoff)

        assertEquals(RoleName.APPROACH, handoff.from)
        assertEquals(RoleName.TOWER, handoff.to)
        assertEquals(PilotHandoffAction.CONTACT, handoff.pilotAction)
        assertEquals(HandoffPointKind.AIRBORNE, handoff.at.kind)
    }

    @Test
    fun contactFrequencyDoesNotCompleteBeforePublishedHandoffPoint() {
        val world = worldWithPublishedHandoffs()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentRole = RoleName.GROUND,
                currentPoint = FixtureIds.holdShort09,
                onGround = true
            ),
            clearance = structuredClearance(
                id = "CLR-HANDOFF-CONTACT-PENDING",
                domain = ClearanceDomain.FREQUENCY,
                content = ClearanceContent.Single(
                    ContactFrequency(
                        target = TEST_AIRCRAFT,
                        role = RoleName.TOWER
                    )
                )
            )
        ).requireResolved()

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.apronJunction,
                entities = emptySet(),
                onGround = true,
                radioState = RadioState(
                    currentRole = RoleName.TOWER,
                    currentFrequency = Frequency.unsafe("118.500"),
                    lastContactRole = RoleName.TOWER
                )
            )
        )

        assertEquals(listOf(CompletionResult.NOT_COMPLETE), evaluation.stepResults.map { it.result })
        assertEquals(ClearanceStatus.ACTIVE, evaluation.updated.source.status)
    }

    @Test
    fun contactFrequencyCompletesAtPublishedHandoffPointAfterContact() {
        val world = worldWithPublishedHandoffs()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentRole = RoleName.GROUND,
                currentPoint = FixtureIds.holdShort09,
                onGround = true
            ),
            clearance = structuredClearance(
                id = "CLR-HANDOFF-CONTACT-COMPLETE",
                domain = ClearanceDomain.FREQUENCY,
                content = ClearanceContent.Single(
                    ContactFrequency(
                        target = TEST_AIRCRAFT,
                        role = RoleName.TOWER
                    )
                )
            )
        ).requireResolved()

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.holdShort09,
                entities = emptySet(),
                onGround = true,
                radioState = RadioState(
                    currentRole = RoleName.TOWER,
                    currentFrequency = Frequency.unsafe("118.500"),
                    lastContactRole = RoleName.TOWER
                )
            )
        )

        assertEquals(listOf(CompletionResult.COMPLETE), evaluation.stepResults.map { it.result })
        assertEquals(ClearanceStatus.COMPLETED, evaluation.updated.source.status)
    }

    @Test
    fun monitorFrequencyCompletesAtPublishedBoundaryFixAfterObservedMonitor() {
        val world = worldWithPublishedHandoffs()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentRole = RoleName.TOWER,
                currentFix = FixId("HOLD"),
                onGround = false
            ),
            clearance = structuredClearance(
                id = "CLR-HANDOFF-MONITOR-COMPLETE",
                domain = ClearanceDomain.FREQUENCY,
                content = ClearanceContent.Single(
                    MonitorFrequency(
                        target = TEST_AIRCRAFT,
                        role = RoleName.APPROACH
                    )
                )
            )
        ).requireResolved()

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.holdFixPoint,
                entities = emptySet(),
                reachedFixes = setOf(FixId("HOLD")),
                onGround = false,
                radioState = RadioState(
                    currentRole = RoleName.APPROACH,
                    currentFrequency = Frequency.unsafe("120.100"),
                    lastContactRole = RoleName.APPROACH
                )
            )
        )

        assertEquals(listOf(CompletionResult.COMPLETE), evaluation.stepResults.map { it.result })
        assertEquals(ClearanceStatus.COMPLETED, evaluation.updated.source.status)
    }

    @Test
    fun setSquawkContactCompoundWaitsForPublishedHandoffThenCompletes() {
        val world = worldWithPublishedHandoffs()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentRole = RoleName.GROUND,
                currentPoint = FixtureIds.holdShort09,
                onGround = true
            ),
            clearance = structuredClearance(
                id = "CLR-SQK-HANDOFF-COMPOUND",
                domain = ClearanceDomain.SQUAWK,
                content = ClearanceContent.Compound(
                    steps = arrow.core.nonEmptyListOf(
                        SetSquawk(
                            target = TEST_AIRCRAFT,
                            squawk = Squawk.unsafe(4672)
                        ),
                        ContactFrequency(
                            target = TEST_AIRCRAFT,
                            role = RoleName.TOWER
                        )
                    )
                )
            )
        ).requireResolved()

        val beforePoint = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.apronJunction,
                entities = emptySet(),
                onGround = true,
                radioState = RadioState(
                    currentRole = RoleName.TOWER,
                    currentFrequency = Frequency.unsafe("118.500"),
                    lastContactRole = RoleName.TOWER
                )
            )
        )
        assertEquals(
            listOf(CompletionResult.COMPLETE, CompletionResult.NOT_COMPLETE),
            beforePoint.stepResults.map { it.result }
        )
        assertEquals(ClearanceStatus.ACTIVE, beforePoint.updated.source.status)

        val afterPoint = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.holdShort09,
                entities = emptySet(),
                onGround = true,
                radioState = RadioState(
                    currentRole = RoleName.TOWER,
                    currentFrequency = Frequency.unsafe("118.500"),
                    lastContactRole = RoleName.TOWER
                )
            )
        )
        assertEquals(
            listOf(CompletionResult.COMPLETE, CompletionResult.COMPLETE),
            afterPoint.stepResults.map { it.result }
        )
        assertEquals(ClearanceStatus.COMPLETED, afterPoint.updated.source.status)
    }

    @Test
    fun ambiguousPublishedHandoffFailsWithoutDisambiguatingContext() {
        val world = worldWithAmbiguousGroundToTowerContactHandoffs()

        val result = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentRole = RoleName.GROUND,
                onGround = true
            ),
            clearance = structuredClearance(
                id = "CLR-HANDOFF-AMBIGUOUS",
                domain = ClearanceDomain.FREQUENCY,
                content = ClearanceContent.Single(
                    ContactFrequency(
                        target = TEST_AIRCRAFT,
                        role = RoleName.TOWER
                    )
                )
            )
        )

        val failure = (result as arrow.core.Either.Left).value
        assertEquals(ResolutionFailureCode.AMBIGUOUS_PUBLISHED_HANDOFF, failure.code)
    }
}

private val TEST_AIRCRAFT = AircraftId("TEST123")
private val HOLD_FIX = FixId("HOLD")

private fun worldWithPublishedHandoffs(): AviationWorld {
    val world = sampleWorld()
    val aerodrome = world.aerodromes.getValue(FixtureIds.aerodrome)
    val handoffs = listOf(
        HandoffStep(
            from = RoleName.GROUND,
            to = RoleName.TOWER,
            at = HandoffPoint(
                kind = HandoffPointKind.HOLDING_POINT,
                point = FixtureIds.holdShort09
            ),
            pilotAction = PilotHandoffAction.CONTACT
        ),
        HandoffStep(
            from = RoleName.TOWER,
            to = RoleName.APPROACH,
            at = HandoffPoint(
                kind = HandoffPointKind.BOUNDARY_FIX,
                fix = HOLD_FIX
            ),
            pilotAction = PilotHandoffAction.MONITOR
        ),
        HandoffStep(
            from = RoleName.APPROACH,
            to = RoleName.TOWER,
            at = HandoffPoint(kind = HandoffPointKind.AIRBORNE),
            pilotAction = PilotHandoffAction.CONTACT
        )
    )
    val mutatedAerodrome = aerodrome.copy(
        aip = AerodromeAip(
            atisFrequency = aerodrome.aip.atisFrequency,
            handoffSequence = handoffs,
            activeRunwaySelection = aerodrome.aip.activeRunwaySelection,
            noiseAbatement = aerodrome.aip.noiseAbatement,
            specialInstructions = aerodrome.aip.specialInstructions
        )
    )
    return world.copy(
        aerodromes = world.aerodromes + (FixtureIds.aerodrome to mutatedAerodrome)
    )
}

private fun worldWithAmbiguousGroundToTowerContactHandoffs(): AviationWorld {
    val world = worldWithPublishedHandoffs()
    val aerodrome = world.aerodromes.getValue(FixtureIds.aerodrome)
    val ambiguousAerodrome = aerodrome.copy(
        aip = aerodrome.aip.copy(
            handoffSequence = aerodrome.aip.handoffSequence + HandoffStep(
                from = RoleName.GROUND,
                to = RoleName.TOWER,
                at = HandoffPoint(
                    kind = HandoffPointKind.HOLDING_POINT,
                    point = FixtureIds.holdShort27
                ),
                pilotAction = PilotHandoffAction.CONTACT
            )
        )
    )
    return world.copy(
        aerodromes = world.aerodromes + (FixtureIds.aerodrome to ambiguousAerodrome)
    )
}

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

private fun <T> ResolutionResult<T>.requireResolved(): T =
    getOrElse { failure -> error("Expected resolved, got ${failure.code}: ${failure.message}") }
