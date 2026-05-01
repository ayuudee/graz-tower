package xyz.easiersaid.twr.pilot

import kotlin.test.Ignore
import kotlin.test.Test

/**
 * `@Ignore`d placeholder tests pinning eventual contracts for deferred work.
 *
 * Each `@Ignore` here corresponds to a deferment in the pilot-firewall plan's
 * deferments register (`/home/andrew/.claude/plans/pilot-firewall.md`).
 * When the deferment is picked up, the implementer flips `@Ignore` off and
 * the test becomes a real-job verification of the contract.
 *
 * Pinning the contract in code (rather than only in prose) makes the
 * deferment auditable — a future plan or implementer cannot quietly choose
 * a different shape; the test names the shape that was promised.
 */
class DeferredContractsSpec {

    /**
     * **D-PF.1** — airport-conditional startup clearance.
     *
     * When implemented:
     *  - `Airport` (or its manifest-derived value) gains a
     *    `requiresStartupClearance: Boolean` field.
     *  - `groundDepartureTask(airport)` returns a tree with `REQUEST_STARTUP`
     *    and `AWAIT_STARTUP_APPROVAL` iff the airport requires it.
     *  - A new `CLEARANCE_DELIVERY` controller role and procedure issues
     *    `StartupApproved` in response to `Request(RequestStartup)`.
     *  - The mission tree branch is determined by airport, never by cockpit
     *    type (that's the same-treatment principle).
     *
     * This test asserts the airport-conditional shape: when the airport's
     * manifest declares `requiresStartupClearance = true`, the mission tree
     * contains the startup steps; when false, it does not.
     */
    @Ignore
    @Test
    fun `D-PF1 airport requiring startup clearance has REQUEST_STARTUP and AWAIT_STARTUP_APPROVAL`() {
        // TODO when D-PF.1 lands:
        //   val tree = groundDepartureTask(airport = lowsAirport)  // requires startup
        //   assertContains(tree.steps, MissionStep.REQUEST_STARTUP)
        //   assertContains(tree.steps, MissionStep.AWAIT_STARTUP_APPROVAL)
    }

    /**
     * **D-PF.2** — sealed `RunwayAssignmentSource` discriminator with explicit precedence.
     *
     * When implemented:
     *  - `RunwayAssignmentSource` is a sealed type:
     *    `TaxiClearance | LineUp | Takeoff | Land | Vacate`.
     *  - `PilotMission.activeRunway` becomes `RunwayAssignment?` carrying
     *    both the runway and the source.
     *  - A total `(prior, new) → RunwayAssignment` precedence function
     *    captures the rules: TaxiClearance can be replaced by anything;
     *    Takeoff replaces TaxiClearance/Land/Vacate but flags as anomalous
     *    if it overwrites Land; Vacate must agree with the most recent
     *    Takeoff/Land or fails (controller bug).
     *
     * This test asserts: a stale `AfterLandingVacateVia` issued for a
     * non-current runway during taxi-back is detected as anomalous (rather
     * than silently overwriting `activeRunway` per current last-write-wins).
     */
    @Ignore
    @Test
    fun `D-PF2 stale Vacate on non-current runway is detected as anomalous`() {
        // TODO when D-PF.2 lands.
    }

    /**
     * **D-PF.3** — airborne spawn has a runway-assignment path via FiledPlan.
     *
     * When implemented:
     *  - `FlightStrip.filed: FiledPlan?` carries `destinationRunway: RunwayId?`
     *    derived from filed approach / ATIS.
     *  - The pilot reads `mission.filed?.destinationRunway` (set at sim init
     *    from the filing event) as the initial `activeRunway`; subsequent
     *    radio updates override per D-PF.2's precedence.
     *
     * This test asserts: an aircraft spawned airborne with a `FiledPlan`
     * has a non-null `activeRunway` matching the filed destination runway,
     * before any radio contact.
     */
    @Ignore
    @Test
    fun `D-PF3 airborne-spawned aircraft with FiledPlan has activeRunway from filed plan`() {
        // TODO when D-PF.3 lands. Currently airborne spawn leaves activeRunway null
        // until the first radio runway-bearing instruction. G0 spawns at stand and
        // is unaffected. G2 (LOWG → LJMB transit) will exercise this path.
    }

