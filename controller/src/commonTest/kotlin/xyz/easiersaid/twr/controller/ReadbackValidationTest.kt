package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.MAX_READBACK_AGE
import xyz.easiersaid.twr.controller.observe.OutstandingCoordination
import xyz.easiersaid.twr.controller.observe.requiredReadbackAtoms
import xyz.easiersaid.twr.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Helper: create an OutstandingCoordination from an instruction and time. */
private fun pending(ac: AircraftId, instr: AtcInstruction, time: SimTime) =
    OutstandingCoordination(ac, instr, emptySet(), time)

/**
 * Regression tests for Bug B (2026-04-16): readback validation against pending instructions.
 *
 * Before the fix, the controller confirmed every readback as `ReadBackCorrect` regardless of
 * content or whether any clearance was pending. Now, readbacks are matched against pending
 * safety-critical atoms, and the verdict is three-state (ICAO 4444 §12.3.2):
 *   - CORRECT         → emit `ReadBackCorrect`, pop pending.
 *   - INCORRECT_ATOM  → emit `ReadbackCorrection(INCORRECT_ATOM)`, keep pending.
 *   - MISSING_ATOM    → emit `ReadbackCorrection(MISSING_ATOM)`, keep pending.
 * Readbacks with no matching pending stay silent; stale pending entries GC after
 * `MAX_READBACK_AGE`.
 */
class ReadbackValidationTest {

    private val world = testWorld()

    private fun readbackMessage(aircraft: AircraftId, vararg atoms: AtomicReadback): ReceivedMessage =
        ReceivedMessage.Clear(aircraft, Readback(atoms.map { SimpleElement(it) }))

    private fun viewWithReadback(
        aircraft: AircraftId,
        time: SimTime,
        messages: List<ReceivedMessage>,
    ): ControllerView = towerView(
        // Position at holding point (ground, human-piloted) so the departure
        // reconciliation produces UNCHANGED and no procedure rules fire.
        // The readback tests care about readback matching, not aircraft position.
        aircraft = mapOf(aircraft to aircraftAt(aircraft, TestIds.holdShort, testWorldIndex(),
            onGround = true, humanPiloted = true)),
        time = time,
        receivedMessages = messages,
    )

    @Test
    fun `correct readback emits ReadBackCorrect and pops pending`() {
        val beliefs = BeliefState.EMPTY.copy(
            coordinations = mapOf(
                TestIds.acAlpha to listOf(
                    pending(TestIds.acAlpha,
                        ClearedToLand(TestIds.acAlpha, TestIds.runway09),
                        SimTime.ofSeconds(0),
                    )
                )
            )
        )
        val view = viewWithReadback(
            TestIds.acAlpha,
            SimTime.ofSeconds(5),
            listOf(readbackMessage(TestIds.acAlpha, ClearedToLandReadback(TestIds.runway09))),
        )
        val result = controllerDecide(view, beliefs, world)

        val response = result.outputs
            .filterIsInstance<ControllerOutput.Respond>()
            .firstOrNull { it.response is ReadBackCorrect }
        assertNotNull(response, "Correct readback should emit ReadBackCorrect")
        assertTrue(
            result.updatedBeliefs.pendingReadbacks[TestIds.acAlpha].isNullOrEmpty(),
            "Matched pending entry should be popped",
        )
    }

    @Test
    fun `wrong-runway readback emits ReadbackCorrection(INCORRECT_ATOM) and leaves pending in place`() {
        val pending = pending(TestIds.acAlpha,
            ClearedToLand(TestIds.acAlpha, TestIds.runway09),
            SimTime.ofSeconds(0),
        )
        val beliefs = BeliefState.EMPTY.copy(
            coordinations = mapOf(TestIds.acAlpha to listOf(pending))
        )
        val wrongRunway = RunwayId("27")
        val view = viewWithReadback(
            TestIds.acAlpha,
            SimTime.ofSeconds(5),
            listOf(readbackMessage(TestIds.acAlpha, ClearedToLandReadback(wrongRunway))),
        )
        val result = controllerDecide(view, beliefs, world)

        val confirm = result.outputs
            .filterIsInstance<ControllerOutput.Respond>()
            .firstOrNull { it.response is ReadBackCorrect }
        assertNull(confirm, "Wrong-runway readback must not be confirmed")

        val correction = result.outputs
            .filterIsInstance<ControllerOutput.Respond>()
            .mapNotNull { it.response as? ReadbackCorrection }
            .firstOrNull()
        assertNotNull(correction, "Wrong-runway readback must trigger a correction")
        assertEquals(ReadbackCorrectionKind.INCORRECT_ATOM, correction.kind)
        assertEquals(pending.instruction, correction.correct,
            "Correction payload should replay the original ClearedToLand")
        assertEquals(
            1, result.updatedBeliefs.pendingReadbacks[TestIds.acAlpha]?.size,
            "Pending must remain until pilot transmits a correct readback",
        )
    }

