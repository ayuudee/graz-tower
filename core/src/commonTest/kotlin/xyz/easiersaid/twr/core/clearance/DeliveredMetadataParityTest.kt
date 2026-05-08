package xyz.easiersaid.twr.core.clearance

import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ApproachType
import xyz.easiersaid.twr.protocol.AvoidLevel
import xyz.easiersaid.twr.protocol.BacktrackRunway
import xyz.easiersaid.twr.protocol.CancelClearance
import xyz.easiersaid.twr.protocol.ClearedApproach
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedLowApproach
import xyz.easiersaid.twr.protocol.ClearedToEnterControlZone
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.CompletionCategory
import xyz.easiersaid.twr.protocol.ConfirmSquawk
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ContinueApproach
import xyz.easiersaid.twr.protocol.ContinuePresentHeading
import xyz.easiersaid.twr.protocol.CrossRunway
import xyz.easiersaid.twr.protocol.DescendWhenReady
import xyz.easiersaid.twr.protocol.ExtendDownwind
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.FlyHeading
import xyz.easiersaid.twr.protocol.GoAround
import xyz.easiersaid.twr.protocol.Heading
import xyz.easiersaid.twr.protocol.HoldPosition
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.IncreaseSpeedTo
import xyz.easiersaid.twr.protocol.InstructionTiming
import xyz.easiersaid.twr.protocol.InterceptLocaliser
import xyz.easiersaid.twr.protocol.JoinAirway
import xyz.easiersaid.twr.protocol.Knots
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.MaintainLevel
import xyz.easiersaid.twr.protocol.MinimumCleanSpeed
import xyz.easiersaid.twr.protocol.MonitorFrequency
import xyz.easiersaid.twr.protocol.Orbit
import xyz.easiersaid.twr.protocol.OrbitDirection
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.PressureSetting
import xyz.easiersaid.twr.protocol.ProceedDirect
import xyz.easiersaid.twr.protocol.ReduceSpeedTo
import xyz.easiersaid.twr.protocol.RemainOutsideControlledAirspace
import xyz.easiersaid.twr.protocol.ResumeNormalSpeed
import xyz.easiersaid.twr.protocol.ResumeOwnNavigation
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RouteAsFiled
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SetPressure
import xyz.easiersaid.twr.protocol.SetSquawk
import xyz.easiersaid.twr.protocol.SpecialVfrClearance
import xyz.easiersaid.twr.protocol.Speed
import xyz.easiersaid.twr.protocol.Squawk
import xyz.easiersaid.twr.protocol.SquawkIdent
import xyz.easiersaid.twr.protocol.SquawkNormal
import xyz.easiersaid.twr.protocol.SquawkStandby
import xyz.easiersaid.twr.protocol.StopSquawk
import xyz.easiersaid.twr.protocol.StopTurn
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.protocol.TaxiToStand
import xyz.easiersaid.twr.protocol.TurnDirection
import xyz.easiersaid.twr.protocol.TurnByDegrees
import xyz.easiersaid.twr.protocol.TurnHeading
import xyz.easiersaid.twr.protocol.instructionCompletionCategory
import xyz.easiersaid.twr.protocol.instructionDomain
import xyz.easiersaid.twr.protocol.instructionMayBeConditional
import xyz.easiersaid.twr.protocol.instructionSupersedesIn
import xyz.easiersaid.twr.protocol.instructionTiming

class DeliveredMetadataParityTest {

