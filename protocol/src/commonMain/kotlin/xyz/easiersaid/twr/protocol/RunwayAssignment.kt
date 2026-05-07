package xyz.easiersaid.twr.protocol

import arrow.core.Either
import arrow.core.Option

/**
 * The clearance class that assigned the runway.
 *
 * Pass 5 (D-PF.2 closure): replaces source-less `Option<RunwayId>` carries on
 * both [xyz.easiersaid.twr.pilot.PilotMission] and the controller's belief
 * state. Without a source tag, last-write-wins silently accepts anomalous
 * orderings (e.g. an `AfterLandingVacateVia` overwriting an active
 * `ClearedToLand`). The sealed source set lets [applyPrecedence] flag the
 * subset of (prior, new) transitions that real ATC treats as anomalous.
 *
 * G2 (D-PF.3): adds [Filing] and groups the existing six radio-derived
 * sources under a [Radio] sub-sealed interface. [Filing] is init-only —
 * produced exclusively by `createMission` from `FiledPlan.destinationRunway`
 * — and never appears as the `new` argument to [applyPrecedence] (the
 * function's `new` parameter narrows to [Radio]). The 7×6 = 42 cells of
 * the precedence table are all production-realistic; there are no dead arms.
 */
sealed interface RunwayAssignmentSource {
    /**
     * Runway pre-set from the filed plan at sim-init.
     *
     * Filing is the pilot's pre-radio expectation. Once any [Radio] source
     * supersedes via [applyPrecedence], the Filing tag is gone.
     *
     * [Filing] is **init-only**: it is produced solely by `createMission`
     * from `FiledPlan.destinationRunway` and is never returned by
     * `runwayFromInstruction`. The type of [applyPrecedence]'s `new` argument
     * is [Radio], so Filing-as-`new` is unrepresentable.
     */
    data object Filing : RunwayAssignmentSource

    /**
     * Radio-derived runway sources — the six clearance classes that mention
     * a runway in their typed shape. Produced by `runwayFromInstruction`
     * total over the AtcInstruction sealed hierarchy.
     */
    sealed interface Radio : RunwayAssignmentSource {
        /** Runway assigned via taxi clearance (TaxiTo to a holding-point on a runway). */
        data object TaxiClearance : Radio
        /** Runway assigned via LineUpAndWait clearance. */
        data object LineUp : Radio
        /** Runway assigned via ClearedForTakeoff clearance. */
        data object Takeoff : Radio
        /** Runway assigned via ClearedToLand clearance. */
        data object Land : Radio
        /** Runway assigned via ClearedTouchAndGo clearance. */
        data object TouchAndGo : Radio
        /** Runway assigned via BacktrackRunway clearance. */
        data object Backtrack : Radio
    }
}

/**
 * The active runway assignment for an aircraft, including which clearance
 * class set it. Carried by both pilot mission and controller belief state
 * post-Pass-5.
 *
 * G2 (Option B): parametric on the source type so that radio-derived
 * assignments (`RunwayAssignment<RunwayAssignmentSource.Radio>`) and
 * filing-derived assignments (`RunwayAssignment<RunwayAssignmentSource.Filing>`)
 * can be type-distinguished where it matters (e.g. [applyPrecedence]'s `new`
 * parameter), while still composing as
 * `RunwayAssignment<RunwayAssignmentSource>` where any source is acceptable
 * (mission storage, anomaly carriage). `out S` makes the type covariant in
 * the source — `RunwayAssignment<Radio>` is a subtype of
 * `RunwayAssignment<RunwayAssignmentSource>`.
 */
data class RunwayAssignment<out S : RunwayAssignmentSource>(
    val runway: RunwayId,
    val source: S,
)

/**
 * Anomalous transitions detected by [applyPrecedence].
 *
 * Pass 5 names one leaf today; the sealed surface extends without
 * restructuring as Pass 7's coordination ledger learns to react to more.
 */
sealed interface AnomalousAssignment {
    val prior: RunwayAssignment<RunwayAssignmentSource>
    val new: RunwayAssignment<RunwayAssignmentSource.Radio>

    /**
     * `Takeoff` issued when the prior was `Land` for a *different* runway.
     * Indicates either a controller error (pilot was cleared to land 16C
     * but controller is now clearing takeoff on 16L for the same aircraft)
     * or a re-issue after diversion. Pass 5 flags; Pass 7's coordination
     * ledger decides how to react (cancel prior, query, re-issue).
     */
    data class TakeoffOverridesDifferentRunwayLand(
        override val prior: RunwayAssignment<RunwayAssignmentSource>,
        override val new: RunwayAssignment<RunwayAssignmentSource.Radio>,
    ) : AnomalousAssignment
}

