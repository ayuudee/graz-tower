package xyz.easiersaid.twr.core.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime

/**
 * fn-12 (R1): smoke test for [Runway.obstruction] field round-trip.
 *
 * **Constructor-site audit context**: the only production constructor
 * site for [Runway] is `migration/.../WorldCandidateLoader.kt:148`, which
 * lets the new `obstruction` field default to `null` (the doctrinally-
 * correct shape — no obstructions are pre-authored at world load).
 * Test-fixture sites at `core/.../WorldConstructionTest.kt:292,304` and
 * `controller/.../CertificationBoundarySpec.kt:400` also default to null.
 *
 * This file pins the round-trip integrity of the typed obstruction
 * declaration:
 *  - Default-construct a Runway → `obstruction == null`.
 *  - Construct with `obstruction = RunwayObstruction(...)` → field
 *    survives data-class equality + `copy(...)` preservation.
 *  - `copy(...)` without obstruction preserves the existing value
 *    (Kotlin data-class semantics — pinned here because it's a
 *    load-bearing assumption for the fn-12 audit).
 *  - `copy(obstruction = null)` explicitly nulls the field (the only
 *    transition the world expiry pass uses).
 */
class RunwayObstructionRoundTripTest {

    private val rwyId = RunwayId("16C")
    private val threshold = PointId("THR")
    private val depEnd = PointId("DEP")

    private fun newRunway(obstruction: RunwayObstruction? = null): Runway = Runway(
        id = rwyId,
        path = Path(listOf(threshold, depEnd)),
        threshold = threshold,
        obstruction = obstruction,
    )

    @Test
    fun `default-constructed Runway has null obstruction`() {
        val r = newRunway()
        assertNull(r.obstruction)
    }

    @Test
    fun `Runway constructed with obstruction preserves the value`() {
        val obs = RunwayObstruction(clearsAt = SimTime.ofSeconds(60))
        val r = newRunway(obstruction = obs)
        assertEquals(obs, r.obstruction)
    }

    @Test
    fun `Runway-copy preserves obstruction when not specified (Kotlin data-class semantics)`() {
        val obs = RunwayObstruction(clearsAt = SimTime.ofSeconds(60))
        val r = newRunway(obstruction = obs)
        val copied = r.copy(exits = emptyList())
        // Pinning the load-bearing assumption for the fn-12 constructor-site audit:
        // copy(...) without obstruction must preserve the existing value.
        assertEquals(obs, copied.obstruction)
    }

    @Test
    fun `Runway-copy with explicit obstruction = null clears the field`() {
        val obs = RunwayObstruction(clearsAt = SimTime.ofSeconds(60))
        val r = newRunway(obstruction = obs)
        val cleared = r.copy(obstruction = null)
        assertNull(cleared.obstruction)
    }

    @Test
    fun `data-class equality treats two Runways with identical obstructions as equal`() {
        val obs = RunwayObstruction(clearsAt = SimTime.ofSeconds(60))
        val a = newRunway(obstruction = obs)
        val b = newRunway(obstruction = obs)
        assertEquals(a, b)
    }

    @Test
    fun `data-class equality distinguishes Runways with vs without obstruction`() {
        val obs = RunwayObstruction(clearsAt = SimTime.ofSeconds(60))
        val a = newRunway(obstruction = obs)
        val b = newRunway(obstruction = null)
        check(a != b) {
            "Runways with different obstruction state must not be equal — pre-Pass-12 default-null " +
                "would silently equate to authored obstruction if the field were missing from equals()"
        }
    }
}
