package xyz.easiersaid.twr.controller.observe

import arrow.core.NonEmptyList
import arrow.core.toNonEmptyListOrNull
import xyz.easiersaid.twr.protocol.*

/**
 * An instruction that has been issued and is awaiting readback from the pilot.
 *
 * Controller records one entry per outgoing [ControllerOutput.Instruct]. Entries are
 * popped when a matching readback arrives, or GC'd after [MAX_READBACK_AGE].
 *
 * Time is load-bearing: it supports ordering resolution when multiple instructions
 * are outstanding, anchors future timeout behaviour ("[callsign], readback?"), and
 * scopes the interpretation layer's context snapshot to voice-time when that layer
 * is eventually built. See wiki/design-decisions/2026-04-16-transmission-reception-architecture.md.
 */
data class PendingReadback(
    val instruction: AtcInstruction,
    val issuedAt: SimTime,
)

/**
 * Maximum age of a pending readback before it is silently GC'd.
 *
 * 30 seconds balances realistic RT lag (pilots may read back after a few seconds of
 * workload), future LLM parser latency, and preventing indefinite pending accumulation.
 */
val MAX_READBACK_AGE: SimDuration = SimDuration.ofSeconds(30)

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
     * No readback received within [MAX_READBACK_AGE]. Pending ages out via GC;
     * after TTL, controller may emit "say again" or re-issue at discretion.
     * No immediate pop — the pending entry remains until explicitly GC'd.
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
 * Safety-critical atoms that a readback must carry for a given instruction.
 *
 * Empty set = no safety-critical items; accept any readback. Coverage spans the
 * ICAO Doc 4444 §12.3.1 readback required list: runway clearances and crossings,
 * level and heading, speed (when on a speed control), route/approach/hold assignments,
 * taxi clearances, frequency changes, squawk, and altimeter setting.
 *
 * Totality is enforced — every [AtcInstruction] leaf has an explicit branch so that
 * adding a new instruction type forces the author to decide whether it has a
 * safety-critical readback atom, rather than silently defaulting to "none".
 */