/**
 * Total over (prior, new). Every cell of the 7×6 source cross-product
 * (`RunwayAssignmentSource × RunwayAssignmentSource.Radio`) has an explicit
 * named arm.
 *
 * **G2 (Option B): Filing-as-`new` is unrepresentable.** The `new` parameter
 * is typed `RunwayAssignment<RunwayAssignmentSource.Radio>` — the type
 * system rules out Filing as a new source. Filing only enters via
 * `createMission` and only ever appears as `prior`.
 *
 * **Pass 5 surface, not its semantics.** Pass 5 flagged exactly *one* cell —
 * `(Land, Takeoff, sameRunway = false)` → [AnomalousAssignment.TakeoffOverridesDifferentRunwayLand].
 * Every other cell returns `Either.Right(new)` with a documented rationale.
 *
 * **No default branch.** Per FP review M3, every cell is named. Kotlin
 * doesn't infer exhaustiveness over `when (Pair(a, b))` of sealed types
 * (it treats `Pair<X, Y>` as opaque generic), so the function dispatches
 * via outer-`when` on `prior.source` then inner-`when` on `new.source`.
 * Adding a new [RunwayAssignmentSource] forces a compile-time decision
 * over every existing source it can interact with.
 *
 * **Filing as `prior` semantics:** filing always loses to radio. Every
 * `(Filing, *)` cell returns `Either.Right(new)`. There is no anomaly leaf
 * for Filing — the filed expectation is informational, not a clearance.
 */
