import CertifiedAtc.GreenfieldCompletion

namespace CertifiedAtc
namespace Greenfield

theorem observedResolvedStepCompletion_route_position_complete
    (observation : CompletionObservation) (hPos : observation.position = some "P-HOLD") :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.clearedTo "TEST123" "HOLD" (some (.viaSid "SID1")))
          (.route
            { clearanceLimitFix := "HOLD"
              clearanceLimitPoint := "P-HOLD"
              routePoints := ["RWY27", "SID-EXIT", "P-HOLD"]
              clearanceLimitHoldingPattern := some "HOLD-PTN" })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hPos]

theorem observedResolvedStepCompletion_route_reachedFix_complete
    (observation : CompletionObservation) (hFix : "HOLD" ∈ observation.reachedFixes) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.clearedTo "TEST123" "HOLD" (some (.viaSid "SID1")))
          (.route
            { clearanceLimitFix := "HOLD"
              clearanceLimitPoint := "P-HOLD"
              routePoints := ["RWY27", "SID-EXIT", "P-HOLD"]
              clearanceLimitHoldingPattern := some "HOLD-PTN" })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hFix]

theorem observedResolvedStepCompletion_route_holdingPattern_complete
    (observation : CompletionObservation)
    (hHolding : "HOLD-PTN" ∈ observation.activeHoldingPatterns) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.clearedTo "TEST123" "HOLD" (some (.viaSid "SID1")))
          (.route
            { clearanceLimitFix := "HOLD"
              clearanceLimitPoint := "P-HOLD"
              routePoints := ["RWY27", "SID-EXIT", "P-HOLD"]
              clearanceLimitHoldingPattern := some "HOLD-PTN" })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hHolding]

theorem observedResolvedStepCompletion_route_notComplete_emptyObservation
    (observation : CompletionObservation) (hPos : observation.position = none)
    (hFix : "HOLD" ∉ observation.reachedFixes)
    (hHolding : "HOLD-PTN" ∉ observation.activeHoldingPatterns) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.clearedTo "TEST123" "HOLD" (some (.viaSid "SID1")))
          (.route
            { clearanceLimitFix := "HOLD"
              clearanceLimitPoint := "P-HOLD"
              routePoints := ["RWY27", "SID-EXIT", "P-HOLD"]
              clearanceLimitHoldingPattern := some "HOLD-PTN" })
          (by simp [resolutionCompatible])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hPos, hFix, hHolding]

theorem observedResolvedStepCompletion_holding_notApplicable
    (observation : CompletionObservation) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.holdAt "TEST123" (.published "HOLD") none)
          (.holding
            { holdingPattern := "HOLD-PTN"
              fix := "HOLD"
              fixPoint := "P-HOLD"
              loopPoints := ["P-HOLD", "LOOP-1"] })
          (by simp [resolutionCompatible])) =
      some .notApplicable := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible]

theorem observedResolvedStepCompletion_approach_runwayTransition_complete
    (observation : CompletionObservation) (hGround : observation.onGround = true)
    (hTransition : "27" ∈ observation.runwayTransitions) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.clearedApproach "TEST123" .ils "27" none)
          (.approach
            { approach := "ILS27"
              runway := "27"
              waypointPoints := ["FAF"]
              thresholdPoint := "THR-27"
              missedApproachPoints := ["MAP"]
              missedApproachHoldingPattern := "MISSED-27" })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hGround, hTransition]

theorem observedResolvedStepCompletion_approach_missedHolding_complete
    (observation : CompletionObservation)
    (hHolding : "MISSED-27" ∈ observation.activeHoldingPatterns) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.clearedApproach "TEST123" .ils "27" none)
          (.approach
            { approach := "ILS27"
              runway := "27"
              waypointPoints := ["FAF"]
              thresholdPoint := "THR-27"
              missedApproachPoints := ["MAP"]
              missedApproachHoldingPattern := "MISSED-27" })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hHolding]

theorem observedResolvedStepCompletion_approach_notComplete_missingTriggers
    (observation : CompletionObservation) (hAirborne : observation.onGround = false)
    (hHolding : "MISSED-27" ∉ observation.activeHoldingPatterns) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.clearedApproach "TEST123" .ils "27" none)
          (.approach
            { approach := "ILS27"
              runway := "27"
              waypointPoints := ["FAF"]
              thresholdPoint := "THR-27"
              missedApproachPoints := ["MAP"]
              missedApproachHoldingPattern := "MISSED-27" })
          (by simp [resolutionCompatible])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hAirborne, hHolding]

theorem observedResolvedStepCompletion_directFix_complete
    (observation : CompletionObservation) (hPos : observation.position = some "FIX-PT") :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.proceedDirect "TEST123" "FIX-A")
          (.directFix { fix := "FIX-A", point := "FIX-PT" })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hPos]

