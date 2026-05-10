package xyz.easiersaid.twr.controller.observe

import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.DecisionTrace
import xyz.easiersaid.twr.protocol.ConfirmInstruction
import xyz.easiersaid.twr.protocol.RegulationDatabase
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.TransmittingBlind
import xyz.easiersaid.twr.protocol.Urgency

/**
 * Pass 9 (D-AUDIT.2): emit escalation outputs for coordinations that
 * just-transitioned this cycle (i.e., `emittedAt == null` on the leaf).
 *
 *  - [CoordinationState.Querying] with `emittedAt == null`
 *      → `ConfirmInstruction(target, instruction)` per CAP 413 Glossary /
 *        Doc 4444 §12.3.1.2.
 *  - [CoordinationState.Reissued] with `emittedAt == null`
 *      → re-emit the original [xyz.easiersaid.twr.protocol.AtcInstruction]
 *        via [ControllerOutput.Instruct] so it goes through the standard
 *        transmission pipeline. The phraseology "I SAY AGAIN" prefix is
 *        a future formatter concern, not encoded on the data class.
 *
 *        **Replay-as-original policy** (post-impl review M.2): the
 *        re-emission carries the *original* `AtcInstruction` verbatim,
 *        not a freshly-enriched version. Per Doc 4444 §12.3.1.2 "I SAY
 *        AGAIN" replays the original transmission — the controller is
 *        verifying the original was received, not issuing a fresh
 *        instruction whose terms have shifted. So the re-emit
 *        intentionally bypasses `enrichInstruction`. If the wind has
 *        changed since `issuedAt`, that is a *new* instruction, not a
 *        re-issue — the controller's procedure rules would emit a fresh
 *        clearance instead, which goes through enrichment via the
 *        normal path.
 *  - [CoordinationState.LostCommsDeclared]
 *      → no on-frequency phraseology (Doc 4444 §15.1.4 — controller
 *        transmits blind, never declares on the working frequency).
 *        The state record alone is the operational signal;
 *        `D-AUDIT.2.A-FOLLOWUP` files the future blind-transmission emission.
 *  - [CoordinationState.Issued]
 *      → no emission (the original instruction was already sent by
 *        `recordCoordinations`).
 *
 * Called from `Controller.kt` *after* [escalateOverdueCoordinations] (so
 * the just-transitioned states are visible) and *before* the companion
 * [markCoordinationEscalationsEmitted] (which bumps `emittedAt` to dampen
 * cycle re-emission).
 *
 * The [now] parameter is threaded into trace descriptions for diagnostic
 * post-mortem ("queried at T+10.5 s"); without it, escalation traces lose
 * their temporal anchor.
 *
 * **Conditional-clearance recursion policy**: when the wrapped instruction
 * is `ConditionalClearance(condition, inner)`, the controller confirms /
 * re-emits the *wrapper*, not the inner. The pilot heard the conditional
 * clearance; that's what they'd be expected to read back.
 */
internal fun coordinationEscalationOutputs(
    beliefs: BeliefState,
    now: SimTime,
): List<ControllerOutput> =
    beliefs.coordinations.flatMap { (aircraft, coords) ->
        coords.mapNotNull { coordination ->
            when (val state = coordination.state) {
                is CoordinationState.Querying -> queryEscalationOutput(aircraft, coordination, now, state)
                is CoordinationState.Reissued -> reissueEscalationOutput(coordination, now, state)
                is CoordinationState.LostCommsDeclared -> blindEscalationOutput(aircraft, coordination, now, state)
                is CoordinationState.Issued -> null
            }
        }
    }