fun applyPrecedence(
    prior: Option<RunwayAssignment<RunwayAssignmentSource>>,
    new: RunwayAssignment<RunwayAssignmentSource.Radio>,
): Either<AnomalousAssignment, RunwayAssignment<RunwayAssignmentSource>> {
    val priorAssignment = prior.getOrNull() ?: return Either.Right(new)
    val priorSrc = priorAssignment.source
    val newSrc = new.source
    val sameRunway = priorAssignment.runway == new.runway
    return when (priorSrc) {
        // ── Prior = Filing (G2) ───────────────────────────────────────────
        // Filed-plan expectation. Any radio source supersedes — radio is
        // current authority; filing is pre-radio intention.
        RunwayAssignmentSource.Filing -> Either.Right(new)
        // ── Prior = TaxiClearance ─────────────────────────────────────────
        // Taxi to a holding point is a "pre-runway" assignment. Any clearance
        // class that follows is a stage progression toward the runway operation.
        RunwayAssignmentSource.Radio.TaxiClearance -> when (newSrc) {
            // Same source, same/different runway: controller corrected the taxi clearance.
            RunwayAssignmentSource.Radio.TaxiClearance -> Either.Right(new)
            // Stage progression: taxi → lineup. Canonical.
            RunwayAssignmentSource.Radio.LineUp -> Either.Right(new)
            // Stage progression: taxi → cleared-for-takeoff. Skips LineUp (immediate-takeoff).
            RunwayAssignmentSource.Radio.Takeoff -> Either.Right(new)
            // Stage progression: taxi → cleared-to-land. Aircraft was taxiing for departure
            // but controller now clears for an arrival (re-purposed slot, mid-pattern).
            RunwayAssignmentSource.Radio.Land -> Either.Right(new)
            // Stage progression: taxi → cleared-T&G. Same as above for circuit traffic.
            RunwayAssignmentSource.Radio.TouchAndGo -> Either.Right(new)
            // Stage progression: taxi → backtrack (unusual but legitimate at small fields).
            RunwayAssignmentSource.Radio.Backtrack -> Either.Right(new)
        }
        // ── Prior = LineUp ────────────────────────────────────────────────
        // Aircraft is holding short or lined up on the runway awaiting takeoff.
        RunwayAssignmentSource.Radio.LineUp -> when (newSrc) {
            // Same source: controller restated the lineup clearance.
            RunwayAssignmentSource.Radio.LineUp -> Either.Right(new)
            // Canonical progression: lineup → cleared-for-takeoff. The expected sequence.
            RunwayAssignmentSource.Radio.Takeoff -> Either.Right(new)
            // Pass-7 candidate: lineup → taxi-back is unusual; controller may be
            // clearing a different aircraft conflict by sending this one back.
            RunwayAssignmentSource.Radio.TaxiClearance -> Either.Right(new)
            // Pass-7 candidate: lineup → land would mean a takeoff slot was reassigned
            // to an arrival (likely a runway-config change mid-rollout). Worth flagging.
            RunwayAssignmentSource.Radio.Land -> Either.Right(new)
            RunwayAssignmentSource.Radio.TouchAndGo -> Either.Right(new)
            // Pass-7 candidate: lineup → backtrack means controller wants a runway-length
            // increase (long takeoff). Real but worth flagging because it cancels the lineup.
            RunwayAssignmentSource.Radio.Backtrack -> Either.Right(new)
        }
        // ── Prior = Takeoff ───────────────────────────────────────────────
        // Aircraft has been cleared for takeoff (and is rolling, or about to).
        RunwayAssignmentSource.Radio.Takeoff -> when (newSrc) {
            // Same source: re-issue or restatement.
            RunwayAssignmentSource.Radio.Takeoff -> Either.Right(new)
            // Pass-7 candidates: every transition out of Takeoff is suspicious
            // (the aircraft is committed to departure). Some are legitimate
            // (mid-roll abort sends back to taxi); others are likely controller
            // error. Pass 7 will promote the suspect cells to named anomalies.
            RunwayAssignmentSource.Radio.TaxiClearance -> Either.Right(new)
            RunwayAssignmentSource.Radio.LineUp -> Either.Right(new)
            RunwayAssignmentSource.Radio.Land -> Either.Right(new)
            RunwayAssignmentSource.Radio.TouchAndGo -> Either.Right(new)
            RunwayAssignmentSource.Radio.Backtrack -> Either.Right(new)
        }
        // ── Prior = Land ──────────────────────────────────────────────────
        // Aircraft has been cleared to land (and is on approach, or has touched down).
        RunwayAssignmentSource.Radio.Land -> when (newSrc) {
            // Same source: re-issue or restatement (e.g. after a missed-approach).
            RunwayAssignmentSource.Radio.Land -> Either.Right(new)
            // **Pass 5 anomaly leaf.** `Land` on runway X then `Takeoff` on runway Y:
            // either a go-around-and-replacement-clearance on a different runway
            // (legitimate — but worth flagging so the controller's coordination
            // ledger can verify the cancellation of the prior Land), or a direct
            // controller error. Same runway is accepted (T&G converted, or re-issue).
            RunwayAssignmentSource.Radio.Takeoff -> if (sameRunway) Either.Right(new)
                else Either.Left(AnomalousAssignment.TakeoffOverridesDifferentRunwayLand(priorAssignment, new))
            // Land ↔ TouchAndGo: legitimate intent change mid-pattern (pilot reports
            // late FULL_STOP after T&G clearance, or vice-versa).
            RunwayAssignmentSource.Radio.TouchAndGo -> Either.Right(new)
            // Pass-7 candidates: post-Land → taxi/lineup/backtrack on the *active
            // landing runway* makes sense (post-roll vacate, lineup for missed-app
            // rejoin); on a *different* runway is suspect.
            RunwayAssignmentSource.Radio.TaxiClearance -> Either.Right(new)
            RunwayAssignmentSource.Radio.LineUp -> Either.Right(new)
            RunwayAssignmentSource.Radio.Backtrack -> Either.Right(new)
        }
        // ── Prior = TouchAndGo ────────────────────────────────────────────
        // Aircraft has been cleared for touch-and-go (will land, then take off again).
        RunwayAssignmentSource.Radio.TouchAndGo -> when (newSrc) {
            // Same source: re-issue or restatement.
            RunwayAssignmentSource.Radio.TouchAndGo -> Either.Right(new)
            // Land ↔ TouchAndGo: legitimate intent change.
            RunwayAssignmentSource.Radio.Land -> Either.Right(new)
            // Pass-7 candidates: TouchAndGo → other ops mid-roll. Most suspect.
            RunwayAssignmentSource.Radio.TaxiClearance -> Either.Right(new)
            RunwayAssignmentSource.Radio.LineUp -> Either.Right(new)
            RunwayAssignmentSource.Radio.Takeoff -> Either.Right(new)
            RunwayAssignmentSource.Radio.Backtrack -> Either.Right(new)
        }
        // ── Prior = Backtrack ─────────────────────────────────────────────
        // Aircraft is backtracking the runway (taxi against the in-use direction).
        // Ends when the controller assigns the next op.
        RunwayAssignmentSource.Radio.Backtrack -> when (newSrc) {
            // Same source: re-issue.
            RunwayAssignmentSource.Radio.Backtrack -> Either.Right(new)
            // Canonical end-of-backtrack progressions.
            RunwayAssignmentSource.Radio.LineUp -> Either.Right(new)
            RunwayAssignmentSource.Radio.Takeoff -> Either.Right(new)
            RunwayAssignmentSource.Radio.TaxiClearance -> Either.Right(new)
            // Pass-7 candidates: backtrack → land/T&G is unusual at small fields.
            RunwayAssignmentSource.Radio.Land -> Either.Right(new)
            RunwayAssignmentSource.Radio.TouchAndGo -> Either.Right(new)
        }
    }
}