    @Test
    fun `missing-atom readback emits ReadbackCorrection(MISSING_ATOM)`() {
        val pending = pending(TestIds.acAlpha,
            ClearedToLand(TestIds.acAlpha, TestIds.runway09),
            SimTime.ofSeconds(0),
        )
        val beliefs = BeliefState.EMPTY.copy(
            coordinations = mapOf(TestIds.acAlpha to listOf(pending))
        )
        // Readback present but without the ClearedToLandReadback atom at all
        val view = viewWithReadback(
            TestIds.acAlpha,
            SimTime.ofSeconds(5),
            listOf(readbackMessage(TestIds.acAlpha, SquawkReadback(Squawk.unsafe(7001)))),
        )
        val result = controllerDecide(view, beliefs, world)

        val correction = result.outputs
            .filterIsInstance<ControllerOutput.Respond>()
            .mapNotNull { it.response as? ReadbackCorrection }
            .firstOrNull()
        assertNotNull(correction)
        assertEquals(ReadbackCorrectionKind.MISSING_ATOM, correction.kind)
        assertEquals(1, result.updatedBeliefs.pendingReadbacks[TestIds.acAlpha]?.size)
    }

    @Test
    fun `level-change readback must match assigned level`() {
        val assigned = Level.AltitudeFeet.unsafe(3000)
        val pending = pending(TestIds.acAlpha,
            ClimbTo(TestIds.acAlpha, assigned),
            SimTime.ofSeconds(0),
        )
        val beliefs = BeliefState.EMPTY.copy(
            coordinations = mapOf(TestIds.acAlpha to listOf(pending))
        )

        // Wrong level → INCORRECT_ATOM
        val wrong = viewWithReadback(
            TestIds.acAlpha,
            SimTime.ofSeconds(5),
            listOf(readbackMessage(TestIds.acAlpha, LevelReadback(Level.AltitudeFeet.unsafe(4000)))),
        )
        val wrongResult = controllerDecide(wrong, beliefs, world)
        val wrongCorrection = wrongResult.outputs.filterIsInstance<ControllerOutput.Respond>()
            .mapNotNull { it.response as? ReadbackCorrection }.firstOrNull()
        assertNotNull(wrongCorrection)
        assertEquals(ReadbackCorrectionKind.INCORRECT_ATOM, wrongCorrection.kind)

        // Correct level → CORRECT
        val right = viewWithReadback(
            TestIds.acAlpha,
            SimTime.ofSeconds(5),
            listOf(readbackMessage(TestIds.acAlpha, LevelReadback(assigned))),
        )
        val rightResult = controllerDecide(right, beliefs, world)
        assertNotNull(
            rightResult.outputs.filterIsInstance<ControllerOutput.Respond>()
                .firstOrNull { it.response is ReadBackCorrect }
        )
    }

