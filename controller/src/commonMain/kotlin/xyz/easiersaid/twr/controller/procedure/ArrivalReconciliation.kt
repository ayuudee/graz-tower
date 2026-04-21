package xyz.easiersaid.twr.controller.procedure

import xyz.easiersaid.twr.controller.bdi.TowerArrivalStage

/**
 * Observation-driven stage reconciliation for tower arrivals.
 *
 * Total over (TowerArrivalStage × ArrivalPosition). Forward-only by default,
 * with a named regression path for go-arounds: an aircraft observed on
 * downwind/base after having been on approach/final regresses to AwaitDownwind.
 * This is standard operations (ICAO 4444 §7.10.2), not an anomaly.
 *
 * The go-around regression is the key difference from departure reconciliation:
 * arrivals can legitimately go backward in the procedure. The TransitionKind
 * distinguishes this from anomalous regressions.
 */
fun reconcileArrivalStage(
    current: TowerArrivalStage,
    position: ArrivalPosition,
): ReconciledStage = when (current) {
    is TowerArrivalStage.AwaitDownwind -> reconcileAwaitDownwind(current, position)
    is TowerArrivalStage.AwaitApproach -> reconcileAwaitApproach(current, position)
    is TowerArrivalStage.LandingClearanceIssued -> reconcileLandingIssued(current, position)
    is TowerArrivalStage.AwaitLandedObserved -> reconcileAwaitLanded(current, position)
    is TowerArrivalStage.AwaitVacating -> reconcileAwaitVacating(current, position)
    is TowerArrivalStage.Complete -> reconcileArrivalComplete(current, position)
}

// ── Per-stage reconciliation ────────────────────────────────────────

private fun reconcileAwaitDownwind(
    current: TowerArrivalStage,
    position: ArrivalPosition,
): ReconciledStage = when (position) {
    // Expected: on the downwind leg.
    is ArrivalPosition.OnDownwind -> ReconciledStage(current, TransitionKind.UNCHANGED)
    // Advanced: already on base — skipped downwind report or joined on base.
    is ArrivalPosition.OnBase -> ReconciledStage(
        TowerArrivalStage.AwaitApproach, TransitionKind.ADVANCED,
    )
    // Advanced: already on final — straight-in or late join.
    is ArrivalPosition.OnFinal -> ReconciledStage(
        TowerArrivalStage.AwaitApproach, TransitionKind.ADVANCED,
    )
    // Advanced: on an approach procedure.
    is ArrivalPosition.OnApproach -> ReconciledStage(
        TowerArrivalStage.AwaitApproach, TransitionKind.ADVANCED,
    )
    // Advanced: already on the runway — touchdown without us seeing approach.
    is ArrivalPosition.OnRunway -> ReconciledStage(
        TowerArrivalStage.AwaitLandedObserved, TransitionKind.ADVANCED,
    )
    // Advanced: already clear of the runway.
    is ArrivalPosition.ClearOfRunway -> ReconciledStage(
        TowerArrivalStage.AwaitVacating, TransitionKind.ADVANCED,
    )
    // Airborne elsewhere — still in the circuit, not yet on a recognisable leg.
    is ArrivalPosition.AirborneElsewhere -> ReconciledStage(current, TransitionKind.UNCHANGED)
    // Elsewhere (ground, not on runway, not clear-of-runway) — unexpected.
    is ArrivalPosition.Elsewhere -> ReconciledStage(current, TransitionKind.UNCHANGED)
}

private fun reconcileAwaitApproach(
    current: TowerArrivalStage,
    position: ArrivalPosition,
): ReconciledStage = when (position) {
    // Go-around: aircraft observed back on downwind after being on approach.
    // This is a defined regression path, not an anomaly.
    is ArrivalPosition.OnDownwind -> ReconciledStage(
        TowerArrivalStage.AwaitDownwind, TransitionKind.EXPECTED,
    )
    // Expected: on base or final, being sequenced.
    is ArrivalPosition.OnBase -> ReconciledStage(current, TransitionKind.EXPECTED)
    is ArrivalPosition.OnFinal -> ReconciledStage(current, TransitionKind.EXPECTED)
    is ArrivalPosition.OnApproach -> ReconciledStage(current, TransitionKind.EXPECTED)
    // Advanced: touched down.
    is ArrivalPosition.OnRunway -> ReconciledStage(
        TowerArrivalStage.AwaitLandedObserved, TransitionKind.ADVANCED,
    )
    // Advanced: already vacating or clear.
    is ArrivalPosition.ClearOfRunway -> ReconciledStage(
        TowerArrivalStage.AwaitVacating, TransitionKind.ADVANCED,
    )
    // Airborne elsewhere — possibly going around (climbing, not yet on downwind).
    // Stay at AwaitApproach; the go-around interrupt or next position update
    // will clarify.
    is ArrivalPosition.AirborneElsewhere -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is ArrivalPosition.Elsewhere -> ReconciledStage(current, TransitionKind.UNCHANGED)
}

