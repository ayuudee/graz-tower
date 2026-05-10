package xyz.easiersaid.twr.controller.observe

import arrow.core.NonEmptyList
import arrow.core.toNonEmptyListOrNull
import xyz.easiersaid.twr.protocol.requiredReadbackAtoms
import xyz.easiersaid.twr.protocol.defectsHaveWrongValue
import xyz.easiersaid.twr.protocol.AfterDepartureCondition
import xyz.easiersaid.twr.protocol.AfterFixCondition
import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
import xyz.easiersaid.twr.protocol.AfterPassingLevelClimbTo
import xyz.easiersaid.twr.protocol.AfterPassingLevelDescendTo
import xyz.easiersaid.twr.protocol.AfterTrafficCondition
import xyz.easiersaid.twr.protocol.AirTaxiTo
import xyz.easiersaid.twr.protocol.AtDistanceCondition
import xyz.easiersaid.twr.protocol.AtLevelCondition
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.AtomDefect
import xyz.easiersaid.twr.protocol.AtomicReadback
import xyz.easiersaid.twr.protocol.AvoidArea
import xyz.easiersaid.twr.protocol.AvoidLevel
import xyz.easiersaid.twr.protocol.BacktrackReadback
import xyz.easiersaid.twr.protocol.BacktrackRunway
import xyz.easiersaid.twr.protocol.BehindTrafficCondition
import xyz.easiersaid.twr.protocol.BreakOff
import xyz.easiersaid.twr.protocol.BreakOffReadback
import xyz.easiersaid.twr.protocol.CancelClearance
import xyz.easiersaid.twr.protocol.ClearedApproach
import xyz.easiersaid.twr.protocol.ClearedApproachReadback
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedForTakeoffReadback
import xyz.easiersaid.twr.protocol.ClearedLowApproach
import xyz.easiersaid.twr.protocol.ClearedLowApproachReadback
import xyz.easiersaid.twr.protocol.ClearedTo
import xyz.easiersaid.twr.protocol.ClearedToEnterControlZone
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedToLandReadback
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.ClearedTouchAndGoReadback
import xyz.easiersaid.twr.protocol.ClearedVisualApproach
import xyz.easiersaid.twr.protocol.ClimbTo
import xyz.easiersaid.twr.protocol.CommenceApproachAt
import xyz.easiersaid.twr.protocol.ConditionalClearance
import xyz.easiersaid.twr.protocol.ConditionalElement
import xyz.easiersaid.twr.protocol.ConditionalPredicate
import xyz.easiersaid.twr.protocol.ConfirmSquawk
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ContinueApproach
import xyz.easiersaid.twr.protocol.ContinuePresentHeading
import xyz.easiersaid.twr.protocol.CrossRunway
import xyz.easiersaid.twr.protocol.CrossRunwayReadback
import xyz.easiersaid.twr.protocol.DescendTo
import xyz.easiersaid.twr.protocol.DescendWhenReady
import xyz.easiersaid.twr.protocol.Disregard
import xyz.easiersaid.twr.protocol.DisregardAcknowledgementReadback
import xyz.easiersaid.twr.protocol.DivertTo
import xyz.easiersaid.twr.protocol.ExpediteClimb
import xyz.easiersaid.twr.protocol.ExpediteDescend
import xyz.easiersaid.twr.protocol.ExpediteTaxi
import xyz.easiersaid.twr.protocol.ExtendDownwind
import xyz.easiersaid.twr.protocol.ExtendDownwindReadback
import xyz.easiersaid.twr.protocol.FlyHeading
import xyz.easiersaid.twr.protocol.FollowTraffic
import xyz.easiersaid.twr.protocol.FreeTextReadback
import xyz.easiersaid.twr.protocol.FrequencyReadback
import xyz.easiersaid.twr.protocol.GiveWayToTraffic
import xyz.easiersaid.twr.protocol.GoAround
import xyz.easiersaid.twr.protocol.GoAroundReadback
import xyz.easiersaid.twr.protocol.HeadingReadback
import xyz.easiersaid.twr.protocol.HoldAt
import xyz.easiersaid.twr.protocol.HoldPosition
import xyz.easiersaid.twr.protocol.HoldPositionCancelTakeoff
import xyz.easiersaid.twr.protocol.HoldReadback
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.HoldShortReadback
import xyz.easiersaid.twr.protocol.HoldingAcknowledgementReadback
import xyz.easiersaid.twr.protocol.IncreaseSpeedTo
import xyz.easiersaid.twr.protocol.InterceptLocaliser
import xyz.easiersaid.twr.protocol.JoinAirway
import xyz.easiersaid.twr.protocol.JoinAirwayReadback
import xyz.easiersaid.twr.protocol.JoinCircuit
import xyz.easiersaid.twr.protocol.LeaveHoldProceedDirect
import xyz.easiersaid.twr.protocol.LeaveHoldProceedDirectReadback
import xyz.easiersaid.twr.protocol.LevelReadback
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.LineUpReadback
import xyz.easiersaid.twr.protocol.MaintainAltitudeUntilEstablished
import xyz.easiersaid.twr.protocol.MaintainAtOrAbove
import xyz.easiersaid.twr.protocol.MaintainAtOrBelow
import xyz.easiersaid.twr.protocol.MaintainLevel
import xyz.easiersaid.twr.protocol.MaintainSpeed
import xyz.easiersaid.twr.protocol.MaintainVisualSeparation
import xyz.easiersaid.twr.protocol.MakeAnotherCircuit
import xyz.easiersaid.twr.protocol.MakeLongApproach
import xyz.easiersaid.twr.protocol.MakeShortApproach
import xyz.easiersaid.twr.protocol.MinimumCleanSpeed
import xyz.easiersaid.twr.protocol.MonitorFrequency
import xyz.easiersaid.twr.protocol.NumberInSequence
import xyz.easiersaid.twr.protocol.Orbit
import xyz.easiersaid.twr.protocol.OrbitReadback
import xyz.easiersaid.twr.protocol.PassingLevelCondition
import xyz.easiersaid.twr.protocol.PressureSettingReadback
import xyz.easiersaid.twr.protocol.ProceedDirect
import xyz.easiersaid.twr.protocol.PushbackApproved
import xyz.easiersaid.twr.protocol.PushbackFace
import xyz.easiersaid.twr.protocol.Readback
import xyz.easiersaid.twr.protocol.ReadbackCondition
import xyz.easiersaid.twr.protocol.ReduceSpeedTo
import xyz.easiersaid.twr.protocol.ReduceTaxiSpeed
import xyz.easiersaid.twr.protocol.RejoinSidAt
import xyz.easiersaid.twr.protocol.RejoinSidAtReadback
import xyz.easiersaid.twr.protocol.RemainOutsideControlledAirspace
import xyz.easiersaid.twr.protocol.ReportIntentions
import xyz.easiersaid.twr.protocol.ReportTrafficInSight
import xyz.easiersaid.twr.protocol.ReportWhen
import xyz.easiersaid.twr.protocol.ResumeNormalSpeed
import xyz.easiersaid.twr.protocol.ResumeOwnNavigation
import xyz.easiersaid.twr.protocol.ResumeOwnNavigationReadback
import xyz.easiersaid.twr.protocol.RouteAsFiled
import xyz.easiersaid.twr.protocol.RouteAsFiledReadback
import xyz.easiersaid.twr.protocol.RouteReadback
import xyz.easiersaid.twr.protocol.RouteSpec
import xyz.easiersaid.twr.protocol.RunwayInUseAdvisory
import xyz.easiersaid.twr.protocol.RunwayInUseReadback
import xyz.easiersaid.twr.protocol.RunwayReadback
import xyz.easiersaid.twr.protocol.SequenceAcknowledgementReadback
import xyz.easiersaid.twr.protocol.SetPressure
import xyz.easiersaid.twr.protocol.SetSquawk
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.SimpleElement
import xyz.easiersaid.twr.protocol.SpecialVfrClearance
import xyz.easiersaid.twr.protocol.SpecialVfrReadback
import xyz.easiersaid.twr.protocol.SpeedReadback
import xyz.easiersaid.twr.protocol.SquawkIdent
import xyz.easiersaid.twr.protocol.SquawkNormal
import xyz.easiersaid.twr.protocol.SquawkReadback
import xyz.easiersaid.twr.protocol.SquawkStandby
import xyz.easiersaid.twr.protocol.StartupApproved
import xyz.easiersaid.twr.protocol.StopClimbAt
import xyz.easiersaid.twr.protocol.StopDescentAt
import xyz.easiersaid.twr.protocol.StopImmediately
import xyz.easiersaid.twr.protocol.StopImmediatelyReadback
import xyz.easiersaid.twr.protocol.StopSquawk
import xyz.easiersaid.twr.protocol.StopTurn
import xyz.easiersaid.twr.protocol.TakeoffImmediatelyOrHoldShort
import xyz.easiersaid.twr.protocol.TakeoffImmediatelyOrHoldShortReadback
import xyz.easiersaid.twr.protocol.TakeoffImmediatelyOrVacateReadback
import xyz.easiersaid.twr.protocol.TakeoffImmediatelyOrVacateRunway
import xyz.easiersaid.twr.protocol.TaxiIntoHoldingBay
import xyz.easiersaid.twr.protocol.TaxiRouteReadback
import xyz.easiersaid.twr.protocol.TaxiViaRunway
import xyz.easiersaid.twr.protocol.TaxiViaRunwayReadback
import xyz.easiersaid.twr.protocol.TaxiWithCaution
import xyz.easiersaid.twr.protocol.TransitionLevelIssuance
import xyz.easiersaid.twr.protocol.TransitionLevelReadback
import xyz.easiersaid.twr.protocol.TurnBase
import xyz.easiersaid.twr.protocol.TurnByDegrees
import xyz.easiersaid.twr.protocol.TurnHeading
import xyz.easiersaid.twr.protocol.VacateReadback
import xyz.easiersaid.twr.protocol.VacateRunway
import xyz.easiersaid.twr.protocol.VisualApproachReadback
import xyz.easiersaid.twr.protocol.WhenAbleCondition
import xyz.easiersaid.twr.protocol.WhenAbleProceedDirect

