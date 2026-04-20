import CertifiedAtc.GreenfieldCompletion

namespace CertifiedAtc
namespace Greenfield

theorem publishedHandoffSatisfied_none (observation : CompletionObservation) : publishedHandoffSatisfied none observation = true := by rfl
theorem groundPointReached_of_position (point : PointId) (observation : CompletionObservation) (h : observation.position = some point) : groundPointReached point observation = true := by simp [groundPointReached, h]
theorem groundPointReached_of_traversed (point : PointId) (observation : CompletionObservation) (h : point ∈ observation.traversedGroundPoints) : groundPointReached point observation = true := by simp [groundPointReached, h]
theorem groundPointReached_of_reachedHoldingPoint (point : PointId) (observation : CompletionObservation) (h : point ∈ observation.reachedHoldingPoints) : groundPointReached point observation = true := by simp [groundPointReached, h]
theorem airspaceInside_of_activeAirspace (airspace : ResolvedAirspaceInstruction) (observation : CompletionObservation) (h : airspace.airspace ∈ observation.activeAirspaces) : airspaceInside airspace observation = true := by simp [airspaceInside, h]
theorem airspaceInside_of_position (airspace : ResolvedAirspaceInstruction) (observation : CompletionObservation) (point : PointId) (hPos : observation.position = some point) (hPoint : point ∈ airspace.points) : airspaceInside airspace observation = true := by simp [airspaceInside, hPos, hPoint]
theorem airspaceEntered_of_transition_and_inside (airspace : ResolvedAirspaceInstruction) (observation : CompletionObservation) (hTransition : airspace.airspace ∈ observation.airspaceTransitions) (hInside : airspaceInside airspace observation = true) : airspaceEntered airspace observation = true := by simp [airspaceEntered, hTransition, hInside]
theorem airspaceExited_of_transition_and_not_inside (airspace : ResolvedAirspaceInstruction) (observation : CompletionObservation) (hTransition : airspace.airspace ∈ observation.airspaceTransitions) (hInside : airspaceInside airspace observation = false) : airspaceExited airspace observation = true := by simp [airspaceExited, hTransition, hInside]
theorem airspaceEntered_implies_transition (airspace : ResolvedAirspaceInstruction) (observation : CompletionObservation) (h : airspaceEntered airspace observation = true) : airspace.airspace ∈ observation.airspaceTransitions := by
  have hParts :
    airspace.airspace ∈ observation.airspaceTransitions ∧
      airspaceInside airspace observation = true := by
    simpa [airspaceEntered] using h
  exact hParts.1
theorem airspaceEntered_implies_inside (airspace : ResolvedAirspaceInstruction) (observation : CompletionObservation) (h : airspaceEntered airspace observation = true) : airspaceInside airspace observation = true := by
  have hParts :
    airspace.airspace ∈ observation.airspaceTransitions ∧
      airspaceInside airspace observation = true := by
    simpa [airspaceEntered] using h
  exact hParts.2
theorem airspaceExited_implies_transition (airspace : ResolvedAirspaceInstruction) (observation : CompletionObservation) (h : airspaceExited airspace observation = true) : airspace.airspace ∈ observation.airspaceTransitions := by
  have hParts :
    airspace.airspace ∈ observation.airspaceTransitions ∧
      airspaceInside airspace observation = false := by
    simpa [airspaceExited] using h
  exact hParts.1
theorem airspaceExited_implies_not_inside (airspace : ResolvedAirspaceInstruction) (observation : CompletionObservation) (h : airspaceExited airspace observation = true) : airspaceInside airspace observation = false := by
  have hParts :
    airspace.airspace ∈ observation.airspaceTransitions ∧
      airspaceInside airspace observation = false := by
    simpa [airspaceExited] using h
  exact hParts.2
theorem publishedHandoffSatisfied_of_holdingPoint (handoff : ResolvedPublishedHandoff) (observation : CompletionObservation) (point : PointId) (hLoc : handoff.location = ResolvedPublishedHandoffPoint.holdingPoint point) (hReached : groundPointReached point observation = true) : publishedHandoffSatisfied (some handoff) observation = true := by
  simp [publishedHandoffSatisfied, hLoc, hReached]
