package xyz.easiersaid.twr.sim

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.left
import arrow.core.right
import arrow.core.some
import xyz.easiersaid.twr.core.world.AirspaceClass
import xyz.easiersaid.twr.core.world.AirspaceVolume
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AirspaceVolumeId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import kotlin.math.hypot

/**
 * Stateless geometric helpers for pilot airspace reasoning.
 *
 * Pure functions over [AviationWorld] and [WorldIndex]. No caching,
 * no per-aircraft state. Each call recomputes from the inputs;
 * profiling will tell us if a derived index is needed later.
 */
object PilotAirspace {

    /** A nautical mile in metres. */
    private const val METRES_PER_NM: Double = 1852.0

    /**
     * Failure modes for [currentVolume]. Distinguishes "world has no
     * airspace data" from "point is outside all known volumes" — they
     * are operationally different states.
     */
    sealed interface AirspaceLookupError {
        /** [AviationWorld.airspace] is empty. */
        data object NoAirspaceData : AirspaceLookupError

        /** Point falls inside no volume's boundary. */
        data object PointOutsideAllVolumes : AirspaceLookupError
    }

    /**
     * A pilot-side rule that fires a frequency-change action when
     * [point] is within [leadNm] of [triggerPoint], the pilot is *not*
     * already inside [targetVolume], and the pilot is *not* already on
     * [targetFrequency]. Modelled after the published "before-entry"
     * contact requirement (e.g. Jepp 19-2 LJMB: contact MARIBOR
     * APPROACH before entering TMA Maribor; LJMB CTR REPs require
     * contact 5 minutes prior).
     *
     * Caller (cognitive layer / G1.6 fixture) supplies the trigger
     * table; this module only does the geometric matching.
     */
    data class FrequencyChangeTrigger(
        val triggerPoint: PointId,
        val targetVolume: AirspaceVolumeId,
        val targetRole: RoleName,
        val targetFrequency: Frequency,
        val targetAerodrome: AerodromeId,
        val leadNm: Double,
    )

    /**
     * The most-restrictive volume containing [point], by [AirspaceClass]
     * precedence (A < B < C < D < E < F < G — A is most restrictive).
     *
     * Uses 2D point-in-polygon (ray casting) over the volume's outer
     * ring; altitudes are not yet considered (G1 is VFR low-level only).
     * Volumes whose boundary is not authored (`boundary == null`) are
     * skipped — they cannot answer "does this point lie inside?"
     */
    fun currentVolume(
        world: AviationWorld,
        worldIndex: WorldIndex,
        point: Position,
    ): Either<AirspaceLookupError, AirspaceVolume> {
        if (world.airspace.isEmpty()) return AirspaceLookupError.NoAirspaceData.left()
        val containing = world.airspace.values
            .filter { volume -> volume.boundary != null && containsPoint(volume, worldIndex, point) }
        if (containing.isEmpty()) return AirspaceLookupError.PointOutsideAllVolumes.left()
        // Most restrictive class first: A < B < C < D < E < F < G.
        return containing.minBy { it.airspaceClass }.right()
    }

    /**
     * The first trigger in [triggers] whose anchor point [point] is
     * within [FrequencyChangeTrigger.leadNm] of, where the pilot is
     * not already inside the target volume and not already on the
     * target frequency.
     *
     * `None` means no rule fires here — most points have no trigger.
     * That is *not* an error.
     */
    fun frequencyChangeTriggerAt(
        world: AviationWorld,
        worldIndex: WorldIndex,
        point: Position,
        currentFrequency: Frequency?,
        triggers: List<FrequencyChangeTrigger>,
    ): Option<FrequencyChangeTrigger> {
        for (trigger in triggers) {
            // Skip if pilot is already on the target frequency. When [currentFrequency]
            // is null (aircraft unowned by any controller), the equality is vacuously
            // false and the trigger remains eligible.
            if (currentFrequency != null && trigger.targetFrequency == currentFrequency) continue
            val targetVolume = world.airspace[trigger.targetVolume] ?: continue
            if (containsPoint(targetVolume, worldIndex, point)) continue  // already inside target
            val anchor = worldIndex.positions[trigger.triggerPoint] ?: continue
            val distNm = horizontalDistanceMetres(point, anchor) / METRES_PER_NM
            if (distNm <= trigger.leadNm) return trigger.some()
        }
        return None
    }

    /** Whether [point] lies inside the outer ring of [volume]'s boundary. */
    private fun containsPoint(
        volume: AirspaceVolume,
        worldIndex: WorldIndex,
        point: Position,
    ): Boolean {
        val boundary = volume.boundary ?: return false
        if (boundary.rings.isEmpty()) return false
        val ring = boundary.rings.first()
        val ringPositions = ring.points.mapNotNull { worldIndex.positions[it] }
        if (ringPositions.size < 3) return false
        return rayCastInside(point, ringPositions)
    }

    /**
     * Horizontal Euclidean distance between two [Position]s, ignoring
     * altitude. Both positions are in the same Cartesian projection
     * (xMeters/yMeters) per the world geometry contract.
     */
    private fun horizontalDistanceMetres(a: Position, b: Position): Double =
        hypot(a.xMeters - b.xMeters, a.yMeters - b.yMeters)

    /**
     * Standard 2D point-in-polygon test (Jordan ray casting).
     * Treats the polygon as closed (first and last vertex implicitly
     * connected). Edge cases on the boundary itself are
     * implementation-defined; for G1 the trigger leadNm guard sits
     * outside the boundary anyway.
     */
    private fun rayCastInside(p: Position, ring: List<Position>): Boolean {
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val pi = ring[i]
            val pj = ring[j]
            val intersects = (pi.yMeters > p.yMeters) != (pj.yMeters > p.yMeters) &&
                (p.xMeters < (pj.xMeters - pi.xMeters) * (p.yMeters - pi.yMeters) /
                    (pj.yMeters - pi.yMeters) + pi.xMeters)
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }
}