/**
 * Doctrine-anchored timeouts for the coordination ledger lifecycle.
 *
 * Pass 9 (D-AUDIT.2): replaces the silent 30 s `MAX_READBACK_AGE` GC with
 * an explicit Issued → Querying → Reissued → LostCommsDeclared escalation.
 * Real controllers don't forget — they query, re-issue, declare lost-comms.
 *
 * Production reads [Default]. Spec tests construct short-window policies
 * so they don't sit at real-time clock for minutes. Every constant in
 * [Default] carries a citation to its doctrine source — deviation is a
 * plan revision, not a free parameter.
 */
data class ReadbackTimeoutPolicy(
    val queryAfter: SimDuration,
    val reissueAfter: SimDuration,
    val reissueInterval: SimDuration,
    val lostCommsAfter: SimDuration,
    val maxReissueAttempts: Int,
) {
    init {
        require(maxReissueAttempts >= 1) { "maxReissueAttempts must be ≥ 1, got $maxReissueAttempts" }
        require(reissueAfter > queryAfter) { "reissueAfter must exceed queryAfter (else Querying has no window)" }
        // Consistency: the attempt-count branch must fire before lost-comms
        // (the time-bound is doctrine-anchored belt-and-braces against a
        // future constant edit that breaks the relationship).
        val maxReissueWindow = reissueAfter + reissueInterval * (maxReissueAttempts - 1)
        require(maxReissueWindow < lostCommsAfter) {
            "lostCommsAfter ($lostCommsAfter) must exceed reissueAfter + (max-1) * reissueInterval ($maxReissueWindow)"
        }
    }

    companion object {
        /**
         * Production policy.
         *
         * - `queryAfter = 10 s`: ICAO Doc 4444 §4.5.7.5.3 "immediate action";
         *    operational ~10 s (NATS MATS Part 1 §1.3; Eurocontrol HUM.ET1.ST05).
         * - `reissueAfter = 30 s`: ~30 s "I SAY AGAIN" doctrine.
         * - `reissueInterval = 20 s`: between subsequent re-emits.
         * - `lostCommsAfter = 5 min`: ~5 min on working freq before lost-comms
         *    (operational; no ICAO codification).
         * - `maxReissueAttempts = 3`: standard before lost-comms.
         */
        val Default = ReadbackTimeoutPolicy(
            queryAfter = SimDuration.ofSeconds(10),
            reissueAfter = SimDuration.ofSeconds(30),
            reissueInterval = SimDuration.ofSeconds(20),
            lostCommsAfter = SimDuration.ofSeconds(300),
            maxReissueAttempts = 3,
        )
    }
}