theorem publishedHandoffSatisfied_of_boundaryFix (handoff : ResolvedPublishedHandoff) (observation : CompletionObservation) (fix : FixId) (hLoc : handoff.location = ResolvedPublishedHandoffPoint.boundaryFix fix) (hReached : fix ∈ observation.reachedFixes) : publishedHandoffSatisfied (some handoff) observation = true := by
  simp [publishedHandoffSatisfied, hLoc, hReached]
theorem publishedHandoffSatisfied_of_airborne (handoff : ResolvedPublishedHandoff) (observation : CompletionObservation) (hLoc : handoff.location = ResolvedPublishedHandoffPoint.airborne) (hAirborne : observation.onGround = false) : publishedHandoffSatisfied (some handoff) observation = true := by
  simp [publishedHandoffSatisfied, hLoc, hAirborne]
def publishedHandoffContactFrequencyStep (aircraft : AircraftId) (role : RoleName) (handoff : ResolvedPublishedHandoff) : ResolvedStep :=
  compileResolvedStep
    0
    .route
    (.contactFrequency aircraft role none)
    (.frequencyChange
      { roleName := role
        instructedFrequency := none
        publishedHandoff := some handoff })
    (by simp [resolutionCompatible])
theorem observedResolvedStepCompletion_contactFrequency_holdingPoint_complete (aircraft : AircraftId) (role : RoleName) (handoff : ResolvedPublishedHandoff) (observation : CompletionObservation) (point : PointId) (hLoc : handoff.location = ResolvedPublishedHandoffPoint.holdingPoint point) (hRole : observation.currentRole = some role) (hReached : groundPointReached point observation = true) : observedResolvedStepCompletion? observation (publishedHandoffContactFrequencyStep aircraft role handoff) = some .complete := by
  have hHandoff : publishedHandoffSatisfied (some handoff) observation = true := by
    exact publishedHandoffSatisfied_of_holdingPoint handoff observation point hLoc hReached
  simp [publishedHandoffContactFrequencyStep, observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible, hRole, hHandoff]
theorem observedResolvedStepCompletion_contactFrequency_boundaryFix_complete (aircraft : AircraftId) (role : RoleName) (handoff : ResolvedPublishedHandoff) (observation : CompletionObservation) (fix : FixId) (hLoc : handoff.location = ResolvedPublishedHandoffPoint.boundaryFix fix) (hRole : observation.currentRole = some role) (hReached : fix ∈ observation.reachedFixes) : observedResolvedStepCompletion? observation (publishedHandoffContactFrequencyStep aircraft role handoff) = some .complete := by
  have hHandoff : publishedHandoffSatisfied (some handoff) observation = true := by
    exact publishedHandoffSatisfied_of_boundaryFix handoff observation fix hLoc hReached
  simp [publishedHandoffContactFrequencyStep, observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible, hRole, hHandoff]
theorem observedResolvedStepCompletion_contactFrequency_airborne_complete (aircraft : AircraftId) (role : RoleName) (handoff : ResolvedPublishedHandoff) (observation : CompletionObservation) (hLoc : handoff.location = ResolvedPublishedHandoffPoint.airborne) (hRole : observation.currentRole = some role) (hAirborne : observation.onGround = false) : observedResolvedStepCompletion? observation (publishedHandoffContactFrequencyStep aircraft role handoff) = some .complete := by
  have hHandoff : publishedHandoffSatisfied (some handoff) observation = true := by
    exact publishedHandoffSatisfied_of_airborne handoff observation hLoc hAirborne
  simp [publishedHandoffContactFrequencyStep, observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible, hRole, hHandoff]

