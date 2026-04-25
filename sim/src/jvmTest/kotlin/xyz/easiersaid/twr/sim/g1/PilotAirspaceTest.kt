package xyz.easiersaid.twr.sim.g1

import arrow.core.None
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import xyz.easiersaid.twr.core.world.AirspaceClass
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.migration.world.WorldCandidateDocument
import xyz.easiersaid.twr.migration.world.WorldCandidateLoader
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AirspaceVolumeId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.sim.PilotAirspace

/**
 * G1.2 — load-bearing test for [PilotAirspace.frequencyChangeTriggerAt].
 *
 * The single behavioural property G1 needs from this module is: when
 * the pilot is approaching a published TMA-entry waypoint (e.g. PETOV
 * for LJMB) from outside the TMA, the trigger fires with the correct
 * target role and frequency. When the pilot is already inside the TMA,
 * already on the target frequency, or far from any trigger waypoint,
 * the trigger does not fire.
 *
 * Per test-review M5: no structural-only "trigger returns Some" or
 * "monotonicity in airspace class" tests — both are implementation
 * details. This test exercises the published procedure: LOWG → FIS →
 * TMA Maribor entry via PETOV.
 */
class PilotAirspaceTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `trigger fires near PETOV from outside TMA Maribor with APP role and frequency`() {
        val ctx = loadCtx()
        val triggers = listOf(LJMB_TMA_TRIGGER)

        // Pick a synthetic position outside TMA Maribor 1, within trigger leadNm of PETOV.
        // PETOV is a TMA boundary entry point; we step outward from the TMA centroid
        // through PETOV by 2 NM to land in airspace outside the TMA.
        val petov = ctx.worldIndex.positions.getValue(PETOV)
        val tmaCentroid = pointInsideTmaMaribor1(ctx.world, ctx.worldIndex)
        val outwardX = petov.xMeters - tmaCentroid.xMeters
        val outwardY = petov.yMeters - tmaCentroid.yMeters
        val outwardLen = kotlin.math.hypot(outwardX, outwardY)
        val stepM = 2.0 * 1852.0  // 2 NM step beyond PETOV away from TMA centre
        val nearPetov = Position(
            xMeters = petov.xMeters + outwardX / outwardLen * stepM,
            yMeters = petov.yMeters + outwardY / outwardLen * stepM,
        )

        // Confirm the synthetic point is *outside* TMA Maribor 1 (precondition).
        val current = PilotAirspace.currentVolume(ctx.world, ctx.worldIndex, nearPetov)
        assertTrue(
            current.fold(
                ifLeft = { true },  // outside all volumes ⇒ outside TMA, OK
                ifRight = { v -> v.id != AirspaceVolumeId("LJMB_OPENAIR_TMA_MARIBOR_1") },
            ),
            "Test precondition: synthetic position must be outside TMA Maribor 1; got $current.",
        )

        // Pilot is currently on the LOWG-FIS handover frequency (Wien Information),
        // not yet on LJMB APP — trigger is eligible.
        val onWienInfo = Frequency.unsafe("124.400")
        val fired = PilotAirspace.frequencyChangeTriggerAt(
            world = ctx.world,
            worldIndex = ctx.worldIndex,
            point = nearPetov,
            currentFrequency = onWienInfo,
            triggers = triggers,
        )