    @Test
    fun groundAndGroundAdjacentMetadataMatchesFrozenParity() {
        assertMetadata(
            TaxiToHoldingPoint(target, PointId("DEST"), RunwayId("09"), emptyList()),
            timing = InstructionTiming.SEQUENTIAL,
            domain = ClearanceDomain.GROUND,
            completion = CompletionCategory.SELF_COMPLETING,
            mayBeConditional = true,
            supersedes = setOf(ClearanceDomain.GROUND)
        )
        assertMetadata(
            TaxiToStand(target, PointId("DEST"), emptyList()),
            timing = InstructionTiming.SEQUENTIAL,
            domain = ClearanceDomain.GROUND,
            completion = CompletionCategory.SELF_COMPLETING,
            mayBeConditional = true,
            supersedes = setOf(ClearanceDomain.GROUND)
        )
        assertMetadata(
            HoldShortOf(target, RunwayId("09")),
            timing = InstructionTiming.PERSISTENT,
            domain = ClearanceDomain.GROUND,
            completion = CompletionCategory.PERSISTENT,
            mayBeConditional = true,
            supersedes = setOf(ClearanceDomain.GROUND)
        )
        assertMetadata(
            CrossRunway(target, RunwayId("09")),
            timing = InstructionTiming.SEQUENTIAL,
            domain = ClearanceDomain.GROUND,
            completion = CompletionCategory.SELF_COMPLETING,
            mayBeConditional = true,
            supersedes = setOf(ClearanceDomain.GROUND)
        )
        assertMetadata(
            HoldPosition(target),
            timing = InstructionTiming.PERSISTENT,
            domain = ClearanceDomain.GROUND,
            completion = CompletionCategory.PERSISTENT,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.GROUND)
        )
        assertMetadata(
            BacktrackRunway(target, RunwayId("09")),
            timing = InstructionTiming.SEQUENTIAL,
            domain = ClearanceDomain.GROUND,
            completion = CompletionCategory.SELF_COMPLETING,
            mayBeConditional = true,
            supersedes = setOf(ClearanceDomain.GROUND)
        )
    }

    @Test
    fun routeControlMetadataMatchesFrozenParity() {
        assertMetadata(
            ProceedDirect(target, FixId("HOLD")),
            timing = null,
            domain = ClearanceDomain.ROUTE,
            completion = CompletionCategory.SELF_COMPLETING,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.ROUTE)
        )
        assertMetadata(
            JoinAirway(target, xyz.easiersaid.twr.protocol.AirwayId("W1"), FixId("JOIN")),
            timing = null,
            domain = ClearanceDomain.ROUTE,
            completion = CompletionCategory.SELF_COMPLETING,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.ROUTE)
        )
        assertMetadata(
            ResumeOwnNavigation(target),
            timing = null,
            domain = ClearanceDomain.ROUTE,
            completion = CompletionCategory.ON_ACTIVATION,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.ROUTE)
        )
        assertMetadata(
            RouteAsFiled(target),
            timing = null,
            domain = ClearanceDomain.ROUTE,
            completion = CompletionCategory.ON_ACTIVATION,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.ROUTE)
        )
        assertMetadata(
            FlyHeading(target, Heading.unsafe(270)),
            timing = InstructionTiming.IMMEDIATE,
            domain = ClearanceDomain.ROUTE,
            completion = CompletionCategory.PERSISTENT,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.ROUTE)
        )
        assertMetadata(
            TurnHeading(target, TurnDirection.LEFT, Heading.unsafe(180)),
            timing = InstructionTiming.IMMEDIATE,
            domain = ClearanceDomain.ROUTE,
            completion = CompletionCategory.PERSISTENT,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.ROUTE)
        )
        assertMetadata(
            TurnByDegrees.unsafe(target, TurnDirection.LEFT, 45),
            timing = InstructionTiming.IMMEDIATE,
            domain = ClearanceDomain.ROUTE,
            completion = CompletionCategory.SELF_COMPLETING,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.ROUTE)
        )
        assertMetadata(
            ContinuePresentHeading(target),
            timing = InstructionTiming.IMMEDIATE,
            domain = ClearanceDomain.ROUTE,
            completion = CompletionCategory.PERSISTENT,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.ROUTE)
        )
        assertMetadata(
            StopTurn(target),
            timing = InstructionTiming.IMMEDIATE,
            domain = ClearanceDomain.ROUTE,
            completion = CompletionCategory.ON_ACTIVATION,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.ROUTE)
        )
        assertMetadata(
            InterceptLocaliser(target, RunwayId("09")),
            timing = InstructionTiming.IMMEDIATE,
            domain = ClearanceDomain.ROUTE,
            completion = CompletionCategory.SELF_COMPLETING,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.ROUTE)
        )
    }

    @Test
    fun routeAdjacentMetadataMatchesFrozenParity() {
        assertMetadata(
            ContinueApproach(target),
            timing = null,
            domain = ClearanceDomain.ROUTE,
            completion = null,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.ROUTE)
        )
        assertMetadata(
            ExtendDownwind(target),
            timing = InstructionTiming.PERSISTENT,
            domain = null,
            completion = CompletionCategory.PERSISTENT,
            mayBeConditional = false,
            supersedes = emptySet()
        )
        assertMetadata(
            Orbit(target, OrbitDirection.LEFT),
            timing = InstructionTiming.PERSISTENT,
            domain = null,
            completion = CompletionCategory.PERSISTENT,
            mayBeConditional = false,
            supersedes = emptySet()
        )
    }

    @Test
    fun airModifierAndAdminMetadataMatchesFrozenParity() {
        assertMetadata(
            DescendWhenReady(target, Level.AltitudeFeet.unsafe(3000)),
            timing = null,
            domain = ClearanceDomain.LEVEL,
            completion = CompletionCategory.SELF_COMPLETING,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.LEVEL)
        )
        assertMetadata(
            MaintainLevel(target, Level.FlightLevel.unsafe(250)),
            timing = InstructionTiming.IMMEDIATE,
            domain = ClearanceDomain.LEVEL,
            completion = CompletionCategory.PERSISTENT,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.LEVEL)
        )
        assertMetadata(
            AvoidLevel(target, Level.FlightLevel.unsafe(80)),
            timing = InstructionTiming.IMMEDIATE,
            domain = ClearanceDomain.LEVEL,
            completion = CompletionCategory.PERSISTENT,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.LEVEL)
        )
        assertMetadata(
            ReduceSpeedTo(target, Speed.InKnots(Knots.unsafe(180))),
            timing = InstructionTiming.IMMEDIATE,
            domain = ClearanceDomain.SPEED,
            completion = CompletionCategory.SELF_COMPLETING,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.SPEED)
        )
        assertMetadata(
            IncreaseSpeedTo(target, Speed.InKnots(Knots.unsafe(220))),
            timing = InstructionTiming.IMMEDIATE,
            domain = ClearanceDomain.SPEED,
            completion = CompletionCategory.SELF_COMPLETING,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.SPEED)
        )
        assertMetadata(
            MinimumCleanSpeed(target),
            timing = InstructionTiming.IMMEDIATE,
            domain = ClearanceDomain.SPEED,
            completion = CompletionCategory.PERSISTENT,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.SPEED)
        )
        assertMetadata(
            ResumeNormalSpeed(target),
            timing = InstructionTiming.IMMEDIATE,
            domain = ClearanceDomain.SPEED,
            completion = CompletionCategory.ON_ACTIVATION,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.SPEED)
        )
        assertMetadata(
            SetPressure(target, PressureSetting.Standard),
            timing = InstructionTiming.IMMEDIATE,
            domain = null,
            completion = CompletionCategory.ON_ACTIVATION,
            mayBeConditional = false,
            supersedes = emptySet()
        )
        assertMetadata(
            CancelClearance(target),
            timing = InstructionTiming.IMMEDIATE,
            domain = null,
            completion = CompletionCategory.ON_ACTIVATION,
            mayBeConditional = false,
            supersedes = emptySet()
        )
    }

    @Test
    fun runwayAirspaceAndCommsMetadataMatchesFrozenParity() {
        assertMetadata(
            LineUpAndWait(target, RunwayId("09")),
            timing = InstructionTiming.PERSISTENT,
            domain = ClearanceDomain.RUNWAY,
            completion = CompletionCategory.PERSISTENT,
            mayBeConditional = true,
            supersedes = setOf(ClearanceDomain.RUNWAY)
        )
        assertMetadata(
            ClearedForTakeoff(target, RunwayId("09")),
            timing = null,
            domain = ClearanceDomain.RUNWAY,
            completion = CompletionCategory.SELF_COMPLETING,
            mayBeConditional = true,
            supersedes = setOf(ClearanceDomain.RUNWAY)
        )
        assertMetadata(
            ClearedToLand(target, RunwayId("09")),
            timing = null,
            domain = ClearanceDomain.RUNWAY,
            completion = CompletionCategory.SELF_COMPLETING,
            mayBeConditional = true,
            supersedes = setOf(ClearanceDomain.RUNWAY)
        )
        assertMetadata(
            ClearedTouchAndGo(target, RunwayId("09")),
            timing = null,
            domain = ClearanceDomain.RUNWAY,
            completion = CompletionCategory.SELF_COMPLETING,
            mayBeConditional = true,
            supersedes = setOf(ClearanceDomain.RUNWAY)
        )
        assertMetadata(
            ClearedLowApproach(target, RunwayId("09")),
            timing = null,
            domain = ClearanceDomain.RUNWAY,
            completion = CompletionCategory.SELF_COMPLETING,
            mayBeConditional = true,
            supersedes = setOf(ClearanceDomain.RUNWAY)
        )
        assertMetadata(
            GoAround(target),
            timing = null,
            domain = ClearanceDomain.RUNWAY,
            completion = null,
            mayBeConditional = false,
            supersedes = setOf(
                ClearanceDomain.RUNWAY,
                ClearanceDomain.ROUTE,
                ClearanceDomain.LEVEL,
                ClearanceDomain.SPEED
            )
        )
        assertMetadata(
            RemainOutsideControlledAirspace(target, xyz.easiersaid.twr.protocol.AirspaceVolumeId("CTR")),
            timing = null,
            domain = ClearanceDomain.ROUTE,
            completion = null,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.ROUTE)
        )
        assertMetadata(
            ClearedToEnterControlZone(target, xyz.easiersaid.twr.protocol.AirspaceVolumeId("CTR")),
            timing = null,
            domain = ClearanceDomain.ROUTE,
            completion = CompletionCategory.PERSISTENT,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.ROUTE)
        )
        assertMetadata(
            SpecialVfrClearance(target, xyz.easiersaid.twr.protocol.AirspaceVolumeId("CTR")),
            timing = null,
            domain = ClearanceDomain.ROUTE,
            completion = CompletionCategory.PERSISTENT,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.ROUTE)
        )
        assertMetadata(
            ContactFrequency(target, RoleName.TOWER),
            timing = InstructionTiming.IMMEDIATE,
            domain = ClearanceDomain.FREQUENCY,
            completion = CompletionCategory.EXTERNAL_EVENT,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.FREQUENCY)
        )
        assertMetadata(
            MonitorFrequency(target, RoleName.APPROACH),
            timing = InstructionTiming.IMMEDIATE,
            domain = ClearanceDomain.FREQUENCY,
            completion = CompletionCategory.EXTERNAL_EVENT,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.FREQUENCY)
        )
        assertMetadata(
            SetSquawk(target, Squawk.unsafe(7000)),
            timing = InstructionTiming.IMMEDIATE,
            domain = ClearanceDomain.SQUAWK,
            completion = CompletionCategory.ON_ACTIVATION,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.SQUAWK)
        )
        assertMetadata(
            ConfirmSquawk(target, Squawk.unsafe(7000)),
            timing = InstructionTiming.IMMEDIATE,
            domain = ClearanceDomain.SQUAWK,
            completion = CompletionCategory.SELF_COMPLETING,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.SQUAWK)
        )
        assertMetadata(
            SquawkIdent(target),
            timing = InstructionTiming.IMMEDIATE,
            domain = ClearanceDomain.SQUAWK,
            completion = CompletionCategory.SELF_COMPLETING,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.SQUAWK)
        )
        assertMetadata(
            SquawkStandby(target),
            timing = InstructionTiming.IMMEDIATE,
            domain = ClearanceDomain.SQUAWK,
            completion = CompletionCategory.SELF_COMPLETING,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.SQUAWK)
        )
        assertMetadata(
            SquawkNormal(target, xyz.easiersaid.twr.protocol.TransponderMode.CHARLIE),
            timing = InstructionTiming.IMMEDIATE,
            domain = ClearanceDomain.SQUAWK,
            completion = CompletionCategory.SELF_COMPLETING,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.SQUAWK)
        )
        assertMetadata(
            StopSquawk(target, xyz.easiersaid.twr.protocol.TransponderMode.CHARLIE),
            timing = InstructionTiming.IMMEDIATE,
            domain = ClearanceDomain.SQUAWK,
            completion = CompletionCategory.SELF_COMPLETING,
            mayBeConditional = false,
            supersedes = setOf(ClearanceDomain.SQUAWK)
        )
        assertMetadata(
            ClearedApproach(target, ApproachType.ILS, RunwayId("09")),
            timing = null,
            domain = ClearanceDomain.ROUTE,
            completion = null,
            mayBeConditional = false,
            supersedes = setOf(
                ClearanceDomain.ROUTE,
                ClearanceDomain.LEVEL,
                ClearanceDomain.SPEED
            )
        )
    }

    private fun assertMetadata(
        instruction: xyz.easiersaid.twr.protocol.AtcInstruction,
        timing: InstructionTiming?,
        domain: ClearanceDomain?,
        completion: CompletionCategory?,
        mayBeConditional: Boolean,
        supersedes: Set<ClearanceDomain>
    ) {
        assertEquals(timing, instructionTiming(instruction), "timing drift for $instruction")
        assertEquals(domain, instructionDomain(instruction), "domain drift for $instruction")
        assertEquals(completion, instructionCompletionCategory(instruction), "completion drift for $instruction")
        assertEquals(mayBeConditional, instructionMayBeConditional(instruction), "conditional drift for $instruction")
        assertEquals(supersedes, instructionSupersedesIn(instruction), "supersession drift for $instruction")
    }

    companion object {
        private val target = AircraftId("TEST123")
    }
}
