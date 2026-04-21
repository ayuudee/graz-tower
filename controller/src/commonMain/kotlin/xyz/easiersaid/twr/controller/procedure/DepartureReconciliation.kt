package xyz.easiersaid.twr.controller.procedure

import xyz.easiersaid.twr.controller.bdi.TowerDepartureStage

/**
 * Observation-driven stage reconciliation for tower departures.
 *
 * Total over (TowerDepartureStage × DeparturePosition). The compiler
 * enforces that every combination has an explicit branch via nested
 * exhaustive `when` on sealed types.
 *
 * Forward-only by default: the reconciled stage's ordinal is always ≥
 * the current stage's ordinal. Named regression paths for abort scenarios
 * (rejected takeoff, cancel-takeoff compliance) would be added here as
 * explicit transitions — they are not violations of the invariant but
 * documented exceptions to it.
 *
 * The [TransitionKind] tells the action layer whether this transition was
 * expected, advanced (pilot ahead of expectations), or anomalous (possible
 * incursion or unclearanced action).
 */
fun reconcileDepartureStage(
    current: TowerDepartureStage,
    position: DeparturePosition,
): ReconciledStage<TowerDepartureStage> = when (current) {
    is TowerDepartureStage.AwaitReady -> reconcileAwaitReady(position)
    is TowerDepartureStage.AwaitLineUpObserved -> reconcileAwaitLineUp(current, position)
    is TowerDepartureStage.TakeoffClearanceIssued -> reconcileTakeoffIssued(current, position)
    is TowerDepartureStage.AwaitTakeoffObserved -> reconcileAwaitTakeoff(current, position)
    is TowerDepartureStage.Complete -> reconcileComplete(current, position)
}

// ── Per-stage reconciliation ────────────────────────────────────────

private fun reconcileAwaitReady(
    position: DeparturePosition,
): ReconciledStage<TowerDepartureStage> = when (position) {
    // Expected: at the holding point, waiting.
    is DeparturePosition.AtHolding -> ReconciledStage(
        TowerDepartureStage.AwaitReady, TransitionKind.UNCHANGED,
    )
    // Anomalous: on the runway without line-up clearance (incursion).
    // Reconcile forward — they're on the runway, deal with it.
    is DeparturePosition.OnRunway -> ReconciledStage(
        TowerDepartureStage.AwaitLineUpObserved, TransitionKind.ANOMALOUS,
    )
    // Anomalous: rolling on runway without any clearance.
    is DeparturePosition.OnRunwayRolling -> ReconciledStage(
        TowerDepartureStage.AwaitLineUpObserved, TransitionKind.ANOMALOUS,
    )
    // Anomalous: airborne without any clearance. Observation wins.
    is DeparturePosition.AirborneOverRunway -> ReconciledStage(
        TowerDepartureStage.AwaitTakeoffObserved, TransitionKind.ANOMALOUS,
    )
    is DeparturePosition.OnClimbout -> ReconciledStage(
        TowerDepartureStage.AwaitTakeoffObserved, TransitionKind.ANOMALOUS,
    )
    // Somewhere else: stay put, they'll turn up.
    is DeparturePosition.Elsewhere -> ReconciledStage(
        TowerDepartureStage.AwaitReady, TransitionKind.UNCHANGED,
    )
}

private fun reconcileAwaitLineUp(
    current: TowerDepartureStage,
    position: DeparturePosition,
): ReconciledStage<TowerDepartureStage> = when (position) {
    // Still taxiing toward the runway — expected while line-up in progress.
    is DeparturePosition.AtHolding -> ReconciledStage(current, TransitionKind.UNCHANGED)
    // On the runway — expected progression after LUAW.
    is DeparturePosition.OnRunway -> ReconciledStage(current, TransitionKind.EXPECTED)
    // Rolling on the runway — expected (accelerating after ClearedForTakeoff, or
    // anomalous if no takeoff clearance issued yet — but we can't tell from
    // position alone; the action layer checks the coordination state).
    is DeparturePosition.OnRunwayRolling -> ReconciledStage(current, TransitionKind.EXPECTED)
    // Airborne without observed takeoff clearance readback. The pilot executed
    // the clearance (or took off without one). Observation wins.
    is DeparturePosition.AirborneOverRunway -> ReconciledStage(
        TowerDepartureStage.AwaitTakeoffObserved, TransitionKind.ADVANCED,
    )
    is DeparturePosition.OnClimbout -> ReconciledStage(
        TowerDepartureStage.AwaitTakeoffObserved, TransitionKind.ADVANCED,
    )
    // Elsewhere — stay.
    is DeparturePosition.Elsewhere -> ReconciledStage(current, TransitionKind.UNCHANGED)
}

private fun reconcileTakeoffIssued(
    current: TowerDepartureStage,
    position: DeparturePosition,
): ReconciledStage<TowerDepartureStage> = when (position) {
    // Still at holding after clearance? Don't regress. Unusual but not impossible
    // (pilot queried the clearance, hasn't moved yet).
    is DeparturePosition.AtHolding -> ReconciledStage(current, TransitionKind.UNCHANGED)
    // Still on the runway — expected (awaiting readback or about to roll).
    is DeparturePosition.OnRunway -> ReconciledStage(current, TransitionKind.UNCHANGED)
    // Rolling — the pilot is executing.
    is DeparturePosition.OnRunwayRolling -> ReconciledStage(current, TransitionKind.EXPECTED)
    // Airborne — they executed the clearance. Observation confirms takeoff
    // whether or not we received the readback.
    is DeparturePosition.AirborneOverRunway -> ReconciledStage(
        TowerDepartureStage.AwaitTakeoffObserved, TransitionKind.ADVANCED,
    )
    is DeparturePosition.OnClimbout -> ReconciledStage(
        TowerDepartureStage.AwaitTakeoffObserved, TransitionKind.ADVANCED,
    )
    is DeparturePosition.Elsewhere -> ReconciledStage(current, TransitionKind.UNCHANGED)
}

private fun reconcileAwaitTakeoff(
    current: TowerDepartureStage,
    position: DeparturePosition,
): ReconciledStage<TowerDepartureStage> = when (position) {
    // Ground observations when we expected airborne — possible rejected takeoff.
    // Don't regress the stage. The DEP-CANCEL-TAKEOFF rule handles this case.
    // Future: named regression to AwaitLineUpObserved for confirmed rejected takeoff.
    is DeparturePosition.AtHolding -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is DeparturePosition.OnRunway -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is DeparturePosition.OnRunwayRolling -> ReconciledStage(current, TransitionKind.EXPECTED)
    // Expected: climbing.
    is DeparturePosition.AirborneOverRunway -> ReconciledStage(current, TransitionKind.EXPECTED)
    is DeparturePosition.OnClimbout -> ReconciledStage(current, TransitionKind.EXPECTED)
    is DeparturePosition.Elsewhere -> ReconciledStage(current, TransitionKind.UNCHANGED)
}

private fun reconcileComplete(
    current: TowerDepartureStage,
    position: DeparturePosition,
): ReconciledStage<TowerDepartureStage> = when (position) {
    // Terminal state. Nothing moves us out. The commitment will be pruned
    // by reconciliation when responsibility transfers.
    is DeparturePosition.AtHolding -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is DeparturePosition.OnRunway -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is DeparturePosition.OnRunwayRolling -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is DeparturePosition.AirborneOverRunway -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is DeparturePosition.OnClimbout -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is DeparturePosition.Elsewhere -> ReconciledStage(current, TransitionKind.UNCHANGED)
}
