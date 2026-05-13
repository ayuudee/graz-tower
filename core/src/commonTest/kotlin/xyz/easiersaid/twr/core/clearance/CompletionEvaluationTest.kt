package xyz.easiersaid.twr.core.clearance

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.easiersaid.twr.core.resolution.ResolutionResult
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.FixtureIds
import xyz.easiersaid.twr.core.world.sampleWorld
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ClearanceContent
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.ClearanceId
import xyz.easiersaid.twr.protocol.ClearanceStatus
import xyz.easiersaid.twr.protocol.ClearedTo
import xyz.easiersaid.twr.protocol.ClearedApproach
import xyz.easiersaid.twr.protocol.ClearedToEnterControlZone
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.CrossRunway
import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
import xyz.easiersaid.twr.protocol.AfterPassingLevelClimbTo
import xyz.easiersaid.twr.protocol.ApproachComponent
import xyz.easiersaid.twr.protocol.ApproachType
import xyz.easiersaid.twr.protocol.BacktrackRunway
import xyz.easiersaid.twr.protocol.CircuitDirection
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.JoinCircuit
import xyz.easiersaid.twr.protocol.JoinType
import xyz.easiersaid.twr.protocol.Knots
import xyz.easiersaid.twr.protocol.MaintainSpeed
import xyz.easiersaid.twr.protocol.MaintainAltitudeUntilEstablished
import xyz.easiersaid.twr.protocol.MaintainLevel
import xyz.easiersaid.twr.protocol.ReduceSpeedTo
import xyz.easiersaid.twr.protocol.RemainOutsideControlledAirspace
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RouteSpec
import xyz.easiersaid.twr.protocol.ConfirmSquawk
import xyz.easiersaid.twr.protocol.Speed
import xyz.easiersaid.twr.protocol.Squawk
import xyz.easiersaid.twr.protocol.SquawkIdent
import xyz.easiersaid.twr.protocol.SquawkNormal
import xyz.easiersaid.twr.protocol.SquawkStandby
import xyz.easiersaid.twr.protocol.StopSquawk
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.protocol.TickNumber
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.TransponderMode
import xyz.easiersaid.twr.protocol.SpecialVfrClearance

class CompletionEvaluationTest {