    @Test
    fun `conditional clearance requires both wrapped atoms and condition to be read back`() {
        val inner = LineUpAndWait(TestIds.acAlpha, TestIds.runway09)
        val predicate = ConditionalPredicate.AfterTraffic(
            TrafficRef.ByCallsign(Callsign("G-AB")),
            TrafficAction.LANDING,
        )
        val pending = pending(TestIds.acAlpha,
            ConditionalClearance(TestIds.acAlpha, predicate, inner),
            SimTime.ofSeconds(0),
        )
        val beliefs = BeliefState.EMPTY.copy(
            coordinations = mapOf(TestIds.acAlpha to listOf(pending))
        )

        // Condition missing → MISSING_ATOM
        val missingCond = viewWithReadback(
            TestIds.acAlpha,
            SimTime.ofSeconds(5),
            listOf(readbackMessage(TestIds.acAlpha, LineUpReadback(TestIds.runway09))),
        )
        val missingResult = controllerDecide(missingCond, beliefs, world)
        val missing = missingResult.outputs.filterIsInstance<ControllerOutput.Respond>()
            .mapNotNull { it.response as? ReadbackCorrection }.firstOrNull()
        assertNotNull(missing, "Conditional clearance without condition readback must trigger correction")
        assertEquals(ReadbackCorrectionKind.MISSING_ATOM, missing.kind)

        // Both atoms + condition → CORRECT
        val full = towerView(
            aircraft = mapOf(TestIds.acAlpha to aircraftAt(TestIds.acAlpha, TestIds.holdShort, testWorldIndex(),
                onGround = true, humanPiloted = true)),
            time = SimTime.ofSeconds(5),
            receivedMessages = listOf(
                ReceivedMessage.Clear(
                    TestIds.acAlpha,
                    Readback(listOf(
                        ConditionalElement(
                            condition = AfterTrafficCondition(predicate.traffic, predicate.action),
                            action = LineUpReadback(TestIds.runway09),
                        ),
                    )),
                ),
            ),
        )
        val fullResult = controllerDecide(full, beliefs, world)
        assertNotNull(
            fullResult.outputs.filterIsInstance<ControllerOutput.Respond>()
                .firstOrNull { it.response is ReadBackCorrect },
            "Correct conditional readback should be confirmed",
        )
    }

    @Test
    fun `readback with no pending emits nothing`() {
        val view = viewWithReadback(
            TestIds.acAlpha,
            SimTime.ofSeconds(5),
            listOf(readbackMessage(TestIds.acAlpha, ClearedToLandReadback(TestIds.runway09))),
        )
        val result = controllerDecide(view, BeliefState.EMPTY, world)

        val response = result.outputs
            .filterIsInstance<ControllerOutput.Respond>()
            .firstOrNull { it.response is ReadBackCorrect }
        assertNull(response, "Readback with no pending must not be confirmed")
    }

    @Test
    fun `pending readback GCs after MAX_READBACK_AGE`() {
        val old = pending(TestIds.acAlpha,
            ClearedToLand(TestIds.acAlpha, TestIds.runway09),
            SimTime.ofSeconds(0),
        )
        val beliefs = BeliefState.EMPTY.copy(
            coordinations = mapOf(TestIds.acAlpha to listOf(old))
        )
        // Age past MAX_READBACK_AGE, no incoming readback this cycle.
        val nowSeconds = (MAX_READBACK_AGE.millis / 1000L).toInt() + 5
        val view = viewWithReadback(
            TestIds.acAlpha,
            SimTime.ofSeconds(nowSeconds),
            emptyList(),
        )
        val result = controllerDecide(view, beliefs, world)

        assertTrue(
            result.updatedBeliefs.pendingReadbacks[TestIds.acAlpha].isNullOrEmpty(),
            "Pending older than MAX_READBACK_AGE must be GC'd",
        )
    }

