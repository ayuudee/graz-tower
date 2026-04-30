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
 */
sealed interface RunwayAssignmentSource {
    /** Runway assigned via taxi clearance (TaxiTo to a holding-point on a runway). */
    data object TaxiClearance : RunwayAssignmentSource
    /** Runway assigned via LineUpAndWait clearance. */
    data object LineUp : RunwayAssignmentSource
    /** Runway assigned via ClearedForTakeoff clearance. */
    data object Takeoff : RunwayAssignmentSource
    /** Runway assigned via ClearedToLand clearance. */
    data object Land : RunwayAssignmentSource
    /** Runway assigned via ClearedTouchAndGo clearance. */
    data object TouchAndGo : RunwayAssignmentSource
    /** Runway assigned via BacktrackRunway clearance. */
    data object Backtrack : RunwayAssignmentSource
}

/**
 * The active runway assignment for an aircraft, including which clearance
 * class set it. Carried by both pilot mission and controller belief state
 * post-Pass-5.
 */
data class RunwayAssignment(
    val runway: RunwayId,
    val source: RunwayAssignmentSource,
)

/**
 * Anomalous transitions detected by [applyPrecedence].
 *
 * Pass 5 names one leaf today; the sealed surface extends without
 * restructuring as Pass 7's coordination ledger learns to react to more.
 */
sealed interface AnomalousAssignment {
    val prior: RunwayAssignment
    val new: RunwayAssignment

    /**
     * `Takeoff` issued when the prior was `Land` for a *different* runway.
     * Indicates either a controller error (pilot was cleared to land 16C
     * but controller is now clearing takeoff on 16L for the same aircraft)
     * or a re-issue after diversion. Pass 5 flags; Pass 7's coordination
     * ledger decides how to react (cancel prior, query, re-issue).
     */
    data class TakeoffOverridesDifferentRunwayLand(
        override val prior: RunwayAssignment,
        override val new: RunwayAssignment,
    ) : AnomalousAssignment
}

/**
 * Total over (prior, new). Every cell of the 6×6 [RunwayAssignmentSource]
 * cross-product has an explicit named arm.
 *
 * **Pass 5 surface, not its semantics.** Pass 5 flags exactly *one* cell —
 * `(Land, Takeoff, sameRunway = false)` → [AnomalousAssignment.TakeoffOverridesDifferentRunwayLand].
 * Every other cell returns `Either.Right(new)` with a documented rationale.
 * The function is therefore "last-write-wins, with one anomaly leaf carved
 * out" — the *structure* (sealed source × explicit cells × sealed
 * anomaly type) is the extension point. Pass 7's coordination ledger will
 * promote additional cells (the obvious candidates are marked below with
 * `// Pass-7 candidate:` comments) into named [AnomalousAssignment] leaves
 * without restructuring this function.
 *
 * **No default branch.** Per FP review M3, every cell is named. Kotlin
 * doesn't infer exhaustiveness over `when (Pair(a, b))` of sealed types
 * (it treats `Pair<X, Y>` as opaque generic), so the function dispatches
 * via outer-`when` on `prior.source` then inner-`when` on `new.source`.
 * Adding a new [RunwayAssignmentSource] forces a compile-time decision
 * over every existing source it can interact with.
 */