theorem observedResolvedStepCompletion_airwayJoin_complete
    (observation : CompletionObservation) (hPos : observation.position = some "JOIN-PT") :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.joinAirway "TEST123" "A1" "FIX-A")
          (.airwayJoin { airway := "A1", joinFix := "FIX-A", joinPoint := "JOIN-PT" })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hPos]

theorem observedResolvedStepCompletion_circuitJoin_complete
    (observation : CompletionObservation)
    (hCircuit : "CIRCUIT-27-LH" ∈ observation.activeCircuits)
    (hAlt : observation.altitude = some (.altitudeFeet 1200)) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .runway
          (.joinCircuit "TEST123" .leftHand .downwind (some "27"))
          (.circuitJoin
            { circuit := "CIRCUIT-27-LH"
              altitude := .altitudeFeet 1200
              entryPoint := "CROSSWIND"
              entryPathPoints := ["JOIN-ENTRY", "CROSSWIND"]
              circuitPoints := ["RWY27", "UPWIND", "CROSSWIND", "DOWNWIND"] })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hCircuit, hAlt]

theorem observedResolvedStepCompletion_circuitJoin_notComplete_missingAltitude
    (observation : CompletionObservation)
    (hCircuit : "CIRCUIT-27-LH" ∈ observation.activeCircuits)
    (hAlt : observation.altitude = some (.altitudeFeet 1000)) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .runway
          (.joinCircuit "TEST123" .leftHand .downwind (some "27"))
          (.circuitJoin
            { circuit := "CIRCUIT-27-LH"
              altitude := .altitudeFeet 1200
              entryPoint := "CROSSWIND"
              entryPathPoints := ["JOIN-ENTRY", "CROSSWIND"]
              circuitPoints := ["RWY27", "UPWIND", "CROSSWIND", "DOWNWIND"] })
          (by simp [resolutionCompatible])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hCircuit, hAlt]

theorem observedResolvedStepCompletion_continueApproach_notComplete
    (observation : CompletionObservation) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.continueApproach "TEST123")
          (.continueApproach { approach := "ILS27", waypointPoints := ["FAF"], thresholdPoint := "THR-27" })
          (by simp [resolutionCompatible])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible]

theorem observedResolvedStepCompletion_extendDownwind_notApplicable
    (observation : CompletionObservation) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .runway
          (.extendDownwind "TEST123")
          (.extendDownwind
            { circuit := "CIRCUIT-27-LH"
              extendedPathPoints := ["DOWNWIND-1", "DOWNWIND-2"]
              offRampPoints := [] })
          (by simp [resolutionCompatible])) =
      some .notApplicable := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible]

theorem observedResolvedStepCompletion_orbit_notApplicable
    (observation : CompletionObservation) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .runway
          (.orbit "TEST123" .left)
          (.orbit
            { circuit := "CIRCUIT-27-LH"
              orbitPoint := "DOWNWIND-1"
              direction := .left
              loopPoints := ["DOWNWIND-1", "DOWNWIND-2"] })
          (by simp [resolutionCompatible])) =
      some .notApplicable := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible]

theorem observedResolvedStepCompletion_vector_turnByDegrees_complete
    (observation : CompletionObservation)
    (hDir : observation.observedTurnDirection = some .left)
    (hDegrees : observation.observedTurnDegrees = some 30) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.turnByDegrees "TEST123" .left 30)
          (.vector { kind := .turnByDegrees, turnDirection := some .left, turnDegrees := some 30 })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hDir, hDegrees]

theorem observedResolvedStepCompletion_vector_turnByDegrees_notComplete_shortTurn
    (observation : CompletionObservation)
    (hDir : observation.observedTurnDirection = some .left)
    (hDegrees : observation.observedTurnDegrees = some 20) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.turnByDegrees "TEST123" .left 30)
          (.vector { kind := .turnByDegrees, turnDirection := some .left, turnDegrees := some 30 })
          (by simp [resolutionCompatible])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hDir, hDegrees]

theorem observedResolvedStepCompletion_vector_flyHeading_notApplicable
    (observation : CompletionObservation) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.flyHeading "TEST123" 270)
          (.vector { kind := .flyHeading, targetHeadingDegreesMagnetic := some 270 })
          (by simp [resolutionCompatible])) =
      some .notApplicable := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible]

theorem observedResolvedStepCompletion_vector_turnHeading_notApplicable
    (observation : CompletionObservation) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.turnHeading "TEST123" .left 270)
          (.vector
            { kind := .turnHeading
              targetHeadingDegreesMagnetic := some 270
              turnDirection := some .left })
          (by simp [resolutionCompatible])) =
      some .notApplicable := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible]

theorem observedResolvedStepCompletion_vector_continuePresentHeading_notApplicable
    (observation : CompletionObservation) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.continuePresentHeading "TEST123")
          (.vector { kind := .continuePresentHeading, capturedHeadingDegreesMagnetic := some 270 })
          (by simp [resolutionCompatible])) =
      some .notApplicable := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible]

end Greenfield
end CertifiedAtc
