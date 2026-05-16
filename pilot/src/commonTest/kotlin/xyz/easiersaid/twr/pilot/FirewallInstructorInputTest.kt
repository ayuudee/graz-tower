package xyz.easiersaid.twr.pilot

import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Architectural enforcement test — `InstructorInput` construction-time field
 * shape (fn-28.8 G0 abort-takeoff foundation).
 *
 * The named-argument constructor below + the reflection scan are the
 * FIREWALL ALLOWLIST for every leaf of [InstructorInput]. Every field on
 * every leaf MUST be one of:
 *  - a domain identifier (`AircraftId`, future `AerodromeId`, etc.)
 *  - a scalar time (`SimTime`)
 *  - a typed-units scalar (Temperature, PressureSetting — same shape as the
 *    fn-28.1 `DensityAltitudeInput` projection).
 *
 * Adding a field reachable from `WorldModel` / `Aerodrome` / `AviationWorld`
 * / `WorldIndex` / `AircraftState` / any controller-side type (e.g.
 * `BeliefState`, `ControllerSpec`, `ControllerView`) re-introduces the
 * world-state-reachability firewall regression the architectural decision
 * memo (`knowledge/decisions/instructor-channel-causation-for-sim-2026-05-16`)
 * deletes.
 *
 * **No-suppression rule:** an architectural test failure is never resolved
 * by `@Disabled`, `@Suppress`, or test removal. Fix the violation or amend
 * the firewall via plan revision.
 */
class FirewallInstructorInputTest {

    @Test
    fun `EngineFailureAt construction names only cockpit-instructor fields`() {
        // FIREWALL ALLOWLIST. Every named argument below maps to a
        // cockpit-instructor briefing input: aircraft identity + briefing
        // time. No world-side reachable references.
        val canonical: InstructorInput = InstructorInput.EngineFailureAt(
            aircraftId = xyz.easiersaid.twr.protocol.AircraftId("X"),
            time = xyz.easiersaid.twr.protocol.SimTime.ZERO,
        )
        @Suppress("UNUSED_VARIABLE")
        val _check = canonical.aircraftId
    }

    /**
     * Reflection-based property scan over the [InstructorInput.EngineFailureAt]
     * leaf. The allowlist enumerates every cockpit-side field; a new field
     * added with a default value would slip past the canonical-constructor
     * block above (call sites need not name defaulted args), so the
     * reflection scan is the structural guarantee.
     *
     * When new leaves of `InstructorInput` are added (D-AUDIT.9.IV fuel
     * exhaustion, .V icing, divert), extend this test with a per-leaf
     * allowlist + reflection block. The enumeration check at the bottom
     * surfaces a missed extension via the leaf-count assertion.
     */
    @Test
    fun `EngineFailureAt property names match the firewall allowlist`() {
        val allowedFields = setOf("aircraftId", "time")
        val actualFields = InstructorInput.EngineFailureAt::class
            .memberProperties.map { it.name }.toSet()
        assertEquals(
            allowedFields,
            actualFields,
            """
            FIREWALL VIOLATION: InstructorInput.EngineFailureAt field set
            does not match the cockpit-instructor allowlist. Every field on
            every InstructorInput leaf must be a domain identifier, scalar
            time, or typed-units scalar. World-state reachability
            (Aerodrome, WorldModel, AviationWorld, WorldIndex, AircraftState,
            BeliefState, ControllerSpec, ControllerView) is forbidden.

            Expected (allowlist): $allowedFields
            Actual (EngineFailureAt): $actualFields

            Resolution paths:
              - If the new field IS a cockpit-instructor input: add it to
                `allowedFields` here AND add it to the canonical-constructor
                block above AND justify in the leaf's KDoc.
              - If the new field IS NOT a cockpit-instructor input (e.g.
                reaches into WorldModel / controller-side state): remove
                it. Smuggling world-side state through InstructorInput is
                the firewall regression the instructor-channel-causation
                architectural decision deletes.

            No `@Suppress`, no `@Disabled`, no test removal.
            """.trimIndent(),
        )
    }

    @Test
    fun `InstructorInput sealed hierarchy carries exactly the v1 leaf set`() {
        // fn-28.8 v1: a single leaf (EngineFailureAt). Future tasks land
        // more leaves (D-AUDIT.9.IV fuel exhaustion, .V icing, divert) —
        // each addition extends this assertion AND adds per-leaf
        // reflection coverage above. The leaf-count gate surfaces a
        // missed test-extension when the hierarchy grows.
        val sealedSubclasses = InstructorInput::class.sealedSubclasses
            .map { it.simpleName ?: "<anonymous>" }
            .toSet()
        assertEquals(
            setOf("EngineFailureAt"),
            sealedSubclasses,
            "InstructorInput sealed-subclass set must match the test allowlist; new leaves require coverage",
        )
    }

    @Test
    fun `EngineFailureAt fields are typed-unit primitives reachable through no world entity`() {
        // Defensive structural check: the AircraftId and SimTime types
        // themselves carry no nested reachable world-state. A regression
        // that promotes either type to a world-entity-bearing shape would
        // surface elsewhere (FirewallAircraftStateTest / typed-units
        // boundary), but this assertion is the explicit pin at the
        // InstructorInput surface.
        val ev = InstructorInput.EngineFailureAt(
            aircraftId = xyz.easiersaid.twr.protocol.AircraftId("X"),
            time = xyz.easiersaid.twr.protocol.SimTime.ZERO,
        )
        // AircraftId carries a single String `value`; SimTime carries a
        // single Long `millis`. No transitive world reachability.
        assertTrue(ev.aircraftId.value == "X", "AircraftId is the typed identifier shape")
        assertTrue(ev.time.millis == 0L, "SimTime is the typed scalar shape")
    }
}