    @Test
    fun `ICAO 4444 4_5_7_5_1 always-read-back families map to structured atoms`() {
        val aircraft = TestIds.acAlpha
        val routeFix = FixId("GIVMI")
        val airway = AirwayId("W112")
        val runway = TestIds.runway09
        val taxiDestination = PointId("RWY_EXIT_A")

        assertEquals(
            setOf(RouteReadback(RouteSpec.Direct(routeFix))),
            requiredReadbackAtoms(ProceedDirect(aircraft, routeFix)),
            "ICAO Doc 4444 §4.5.7.5.1(a): route clearances must require route readback",
        )
        assertEquals(
            setOf(ResumeOwnNavigationReadback),
            requiredReadbackAtoms(ResumeOwnNavigation(aircraft)),
            "ICAO Doc 4444 §4.5.7.5.1(a): resume own navigation is a route instruction and must require readback",
        )
        assertEquals(
            setOf(RouteAsFiledReadback),
            requiredReadbackAtoms(RouteAsFiled(aircraft)),
            "ICAO Doc 4444 §4.5.7.5.1(a): route-as-filed clearance must require readback",
        )
        assertEquals(
            setOf(JoinAirwayReadback(airway, routeFix)),
            requiredReadbackAtoms(JoinAirway(aircraft, airway, routeFix)),
            "ICAO Doc 4444 §4.5.7.5.1(a): airway join clearances must require readback",
        )
        assertEquals(
            setOf(RejoinSidAtReadback(routeFix)),
            requiredReadbackAtoms(RejoinSidAt(aircraft, routeFix)),
            "ICAO Doc 4444 §4.5.7.5.1(a): SID rejoin instructions must require readback",
        )
        assertEquals(
            setOf(LeaveHoldProceedDirectReadback(routeFix)),
            requiredReadbackAtoms(LeaveHoldProceedDirect(aircraft, routeFix)),
            "ICAO Doc 4444 §4.5.7.5.1(a): leave-hold direct instructions must require readback",
        )

        assertEquals(
            setOf(TaxiViaRunwayReadback(runway, taxiDestination)),
            requiredReadbackAtoms(TaxiViaRunway(aircraft, runway, taxiDestination)),
            "ICAO Doc 4444 §4.5.7.5.1(b): taxi-via-runway instructions must require readback",
        )
        assertEquals(
            setOf(HoldShortReadback(runway)),
            requiredReadbackAtoms(HoldShortOf(aircraft, runway)),
            "ICAO Doc 4444 §4.5.7.5.1(b): hold-short clearances must require readback",
        )
        assertEquals(
            setOf(BacktrackReadback(runway)),
            requiredReadbackAtoms(BacktrackRunway(aircraft, runway)),
            "ICAO Doc 4444 §4.5.7.5.1(b): backtrack instructions must require readback",
        )

        assertEquals(
            setOf(PressureSettingReadback(PressureSetting.QnhHpa.unsafe(1016))),
            requiredReadbackAtoms(SetPressure(aircraft, PressureSetting.QnhHpa.unsafe(1016))),
            "ICAO Doc 4444 §4.5.7.5.1(c): altimeter settings must require readback",
        )
        assertEquals(
            setOf(SquawkReadback(Squawk.unsafe(4612))),
            requiredReadbackAtoms(SetSquawk(aircraft, Squawk.unsafe(4612))),
            "ICAO Doc 4444 §4.5.7.5.1(c): SSR codes must require readback",
        )
        assertEquals(
            setOf(LevelReadback(Level.AltitudeFeet.unsafe(3000))),
            requiredReadbackAtoms(ClimbTo(aircraft, Level.AltitudeFeet.unsafe(3000))),
            "ICAO Doc 4444 §4.5.7.5.1(c): level instructions must require readback",
        )
        assertEquals(
            setOf(HeadingReadback(Heading.unsafe(270))),
            requiredReadbackAtoms(FlyHeading(aircraft, Heading.unsafe(270))),
            "ICAO Doc 4444 §4.5.7.5.1(c): heading instructions must require readback",
        )
        assertEquals(
            setOf(SpeedReadback(Speed.InKnots(Knots.unsafe(160)))),
            requiredReadbackAtoms(MaintainSpeed(aircraft, Speed.InKnots(Knots.unsafe(160)))),
            "ICAO Doc 4444 §4.5.7.5.1(c): speed instructions must require readback",
        )
        assertEquals(
            setOf(RunwayInUseReadback(runway)),
            requiredReadbackAtoms(RunwayInUseAdvisory(aircraft, runway)),
            "ICAO Doc 4444 §4.5.7.5.1(c): runway-in-use advisory must require readback",
        )
        assertEquals(
            setOf(TransitionLevelReadback(Level.FlightLevel.unsafe(60))),
            requiredReadbackAtoms(TransitionLevelIssuance(aircraft, Level.FlightLevel.unsafe(60))),
            "ICAO Doc 4444 §4.5.7.5.1(c): transition-level issuance must require readback",
        )
    }

    @Test
    fun `resume-own-navigation does not accept empty readback`() {
        val pending = pending(
            TestIds.acAlpha,
            ResumeOwnNavigation(TestIds.acAlpha),
            SimTime.ofSeconds(0),
        )
        val beliefs = BeliefState.EMPTY.copy(
            coordinations = mapOf(TestIds.acAlpha to listOf(pending))
        )
        val view = viewWithReadback(
            TestIds.acAlpha,
            SimTime.ofSeconds(5),
            listOf(readbackMessage(TestIds.acAlpha)),
        )

        val result = controllerDecide(view, beliefs, world)

        val correction = result.outputs
            .filterIsInstance<ControllerOutput.Respond>()
            .mapNotNull { it.response as? ReadbackCorrection }
            .firstOrNull()
        assertNotNull(correction, "Empty readback must not satisfy a route-clearance readback requirement")
        assertEquals(ReadbackCorrectionKind.MISSING_ATOM, correction.kind)
        assertEquals(1, result.updatedBeliefs.pendingReadbacks[TestIds.acAlpha]?.size)
    }

