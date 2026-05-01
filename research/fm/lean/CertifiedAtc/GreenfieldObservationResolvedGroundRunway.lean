import CertifiedAtc.GreenfieldCompletion

namespace CertifiedAtc
namespace Greenfield

theorem observedResolvedStepCompletion_taxi_position_complete
    (observation : CompletionObservation) (hPos : observation.position = some "GATE-A") :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .ground
          (.taxiTo "TEST123" "GATE-A" ["TWY-A"])
          (.taxi { destination := "GATE-A", path := ["TWY-A", "GATE-A"] })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, groundPointReached, hPos]

theorem observedResolvedStepCompletion_taxi_traversed_complete
    (observation : CompletionObservation)
    (hTraversed : "GATE-A" ∈ observation.traversedGroundPoints) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .ground
          (.taxiTo "TEST123" "GATE-A" ["TWY-A"])
          (.taxi { destination := "GATE-A", path := ["TWY-A", "GATE-A"] })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, groundPointReached, hTraversed]

theorem observedResolvedStepCompletion_taxi_reachedHoldingPoint_complete
    (observation : CompletionObservation)
    (hReached : "GATE-A" ∈ observation.reachedHoldingPoints) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .ground
          (.taxiTo "TEST123" "GATE-A" ["TWY-A"])
          (.taxi { destination := "GATE-A", path := ["TWY-A", "GATE-A"] })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, groundPointReached, hReached]

theorem observedResolvedStepCompletion_holdShort_notApplicable
    (observation : CompletionObservation) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .ground
          (.holdShortOf "TEST123" "27")
          (.holdShort { runway := "27", point := "HS-27" })
          (by simp [resolutionCompatible])) =
      some .notApplicable := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible]

theorem observedResolvedStepCompletion_crossing_crossedRunway_complete
    (observation : CompletionObservation) (hCrossed : "27" ∈ observation.crossedRunways) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .ground
          (.crossRunway "TEST123" "27")
          (.crossing { runway := "27", crossingPoint := "X-27" })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, groundPointReached, hCrossed]

theorem observedResolvedStepCompletion_crossing_crossingPoint_complete
    (observation : CompletionObservation) (hPos : observation.position = some "X-27") :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .ground
          (.crossRunway "TEST123" "27")
          (.crossing { runway := "27", crossingPoint := "X-27" })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, groundPointReached, hPos]

theorem observedResolvedStepCompletion_crossing_transition_complete
    (observation : CompletionObservation)
    (hTransition : "27" ∈ observation.runwayTransitions)
    (hInactive : "27" ∉ observation.activeRunways) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .ground
          (.crossRunway "TEST123" "27")
          (.crossing { runway := "27", crossingPoint := "X-27" })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, groundPointReached, runwayTransitionComplete, hTransition, hInactive]

theorem observedResolvedStepCompletion_backtrack_farEnd_complete
    (observation : CompletionObservation) (hPos : observation.position = some "RWY27-FAR") :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .ground
          (.backtrackRunway "TEST123" "27")
          (.backtrack { runway := "27", farEndPoint := "RWY27-FAR" })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, groundPointReached, hPos]

theorem observedResolvedStepCompletion_lineUpAndWait_notApplicable
    (observation : CompletionObservation) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .runway
          (.lineUpAndWait "TEST123" "27")
          (.runwayOperation { runway := "27", thresholdPoint := "THR-27", pathPoints := ["THR-27"] })
          (by simp [resolutionCompatible])) =
      some .notApplicable := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible]

theorem observedResolvedStepCompletion_takeoff_complete_transition
    (observation : CompletionObservation) (hAirborne : observation.onGround = false)
    (hTransition : "27" ∈ observation.runwayTransitions) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .runway
          (.clearedForTakeoff "TEST123" "27")
          (.runwayOperation { runway := "27", thresholdPoint := "THR-27", pathPoints := ["THR-27"] })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hAirborne, hTransition]

theorem observedResolvedStepCompletion_takeoff_notComplete_grounded
    (observation : CompletionObservation) (hGround : observation.onGround = true) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .runway
          (.clearedForTakeoff "TEST123" "27")
          (.runwayOperation { runway := "27", thresholdPoint := "THR-27", pathPoints := ["THR-27"] })
          (by simp [resolutionCompatible])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hGround]

theorem observedResolvedStepCompletion_takeoff_notComplete_missingTransition
    (observation : CompletionObservation) (hAirborne : observation.onGround = false)
    (hMissing : "27" ∉ observation.runwayTransitions) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .runway
          (.clearedForTakeoff "TEST123" "27")
          (.runwayOperation { runway := "27", thresholdPoint := "THR-27", pathPoints := ["THR-27"] })
          (by simp [resolutionCompatible])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hAirborne, hMissing]