    @Test
    fun compoundTaxiCompletionAdvancesCompletedSteps() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(
                aerodromeId = FixtureIds.aerodrome,
                currentPoint = FixtureIds.standPoint
            ),
            clearance = structuredClearance(
                id = "CLR-TAXI-COMP",
                domain = ClearanceDomain.GROUND,
                content = ClearanceContent.Compound(
                    steps = arrow.core.nonEmptyListOf(
                        TaxiToHoldingPoint(
                            target = TEST_AIRCRAFT,
                            destination = FixtureIds.holdShort27,
                            runway = FixtureIds.runway27,
                            via = listOf(FixtureIds.apronJunction)
                        ),
                        CrossRunway(
                            target = TEST_AIRCRAFT,
                            runway = FixtureIds.runway09
                        ),
                        HoldShortOf(
                            target = TEST_AIRCRAFT,
                            runway = FixtureIds.runway27
                        )
                    )
                )
            )
        ).requireResolved()

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.holdShort27,
                entities = setOf(EntityRef.TaxiwayRef(FixtureIds.taxiwayAlpha)),
                onGround = true,
                transitionHistory = setOf(EntityRef.RunwayRef(FixtureIds.runway09))
            )
        )

        assertEquals(
            listOf(
                CompletionResult.COMPLETE,
                CompletionResult.COMPLETE,
                CompletionResult.NOT_APPLICABLE
            ),
            evaluation.stepResults.map { stepResult -> stepResult.result }
        )
        assertEquals(setOf(0, 1), evaluation.newlyCompletedSteps)
        assertEquals(setOf(0, 1), evaluation.completedSteps)
        assertEquals(ClearanceStatus.COMPLETED, evaluation.updated.source.status)
        assertEquals(true, evaluation.isComplete)
    }

    @Test
    fun mixedRouteFrequencyCompletionUpdatesCompoundState() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-ROUTE-FREQ",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Compound(
                    steps = arrow.core.nonEmptyListOf(
                        ClearedTo(
                            target = TEST_AIRCRAFT,
                            clearanceLimit = FixId("HOLD"),
                            route = RouteSpec.ViaSid(FixtureIds.sid)
                        ),
                        ContactFrequency(
                            target = TEST_AIRCRAFT,
                            role = RoleName.APPROACH
                        )
                    )
                )
            )
        ).requireResolved()

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.holdFixPoint,
                entities = setOf(EntityRef.HoldingPatternRef(FixtureIds.hold)),
                onGround = false,
                transitionHistory = emptySet(),
                radioState = RadioState(
                    currentRole = RoleName.APPROACH,
                    currentFrequency = Frequency.unsafe("120.100"),
                    lastContactRole = RoleName.APPROACH
                )
            )
        )

        assertEquals(
            listOf(
                CompletionResult.COMPLETE,
                CompletionResult.COMPLETE
            ),
            evaluation.stepResults.map { stepResult -> stepResult.result }
        )
        assertEquals(setOf(0, 1), evaluation.completedSteps)
        assertEquals(ClearanceStatus.COMPLETED, evaluation.updated.source.status)
    }

    @Test
    fun routeClearanceCompletesOnReachedFixOrHoldingPatternEntry() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-ROUTE-WORLDBACKED",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(
                    ClearedTo(
                        target = TEST_AIRCRAFT,
                        clearanceLimit = FixId("HOLD"),
                        route = RouteSpec.ViaSid(FixtureIds.sid)
                    )
                )
            )
        ).requireResolved()

        val reachedFix = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.sidExit,
                entities = emptySet(),
                reachedFixes = setOf(FixId("HOLD")),
                onGround = false
            )
        )
        val enteredHold = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.holdFixPoint,
                entities = setOf(EntityRef.HoldingPatternRef(FixtureIds.hold)),
                onGround = false
            )
        )

        assertEquals(CompletionResult.COMPLETE, reachedFix.stepResults.single().result)
        assertEquals(ClearanceStatus.COMPLETED, reachedFix.updated.source.status)
        assertEquals(CompletionResult.COMPLETE, enteredHold.stepResults.single().result)
        assertEquals(ClearanceStatus.COMPLETED, enteredHold.updated.source.status)
    }

    @Test
    fun approachCompletesOnLandingOrMissedApproachHold() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-APPROACH-WORLDBACKED",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(
                    ClearedApproach(
                        target = TEST_AIRCRAFT,
                        approachType = ApproachType.ILS,
                        runway = FixtureIds.runway09
                    )
                )
            )
        ).requireResolved()

        val landed = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.runway09Threshold,
                entities = setOf(EntityRef.RunwayRef(FixtureIds.runway09)),
                onGround = true,
                transitionHistory = setOf(EntityRef.RunwayRef(FixtureIds.runway09))
            )
        )
        val enteredMissedHold = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.holdFixPoint,
                entities = setOf(EntityRef.HoldingPatternRef(FixtureIds.hold)),
                onGround = false
            )
        )

        assertEquals(CompletionResult.COMPLETE, landed.stepResults.single().result)
        assertEquals(ClearanceStatus.COMPLETED, landed.updated.source.status)
        assertEquals(CompletionResult.COMPLETE, enteredMissedHold.stepResults.single().result)
        assertEquals(ClearanceStatus.COMPLETED, enteredMissedHold.updated.source.status)
    }

    @Test
    fun joinCircuitCompletesOnCircuitMembershipAndAltitude() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-JOIN-CIRCUIT",
                domain = ClearanceDomain.RUNWAY,
                content = ClearanceContent.Single(
                    JoinCircuit(
                        target = TEST_AIRCRAFT,
                        circuitDirection = CircuitDirection.LEFT_HAND,
                        joinType = JoinType.DOWNWIND,
                        runway = FixtureIds.runway09
                    )
                )
            )
        ).requireResolved()

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.crosswindEnd,
                entities = setOf(EntityRef.CircuitProcedureRef(FixtureIds.circuit09)),
                altitude = Level.AltitudeFeet.unsafe(1800),
                onGround = false
            )
        )

        assertEquals(CompletionResult.COMPLETE, evaluation.stepResults.single().result)
        assertEquals(ClearanceStatus.COMPLETED, evaluation.updated.source.status)
    }

    @Test
    fun remainOutsideControlledAirspaceUsesWorldBackedInsideOutsideObservation() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-ROCA",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(
                    RemainOutsideControlledAirspace(
                        target = TEST_AIRCRAFT,
                        airspace = FixtureIds.airspace
                    )
                )
            )
        ).requireResolved()

        val outsideEvaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = xyz.easiersaid.twr.protocol.PointId("OUTSIDE-CTR"),
                entities = emptySet(),
                onGround = false
            )
        )
        val insideEvaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.runway09Threshold,
                entities = setOf(EntityRef.AirspaceVolumeRef(FixtureIds.airspace)),
                onGround = false
            )
        )

        assertEquals(CompletionResult.NOT_APPLICABLE, outsideEvaluation.stepResults.single().result)
        assertEquals(ClearanceStatus.ACTIVE, outsideEvaluation.updated.source.status)
        assertEquals(CompletionResult.NOT_COMPLETE, insideEvaluation.stepResults.single().result)
        assertEquals(ClearanceStatus.ACTIVE, insideEvaluation.updated.source.status)
    }

    @Test
    fun remainOutsideControlledAirspaceUsesExplicitMemberPointWithoutEntityRef() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-ROCA-MEMBER-POINT",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(
                    RemainOutsideControlledAirspace(
                        target = TEST_AIRCRAFT,
                        airspace = FixtureIds.airspace
                    )
                )
            )
        ).requireResolved()

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.runway09Threshold,
                entities = emptySet(),
                onGround = false
            )
        )

        assertEquals(CompletionResult.NOT_COMPLETE, evaluation.stepResults.single().result)
        assertEquals(ClearanceStatus.ACTIVE, evaluation.updated.source.status)
    }

    @Test
    fun remainOutsideControlledAirspaceTreatsEntryAndExitTransitionsDifferently() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-ROCA-TRANSITIONS",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(
                    RemainOutsideControlledAirspace(
                        target = TEST_AIRCRAFT,
                        airspace = FixtureIds.airspace
                    )
                )
            )
        ).requireResolved()

        val entryEvaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.runway09Threshold,
                entities = setOf(EntityRef.AirspaceVolumeRef(FixtureIds.airspace)),
                onGround = false,
                transitionHistory = setOf(EntityRef.AirspaceVolumeRef(FixtureIds.airspace))
            )
        )
        val exitEvaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = xyz.easiersaid.twr.protocol.PointId("OUTSIDE-CTR"),
                entities = emptySet(),
                onGround = false,
                transitionHistory = setOf(EntityRef.AirspaceVolumeRef(FixtureIds.airspace))
            )
        )

        assertEquals(CompletionResult.NOT_COMPLETE, entryEvaluation.stepResults.single().result)
        assertEquals(ClearanceStatus.ACTIVE, entryEvaluation.updated.source.status)
        assertEquals(CompletionResult.NOT_APPLICABLE, exitEvaluation.stepResults.single().result)
        assertEquals(ClearanceStatus.ACTIVE, exitEvaluation.updated.source.status)
    }

    @Test
    fun controlZoneAndSpecialVfrPermissionsRemainActiveWhenObservedInsideAirspace() {
        val world = sampleWorld()
        val enterZone = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-ENTER-CTR",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(
                    ClearedToEnterControlZone(
                        target = TEST_AIRCRAFT,
                        airspace = FixtureIds.airspace
                    )
                )
            )
        ).requireResolved()
        val specialVfr = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-SVFR",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(
                    SpecialVfrClearance(
                        target = TEST_AIRCRAFT,
                        airspace = FixtureIds.airspace
                    )
                )
            )
        ).requireResolved()

        val insideView = CompletionView(
            position = FixtureIds.runway09Threshold,
            entities = setOf(EntityRef.AirspaceVolumeRef(FixtureIds.airspace)),
            onGround = false
        )

        assertEquals(
            CompletionResult.NOT_APPLICABLE,
            evaluateCompletion(enterZone, insideView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.NOT_APPLICABLE,
            evaluateCompletion(specialVfr, insideView).stepResults.single().result
        )
        assertEquals(
            ClearanceStatus.ACTIVE,
            evaluateCompletion(enterZone, insideView).updated.source.status
        )
        assertEquals(
            ClearanceStatus.ACTIVE,
            evaluateCompletion(specialVfr, insideView).updated.source.status
        )
    }

    @Test
    fun controlZoneAndSpecialVfrPermissionsRemainActiveAcrossEntryTransition() {
        val world = sampleWorld()
        val enterZone = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-ENTER-CTR-TRANSITION",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(
                    ClearedToEnterControlZone(
                        target = TEST_AIRCRAFT,
                        airspace = FixtureIds.airspace
                    )
                )
            )
        ).requireResolved()
        val specialVfr = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-SVFR-TRANSITION",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(
                    SpecialVfrClearance(
                        target = TEST_AIRCRAFT,
                        airspace = FixtureIds.airspace
                    )
                )
            )
        ).requireResolved()

        val entryView = CompletionView(
            position = FixtureIds.runway09Threshold,
            entities = setOf(EntityRef.AirspaceVolumeRef(FixtureIds.airspace)),
            onGround = false,
            transitionHistory = setOf(EntityRef.AirspaceVolumeRef(FixtureIds.airspace))
        )

        assertEquals(
            CompletionResult.NOT_APPLICABLE,
            evaluateCompletion(enterZone, entryView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.NOT_APPLICABLE,
            evaluateCompletion(specialVfr, entryView).stepResults.single().result
        )
        assertEquals(
            ClearanceStatus.ACTIVE,
            evaluateCompletion(enterZone, entryView).updated.source.status
        )
        assertEquals(
            ClearanceStatus.ACTIVE,
            evaluateCompletion(specialVfr, entryView).updated.source.status
        )
    }

    @Test
    fun controlZoneAndSpecialVfrPermissionsCompleteOnExitTransitionOrLanding() {
        val world = sampleWorld()
        val enterZone = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-ENTER-CTR-EXIT",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(
                    ClearedToEnterControlZone(
                        target = TEST_AIRCRAFT,
                        airspace = FixtureIds.airspace
                    )
                )
            )
        ).requireResolved()
        val specialVfr = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-SVFR-LAND",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Single(
                    SpecialVfrClearance(
                        target = TEST_AIRCRAFT,
                        airspace = FixtureIds.airspace
                    )
                )
            )
        ).requireResolved()

        val exitView = CompletionView(
            position = xyz.easiersaid.twr.protocol.PointId("OUTSIDE-CTR"),
            entities = emptySet(),
            onGround = false,
            transitionHistory = setOf(EntityRef.AirspaceVolumeRef(FixtureIds.airspace))
        )
        val landingView = CompletionView(
            position = FixtureIds.standPoint,
            entities = emptySet(),
            onGround = true
        )

        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(enterZone, exitView).stepResults.single().result
        )
        assertEquals(
            ClearanceStatus.COMPLETED,
            evaluateCompletion(enterZone, exitView).updated.source.status
        )
        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(specialVfr, landingView).stepResults.single().result
        )
        assertEquals(
            ClearanceStatus.COMPLETED,
            evaluateCompletion(specialVfr, landingView).updated.source.status
        )
    }

    @Test
    fun controlZoneCompoundCompletesWhenImmediateFrequencyTailCompletes() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-ENTER-CTR-FREQ",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Compound(
                    steps = arrow.core.nonEmptyListOf(
                        ClearedToEnterControlZone(
                            target = TEST_AIRCRAFT,
                            airspace = FixtureIds.airspace
                        ),
                        ContactFrequency(
                            target = TEST_AIRCRAFT,
                            role = RoleName.TOWER
                        )
                    )
                )
            )
        ).requireResolved()

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.runway09Threshold,
                entities = setOf(EntityRef.AirspaceVolumeRef(FixtureIds.airspace)),
                onGround = false,
                radioState = RadioState(
                    currentRole = RoleName.TOWER,
                    currentFrequency = Frequency.unsafe("118.500"),
                    lastContactRole = RoleName.TOWER
                )
            )
        )

        assertEquals(
            listOf(
                CompletionResult.NOT_APPLICABLE,
                CompletionResult.COMPLETE
            ),
            evaluation.stepResults.map { stepResult -> stepResult.result }
        )
        assertEquals(setOf(1), evaluation.newlyCompletedSteps)
        assertEquals(ClearanceStatus.COMPLETED, evaluation.updated.source.status)
    }

    @Test
    fun remainOutsideCompoundKeepsPrimaryActiveAfterImmediateFrequencyTailCompletes() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-ROCA-FREQ",
                domain = ClearanceDomain.ROUTE,
                content = ClearanceContent.Compound(
                    steps = arrow.core.nonEmptyListOf(
                        RemainOutsideControlledAirspace(
                            target = TEST_AIRCRAFT,
                            airspace = FixtureIds.airspace
                        ),
                        ContactFrequency(
                            target = TEST_AIRCRAFT,
                            role = RoleName.TOWER
                        )
                    )
                )
            )
        ).requireResolved()

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = xyz.easiersaid.twr.protocol.PointId("OUTSIDE-CTR"),
                entities = emptySet(),
                onGround = false,
                radioState = RadioState(
                    currentRole = RoleName.TOWER,
                    currentFrequency = Frequency.unsafe("118.500"),
                    lastContactRole = RoleName.TOWER
                )
            )
        )

        assertEquals(
            listOf(
                CompletionResult.NOT_APPLICABLE,
                CompletionResult.COMPLETE
            ),
            evaluation.stepResults.map { stepResult -> stepResult.result }
        )
        assertEquals(setOf(1), evaluation.newlyCompletedSteps)
        assertEquals(ClearanceStatus.ACTIVE, evaluation.updated.source.status)
    }

    @Test
    fun speedCompletionDistinguishesMaintainReduceAndIncreaseSemantics() {
        val world = sampleWorld()

        val maintain = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-MAINTAIN-SPEED",
                domain = ClearanceDomain.SPEED,
                content = ClearanceContent.Single(
                    MaintainSpeed(
                        target = TEST_AIRCRAFT,
                        speed = Speed.InKnots(Knots.unsafe(180))
                    )
                )
            )
        ).requireResolved()
        val reduce = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-REDUCE-SPEED",
                domain = ClearanceDomain.SPEED,
                content = ClearanceContent.Single(
                    ReduceSpeedTo(
                        target = TEST_AIRCRAFT,
                        speed = Speed.InKnots(Knots.unsafe(180))
                    )
                )
            )
        ).requireResolved()

        val fastView = CompletionView(
            position = FixtureIds.holdFixPoint,
            entities = emptySet(),
            speed = Speed.InKnots(Knots.unsafe(220)),
            onGround = false
        )
        val targetView = fastView.copy(speed = Speed.InKnots(Knots.unsafe(180)))
        val slowView = fastView.copy(speed = Speed.InKnots(Knots.unsafe(170)))

        assertEquals(
            CompletionResult.NOT_COMPLETE,
            evaluateCompletion(maintain, fastView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(maintain, targetView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.NOT_COMPLETE,
            evaluateCompletion(reduce, fastView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(reduce, targetView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(reduce, slowView).stepResults.single().result
        )
    }

    @Test
    fun levelCompletionHandlesMaintainAndAfterPassingInstructions() {
        val world = sampleWorld()
        val maintain = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-MAINTAIN-LEVEL",
                domain = ClearanceDomain.LEVEL,
                content = ClearanceContent.Single(
                    MaintainLevel(
                        target = TEST_AIRCRAFT,
                        level = Level.AltitudeFeet.unsafe(5000)
                    )
                )
            )
        ).requireResolved()
        val afterPassing = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-AFTER-PASSING",
                domain = ClearanceDomain.LEVEL,
                content = ClearanceContent.Single(
                    AfterPassingLevelClimbTo(
                        target = TEST_AIRCRAFT,
                        afterPassing = Level.AltitudeFeet.unsafe(3000),
                        climbTo = Level.AltitudeFeet.unsafe(5000)
                    )
                )
            )
        ).requireResolved()

        val lowView = CompletionView(
            position = FixtureIds.holdFixPoint,
            entities = emptySet(),
            altitude = Level.AltitudeFeet.unsafe(4500),
            onGround = false
        )
        val targetView = lowView.copy(altitude = Level.AltitudeFeet.unsafe(5000))

        assertEquals(
            CompletionResult.NOT_COMPLETE,
            evaluateCompletion(maintain, lowView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(maintain, targetView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.NOT_COMPLETE,
            evaluateCompletion(afterPassing, lowView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(afterPassing, targetView).stepResults.single().result
        )
    }

    @Test
    fun vacateViaCompletesAtAssignedExitPoint() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-VACATE-VIA",
                domain = ClearanceDomain.RUNWAY,
                content = ClearanceContent.Single(
                    AfterLandingVacateVia(
                        target = TEST_AIRCRAFT,
                        exit = FixtureIds.runwayMid
                    )
                )
            )
        ).requireResolved()

        val evaluation = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.runwayMid,
                entities = setOf(EntityRef.RunwayRef(FixtureIds.runway09)),
                onGround = true
            )
        )

        assertEquals(CompletionResult.COMPLETE, evaluation.stepResults.single().result)
        assertEquals(ClearanceStatus.COMPLETED, evaluation.updated.source.status)
    }

    @Test
    fun backtrackCompletesAtFarRunwayEnd() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-BACKTRACK",
                domain = ClearanceDomain.GROUND,
                content = ClearanceContent.Single(
                    BacktrackRunway(
                        target = TEST_AIRCRAFT,
                        runway = FixtureIds.runway09
                    )
                )
            )
        ).requireResolved()

        val beforeEnd = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.runwayMid,
                entities = setOf(EntityRef.RunwayRef(FixtureIds.runway09)),
                onGround = true
            )
        )
        val atFarEnd = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.runway27Threshold,
                entities = setOf(EntityRef.RunwayRef(FixtureIds.runway09)),
                onGround = true
            )
        )

        assertEquals(CompletionResult.NOT_COMPLETE, beforeEnd.stepResults.single().result)
        assertEquals(CompletionResult.COMPLETE, atFarEnd.stepResults.single().result)
        assertEquals(ClearanceStatus.COMPLETED, atFarEnd.updated.source.status)
        assertEquals(true, atFarEnd.isComplete)
    }

    @Test
    fun maintainAltitudeUntilEstablishedCompletesOnApproachCapture() {
        val world = sampleWorld()
        val resolved = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-MAINTAIN-UNTIL-ESTABLISHED",
                domain = ClearanceDomain.LEVEL,
                content = ClearanceContent.Single(
                    MaintainAltitudeUntilEstablished(
                        target = TEST_AIRCRAFT,
                        level = Level.AltitudeFeet.unsafe(3000),
                        on = ApproachComponent.LOCALISER
                    )
                )
            )
        ).requireResolved()

        val notEstablished = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.fafPoint,
                entities = setOf(EntityRef.ApproachRef(FixtureIds.approach)),
                altitude = Level.AltitudeFeet.unsafe(3000),
                onGround = false
            )
        )
        val established = evaluateCompletion(
            clearance = resolved,
            view = CompletionView(
                position = FixtureIds.fafPoint,
                entities = setOf(EntityRef.ApproachRef(FixtureIds.approach)),
                altitude = Level.AltitudeFeet.unsafe(3000),
                onGround = false,
                establishedApproachComponents = setOf(ApproachComponent.LOCALISER)
            )
        )

        assertEquals(CompletionResult.NOT_COMPLETE, notEstablished.stepResults.single().result)
        assertEquals(CompletionResult.COMPLETE, established.stepResults.single().result)
    }

    @Test
    fun surveillanceInstructionsCompleteFromTransponderState() {
        val world = sampleWorld()
        val confirm = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-CONFIRM-SQUAWK",
                domain = ClearanceDomain.SQUAWK,
                content = ClearanceContent.Single(
                    ConfirmSquawk(
                        target = TEST_AIRCRAFT,
                        squawk = Squawk.unsafe(4521)
                    )
                )
            )
        ).requireResolved()
        val ident = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-SQUAWK-IDENT",
                domain = ClearanceDomain.SQUAWK,
                content = ClearanceContent.Single(
                    SquawkIdent(target = TEST_AIRCRAFT)
                )
            )
        ).requireResolved()
        val normal = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-SQUAWK-NORMAL",
                domain = ClearanceDomain.SQUAWK,
                content = ClearanceContent.Single(
                    SquawkNormal(
                        target = TEST_AIRCRAFT,
                        mode = TransponderMode.NORMAL
                    )
                )
            )
        ).requireResolved()
        val standby = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-SQUAWK-STANDBY",
                domain = ClearanceDomain.SQUAWK,
                content = ClearanceContent.Single(
                    SquawkStandby(target = TEST_AIRCRAFT)
                )
            )
        ).requireResolved()
        val stopCharlie = world.resolveClearance(
            context = ClearanceResolutionContext(FixtureIds.aerodrome),
            clearance = structuredClearance(
                id = "CLR-STOP-CHARLIE",
                domain = ClearanceDomain.SQUAWK,
                content = ClearanceContent.Single(
                    StopSquawk(
                        target = TEST_AIRCRAFT,
                        mode = TransponderMode.CHARLIE
                    )
                )
            )
        ).requireResolved()

        val transponderView = CompletionView(
            position = FixtureIds.holdFixPoint,
            entities = emptySet(),
            onGround = false,
            transponderCode = Squawk.unsafe(4521),
            transponderMode = TransponderMode.NORMAL,
            transponderIdentActive = true
        )

        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(confirm, transponderView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(ident, transponderView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(normal, transponderView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.NOT_COMPLETE,
            evaluateCompletion(standby, transponderView).stepResults.single().result
        )
        assertEquals(
            CompletionResult.COMPLETE,
            evaluateCompletion(stopCharlie, transponderView).stepResults.single().result
        )
    }
}

private val TEST_AIRCRAFT = AircraftId("TEST123")

private fun structuredClearance(
    id: String,
    domain: ClearanceDomain,
    content: ClearanceContent,
    status: ClearanceStatus = ClearanceStatus.ACTIVE
): StructuredClearance =
    StructuredClearance(
        id = ClearanceId(id),
        aircraft = TEST_AIRCRAFT,
        content = content,
        domain = domain,
        issuedBy = ControllerId("CTRL-1"),
        issuedAt = TickNumber(1),
        status = status
    )

private fun <T> ResolutionResult<T>.requireResolved(): T =
    getOrElse { failure -> error("Expected resolved, got ${failure.code}: ${failure.message}") }