@Suppress("CyclomaticComplexMethod", "LongMethod")
fun requiredReadbackAtoms(instruction: AtcInstruction): Set<AtomicReadback> = when (instruction) {
    // Conditional clearances recurse at the call site — classifyReadback peels them off
    // before reaching this function. Falling through here with only the condition text
    // would be a logic error, so emit an empty set and rely on classifyReadback's
    // recursion to enforce the readback of both wrapped atom and condition.
    is ConditionalClearance -> emptySet()

    // ── Runway operations (safety-critical: all carry runway id) ──────────
    is ClearedForTakeoff -> setOf(ClearedForTakeoffReadback(instruction.runway))
    is ClearedToLand -> setOf(ClearedToLandReadback(instruction.runway))
    is ClearedTouchAndGo -> setOf(ClearedTouchAndGoReadback(instruction.runway))
    is ClearedLowApproach -> setOf(ClearedLowApproachReadback(instruction.runway))
    is LineUpAndWait -> setOf(LineUpReadback(instruction.runway))
    is CrossRunway -> setOf(CrossRunwayReadback(instruction.runway))
    is BacktrackRunway -> setOf(BacktrackReadback(instruction.runway))
    is HoldShortOf -> setOf(HoldShortReadback(instruction.runway))
    is GoAround -> setOf(GoAroundReadback(level = instruction.level, heading = instruction.heading))
    is BreakOff -> setOf(BreakOffReadback(
        level = instruction.missedApproachInstructions.filterIsInstance<ClimbTo>().firstOrNull()?.level,
        heading = instruction.missedApproachInstructions.filterIsInstance<FlyHeading>().firstOrNull()?.heading
            ?: instruction.missedApproachInstructions.filterIsInstance<TurnHeading>().firstOrNull()?.heading,
    ))
    is AfterLandingVacateVia -> setOf(VacateReadback(via = instruction.exit))
    is VacateRunway -> setOf(VacateReadback(direction = instruction.direction, via = instruction.via))

    // Runway urgency / override: hold instructions carry a mandatory
    // acknowledgement atom (CAP 413 §4.46 / ICAO 4444 §12.3.1) — silent
    // compliance is not acceptable for runway-safety-critical commands. The
    // cancel-takeoff variant is distinguished so a bare "holding" readback
    // does not satisfy a cancel-takeoff pending entry.
    is HoldPosition -> setOf(HoldingAcknowledgementReadback(cancelTakeoff = false))
    is HoldPositionCancelTakeoff -> setOf(HoldingAcknowledgementReadback(cancelTakeoff = true))
    // Urgency runway override: mandatory acknowledgement per CAP 413 §4.46 /
    // ICAO 4444 §12.3.1. Each instruction gets a distinct atom so cross-instruction
    // satisfaction is detected ("stopping" cannot satisfy "taking off or vacating").
    is StopImmediately -> setOf(StopImmediatelyReadback)
    is TakeoffImmediatelyOrVacateRunway -> setOf(TakeoffImmediatelyOrVacateReadback(instruction.runway))
    is TakeoffImmediatelyOrHoldShort -> setOf(TakeoffImmediatelyOrHoldShortReadback(instruction.runway))

    // ── Level / climb / descent ──────────────────────────────────────────
    is ClimbTo -> setOf(LevelReadback(instruction.level))
    is DescendTo -> setOf(LevelReadback(instruction.level))
    is MaintainLevel -> setOf(LevelReadback(instruction.level))
    is StopClimbAt -> setOf(LevelReadback(instruction.level))
    is StopDescentAt -> setOf(LevelReadback(instruction.level))
    is MaintainAtOrAbove -> setOf(LevelReadback(instruction.minimumLevel))
    is MaintainAtOrBelow -> setOf(LevelReadback(instruction.maximumLevel))
    is ExpediteClimb -> setOf(LevelReadback(instruction.level))
    is ExpediteDescend -> setOf(LevelReadback(instruction.level))
    is DescendWhenReady -> setOf(LevelReadback(instruction.level))
    is AfterPassingLevelClimbTo -> setOf(LevelReadback(instruction.climbTo))
    is AfterPassingLevelDescendTo -> setOf(LevelReadback(instruction.descendTo))
    is MaintainAltitudeUntilEstablished -> setOf(LevelReadback(instruction.level))
    is AvoidLevel -> setOf(LevelReadback(instruction.level))

    // ── Heading / vectoring ──────────────────────────────────────────────
    is FlyHeading -> setOf(HeadingReadback(instruction.heading))
    is TurnHeading -> setOf(HeadingReadback(instruction.heading))
    // TurnByDegrees / ContinuePresentHeading / StopTurn / InterceptLocaliser:
    // no explicit heading atom (relative turn or continuation) — pilot
    // readback is free-form acknowledgement of the action, not a digit match.
    is TurnByDegrees -> emptySet()
    is ContinuePresentHeading -> emptySet()
    is StopTurn -> emptySet()
    is InterceptLocaliser -> setOf(RunwayReadback(instruction.runway))

    // ── Speed ────────────────────────────────────────────────────────────
    is MaintainSpeed -> setOf(SpeedReadback(instruction.speed))
    is ReduceSpeedTo -> setOf(SpeedReadback(instruction.speed))
    is IncreaseSpeedTo -> setOf(SpeedReadback(instruction.speed))
    // No numeric atom — pilot echoes the instruction in words.
    is MinimumCleanSpeed -> emptySet()
    is ResumeNormalSpeed -> emptySet()

    // ── Pressure ─────────────────────────────────────────────────────────
    is SetPressure -> setOf(PressureSettingReadback(instruction.pressure))

    // ── Aerodrome / atmospheric advisories (ICAO 4444 §4.5.7.5.1(c)) ─────
    // Standalone advisories that nevertheless require readback for the same
    // mis-hearing-detection reasons as level/heading/speed instructions.
    is RunwayInUseAdvisory -> setOf(RunwayInUseReadback(instruction.runway))
    is TransitionLevelIssuance -> setOf(TransitionLevelReadback(instruction.transitionLevel))

    // ── Route / approach / hold ──────────────────────────────────────────
    is ClearedTo -> instruction.route?.let { setOf(RouteReadback(it)) }
        ?: setOf(RouteReadback(RouteSpec.Direct(instruction.clearanceLimit)))
    is ProceedDirect -> setOf(RouteReadback(RouteSpec.Direct(instruction.fix)))
    is WhenAbleProceedDirect -> setOf(RouteReadback(RouteSpec.Direct(instruction.fix)))
    is ResumeOwnNavigation -> setOf(ResumeOwnNavigationReadback)
    is RouteAsFiled -> setOf(RouteAsFiledReadback)
    is JoinAirway -> setOf(JoinAirwayReadback(instruction.airway, instruction.joinFix))
    is RejoinSidAt -> setOf(RejoinSidAtReadback(instruction.fix))
    is ClearedApproach -> setOf(ClearedApproachReadback(instruction.approachType, instruction.runway))
    is ClearedVisualApproach -> setOf(VisualApproachReadback(instruction.runway))
    is HoldAt -> setOf(HoldReadback(instruction.hold))
    is LeaveHoldProceedDirect -> setOf(LeaveHoldProceedDirectReadback(instruction.fix))

    // ── Approach / circuit instructions without a readback atom ──────────
    // Pilot echoes the word (e.g. "extending downwind") — no structural match.
    is ContinueApproach -> emptySet()
    is JoinCircuit -> emptySet()
    is MakeShortApproach -> emptySet()
    is MakeLongApproach -> emptySet()
    is ExtendDownwind -> setOf(ExtendDownwindReadback())
    is TurnBase -> emptySet() // no dedicated TurnBaseReadback atom yet — track for R2
    is Orbit -> setOf(OrbitReadback(instruction.direction))
    is MakeAnotherCircuit -> emptySet()
    is CommenceApproachAt -> emptySet()

    // ── Taxi / ground movement ───────────────────────────────────────────
    is TaxiTo -> setOf(TaxiRouteReadback(instruction.destination, instruction.via))
    is AirTaxiTo -> setOf(TaxiRouteReadback(instruction.destination, instruction.via))
    is TaxiViaRunway -> setOf(TaxiViaRunwayReadback(instruction.runway, instruction.destination))
    // Non-routed taxi ops — no structural atom to compare.
    is StartupApproved -> emptySet()
    is PushbackApproved -> emptySet()
    is PushbackFace -> emptySet()
    is TaxiIntoHoldingBay -> emptySet()
    is TaxiWithCaution -> emptySet()
    is ExpediteTaxi -> emptySet()
    is ReduceTaxiSpeed -> emptySet()
    is GiveWayToTraffic -> emptySet()

    // ── Reporting ────────────────────────────────────────────────────────
    // "Wilco" acknowledgement; no structural atom.
    is ReportWhen -> emptySet()
    is ReportTrafficInSight -> emptySet()
    is ReportIntentions -> emptySet()

    // ── Sequencing ───────────────────────────────────────────────────────
    is FollowTraffic -> emptySet()
    is NumberInSequence -> setOf(SequenceAcknowledgementReadback(instruction.number, instruction.behindTraffic))
    is MaintainVisualSeparation -> emptySet()

    // ── Frequency / squawk ───────────────────────────────────────────────
    is ContactFrequency -> instruction.frequency?.let {
        setOf(FrequencyReadback(it, instruction.role))
    } ?: error("ContactFrequency issued without frequency")
    is MonitorFrequency -> instruction.frequency?.let {
        setOf(FrequencyReadback(it, instruction.role))
    } ?: error("MonitorFrequency issued without frequency")
    is SetSquawk -> setOf(SquawkReadback(instruction.squawk))
    // Transponder-mode ops: pilot echoes the word, no digit match.
    is ConfirmSquawk -> emptySet()
    is SquawkIdent -> emptySet()
    is SquawkStandby -> emptySet()
    is SquawkNormal -> emptySet()
    is StopSquawk -> emptySet()

    // ── Airspace / emergency / misc ──────────────────────────────────────
    is DivertTo -> error("Readback atoms not yet implemented for DivertTo")
    is ClearedToEnterControlZone -> error("Readback atoms not yet implemented for ClearedToEnterControlZone")
    is RemainOutsideControlledAirspace -> error("Readback atoms not yet implemented for RemainOutsideControlledAirspace")
    is SpecialVfrClearance -> error("Readback atoms not yet implemented for SpecialVfrClearance")
    is CancelClearance -> error("Readback atoms not yet implemented for CancelClearance")
    is Disregard -> setOf(DisregardAcknowledgementReadback)
    is AvoidArea -> error("Readback atoms not yet implemented for AvoidArea")
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
        val atoms = requiredReadbackAtoms(output.instruction)
        val readbackStage = output.readbackAdvancesToStage
        val coord = OutstandingCoordination(
            aircraft = output.target,
            instruction = output.instruction,
            expectedReadback = atoms,
            issuedAt = time,
            advanceToStage = readbackStage,
        )
        updated[output.target] = (updated[output.target] ?: emptyList()) + coord
    }
    return copy(coordinations = updated)
}

/** Drop coordinations older than [MAX_READBACK_AGE] that are still ISSUED. */
/** Drop ISSUED coordinations older than [MAX_READBACK_AGE]. CONFIRMED/CANCELLED are never stored. */
internal fun BeliefState.gcOldCoordinations(now: SimTime): BeliefState {
    if (coordinations.isEmpty()) return this
    val kept = coordinations.mapValues { (_, coords) ->
        coords.filter { (now - it.issuedAt) <= MAX_READBACK_AGE }
    }.filterValues { it.isNotEmpty() }
    return if (kept == coordinations) this else copy(coordinations = kept)
}
