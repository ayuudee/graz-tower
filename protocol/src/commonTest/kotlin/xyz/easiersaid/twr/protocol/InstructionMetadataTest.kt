package xyz.easiersaid.twr.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class InstructionMetadataTest {

    private val target = AircraftId("BAW123")

    // ---- Domain classification spot-checks ----

    @Test
    fun groundInstructionsHaveGroundDomain() {
        assertEquals(ClearanceDomain.GROUND, instructionDomain(TaxiTo(target, PointId("A"), emptyList())))
        assertEquals(ClearanceDomain.GROUND, instructionDomain(HoldPosition(target)))
        assertEquals(ClearanceDomain.GROUND, instructionDomain(CrossRunway(target, RunwayId("09"))))
        assertEquals(ClearanceDomain.GROUND, instructionDomain(StartupApproved(target)))
    }

    @Test
    fun runwayInstructionsHaveRunwayDomain() {
        assertEquals(ClearanceDomain.RUNWAY, instructionDomain(ClearedForTakeoff(target, RunwayId("27"))))
        assertEquals(ClearanceDomain.RUNWAY, instructionDomain(ClearedToLand(target, RunwayId("27"))))
        assertEquals(ClearanceDomain.RUNWAY, instructionDomain(LineUpAndWait(target, RunwayId("27"))))
        assertEquals(ClearanceDomain.RUNWAY, instructionDomain(GoAround(target)))
    }

    @Test
    fun levelInstructionsHaveLevelDomain() {
        assertEquals(ClearanceDomain.LEVEL, instructionDomain(ClimbTo(target, Level.FlightLevel.unsafe(350))))
        assertEquals(ClearanceDomain.LEVEL, instructionDomain(DescendTo(target, Level.AltitudeFeet.unsafe(3000))))
        assertEquals(ClearanceDomain.LEVEL, instructionDomain(MaintainLevel(target, Level.FlightLevel.unsafe(250))))
    }

    @Test
    fun frequencyInstructionsHaveFrequencyDomain() {
        assertEquals(ClearanceDomain.FREQUENCY, instructionDomain(ContactFrequency(target, RoleName.TOWER)))
        assertEquals(ClearanceDomain.FREQUENCY, instructionDomain(MonitorFrequency(target, RoleName.APPROACH)))
    }

    @Test
    fun domainlessInstructionsReturnNull() {
        assertNull(instructionDomain(SetPressure(target, PressureSetting.Standard)))
        assertNull(instructionDomain(ReportWhen(target, ReportEvent.Downwind())))
        assertNull(instructionDomain(ReportTrafficInSight(target, TrafficRef.ByCallsign(Callsign("SHT456")))))
    }

    // ---- Timing classification ----

    @Test
    fun sequentialTimingForGroundMovement() {
        assertEquals(InstructionTiming.SEQUENTIAL, instructionTiming(TaxiTo(target, PointId("A"), emptyList())))
        assertEquals(InstructionTiming.SEQUENTIAL, instructionTiming(CrossRunway(target, RunwayId("09"))))
        assertEquals(InstructionTiming.SEQUENTIAL, instructionTiming(BacktrackRunway(target, RunwayId("09"))))
    }

    @Test
    fun immediateTimingForLevelSpeedSquawkFrequencyAndVectors() {
        assertEquals(InstructionTiming.IMMEDIATE, instructionTiming(ClimbTo(target, Level.FlightLevel.unsafe(350))))
        assertEquals(InstructionTiming.IMMEDIATE, instructionTiming(MaintainSpeed(target, Speed.InKnots(Knots.unsafe(250)))))
        assertEquals(InstructionTiming.IMMEDIATE, instructionTiming(SetSquawk(target, Squawk.unsafe(7000))))
        assertEquals(InstructionTiming.IMMEDIATE, instructionTiming(ContactFrequency(target, RoleName.TOWER)))
        assertEquals(InstructionTiming.IMMEDIATE, instructionTiming(FlyHeading(target, Heading.unsafe(270))))
        assertEquals(InstructionTiming.IMMEDIATE, instructionTiming(TurnHeading(target, TurnDirection.LEFT, Heading.unsafe(180))))
    }

    @Test
    fun persistentTimingForHoldsAndOrbits() {
        assertEquals(InstructionTiming.PERSISTENT, instructionTiming(HoldPosition(target)))
        assertEquals(InstructionTiming.PERSISTENT, instructionTiming(HoldShortOf(target, RunwayId("09"))))
        assertEquals(InstructionTiming.PERSISTENT, instructionTiming(Orbit(target, OrbitDirection.LEFT)))
        assertEquals(InstructionTiming.PERSISTENT, instructionTiming(ExtendDownwind(target)))
    }

    @Test
    fun standaloneInstructionsHaveNullTiming() {
        assertNull(instructionTiming(ClearedForTakeoff(target, RunwayId("27"))))
        assertNull(instructionTiming(GoAround(target)))
        assertNull(instructionTiming(ClearedApproach(target, ApproachType.ILS, RunwayId("27"))))
    }

    // ---- Supersession overrides ----

    @Test
    fun goAroundSupersedesRunwayRouteLevelAndSpeed() {
        assertEquals(
            setOf(ClearanceDomain.RUNWAY, ClearanceDomain.ROUTE, ClearanceDomain.LEVEL, ClearanceDomain.SPEED),
            instructionSupersedesIn(GoAround(target))
        )
    }

    @Test
    fun clearedApproachSupersedesRouteLevelAndSpeed() {
        assertEquals(
            setOf(ClearanceDomain.ROUTE, ClearanceDomain.LEVEL, ClearanceDomain.SPEED),
            instructionSupersedesIn(ClearedApproach(target, ApproachType.ILS, RunwayId("27")))
        )
    }

    @Test
    fun regularInstructionSupersedesOwnDomain() {
        assertEquals(
            setOf(ClearanceDomain.LEVEL),
            instructionSupersedesIn(ClimbTo(target, Level.FlightLevel.unsafe(350)))
        )
    }

    @Test
    fun domainlessInstructionSupersedesNothing() {
        assertTrue(instructionSupersedesIn(ReportWhen(target, ReportEvent.Downwind())).isEmpty())
    }

    // ---- Conditional eligibility ----

    @Test
    fun groundMovementInstructionsMayBeConditional() {
        assertTrue(instructionMayBeConditional(TaxiTo(target, PointId("A"), emptyList())))
        assertTrue(instructionMayBeConditional(CrossRunway(target, RunwayId("09"))))
        assertTrue(instructionMayBeConditional(BacktrackRunway(target, RunwayId("09"))))
        assertTrue(instructionMayBeConditional(HoldShortOf(target, RunwayId("09"))))
        assertTrue(instructionMayBeConditional(LineUpAndWait(target, RunwayId("09"))))
    }

    @Test
    fun nonSurfaceInstructionsMayNotBeConditional() {
        assertFalse(instructionMayBeConditional(ClimbTo(target, Level.FlightLevel.unsafe(350))))
        assertFalse(instructionMayBeConditional(FlyHeading(target, Heading.unsafe(270))))
        assertFalse(instructionMayBeConditional(SetSquawk(target, Squawk.unsafe(7000))))
        assertFalse(instructionMayBeConditional(GoAround(target)))
    }

    @Test
    fun conditionalTakeoffAndLandingAllowed() {
        assertTrue(instructionMayBeConditional(ClearedForTakeoff(target, RunwayId("27"))))
        assertTrue(instructionMayBeConditional(ClearedToLand(target, RunwayId("27"))))
        assertTrue(instructionMayBeConditional(ClearedTouchAndGo(target, RunwayId("27"))))
        assertTrue(instructionMayBeConditional(ClearedLowApproach(target, RunwayId("27"))))
    }

    // ---- Completion categories ----

    @Test
    fun selfCompletingInstructions() {
        assertEquals(CompletionCategory.SELF_COMPLETING, instructionCompletionCategory(TaxiTo(target, PointId("A"), emptyList())))
        assertEquals(CompletionCategory.SELF_COMPLETING, instructionCompletionCategory(ClimbTo(target, Level.FlightLevel.unsafe(350))))
        assertEquals(CompletionCategory.SELF_COMPLETING, instructionCompletionCategory(ClearedForTakeoff(target, RunwayId("27"))))
    }

    @Test
    fun persistentCompletionForHoldsAndOrbits() {
        assertEquals(CompletionCategory.PERSISTENT, instructionCompletionCategory(HoldPosition(target)))
        assertEquals(CompletionCategory.PERSISTENT, instructionCompletionCategory(HoldShortOf(target, RunwayId("09"))))
        assertEquals(CompletionCategory.PERSISTENT, instructionCompletionCategory(Orbit(target, OrbitDirection.LEFT)))
    }

    @Test
    fun onActivationCompletion() {
        assertEquals(CompletionCategory.ON_ACTIVATION, instructionCompletionCategory(SetSquawk(target, Squawk.unsafe(7000))))
        assertEquals(CompletionCategory.ON_ACTIVATION, instructionCompletionCategory(ResumeOwnNavigation(target)))
        assertEquals(CompletionCategory.ON_ACTIVATION, instructionCompletionCategory(SetPressure(target, PressureSetting.Standard)))
    }

    @Test
    fun externalEventCompletion() {
        assertEquals(CompletionCategory.EXTERNAL_EVENT, instructionCompletionCategory(ContactFrequency(target, RoleName.TOWER)))
        assertEquals(CompletionCategory.EXTERNAL_EVENT, instructionCompletionCategory(MonitorFrequency(target, RoleName.APPROACH)))
    }

    // ---- ConditionalClearance delegates to inner instruction ----

    @Test
    fun conditionalClearanceDelegatesToInnerInstruction() {
        val inner = TaxiTo(target, PointId("A"), emptyList())
        val conditional = ConditionalClearance(
            target = target,
            condition = ConditionalPredicate.AfterTraffic(
                TrafficRef.ByCallsign(Callsign("SHT456")),
                TrafficAction.LANDING
            ),
            instruction = inner
        )
        assertEquals(instructionTiming(inner), instructionTiming(conditional))
        assertEquals(instructionDomain(inner), instructionDomain(conditional))
        assertEquals(instructionCompletionCategory(inner), instructionCompletionCategory(conditional))
        assertEquals(instructionMayBeConditional(inner), instructionMayBeConditional(conditional))
        assertEquals(instructionSupersedesIn(inner), instructionSupersedesIn(conditional))
    }
}
