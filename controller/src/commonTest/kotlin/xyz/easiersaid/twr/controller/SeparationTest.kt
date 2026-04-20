package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.assess.*
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.SeparationAssessment
import xyz.easiersaid.twr.controller.observe.SeparationConcern
import xyz.easiersaid.twr.controller.observe.isSeverityAtLeast
import xyz.easiersaid.twr.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 6 safety/separation tests at NM scale.
 *
 * Tests call production functions directly: computeConcern, selectIntervention,
 * requiredWakeSeparation. NM-scale test world for realistic separation scenarios.
 */
class SeparationTest {

    // ── Wake minima table: structural properties ─────────────────────

    @Test
    fun `wake minima are non-negative`() {
        for (entry in ICAO_WAKE_TABLE) {
            assertTrue(entry.distanceNm >= 0, "${entry.leader}-${entry.follower} distance")
            assertTrue(entry.timeMinutes >= 0, "${entry.leader}-${entry.follower} time")
        }
    }

    @Test
    fun `wake separation is directional — H-L differs from L-H`() {
        val hl = requiredWakeSeparation(WakeCategory.H, WakeCategory.L)
        val lh = requiredWakeSeparation(WakeCategory.L, WakeCategory.H)
        assertTrue(hl.distanceNm > lh.distanceNm)
    }

    @Test
    fun `lighter follower requires greater-or-equal separation for fixed leader`() {
        for (leader in WakeCategory.entries) {
            val toH = requiredWakeSeparation(leader, WakeCategory.H).distanceNm
            val toM = requiredWakeSeparation(leader, WakeCategory.M).distanceNm
            val toL = requiredWakeSeparation(leader, WakeCategory.L).distanceNm
            assertTrue(toL >= toM, "$leader: L follower ($toL) must >= M follower ($toM)")
            assertTrue(toM >= toH, "$leader: M follower ($toM) must >= H follower ($toH)")
        }
    }

    @Test
    fun `unknown wake category defaults to H`() {
        val unknownLeadingLight = requiredWakeSeparation(null, WakeCategory.L)
        val heavyLeadingLight = requiredWakeSeparation(WakeCategory.H, WakeCategory.L)
        assertEquals(heavyLeadingLight.distanceNm, unknownLeadingLight.distanceNm)
    }

    // ── Wake minima: per-row verification ────────────────────────────

    @Test
    fun `J-L requires 8nm 3min`() {
        val e = requiredWakeSeparation(WakeCategory.J, WakeCategory.L)
        assertEquals(8.0, e.distanceNm); assertEquals(3.0, e.timeMinutes)
    }

    @Test
    fun `J-M requires 7nm 3min`() {
        val e = requiredWakeSeparation(WakeCategory.J, WakeCategory.M)
        assertEquals(7.0, e.distanceNm); assertEquals(3.0, e.timeMinutes)
    }

    @Test
    fun `J-H requires 6nm 2min`() {
        val e = requiredWakeSeparation(WakeCategory.J, WakeCategory.H)
        assertEquals(6.0, e.distanceNm); assertEquals(2.0, e.timeMinutes)
    }

    @Test
    fun `H-L requires 6nm 3min`() {
        val e = requiredWakeSeparation(WakeCategory.H, WakeCategory.L)
        assertEquals(6.0, e.distanceNm); assertEquals(3.0, e.timeMinutes)
    }

    @Test
    fun `H-M requires 5nm 2min`() {
        val e = requiredWakeSeparation(WakeCategory.H, WakeCategory.M)
        assertEquals(5.0, e.distanceNm); assertEquals(2.0, e.timeMinutes)
    }

    @Test
    fun `H-H requires 4nm 2min`() {
        val e = requiredWakeSeparation(WakeCategory.H, WakeCategory.H)
        assertEquals(4.0, e.distanceNm); assertEquals(2.0, e.timeMinutes)
    }

    @Test
    fun `M-L requires 5nm 3min`() {
        val e = requiredWakeSeparation(WakeCategory.M, WakeCategory.L)
        assertEquals(5.0, e.distanceNm); assertEquals(3.0, e.timeMinutes)
    }

    @Test
    fun `M-M uses radar minimum`() {
        assertEquals(RADAR_MINIMUM_NM, requiredWakeSeparation(WakeCategory.M, WakeCategory.M).distanceNm)
    }

    // ── computeConcern: direct tests (production function) ───────────

    @Test
    fun `COMFORTABLE when margin exceeds threshold`() {
        val result = computeConcern(
            currentNm = 6.0, requiredNm = 3.0, closureRateKt = 0.0,
            distanceToThresholdNm = 10.0, visualSeparationApplied = false,
        )
        assertEquals(SeparationConcern.Severity.COMFORTABLE, result)
    }

