package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.ResponsibilityState
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Pass 7 (D-AUDIT.5 + Impact-O.1 / FP-M.3): cross-controller `Owned`
 * invariant.
 *
 * `ControllerSpec.responsibilities: Map<AircraftId, ResponsibilityState>`
 * is per-controller. The "no two controllers can simultaneously own the
 * same aircraft" invariant is *cross*-controller — the type can't enforce
 * it. The sim's per-step `assertResponsibilityInvariant(state)` does.
 *
 * This spec hand-constructs a two-Owned state and asserts the throw fires
 * with a clear diagnostic. Without this, a regression that produced two-
 * Owned would silently let two controllers issue conflicting clearances.
 */
class ResponsibilityInvariantSpec {

    @Test
    fun `two controllers Owning the same aircraft fails the invariant`() {
        val ac = AircraftId("OE-ABC")
        val now = SimTime.ofMillis(0)
        val ownedAt = ResponsibilityState.Owned(now)
        val ctrlA = ControllerSpec(
            id = ControllerId("CTRL_A"),
            role = RoleName.TOWER,
            aerodromeId = AerodromeId("LOWG"),
            frequency = Frequency.unsafe("118.200"),
            responsibilities = mapOf(ac to ownedAt),
        )
        val ctrlB = ControllerSpec(
            id = ControllerId("CTRL_B"),
            role = RoleName.GROUND,
            aerodromeId = AerodromeId("LOWG"),
            frequency = Frequency.unsafe("118.200"),
            responsibilities = mapOf(ac to ownedAt),
        )
        // Build a minimal SimState; we don't run step(), we just call the
        // assertion directly. The state's other fields don't matter.
        val state = minimalSimStateWith(listOf(ctrlA, ctrlB))
        val ex = assertFailsWith<IllegalStateException> {
            assertResponsibilityInvariant(state)
        }
        check(ex.message?.contains("Owned by both") == true) {
            "Expected violation message to mention 'Owned by both'; got: ${ex.message}"
        }
        check(ex.message?.contains("CTRL_A") == true && ex.message?.contains("CTRL_B") == true) {
            "Expected violation message to name both controllers; got: ${ex.message}"
        }
    }

    @Test
    fun `single Owned plus other controller Watching satisfies the invariant`() {
        // The valid mid-handoff state: one Owned, one Watching. No throw.
        val ac = AircraftId("OE-ABC")
        val now = SimTime.ofMillis(0)
        val ctrlA = ControllerSpec(
            id = ControllerId("CTRL_A"),
            role = RoleName.TOWER,
            aerodromeId = AerodromeId("LOWG"),
            frequency = Frequency.unsafe("118.200"),
            responsibilities = mapOf(ac to ResponsibilityState.Owned(now)),
        )
        val ctrlB = ControllerSpec(
            id = ControllerId("CTRL_B"),
            role = RoleName.GROUND,
            aerodromeId = AerodromeId("LOWG"),
            frequency = Frequency.unsafe("118.200"),
            responsibilities = mapOf(ac to ResponsibilityState.Watching(from = ctrlA.id, since = now)),
        )
        val state = minimalSimStateWith(listOf(ctrlA, ctrlB))
        // Should NOT throw.
        assertResponsibilityInvariant(state)
    }

    private fun minimalSimStateWith(controllers: List<ControllerSpec>): SimState {
        // Construct a SimState directly. For invariant checking we only
        // need state.controllers populated; other fields are unused but
        // required by the data class.
        return SimState(
            now = SimTime.ofMillis(0),
            seq = 0L,
            rng = SimRandom(0L),
            aircraft = LinkedHashMap(),
            controllers = controllers.associateBy { it.id },
            beliefs = emptyMap(),
            world = xyz.easiersaid.twr.core.world.AviationWorld(),
            worldIndex = xyz.easiersaid.twr.core.world.WorldIndex(),
            weatherByAerodrome = emptyMap(),
        )
    }
}
