package xyz.easiersaid.twr.core.clearance

import arrow.core.getOrElse
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
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.MonitorFrequency
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SetSquawk
import xyz.easiersaid.twr.protocol.Squawk
import xyz.easiersaid.twr.protocol.TickNumber
import xyz.easiersaid.twr.protocol.TransponderMode
import xyz.easiersaid.twr.protocol.ConfirmSquawk
import xyz.easiersaid.twr.protocol.StopSquawk

class CommunicationsCurrentShapeTest {

    @Test
    fun setSquawkContactCompoundStaysActiveUntilContact() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-SQK-CONTACT",
                domain = ClearanceDomain.SQUAWK,
                content = ClearanceContent.Compound(
                    steps = arrow.core.nonEmptyListOf(
                        SetSquawk(
                            target = TEST_AIRCRAFT,
                            squawk = Squawk.unsafe(4672)
                        ),
                        ContactFrequency(
                            target = TEST_AIRCRAFT,
                            role = RoleName.TOWER,
                            frequency = Frequency.unsafe("118.500")
                        )
                    )
                )
            )
        ).requireResolved()

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.holdFixPoint,
                entities = emptySet(),
                onGround = false
            )
        )

        assertEquals(
            listOf(
                CompletionResult.COMPLETE,
                CompletionResult.NOT_COMPLETE
            ),
            evaluation.stepResults.map { it.result }
        )
        assertEquals(setOf(0), evaluation.completedSteps)
        assertEquals(ClearanceStatus.ACTIVE, evaluation.updated.source.status)
    }

    @Test
    fun setSquawkContactCompoundCompletesOnContact() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-SQK-CONTACT-COMPLETE",
                domain = ClearanceDomain.SQUAWK,
                content = ClearanceContent.Compound(
                    steps = arrow.core.nonEmptyListOf(
                        SetSquawk(
                            target = TEST_AIRCRAFT,
                            squawk = Squawk.unsafe(4672)
                        ),
                        ContactFrequency(
                            target = TEST_AIRCRAFT,
                            role = RoleName.TOWER,
                            frequency = Frequency.unsafe("118.500")
                        )
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
                    currentFrequency = Frequency.unsafe("118.500"),
                    lastContactRole = RoleName.TOWER
                )
            )
        )

        assertEquals(
            listOf(
                CompletionResult.COMPLETE,
                CompletionResult.COMPLETE
            ),
            evaluation.stepResults.map { it.result }
        )
        assertEquals(setOf(0, 1), evaluation.completedSteps)
        assertEquals(ClearanceStatus.COMPLETED, evaluation.updated.source.status)
    }

    @Test
    fun incomingFrequencyPartiallySupersedesMixedCommunicationsCompound() {
        val world = sampleWorld()
        val existing = stageIncomingClearance(
            world.resolveClearance(
                context = ClearanceResolutionContext(FixtureIds.aerodrome),
                clearance = structuredClearance(
                    id = "CLR-SQK-CONTACT-SUPERSEDE",
                    domain = ClearanceDomain.SQUAWK,
                    content = ClearanceContent.Compound(
                        steps = arrow.core.nonEmptyListOf(
                            SetSquawk(
                                target = TEST_AIRCRAFT,
                                squawk = Squawk.unsafe(4672)
                            ),
                            ContactFrequency(
                                target = TEST_AIRCRAFT,
                                role = RoleName.TOWER,
                                frequency = Frequency.unsafe("118.500")
                            )
                        )
                    )
                )
            ).requireResolved()
        )
        val incoming = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-CONTACT-NEW",
                domain = ClearanceDomain.FREQUENCY,
                content = ClearanceContent.Single(
                    ContactFrequency(
                        target = TEST_AIRCRAFT,
                        role = RoleName.TOWER,
                        frequency = Frequency.unsafe("118.700")
                    )
                )
            )
        ).requireResolved()

        val admission = admitClearance(listOf(existing), incoming)
        val old = admission.clearances.single { it.source.id.value == "CLR-SQK-CONTACT-SUPERSEDE" }

        assertEquals(setOf(ClearanceDomain.FREQUENCY), old.suppressedDomains)
        assertTrue(admission.fullySuperseded.isEmpty())
        assertEquals(listOf("CLR-SQK-CONTACT-SUPERSEDE"), admission.partiallySuperseded.map { it.source.id.value })
    }

    @Test
    fun setSquawkContactCompoundTerminalizesAfterFrequencySupersession() {
        val world = sampleWorld()
        val existing = stageIncomingClearance(
            world.resolveClearance(
                context = ClearanceResolutionContext(FixtureIds.aerodrome),
                clearance = structuredClearance(
                    id = "CLR-SQK-CONTACT-TERMINAL",
                    domain = ClearanceDomain.SQUAWK,
                    content = ClearanceContent.Compound(
                        steps = arrow.core.nonEmptyListOf(
                            SetSquawk(
                                target = TEST_AIRCRAFT,
                                squawk = Squawk.unsafe(4672)
                            ),
                            ContactFrequency(
                                target = TEST_AIRCRAFT,
                                role = RoleName.TOWER,
                                frequency = Frequency.unsafe("118.500")
                            )
                        )
                    )
                )
            ).requireResolved()
        )
        val incoming = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-CONTACT-NEW-TERMINAL",
                domain = ClearanceDomain.FREQUENCY,
                content = ClearanceContent.Single(
                    ContactFrequency(
                        target = TEST_AIRCRAFT,
                        role = RoleName.TOWER,
                        frequency = Frequency.unsafe("118.700")
                    )
                )
            )
        ).requireResolved()

        val admission = admitClearance(listOf(existing), incoming)
        val reconciliation = reconcileClearances(
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
            reconciliation.clearances.any { it.source.id.value == "CLR-CONTACT-NEW-TERMINAL" }
        )
        assertTrue(
            reconciliation.terminalClearances.any {
                it.source.id.value == "CLR-SQK-CONTACT-TERMINAL" &&
                    it.status == ClearanceStatus.COMPLETED
            }
        )
    }

    @Test
    fun contactConfirmSquawkCompoundStaysActiveUntilContact() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-CONTACT-CONFIRM",
                domain = ClearanceDomain.FREQUENCY,
                content = ClearanceContent.Compound(
                    steps = arrow.core.nonEmptyListOf(
                        ContactFrequency(
                            target = TEST_AIRCRAFT,
                            role = RoleName.TOWER,
                            frequency = Frequency.unsafe("118.500")
                        ),
                        ConfirmSquawk(
                            target = TEST_AIRCRAFT,
                            squawk = Squawk.unsafe(4672)
                        )
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
                transponderCode = Squawk.unsafe(4672)
            )
        )

        assertEquals(
            listOf(
                CompletionResult.NOT_COMPLETE,
                CompletionResult.COMPLETE
            ),
            evaluation.stepResults.map { it.result }
        )
        assertEquals(setOf(1), evaluation.completedSteps)
        assertEquals(ClearanceStatus.ACTIVE, evaluation.updated.source.status)
    }

    @Test
    fun incomingSquawkPartiallySupersedesMixedCommunicationsCompound() {
        val world = sampleWorld()
        val existing = stageIncomingClearance(
            world.resolveClearance(
                context = ClearanceResolutionContext(FixtureIds.aerodrome),
                clearance = structuredClearance(
                    id = "CLR-CONTACT-CONFIRM-SUPERSEDE",
                    domain = ClearanceDomain.FREQUENCY,
                    content = ClearanceContent.Compound(
                        steps = arrow.core.nonEmptyListOf(
                            ContactFrequency(
                                target = TEST_AIRCRAFT,
                                role = RoleName.TOWER,
                                frequency = Frequency.unsafe("118.500")
                            ),
                            ConfirmSquawk(
                                target = TEST_AIRCRAFT,
                                squawk = Squawk.unsafe(4672)
                            )
                        )
                    )
                )
            ).requireResolved()
        )
        val incoming = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-SQK-NEW",
                domain = ClearanceDomain.SQUAWK,
                content = ClearanceContent.Single(
                    SetSquawk(
                        target = TEST_AIRCRAFT,
                        squawk = Squawk.unsafe(7000)
                    )
                )
            )
        ).requireResolved()

        val admission = admitClearance(listOf(existing), incoming)
        val old = admission.clearances.single { it.source.id.value == "CLR-CONTACT-CONFIRM-SUPERSEDE" }

        assertEquals(setOf(ClearanceDomain.SQUAWK), old.suppressedDomains)
        assertTrue(admission.fullySuperseded.isEmpty())
        assertEquals(listOf("CLR-CONTACT-CONFIRM-SUPERSEDE"), admission.partiallySuperseded.map { it.source.id.value })
    }

    @Test
    fun contactConfirmSquawkCompoundCompletesOnContactAfterTailSuppressed() {
        val world = sampleWorld()
        val existing = stageIncomingClearance(
            world.resolveClearance(
                context = ClearanceResolutionContext(FixtureIds.aerodrome),
                clearance = structuredClearance(
                    id = "CLR-CONTACT-CONFIRM-TERMINAL",
                    domain = ClearanceDomain.FREQUENCY,
                    content = ClearanceContent.Compound(
                        steps = arrow.core.nonEmptyListOf(
                            ContactFrequency(
                                target = TEST_AIRCRAFT,
                                role = RoleName.TOWER,
                                frequency = Frequency.unsafe("118.500")
                            ),
                            ConfirmSquawk(
                                target = TEST_AIRCRAFT,
                                squawk = Squawk.unsafe(4672)
                            )
                        )
                    )
                )
            ).requireResolved()
        )
        val incoming = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-SQK-NEW-TERMINAL",
                domain = ClearanceDomain.SQUAWK,
                content = ClearanceContent.Single(
                    SetSquawk(
                        target = TEST_AIRCRAFT,
                        squawk = Squawk.unsafe(7000)
                    )
                )
            )
        ).requireResolved()

        val admission = admitClearance(listOf(existing), incoming)
        val reconciliation = reconcileClearances(
            existing = admission.clearances,
            completionViews = mapOf(
                TEST_AIRCRAFT to CompletionView(
                    position = FixtureIds.holdFixPoint,
                    entities = emptySet(),
                    onGround = false,
                    radioState = RadioState(
                        currentRole = RoleName.TOWER,
                        currentFrequency = Frequency.unsafe("118.500"),
                        lastContactRole = RoleName.TOWER
                    )
                )
            )
        )

        assertTrue(reconciliation.clearances.isEmpty())
        assertEquals(
            setOf("CLR-CONTACT-CONFIRM-TERMINAL", "CLR-SQK-NEW-TERMINAL"),
            reconciliation.terminalClearances.map { it.source.id.value }.toSet()
        )
    }

    @Test
    fun monitorStopSquawkCompoundCompletesOnObservedMonitorAndModeExit() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-MONITOR-STOP",
                domain = ClearanceDomain.FREQUENCY,
                content = ClearanceContent.Compound(
                    steps = arrow.core.nonEmptyListOf(
                        MonitorFrequency(
                            target = TEST_AIRCRAFT,
                            role = RoleName.APPROACH,
                            frequency = Frequency.unsafe("120.100")
                        ),
                        StopSquawk(
                            target = TEST_AIRCRAFT,
                            mode = TransponderMode.CHARLIE
                        )
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
                    currentRole = RoleName.APPROACH,
                    currentFrequency = Frequency.unsafe("120.100"),
                    lastContactRole = RoleName.APPROACH
                ),
                transponderMode = TransponderMode.NORMAL
            )
        )

        assertEquals(
            listOf(
                CompletionResult.COMPLETE,
                CompletionResult.COMPLETE
            ),
            evaluation.stepResults.map { it.result }
        )
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

private fun <T> ResolutionResult<T>.requireResolved(): T =
    getOrElse { failure -> error("Expected resolved, got ${failure.code}: ${failure.message}") }