/**
 * Validate a readback against a pending instruction by matching safety-critical atoms.
 *
 * Returns a three-state verdict ([ReadbackVerdict]) so callers can distinguish
 * "fully correct" from "incorrect atom read back" from "atom missing entirely". The
 * first maps to `ReadBackCorrect`; the other two map to a correction prompt under
 * ICAO Doc 4444 §12.3.2 / CAP 413 §1.5.6 (controller must correct an incorrect or
 * incomplete readback rather than stay silent).
 *
 * The controller-side check is intentionally structural (atom equality). Phraseology
 * fidelity (digit-by-digit verbalisation, "I say again" doubling, style adaptation)
 * is the future LLM interpretation layer's responsibility — see
 * transmission-reception-architecture design doc.
 */
fun matchReadback(instruction: AtcInstruction, readback: Readback): Boolean =
    classifyReadback(instruction, readback) is ReadbackVerdict.Correct

/**
 * Algebraic verdict for a readback against one pending instruction.
 *
 * [Correct] is the single right answer. [Incorrect] carries a non-empty, deterministically
 * ordered list of [AtomDefect]s so a correction response can be precise about *what* the
 * pilot got wrong, not just "something was wrong". Totality of the algebra means every
 * incorrect case is a data-bearing value, not a bare enum tag.
 */