private fun queryEscalationOutput(
    aircraft: xyz.easiersaid.twr.protocol.AircraftId,
    coordination: OutstandingCoordination,
    now: SimTime,
    state: CoordinationState.Querying,
): ControllerOutput? {
    if (state.emittedAt != null) return null
    val ageSec = (now - coordination.issuedAt).millis / 1000.0
    return ControllerOutput.Respond(
        target = aircraft,
        response = ConfirmInstruction(target = aircraft, instruction = coordination.readbackInstruction),
        trace = DecisionTrace(
            ruleId = "COORD-QUERY",
            description = "Readback overdue (issued ${"%.1f".format(ageSec)} s ago) — confirm prior " +
                "instruction (CAP 413 / Doc 4444 §12.3.1.2)",
            regulations = listOf(RegulationDatabase.ICAO9432_READBACK),
        ),
    )
}

private fun reissueEscalationOutput(
    coordination: OutstandingCoordination,
    now: SimTime,
    state: CoordinationState.Reissued,
): ControllerOutput? {
    if (state.emittedAt != null) return null
    val ageSec = (now - coordination.issuedAt).millis / 1000.0
    return ControllerOutput.Instruct.fromCoordinationReissue(
        coordination = coordination,
        urgency = Urgency.TIME_SENSITIVE,
        trace = DecisionTrace(
            ruleId = "COORD-REISSUE",
            description = "Re-issue instruction after query unanswered (attempt ${state.attemptCount}, " +
                "issued ${"%.1f".format(ageSec)} s ago; Doc 4444 §12.3.1.2)",
            regulations = listOf(RegulationDatabase.ICAO9432_READBACK),
        ),
        // Replay-as-original (M.2): re-emit the original instruction verbatim,
        // NOT enrichInstruction(c.instruction, weather). §12.3.1.2 "I SAY
        // AGAIN" replays the original transmission.
    )
}

private fun blindEscalationOutput(
    aircraft: xyz.easiersaid.twr.protocol.AircraftId,
    coordination: OutstandingCoordination,
    now: SimTime,
    state: CoordinationState.LostCommsDeclared,
): ControllerOutput? {
    if (state.emittedBlindAt != null) return null
    val ageSec = (now - coordination.issuedAt).millis / 1000.0
    return ControllerOutput.Respond(
        target = aircraft,
        response = TransmittingBlind(target = aircraft, instruction = coordination.readbackInstruction),
        trace = DecisionTrace(
            ruleId = "COORD-BLIND",
            description = "Lost-comms posture — transmit blind (issued ${"%.1f".format(ageSec)} s ago; " +
                "Doc 4444 §12.3.1.4 / §15.1.4)",
            regulations = listOf(RegulationDatabase.ICAO9432_READBACK),
        ),
    )
}

/**
 * Bump emission timestamps on every just-emitted entry. Companion to
 * [coordinationEscalationOutputs]; the two are paired and called together.
 * Splitting them keeps each one pure.
 *
 * - `Querying.emittedAt`: bumped from null to [now] (Pass 9).
 * - `Reissued.emittedAt`: bumped from null to [now] (Pass 9).
 * - `LostCommsDeclared.emittedBlindAt`: bumped from null to [now]
 *   (Pass 12 D-AUDIT.2.A).
 */
internal fun BeliefState.markCoordinationEscalationsEmitted(now: SimTime): BeliefState {
    if (coordinations.isEmpty()) return this
    var changed = false
    val updated = coordinations.mapValues { (_, coords) ->
        coords.map { c ->
            val nextState = when (val s = c.state) {
                is CoordinationState.Querying ->
                    if (s.emittedAt == null) { changed = true; s.copy(emittedAt = now) } else s
                is CoordinationState.Reissued ->
                    if (s.emittedAt == null) { changed = true; s.copy(emittedAt = now) } else s
                is CoordinationState.LostCommsDeclared ->
                    if (s.emittedBlindAt == null) { changed = true; s.copy(emittedBlindAt = now) } else s
                is CoordinationState.Issued -> s
            }
            if (nextState === c.state) c else c.copy(state = nextState)
        }
    }
    return if (!changed) this else copy(coordinations = updated)
}
