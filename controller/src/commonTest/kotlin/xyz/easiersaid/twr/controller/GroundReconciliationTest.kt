package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.bdi.GroundArrivalStage
import xyz.easiersaid.twr.controller.bdi.GroundDepartureStage
import xyz.easiersaid.twr.controller.procedure.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroundReconciliationTest {

    // ── Departure ───────────────────────────────────────────────────

    private val depStages = listOf(
        GroundDepartureStage.AwaitTaxiRequest,
        GroundDepartureStage.AwaitAtHolding,
        GroundDepartureStage.Complete,
    )

    private val allPositions = listOf(
        GroundPosition.AtStand,
        GroundPosition.Taxiing,
        GroundPosition.AtHoldingPoint,
        GroundPosition.OnRunway,
        GroundPosition.Elsewhere,
    )

    @Test
    fun `ground departure — monotonic`() {
        for (stage in depStages) {
            for (pos in allPositions) {
                val result = reconcileGroundDepartureStage(stage, pos)
                val resultStage = result.stage as GroundDepartureStage
                assertTrue(
                    resultStage.ordinal >= stage.ordinal,
                    "reconcile($stage, $pos) = ${result.stage} regresses",
                )
            }
        }
    }

    @Test
    fun `ground departure — Complete is absorbing`() {
        for (pos in allPositions) {
            val result = reconcileGroundDepartureStage(GroundDepartureStage.Complete, pos)
            assertEquals(GroundDepartureStage.Complete, result.stage)
        }
    }

    @Test
    fun `ground departure — at holding point always advances to at least AwaitAtHolding`() {
        for (stage in depStages.filter { it != GroundDepartureStage.Complete }) {
            val result = reconcileGroundDepartureStage(stage, GroundPosition.AtHoldingPoint)
            val resultStage = result.stage as GroundDepartureStage
            assertTrue(
                resultStage.ordinal >= GroundDepartureStage.AwaitAtHolding.ordinal,
                "AtHoldingPoint from $stage should reach at least AwaitAtHolding, got ${result.stage}",
            )
        }
    }

    @Test
    fun `ground departure — runway incursion from AwaitTaxiRequest is anomalous`() {
        val result = reconcileGroundDepartureStage(
            GroundDepartureStage.AwaitTaxiRequest, GroundPosition.OnRunway,
        )
        assertEquals(TransitionKind.ANOMALOUS, result.transition)
    }

    @Test
    fun `ground departure — full table produces defined results`() {
        val total = depStages.size * allPositions.size
        var tested = 0
        for (stage in depStages) {
            for (pos in allPositions) {
                assertTrue(reconcileGroundDepartureStage(stage, pos).stage is GroundDepartureStage)
                tested++
            }
        }
        assertEquals(total, tested)
    }

    // ── Arrival ─────────────────────────────────────────────────────

    private val arrStages = listOf(
        GroundArrivalStage.TaxiToStand,
        GroundArrivalStage.AwaitParked,
        GroundArrivalStage.Complete,
    )

    @Test
    fun `ground arrival — monotonic`() {
        for (stage in arrStages) {
            for (pos in allPositions) {
                val result = reconcileGroundArrivalStage(stage, pos)
                val resultStage = result.stage as GroundArrivalStage
                assertTrue(
                    resultStage.ordinal >= stage.ordinal,
                    "reconcile($stage, $pos) = ${result.stage} regresses",
                )
            }
        }
    }

    @Test
    fun `ground arrival — Complete is absorbing`() {
        for (pos in allPositions) {
            val result = reconcileGroundArrivalStage(GroundArrivalStage.Complete, pos)
            assertEquals(GroundArrivalStage.Complete, result.stage)
        }
    }

    @Test
    fun `ground arrival — at stand advances to at least AwaitParked`() {
        val result = reconcileGroundArrivalStage(
            GroundArrivalStage.TaxiToStand, GroundPosition.AtStand,
        )
        val resultStage = result.stage as GroundArrivalStage
        assertTrue(resultStage.ordinal >= GroundArrivalStage.AwaitParked.ordinal)
    }

    @Test
    fun `ground arrival — full table produces defined results`() {
        val total = arrStages.size * allPositions.size
        var tested = 0
        for (stage in arrStages) {
            for (pos in allPositions) {
                assertTrue(reconcileGroundArrivalStage(stage, pos).stage is GroundArrivalStage)
                tested++
            }
        }
        assertEquals(total, tested)
    }
}