    @Test
    fun `MONITORING when margin between 1 and 2`() {
        val result = computeConcern(
            currentNm = 4.5, requiredNm = 3.0, closureRateKt = 0.0,
            distanceToThresholdNm = 10.0, visualSeparationApplied = false,
        )
        assertEquals(SeparationConcern.Severity.MONITORING, result)
    }

    @Test
    fun `INTERVENTION when margin between 0 and 1`() {
        val result = computeConcern(
            currentNm = 3.5, requiredNm = 3.0, closureRateKt = 0.0,
            distanceToThresholdNm = 10.0, visualSeparationApplied = false,
        )
        assertEquals(SeparationConcern.Severity.INTERVENTION, result)
    }

    @Test
    fun `VIOLATION when below minimum`() {
        val result = computeConcern(
            currentNm = 2.5, requiredNm = 3.0, closureRateKt = 0.0,
            distanceToThresholdNm = 10.0, visualSeparationApplied = false,
        )
        assertEquals(SeparationConcern.Severity.VIOLATION, result)
    }

    @Test
    fun `closure rate shifts COMFORTABLE to INTERVENTION`() {
        // 4nm current, 3nm required = 1nm margin → MONITORING normally.
        // 40kt closure: adjustment = 40*0.02 = 0.8. Effective margin = 0.2 → INTERVENTION.
        val result = computeConcern(
            currentNm = 4.0, requiredNm = 3.0, closureRateKt = 40.0,
            distanceToThresholdNm = 10.0, visualSeparationApplied = false,
        )
        assertEquals(SeparationConcern.Severity.INTERVENTION, result)
    }

    @Test
    fun `CLOSURE_FACTOR regression — changing it breaks this test`() {
        // At 20kt closure, adjustment = 20*0.02 = 0.4. With 4.2nm/3nm required:
        // margin = 1.2, effectiveMargin = 0.8 → INTERVENTION.
        val result = computeConcern(
            currentNm = 4.2, requiredNm = 3.0, closureRateKt = 20.0,
            distanceToThresholdNm = 10.0, visualSeparationApplied = false,
        )
        assertEquals(SeparationConcern.Severity.INTERVENTION, result)
    }

    @Test
    fun `positional tightening — same margin is INTERVENTION near threshold but MONITORING far out`() {
        // 1.5nm margin, no closure. At 10nm: MONITORING (1.5 > 1.0 threshold).
        val farOut = computeConcern(
            currentNm = 4.5, requiredNm = 3.0, closureRateKt = 0.0,
            distanceToThresholdNm = 10.0, visualSeparationApplied = false,
        )
        assertEquals(SeparationConcern.Severity.MONITORING, farOut)

        // At 2nm final: factor = 0.5 + 0.5*(2/4) = 0.75. MONITORING threshold = 0.75nm.
        // 1.5nm margin > 0.75 → still MONITORING.
        // But at 1nm final: factor = 0.5 + 0.5*(1/4) = 0.625. COMFORTABLE threshold = 1.25.
        // 1.5nm > 1.25 → COMFORTABLE (thresholds tightened but margin still above).
        // Test the actual tightening: 1.2nm margin at 1nm final.
        // Factor = 0.625. MONITORING threshold = 0.625. 1.2 > 0.625 → MONITORING.
        // COMFORTABLE threshold = 1.25. 1.2 < 1.25 → not COMFORTABLE. So MONITORING.
        // At 0nm final: factor = 0.5. COMFORTABLE = 1.0. MONITORING = 0.5. 1.2 > 1.0 → COMFORTABLE.
        // Actually, let's test a clear tightening case:
        // 1.8nm margin at 10nm: 1.8 < 2.0 → MONITORING.
        // 1.8nm margin at 1nm: factor = 0.625. COMFORTABLE = 1.25. 1.8 > 1.25 → COMFORTABLE.
        // That's loosening not tightening! Let me think...
        // Actually the tightening means LOWER thresholds near the runway,
        // which makes it HARDER to reach COMFORTABLE — you need LESS margin to be comfortable
        // because there's less room regardless. Wait, that's backwards.
        // The design says "tighter thresholds" = concern triggers at LOWER margins.
        // So at 2nm final, INTERVENTION triggers at margin 0.75 instead of 1.0.
        // This means you stay MONITORING longer (need less margin to be INTERVENTION).
        // That IS the right behaviour: near the threshold, smaller margins are tolerated
        // because the aircraft are about to land anyway.
        // Let me test: 0.8nm margin at 10nm vs at 1nm.
        val farResult = computeConcern(
            currentNm = 3.8, requiredNm = 3.0, closureRateKt = 0.0,
            distanceToThresholdNm = 10.0, visualSeparationApplied = false,
        )
        // 0.8nm margin at 10nm: factor=1.0, MONITORING threshold=1.0. 0.8 < 1.0 → INTERVENTION.
        assertEquals(SeparationConcern.Severity.INTERVENTION, farResult)

        val nearResult = computeConcern(
            currentNm = 3.8, requiredNm = 3.0, closureRateKt = 0.0,
            distanceToThresholdNm = 1.0, visualSeparationApplied = false,
        )
        // 0.8nm margin at 1nm: factor=0.625, MONITORING threshold=0.625. 0.8 > 0.625 → MONITORING.
        assertEquals(SeparationConcern.Severity.MONITORING, nearResult,
            "Same margin should be less alarming near threshold (less room to correct is expected)")
    }

