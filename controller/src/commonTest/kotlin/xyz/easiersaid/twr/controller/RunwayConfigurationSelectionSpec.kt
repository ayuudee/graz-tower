package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.assess.RunwayConfigurationFailure
import xyz.easiersaid.twr.controller.assess.selectRunwayConfiguration
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.protocol.WindReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Pass 15 (D-AUDIT.7 closure) — `selectRunwayConfiguration` selection
 * logic. Each row exercises a distinct branch.
 *
 * Doctrine: ICAO Doc 4444 §7.2 (runway-in-use selection).
 */
class RunwayConfigurationSelectionSpec {

    @Test
    fun `synthetic parallel runways select all into-wind parallels deterministically per Doc 4444 sec7dot2`() {
        // Synthetic fixture (post-impl test review M1 fold): LOWG world-
        // candidate JSON does not yet have all parallels; the parallel-
        // runway selection branch is exercised against a synthetic shape
        // until D-WORLD.1 lands.
        val r16C = RunwayId("16C")
        val r16L = RunwayId("16L")
        val r16R = RunwayId("16R")
        val r28 = RunwayId("28")
        val r04C = RunwayId("04C")
        val wind = Wind.unsafe(directionDegrees = 160, speedKnots = 8)

        val result = selectRunwayConfiguration(
            runways = listOf(r28, r04C, r16L, r16R, r16C),
            wind = WindReport.Available(wind),
        )
        val cfg = result.fold({ fail("expected Right, got Left($it)") }, { it })
        // All three parallels are within ±90° of 160; 28 (heading 280) is
        // 120° off — outside bucket. 04 (heading 40) is also 120° off.
        // Within the bucket, 16C/16L/16R all have heading-diff = 0;
        // tie-break by RunwayId.value lex order: 16C < 16L < 16R.
        assertEquals(
            listOf(r16C, r16L, r16R),
            cfg.arrivals,
            "Doc 4444 §7.2: into-wind parallels selected, deterministic lex tie-break",
        )
        assertEquals(cfg.arrivals, cfg.departures, "single-mode config: arrivals == departures (D-AUDIT.7.II-FOLLOWUP)")
    }

    @Test
    fun `WindReport NotReported returns Left WindNotReported`() {
        val result = selectRunwayConfiguration(
            runways = listOf(RunwayId("16C")),
            wind = WindReport.NotReported,
        )
        assertTrue(result.isLeft(), "no wind report → Left")
        assertEquals(
            RunwayConfigurationFailure.WindNotReported,
            result.swap().getOrNull(),
            "Left distinguishes the two failure modes",
        )
    }

    @Test
    fun `crosswind-only runways return Left NoRunwayInBucket per Doc 4444 sec7dot2`() {
        // Wind 090; runway 18 is 90° off (heading 180 vs wind 090 = 90° diff,
        // exactly on the bucket boundary which is INCLUDED). Wind 090 against
        // runway 36 is 90° off but again on-boundary. Wind 100 against
        // runway 19 (heading 190) is 90° off too. Use wind 010 vs runway 28
        // (heading 280): diff is min(|10-280|, 360-|10-280|) = min(270, 90) = 90 — boundary.
        // Use wind 000 vs runway 09 only (heading 90): diff = 90 — boundary, included.
        // For NoRunwayInBucket we need ALL runways outside ±90°. Easiest:
        // wind 360 (north) and runway 18C (heading 180, diff = 180 — outside).
        val wind = Wind.unsafe(directionDegrees = 360, speedKnots = 5)
        val result = selectRunwayConfiguration(
            runways = listOf(RunwayId("18")),
            wind = WindReport.Available(wind),
        )
        assertTrue(result.isLeft(), "all runways outside ±90° → Left(NoRunwayInBucket)")
        val failure = result.swap().getOrNull()
        assertTrue(
            failure is RunwayConfigurationFailure.NoRunwayInBucket,
            "Left carries the wind for diagnostic surface: got $failure",
        )
    }

    @Test
    fun `empty runway set returns Left NoRunwaysPublished`() {
        val result = selectRunwayConfiguration(
            runways = emptyList(),
            wind = WindReport.Available(Wind.unsafe(160, 8)),
        )
        assertEquals(
            RunwayConfigurationFailure.NoRunwaysPublished,
            result.swap().getOrNull(),
            "empty published-runways set → Left",
        )
    }
}