sealed interface ReadbackVerdict {
    data object Correct : ReadbackVerdict

    /** Non-empty defect list; iteration order matches required-atom order for determinism. */
    data class Incorrect(val defects: NonEmptyList<AtomDefect>) : ReadbackVerdict

    /**
     * No readback received yet. Pass 9 (D-AUDIT.2): the coordination
     * ledger escalates Issued → Querying → Reissued → LostCommsDeclared
     * via [escalateOverdueCoordinations]; this verdict marks "no readback
     * matched" without erasing the pending entry.
     */
    data object Missing : ReadbackVerdict

    /**
     * Pilot explicitly refused the instruction ("unable [reason]").
     *
     * Not a readback defect — a goal-state change. processReadback routing:
     * pop pending, do NOT activate clearance, route to re-sequencing via
     * NeedsReplan on the commitment. Required for Phase 5c speed control
     * ("unable due turbulence").
     */
    data class Refused(val reason: String?) : ReadbackVerdict
}

/** True if any defect is a wrong value (as opposed to merely missing). Delegates to protocol-level [defectsHaveWrongValue]. */
val ReadbackVerdict.Incorrect.hasWrongValue: Boolean
    get() = defectsHaveWrongValue(defects)

/**
 * Classify a readback against a pending instruction.
 *
 * Collects *all* defects in a single pass — a readback that is both missing one atom and
 * has another wrong returns both in [ReadbackVerdict.Incorrect.defects]. The corresponding
 * correction at the [Controller] level uses [hasWrongValue] to choose between
 * [ReadbackCorrectionKind.INCORRECT_ATOM] (any wrong value) and
 * [ReadbackCorrectionKind.MISSING_ATOM] (all defects are omissions).
 *
 * Conditional clearances ([ConditionalClearance]) recurse: both the wrapped instruction's
 * atoms AND the predicate must be read back. Wrapped defects propagate up the recursion
 * unchanged; a condition problem becomes a [AtomDefect.MissingCondition] or
 * [AtomDefect.WrongCondition] appended to the wrapped defect list.
 */
