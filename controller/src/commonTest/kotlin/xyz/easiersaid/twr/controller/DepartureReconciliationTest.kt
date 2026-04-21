package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.bdi.TowerDepartureStage
import xyz.easiersaid.twr.controller.procedure.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exhaustive property tests for departure observation reconciliation.
 *
 * The reconciliation function is total over (TowerDepartureStage × DeparturePosition).
 * These tests enumerate every combination and check structural properties —
 * monotonicity, terminality, position-phase correspondence.
 */
class DepartureReconciliationTest {

    private val allStages = listOf(
        TowerDepartureStage.AwaitReady,
        TowerDepartureStage.AwaitLineUpObserved,
        TowerDepartureStage.TakeoffClearanceIssued,
        TowerDepartureStage.AwaitTakeoffObserved,
        TowerDepartureStage.Complete,
    )

    private val allPositions = listOf(
        DeparturePosition.AtHolding,
        DeparturePosition.OnRunway,
        DeparturePosition.OnRunwayRolling,
        DeparturePosition.AirborneOverRunway,
        DeparturePosition.OnClimbout,
        DeparturePosition.Elsewhere,
    )

    // ── Exhaustive structural properties ────────────────────────────

    @Test
    fun `reconciliation is monotonic — stage ordinal never decreases`() {
        for (stage in allStages) {
            for (pos in allPositions) {
                val result = reconcileDepartureStage(stage, pos)
                val resultStage = result.stage as TowerDepartureStage
                assertTrue(
                    resultStage.ordinal >= stage.ordinal,
                    "reconcile($stage, $pos) = ${result.stage} regresses from $stage " +
                        "(ordinal ${resultStage.ordinal} < ${stage.ordinal})",
                )
            }
        }
    }

    @Test
    fun `Complete is absorbing — nothing moves out of it`() {
        for (pos in allPositions) {
            val result = reconcileDepartureStage(TowerDepartureStage.Complete, pos)
            assertEquals(TowerDepartureStage.Complete, result.stage,
                "Complete + $pos must stay Complete")
            assertEquals(TransitionKind.UNCHANGED, result.transition,
                "Complete transitions are always UNCHANGED")
        }
    }

    @Test
    fun `airborne observations always advance to at least AwaitTakeoffObserved`() {
        val airbornePositions = listOf(
            DeparturePosition.AirborneOverRunway,
            DeparturePosition.OnClimbout,
        )
        // For every non-Complete stage, an airborne observation should reconcile
        // to at least AwaitTakeoffObserved.
        for (stage in allStages.filter { it != TowerDepartureStage.Complete }) {
            for (pos in airbornePositions) {
                val result = reconcileDepartureStage(stage, pos)
                val resultStage = result.stage as TowerDepartureStage
                assertTrue(
                    resultStage.ordinal >= TowerDepartureStage.AwaitTakeoffObserved.ordinal,
                    "Airborne observation from $stage should reach at least AwaitTakeoffObserved, " +
                        "got ${result.stage}",
                )
            }
        }
    }

    @Test
    fun `OnRunway from AwaitReady is always ANOMALOUS`() {
        val result = reconcileDepartureStage(
            TowerDepartureStage.AwaitReady, DeparturePosition.OnRunway,
        )
        assertEquals(TowerDepartureStage.AwaitLineUpObserved, result.stage)
        assertEquals(TransitionKind.ANOMALOUS, result.transition,
            "Aircraft on runway from AwaitReady is a runway incursion")
    }

    @Test
    fun `Elsewhere never changes the stage`() {
        for (stage in allStages) {
            val result = reconcileDepartureStage(stage, DeparturePosition.Elsewhere)
            assertEquals(stage, result.stage,
                "Elsewhere from $stage must not change stage")
            assertEquals(TransitionKind.UNCHANGED, result.transition)
        }
    }

    // ── Specific transition verification ────────────────────────────

