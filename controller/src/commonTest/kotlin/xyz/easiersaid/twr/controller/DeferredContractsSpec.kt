package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.bdi.RunwayLengthFailure
import xyz.easiersaid.twr.controller.bdi.RunwayLengthOperation
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.protocol.IcaoTypeDesignator
import xyz.easiersaid.twr.protocol.RunwayId
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `@Ignore`d placeholder tests pinning eventual contracts for deferred work
 * in `:controller`. Mirrors the `:pilot` pattern; see
 * `docs/deferments.md` for the canonical register and
 * `docs/deferments-CONVENTION.md` for the four-bucket model.
 *
 * **Bucket discipline (per deferments-CONVENTION § 5):**
 * - Bucket 1 tests reference real current-API types/values so a rename
 *   breaks compile.
 * - Bucket 2 tests body is commented-out pseudo-code only — the API
 *   doesn't exist yet, and the comment names what API the implementer
 *   adds when uncommenting.
 *
 * When a deferment is picked up, the implementer flips `@Ignore` off,
 * uncomments / extends the body, and the test becomes a real verification
 * of the contract.
 */
class DeferredContractsSpec {

    /**
     * **D-AUDIT.7.III-FOLLOWUP** — derive `BeliefState.activeRunway` on read.
     *
     * When implemented:
     *  - `BeliefState.activeRunway: RunwayId?` stored slice is removed.
     *  - A derivation helper computes it on read from
     *    `BeliefState.expectedAtisLetter` + `runwayConfiguration` + wind.
     *  - All read sites migrate; the stored slice is gone.
     *
     * This test asserts: today the stored slice is still present (so a
     * future deletion forces this test to fail to compile, signalling the
     * deferment is closing and the assertions need migrating to derivation).
     */
    @Ignore
    @Test
    fun `D-AUDIT7-III BeliefState still carries stored activeRunway slice (will be deleted)`() {
        // Bucket 1: API exists today; this references the stored slice to
        // anchor the eventual deletion.
        val state: BeliefState = BeliefState()
        assertNull(state.activeRunway, "default BeliefState.activeRunway is null today")
        val seeded = state.copy(activeRunway = RunwayId("16C"))
        assertEquals(RunwayId("16C"), seeded.activeRunway)
        // TODO when D-AUDIT.7.III-FOLLOWUP lands: delete the stored slice;
        // replace with `assertEquals("16C", deriveActiveRunway(view, beliefs).id)`.
    }

    /**
     * **D-PASS-13.3-II-FOLLOWUP** — wire `RunwayLengthFailure` into
     * `DecisionTrace.skippedActions`.
     *
     * When implemented:
     *  - `SkippedAction.reason` (today `String`) gains a sibling
     *    `failure: RunwayLengthFailure?` field (or sealed wrapper) carrying
     *    the typed payload from `RunwayLengthSufficient.classify(...)`.
     *  - `DecisionTrace` rendering surfaces the operation (TAKEOFF / LANDING),
     *    designator, runway, required vs available metres.
     *
     * This test asserts the typed-failure surface exists today and is
     * exhaustively constructable; the missing piece is the wire into
     * `DecisionTrace.skippedActions`.
     */
    @Ignore
    @Test
    fun `PASS-13_3-II RunwayLengthFailure typed surface is plumbed into DecisionTrace`() {
        // Bucket 1: the typed-failure surface exists today; the integration
        // into `DecisionTrace.skippedActions` is the deferred wiring.
        val failure: RunwayLengthFailure = RunwayLengthFailure.RunwayTooShort(
            operation = RunwayLengthOperation.LANDING,
            designator = IcaoTypeDesignator.unsafe("B738"),
            runway = RunwayId("16C"),
            requiredM = 1850,
            availableM = 1500.0,
        )
        assertTrue(failure is RunwayLengthFailure.RunwayTooShort)
        assertEquals(RunwayLengthOperation.LANDING, failure.operation)
        // TODO when D-PASS-13.3-II-FOLLOWUP lands:
        //   val trace = decideAndCollectTrace(...)
        //   val skipped = trace.skippedActions.single()
        //   assertEquals(failure, skipped.failure)  // new typed field on SkippedAction
    }

    /**
     * **D-AUDIT.4.A.II-FOLLOWUP** — runway-condition gating
     * (wet, contaminated, displaced threshold).
     *
     * When implemented:
     *  - `RunwayDeclaredDistances` gains a condition-modifier overlay
     *    (or `RunwayCondition { Dry | Wet | Contaminated(...) }` sealed
     *    type read by `RunwayLengthSufficient.classify(...)`).
     *  - Displaced-threshold runways carry the effective LDA/TODA per
     *    operation, applied at gate time.
     *  - `RunwayLengthGatingSpec` extends with wet / contaminated /
     *    displaced rows.
     *
     * Bucket 2 — `RunwayCondition` / displaced-threshold modelling does not
     * exist; today the gating uses dry / MTOW.
     */
    @Ignore
    @Test
    fun `D-AUDIT4-A-II runway-condition gating affects runway-length classification`() {
        // Bucket 2: no condition modelling on `RunwayDeclaredDistances` today.
        // TODO when D-AUDIT.4.A.II-FOLLOWUP lands — requires
        //   `RunwayCondition` sealed type (Dry | Wet | Contaminated(depth, kind)).
        //   val wetRunway = runwayWith(condition = RunwayCondition.Wet)
        //   val failure = RunwayLengthSufficient.classify(b738, wetRunway, LANDING)
        //   assertIs<RunwayLengthFailure.RunwayTooShort>(failure)  // wet 15% longer
    }