fun classifyReadback(instruction: AtcInstruction, readback: Readback): ReadbackVerdict {
    // Conditional clearance: the wrapped instruction's atoms PLUS the predicate must be read back.
    if (instruction is ConditionalClearance) {
        val innerVerdict = classifyReadback(instruction.instruction, readback)
        val innerDefects = if (innerVerdict is ReadbackVerdict.Incorrect) innerVerdict.defects.toList() else emptyList()
        val conditionDefect = classifyCondition(instruction.condition, readback)
        val all = innerDefects + listOfNotNull(conditionDefect)
        val nel = all.toNonEmptyListOrNull()
        return if (nel == null) ReadbackVerdict.Correct else ReadbackVerdict.Incorrect(nel)
    }

    val required = requiredReadbackAtoms(instruction)
    if (required.isEmpty()) return ReadbackVerdict.Correct

    val presentAtoms: List<AtomicReadback> = readback.elements.map { element ->
        when (element) {
            is SimpleElement -> element.value
            is ConditionalElement -> element.action
        }
    }

    // LinkedHashSet (the default setOf() implementation) preserves insertion order,
    // and requiredReadbackAtoms emits its set from a single when-branch literal — so
    // iterating `required` is deterministic. We still build the defect list eagerly
    // rather than early-returning so mixed wrong+missing cases return both defects.
    val defects = mutableListOf<AtomDefect>()
    for (req in required) {
        if (req in presentAtoms) continue
        val reqKind = req.kind()
        if (presentAtoms.any { it.kind() == reqKind }) defects += AtomDefect.WrongAtom(req)
        else defects += AtomDefect.MissingAtom(req)
    }

    // AfterLandingVacateVia with whenAble=true: pilot must also include WhenAbleCondition.
    if (instruction is AfterLandingVacateVia && instruction.whenAble) {
        val presentConditions = readback.elements.mapNotNull { (it as? ConditionalElement)?.condition }
        val hasWhenAble = presentConditions.any { it is WhenAbleCondition }
        if (!hasWhenAble) {
            val kindMatch = presentConditions.any { it.kind() == ConditionKind.WhenAble }
            if (kindMatch) defects += AtomDefect.WrongCondition(WhenAbleCondition)
            else defects += AtomDefect.MissingCondition(WhenAbleCondition)
        }
    }

    val nel = defects.toNonEmptyListOrNull()
    return if (nel == null) ReadbackVerdict.Correct else ReadbackVerdict.Incorrect(nel)
}

/**
 * Classifier-local kind tag for [AtomicReadback]. Used to answer
 * "same kind but different value?" without reflection.
 */
private enum class AtomKind {
    Heading, Level, Speed, Route, Runway, Squawk, Frequency, Pressure,
    HoldShort, ClearedForTakeoff, ClearedToLand, ClearedApproach,
    ClearedTouchAndGo, ClearedLowApproach, LineUp, CrossRunway, Backtrack,
    TaxiViaRunway, TaxiRoute, Hold, ResumeOwnNavigation, RouteAsFiled,
    JoinAirway, RejoinSidAt, LeaveHoldProceedDirect, GoAround, Vacate, Orbit, ExtendDownwind,
    VisualApproach, SpecialVfr, FreeText,
    // HoldingAck (and its cancel-takeoff sibling) are distinct kinds so the
    // classifier distinguishes "pilot read back the wrong hold variant" from
    // "pilot didn't acknowledge at all".
    HoldingAck, HoldingAckCancelTakeoff,
    SequenceAck, BreakOff, DisregardAck,
    // Urgency runway override instructions — each is a distinct kind so cross-
    // instruction satisfaction is caught (e.g. "stopping" ≠ "taking off").
    StopImmediately, TakeoffOrVacate, TakeoffOrHoldShort,
    // ICAO 4444 §4.5.7.5.1(c) standalone advisories — distinct kinds so the
    // classifier distinguishes a wrong runway-in-use from a wrong runway
    // clearance, and a wrong transition level from a wrong assigned level.
    RunwayInUse, TransitionLevel,
}

