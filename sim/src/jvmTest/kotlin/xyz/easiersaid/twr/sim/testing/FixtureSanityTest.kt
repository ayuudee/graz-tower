package xyz.easiersaid.twr.sim.testing

import arrow.core.getOrElse
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.RoleName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Pass 4 — fixture sanity. Per Test review M1, only LJMB has a sanity row;
 * G0 (`LowgGoldenTest`) covers LOWG end-to-end so a separate "LOWG loads
 * cleanly" test would be scaffold.
 *
 * The LJMB row is the floor not the ceiling: when Pass 8/11 introduces an
 * LJMB-driving integration test, this row becomes redundant and is dropped.
 *
 * Pass 6 (Test review M.4 fold-in) extends with **two rows per violation**:
 * positive non-trigger + negative trigger for `RoleNotPublished` and
 * `FrequencyMismatch`. The positive non-trigger row proves the validator
 * doesn't spuriously fire on unrelated correct configurations; the negative
 * trigger row proves it does fire on the intended condition. Without both,
 * a regression where the validator silently no-ops would still pass the
 * trigger row (because there's no positive baseline to compare against).
 */
class FixtureSanityTest {

    @Test
    fun `LJMB fixture loads cleanly and validates`() {
        val loaded = Fixtures.LJMB.load().getOrElse {
            fail("LJMB fixture failed to load: $it")
        }
        val violations = loaded.validate(Fixtures.LJMB)
        assertEquals(emptyList(), violations,
            "LJMB fixture violated sanity: $violations")
    }

    // ── RoleNotPublished — positive non-trigger ─────────────────────────────

    @Test
    fun `LOWG fixture asking only for published roles produces no RoleNotPublished`() {
        // LOWG world-candidate.json publishes TOWER, GROUND, APPROACH.
        // The standard LOWG fixture asks for TOWER+GROUND — both published.
        // No RoleNotPublished should fire.
        val loaded = Fixtures.LOWG.load().getOrElse {
            fail("LOWG fixture failed to load: $it")
        }
        val violations = loaded.validate(Fixtures.LOWG)
        val notPublished = violations.filterIsInstance<FixtureViolation.RoleNotPublished>()
        assertTrue(notPublished.isEmpty(),
            "Expected no RoleNotPublished for the canonical LOWG fixture; got $notPublished")
    }

    // ── RoleNotPublished — negative trigger ─────────────────────────────────

    @Test
    fun `fixture asking for a role the world-candidate does not publish triggers RoleNotPublished`() {
        // LJMB publishes only TOWER (G2 Phase A authoring). Construct a fixture
        // asking for both TOWER and GROUND — TOWER passes, GROUND triggers
        // RoleNotPublished. load()'s inline validate() surfaces the violation
        // list as a ValidationFailed Left.
        //
        // Pre-Phase-A this test passed for the wrong reason: LJMB published NO
        // roles, so BOTH TOWER and GROUND violated. The post-Phase-A semantics
        // are tighter: exactly one violation, and it's specifically GROUND.
        val drifted = Fixtures.LJMB.copy(
            controllerRoles = setOf(RoleName.TOWER, RoleName.GROUND),
        )
        val violations = drifted.load().fold(
            { error ->
                check(error is LoadError.ValidationFailed) {
                    "Expected ValidationFailed; got $error"
                }
                error.violations
            },
            { fail("Expected the drifted fixture to fail validation; got $it") },
        )
        val notPublished = violations.filterIsInstance<FixtureViolation.RoleNotPublished>()
        assertEquals(1, notPublished.size,
            "Expected exactly one RoleNotPublished (GROUND); TOWER is now published. Got $notPublished")
        assertEquals(RoleName.GROUND, notPublished.single().role,
            "Expected RoleNotPublished(GROUND) specifically; got $notPublished")
    }

    // ── FrequencyMismatch — positive non-trigger ────────────────────────────

    @Test
    fun `LOWG fixture frequency matching world-candidate produces no FrequencyMismatch`() {
        val loaded = Fixtures.LOWG.load().getOrElse {
            fail("LOWG fixture failed to load: $it")
        }
        val violations = loaded.validate(Fixtures.LOWG)
        val mismatches = violations.filterIsInstance<FixtureViolation.FrequencyMismatch>()
        assertTrue(mismatches.isEmpty(),
            "Expected no FrequencyMismatch for the canonical LOWG fixture; got $mismatches")
    }

    // ── FrequencyMismatch — negative trigger ────────────────────────────────

    @Test
    fun `fixture with a frequency that disagrees with the world-candidate triggers FrequencyMismatch`() {
        // LOWG publishes TOWER/GROUND on 118.200. Construct a fixture that
        // expects 119.500 — load()'s inline validate() must surface
        // FrequencyMismatch as a ValidationFailed Left.
        val drifted = Fixtures.LOWG.copy(
            frequency = Frequency.unsafe("119.500"),
        )
        val violations = drifted.load().fold(
            { error ->
                check(error is LoadError.ValidationFailed) {
                    "Expected ValidationFailed; got $error"
                }
                error.violations
            },
            { fail("Expected the drifted fixture to fail validation; got $it") },
        )
        val mismatches = violations.filterIsInstance<FixtureViolation.FrequencyMismatch>()
        assertTrue(mismatches.isNotEmpty(),
            "Expected FrequencyMismatch for a drifted LOWG fixture; got $violations")
        // Sanity-check the delta accessor — proves the named diff carries the
        // computed value (Pass 6 FP review M.4 introduced FrequencyDelta;
        // post-impl FP-P.2 typed deltaKhz as Option<Int>).
        val delta = mismatches.first().delta
        assertTrue(delta.deltaKhz.fold({ false }, { it != 0 }),
            "Expected non-zero deltaKhz; got ${delta.deltaKhz}")
    }
}