        assertTrue(fired.isSome(), "Trigger must fire when pilot approaches PETOV from outside TMA.")
        val trigger = fired.getOrNull()!!
        assertEquals(RoleName.APPROACH, trigger.targetRole)
        assertEquals(Frequency.unsafe("134.305"), trigger.targetFrequency)
        assertEquals(AerodromeId("LJMB"), trigger.targetAerodrome)
    }

    @Test
    fun `trigger does not fire when pilot is already inside TMA Maribor`() {
        val ctx = loadCtx()
        val triggers = listOf(LJMB_TMA_TRIGGER)

        // PETOV itself is a member point of TMA Maribor 1 — i.e., the
        // boundary touches it. Pick a point well inside the volume by
        // taking the centroid of the boundary ring.
        val tmaInside = pointInsideTmaMaribor1(ctx.world, ctx.worldIndex)

        // Verify the synthetic point really is inside TMA Maribor 1.
        val current = PilotAirspace.currentVolume(ctx.world, ctx.worldIndex, tmaInside).getOrNull()
        assertTrue(
            current?.id == AirspaceVolumeId("LJMB_OPENAIR_TMA_MARIBOR_1") ||
                current?.airspaceClass == AirspaceClass.D,
            "Test precondition: centroid must lie inside TMA Maribor 1 (got ${current?.id}).",
        )

        val onWienInfo = Frequency.unsafe("124.400")
        val fired = PilotAirspace.frequencyChangeTriggerAt(
            world = ctx.world,
            worldIndex = ctx.worldIndex,
            point = tmaInside,
            currentFrequency = onWienInfo,
            triggers = triggers,
        )
        assertEquals(None, fired, "Trigger must not fire when already inside the target volume.")
    }

    @Test
    fun `trigger fires again after re-crossing the TMA boundary outbound — reversal`() {
        // Test-review round-3 finding: §4 G1.5 explicitly committed to
        // reverse / re-cross coverage. Geometric reversal at the predicate
        // level: pilot transiently inside TMA → no trigger; pilot back
        // outside TMA → trigger fires again. This proves the predicate is
        // *position-dependent* (not a one-shot latch).
        val ctx = loadCtx()
        val triggers = listOf(LJMB_TMA_TRIGGER)
        val petov = ctx.worldIndex.positions.getValue(PETOV)
        val tmaCentroid = pointInsideTmaMaribor1(ctx.world, ctx.worldIndex)
        val outwardX = petov.xMeters - tmaCentroid.xMeters
        val outwardY = petov.yMeters - tmaCentroid.yMeters
        val outwardLen = kotlin.math.hypot(outwardX, outwardY)
        val stepM = 2.0 * 1852.0
        val outsidePos = Position(
            xMeters = petov.xMeters + outwardX / outwardLen * stepM,
            yMeters = petov.yMeters + outwardY / outwardLen * stepM,
        )
        val onWienInfo = Frequency.unsafe("124.400")

        // Phase 1 — outside TMA, near PETOV. Trigger eligible.
        val firstFire = PilotAirspace.frequencyChangeTriggerAt(
            ctx.world, ctx.worldIndex, outsidePos, onWienInfo, triggers,
        )
        assertTrue(firstFire.isSome(), "First approach: trigger must fire.")

        // Phase 2 — pilot crosses into TMA (centroid). Trigger suppressed.
        val insideFire = PilotAirspace.frequencyChangeTriggerAt(
            ctx.world, ctx.worldIndex, tmaCentroid, onWienInfo, triggers,
        )
        assertEquals(None, insideFire, "Inside TMA: trigger must be suppressed.")

        // Phase 3 — pilot exits TMA back to outside (re-cross). Trigger
        // eligible again. The predicate is purely position-dependent; no
        // hidden state latches across the boundary.
        val reCrossFire = PilotAirspace.frequencyChangeTriggerAt(
            ctx.world, ctx.worldIndex, outsidePos, onWienInfo, triggers,
        )
        assertTrue(reCrossFire.isSome(), "After re-crossing back outside: trigger must fire again.")
    }

    @Test
    fun `trigger fires when pilot has no current frequency (uncontrolled in FIS)`() {
        // Test-review round-3 finding: the `currentFrequency = null` path
        // (uncontrolled aircraft, no current frequency to skip against)
        // must remain trigger-eligible. Pin the contract so a future
        // refactor that returns `None` on null frequency would break this
        // test loudly.
        val ctx = loadCtx()
        val triggers = listOf(LJMB_TMA_TRIGGER)
        val petov = ctx.worldIndex.positions.getValue(PETOV)
        val tmaCentroid = pointInsideTmaMaribor1(ctx.world, ctx.worldIndex)
        val outwardX = petov.xMeters - tmaCentroid.xMeters
        val outwardY = petov.yMeters - tmaCentroid.yMeters
        val outwardLen = kotlin.math.hypot(outwardX, outwardY)
        val stepM = 2.0 * 1852.0
        val nearPetov = Position(
            xMeters = petov.xMeters + outwardX / outwardLen * stepM,
            yMeters = petov.yMeters + outwardY / outwardLen * stepM,
        )

        val fired = PilotAirspace.frequencyChangeTriggerAt(
            ctx.world, ctx.worldIndex, nearPetov, currentFrequency = null, triggers,
        )
        assertTrue(fired.isSome(),
            "Uncontrolled aircraft (currentFrequency=null) near PETOV must fire the trigger.")
    }

    @Test
    fun `merged LOWG plus LJMB world has coherent geometry on both sides — G1-DEF-11`() {
        // After G1-DEF-11: mergeAviationWorlds reprojects each airport's
        // local Cartesian frame into a single shared frame. Before the
        // fix, PETOV at LJMB-frame (22209, -18939) was reported as
        // inside a LOWG TMA volume because the frames overlapped.
        //
        // Pinning behaviour: in the merged world, a point near LJMB's
        // PETOV must resolve to an LJMB airspace volume (not a LOWG one),
        // AND a point inside LOWG's TMA must resolve to a LOWG volume
        // (not an LJMB one). Both sides — round-3 test review: silent
        // partial reprojection would pass an LJMB-side-only assertion.
        val merged = loadMergedWorldCoherent()

        // ── LJMB side ──
        val petov = merged.worldIndex.positions.getValue(PETOV)
        val ljmbTmaCentroid = pointInsideTmaMaribor1(merged.world, merged.worldIndex)
        val inLjmbTma = Position(
            xMeters = (petov.xMeters + ljmbTmaCentroid.xMeters) / 2.0,
            yMeters = (petov.yMeters + ljmbTmaCentroid.yMeters) / 2.0,
        )
        val ljmbVolume = PilotAirspace.currentVolume(merged.world, merged.worldIndex, inLjmbTma)
        assertTrue(ljmbVolume.isRight(),
            "Point near PETOV in merged world should resolve to an LJMB volume; got $ljmbVolume")
        val ljmbVolumeId = ljmbVolume.getOrNull()!!.id.value
        assertTrue(ljmbVolumeId.startsWith("LJMB_"),
            "Point near PETOV must resolve to an LJMB volume, not '$ljmbVolumeId'.")

        // ── LOWG side ──
        // Pick a point inside a known LOWG TMA volume's boundary. Use the
        // centroid of LO80C_D's first ring (the volume that previously
        // wrongly captured LJMB's PETOV in the unprojected merge).
        val lowgVolume = merged.world.airspace.getValue(AirspaceVolumeId("LO80C_D"))
        val lowgRing = lowgVolume.boundary!!.rings.first().points
            .mapNotNull { merged.worldIndex.positions[it] }
        val lowgCentroid = Position(
            xMeters = lowgRing.map { it.xMeters }.average(),
            yMeters = lowgRing.map { it.yMeters }.average(),
        )
        val resolvedLowg = PilotAirspace.currentVolume(merged.world, merged.worldIndex, lowgCentroid)
        assertTrue(resolvedLowg.isRight(),
            "LOWG TMA centroid should resolve to a LOWG volume; got $resolvedLowg")
        val lowgVolumeId = resolvedLowg.getOrNull()!!.id.value
        assertTrue(!lowgVolumeId.startsWith("LJMB_"),
            "LOWG TMA centroid must not resolve to an LJMB volume; got '$lowgVolumeId'.")
    }

    @Test
    fun `trigger does not fire when pilot is already on the target frequency`() {
        val ctx = loadCtx()
        val triggers = listOf(LJMB_TMA_TRIGGER)

        val petov = ctx.worldIndex.positions.getValue(PETOV)
        val tmaCentroid = pointInsideTmaMaribor1(ctx.world, ctx.worldIndex)
        val outwardX = petov.xMeters - tmaCentroid.xMeters
        val outwardY = petov.yMeters - tmaCentroid.yMeters
        val outwardLen = kotlin.math.hypot(outwardX, outwardY)
        val stepM = 2.0 * 1852.0
        val nearPetov = Position(
            xMeters = petov.xMeters + outwardX / outwardLen * stepM,
            yMeters = petov.yMeters + outwardY / outwardLen * stepM,
        )

        val onLjmbApp = Frequency.unsafe("134.305")  // already on APP
        val fired = PilotAirspace.frequencyChangeTriggerAt(
            world = ctx.world,
            worldIndex = ctx.worldIndex,
            point = nearPetov,
            currentFrequency = onLjmbApp,
            triggers = triggers,
        )
        assertEquals(None, fired, "Trigger must not fire when already on the target frequency.")
    }

    private data class Ctx(val world: AviationWorld, val worldIndex: WorldIndex)

    private fun loadCtx(): Ctx {
        // Most tests in this suite operate on a single airport (LJMB) —
        // PilotAirspace's contract is "single coherent frame," and LJMB-
        // alone is the simplest such frame. The merged-world coherence
        // check uses [loadMergedWorldCoherent].
        val projectRoot = resolveProjectRoot()
        val ljmbPath = projectRoot.resolve("cad/airports/rendered/ljmb/world-candidate.json")
        val world = WorldCandidateLoader.toWorld(
            json.decodeFromString<WorldCandidateDocument>(java.nio.file.Files.readString(ljmbPath))
        )
        return Ctx(world, WorldIndex(positions = world.geometry.points))
    }

    private fun loadMergedWorldCoherent(): Ctx {
        // After G1-DEF-11, mergeAviationWorlds reprojects each airport's
        // geometry into a single shared frame. This loader exercises that
        // path so the test asserts coherence in the merged frame.
        val projectRoot = resolveProjectRoot()
        val lowgPath = projectRoot.resolve("cad/airports/rendered/lowg/world-candidate.json")
        val ljmbPath = projectRoot.resolve("cad/airports/rendered/ljmb/world-candidate.json")
        val lowg = WorldCandidateLoader.toWorld(
            json.decodeFromString<WorldCandidateDocument>(java.nio.file.Files.readString(lowgPath))
        )
        val ljmb = WorldCandidateLoader.toWorld(
            json.decodeFromString<WorldCandidateDocument>(java.nio.file.Files.readString(ljmbPath))
        )
        val merged = WorldCandidateLoader.mergeAviationWorlds(listOf(lowg, ljmb))
        return Ctx(merged, WorldIndex(positions = merged.geometry.points))
    }

    private fun pointInsideTmaMaribor1(world: AviationWorld, idx: WorldIndex): Position {
        val tma = world.airspace.getValue(AirspaceVolumeId("LJMB_OPENAIR_TMA_MARIBOR_1"))
        val ring = tma.boundary!!.rings.first().points.mapNotNull { idx.positions[it] }
        val cx = ring.map { it.xMeters }.average()
        val cy = ring.map { it.yMeters }.average()
        return Position(xMeters = cx, yMeters = cy)
    }

    private fun resolveProjectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val direct = cwd.resolve("settings.gradle.kts")
        return if (java.nio.file.Files.exists(direct)) cwd else cwd.parent ?: cwd
    }

    companion object {
        private val PETOV = PointId("LJMB_FIX_PETOV")
        private val LJMB_TMA_TRIGGER = PilotAirspace.FrequencyChangeTrigger(
            triggerPoint = PETOV,
            targetVolume = AirspaceVolumeId("LJMB_OPENAIR_TMA_MARIBOR_1"),
            targetRole = RoleName.APPROACH,
            targetFrequency = Frequency.unsafe("134.305"),
            targetAerodrome = AerodromeId("LJMB"),
            leadNm = 5.0,
        )
    }
}