@Suppress("CyclomaticComplexMethod")
private fun AtomicReadback.kind(): AtomKind = when (this) {
    is HeadingReadback -> AtomKind.Heading
    is LevelReadback -> AtomKind.Level
    is SpeedReadback -> AtomKind.Speed
    is RouteReadback -> AtomKind.Route
    is RunwayReadback -> AtomKind.Runway
    is SquawkReadback -> AtomKind.Squawk
    is FrequencyReadback -> AtomKind.Frequency
    is PressureSettingReadback -> AtomKind.Pressure
    is HoldShortReadback -> AtomKind.HoldShort
    is ClearedForTakeoffReadback -> AtomKind.ClearedForTakeoff
    is ClearedToLandReadback -> AtomKind.ClearedToLand
    is ClearedApproachReadback -> AtomKind.ClearedApproach
    is ClearedTouchAndGoReadback -> AtomKind.ClearedTouchAndGo
    is ClearedLowApproachReadback -> AtomKind.ClearedLowApproach
    is LineUpReadback -> AtomKind.LineUp
    is CrossRunwayReadback -> AtomKind.CrossRunway
    is BacktrackReadback -> AtomKind.Backtrack
    is TaxiViaRunwayReadback -> AtomKind.TaxiViaRunway
    is TaxiRouteReadback -> AtomKind.TaxiRoute
    is HoldReadback -> AtomKind.Hold
    ResumeOwnNavigationReadback -> AtomKind.ResumeOwnNavigation
    RouteAsFiledReadback -> AtomKind.RouteAsFiled
    is JoinAirwayReadback -> AtomKind.JoinAirway
    is RejoinSidAtReadback -> AtomKind.RejoinSidAt
    is LeaveHoldProceedDirectReadback -> AtomKind.LeaveHoldProceedDirect
    is GoAroundReadback -> AtomKind.GoAround
    is VacateReadback -> AtomKind.Vacate
    is OrbitReadback -> AtomKind.Orbit
    is ExtendDownwindReadback -> AtomKind.ExtendDownwind
    is VisualApproachReadback -> AtomKind.VisualApproach
    is SpecialVfrReadback -> AtomKind.SpecialVfr
    is FreeTextReadback -> AtomKind.FreeText
    is HoldingAcknowledgementReadback ->
        if (cancelTakeoff) AtomKind.HoldingAckCancelTakeoff else AtomKind.HoldingAck
    is SequenceAcknowledgementReadback -> AtomKind.SequenceAck
    is BreakOffReadback -> AtomKind.BreakOff
    is DisregardAcknowledgementReadback -> AtomKind.DisregardAck
    is StopImmediatelyReadback -> AtomKind.StopImmediately
    is TakeoffImmediatelyOrVacateReadback -> AtomKind.TakeoffOrVacate
    is TakeoffImmediatelyOrHoldShortReadback -> AtomKind.TakeoffOrHoldShort
    is RunwayInUseReadback -> AtomKind.RunwayInUse
    is TransitionLevelReadback -> AtomKind.TransitionLevel
}

/** Classifier-local kind tag for [ReadbackCondition]. */
private enum class ConditionKind {
    PassingLevel, WhenAble, AfterFix, AfterTraffic, BehindTraffic,
    AfterDeparture, AtLevel, AtDistance,
}

private fun ReadbackCondition.kind(): ConditionKind = when (this) {
    is PassingLevelCondition -> ConditionKind.PassingLevel
    is WhenAbleCondition -> ConditionKind.WhenAble
    is AfterFixCondition -> ConditionKind.AfterFix
    is AfterTrafficCondition -> ConditionKind.AfterTraffic
    is BehindTrafficCondition -> ConditionKind.BehindTraffic
    is AfterDepartureCondition -> ConditionKind.AfterDeparture
    is AtLevelCondition -> ConditionKind.AtLevel
    is AtDistanceCondition -> ConditionKind.AtDistance
}

/** Returns null when the condition matches; otherwise the specific defect. */
private fun classifyCondition(predicate: ConditionalPredicate, readback: Readback): AtomDefect? {
    val expected: ReadbackCondition = when (predicate) {
        is ConditionalPredicate.AfterTraffic -> AfterTrafficCondition(predicate.traffic, predicate.action)
        is ConditionalPredicate.BehindTraffic -> BehindTrafficCondition(predicate.traffic)
        is ConditionalPredicate.AtLevel -> AtLevelCondition(predicate.level)
        is ConditionalPredicate.AtDistance -> AtDistanceCondition(predicate.distance)
        is ConditionalPredicate.AfterPassing -> AfterFixCondition(predicate.fix)
    }
    val presentConditions = readback.elements.mapNotNull { (it as? ConditionalElement)?.condition }
    val expectedKind = expected.kind()
    return when {
        expected in presentConditions -> null
        presentConditions.any { it.kind() == expectedKind } -> AtomDefect.WrongCondition(expected)
        else -> AtomDefect.MissingCondition(expected)
    }
}


/**
 * Record outgoing instructions as outstanding coordinations. Called after arbitration.
 *
 * If [ControllerOutput.Instruct.readbackAdvancesToStage] is set, the coordination
 * carries the target stage. The readback validator advances the commitment when
 * a correct readback is received.
 */
