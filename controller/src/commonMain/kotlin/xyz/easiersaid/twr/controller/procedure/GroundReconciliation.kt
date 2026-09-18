package xyz.easiersaid.twr.controller.procedure

import xyz.easiersaid.twr.controller.bdi.GroundArrivalStage
import xyz.easiersaid.twr.controller.bdi.GroundDepartureStage

/**
 * Observation-driven stage reconciliation for ground departure taxi.
 *
 * Total over (GroundDepartureStage × GroundPosition).
 */
fun reconcileGroundDepartureStage(
    current: GroundDepartureStage,
    position: GroundPosition,
): ReconciledStage<GroundDepartureStage> = when (current) {
    is GroundDepartureStage.AwaitTaxiRequest -> reconcileAwaitTaxiRequest(current, position)
    is GroundDepartureStage.AwaitPushbackCompletion -> reconcileAwaitPushbackCompletion(current, position)
    is GroundDepartureStage.AwaitAtHolding -> reconcileAwaitAtHolding(current, position)
    is GroundDepartureStage.Complete -> unchanged(current)
}

private fun reconcileAwaitTaxiRequest(
    current: GroundDepartureStage.AwaitTaxiRequest,
    position: GroundPosition,
): ReconciledStage<GroundDepartureStage> = when (position) {
    is GroundPosition.AtStand -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is GroundPosition.Taxiing -> ReconciledStage(current, TransitionKind.EXPECTED)
    // Already at the holding point — reconcile forward.
    is GroundPosition.AtHoldingPoint -> ReconciledStage(
        GroundDepartureStage.AwaitAtHolding, TransitionKind.ADVANCED,
    )
    // On the runway is anomalous for ground (incursion).
    is GroundPosition.OnRunway -> ReconciledStage(
        GroundDepartureStage.AwaitAtHolding, TransitionKind.ANOMALOUS,
    )
    is GroundPosition.Elsewhere -> ReconciledStage(current, TransitionKind.UNCHANGED)
}

private fun reconcileAwaitPushbackCompletion(
    current: GroundDepartureStage.AwaitPushbackCompletion,
    position: GroundPosition,
): ReconciledStage<GroundDepartureStage> = when (position) {
    is GroundPosition.AtStand -> ReconciledStage(current, TransitionKind.EXPECTED)
    is GroundPosition.Taxiing -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is GroundPosition.AtHoldingPoint -> ReconciledStage(
        GroundDepartureStage.AwaitAtHolding, TransitionKind.ADVANCED,
    )
    is GroundPosition.OnRunway -> ReconciledStage(current, TransitionKind.ANOMALOUS)
    is GroundPosition.Elsewhere -> ReconciledStage(current, TransitionKind.UNCHANGED)
}

private fun reconcileAwaitAtHolding(
    current: GroundDepartureStage.AwaitAtHolding,
    position: GroundPosition,
): ReconciledStage<GroundDepartureStage> = when (position) {
    is GroundPosition.AtStand -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is GroundPosition.Taxiing -> ReconciledStage(current, TransitionKind.UNCHANGED)
    // Expected: at the holding point, waiting for tower handoff.
    is GroundPosition.AtHoldingPoint -> ReconciledStage(current, TransitionKind.EXPECTED)
    // On the runway — the pilot went past the hold line. Anomalous from
    // ground's perspective but ground can't do anything about it now —
    // tower will handle the runway incursion.
    is GroundPosition.OnRunway -> ReconciledStage(current, TransitionKind.ANOMALOUS)
    is GroundPosition.Elsewhere -> ReconciledStage(current, TransitionKind.UNCHANGED)
}

private fun unchanged(
    current: GroundDepartureStage,
): ReconciledStage<GroundDepartureStage> = ReconciledStage(current, TransitionKind.UNCHANGED)

/**
 * Observation-driven stage reconciliation for ground arrival taxi.
 *
 * Total over (GroundArrivalStage × GroundPosition).
 */
fun reconcileGroundArrivalStage(
    current: GroundArrivalStage,
    position: GroundPosition,
): ReconciledStage<GroundArrivalStage> = when (current) {
    is GroundArrivalStage.TaxiToStand -> when (position) {
        // Already at the stand — reconcile forward.
        is GroundPosition.AtStand -> ReconciledStage(
            GroundArrivalStage.AwaitParked, TransitionKind.ADVANCED,
        )
        is GroundPosition.Taxiing -> ReconciledStage(current, TransitionKind.EXPECTED)
        is GroundPosition.AtHoldingPoint -> ReconciledStage(current, TransitionKind.UNCHANGED)
        // Still on runway — hasn't vacated yet. Ground waits.
        is GroundPosition.OnRunway -> ReconciledStage(current, TransitionKind.UNCHANGED)
        is GroundPosition.Elsewhere -> ReconciledStage(current, TransitionKind.UNCHANGED)
    }
    is GroundArrivalStage.AwaitParked -> when (position) {
        // Expected: at the stand, parked.
        is GroundPosition.AtStand -> ReconciledStage(current, TransitionKind.EXPECTED)
        is GroundPosition.Taxiing -> ReconciledStage(current, TransitionKind.UNCHANGED)
        is GroundPosition.AtHoldingPoint -> ReconciledStage(current, TransitionKind.UNCHANGED)
        is GroundPosition.OnRunway -> ReconciledStage(current, TransitionKind.UNCHANGED)
        is GroundPosition.Elsewhere -> ReconciledStage(current, TransitionKind.UNCHANGED)
    }
    is GroundArrivalStage.Complete -> when (position) {
        is GroundPosition.AtStand -> ReconciledStage(current, TransitionKind.UNCHANGED)
        is GroundPosition.Taxiing -> ReconciledStage(current, TransitionKind.UNCHANGED)
        is GroundPosition.AtHoldingPoint -> ReconciledStage(current, TransitionKind.UNCHANGED)
        is GroundPosition.OnRunway -> ReconciledStage(current, TransitionKind.UNCHANGED)
        is GroundPosition.Elsewhere -> ReconciledStage(current, TransitionKind.UNCHANGED)
    }
}