theorem observedResolvedStepCompletion_contactFrequency_lastRole_holdingPoint_complete (aircraft : AircraftId) (role : RoleName) (handoff : ResolvedPublishedHandoff) (observation : CompletionObservation) (point : PointId) (hLoc : handoff.location = ResolvedPublishedHandoffPoint.holdingPoint point) (hLastRole : observation.lastContactRole = some role) (hReached : groundPointReached point observation = true) : observedResolvedStepCompletion? observation (publishedHandoffContactFrequencyStep aircraft role handoff) = some .complete := by
  have hHandoff : publishedHandoffSatisfied (some handoff) observation = true := by
    exact publishedHandoffSatisfied_of_holdingPoint handoff observation point hLoc hReached
  simp [publishedHandoffContactFrequencyStep, observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible, hLastRole, hHandoff]
def remainOutsideAirspaceStep (aircraft : AircraftId) (airspace : ResolvedAirspaceInstruction) : ResolvedStep :=
  compileResolvedStep
    0
    .route
    (.remainOutsideControlledAirspace aircraft airspace.airspace)
    (.airspace airspace)
    (by simp [resolutionCompatible])
def clearedToEnterControlZoneAirspaceStep (aircraft : AircraftId) (airspace : ResolvedAirspaceInstruction) : ResolvedStep :=
  compileResolvedStep
    0
    .route
    (.clearedToEnterControlZone aircraft airspace.airspace none none)
    (.airspace airspace)
    (by simp [resolutionCompatible])
def specialVfrAirspaceStep (aircraft : AircraftId) (airspace : ResolvedAirspaceInstruction) : ResolvedStep :=
  compileResolvedStep
    0
    .route
    (.specialVfrClearance aircraft airspace.airspace none none)
    (.airspace airspace)
    (by simp [resolutionCompatible])
theorem observedResolvedStepCompletion_remainOutside_activeAirspace_notComplete (aircraft : AircraftId) (airspace : ResolvedAirspaceInstruction) (observation : CompletionObservation) (hActive : airspace.airspace ∈ observation.activeAirspaces) : observedResolvedStepCompletion? observation (remainOutsideAirspaceStep aircraft airspace) = some .notComplete := by
  have hInside : airspaceInside airspace observation = true := by
    exact airspaceInside_of_activeAirspace airspace observation hActive
  simp [remainOutsideAirspaceStep, observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible, hInside]
theorem observedResolvedStepCompletion_remainOutside_entered_notComplete (aircraft : AircraftId) (airspace : ResolvedAirspaceInstruction) (observation : CompletionObservation) (point : PointId) (hTransition : airspace.airspace ∈ observation.airspaceTransitions) (hPos : observation.position = some point) (hPoint : point ∈ airspace.points) : observedResolvedStepCompletion? observation (remainOutsideAirspaceStep aircraft airspace) = some .notComplete := by
  have hInside : airspaceInside airspace observation = true := by
    exact airspaceInside_of_position airspace observation point hPos hPoint
  have hEntered : airspaceEntered airspace observation = true := by
    exact airspaceEntered_of_transition_and_inside airspace observation hTransition hInside
  simp [remainOutsideAirspaceStep, observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible, hEntered]
theorem observedResolvedStepCompletion_clearedToEnterControlZone_exited_complete (aircraft : AircraftId) (airspace : ResolvedAirspaceInstruction) (observation : CompletionObservation) (hTransition : airspace.airspace ∈ observation.airspaceTransitions) (hOutside : airspaceInside airspace observation = false) : observedResolvedStepCompletion? observation (clearedToEnterControlZoneAirspaceStep aircraft airspace) = some .complete := by
  have hExited : airspaceExited airspace observation = true := by
    exact airspaceExited_of_transition_and_not_inside airspace observation hTransition hOutside
  simp [clearedToEnterControlZoneAirspaceStep, observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible, hExited]
theorem observedResolvedStepCompletion_specialVfr_exited_complete (aircraft : AircraftId) (airspace : ResolvedAirspaceInstruction) (observation : CompletionObservation) (hTransition : airspace.airspace ∈ observation.airspaceTransitions) (hOutside : airspaceInside airspace observation = false) : observedResolvedStepCompletion? observation (specialVfrAirspaceStep aircraft airspace) = some .complete := by
  have hExited : airspaceExited airspace observation = true := by
    exact airspaceExited_of_transition_and_not_inside airspace observation hTransition hOutside
  simp [specialVfrAirspaceStep, observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible, hExited]