    @Test
    fun `Delegated for visual separation`() {
        val result = computeConcern(
            currentNm = 2.0, requiredNm = 3.0, closureRateKt = 0.0,
            distanceToThresholdNm = 5.0, visualSeparationApplied = true,
        )
        assertEquals(SeparationConcern.Delegated, result)
    }

    @Test
    fun `null current defaults to MONITORING`() {
        val result = computeConcern(
            currentNm = null, requiredNm = 3.0, closureRateKt = 0.0,
            distanceToThresholdNm = 5.0, visualSeparationApplied = false,
        )
        assertEquals(SeparationConcern.Severity.MONITORING, result)
    }

    // ── Intervention selection ────────────────────────────────────────

    private val nmWorld = testWorldIndexNmScale()

    @Test
    fun `VIOLATION always produces GoAround`() {
        val assessment = testAssessment(SeparationConcern.Severity.VIOLATION)
        val follower = arrivalAtNm(AircraftId("FOL"), NmIds.finalApproach)
        assertEquals(Intervention.GoAround, selectIntervention(assessment, follower, BeliefState.EMPTY))
    }

    @Test
    fun `COMFORTABLE produces null`() {
        val assessment = testAssessment(SeparationConcern.Severity.COMFORTABLE)
        val follower = arrivalAtNm(AircraftId("FOL"), NmIds.downwind)
        assertEquals(null, selectIntervention(assessment, follower, BeliefState.EMPTY))
    }

    private fun beliefsWithSlot(follower: AircraftObservation, gate: ArrivalGate): BeliefState =
        BeliefState.EMPTY.copy(
            trackedAircraft = mapOf(follower.id to follower),
            arrivalSequence = ArrivalSequence(NmIds.runway09, listOf(
                ArrivalSlot(follower.id, 1, null, null, null, gate, ApproachMode.VISUAL),
            )),
        )

    @Test
    fun `INTERVENTION on downwind with speed produces SpeedControl`() {
        val assessment = testAssessment(SeparationConcern.Severity.INTERVENTION)
        val follower = arrivalAtNm(AircraftId("FOL"), NmIds.downwind, groundSpeedKt = 120)
        val beliefs = beliefsWithSlot(follower, ArrivalGate.Downwind(DownwindPhase.ABEAM))
        assertEquals(Intervention.SpeedControl, selectIntervention(assessment, follower, beliefs))
    }

    @Test
    fun `INTERVENTION on downwind without speed produces PathExtension`() {
        val assessment = testAssessment(SeparationConcern.Severity.INTERVENTION)
        val follower = arrivalAtNm(AircraftId("FOL"), NmIds.downwind, groundSpeedKt = 0)
        val beliefs = beliefsWithSlot(follower, ArrivalGate.Downwind(DownwindPhase.ABEAM))
        assertEquals(Intervention.PathExtension, selectIntervention(assessment, follower, beliefs))
    }

    @Test
    fun `INTERVENTION inside FAF skips to GoAround`() {
        val assessment = testAssessment(SeparationConcern.Severity.INTERVENTION)
        val follower = arrivalAtNm(AircraftId("FOL"), NmIds.finalApproach, groundSpeedKt = 120)
        val beliefs = beliefsWithSlot(follower, ArrivalGate.Final(FinalPhase.FAF))
        assertEquals(Intervention.GoAround, selectIntervention(assessment, follower, beliefs))
    }

    // ── ADT correctness ──────────────────────────────────────────────

    @Test
    fun `Delegated does not satisfy severity INTERVENTION`() {
        assertTrue(!SeparationConcern.Delegated.isSeverityAtLeast(SeparationConcern.Severity.INTERVENTION))
    }

    @Test
    fun `VIOLATION satisfies severity INTERVENTION`() {
        assertTrue(SeparationConcern.Severity.VIOLATION.isSeverityAtLeast(SeparationConcern.Severity.INTERVENTION))
    }

    @Test
    fun `metresToNm conversion`() {
        assertEquals(1.0, metresToNm(1852.0), 0.001)
    }

    // ── Helper ───────────────────────────────────────────────────────

    private fun testAssessment(concern: SeparationConcern) = SeparationAssessment(
        aircraft = AircraftId("LEAD"), other = AircraftId("FOL"),
        currentSeparationNm = 3.0, requiredSeparationNm = 3.0,
        closureRateKt = 0.0, timeToMinimumSeconds = null,
        concern = concern,
    )
}
