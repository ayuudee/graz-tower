package xyz.easiersaid.twr.controller.certify

import arrow.core.Either
import xyz.easiersaid.twr.controller.AircraftObservation
import xyz.easiersaid.twr.controller.ControllerView
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.controller.bdi.ProposedAction
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.Degrees
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.Feet
import xyz.easiersaid.twr.core.world.Path
import xyz.easiersaid.twr.core.world.Runway
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ConfirmSquawk
import xyz.easiersaid.twr.protocol.ConditionalPredicate
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.GoAround
import xyz.easiersaid.twr.protocol.HoldPosition
import xyz.easiersaid.twr.protocol.HoldPositionCancelTakeoff
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.NumberInSequence
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.StartupApproved
import xyz.easiersaid.twr.protocol.TrafficAction
import xyz.easiersaid.twr.protocol.TrafficRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CertificationBoundarySpec {
    private val aircraft = AircraftId("OEABC")
    private val runway = RunwayId("16C")

    @Test
    fun `classifier maps initial safety surface to kernel requirements`() {
        val surface = certificationPlanFor(HoldPosition(aircraft)).getOrNull()
            ?: error("HoldPosition should have a surface certification plan")
        assertEquals(setOf(KernelRequirement.Surface), surface.requirements)

        val runwayAccess = certificationPlanFor(LineUpAndWait(aircraft, runway)).getOrNull()
            ?: error("LineUpAndWait should have a joint runway-surface plan")
        assertEquals(setOf(KernelRequirement.Runway, KernelRequirement.Surface), runwayAccess.requirements)
        assertTrue(runwayAccess.joint)

        val airRunway = certificationPlanFor(GoAround(aircraft)).getOrNull()
            ?: error("GoAround should have a runway-air-separation plan")
        assertEquals(
            setOf(KernelRequirement.Runway, KernelRequirement.AirPath, KernelRequirement.Separation),
            airRunway.requirements,
        )

        val cancelTakeoff = certificationPlanFor(HoldPositionCancelTakeoff(aircraft)).getOrNull()
            ?: error("HoldPositionCancelTakeoff should have a joint runway-surface plan")
        assertEquals(setOf(KernelRequirement.Runway, KernelRequirement.Surface), cancelTakeoff.requirements)
        assertTrue(cancelTakeoff.joint)

        val vacate = certificationPlanFor(AfterLandingVacateVia(aircraft, PointId("E1"))).getOrNull()
            ?: error("AfterLandingVacateVia should have a joint runway-surface plan")
        assertEquals(setOf(KernelRequirement.Runway, KernelRequirement.Surface), vacate.requirements)
        assertTrue(vacate.joint)
    }

    @Test
    fun `classifier gives administrative instructions named no-certification plans`() {
        val sequence = certificationPlanFor(NumberInSequence.unsafe(aircraft, 1)).getOrNull()
            ?: error("NumberInSequence should be administratively classifiable")
        assertTrue(sequence.requirements.isEmpty())

        val frequency = certificationPlanFor(ContactFrequency(aircraft, RoleName.TOWER)).getOrNull()
            ?: error("ContactFrequency should be administratively classifiable")
        assertTrue(frequency.requirements.isEmpty())

        val surveillance = certificationPlanFor(ConfirmSquawk(aircraft, xyz.easiersaid.twr.protocol.Squawk.unsafe(7000))).getOrNull()
            ?: error("ConfirmSquawk should be administratively classifiable")
        assertTrue(surveillance.requirements.isEmpty())
    }

    @Test
    fun `unsupported safety-relevant instruction fails closed`() {
        val result = certificationPlanFor(StartupApproved(aircraft))
        assertTrue(result.isLeft())
        assertIs<CertificationFailure.UnsupportedInstruction>(result.leftOrNull())
    }

    @Test
    fun `certifier cannot certify unsupported safety-relevant instruction`() {
        val result = certifier().certify(
            action = ProposedAction(StartupApproved(aircraft)),
            context = contextWithTrackedAircraft(),
        )
        assertTrue(result.isLeft())
        assertIs<CertificationFailure.UnsupportedInstruction>(result.leftOrNull())
    }

    @Test
    fun `certifier requires tracked aircraft for kernel-backed plans`() {
        val result = certifier().certify(
            action = ProposedAction(LineUpAndWait(aircraft, runway)),
            context = contextWithTrackedAircraft(isTracked = false),
        )
        assertTrue(result.isLeft())
        assertIs<CertificationFailure.MissingAircraft>(result.leftOrNull())
    }

    @Test
    fun `certifier rejects conditional dispatch for instruction that cannot be conditional`() {
        val result = certifier().certify(
            action = ProposedAction(
                dispatch = Dispatch.Conditional(
                    instruction = HoldPositionCancelTakeoff(aircraft),
                    condition = ConditionalPredicate.AfterTraffic(
                        traffic = TrafficRef.ByCallsign(Callsign("OEXYZ")),
                        action = TrafficAction.CROSSING,
                    ),
                ),
            ),
            context = contextWithTrackedAircraft(
                entities = setOf(EntityRef.RunwayRef(runway)),
                world = worldWithRunway(),
            ),
        )
        assertTrue(result.isLeft())
        assertIs<CertificationFailure.CompatibilityRejected>(result.leftOrNull())
    }

    @Test
    fun `certifier rejects conditional dispatch in low visibility procedures`() {
        val result = certifier().certify(
            action = ProposedAction(
                dispatch = Dispatch.Conditional(
                    instruction = LineUpAndWait(aircraft, runway),
                    condition = ConditionalPredicate.AfterTraffic(
                        traffic = TrafficRef.ByCallsign(Callsign("OEXYZ")),
                        action = TrafficAction.CROSSING,
                    ),
                ),
            ),
            context = contextWithTrackedAircraft(lvpMode = true),
        )
        assertTrue(result.isLeft())
        assertIs<CertificationFailure.CompatibilityRejected>(result.leftOrNull())
    }

    @Test
    fun `certifier rejects cancel takeoff when aircraft is not observed on a runway`() {
        val result = runtimeCertifier().certify(
            action = ProposedAction(HoldPositionCancelTakeoff(aircraft)),
            context = contextWithTrackedAircraft(),
        )
        assertTrue(result.isLeft())
        assertIs<CertificationFailure.KernelRejected>(result.leftOrNull())
    }

    @Test
    fun `certifier rejects vacate exit that is not on observed runway`() {
        val result = runtimeCertifier().certify(
            action = ProposedAction(AfterLandingVacateVia(aircraft, PointId("BAD-EXIT"))),
            context = contextWithTrackedAircraft(
                entities = setOf(EntityRef.RunwayRef(runway)),
                activeRunway = runway,
                world = worldWithRunway(),
            ),
        )
        assertTrue(result.isLeft())
        assertIs<CertificationFailure.KernelRejected>(result.leftOrNull())
    }

    @Test
    fun `runtime runway evidence fails closed when active runway is absent`() {
        val result = runtimeCertifier().certify(
            action = ProposedAction(LineUpAndWait(aircraft, runway)),
            context = contextWithTrackedAircraft(),
        )
        assertTrue(result.isLeft())
        assertIs<CertificationFailure.KernelRejected>(result.leftOrNull())
    }

    @Test
    fun `runtime runway evidence fails closed for runway-required instruction without runway field`() {
        val result = runtimeCertifier().certify(
            action = ProposedAction(GoAround(aircraft)),
            context = contextWithTrackedAircraft(onGround = false),
        )
        assertTrue(result.isLeft())
        val failure = assertIs<CertificationFailure.KernelRejected>(result.leftOrNull())
        assertEquals(KernelRequirement.Runway, failure.requirement)
    }

    @Test
    fun `certifier creates non-empty evidence for supported kernel-backed instruction`() {
        val result = certifier().certify(
            action = ProposedAction(LineUpAndWait(aircraft, runway)),
            context = contextWithTrackedAircraft(),
        )
        val certified = result.getOrNull() ?: error("LineUpAndWait should certify with the test kernels")
        assertEquals(aircraft, certified.aircraft)
        assertEquals(setOf(KernelRequirement.Runway, KernelRequirement.Surface), certified.plan.requirements)
        assertEquals(
            setOf(KernelRequirement.Runway, KernelRequirement.Surface),
            certified.evidence.all.mapNotNull { (it as? CertificationEvidence.KernelBacked)?.requirement }.toSet(),
        )
        assertTrue(
            certified.evidence.all.any {
                it is CertificationEvidence.RuntimeChecked && it.checkId == "dispatch-compatibility"
            },
        )
    }

    @Test
    fun `runtime certifier creates full runway airpath separation evidence for go-around`() {
        val result = runtimeCertifier().certify(
            action = ProposedAction(GoAround(aircraft)),
            context = contextWithTrackedAircraft(activeRunway = runway, onGround = false),
        )
        val certified = result.getOrNull() ?: error("GoAround should certify for airborne aircraft with active runway")
        assertEquals(
            setOf(KernelRequirement.Runway, KernelRequirement.AirPath, KernelRequirement.Separation),
            certified.evidence.all.mapNotNull { (it as? CertificationEvidence.KernelBacked)?.requirement }.toSet(),
        )
        assertTrue(
            certified.evidence.all.any {
                it is CertificationEvidence.RuntimeChecked && it.checkId == "dispatch-compatibility"
            },
        )
    }

    @Test
    fun `certifier creates named no-certification evidence for administrative instruction`() {
        val result = certifier().certify(
            action = ProposedAction(NumberInSequence.unsafe(aircraft, 1)),
            context = contextWithTrackedAircraft(isTracked = false),
        )
        val certified = result.getOrNull() ?: error("NumberInSequence should not require a tracked aircraft")
        assertTrue(certified.plan.requirements.isEmpty())
        val evidence = certified.evidence.head
        assertIs<CertificationEvidence.NotRequired>(evidence)
        assertEquals(NoCertificationRequired.AdministrativeSequencing, evidence.reason)
    }

    private fun certifier(): ActionCertifier = ActionCertifier(TestKernelCertifiers)

    private fun runtimeCertifier(): ActionCertifier = ActionCertifier(KotlinRuntimeKernelCertifiers)

    private fun contextWithTrackedAircraft(
        isTracked: Boolean = true,
        entities: Set<EntityRef> = emptySet(),
        world: AviationWorld = AviationWorld(),
        lvpMode: Boolean = false,
        activeRunway: RunwayId? = null,
        onGround: Boolean = true,
    ): CertificationContext {
        val observation = AircraftObservation(
            id = aircraft,
            callsign = Callsign("OEABC"),
            position = PointId("P"),
            entities = entities,
            altitude = null,
            speed = null,
            onGround = onGround,
        )
        val tracked = if (isTracked) mapOf(aircraft to observation) else emptyMap()
        val view = ControllerView(
            time = SimTime.ZERO,
            controllerId = xyz.easiersaid.twr.protocol.ControllerId("TWR"),
            role = RoleName.TOWER,
            aerodromeId = AerodromeId("LOWG"),
            responsibilities = setOf(aircraft),
            aircraft = tracked,
            runways = emptyMap(),
            activeClearances = emptyMap(),
            receivedMessages = emptyList(),
            weather = null,
            worldIndex = WorldIndex(),
            lvpMode = lvpMode,
        )
        return CertificationContext(
            view = view,
            beliefs = BeliefState.EMPTY.copy(trackedAircraft = tracked, activeRunway = activeRunway),
            world = world,
            decisionTime = SimTime.ZERO,
        )
    }

    private fun worldWithRunway(): AviationWorld {
        val runwayModel = Runway(
            id = runway,
            path = Path(listOf(PointId("T"), PointId("D"))),
            threshold = PointId("T"),
        )
        val aerodrome = Aerodrome(
            icao = AerodromeId("LOWG"),
            elevation = Feet(0),
            magneticVariation = Degrees(0.0),
            transitionAltitude = xyz.easiersaid.twr.protocol.Level.AltitudeFeet.unsafe(5000),
            runways = mapOf(runway to runwayModel),
        )
        return AviationWorld(aerodromes = mapOf(AerodromeId("LOWG") to aerodrome))
    }
}

private object TestKernelCertifiers : RuntimeKernelCertifiers {
    override fun certifyRunway(
        work: RunwayCertificationWork,
    ): Either<CertificationFailure, CertificationEvidence.KernelBacked> =
        evidence(KernelRequirement.Runway)

    override fun certifySurface(
        work: SurfaceCertificationWork,
    ): Either<CertificationFailure, CertificationEvidence.KernelBacked> =
        evidence(KernelRequirement.Surface)

    override fun certifyAirPath(
        work: AirPathCertificationWork,
    ): Either<CertificationFailure, CertificationEvidence.KernelBacked> =
        evidence(KernelRequirement.AirPath)

    override fun certifySeparation(
        work: SeparationCertificationWork,
    ): Either<CertificationFailure, CertificationEvidence.KernelBacked> =
        evidence(KernelRequirement.Separation)

    private fun evidence(
        requirement: KernelRequirement,
    ): Either<CertificationFailure, CertificationEvidence.KernelBacked> =
        Either.Right(CertificationEvidence.KernelBacked(requirement, "test kernel approved"))
}