def directFixStep (aircraft : AircraftId) (fix : FixId) (point : PointId) : ResolvedStep :=
  compileResolvedStep
    0
    .route
    (.proceedDirect aircraft fix)
    (.directFix { fix := fix, point := point })
    (by simp [resolutionCompatible])
theorem observedResolvedStepCompletion_directFix_position_complete (aircraft : AircraftId) (fix : FixId) (point : PointId) (observation : CompletionObservation) (hPos : observation.position = some point) : observedResolvedStepCompletion? observation (directFixStep aircraft fix point) = some .complete := by
  simp [directFixStep, observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible, hPos]
def airwayJoinStep (aircraft : AircraftId) (airway : AirwayId) (joinFix : FixId) (joinPoint : PointId) : ResolvedStep :=
  compileResolvedStep
    0
    .route
    (.joinAirway aircraft airway joinFix)
    (.airwayJoin { airway := airway, joinFix := joinFix, joinPoint := joinPoint })
    (by simp [resolutionCompatible])
theorem observedResolvedStepCompletion_airwayJoin_position_complete (aircraft : AircraftId) (airway : AirwayId) (joinFix : FixId) (joinPoint : PointId) (observation : CompletionObservation) (hPos : observation.position = some joinPoint) : observedResolvedStepCompletion? observation (airwayJoinStep aircraft airway joinFix joinPoint) = some .complete := by
  simp [airwayJoinStep, observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible, hPos]
def circuitJoinStep (aircraft : AircraftId) (direction : CircuitDirection) (joinType : JoinType) (runway : Option RunwayId) (circuit : CircuitProcedureId) (altitude : Level) (entryPoint : PointId) (entryPathPoints : List PointId := []) (circuitPoints : List PointId := []) : ResolvedStep :=
  compileResolvedStep
    0
    .runway
    (.joinCircuit aircraft direction joinType runway)
    (.circuitJoin
      { circuit := circuit
        altitude := altitude
        entryPoint := entryPoint
        entryPathPoints := entryPathPoints
        circuitPoints := circuitPoints })
    (by simp [resolutionCompatible])
theorem observedResolvedStepCompletion_circuitJoin_active_altitude_complete (aircraft : AircraftId) (direction : CircuitDirection) (joinType : JoinType) (runway : Option RunwayId) (circuit : CircuitProcedureId) (altitude : Level) (entryPoint : PointId) (entryPathPoints : List PointId) (circuitPoints : List PointId) (observation : CompletionObservation) (hActive : circuit ∈ observation.activeCircuits) (hAltitude : observation.altitude = some altitude) : observedResolvedStepCompletion? observation (circuitJoinStep aircraft direction joinType runway circuit altitude entryPoint entryPathPoints circuitPoints) = some .complete := by
  simp [circuitJoinStep, observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible, hActive, hAltitude]
def turnByDegreesStep (aircraft : AircraftId) (direction : TurnDirection) (degrees : Nat) : ResolvedStep :=
  compileResolvedStep
    0
    .route
    (.turnByDegrees aircraft direction degrees)
    (.vector
      { kind := .turnByDegrees
        turnDirection := some direction
        turnDegrees := some degrees })
    (by simp [resolutionCompatible])
theorem observedResolvedStepCompletion_turnByDegrees_complete (aircraft : AircraftId) (direction : TurnDirection) (degrees : Nat) (observedDegrees : Nat) (observation : CompletionObservation) (hDir : observation.observedTurnDirection = some direction) (hDegrees : observation.observedTurnDegrees = some observedDegrees) (hAtLeast : observedDegrees >= degrees) : observedResolvedStepCompletion? observation (turnByDegreesStep aircraft direction degrees) = some .complete := by
  simp [turnByDegreesStep, observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible, hDir, hDegrees, hAtLeast]
end Greenfield
end CertifiedAtc