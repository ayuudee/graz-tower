package xyz.easiersaid.twr.core.clearance

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import xyz.easiersaid.twr.core.world.FixtureIds
import xyz.easiersaid.twr.core.world.sampleWorld
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ClearanceContent
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.ClearanceId
import xyz.easiersaid.twr.protocol.ClearanceStatus
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.HoldPosition
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.TickNumber

class GroundMovementCurrentShapeTest {

    @Test
    fun holdPositionResolvesAsPlainPersistentGroundStep() {
        val resolved = sampleWorld().resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-HOLD-POSITION",
                domain = ClearanceDomain.GROUND,
                content = ClearanceContent.Single(
                    HoldPosition(target = TEST_AIRCRAFT)
                )
            )
        ).requireResolved()

        val step = assertIs<ResolvedStep.Plain>(resolved.steps.single())
        assertEquals(ClearanceDomain.GROUND, step.domain)
        assertEquals(xyz.easiersaid.twr.protocol.InstructionTiming.PERSISTENT, step.timing)
        assertEquals(xyz.easiersaid.twr.protocol.CompletionCategory.PERSISTENT, step.completionCategory)
    }

    @Test
    fun holdPositionRemainsActiveUnderCompletionEvaluation() {
        val resolved = sampleWorld().resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-HOLD-POSITION-COMPLETE",
                domain = ClearanceDomain.GROUND,
                content = ClearanceContent.Single(
                    HoldPosition(target = TEST_AIRCRAFT)
                )
            )
        ).requireResolved()

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.apronJunction,
                entities = emptySet(),
                onGround = true
            )
        )

        assertEquals(CompletionResult.NOT_APPLICABLE, evaluation.stepResults.single().result)
        assertEquals(ClearanceStatus.ACTIVE, evaluation.updated.source.status)
        assertEquals(false, evaluation.isComplete)
    }

    @Test
    fun frequencyInstructionDoesNotSupersedeHoldPosition() {
        val world = sampleWorld()
        val existing = stageIncomingClearance(
            world.resolveClearance(
                context = ClearanceResolutionContext(FixtureIds.aerodrome),
                clearance = structuredClearance(
                    id = "CLR-HOLD-POSITION-ACTIVE",
                    domain = ClearanceDomain.GROUND,
                    content = ClearanceContent.Single(
                        HoldPosition(target = TEST_AIRCRAFT)
                    )
                )
            ).requireResolved()
        )
        val incoming = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-HOLD-POSITION-FREQ",
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

        assertTrue(admission.fullySuperseded.isEmpty())
        assertTrue(admission.partiallySuperseded.isEmpty())
        assertTrue(
            admission.clearances.any { managed ->
                managed.source.id.value == "CLR-HOLD-POSITION-ACTIVE" &&
                    managed.status == ClearanceStatus.ACTIVE
            }
        )
    }
}

private val TEST_AIRCRAFT = AircraftId("TEST123")

private fun structuredClearance(
    id: String,
    domain: ClearanceDomain,
    content: ClearanceContent,
    issuedAt: Long = 1L
) = StructuredClearance(
    id = ClearanceId(id),
    aircraft = TEST_AIRCRAFT,
    content = content,
    domain = domain,
    issuedBy = ControllerId("CTRL-1"),
    issuedAt = TickNumber(issuedAt),
    status = ClearanceStatus.ACTIVE
)

private fun <T> arrow.core.Either<*, T>.requireResolved(): T =
    getOrElse { failure -> error("Expected resolved, got $failure") }
