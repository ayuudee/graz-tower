import CertifiedAtc.GreenfieldCompletion

namespace CertifiedAtc
namespace Greenfield

theorem observedInstructionCompletion_maintainAtOrAbove_complete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 4500)) :
    observedInstructionCompletion? (.maintainAtOrAbove "TEST123" (.altitudeFeet 4000))
        observation =
      some .complete := by
  simp [observedInstructionCompletion?, levelAtOrAbove, hAlt, comparableFeet]

theorem observedInstructionCompletion_maintainAtOrAbove_notComplete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 3500)) :
    observedInstructionCompletion? (.maintainAtOrAbove "TEST123" (.altitudeFeet 4000))
        observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, levelAtOrAbove, hAlt, comparableFeet]

theorem observedInstructionCompletion_maintainAtOrBelow_complete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 2500)) :
    observedInstructionCompletion? (.maintainAtOrBelow "TEST123" (.altitudeFeet 3000))
        observation =
      some .complete := by
  simp [observedInstructionCompletion?, levelAtOrBelow, hAlt, comparableFeet]

theorem observedInstructionCompletion_maintainAtOrBelow_notComplete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 3500)) :
    observedInstructionCompletion? (.maintainAtOrBelow "TEST123" (.altitudeFeet 3000))
        observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, levelAtOrBelow, hAlt, comparableFeet]

theorem observedInstructionCompletion_afterPassingLevelClimbTo_complete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 7000)) :
    observedInstructionCompletion?
        (.afterPassingLevelClimbTo "TEST123" (.altitudeFeet 5000) (.altitudeFeet 6000))
        observation =
      some .complete := by
  simp [observedInstructionCompletion?, levelAtOrAbove, hAlt, comparableFeet]

theorem observedInstructionCompletion_afterPassingLevelClimbTo_notComplete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 5500)) :
    observedInstructionCompletion?
        (.afterPassingLevelClimbTo "TEST123" (.altitudeFeet 5000) (.altitudeFeet 6000))
        observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, levelAtOrAbove, hAlt, comparableFeet]

theorem observedInstructionCompletion_afterPassingLevelDescendTo_complete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 2500)) :
    observedInstructionCompletion?
        (.afterPassingLevelDescendTo "TEST123" (.altitudeFeet 5000) (.altitudeFeet 3000))
        observation =
      some .complete := by
  simp [observedInstructionCompletion?, levelAtOrBelow, hAlt, comparableFeet]

theorem observedInstructionCompletion_afterPassingLevelDescendTo_notComplete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 3500)) :
    observedInstructionCompletion?
        (.afterPassingLevelDescendTo "TEST123" (.altitudeFeet 5000) (.altitudeFeet 3000))
        observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, levelAtOrBelow, hAlt, comparableFeet]

theorem observedInstructionCompletion_avoidLevel_complete_differentLevel
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 3500)) :
    observedInstructionCompletion? (.avoidLevel "TEST123" (.altitudeFeet 3000)) observation =
      some .complete := by
  simp [observedInstructionCompletion?, levelMatches, hAlt, comparableFeet]

theorem observedInstructionCompletion_avoidLevel_notComplete_matchingLevel
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 3000)) :
    observedInstructionCompletion? (.avoidLevel "TEST123" (.altitudeFeet 3000)) observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, levelMatches, hAlt, comparableFeet]

theorem observedInstructionCompletion_maintainSpeed_complete_exact
    (observation : CompletionObservation)
    (hSpeed : observation.speed = some (.inKnots 180)) :
    observedInstructionCompletion? (.maintainSpeed "TEST123" (.inKnots 180)) observation =
      some .complete := by
  simp [observedInstructionCompletion?, speedMatches, comparableSpeed?, hSpeed]

theorem observedInstructionCompletion_maintainSpeed_notComplete_exact
    (observation : CompletionObservation)
    (hSpeed : observation.speed = some (.inKnots 160)) :
    observedInstructionCompletion? (.maintainSpeed "TEST123" (.inKnots 180)) observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, speedMatches, comparableSpeed?, hSpeed]

theorem observedInstructionCompletion_reduceSpeedTo_complete_exact
    (observation : CompletionObservation)
    (hSpeed : observation.speed = some (.inKnots 170)) :
    observedInstructionCompletion? (.reduceSpeedTo "TEST123" (.inKnots 180)) observation =
      some .complete := by
  simp [observedInstructionCompletion?, speedAtOrBelow, comparableSpeed?, hSpeed]

theorem observedInstructionCompletion_reduceSpeedTo_notComplete_exact
    (observation : CompletionObservation)
    (hSpeed : observation.speed = some (.inKnots 190)) :
    observedInstructionCompletion? (.reduceSpeedTo "TEST123" (.inKnots 180)) observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, speedAtOrBelow, comparableSpeed?, hSpeed]

theorem observedInstructionCompletion_increaseSpeedTo_complete_exact
    (observation : CompletionObservation)
    (hSpeed : observation.speed = some (.inKnots 210)) :
    observedInstructionCompletion? (.increaseSpeedTo "TEST123" (.inKnots 200)) observation =
      some .complete := by
  simp [observedInstructionCompletion?, speedAtOrAbove, comparableSpeed?, hSpeed]

theorem observedInstructionCompletion_increaseSpeedTo_notComplete_exact
    (observation : CompletionObservation)
    (hSpeed : observation.speed = some (.inKnots 190)) :
    observedInstructionCompletion? (.increaseSpeedTo "TEST123" (.inKnots 200)) observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, speedAtOrAbove, comparableSpeed?, hSpeed]

theorem observedInstructionCompletion_turnByDegrees_complete_threshold
    (observation : CompletionObservation)
    (hDir : observation.observedTurnDirection = some .left)
    (hDegrees : observation.observedTurnDegrees = some 35) :
    observedInstructionCompletion? (.turnByDegrees "TEST123" .left 30) observation =
      some .complete := by
  simp [observedInstructionCompletion?, hDir, hDegrees]

theorem observedInstructionCompletion_turnByDegrees_notComplete_shortTurn
    (observation : CompletionObservation)
    (hDir : observation.observedTurnDirection = some .left)
    (hDegrees : observation.observedTurnDegrees = some 20) :
    observedInstructionCompletion? (.turnByDegrees "TEST123" .left 30) observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, hDir, hDegrees]

end Greenfield
end CertifiedAtc
