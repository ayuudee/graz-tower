import CertifiedAtc.GreenfieldCompletion

namespace CertifiedAtc
namespace Greenfield

theorem observedInstructionCompletion_confirmSquawk_complete_match
    (observation : CompletionObservation) (hCode : observation.transponderCode = some 4321) :
    observedInstructionCompletion? (.confirmSquawk "TEST123" 4321) observation =
      some .complete := by
  simp [observedInstructionCompletion?, hCode]

theorem observedInstructionCompletion_confirmSquawk_notComplete_mismatch
    (observation : CompletionObservation) (hCode : observation.transponderCode = some 1200) :
    observedInstructionCompletion? (.confirmSquawk "TEST123" 4321) observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, hCode]

theorem observedInstructionCompletion_squawkIdent_complete_active
    (observation : CompletionObservation) (hIdent : observation.transponderIdentActive = true) :
    observedInstructionCompletion? (.squawkIdent "TEST123") observation =
      some .complete := by
  simp [observedInstructionCompletion?, hIdent]

theorem observedInstructionCompletion_squawkIdent_notComplete_inactive
    (observation : CompletionObservation)
    (hIdent : observation.transponderIdentActive = false) :
    observedInstructionCompletion? (.squawkIdent "TEST123") observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, hIdent]

theorem observedInstructionCompletion_squawkStandby_complete_match
    (observation : CompletionObservation) (hMode : observation.transponderMode = some .standby) :
    observedInstructionCompletion? (.squawkStandby "TEST123") observation =
      some .complete := by
  simp [observedInstructionCompletion?, hMode]

theorem observedInstructionCompletion_squawkStandby_notComplete_mismatch
    (observation : CompletionObservation) (hMode : observation.transponderMode = some .charlie) :
    observedInstructionCompletion? (.squawkStandby "TEST123") observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, hMode]

theorem observedInstructionCompletion_squawkNormal_complete_match
    (observation : CompletionObservation) (hMode : observation.transponderMode = some .charlie) :
    observedInstructionCompletion? (.squawkNormal "TEST123" .charlie) observation =
      some .complete := by
  simp [observedInstructionCompletion?, hMode]

theorem observedInstructionCompletion_squawkNormal_notComplete_mismatch
    (observation : CompletionObservation) (hMode : observation.transponderMode = some .standby) :
    observedInstructionCompletion? (.squawkNormal "TEST123" .charlie) observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, hMode]

theorem observedInstructionCompletion_stopSquawk_complete_otherMode
    (observation : CompletionObservation) (hMode : observation.transponderMode = some .standby) :
    observedInstructionCompletion? (.stopSquawk "TEST123" .charlie) observation =
      some .complete := by
  simp [observedInstructionCompletion?, hMode]

theorem observedInstructionCompletion_stopSquawk_notComplete_sameMode
    (observation : CompletionObservation) (hMode : observation.transponderMode = some .charlie) :
    observedInstructionCompletion? (.stopSquawk "TEST123" .charlie) observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, hMode]

theorem observedInstructionCompletion_turnByDegrees_complete_rightTurn
    (observation : CompletionObservation)
    (hDir : observation.observedTurnDirection = some .right)
    (hDegrees : observation.observedTurnDegrees = some 20) :
    observedInstructionCompletion? (.turnByDegrees "TEST123" .right 20) observation =
      some .complete := by
  simp [observedInstructionCompletion?, hDir, hDegrees]

theorem observedInstructionCompletion_turnByDegrees_notComplete_wrongDirection
    (observation : CompletionObservation)
    (hDir : observation.observedTurnDirection = some .right)
    (hDegrees : observation.observedTurnDegrees = some 35) :
    observedInstructionCompletion? (.turnByDegrees "TEST123" .left 30) observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, hDir, hDegrees]

end Greenfield
end CertifiedAtc
