package xyz.easiersaid.twr.sim.testing

import arrow.core.getOrElse
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.RoleName
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * G2 Phase G — `MultiAerodromeFixture` staffing doctrine pins.
 *
 * Test pins for the cross-aerodrome staffing rules. Each row encodes a
 * doctrine that, if silently broken in a future fixture edit, would let
 * a regression slip through `FixtureLoadSpec`'s "loads cleanly" check
 * but break the runtime semantics of cross-aerodrome flow.
 *
 * Doctrines pinned:
 *  1. **Cross-aerodrome frequency separation** — every staffed
 *     controller's frequency at the destination aerodrome must be
 *     distinct from every staffed controller's frequency at the
 *     departure aerodrome. Co-location of frequencies across aerodromes
 *     would collapse the wire-layer's frequency-scoped party-line
 *     broadcast (`Step.kt:handleTransmissionEnd`) — pilots' transmissions
 *     intended for one aerodrome would reach controllers at the other.
 *
 *  2. **Departure aerodrome's TOWER has at least one peer-handoff target**
 *     (i.e., a same-aerodrome controller other than itself). Without a
 *     peer, intra-aerodrome handoff (GROUND ↔ TOWER, TOWER ↔ APPROACH)
 *     can't fire — the aircraft would either get stuck at GROUND (if no
 *     TOWER) or at TOWER (if no GROUND-feeding-into-TOWER). The
 *     cross-aerodrome flow assumes the departure aerodrome can run a
 *     normal departure pipeline up to release at the boundary.
 *
 *  3. **Destination aerodrome staffs TOWER** — landing requires a tower
 *     to issue clearance. A destination without TOWER would mean the
 *     aircraft can't land under controlled-airspace rules; G2's doctrine
 *     assumes destinations have at minimum a TOWER (AFIS / uncontrolled
 *     destinations are a future scope item).
 *
 *  4. **Distinct aerodrome IDs** — already enforced as a runtime invariant
 *     on `MultiAerodromeFixture` init, but pinned here at the doctrine
 *     level so a regression that lowers the constructor's invariant from
 *     `require` to a soft check still trips this test.
 *
 * The fixture under test is `Fixtures.LOWG_LJMB_VFR`. Future
 * cross-aerodrome fixtures should be added as additional rows.
 *
 * **No-suppression rule:** an architectural doctrine failure is never
 * resolved by `@Disabled`, `@Suppress`, or test removal. Fix the
 * fixture, or amend the doctrine via plan revision.
 */
class FixtureAerodromeStaffingDoctrineSpec {

    @Test
    fun `LOWG_LJMB_VFR cross-aerodrome frequency separation between aerodromes`() {
        val loaded = Fixtures.LOWG_LJMB_VFR.load()
            .getOrElse { fail("LOWG_LJMB_VFR fixture failed to load: $it") }
        val lowg = AerodromeId("LOWG")
        val ljmb = AerodromeId("LJMB")
        val lowgFreqs: Set<Frequency> = loaded.controllers.values
            .filter { it.aerodromeId == lowg }
            .map { it.frequency }
            .toSet()
        val ljmbFreqs: Set<Frequency> = loaded.controllers.values
            .filter { it.aerodromeId == ljmb }
            .map { it.frequency }
            .toSet()
        val collisions = lowgFreqs intersect ljmbFreqs
        assertTrue(
            collisions.isEmpty(),
            "Cross-aerodrome frequency collision in LOWG_LJMB_VFR: $collisions. " +
                "Each aerodrome's staffed frequencies must be disjoint from the " +
                "other's so the wire-layer's frequency-scoped party-line " +
                "broadcast (sim/Step.kt:handleTransmissionEnd) routes correctly. " +
                "LOWG: $lowgFreqs, LJMB: $ljmbFreqs.",
        )
    }

