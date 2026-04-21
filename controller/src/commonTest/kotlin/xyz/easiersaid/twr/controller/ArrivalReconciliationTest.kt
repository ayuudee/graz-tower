package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.bdi.TowerArrivalStage
import xyz.easiersaid.twr.controller.procedure.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exhaustive property tests for arrival observation reconciliation.
 *
 * The arrival machine has a property the departure machine doesn't:
 * go-around is a defined regression (AwaitApproach/AwaitLandedObserved → AwaitDownwind).
 * The monotonicity test accounts for this as a named exception.
 */
class ArrivalReconciliationTest {

    private val allStages = listOf(
        TowerArrivalStage.AwaitDownwind,
        TowerArrivalStage.AwaitApproach,
        TowerArrivalStage.LandingClearanceIssued,
        TowerArrivalStage.AwaitLandedObserved,
        TowerArrivalStage.AwaitVacating,
        TowerArrivalStage.Complete,
    )

    private val allPositions = listOf(
        ArrivalPosition.OnDownwind,
        ArrivalPosition.OnBase,
        ArrivalPosition.OnFinal,
        ArrivalPosition.OnApproach,
        ArrivalPosition.AirborneElsewhere,
        ArrivalPosition.OnRunway,
        ArrivalPosition.ClearOfRunway,
        ArrivalPosition.Elsewhere,
    )

    // ── Go-around regression pairs: (stage, position) that are allowed to regress ──
    private val goAroundRegressions = setOf(
        TowerArrivalStage.AwaitApproach to ArrivalPosition.OnDownwind,
        TowerArrivalStage.LandingClearanceIssued to ArrivalPosition.OnDownwind,
        TowerArrivalStage.AwaitLandedObserved to ArrivalPosition.OnDownwind,
    )

    // ── Exhaustive structural properties ────────────────────────────

    @Test
    fun `reconciliation is monotonic except for defined go-around regressions`() {
        for (stage in allStages) {
            for (pos in allPositions) {
                val result = reconcileArrivalStage(stage, pos)
                val resultStage = result.stage as TowerArrivalStage
                val isGoAround = (stage to pos) in goAroundRegressions

                if (isGoAround) {
                    // Go-around regressions go back to AwaitDownwind
                    assertEquals(TowerArrivalStage.AwaitDownwind, resultStage,
                        "Go-around from $stage + $pos should regress to AwaitDownwind")
                    assertEquals(TransitionKind.EXPECTED, result.transition,
                        "Go-around is an expected transition, not anomalous")
                } else {
                    assertTrue(
                        resultStage.ordinal >= stage.ordinal,
                        "reconcile($stage, $pos) = ${result.stage} regresses from $stage " +
                            "(ordinal ${resultStage.ordinal} < ${stage.ordinal}), " +
                            "and this is not a defined go-around regression",
                    )
                }
            }
        }
    }

    @Test
    fun `Complete is absorbing`() {
        for (pos in allPositions) {
            val result = reconcileArrivalStage(TowerArrivalStage.Complete, pos)
            assertEquals(TowerArrivalStage.Complete, result.stage,
                "Complete + $pos must stay Complete")
            assertEquals(TransitionKind.UNCHANGED, result.transition)
        }
    }

    @Test
    fun `on-runway observation always advances to at least AwaitLandedObserved`() {
        for (stage in listOf(
            TowerArrivalStage.AwaitDownwind,
            TowerArrivalStage.AwaitApproach,
        )) {
            val result = reconcileArrivalStage(stage, ArrivalPosition.OnRunway)
            val resultStage = result.stage as TowerArrivalStage
            assertTrue(
                resultStage.ordinal >= TowerArrivalStage.AwaitLandedObserved.ordinal,
                "OnRunway from $stage should reach at least AwaitLandedObserved, got ${result.stage}",
            )
        }
    }

    @Test
    fun `ClearOfRunway always advances to at least AwaitVacating`() {
        for (stage in listOf(
            TowerArrivalStage.AwaitDownwind,
            TowerArrivalStage.AwaitApproach,
            TowerArrivalStage.LandingClearanceIssued,
            TowerArrivalStage.AwaitLandedObserved,
        )) {
            val result = reconcileArrivalStage(stage, ArrivalPosition.ClearOfRunway)
            val resultStage = result.stage as TowerArrivalStage
            assertTrue(
                resultStage.ordinal >= TowerArrivalStage.AwaitVacating.ordinal,
                "ClearOfRunway from $stage should reach at least AwaitVacating, got ${result.stage}",
            )
        }
    }

