package xyz.easiersaid.twr.protocol

data class InstructionMetadata(
    val timing: InstructionTiming?,
    val domain: ClearanceDomain?,
    val completionCategory: CompletionCategory?,
    val mayBeConditional: Boolean,
    val supersedesOverride: Set<ClearanceDomain>? = null
)

fun instructionMetadata(instruction: AtcInstruction): InstructionMetadata = when (instruction) {
    is ConditionalClearance -> instructionMetadata(instruction.instruction)

    // ---- Ground ----
    is StartupApproved -> InstructionMetadata(null, ClearanceDomain.GROUND, null, mayBeConditional = false)
    is PushbackApproved -> InstructionMetadata(null, ClearanceDomain.GROUND, null, mayBeConditional = false)
    is PushbackFace -> InstructionMetadata(null, ClearanceDomain.GROUND, null, mayBeConditional = false)
    is TaxiTo -> InstructionMetadata(InstructionTiming.SEQUENTIAL, ClearanceDomain.GROUND, CompletionCategory.SELF_COMPLETING, mayBeConditional = true)
    is TaxiViaRunway -> InstructionMetadata(InstructionTiming.SEQUENTIAL, ClearanceDomain.GROUND, CompletionCategory.SELF_COMPLETING, mayBeConditional = true)
    is AirTaxiTo -> InstructionMetadata(InstructionTiming.SEQUENTIAL, ClearanceDomain.GROUND, CompletionCategory.SELF_COMPLETING, mayBeConditional = true) // E: helicopter equivalent of TaxiTo
    is HoldPosition -> InstructionMetadata(InstructionTiming.PERSISTENT, ClearanceDomain.GROUND, CompletionCategory.PERSISTENT, mayBeConditional = false)
    is HoldShortOf -> InstructionMetadata(InstructionTiming.PERSISTENT, ClearanceDomain.GROUND, CompletionCategory.PERSISTENT, mayBeConditional = true)
    is CrossRunway -> InstructionMetadata(InstructionTiming.SEQUENTIAL, ClearanceDomain.GROUND, CompletionCategory.SELF_COMPLETING, mayBeConditional = true)
    is BacktrackRunway -> InstructionMetadata(InstructionTiming.SEQUENTIAL, ClearanceDomain.GROUND, CompletionCategory.SELF_COMPLETING, mayBeConditional = true)
    is VacateRunway -> InstructionMetadata(null, ClearanceDomain.GROUND, CompletionCategory.SELF_COMPLETING, mayBeConditional = false) // K: completes when aircraft exits runway
    is TaxiIntoHoldingBay -> InstructionMetadata(null, ClearanceDomain.GROUND, null, mayBeConditional = false)
    is TaxiWithCaution -> InstructionMetadata(null, ClearanceDomain.GROUND, null, mayBeConditional = false)
    is ExpediteTaxi -> InstructionMetadata(null, ClearanceDomain.GROUND, null, mayBeConditional = false)
    is ReduceTaxiSpeed -> InstructionMetadata(null, ClearanceDomain.GROUND, null, mayBeConditional = false)
    is GiveWayToTraffic -> InstructionMetadata(null, ClearanceDomain.GROUND, null, mayBeConditional = false)

    // ---- Runway ----
    // Runway clearances have null timing intentionally — their execution depends on pilot
    // readiness state rather than being sequential/immediate/persistent within compound clearances.
    is LineUpAndWait -> InstructionMetadata(InstructionTiming.PERSISTENT, ClearanceDomain.RUNWAY, CompletionCategory.PERSISTENT, mayBeConditional = true)
    // G: Conditional takeoff/landing per ICAO 4444 7.9/7.10: "after the landing/departing [traffic]..."
    is ClearedForTakeoff -> InstructionMetadata(null, ClearanceDomain.RUNWAY, CompletionCategory.SELF_COMPLETING, mayBeConditional = true)
    is ClearedToLand -> InstructionMetadata(null, ClearanceDomain.RUNWAY, CompletionCategory.SELF_COMPLETING, mayBeConditional = true)
    is ClearedTouchAndGo -> InstructionMetadata(null, ClearanceDomain.RUNWAY, CompletionCategory.SELF_COMPLETING, mayBeConditional = true)
    is ClearedLowApproach -> InstructionMetadata(null, ClearanceDomain.RUNWAY, CompletionCategory.SELF_COMPLETING, mayBeConditional = true)
    is GoAround -> InstructionMetadata(null, ClearanceDomain.RUNWAY, null, mayBeConditional = false, supersedesOverride = setOf(ClearanceDomain.RUNWAY, ClearanceDomain.ROUTE, ClearanceDomain.LEVEL, ClearanceDomain.SPEED))
    is HoldPositionCancelTakeoff -> InstructionMetadata(null, ClearanceDomain.RUNWAY, null, mayBeConditional = false)
    is StopImmediately -> InstructionMetadata(null, ClearanceDomain.RUNWAY, null, mayBeConditional = false)
    is TakeoffImmediatelyOrVacateRunway -> InstructionMetadata(null, ClearanceDomain.RUNWAY, null, mayBeConditional = false)
    is TakeoffImmediatelyOrHoldShort -> InstructionMetadata(null, ClearanceDomain.RUNWAY, null, mayBeConditional = false)
    is AfterLandingVacateVia -> InstructionMetadata(null, ClearanceDomain.RUNWAY, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)

    // ---- Sequencing / circuit ----
    // Independent of landing/takeoff clearances. Null domain = no domain-based supersession.
    is JoinCircuit -> InstructionMetadata(null, null, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    is ExtendDownwind -> InstructionMetadata(InstructionTiming.PERSISTENT, null, CompletionCategory.PERSISTENT, mayBeConditional = false)
    is TurnBase -> InstructionMetadata(null, null, null, mayBeConditional = false)
    // J: Orbit is a circuit/aerodrome instruction, not a vector — does not implement VectorInstruction
    is Orbit -> InstructionMetadata(InstructionTiming.PERSISTENT, null, CompletionCategory.PERSISTENT, mayBeConditional = false)
    is MakeAnotherCircuit -> InstructionMetadata(null, null, null, mayBeConditional = false)
    is MakeShortApproach -> InstructionMetadata(null, null, null, mayBeConditional = false)
    is MakeLongApproach -> InstructionMetadata(null, null, null, mayBeConditional = false)
    is FollowTraffic -> InstructionMetadata(null, null, null, mayBeConditional = false)
    is NumberInSequence -> InstructionMetadata(null, null, null, mayBeConditional = false)
    is MaintainVisualSeparation -> InstructionMetadata(null, null, CompletionCategory.PERSISTENT, mayBeConditional = false)

    // ---- Route ----
    is ClearedTo -> InstructionMetadata(null, ClearanceDomain.ROUTE, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    is ProceedDirect -> InstructionMetadata(null, ClearanceDomain.ROUTE, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    is ResumeOwnNavigation -> InstructionMetadata(null, ClearanceDomain.ROUTE, CompletionCategory.ON_ACTIVATION, mayBeConditional = false)
    is RouteAsFiled -> InstructionMetadata(null, ClearanceDomain.ROUTE, CompletionCategory.ON_ACTIVATION, mayBeConditional = false)
    is JoinAirway -> InstructionMetadata(null, ClearanceDomain.ROUTE, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    is RejoinSidAt -> InstructionMetadata(null, ClearanceDomain.ROUTE, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    is HoldAt -> InstructionMetadata(InstructionTiming.PERSISTENT, ClearanceDomain.ROUTE, CompletionCategory.PERSISTENT, mayBeConditional = false)
    is LeaveHoldProceedDirect -> InstructionMetadata(null, ClearanceDomain.ROUTE, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    is WhenAbleProceedDirect -> InstructionMetadata(null, ClearanceDomain.ROUTE, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)

    // Vector instructions — IMMEDIATE timing (pilot starts turning immediately).
    // FlyHeading/TurnHeading/ContinuePresentHeading are PERSISTENT (heading maintained until cancelled).
    // C: TurnByDegrees is SELF_COMPLETING (finite manoeuvre — once degrees are turned, it's done).
    // F: StopTurn is ON_ACTIVATION (wings level on acknowledgement).
    is FlyHeading -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.ROUTE, CompletionCategory.PERSISTENT, mayBeConditional = false)
    is TurnHeading -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.ROUTE, CompletionCategory.PERSISTENT, mayBeConditional = false)
    is TurnByDegrees -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.ROUTE, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    is ContinuePresentHeading -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.ROUTE, CompletionCategory.PERSISTENT, mayBeConditional = false)
    is StopTurn -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.ROUTE, CompletionCategory.ON_ACTIVATION, mayBeConditional = false)
    // D: InterceptLocaliser is SELF_COMPLETING — completes when localiser captured.
    is InterceptLocaliser -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.ROUTE, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)

    // A: Approach clearances supersede ROUTE + LEVEL + SPEED (speed restrictions cancelled on approach).
    is ClearedApproach -> InstructionMetadata(null, ClearanceDomain.ROUTE, null, mayBeConditional = false, supersedesOverride = setOf(ClearanceDomain.ROUTE, ClearanceDomain.LEVEL, ClearanceDomain.SPEED))
    is ClearedVisualApproach -> InstructionMetadata(null, ClearanceDomain.ROUTE, null, mayBeConditional = false, supersedesOverride = setOf(ClearanceDomain.ROUTE, ClearanceDomain.LEVEL, ClearanceDomain.SPEED))
    is ContinueApproach -> InstructionMetadata(null, ClearanceDomain.ROUTE, null, mayBeConditional = false)
    is CommenceApproachAt -> InstructionMetadata(null, ClearanceDomain.ROUTE, null, mayBeConditional = false)
    is RemainOutsideControlledAirspace -> InstructionMetadata(null, ClearanceDomain.ROUTE, null, mayBeConditional = false)
    // I: Airspace clearances are PERSISTENT — in force until aircraft exits zone or lands.
    is ClearedToEnterControlZone -> InstructionMetadata(null, ClearanceDomain.ROUTE, CompletionCategory.PERSISTENT, mayBeConditional = false)
    is SpecialVfrClearance -> InstructionMetadata(null, ClearanceDomain.ROUTE, CompletionCategory.PERSISTENT, mayBeConditional = false)

    // ---- Level ----
    is ClimbTo -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.LEVEL, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    is DescendTo -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.LEVEL, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    // M: DescendWhenReady — null timing (pilot has discretion over when to begin descent).
    is DescendWhenReady -> InstructionMetadata(null, ClearanceDomain.LEVEL, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    is ExpediteClimb -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.LEVEL, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    is ExpediteDescend -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.LEVEL, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    // MaintainLevel/MaintainAtOrAbove/MaintainAtOrBelow are ongoing constraints, not targets.
    is MaintainLevel -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.LEVEL, CompletionCategory.PERSISTENT, mayBeConditional = false)
    is StopClimbAt -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.LEVEL, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    is StopDescentAt -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.LEVEL, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    is MaintainAtOrAbove -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.LEVEL, CompletionCategory.PERSISTENT, mayBeConditional = false)
    is MaintainAtOrBelow -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.LEVEL, CompletionCategory.PERSISTENT, mayBeConditional = false)
    is AfterPassingLevelClimbTo -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.LEVEL, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    is AfterPassingLevelDescendTo -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.LEVEL, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    is MaintainAltitudeUntilEstablished -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.LEVEL, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    is AvoidLevel -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.LEVEL, CompletionCategory.PERSISTENT, mayBeConditional = false)

    // ---- Speed ----
    // MaintainSpeed is an ongoing constraint.
    is MaintainSpeed -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.SPEED, CompletionCategory.PERSISTENT, mayBeConditional = false)
    is ReduceSpeedTo -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.SPEED, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    is IncreaseSpeedTo -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.SPEED, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    is MinimumCleanSpeed -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.SPEED, CompletionCategory.PERSISTENT, mayBeConditional = false)
    is ResumeNormalSpeed -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.SPEED, CompletionCategory.ON_ACTIVATION, mayBeConditional = false)

    // ---- Squawk ----
    is SetSquawk -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.SQUAWK, CompletionCategory.ON_ACTIVATION, mayBeConditional = false)
    is ConfirmSquawk -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.SQUAWK, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    is SquawkIdent -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.SQUAWK, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    is SquawkStandby -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.SQUAWK, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    is SquawkNormal -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.SQUAWK, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)
    is StopSquawk -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.SQUAWK, CompletionCategory.SELF_COMPLETING, mayBeConditional = false)

    // ---- Frequency ----
    is ContactFrequency -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.FREQUENCY, CompletionCategory.EXTERNAL_EVENT, mayBeConditional = false)
    is MonitorFrequency -> InstructionMetadata(InstructionTiming.IMMEDIATE, ClearanceDomain.FREQUENCY, CompletionCategory.EXTERNAL_EVENT, mayBeConditional = false)

    // ---- No domain ----
    is SetPressure -> InstructionMetadata(InstructionTiming.IMMEDIATE, null, CompletionCategory.ON_ACTIVATION, mayBeConditional = false)
    is ReportWhen -> InstructionMetadata(null, null, null, mayBeConditional = false)
    is ReportTrafficInSight -> InstructionMetadata(null, null, null, mayBeConditional = false)
    is ReportIntentions -> InstructionMetadata(null, null, null, mayBeConditional = false)
    // B: DivertTo supersedes route, level, and speed — pilot needs full authority when diverting.
    is DivertTo -> InstructionMetadata(null, ClearanceDomain.ROUTE, null, mayBeConditional = false, supersedesOverride = setOf(ClearanceDomain.ROUTE, ClearanceDomain.LEVEL, ClearanceDomain.SPEED))
    is CancelClearance -> InstructionMetadata(InstructionTiming.IMMEDIATE, null, CompletionCategory.ON_ACTIVATION, mayBeConditional = false)
    is AvoidArea -> InstructionMetadata(null, null, null, mayBeConditional = false)
}

fun instructionTiming(instruction: AtcInstruction): InstructionTiming? =
    instructionMetadata(instruction).timing

fun instructionDomain(instruction: AtcInstruction): ClearanceDomain? =
    instructionMetadata(instruction).domain

fun instructionSupersedesIn(instruction: AtcInstruction): Set<ClearanceDomain> {
    val meta = instructionMetadata(instruction)
    return meta.supersedesOverride ?: buildSet { meta.domain?.let(::add) }
}

fun instructionCompletionCategory(instruction: AtcInstruction): CompletionCategory? =
    instructionMetadata(instruction).completionCategory

fun instructionMayBeConditional(instruction: AtcInstruction): Boolean =
    instructionMetadata(instruction).mayBeConditional
