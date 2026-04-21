import CertifiedAtc.GreenfieldCompletion

namespace CertifiedAtc
namespace Greenfield

theorem observedInstructionCompletion_clearedForTakeoff_complete_airborne
    (observation : CompletionObservation) (hAirborne : observation.onGround = false) :
    observedInstructionCompletion? (.clearedForTakeoff "TEST123" "27") observation =
      some .complete := by
  simp [observedInstructionCompletion?, hAirborne]

theorem observedInstructionCompletion_clearedForTakeoff_notComplete_grounded
    (observation : CompletionObservation) (hGround : observation.onGround = true) :
    observedInstructionCompletion? (.clearedForTakeoff "TEST123" "27") observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, hGround]

theorem observedInstructionCompletion_clearedToLand_complete_transition
    (observation : CompletionObservation) (hTransition : "27" ∈ observation.runwayTransitions)
    (hInactive : "27" ∉ observation.activeRunways) :
    observedInstructionCompletion? (.clearedToLand "TEST123" "27") observation =
      some .complete := by
  simp [observedInstructionCompletion?, runwayTransitionComplete, hTransition, hInactive]

theorem observedInstructionCompletion_clearedToLand_notComplete_activeRunway
    (observation : CompletionObservation) (hTransition : "27" ∈ observation.runwayTransitions)
    (hActive : "27" ∈ observation.activeRunways) :
    observedInstructionCompletion? (.clearedToLand "TEST123" "27") observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, runwayTransitionComplete, hTransition, hActive]

theorem observedInstructionCompletion_clearedToLand_notComplete_missingTransition
    (observation : CompletionObservation) (hMissing : "27" ∉ observation.runwayTransitions) :
    observedInstructionCompletion? (.clearedToLand "TEST123" "27") observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, runwayTransitionComplete, hMissing]

theorem observedInstructionCompletion_clearedTouchAndGo_complete_transition
    (observation : CompletionObservation) (hAirborne : observation.onGround = false)
    (hTransition : "27" ∈ observation.runwayTransitions) :
    observedInstructionCompletion? (.clearedTouchAndGo "TEST123" "27") observation =
      some .complete := by
  simp [observedInstructionCompletion?, hAirborne, hTransition]

theorem observedInstructionCompletion_clearedTouchAndGo_notComplete_grounded
    (observation : CompletionObservation) (hGround : observation.onGround = true) :
    observedInstructionCompletion? (.clearedTouchAndGo "TEST123" "27") observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, hGround]

theorem observedInstructionCompletion_clearedTouchAndGo_notComplete_missingTransition
    (observation : CompletionObservation) (hAirborne : observation.onGround = false)
    (hMissing : "27" ∉ observation.runwayTransitions) :
    observedInstructionCompletion? (.clearedTouchAndGo "TEST123" "27") observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, hAirborne, hMissing]

theorem observedInstructionCompletion_clearedLowApproach_complete_transition
    (observation : CompletionObservation) (hAirborne : observation.onGround = false)
    (hTransition : "27" ∈ observation.runwayTransitions)
    (hInactive : "27" ∉ observation.activeRunways) :
    observedInstructionCompletion? (.clearedLowApproach "TEST123" "27") observation =
      some .complete := by
  simp [observedInstructionCompletion?, hAirborne, hTransition, hInactive]

theorem observedInstructionCompletion_clearedLowApproach_notComplete_grounded
    (observation : CompletionObservation) (hGround : observation.onGround = true) :
    observedInstructionCompletion? (.clearedLowApproach "TEST123" "27") observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, hGround]

theorem observedInstructionCompletion_clearedLowApproach_notComplete_activeRunway
    (observation : CompletionObservation) (hAirborne : observation.onGround = false)
    (hTransition : "27" ∈ observation.runwayTransitions)
    (hActive : "27" ∈ observation.activeRunways) :
    observedInstructionCompletion? (.clearedLowApproach "TEST123" "27") observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, hAirborne, hTransition, hActive]

