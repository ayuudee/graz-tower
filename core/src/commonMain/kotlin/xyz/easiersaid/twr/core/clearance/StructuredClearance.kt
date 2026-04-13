package xyz.easiersaid.twr.core.clearance

import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.ApproachComponent
import xyz.easiersaid.twr.protocol.ClearanceContent
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.ClearanceId
import xyz.easiersaid.twr.protocol.ClearanceStatus
import xyz.easiersaid.twr.protocol.ConditionalPredicate
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.Speed
import xyz.easiersaid.twr.protocol.Squawk
import xyz.easiersaid.twr.protocol.TickNumber
import xyz.easiersaid.twr.protocol.TransponderMode

data class StructuredClearance(
    val id: ClearanceId,
    val aircraft: AircraftId,
    val content: ClearanceContent,
    val domain: ClearanceDomain,
    val issuedBy: ControllerId,
    val issuedAt: TickNumber,
    val status: ClearanceStatus,
    val condition: ConditionalPredicate? = null
)

data class RadioState(
    val currentRole: RoleName? = null,
    val currentFrequency: Frequency? = null,
    val lastContactRole: RoleName? = null
)

enum class CompletionResult {
    COMPLETE,
    NOT_COMPLETE,
    NOT_APPLICABLE
}

data class CompletionView(
    val position: PointId,
    val entities: Set<EntityRef>,
    val altitude: Level? = null,
    val speed: Speed? = null,
    val onGround: Boolean,
    val transitionHistory: Set<EntityRef> = emptySet(),
    val establishedApproachComponents: Set<ApproachComponent> = emptySet(),
    val radioState: RadioState = RadioState(),
    val transponderCode: Squawk? = null,
    val transponderMode: TransponderMode? = null,
    val transponderIdentActive: Boolean = false
)

fun isCompoundComplete(content: ClearanceContent.Compound, isPersistent: (AtcInstruction) -> Boolean): Boolean =
    content.steps.withIndex()
        .filterNot { (_, step) -> isPersistent(step) }
        .all { (index, _) -> index in content.completedSteps }