    // **D-PF.5 — CLOSED.** FlightStrip is now filed-plan-only:
    // `inferIntentFromGoal(goal: HighLevelGoal?)` reads only the goal, never
    // mission tree. The dynamic Departing → Arriving transition is driven by
    // `ControllerEvent.AircraftArrivalCommitted` from `Report(RunwayVacated)`
    // observation. Verification:
    //  - structural: `inferIntentFromGoal`'s signature cannot read mission
    //    state because it has no `mission` argument;
    //  - source-text: `FirewallStripStaticTest` (`sim/jvmTest`) scans
    //    FlightStrip.kt for any `(pilotMission|mission)\\.<x>` access where
    //    `<x>` is not in `{goal, navigationMode}`;
    //  - integration: G0 asserts `groundBeliefs.aircraftIntent[ac] == Arriving`
    //    after the post-landing flow, pinning the radio→event→belief chain.
    //  - spec: `BeliefFoldSpec`'s rows for AircraftArrivalCommitted, seed
    //    idempotence, and seed-doesn't-clobber-radio.
    // The placeholder test is removed because the contract is enforced by the
    // tests above; an `@Ignore` test referencing a closed deferment is rot.

    // D-PF.6 — CLOSED (Pass 6). The placeholder is removed because the
    // contract is now enforced by:
    //  - schema: `TaxiTo` is split into `TaxiToHoldingPoint(runway)` and
    //    `TaxiToStand` in `protocol/Instruction.kt`;
    //  - architectural: `TaxiToSplitFirewallTest` (E14) asserts both new
    //    sealed leaves under `GroundInstruction` and that no `TaxiTo`
    //    leaf reappears;
    //  - spec: `ProcessInstructionRunwayDerivationSpec` pins the runway-
    //    from-field path including the multi-runway twin-row;
    //  - integration: G0's assertion (g) sealed-type match on `TaxiToStand`.

    /**
     * **D-AUDIT.3** — TIMED step durations are aircraft-type / airport-specific.
     *
     * Today `TIMED_STEP_DURATION_MS = 10_000L` for every aircraft, every
     * airport. Real run-up at GA-class is ~60-90 s; jet pre-flight is
     * several minutes. The duration is a placeholder, not a model.
     *
     * When implemented:
     *  - `(aircraftType, airport, step) → Duration` lookup from a
     *    procedure-times manifest.
     *  - PilotConstants becomes per-type lookups (composes with D-AUDIT.4).
     *  - G0's 30-min budget revisited if jet pre-flight exceeds.
     */
    @Ignore
    @Test
    fun `D-AUDIT3 RUN_UP_CHECKS duration is aircraft-type-specific`() {
        // TODO when D-AUDIT.3 lands. Today the duration is a flat 10_000 ms.
    }

    /**
     * **D-AUDIT.5** — Responsibility transfer has an overlap window.
     *
     * Today the controller's `responsibilities: Set<AircraftId>` flips
     * cleanly at readback receipt: the sending controller drops, the
     * receiving controller adds. Real ATC has a brief overlap (the
     * receiving controller monitors before the pilot calls; the sending
     * controller doesn't release until confirmed received). Treating the
     * transition as a single edge means we can't model handoff failures.
     *
     * When implemented:
     *  - Sealed `ResponsibilityState { Owned ; HandingOff(to, since) ;
     *    Watching(from) }` per (aircraft, controller) pair.
     *  - Timeout on `HandingOff` fires `PilotDidNotCallNewController`.
     */
    @Ignore
    @Test
    fun `D-AUDIT5 responsibility transfer has an overlap window`() {
        // TODO when D-AUDIT.5 lands.
    }

    /**
     * **D-AUDIT.6** — Flight-plan filing is a separate event from spawn.
     *
     * Today `Step.handleSpawn` inserts an aircraft with a fully-formed
     * `PilotMission`; the strip and the aircraft appear simultaneously.
     * Real flight plans are filed minutes-to-hours before the aircraft
     * physically appears. The strip pre-exists its aircraft.
     *
     * When implemented:
     *  - `SimEvent.FlightPlanFiled(filed: FiledPlan, atTime: SimTime)`
     *    creates the strip on the relevant controllers.
     *  - Spawn becomes the kinematic-appearance event, separate, possibly
     *    later.
     */
    @Ignore
    @Test
    fun `D-AUDIT6 flight-plan filing is separate from kinematic spawn`() {
        // TODO when D-AUDIT.6 lands.
    }

    /**
     * **D-AUDIT.10** — Test fixture stops mutating `responsibilities` directly.
     *
     * Today G0 starts with `ground.responsibilities = setOf(aircraftId)` —
     * the test injects directly. After D-AUDIT.6, the strip-arrival event
     * is the legitimate path that adds to responsibilities. The fixture
     * cheat goes away.
     */
    @Ignore
    @Test
    fun `D-AUDIT10 fixture populates responsibilities via FlightPlanFiled, not direct mutation`() {
        // TODO when D-AUDIT.6+10 land together.
    }
}