internal fun BeliefState.recordCoordinations(
    outputs: List<xyz.easiersaid.twr.controller.ControllerOutput.Instruct>,
    time: SimTime,
): BeliefState {
    if (outputs.isEmpty()) return this
    val updated = coordinations.toMutableMap()
    for (output in outputs) {
        val readbackInstruction = when (val dispatch = output.dispatch) {
            is xyz.easiersaid.twr.controller.bdi.Dispatch.Direct -> output.instruction
            is xyz.easiersaid.twr.controller.bdi.Dispatch.Conditional ->
                ConditionalClearance(
                    target = output.instruction.target,
                    condition = dispatch.condition,
                    instruction = output.instruction,
                )
        }
        val atoms = requiredReadbackAtoms(readbackInstruction)
        val readbackStage = output.readbackAdvancesToStage
        val coord = OutstandingCoordination(
            aircraft = output.target,
            dispatch = output.dispatch,
            certificationEvidence = output.certificationEvidence,
            expectedReadback = atoms,
            issuedAt = time,
            advanceToStage = readbackStage,
        )
        updated[output.target] = (updated[output.target] ?: emptyList()) + coord
    }
    return copy(coordinations = updated)
}

/**
 * Advance every outstanding coordination's [CoordinationState] per elapsed
 * time. Pass 9 (D-AUDIT.2): replaces the pre-Pass-9 `gcOldCoordinations`
 * which silently dropped stale entries — a firewall violation against the
 * "real controllers don't forget" rule.
 *
 * Per-entry transitions (total partition; precedence is the order below):
 *
 *   Issued    + (now - issuedAt)    > queryAfter           → Querying(now, null)
 *   Querying  + (now - queriedAt)   > (reissueAfter - queryAfter)
 *                                                          → Reissued(now, 1, null)
 *   Reissued  + attemptCount >= maxReissueAttempts         → LostCommsDeclared(now)
 *   Reissued  + (now - issuedAt)    > lostCommsAfter       → LostCommsDeclared(now)
 *                                                            [unreachable under
 *                                                             [ReadbackTimeoutPolicy.Default]
 *                                                             per init invariant; kept as
 *                                                             consistency-check against
 *                                                             future constant edits]
 *   Reissued  + (now - reissuedAt)  > reissueInterval      → Reissued(now, attempt+1, null)
 *   LostCommsDeclared                                      → unchanged (terminal)
 *
 * Returns the updated [BeliefState]. Escalation is purely a state advance;
 * the controller's [coordinationEscalationOutputs] reads the just-
 * transitioned states and emits the corresponding phraseology output.
 */
internal fun BeliefState.escalateOverdueCoordinations(
    now: SimTime,
    policy: ReadbackTimeoutPolicy = ReadbackTimeoutPolicy.Default,
): BeliefState {
    if (coordinations.isEmpty()) return this
    var changed = false
    val advanced = coordinations.mapValues { (_, coords) ->
        coords.map { c ->
            val nextState = advanceState(c, now, policy)
            if (nextState === c.state) c else { changed = true; c.copy(state = nextState) }
        }
    }
    return if (!changed) this else copy(coordinations = advanced)
}

private fun advanceState(
    c: OutstandingCoordination,
    now: SimTime,
    policy: ReadbackTimeoutPolicy,
): CoordinationState = when (val s = c.state) {
    is CoordinationState.Issued -> {
        if ((now - c.issuedAt) > policy.queryAfter) CoordinationState.Querying(queriedAt = now, emittedAt = null)
        else s
    }
    is CoordinationState.Querying -> {
        // Querying → Reissued(1) when (now - queriedAt) exceeds the gap
        // between reissueAfter and queryAfter — i.e., when total elapsed
        // (now - issuedAt) crosses reissueAfter.
        val querySpent = now - s.queriedAt
        val reissueGap = policy.reissueAfter - policy.queryAfter
        if (querySpent > reissueGap) CoordinationState.Reissued(reissuedAt = now, attemptCount = 1, emittedAt = null)
        else s
    }
    is CoordinationState.Reissued -> when {
        s.attemptCount >= policy.maxReissueAttempts -> CoordinationState.LostCommsDeclared(declaredAt = now, emittedBlindAt = null)
        (now - c.issuedAt) > policy.lostCommsAfter -> CoordinationState.LostCommsDeclared(declaredAt = now, emittedBlindAt = null)
        (now - s.reissuedAt) > policy.reissueInterval ->
            CoordinationState.Reissued(reissuedAt = now, attemptCount = s.attemptCount + 1, emittedAt = null)
        else -> s
    }
    is CoordinationState.LostCommsDeclared -> s
}
