import CertifiedAtc.GreenfieldRouteBearingLifecycle

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldRouteBearingSupersession` records the current supersession behavior
of the widened greenfield route-bearing surface.

The focus here is not generic supersession algebra; that already lives in
`GreenfieldExecution`. This module packages the concrete route-bearing
consequences we care about:

- frequency updates partially supersede mixed route/frequency compounds without
  destroying the route-bearing step
- `GoAround` fully supersedes active approach compounds across runway, route,
  and level domains
- a `HoldAt` compound whose only non-persistent adjunct is frequency becomes
  terminal once that adjunct is superseded, which is the current modeled
  behavior and an important semantic fact to keep visible
-/

def sampleResolvedTowerContact : ResolvedClearance :=
  { source :=
      { id := "CLR-FREQ-TWR"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower none)
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 7
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower none)
          (.frequencyChange { roleName := .tower, instructedFrequency := none })
          (by native_decide) ] }

def sampleRouteLimitOnlyObservation : CompletionObservation :=
  { position := some "P-HOLD" }

def sampleResolvedGoAround : ResolvedClearance :=
  { source :=
      { id := "CLR-GA"
        aircraft := "TEST123"
        content := .single (.goAround "TEST123")
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 8
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .runway
          (.goAround "TEST123")
          .plain
          rfl ] }

theorem sampleRouteFrequency_frequencySupersession_preserves_route_step :
    let admission :=
      admitResolvedClearance
        [sampleManagedResolvedRouteFrequencyActive]
        sampleResolvedTowerContact
    resolvedClearanceIds admission.clearances = ["CLR-ROUTE-FREQ", "CLR-FREQ-TWR"] ∧
      resolvedClearanceIds admission.fullySuperseded = [] ∧
      resolvedClearanceIds admission.partiallySuperseded = ["CLR-ROUTE-FREQ"] ∧
      findResolvedById admission.clearances "CLR-ROUTE-FREQ" =
        some (sampleManagedResolvedRouteFrequencyActive.suppress (UniqueSet.singleton .frequency)) := by
  native_decide

theorem sampleRouteFrequency_frequencySupersession_then_route_limit_completes_old_route :
    let admission :=
      admitResolvedClearance
        [sampleManagedResolvedRouteFrequencyActive]
        sampleResolvedTowerContact
    let reconciliation :=
      reconcileResolvedClearances
        admission.clearances
        sampleRouteLimitOnlyObservation
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = ["CLR-FREQ-TWR"] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-ROUTE-FREQ"] := by
  native_decide

theorem sampleApproachEstablished_goAround_fullySupersedes :
    let admission :=
      admitResolvedClearance
        [sampleManagedResolvedApproachEstablished]
        sampleResolvedGoAround
    resolvedClearanceIds admission.clearances = ["CLR-GA"] ∧
      resolvedClearanceIds admission.fullySuperseded = ["CLR-APP-EST"] ∧
      resolvedClearanceIds admission.partiallySuperseded = [] := by
  native_decide

theorem sampleHoldContact_frequencySupersession_closes_old_hold_compound :
    let admission :=
      admitResolvedClearance
        [sampleManagedResolvedHoldContact]
        sampleResolvedTowerContact
    let reconciliation :=
      reconcileResolvedClearances
        admission.clearances
        {}
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = ["CLR-FREQ-TWR"] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-HOLD-CONTACT"] := by
  native_decide

end Greenfield
end CertifiedAtc
