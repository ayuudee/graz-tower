package xyz.easiersaid.twr.protocol

fun instructionTiming(instruction: AtcInstruction): InstructionTiming? = when (instruction) {
    is ConditionalClearance -> instructionTiming(instruction.instruction)

    is TaxiTo,
    is CrossRunway,
    is BacktrackRunway -> InstructionTiming.SEQUENTIAL

    is SetSquawk,
    is ConfirmSquawk,
    is SquawkIdent,
    is SquawkStandby,
    is SquawkNormal,
    is StopSquawk,
    is SetPressure,
    is ClimbTo,
    is DescendTo,
    is ExpediteClimb,
    is ExpediteDescend,
    is MaintainLevel,
    is StopClimbAt,
    is StopDescentAt,
    is MaintainAtOrAbove,
    is MaintainAtOrBelow,
    is AfterPassingLevelClimbTo,
    is AfterPassingLevelDescendTo,
    is MaintainAltitudeUntilEstablished,
    is MaintainSpeed,
    is ReduceSpeedTo,
    is IncreaseSpeedTo,
    is MinimumCleanSpeed,
    is ResumeNormalSpeed,
    is ContactFrequency,
    is MonitorFrequency -> InstructionTiming.IMMEDIATE

    is HoldPosition,
    is HoldShortOf,
    is LineUpAndWait,
    is Orbit,
    is ExtendDownwind,
    is HoldAt -> InstructionTiming.PERSISTENT

    else -> null
}

fun instructionDomain(instruction: AtcInstruction): ClearanceDomain? = when (instruction) {
    is StartupApproved,
    is PushbackApproved,
    is PushbackFace,
    is TaxiTo,
    is TaxiViaRunway,
    is AirTaxiTo,
    is HoldPosition,
    is HoldShortOf,
    is CrossRunway,
    is BacktrackRunway,
    is VacateRunway,
    is TaxiIntoHoldingBay,
    is TaxiWithCaution,
    is ExpediteTaxi,
    is ReduceTaxiSpeed,
    is GiveWayToTraffic -> ClearanceDomain.GROUND

    is LineUpAndWait,
    is ClearedForTakeoff,
    is ClearedToLand,
    is ClearedTouchAndGo,
    is ClearedLowApproach,
    is GoAround,
    is HoldPositionCancelTakeoff,
    is StopImmediately,
    is TakeoffImmediatelyOrVacateRunway,
    is TakeoffImmediatelyOrHoldShort,
    is AfterLandingVacateVia,
    is JoinCircuit,
    is ExtendDownwind,
    is TurnBase,
    is Orbit,
    is MakeAnotherCircuit,
    is FollowTraffic,
    is NumberInSequence,
    is MaintainVisualSeparation -> ClearanceDomain.RUNWAY

    is ClearedTo,
    is ProceedDirect,
    is ResumeOwnNavigation,
    is RouteAsFiled,
    is JoinAirway,
    is RejoinSidAt,
    is HoldAt,
    is LeaveHoldProceedDirect,
    is WhenAbleProceedDirect,
    is ClearedApproach,
    is ContinueApproach,
    is FlyHeading,
    is TurnHeading,
    is TurnByDegrees,
    is ContinuePresentHeading,
    is StopTurn,
    is InterceptLocaliser,
    is RemainOutsideControlledAirspace,
    is ClearedToEnterControlZone -> ClearanceDomain.ROUTE

    is ClimbTo,
    is DescendTo,
    is ExpediteClimb,
    is ExpediteDescend,
    is MaintainLevel,
    is StopClimbAt,
    is StopDescentAt,
    is MaintainAtOrAbove,
    is MaintainAtOrBelow,
    is AfterPassingLevelClimbTo,
    is AfterPassingLevelDescendTo,
    is MaintainAltitudeUntilEstablished -> ClearanceDomain.LEVEL

    is MaintainSpeed,
    is ReduceSpeedTo,
    is IncreaseSpeedTo,
    is MinimumCleanSpeed,
    is ResumeNormalSpeed -> ClearanceDomain.SPEED

    is SetSquawk,
    is ConfirmSquawk,
    is SquawkIdent,
    is SquawkStandby,
    is SquawkNormal,
    is StopSquawk -> ClearanceDomain.SQUAWK

    is ContactFrequency,
    is MonitorFrequency -> ClearanceDomain.FREQUENCY

    is ConditionalClearance -> instructionDomain(instruction.instruction)

    else -> null
}

fun instructionSupersedesIn(instruction: AtcInstruction): Set<ClearanceDomain> =
    when (instruction) {
        is ConditionalClearance -> instructionSupersedesIn(instruction.instruction)
        else -> buildSet {
            instructionDomain(instruction)?.let(::add)
            when (instruction) {
                is GoAround -> addAll(setOf(ClearanceDomain.ROUTE, ClearanceDomain.LEVEL))
                is ClearedApproach -> add(ClearanceDomain.LEVEL)
                else -> Unit
            }
        }
    }

fun instructionCompletionCategory(instruction: AtcInstruction): CompletionCategory? = when (instruction) {
    is ConditionalClearance -> instructionCompletionCategory(instruction.instruction)

    is TaxiTo,
    is CrossRunway,
    is BacktrackRunway,
    is ClearedForTakeoff,
    is ClearedToLand,
    is ClearedTouchAndGo,
    is ClearedLowApproach,
    is AfterLandingVacateVia,
    is ClearedTo,
    is ProceedDirect,
    is JoinAirway,
    is RejoinSidAt,
    is LeaveHoldProceedDirect,
    is WhenAbleProceedDirect,
    is ClimbTo,
    is DescendTo,
    is ExpediteClimb,
    is ExpediteDescend,
    is MaintainLevel,
    is StopClimbAt,
    is StopDescentAt,
    is MaintainAtOrAbove,
    is MaintainAtOrBelow,
    is AfterPassingLevelClimbTo,
    is AfterPassingLevelDescendTo,
    is MaintainAltitudeUntilEstablished,
    is MaintainSpeed,
    is ReduceSpeedTo,
    is IncreaseSpeedTo,
    is ConfirmSquawk,
    is SquawkIdent,
    is SquawkStandby,
    is SquawkNormal,
    is StopSquawk,
    is JoinCircuit -> CompletionCategory.SELF_COMPLETING

    is SetSquawk,
    is SetPressure,
    is ResumeOwnNavigation,
    is RouteAsFiled,
    is MinimumCleanSpeed,
    is ResumeNormalSpeed -> CompletionCategory.ON_ACTIVATION

    is ContactFrequency,
    is MonitorFrequency -> CompletionCategory.EXTERNAL_EVENT

    is HoldPosition,
    is HoldShortOf,
    is LineUpAndWait,
    is Orbit,
    is ExtendDownwind,
    is HoldAt -> CompletionCategory.PERSISTENT

    else -> null
}

fun instructionMayBeConditional(instruction: AtcInstruction): Boolean = when (instruction) {
    is PushbackApproved,
    is TaxiTo,
    is TaxiViaRunway,
    is AirTaxiTo,
    is HoldShortOf,
    is CrossRunway,
    is BacktrackRunway,
    is LineUpAndWait -> true

    else -> false
}
