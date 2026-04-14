import CertifiedAtc.GreenfieldRouteBearingCompound

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldRouteBearingLifecycle` packages the current execution behavior of
the widened route-bearing greenfield surface.

This module is intentionally about the model as it exists today, not a more
aspirational future surface:

- `ClearedTo` compounds complete once the resolved clearance limit and their
  immediate adjuncts complete
- single-step `HoldAt` remains active because the holding step is persistent
- `HoldAt` compounds complete once their non-persistent adjuncts complete
- `ClearedApproach` remains active even after immediate adjunct completion
- `JoinCircuit` compounds complete once circuit membership/altitude and their
  immediate adjuncts complete
-/

def sampleRouteBearingRouteContactObservation : CompletionObservation :=
  { position := some "P-HOLD"
    currentRole := some .approach }

def sampleManagedResolvedRouteFrequencyActive : ManagedResolvedClearance :=
  { resolved := sampleResolvedRouteFrequency }

def sampleResolvedSinglePublishedHold : ResolvedClearance :=
  sampleResolvedHoldingFromWorld

def sampleManagedResolvedSinglePublishedHold : ManagedResolvedClearance :=
  { resolved := sampleResolvedSinglePublishedHold }

def sampleResolvedHoldContact : ResolvedClearance :=
  { source :=
      { id := "CLR-HOLD-CONTACT"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .holdAt "TEST123" (.published "HOLD") (some "1200Z")
              , .contactFrequency "TEST123" .approach none ] }
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 3
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .route
          (.holdAt "TEST123" (.published "HOLD") (some "1200Z"))
          (.holding { holdingPattern := "HOLD-PTN", fix := "HOLD" })
          (by native_decide)
      , compileResolvedStep
          1
          .route
          (.contactFrequency "TEST123" .approach none)
          (.frequencyChange { roleName := .approach, instructedFrequency := none })
          (by native_decide) ] }

def sampleManagedResolvedHoldContact : ManagedResolvedClearance :=
  { resolved := sampleResolvedHoldContact }

def sampleHoldContactObservation : CompletionObservation :=
  { currentRole := some .approach }

def sampleResolvedSingleApproach : ResolvedClearance :=
  { source :=
      { id := "CLR-APP-SINGLE"
        aircraft := "TEST123"
        content := .single (.clearedApproach "TEST123" .ils "27" none)
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 4
        status := .active
        condition := none }
    steps := [sampleResolvedApproachStep] }

def sampleManagedResolvedSingleApproach : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleApproach }

def sampleResolvedApproachEstablished : ResolvedClearance :=
  { source :=
      { id := "CLR-APP-EST"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .clearedApproach "TEST123" .ils "27" none
              , .maintainAltitudeUntilEstablished
                  "TEST123"
                  (.altitudeFeet 2000)
                  .localiser ] }
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 5
        status := .active
        condition := none }
    steps :=
      [ sampleResolvedApproachStep
      , compileResolvedStep
          1
          .route
          (.maintainAltitudeUntilEstablished
            "TEST123"
            (.altitudeFeet 2000)
            .localiser)
          .plain
          rfl ] }

def sampleManagedResolvedApproachEstablished : ManagedResolvedClearance :=
  { resolved := sampleResolvedApproachEstablished }

def sampleApproachEstablishedObservation : CompletionObservation :=
  { establishedApproachComponents := UniqueSet.singleton .localiser }

def sampleResolvedJoinCircuitMonitor : ResolvedClearance :=
  { source :=
      { id := "CLR-CIRCUIT-MON"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .joinCircuit "TEST123" .leftHand .downwind (some "27")
              , .monitorFrequency "TEST123" .tower none ] }
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 6
        status := .active
        condition := none }
    steps :=
      [ sampleResolvedCircuitJoinStep
      , compileResolvedStep
          1
          .runway
          (.monitorFrequency "TEST123" .tower none)
          (.frequencyChange { roleName := .tower, instructedFrequency := none })
          (by native_decide) ] }

def sampleManagedResolvedJoinCircuitMonitor : ManagedResolvedClearance :=
  { resolved := sampleResolvedJoinCircuitMonitor }

def sampleJoinCircuitMonitorObservation : CompletionObservation :=
  { activeCircuits := UniqueSet.singleton "CIRCUIT-27-LH"
    altitude := some (.altitudeFeet 1200)
    currentRole := some .tower }

theorem sampleResolvedRouteFrequency_requiredCompletionStepIndices :
    sampleManagedResolvedRouteFrequencyActive.requiredCompletionStepIndices = [0, 1] := by
  native_decide

theorem sampleResolvedRouteFrequency_completes_on_limit_and_contact :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedRouteFrequencyActive
        sampleRouteBearingRouteContactObservation
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.ofList [0, 1] := by
  native_decide

theorem sampleResolvedRouteFrequency_reconcile_transitions_to_terminal :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedRouteFrequencyActive]
        sampleRouteBearingRouteContactObservation
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = [] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-ROUTE-FREQ"] := by
  native_decide

theorem singlePublishedHold_remains_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSinglePublishedHold
        {}
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = ({} : UniqueSet Nat) := by
  native_decide

theorem sampleResolvedHoldContact_requiredCompletionStepIndices :
    sampleManagedResolvedHoldContact.requiredCompletionStepIndices = [1] := by
  native_decide

theorem sampleResolvedHoldContact_completes_on_contact :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedHoldContact
        sampleHoldContactObservation
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 1 := by
  native_decide

theorem sampleResolvedHoldContact_reconcile_transitions_to_terminal :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedHoldContact]
        sampleHoldContactObservation
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = [] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-HOLD-CONTACT"] := by
  native_decide

theorem singleClearedApproach_remains_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleApproach
        sampleApproachEstablishedObservation
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = ({} : UniqueSet Nat) := by
  native_decide

theorem sampleResolvedApproachEstablished_requiredCompletionStepIndices :
    sampleManagedResolvedApproachEstablished.requiredCompletionStepIndices = [0, 1] := by
  native_decide

theorem sampleResolvedApproachEstablished_remains_active_after_adjunct_completion :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedApproachEstablished
        sampleApproachEstablishedObservation
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 1 := by
  native_decide

theorem sampleResolvedApproachEstablished_reconcile_stays_active :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedApproachEstablished]
        sampleApproachEstablishedObservation
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = ["CLR-APP-EST"] ∧
      resolvedClearanceIds reconciliation.terminalClearances = [] := by
  native_decide

theorem sampleResolvedJoinCircuitMonitor_requiredCompletionStepIndices :
    sampleManagedResolvedJoinCircuitMonitor.requiredCompletionStepIndices = [0, 1] := by
  native_decide

theorem sampleResolvedJoinCircuitMonitor_completes_on_membership_and_monitor :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedJoinCircuitMonitor
        sampleJoinCircuitMonitorObservation
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.ofList [0, 1] := by
  native_decide

theorem sampleResolvedJoinCircuitMonitor_reconcile_transitions_to_terminal :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedJoinCircuitMonitor]
        sampleJoinCircuitMonitorObservation
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = [] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-CIRCUIT-MON"] := by
  native_decide

end Greenfield
end CertifiedAtc
