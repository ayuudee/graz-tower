import CertifiedAtc.GreenfieldCompletion

namespace CertifiedAtc
namespace Greenfield

theorem observedInstructionCompletion_climbTo_complete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 5000)) :
    observedInstructionCompletion? (.climbTo "TEST123" (.altitudeFeet 4000)) observation =
      some .complete := by
  simp [observedInstructionCompletion?, levelAtOrAbove, hAlt, comparableFeet]

theorem observedInstructionCompletion_climbTo_notComplete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 3000)) :
    observedInstructionCompletion? (.climbTo "TEST123" (.altitudeFeet 4000)) observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, levelAtOrAbove, hAlt, comparableFeet]

theorem observedInstructionCompletion_descendTo_complete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 2000)) :
    observedInstructionCompletion? (.descendTo "TEST123" (.altitudeFeet 3000)) observation =
      some .complete := by
  simp [observedInstructionCompletion?, levelAtOrBelow, hAlt, comparableFeet]

theorem observedInstructionCompletion_descendTo_notComplete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 4000)) :
    observedInstructionCompletion? (.descendTo "TEST123" (.altitudeFeet 3000)) observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, levelAtOrBelow, hAlt, comparableFeet]

theorem observedInstructionCompletion_descendWhenReady_complete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 2000)) :
    observedInstructionCompletion? (.descendWhenReady "TEST123" (.altitudeFeet 3000))
        observation =
      some .complete := by
  simp [observedInstructionCompletion?, levelAtOrBelow, hAlt, comparableFeet]

theorem observedInstructionCompletion_descendWhenReady_notComplete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 4000)) :
    observedInstructionCompletion? (.descendWhenReady "TEST123" (.altitudeFeet 3000))
        observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, levelAtOrBelow, hAlt, comparableFeet]

theorem observedInstructionCompletion_expediteClimb_complete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 6000)) :
    observedInstructionCompletion? (.expediteClimb "TEST123" (.altitudeFeet 4000))
        observation =
      some .complete := by
  simp [observedInstructionCompletion?, levelAtOrAbove, hAlt, comparableFeet]

theorem observedInstructionCompletion_expediteClimb_notComplete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 3000)) :
    observedInstructionCompletion? (.expediteClimb "TEST123" (.altitudeFeet 4000))
        observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, levelAtOrAbove, hAlt, comparableFeet]

theorem observedInstructionCompletion_expediteDescend_complete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 2500)) :
    observedInstructionCompletion? (.expediteDescend "TEST123" (.altitudeFeet 3000))
        observation =
      some .complete := by
  simp [observedInstructionCompletion?, levelAtOrBelow, hAlt, comparableFeet]

theorem observedInstructionCompletion_expediteDescend_notComplete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 3500)) :
    observedInstructionCompletion? (.expediteDescend "TEST123" (.altitudeFeet 3000))
        observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, levelAtOrBelow, hAlt, comparableFeet]

theorem observedInstructionCompletion_maintainLevel_complete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 3000)) :
    observedInstructionCompletion? (.maintainLevel "TEST123" (.altitudeFeet 3000))
        observation =
      some .complete := by
  simp [observedInstructionCompletion?, levelMatches, hAlt, comparableFeet]

theorem observedInstructionCompletion_maintainLevel_notComplete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 2800)) :
    observedInstructionCompletion? (.maintainLevel "TEST123" (.altitudeFeet 3000))
        observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, levelMatches, hAlt, comparableFeet]

theorem observedInstructionCompletion_stopClimbAt_complete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 5000)) :
    observedInstructionCompletion? (.stopClimbAt "TEST123" (.altitudeFeet 5000))
        observation =
      some .complete := by
  simp [observedInstructionCompletion?, levelMatches, hAlt, comparableFeet]

theorem observedInstructionCompletion_stopClimbAt_notComplete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 4800)) :
    observedInstructionCompletion? (.stopClimbAt "TEST123" (.altitudeFeet 5000))
        observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, levelMatches, hAlt, comparableFeet]

theorem observedInstructionCompletion_stopDescentAt_complete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 4000)) :
    observedInstructionCompletion? (.stopDescentAt "TEST123" (.altitudeFeet 4000))
        observation =
      some .complete := by
  simp [observedInstructionCompletion?, levelMatches, hAlt, comparableFeet]

theorem observedInstructionCompletion_stopDescentAt_notComplete_exact
    (observation : CompletionObservation)
    (hAlt : observation.altitude = some (.altitudeFeet 4200)) :
    observedInstructionCompletion? (.stopDescentAt "TEST123" (.altitudeFeet 4000))
        observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, levelMatches, hAlt, comparableFeet]

theorem observedInstructionCompletion_maintainAltitudeUntilEstablished_complete_component
    (observation : CompletionObservation)
    (hEstablished : .localiser ∈ observation.establishedApproachComponents) :
    observedInstructionCompletion?
        (.maintainAltitudeUntilEstablished "TEST123" (.altitudeFeet 3000) .localiser)
        observation =
      some .complete := by
  simp [observedInstructionCompletion?, hEstablished]

theorem observedInstructionCompletion_maintainAltitudeUntilEstablished_notComplete_missingComponent
    (observation : CompletionObservation)
    (hMissing : .localiser ∉ observation.establishedApproachComponents) :
    observedInstructionCompletion?
        (.maintainAltitudeUntilEstablished "TEST123" (.altitudeFeet 3000) .localiser)
        observation =
      some .notComplete := by
  simp [observedInstructionCompletion?, hMissing]

end Greenfield
end CertifiedAtc