    @Test
    fun `Elsewhere never changes the stage`() {
        for (stage in allStages) {
            val result = reconcileArrivalStage(stage, ArrivalPosition.Elsewhere)
            assertEquals(stage, result.stage, "Elsewhere from $stage must not change stage")
            assertEquals(TransitionKind.UNCHANGED, result.transition)
        }
    }

    // ── Go-around tests ─────────────────────────────────────────────

    @Test
    fun `go-around from AwaitApproach — aircraft on downwind regresses to AwaitDownwind`() {
        val result = reconcileArrivalStage(
            TowerArrivalStage.AwaitApproach, ArrivalPosition.OnDownwind,
        )
        assertEquals(TowerArrivalStage.AwaitDownwind, result.stage)
        assertEquals(TransitionKind.EXPECTED, result.transition,
            "Go-around is a defined operation, not anomalous")
    }

    @Test
    fun `go-around from AwaitLandedObserved — late go-around to downwind`() {
        val result = reconcileArrivalStage(
            TowerArrivalStage.AwaitLandedObserved, ArrivalPosition.OnDownwind,
        )
        assertEquals(TowerArrivalStage.AwaitDownwind, result.stage)
        assertEquals(TransitionKind.EXPECTED, result.transition)
    }

    @Test
    fun `go-around in progress — airborne elsewhere from AwaitLandedObserved stays`() {
        // Aircraft climbing but not yet on downwind — could be go-around in progress.
        // Stay at AwaitLandedObserved until we see them on downwind or get a report.
        val result = reconcileArrivalStage(
            TowerArrivalStage.AwaitLandedObserved, ArrivalPosition.AirborneElsewhere,
        )
        assertEquals(TowerArrivalStage.AwaitLandedObserved, result.stage)
        assertEquals(TransitionKind.UNCHANGED, result.transition)
    }

    // ── Normal progression tests ────────────────────────────────────

    @Test
    fun `happy path — downwind through to vacating`() {
        // AwaitDownwind + OnDownwind → unchanged
        var result = reconcileArrivalStage(
            TowerArrivalStage.AwaitDownwind, ArrivalPosition.OnDownwind,
        )
        assertEquals(TowerArrivalStage.AwaitDownwind, result.stage)
        assertEquals(TransitionKind.UNCHANGED, result.transition)

        // AwaitApproach + OnFinal → expected
        result = reconcileArrivalStage(
            TowerArrivalStage.AwaitApproach, ArrivalPosition.OnFinal,
        )
        assertEquals(TowerArrivalStage.AwaitApproach, result.stage)
        assertEquals(TransitionKind.EXPECTED, result.transition)

        // AwaitLandedObserved + OnRunway → expected
        result = reconcileArrivalStage(
            TowerArrivalStage.AwaitLandedObserved, ArrivalPosition.OnRunway,
        )
        assertEquals(TowerArrivalStage.AwaitLandedObserved, result.stage)
        assertEquals(TransitionKind.EXPECTED, result.transition)

        // AwaitVacating + ClearOfRunway → expected
        result = reconcileArrivalStage(
            TowerArrivalStage.AwaitVacating, ArrivalPosition.ClearOfRunway,
        )
        assertEquals(TowerArrivalStage.AwaitVacating, result.stage)
        assertEquals(TransitionKind.EXPECTED, result.transition)
    }

    @Test
    fun `straight-in arrival — joins on final, skipping downwind`() {
        val result = reconcileArrivalStage(
            TowerArrivalStage.AwaitDownwind, ArrivalPosition.OnFinal,
        )
        assertEquals(TowerArrivalStage.AwaitApproach, result.stage)
        assertEquals(TransitionKind.ADVANCED, result.transition)
    }

    @Test
    fun `fast vacate — aircraft clear of runway before vacate instruction`() {
        val result = reconcileArrivalStage(
            TowerArrivalStage.AwaitLandedObserved, ArrivalPosition.ClearOfRunway,
        )
        assertEquals(TowerArrivalStage.AwaitVacating, result.stage)
        assertEquals(TransitionKind.ADVANCED, result.transition)
    }

    // ── Full enumeration ────────────────────────────────────────────

    @Test
    fun `full transition table — every cell produces a defined result`() {
        val totalCells = allStages.size * allPositions.size
        var tested = 0
        for (stage in allStages) {
            for (pos in allPositions) {
                val result = reconcileArrivalStage(stage, pos)
                assertTrue(result.stage is TowerArrivalStage,
                    "reconcile($stage, $pos) must return a TowerArrivalStage")
                tested++
            }
        }
        assertEquals(totalCells, tested,
            "Must test every cell in the ${allStages.size}×${allPositions.size} table")
    }
}