    /**
     * **D-AUDIT.7.II-FOLLOWUP** — mixed-mode parallel-runway operations.
     *
     * When implemented:
     *  - `RunwayConfiguration` accepts non-identity `arrivals` vs `departures`
     *    on parallel runway pairs (e.g. 16L arrivals + 16R departures).
     *  - `RunwayConfigurationSelection` selects the mixed-mode bucket when
     *    a published parallel-runway configuration carries distinct sets.
     *  - `selectRunwayConfiguration` + ATIS publish layer surface the split.
     *
     * Bucket 2 — `RunwayConfiguration` accepts arrivals/departures sets today
     * but no consumer reads them as distinct; today the selection assumes
     * `arrivals == departures`.
     */
    @Ignore
    @Test
    fun `D-AUDIT7-II mixed-mode parallel runway configuration is selectable`() {
        // Bucket 2: today `arrivals == departures` is the only validated case.
        // TODO when D-AUDIT.7.II-FOLLOWUP lands — needs a simultaneous-parallel-
        // approach scenario and the consumer wiring to read distinct sets.
        //   val config = RunwayConfiguration(
        //       arrivals = listOf(RunwayId("16L")),
        //       departures = listOf(RunwayId("16R")),
        //   )
        //   val outcome = selectRunwayConfiguration(parallelRunways, wind)
        //   assertEquals(config, outcome.getOrNull())
    }

    /**
     * **D-AUDIT.8.II-FOLLOWUP** — separate ATIS frequency.
     *
     * When implemented:
     *  - `ControllerSpec` carries a per-aerodrome `atisFrequency: Frequency?`.
     *  - `Step.handleAtisIssued` emits on the ATIS frequency, not the
     *    controller's primary.
     *  - Pilot tunes to ATIS first then to the operator role (TOWER / GROUND).
     *
     * Bucket 2 — multi-frequency comms model is not in place; today ATIS
     * is implicit on the role's primary frequency.
     */
    @Ignore
    @Test
    fun `D-AUDIT8-II ATIS broadcast lives on its own frequency`() {
        // Bucket 2: no `atisFrequency` field today; pilot/controller share
        // one frequency per role.
        // TODO when D-AUDIT.8.II-FOLLOWUP lands — requires multi-frequency
        // comms model + `ControllerSpec.atisFrequency`.
        //   val spec = controllerSpecOf(atisFrequency = Frequency.atisFor(LOWG))
        //   val atisTxStream = step(...).transmissions.filter { it.frequency == spec.atisFrequency }
        //   assertTrue(atisTxStream.isNotEmpty())
    }

    /**
     * **D-PASS-17.2** — sweep `firstNotNullOfOrNull` walks in IFR
     * procedure helpers (sids/stars/approaches/missed-approach).
     *
     * When implemented:
     *  - `buildSidDepartureRoute`, `buildStarApproachRoute`,
     *    `buildArrivalJoinRoute`, `buildMissedApproachRoute` each accept
     *    the aerodrome ID and scope the procedure lookup to that aerodrome's
     *    `procedures` map (mirroring D-PASS-13.1's
     *    `RunwayLengthSufficient` fix).
     *  - Cross-aerodrome procedure-ID-collision spec pins the fix.
     *
     * Bucket 2 — the sweep is a refactor; today the walks succeed because
     * procedure IDs do not collide across the fixture (LOWG / LJMB).
     */
    @Ignore
    @Test
    fun `PASS-17_2 IFR procedure helpers are aerodrome-scoped (no firstNotNullOfOrNull walks)`() {
        // Bucket 2: today the walks succeed because procedure IDs are unique
        // across the in-fixture aerodrome set; collision exposure needs a
        // multi-aerodrome procedure-ID-collision test scenario.
        // TODO when D-PASS-17.2 lands:
        //   val lowg = aerodromeWithProcedure(LOWG, SidId("BUDOV1A"))
        //   val ljmb = aerodromeWithProcedure(LJMB, SidId("BUDOV1A"))  // same ID, different shape
        //   val route = buildSidDepartureRoute(LJMB, SidId("BUDOV1A"), world = listOf(lowg, ljmb))
        //   assertEquals(ljmb.procedures.sids[SidId("BUDOV1A")], route.source)
    }
}