    @Test
    fun `taxi-via-runway wrong-runway readback is corrected not confirmed`() {
        val pending = pending(
            TestIds.acAlpha,
            TaxiViaRunway(TestIds.acAlpha, TestIds.runway09, PointId("RWY_EXIT_A")),
            SimTime.ofSeconds(0),
        )
        val beliefs = BeliefState.EMPTY.copy(
            coordinations = mapOf(TestIds.acAlpha to listOf(pending))
        )
        val view = viewWithReadback(
            TestIds.acAlpha,
            SimTime.ofSeconds(5),
            listOf(readbackMessage(
                TestIds.acAlpha,
                TaxiViaRunwayReadback(RunwayId("27"), PointId("RWY_EXIT_A")),
            )),
        )

        val result = controllerDecide(view, beliefs, world)

        val confirm = result.outputs
            .filterIsInstance<ControllerOutput.Respond>()
            .firstOrNull { it.response is ReadBackCorrect }
        assertNull(confirm, "Wrong-runway taxi-via-runway readback must not be confirmed")

        val correction = result.outputs
            .filterIsInstance<ControllerOutput.Respond>()
            .mapNotNull { it.response as? ReadbackCorrection }
            .firstOrNull()
        assertNotNull(correction)
        assertEquals(ReadbackCorrectionKind.INCORRECT_ATOM, correction.kind)
        assertEquals(1, result.updatedBeliefs.pendingReadbacks[TestIds.acAlpha]?.size)
    }

    @Test
    fun `matches most recent pending when multiple outstanding`() {
        // Aircraft lines up and then gets cleared for takeoff — two pending readbacks.
        // A line-up readback should match the line-up pending, not the takeoff pending.
        val lineUp = pending(TestIds.acAlpha,
            LineUpAndWait(TestIds.acAlpha, TestIds.runway09),
            SimTime.ofSeconds(0),
        )
        val takeoff = pending(TestIds.acAlpha,
            ClearedForTakeoff(TestIds.acAlpha, TestIds.runway09),
            SimTime.ofSeconds(5),
        )
        val beliefs = BeliefState.EMPTY.copy(
            coordinations = mapOf(TestIds.acAlpha to listOf(lineUp, takeoff))
        )
        val view = viewWithReadback(
            TestIds.acAlpha,
            SimTime.ofSeconds(6),
            listOf(readbackMessage(TestIds.acAlpha, LineUpReadback(TestIds.runway09))),
        )
        val result = controllerDecide(view, beliefs, world)

        assertNotNull(
            result.outputs.filterIsInstance<ControllerOutput.Respond>()
                .firstOrNull { it.response is ReadBackCorrect },
            "Line-up readback should be confirmed"
        )
        assertEquals(
            1, result.updatedBeliefs.pendingReadbacks[TestIds.acAlpha]?.size,
            "Line-up pending popped; takeoff pending remains",
        )
    }

    @Test
    fun `outgoing instruction recorded as pending`() {
        // Drive a full cycle: tower issues ClearedToLand (via normal procedure flow) →
        // the outgoing instruction should appear in pendingReadbacks.
        var beliefs = BeliefState.EMPTY
        val worldIndex = testWorldIndex()

        // Cycle 1: aircraft on downwind, no runway observation issues.
        val ac1 = aircraftAt(TestIds.acAlpha, TestIds.downwind, worldIndex, onGround = false)
        val view1 = towerView(
            aircraft = mapOf(TestIds.acAlpha to ac1),
            time = SimTime.ofSeconds(0),
            receivedMessages = listOf(positionReportMessage(TestIds.acAlpha, ReportEvent.Downwind())),
        )
        beliefs = controllerDecide(view1, beliefs, world).updatedBeliefs

        // Cycle 2+: progress aircraft to final and observe output.
        val ac2 = aircraftAt(TestIds.acAlpha, TestIds.finalApproach, worldIndex, onGround = false)
        val view2 = towerView(
            aircraft = mapOf(TestIds.acAlpha to ac2),
            time = SimTime.ofSeconds(10),
        )
        val result = controllerDecide(view2, beliefs, world)

        val issued = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.instruction is ClearedToLand }
        // If ClearedToLand was issued this cycle, it should now be pending.
        if (issued != null) {
            val pending = result.updatedBeliefs.pendingReadbacks[TestIds.acAlpha].orEmpty()
            assertTrue(
                pending.any { it.instruction is ClearedToLand },
                "ClearedToLand should be recorded as pending readback after arbitration",
            )
        }
    }
}