theorem observedInstructionCompletion_clearedLowApproach_notComplete_missingTransition
    (observation : CompletionObservation) (hAirborne : observation.onGround = false)
    (hMissing : "27" ∉ observation.runwayTransitions) :
    observedInstructionCompletion? (.clearedLowApproach "TEST123" "27") observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, hAirborne, hMissing]

theorem observedInstructionCompletion_afterLandingVacateVia_complete_position
    (observation : CompletionObservation) (hPos : observation.position = some "EXIT-A") :
    observedInstructionCompletion? (.afterLandingVacateVia "TEST123" "EXIT-A") observation =
      some .complete := by
  simp [observedInstructionCompletion?, hPos]

theorem observedInstructionCompletion_contactFrequency_currentRole_complete
    (observation : CompletionObservation) (hRole : observation.currentRole = some .approach) :
    observedInstructionCompletion? (.contactFrequency "TEST123" .approach none) observation =
      some .complete := by
  simp [observedInstructionCompletion?, hRole]

theorem observedInstructionCompletion_contactFrequency_lastRole_complete
    (observation : CompletionObservation)
    (hRole : observation.lastContactRole = some .approach) :
    observedInstructionCompletion? (.contactFrequency "TEST123" .approach none) observation =
      some .complete := by
  simp [observedInstructionCompletion?, hRole]

theorem observedInstructionCompletion_contactFrequency_frequency_complete
    (freq : Frequency) (observation : CompletionObservation)
    (hFreq : observation.currentFrequency = some freq) :
    observedInstructionCompletion? (.contactFrequency "TEST123" .approach (some freq))
        observation =
      some .complete := by
  simp [observedInstructionCompletion?, hFreq]

theorem observedInstructionCompletion_contactFrequency_noMatch_notComplete
    (freq : Frequency) (observation : CompletionObservation)
    (hCurrent : observation.currentRole ≠ some .approach)
    (hLast : observation.lastContactRole ≠ some .approach)
    (hFreq : observation.currentFrequency ≠ some freq) :
    observedInstructionCompletion? (.contactFrequency "TEST123" .approach (some freq))
        observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, hCurrent, hLast, hFreq]

theorem observedInstructionCompletion_monitorFrequency_currentRole_complete
    (observation : CompletionObservation) (hRole : observation.currentRole = some .tower) :
    observedInstructionCompletion? (.monitorFrequency "TEST123" .tower none) observation =
      some .complete := by
  simp [observedInstructionCompletion?, hRole]

theorem observedInstructionCompletion_monitorFrequency_lastRole_complete
    (observation : CompletionObservation) (hRole : observation.lastContactRole = some .tower) :
    observedInstructionCompletion? (.monitorFrequency "TEST123" .tower none) observation =
      some .complete := by
  simp [observedInstructionCompletion?, hRole]

theorem observedInstructionCompletion_monitorFrequency_frequency_complete
    (freq : Frequency) (observation : CompletionObservation)
    (hFreq : observation.currentFrequency = some freq) :
    observedInstructionCompletion? (.monitorFrequency "TEST123" .tower (some freq))
        observation =
      some .complete := by
  simp [observedInstructionCompletion?, hFreq]

theorem observedInstructionCompletion_monitorFrequency_noMatch_notComplete
    (freq : Frequency) (observation : CompletionObservation)
    (hCurrent : observation.currentRole ≠ some .tower)
    (hLast : observation.lastContactRole ≠ some .tower)
    (hFreq : observation.currentFrequency ≠ some freq) :
    observedInstructionCompletion? (.monitorFrequency "TEST123" .tower (some freq))
        observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, hCurrent, hLast, hFreq]

theorem observedInstructionCompletion_interceptLocaliser_complete_established
    (observation : CompletionObservation)
    (hEstablished : .localiser ∈ observation.establishedApproachComponents) :
    observedInstructionCompletion? (.interceptLocaliser "TEST123") observation =
      some .complete := by
  simp [observedInstructionCompletion?, hEstablished]

theorem observedInstructionCompletion_interceptLocaliser_notComplete_missing
    (observation : CompletionObservation)
    (hMissing : .localiser ∉ observation.establishedApproachComponents) :
    observedInstructionCompletion? (.interceptLocaliser "TEST123") observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, hMissing]

end Greenfield
end CertifiedAtc