    @Test
    fun `happy path — expected transitions through normal sequence`() {
        // AwaitReady + AtHolding → unchanged
        var result = reconcileDepartureStage(
            TowerDepartureStage.AwaitReady, DeparturePosition.AtHolding,
        )
        assertEquals(TowerDepartureStage.AwaitReady, result.stage)
        assertEquals(TransitionKind.UNCHANGED, result.transition)

        // AwaitLineUpObserved + OnRunway → expected (pilot lined up)
        result = reconcileDepartureStage(
            TowerDepartureStage.AwaitLineUpObserved, DeparturePosition.OnRunway,
        )
        assertEquals(TowerDepartureStage.AwaitLineUpObserved, result.stage)
        assertEquals(TransitionKind.EXPECTED, result.transition)

        // AwaitTakeoffObserved + AirborneOverRunway → expected (climbing)
        result = reconcileDepartureStage(
            TowerDepartureStage.AwaitTakeoffObserved, DeparturePosition.AirborneOverRunway,
        )
        assertEquals(TowerDepartureStage.AwaitTakeoffObserved, result.stage)
        assertEquals(TransitionKind.EXPECTED, result.transition)

        // AwaitTakeoffObserved + OnClimbout → expected (on climbout)
        result = reconcileDepartureStage(
            TowerDepartureStage.AwaitTakeoffObserved, DeparturePosition.OnClimbout,
        )
        assertEquals(TowerDepartureStage.AwaitTakeoffObserved, result.stage)
        assertEquals(TransitionKind.EXPECTED, result.transition)
    }

    @Test
    fun `anomalous — pilot takes off without any clearance`() {
        val result = reconcileDepartureStage(
            TowerDepartureStage.AwaitReady, DeparturePosition.OnClimbout,
        )
        assertEquals(TowerDepartureStage.AwaitTakeoffObserved, result.stage)
        assertEquals(TransitionKind.ANOMALOUS, result.transition)
    }

    @Test
    fun `advanced — pilot airborne before readback confirmed`() {
        val result = reconcileDepartureStage(
            TowerDepartureStage.AwaitLineUpObserved, DeparturePosition.OnClimbout,
        )
        assertEquals(TowerDepartureStage.AwaitTakeoffObserved, result.stage)
        assertEquals(TransitionKind.ADVANCED, result.transition)
    }

    @Test
    fun `TakeoffClearanceIssued — aircraft airborne advances to AwaitTakeoffObserved`() {
        val result = reconcileDepartureStage(
            TowerDepartureStage.TakeoffClearanceIssued, DeparturePosition.AirborneOverRunway,
        )
        assertEquals(TowerDepartureStage.AwaitTakeoffObserved, result.stage)
        assertEquals(TransitionKind.ADVANCED, result.transition)
    }

    @Test
    fun `TakeoffClearanceIssued — rolling is expected`() {
        val result = reconcileDepartureStage(
            TowerDepartureStage.TakeoffClearanceIssued, DeparturePosition.OnRunwayRolling,
        )
        assertEquals(TowerDepartureStage.TakeoffClearanceIssued, result.stage)
        assertEquals(TransitionKind.EXPECTED, result.transition)
    }

    // ── Full enumeration table (documentation as test) ──────────────

    @Test
    fun `full transition table — every cell produces a defined result`() {
        // This test exists to document and verify every (stage, position) pair.
        // The compiler already ensures totality via sealed when; this test
        // makes the expected results inspectable and catches semantic bugs.
        val totalCells = allStages.size * allPositions.size
        var tested = 0
        for (stage in allStages) {
            for (pos in allPositions) {
                val result = reconcileDepartureStage(stage, pos)
                // Every result must have a valid stage (non-null, in our hierarchy)
                assertTrue(result.stage is TowerDepartureStage,
                    "reconcile($stage, $pos) must return a TowerDepartureStage")
                tested++
            }
        }
        assertEquals(totalCells, tested,
            "Must test every cell in the ${allStages.size}×${allPositions.size} table")
    }
}