fun applyPrecedence(
    prior: Option<RunwayAssignment>,
    new: RunwayAssignment,
): Either<AnomalousAssignment, RunwayAssignment> {
    val priorAssignment = prior.getOrNull() ?: return Either.Right(new)
    val priorSrc = priorAssignment.source
    val newSrc = new.source
    val sameRunway = priorAssignment.runway == new.runway
    return when (priorSrc) {
        // ── Prior = TaxiClearance ─────────────────────────────────────────
        // Taxi to a holding point is a "pre-runway" assignment. Any clearance
        // class that follows is a stage progression toward the runway operation.
        RunwayAssignmentSource.TaxiClearance -> when (newSrc) {
            // Same source, same/different runway: controller corrected the taxi clearance.
            RunwayAssignmentSource.TaxiClearance -> Either.Right(new)
            // Stage progression: taxi → lineup. Canonical.
            RunwayAssignmentSource.LineUp -> Either.Right(new)
            // Stage progression: taxi → cleared-for-takeoff. Skips LineUp (immediate-takeoff).
            RunwayAssignmentSource.Takeoff -> Either.Right(new)
            // Stage progression: taxi → cleared-to-land. Aircraft was taxiing for departure
            // but controller now clears for an arrival (re-purposed slot, mid-pattern).
            RunwayAssignmentSource.Land -> Either.Right(new)
            // Stage progression: taxi → cleared-T&G. Same as above for circuit traffic.
            RunwayAssignmentSource.TouchAndGo -> Either.Right(new)
            // Stage progression: taxi → backtrack (unusual but legitimate at small fields).
            RunwayAssignmentSource.Backtrack -> Either.Right(new)
        }
        // ── Prior = LineUp ────────────────────────────────────────────────
        // Aircraft is holding short or lined up on the runway awaiting takeoff.
        RunwayAssignmentSource.LineUp -> when (newSrc) {
            // Same source: controller restated the lineup clearance.
            RunwayAssignmentSource.LineUp -> Either.Right(new)
            // Canonical progression: lineup → cleared-for-takeoff. The expected sequence.
            RunwayAssignmentSource.Takeoff -> Either.Right(new)
            // Pass-7 candidate: lineup → taxi-back is unusual; controller may be
            // clearing a different aircraft conflict by sending this one back.
            RunwayAssignmentSource.TaxiClearance -> Either.Right(new)
            // Pass-7 candidate: lineup → land would mean a takeoff slot was reassigned
            // to an arrival (likely a runway-config change mid-rollout). Worth flagging.
            RunwayAssignmentSource.Land -> Either.Right(new)
            RunwayAssignmentSource.TouchAndGo -> Either.Right(new)
            // Pass-7 candidate: lineup → backtrack means controller wants a runway-length
            // increase (long takeoff). Real but worth flagging because it cancels the lineup.
            RunwayAssignmentSource.Backtrack -> Either.Right(new)
        }
        // ── Prior = Takeoff ───────────────────────────────────────────────
        // Aircraft has been cleared for takeoff (and is rolling, or about to).
        RunwayAssignmentSource.Takeoff -> when (newSrc) {
            // Same source: re-issue or restatement.
            RunwayAssignmentSource.Takeoff -> Either.Right(new)
            // Pass-7 candidates: every transition out of Takeoff is suspicious
            // (the aircraft is committed to departure). Some are legitimate
            // (mid-roll abort sends back to taxi); others are likely controller
            // error. Pass 7 will promote the suspect cells to named anomalies.
            RunwayAssignmentSource.TaxiClearance -> Either.Right(new)
            RunwayAssignmentSource.LineUp -> Either.Right(new)
            RunwayAssignmentSource.Land -> Either.Right(new)
            RunwayAssignmentSource.TouchAndGo -> Either.Right(new)
            RunwayAssignmentSource.Backtrack -> Either.Right(new)
        }
        // ── Prior = Land ──────────────────────────────────────────────────
        // Aircraft has been cleared to land (and is on approach, or has touched down).
        RunwayAssignmentSource.Land -> when (newSrc) {
            // Same source: re-issue or restatement (e.g. after a missed-approach).
            RunwayAssignmentSource.Land -> Either.Right(new)
            // **Pass 5 anomaly leaf.** `Land` on runway X then `Takeoff` on runway Y:
            // either a go-around-and-replacement-clearance on a different runway
            // (legitimate — but worth flagging so the controller's coordination
            // ledger can verify the cancellation of the prior Land), or a direct
            // controller error. Same runway is accepted (T&G converted, or re-issue).
            RunwayAssignmentSource.Takeoff -> if (sameRunway) Either.Right(new)
                else Either.Left(AnomalousAssignment.TakeoffOverridesDifferentRunwayLand(priorAssignment, new))
            // Land ↔ TouchAndGo: legitimate intent change mid-pattern (pilot reports
            // late FULL_STOP after T&G clearance, or vice-versa).
            RunwayAssignmentSource.TouchAndGo -> Either.Right(new)
            // Pass-7 candidates: post-Land → taxi/lineup/backtrack on the *active
            // landing runway* makes sense (post-roll vacate, lineup for missed-app
            // rejoin); on a *different* runway is suspect.
            RunwayAssignmentSource.TaxiClearance -> Either.Right(new)
            RunwayAssignmentSource.LineUp -> Either.Right(new)
            RunwayAssignmentSource.Backtrack -> Either.Right(new)
        }
        // ── Prior = TouchAndGo ────────────────────────────────────────────
        // Aircraft has been cleared for touch-and-go (will land, then take off again).
        RunwayAssignmentSource.TouchAndGo -> when (newSrc) {
            // Same source: re-issue or restatement.
            RunwayAssignmentSource.TouchAndGo -> Either.Right(new)
            // Land ↔ TouchAndGo: legitimate intent change.
            RunwayAssignmentSource.Land -> Either.Right(new)
            // Pass-7 candidates: TouchAndGo → other ops mid-roll. Most suspect.
            RunwayAssignmentSource.TaxiClearance -> Either.Right(new)
            RunwayAssignmentSource.LineUp -> Either.Right(new)
            RunwayAssignmentSource.Takeoff -> Either.Right(new)
            RunwayAssignmentSource.Backtrack -> Either.Right(new)
        }
        // ── Prior = Backtrack ─────────────────────────────────────────────
        // Aircraft is backtracking the runway (taxi against the in-use direction).
        // Ends when the controller assigns the next op.
        RunwayAssignmentSource.Backtrack -> when (newSrc) {
            // Same source: re-issue.
            RunwayAssignmentSource.Backtrack -> Either.Right(new)
            // Canonical end-of-backtrack progressions.
            RunwayAssignmentSource.LineUp -> Either.Right(new)
            RunwayAssignmentSource.Takeoff -> Either.Right(new)
            RunwayAssignmentSource.TaxiClearance -> Either.Right(new)
            // Pass-7 candidates: backtrack → land/T&G is unusual at small fields.
            RunwayAssignmentSource.Land -> Either.Right(new)
            RunwayAssignmentSource.TouchAndGo -> Either.Right(new)
        }
    }
}
