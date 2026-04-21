import CertifiedAtc.GreenfieldCompletion

namespace CertifiedAtc
namespace Greenfield

theorem observedResolvedStepCompletion_frequencyChange_currentRole_complete
    (observation : CompletionObservation) (hRole : observation.currentRole = some .approach) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.contactFrequency "TEST123" .approach none)
          (.frequencyChange
            { roleName := .approach, instructedFrequency := none, publishedHandoff := none })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, publishedHandoffSatisfied, hRole]

theorem observedResolvedStepCompletion_frequencyChange_lastRole_complete
    (observation : CompletionObservation)
    (hRole : observation.lastContactRole = some .approach) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.contactFrequency "TEST123" .approach none)
          (.frequencyChange
            { roleName := .approach, instructedFrequency := none, publishedHandoff := none })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, publishedHandoffSatisfied, hRole]

theorem observedResolvedStepCompletion_frequencyChange_instructedFrequency_complete
    (freq : Frequency) (observation : CompletionObservation)
    (hFreq : observation.currentFrequency = some freq) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.contactFrequency "TEST123" .approach (some freq))
          (.frequencyChange
            { roleName := .approach
              instructedFrequency := some freq
              publishedHandoff := none })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, publishedHandoffSatisfied, hFreq]

theorem observedResolvedStepCompletion_frequencyChange_noMatch_notComplete
    (freq : Frequency) (observation : CompletionObservation)
    (hCurrent : observation.currentRole ≠ some .approach)
    (hLast : observation.lastContactRole ≠ some .approach)
    (hFreq : observation.currentFrequency ≠ some freq) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.contactFrequency "TEST123" .approach (some freq))
          (.frequencyChange
            { roleName := .approach
              instructedFrequency := some freq
              publishedHandoff := none })
          (by simp [resolutionCompatible])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, publishedHandoffSatisfied, hCurrent, hLast, hFreq]

theorem observedResolvedStepCompletion_remainOutside_notApplicable_outside
    (observation : CompletionObservation) (hPos : observation.position = some "P-OUTSIDE")
    (hActive : "CTR-1" ∉ observation.activeAirspaces)
    (hTransition : "CTR-1" ∉ observation.airspaceTransitions) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.remainOutsideControlledAirspace "TEST123" "CTR-1")
          (.airspace
            { airspace := "CTR-1"
              points := ["P-IN-CTR"]
              routePoints := []
              entryTransitions := []
              exitTransitions := [] })
          (by simp [resolutionCompatible])) =
      some .notApplicable := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, airspaceInside, airspaceEntered, hPos, hActive, hTransition]

theorem observedResolvedStepCompletion_remainOutside_notComplete_activeAirspace
    (observation : CompletionObservation) (hActive : "CTR-1" ∈ observation.activeAirspaces) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.remainOutsideControlledAirspace "TEST123" "CTR-1")
          (.airspace
            { airspace := "CTR-1"
              points := ["P-IN-CTR"]
              routePoints := []
              entryTransitions := []
              exitTransitions := [] })
          (by simp [resolutionCompatible])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, airspaceInside, airspaceEntered, hActive]

theorem observedResolvedStepCompletion_remainOutside_notComplete_positionInside
    (observation : CompletionObservation) (hPos : observation.position = some "P-IN-CTR") :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.remainOutsideControlledAirspace "TEST123" "CTR-1")
          (.airspace
            { airspace := "CTR-1"
              points := ["P-IN-CTR"]
              routePoints := []
              entryTransitions := []
              exitTransitions := [] })
          (by simp [resolutionCompatible])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, airspaceInside, airspaceEntered, hPos]

theorem observedResolvedStepCompletion_clearedToEnterControlZone_complete_exited
    (observation : CompletionObservation) (hTransition : "CTR-1" ∈ observation.airspaceTransitions)
    (hPos : observation.position = some "P-OUTSIDE")
    (hActive : "CTR-1" ∉ observation.activeAirspaces) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.clearedToEnterControlZone "TEST123" "CTR-1" none none)
          (.airspace
            { airspace := "CTR-1"
              points := ["P-IN-CTR"]
              routePoints := []
              entryTransitions := []
              exitTransitions := [] })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, airspaceInside, airspaceExited, hTransition, hPos, hActive]

theorem observedResolvedStepCompletion_clearedToEnterControlZone_complete_onGround
    (observation : CompletionObservation) (hGround : observation.onGround = true) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.clearedToEnterControlZone "TEST123" "CTR-1" none none)
          (.airspace
            { airspace := "CTR-1"
              points := ["P-IN-CTR"]
              routePoints := []
              entryTransitions := []
              exitTransitions := [] })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, airspaceInside, airspaceExited, hGround]

theorem observedResolvedStepCompletion_clearedToEnterControlZone_notApplicable
    (observation : CompletionObservation) (hGround : observation.onGround = false)
    (hTransition : "CTR-1" ∉ observation.airspaceTransitions)
    (hPos : observation.position = some "P-IN-CTR") :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.clearedToEnterControlZone "TEST123" "CTR-1" none none)
          (.airspace
            { airspace := "CTR-1"
              points := ["P-IN-CTR"]
              routePoints := []
              entryTransitions := []
              exitTransitions := [] })
          (by simp [resolutionCompatible])) =
      some .notApplicable := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, airspaceInside, airspaceExited, hGround, hTransition, hPos]

theorem observedResolvedStepCompletion_specialVfr_complete_exited
    (observation : CompletionObservation) (hTransition : "CTR-1" ∈ observation.airspaceTransitions)
    (hPos : observation.position = some "P-OUTSIDE")
    (hActive : "CTR-1" ∉ observation.activeAirspaces) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.specialVfrClearance "TEST123" "CTR-1" none none)
          (.airspace
            { airspace := "CTR-1"
              points := ["P-IN-CTR"]
              routePoints := []
              entryTransitions := []
              exitTransitions := [] })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, airspaceInside, airspaceExited, hTransition, hPos, hActive]

theorem observedResolvedStepCompletion_specialVfr_complete_onGround
    (observation : CompletionObservation) (hGround : observation.onGround = true) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.specialVfrClearance "TEST123" "CTR-1" none none)
          (.airspace
            { airspace := "CTR-1"
              points := ["P-IN-CTR"]
              routePoints := []
              entryTransitions := []
              exitTransitions := [] })
          (by simp [resolutionCompatible])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, airspaceInside, airspaceExited, hGround]

theorem observedResolvedStepCompletion_specialVfr_notApplicable
    (observation : CompletionObservation) (hGround : observation.onGround = false)
    (hTransition : "CTR-1" ∉ observation.airspaceTransitions)
    (hPos : observation.position = some "P-IN-CTR") :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.specialVfrClearance "TEST123" "CTR-1" none none)
          (.airspace
            { airspace := "CTR-1"
              points := ["P-IN-CTR"]
              routePoints := []
              entryTransitions := []
              exitTransitions := [] })
          (by simp [resolutionCompatible])) =
      some .notApplicable := by
  simp [observedResolvedStepCompletion?, ResolvedStep.isCompatible, compileResolvedStep,
    resolutionCompatible, airspaceInside, airspaceExited, hGround, hTransition, hPos]

end Greenfield
end CertifiedAtc