    @Test
    fun `LOWG_LJMB_VFR departure aerodrome has both GROUND and TOWER`() {
        val loaded = Fixtures.LOWG_LJMB_VFR.load()
            .getOrElse { fail("LOWG_LJMB_VFR fixture failed to load: $it") }
        val lowg = AerodromeId("LOWG")
        val lowgRoles: Set<RoleName> = loaded.controllers.values
            .filter { it.aerodromeId == lowg }
            .map { it.role }
            .toSet()
        val required = setOf(RoleName.GROUND, RoleName.TOWER)
        val missing = required - lowgRoles
        assertTrue(
            missing.isEmpty(),
            "LOWG (departure aerodrome) is missing required roles for the " +
                "departure pipeline: $missing. The departure aerodrome must " +
                "staff at least GROUND + TOWER so the aircraft can taxi from " +
                "stand to runway and depart under controlled-airspace rules. " +
                "Found: $lowgRoles.",
        )
    }

    @Test
    fun `LOWG_LJMB_VFR destination aerodrome staffs TOWER`() {
        val loaded = Fixtures.LOWG_LJMB_VFR.load()
            .getOrElse { fail("LOWG_LJMB_VFR fixture failed to load: $it") }
        val ljmb = AerodromeId("LJMB")
        val ljmbRoles: Set<RoleName> = loaded.controllers.values
            .filter { it.aerodromeId == ljmb }
            .map { it.role }
            .toSet()
        assertTrue(
            RoleName.TOWER in ljmbRoles,
            "LJMB (destination aerodrome) must staff TOWER — landing " +
                "requires tower clearance. AFIS / uncontrolled destinations " +
                "are a future scope item. Found: $ljmbRoles.",
        )
    }

    @Test
    fun `LOWG_LJMB_VFR has at least two distinct aerodromes`() {
        val loaded = Fixtures.LOWG_LJMB_VFR.load()
            .getOrElse { fail("LOWG_LJMB_VFR fixture failed to load: $it") }
        val aerodromes: Set<AerodromeId> = loaded.controllers.values
            .map { it.aerodromeId }
            .toSet()
        assertTrue(
            aerodromes.size >= 2,
            "Cross-aerodrome fixture must staff at least two distinct " +
                "aerodromes; found only $aerodromes. A single-aerodrome " +
                "fixture should use `Fixture` (single-aerodrome variant), " +
                "not `MultiAerodromeFixture`.",
        )
    }

    @Test
    fun `LOWG_LJMB_VFR cardinal staffing claim — exact (role,aerodrome) set`() {
        // R6 exact-set match per epic spec
        // (.flow/specs/fn-5-g2-cross-aerodrome-vfr-transit-lowg-ljmb.md).
        // Sibling tests above each enforce one shape constraint
        // independently (departure-side roles, destination-side TOWER,
        // distinct aerodromes); this row pins the *complete* staffing
        // tuple set so an extension that adds e.g. LJMB_GROUND or removes
        // LOWG_APPROACH trips immediately. The pre-existing
        // `FixtureLoadSpec`'s `keys` check pins ControllerIds; that
        // representation can drift from doctrine if naming conventions
        // change. The (role, aerodrome) tuple is the doctrine-shaped
        // primary key.
        val loaded = Fixtures.LOWG_LJMB_VFR.load()
            .getOrElse { fail("LOWG_LJMB_VFR fixture failed to load: $it") }
        val lowg = AerodromeId("LOWG")
        val ljmb = AerodromeId("LJMB")
        val staffed: Set<Pair<RoleName, AerodromeId>> = loaded.controllers.values
            .map { it.role to it.aerodromeId }
            .toSet()
        val expected: Set<Pair<RoleName, AerodromeId>> = setOf(
            RoleName.GROUND to lowg,
            RoleName.TOWER to lowg,
            RoleName.APPROACH to lowg,
            RoleName.TOWER to ljmb,
        )
        assertTrue(
            staffed == expected,
            "LOWG_LJMB_VFR cardinal staffing claim broken.\n" +
                "  Found:    $staffed\n" +
                "  Expected: $expected\n" +
                "  Extra:    ${staffed - expected}\n" +
                "  Missing:  ${expected - staffed}\n" +
                "Per the epic spec (R6 in fn-5-…), LOWG staffs all three " +
                "controlled-airspace roles; LJMB staffs only TOWER " +
                "(LJMB_APPROACH is explicitly out of scope for G2).",
        )
    }
}
