import CertifiedAtc.GreenfieldCompletion

namespace CertifiedAtc
namespace Greenfield

theorem observedResolvedStepCompletion_plain_clearedForTakeoff_complete
    (observation : CompletionObservation) (hAirborne : observation.onGround = false) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .runway
          (.clearedForTakeoff "TEST123" "27")
          .plain
          (by simp [resolutionCompatible, instructionNeedsSpecificResolution])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, observedInstructionCompletion?,
    ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible,
    instructionNeedsSpecificResolution, observedInstructionCompletion?, hAirborne]

theorem observedResolvedStepCompletion_plain_clearedForTakeoff_notComplete
    (observation : CompletionObservation) (hGround : observation.onGround = true) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .runway
          (.clearedForTakeoff "TEST123" "27")
          .plain
          (by simp [resolutionCompatible, instructionNeedsSpecificResolution])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, observedInstructionCompletion?,
    ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible,
    instructionNeedsSpecificResolution, observedInstructionCompletion?, hGround]

theorem observedResolvedStepCompletion_plain_maintainLevel_complete
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 3000)) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.maintainLevel "TEST123" (.altitudeFeet 3000))
          .plain
          (by simp [resolutionCompatible, instructionNeedsSpecificResolution])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, observedInstructionCompletion?,
    ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible,
    instructionNeedsSpecificResolution, observedInstructionCompletion?, levelMatches, hAlt,
    comparableFeet]

theorem observedResolvedStepCompletion_plain_maintainLevel_notComplete
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 2800)) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.maintainLevel "TEST123" (.altitudeFeet 3000))
          .plain
          (by simp [resolutionCompatible, instructionNeedsSpecificResolution])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, observedInstructionCompletion?,
    ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible,
    instructionNeedsSpecificResolution, observedInstructionCompletion?, levelMatches, hAlt,
    comparableFeet]

theorem observedResolvedStepCompletion_plain_confirmSquawk_complete
    (observation : CompletionObservation) (hCode : observation.transponderCode = some 4321) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.confirmSquawk "TEST123" 4321)
          .plain
          (by simp [resolutionCompatible, instructionNeedsSpecificResolution])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, observedInstructionCompletion?,
    ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible,
    instructionNeedsSpecificResolution, observedInstructionCompletion?, hCode]

theorem observedResolvedStepCompletion_plain_confirmSquawk_notComplete
    (observation : CompletionObservation) (hCode : observation.transponderCode = some 1200) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.confirmSquawk "TEST123" 4321)
          .plain
          (by simp [resolutionCompatible, instructionNeedsSpecificResolution])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, observedInstructionCompletion?,
    ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible,
    instructionNeedsSpecificResolution, observedInstructionCompletion?, hCode]

theorem observedResolvedStepCompletion_plain_interceptLocaliser_complete
    (observation : CompletionObservation)
    (hEstablished : .localiser ∈ observation.establishedApproachComponents) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.interceptLocaliser "TEST123")
          .plain
          (by simp [resolutionCompatible, instructionNeedsSpecificResolution])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, observedInstructionCompletion?,
    ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible,
    instructionNeedsSpecificResolution, observedInstructionCompletion?, hEstablished]

theorem observedResolvedStepCompletion_plain_interceptLocaliser_notComplete
    (observation : CompletionObservation)
    (hMissing : .localiser ∉ observation.establishedApproachComponents) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.interceptLocaliser "TEST123")
          .plain
          (by simp [resolutionCompatible, instructionNeedsSpecificResolution])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, observedInstructionCompletion?,
    ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible,
    instructionNeedsSpecificResolution, observedInstructionCompletion?, hMissing]

theorem observedResolvedStepCompletion_plain_maintainSpeed_complete
    (observation : CompletionObservation) (hSpeed : observation.speed = some (.inKnots 180)) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.maintainSpeed "TEST123" (.inKnots 180))
          .plain
          (by simp [resolutionCompatible, instructionNeedsSpecificResolution])) =
      some .complete := by
  simp [observedResolvedStepCompletion?, observedInstructionCompletion?,
    ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible,
    instructionNeedsSpecificResolution, observedInstructionCompletion?, speedMatches,
    comparableSpeed?, hSpeed]

theorem observedResolvedStepCompletion_plain_maintainSpeed_notComplete
    (observation : CompletionObservation) (hSpeed : observation.speed = some (.inKnots 160)) :
    observedResolvedStepCompletion? observation
        (compileResolvedStep
          0
          .route
          (.maintainSpeed "TEST123" (.inKnots 180))
          .plain
          (by simp [resolutionCompatible, instructionNeedsSpecificResolution])) =
      some .notComplete := by
  simp [observedResolvedStepCompletion?, observedInstructionCompletion?,
    ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible,
    instructionNeedsSpecificResolution, observedInstructionCompletion?, speedMatches,
    comparableSpeed?, hSpeed]

end Greenfield
end CertifiedAtc