theorem observedResolvedStepCompletion_landing_complete_transition
    (observation : CompletionObservation)
    (hTransition : "27" ∈ observation.runwayTransitions)
    (hInactive : "27" ∉ observation.activeRunways) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .runway
          (.clearedToLand "TEST123" "27")
          (.runwayOperation { runway := "27", thresholdPoint := "THR-27", pathPoints := ["THR-27"] })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, runwayTransitionComplete, hTransition, hInactive]

theorem observedResolvedStepCompletion_landing_notComplete_activeRunway
    (observation : CompletionObservation)
    (hTransition : "27" ∈ observation.runwayTransitions)
    (hActive : "27" ∈ observation.activeRunways) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .runway
          (.clearedToLand "TEST123" "27")
          (.runwayOperation { runway := "27", thresholdPoint := "THR-27", pathPoints := ["THR-27"] })
          (by simp [resolutionCompatible])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, runwayTransitionComplete, hTransition, hActive]

theorem observedResolvedStepCompletion_landing_notComplete_missingTransition
    (observation : CompletionObservation) (hMissing : "27" ∉ observation.runwayTransitions) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .runway
          (.clearedToLand "TEST123" "27")
          (.runwayOperation { runway := "27", thresholdPoint := "THR-27", pathPoints := ["THR-27"] })
          (by simp [resolutionCompatible])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, runwayTransitionComplete, hMissing]

theorem observedResolvedStepCompletion_touchAndGo_complete_transition
    (observation : CompletionObservation) (hAirborne : observation.onGround = false)
    (hTransition : "27" ∈ observation.runwayTransitions) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .runway
          (.clearedTouchAndGo "TEST123" "27")
          (.runwayOperation { runway := "27", thresholdPoint := "THR-27", pathPoints := ["THR-27"] })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hAirborne, hTransition]

theorem observedResolvedStepCompletion_touchAndGo_notComplete_grounded
    (observation : CompletionObservation) (hGround : observation.onGround = true) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .runway
          (.clearedTouchAndGo "TEST123" "27")
          (.runwayOperation { runway := "27", thresholdPoint := "THR-27", pathPoints := ["THR-27"] })
          (by simp [resolutionCompatible])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hGround]

theorem observedResolvedStepCompletion_touchAndGo_notComplete_missingTransition
    (observation : CompletionObservation) (hAirborne : observation.onGround = false)
    (hMissing : "27" ∉ observation.runwayTransitions) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .runway
          (.clearedTouchAndGo "TEST123" "27")
          (.runwayOperation { runway := "27", thresholdPoint := "THR-27", pathPoints := ["THR-27"] })
          (by simp [resolutionCompatible])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hAirborne, hMissing]

theorem observedResolvedStepCompletion_lowApproach_complete_transition
    (observation : CompletionObservation) (hAirborne : observation.onGround = false)
    (hTransition : "27" ∈ observation.runwayTransitions)
    (hInactive : "27" ∉ observation.activeRunways) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .runway
          (.clearedLowApproach "TEST123" "27")
          (.runwayOperation { runway := "27", thresholdPoint := "THR-27", pathPoints := ["THR-27"] })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hAirborne, hTransition, hInactive]

theorem observedResolvedStepCompletion_lowApproach_notComplete_grounded
    (observation : CompletionObservation) (hGround : observation.onGround = true) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .runway
          (.clearedLowApproach "TEST123" "27")
          (.runwayOperation { runway := "27", thresholdPoint := "THR-27", pathPoints := ["THR-27"] })
          (by simp [resolutionCompatible])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hGround]

theorem observedResolvedStepCompletion_lowApproach_notComplete_activeRunway
    (observation : CompletionObservation) (hAirborne : observation.onGround = false)
    (hTransition : "27" ∈ observation.runwayTransitions)
    (hActive : "27" ∈ observation.activeRunways) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .runway
          (.clearedLowApproach "TEST123" "27")
          (.runwayOperation { runway := "27", thresholdPoint := "THR-27", pathPoints := ["THR-27"] })
          (by simp [resolutionCompatible])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hAirborne, hTransition, hActive]

theorem observedResolvedStepCompletion_lowApproach_notComplete_missingTransition
    (observation : CompletionObservation) (hAirborne : observation.onGround = false)
    (hMissing : "27" ∉ observation.runwayTransitions) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .runway
          (.clearedLowApproach "TEST123" "27")
          (.runwayOperation { runway := "27", thresholdPoint := "THR-27", pathPoints := ["THR-27"] })
          (by simp [resolutionCompatible])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, hAirborne, hMissing]

theorem observedResolvedStepCompletion_goAround_notComplete
    (observation : CompletionObservation) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .runway
          (.goAround "TEST123")
          (.runwayOperation { runway := "27", thresholdPoint := "THR-27", pathPoints := ["THR-27"] })
          (by simp [resolutionCompatible])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible]

end Greenfield
end CertifiedAtc
