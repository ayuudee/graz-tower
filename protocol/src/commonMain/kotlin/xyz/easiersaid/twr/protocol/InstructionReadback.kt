package xyz.easiersaid.twr.protocol

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
    // Pass 6 (D-PF.6 closure): runway-bound taxi reads back the runway too;
    // stand-bound taxi has no runway atom (the runway field is forbidden
    // for stand taxi by construction — see TaxiToStand's KDoc).
    is TaxiToHoldingPoint -> setOf(
        RunwayReadback(instruction.runway),
        TaxiRouteReadback(instruction.destination, instruction.via),
    )
    is TaxiToStand -> setOf(TaxiRouteReadback(instruction.destination, instruction.via))
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
