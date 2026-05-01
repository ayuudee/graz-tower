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
    fun `three controllers, two Owned and one Watching still fails the invariant`() {
        // Pass 7 post-impl Test-2: three-controller variant. The Owned
        // duplication is still detected; the third controller's clean
        // Watching state should not mask or confuse the diagnostic.
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
        val ctrlC = ControllerSpec(
            id = ControllerId("CTRL_C"),
            role = RoleName.APPROACH,
            aerodromeId = AerodromeId("LOWG"),
            frequency = Frequency.unsafe("119.300"),
            responsibilities = mapOf(ac to ResponsibilityState.Watching(from = ctrlA.id, since = now)),
        )
        val state = minimalSimStateWith(listOf(ctrlA, ctrlB, ctrlC))
        val ex = assertFailsWith<IllegalStateException> {
            assertResponsibilityInvariant(state)
        }
        // First throw should be on the Owned duplication (CTRL_A vs CTRL_B);
        // CTRL_C's Watching is a separate concern and shouldn't be the cause.
        check(ex.message?.contains("Owned by both") == true) {
            "Expected Owned-duplication diagnostic, not pairing diagnostic; got: ${ex.message}"
        }
    }

    @Test
    fun `valid mid-handoff (HandingOff plus paired Watching) satisfies the invariant`() {
        // The valid mid-handoff state: A is HandingOff(Peer(B)) + B is
        // Watching(from=A). The pairing invariant (Pass 7 post-impl
        // Impact-M.1) requires both sides match; this row pins the happy
        // path.
        val ac = AircraftId("OE-ABC")
        val now = SimTime.ofMillis(0)
        val aId = ControllerId("CTRL_A")
        val bId = ControllerId("CTRL_B")
        val ctrlA = ControllerSpec(
            id = aId,
            role = RoleName.TOWER,
            aerodromeId = AerodromeId("LOWG"),
            frequency = Frequency.unsafe("118.200"),
            responsibilities = mapOf(ac to ResponsibilityState.HandingOff(
                target = xyz.easiersaid.twr.protocol.HandoffTarget.Peer(bId), since = now,
            )),
        )
        val ctrlB = ControllerSpec(
            id = bId,
            role = RoleName.GROUND,
            aerodromeId = AerodromeId("LOWG"),
            frequency = Frequency.unsafe("118.200"),
            responsibilities = mapOf(ac to ResponsibilityState.Watching(from = aId, since = now)),
        )
        val state = minimalSimStateWith(listOf(ctrlA, ctrlB))
        // Should NOT throw.
        assertResponsibilityInvariant(state)
    }

    @Test
    fun `unpaired Watching (no matching HandingOff at sender) fails the invariant`() {
        // Pass 7 post-impl Impact-M.1: a Watching controller without a
        // matching HandingOff(Peer) on the named sender is desync. This
        // is the regression mode the pairing invariant catches — one side
        // updated, the other skipped.
        val ac = AircraftId("OE-ABC")
        val now = SimTime.ofMillis(0)
        val aId = ControllerId("CTRL_A")
        val bId = ControllerId("CTRL_B")
        val ctrlA = ControllerSpec(
            id = aId,
            role = RoleName.TOWER,
            aerodromeId = AerodromeId("LOWG"),
            frequency = Frequency.unsafe("118.200"),
            responsibilities = mapOf(ac to ResponsibilityState.Owned(now)),  // not HandingOff
        )
        val ctrlB = ControllerSpec(
            id = bId,
            role = RoleName.GROUND,
            aerodromeId = AerodromeId("LOWG"),
            frequency = Frequency.unsafe("118.200"),
            responsibilities = mapOf(ac to ResponsibilityState.Watching(from = aId, since = now)),
        )
        val state = minimalSimStateWith(listOf(ctrlA, ctrlB))
        val ex = assertFailsWith<IllegalStateException> {
            assertResponsibilityInvariant(state)
        }
        check(ex.message?.contains("pairing") == true) {
            "Expected pairing violation; got: ${ex.message}"
        }
    }

    @Test
    fun `unpaired HandingOff (no matching Watching at receiver) fails the invariant`() {
        // Pass 7 re-review Test-M.1 fold-in: symmetric coverage of the
        // pairing rule. The previous row tests the receiver-side desync
        // (Watching present but sender is Owned). This row tests the
        // sender-side desync (HandingOff present but receiver is Owned/
        // empty/wrong-Watching). Step.kt's invariant covers both
        // directions; this row pins the second direction at the spec
        // level so a regression that only checks one side fails loudly.
        val ac = AircraftId("OE-ABC")
        val now = SimTime.ofMillis(0)
        val aId = ControllerId("CTRL_A")
        val bId = ControllerId("CTRL_B")
        val ctrlA = ControllerSpec(
            id = aId,
            role = RoleName.TOWER,
            aerodromeId = AerodromeId("LOWG"),
            frequency = Frequency.unsafe("118.200"),
            responsibilities = mapOf(ac to ResponsibilityState.HandingOff(
                target = xyz.easiersaid.twr.protocol.HandoffTarget.Peer(bId), since = now,
            )),
        )
        val ctrlB = ControllerSpec(
            id = bId,
            role = RoleName.GROUND,
            aerodromeId = AerodromeId("LOWG"),
            frequency = Frequency.unsafe("118.200"),
            responsibilities = mapOf(ac to ResponsibilityState.Owned(now)),  // not Watching
        )
        val state = minimalSimStateWith(listOf(ctrlA, ctrlB))
        val ex = assertFailsWith<IllegalStateException> {
            assertResponsibilityInvariant(state)
        }
        check(ex.message?.contains("pairing") == true) {
            "Expected pairing violation; got: ${ex.message}"
        }
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