private fun reconcileLandingIssued(
    current: TowerArrivalStage,
    position: ArrivalPosition,
): ReconciledStage = when (position) {
    // Go-around: aircraft observed back on downwind after landing clearance issued.
    is ArrivalPosition.OnDownwind -> ReconciledStage(
        TowerArrivalStage.AwaitDownwind, TransitionKind.EXPECTED,
    )
    // Still on approach/final — expected, waiting for readback and touchdown.
    is ArrivalPosition.OnBase -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is ArrivalPosition.OnFinal -> ReconciledStage(current, TransitionKind.EXPECTED)
    is ArrivalPosition.OnApproach -> ReconciledStage(current, TransitionKind.UNCHANGED)
    // Touched down — observation advances past readback confirmation.
    is ArrivalPosition.OnRunway -> ReconciledStage(
        TowerArrivalStage.AwaitLandedObserved, TransitionKind.ADVANCED,
    )
    is ArrivalPosition.ClearOfRunway -> ReconciledStage(
        TowerArrivalStage.AwaitVacating, TransitionKind.ADVANCED,
    )
    // Go-around in progress.
    is ArrivalPosition.AirborneElsewhere -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is ArrivalPosition.Elsewhere -> ReconciledStage(current, TransitionKind.UNCHANGED)
}

private fun reconcileAwaitLanded(
    current: TowerArrivalStage,
    position: ArrivalPosition,
): ReconciledStage = when (position) {
    // Go-around: aircraft observed back on downwind after landing clearance.
    // Late go-around — pilot decided not to land. Regression to AwaitDownwind.
    is ArrivalPosition.OnDownwind -> ReconciledStage(
        TowerArrivalStage.AwaitDownwind, TransitionKind.EXPECTED,
    )
    // Go-around in progress: aircraft climbing, not yet on downwind.
    // Could be a late go-around from short final. The position is ambiguous —
    // stay at AwaitLandedObserved until we see them on downwind or the
    // go-around interrupt fires from a pilot report.
    is ArrivalPosition.AirborneElsewhere -> ReconciledStage(current, TransitionKind.UNCHANGED)
    // Still on approach/final — expected, waiting for touchdown.
    is ArrivalPosition.OnBase -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is ArrivalPosition.OnFinal -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is ArrivalPosition.OnApproach -> ReconciledStage(current, TransitionKind.UNCHANGED)
    // Expected: on the runway (landed).
    is ArrivalPosition.OnRunway -> ReconciledStage(current, TransitionKind.EXPECTED)
    // Advanced: already clear of the runway (fast vacate or touch-and-go lift-off).
    is ArrivalPosition.ClearOfRunway -> ReconciledStage(
        TowerArrivalStage.AwaitVacating, TransitionKind.ADVANCED,
    )
    is ArrivalPosition.Elsewhere -> ReconciledStage(current, TransitionKind.UNCHANGED)
}

private fun reconcileAwaitVacating(
    current: TowerArrivalStage,
    position: ArrivalPosition,
): ReconciledStage = when (position) {
    // Still on the runway — vacate instruction issued but not yet off.
    is ArrivalPosition.OnRunway -> ReconciledStage(current, TransitionKind.UNCHANGED)
    // Expected: clear of the runway, awaiting handoff to ground.
    is ArrivalPosition.ClearOfRunway -> ReconciledStage(current, TransitionKind.EXPECTED)
    // Airborne observations are unexpected at this stage — possible touch-and-go
    // lift-off that should have been caught by the T&G rules. Don't regress.
    is ArrivalPosition.OnDownwind -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is ArrivalPosition.OnBase -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is ArrivalPosition.OnFinal -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is ArrivalPosition.OnApproach -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is ArrivalPosition.AirborneElsewhere -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is ArrivalPosition.Elsewhere -> ReconciledStage(current, TransitionKind.UNCHANGED)
}

private fun reconcileArrivalComplete(
    current: TowerArrivalStage,
    position: ArrivalPosition,
): ReconciledStage = when (position) {
    is ArrivalPosition.OnDownwind -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is ArrivalPosition.OnBase -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is ArrivalPosition.OnFinal -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is ArrivalPosition.OnApproach -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is ArrivalPosition.AirborneElsewhere -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is ArrivalPosition.OnRunway -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is ArrivalPosition.ClearOfRunway -> ReconciledStage(current, TransitionKind.UNCHANGED)
    is ArrivalPosition.Elsewhere -> ReconciledStage(current, TransitionKind.UNCHANGED)
}
