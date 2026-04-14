import CertifiedAtc.GreenfieldOrbit
import CertifiedAtc.GreenfieldSourceDomainPersistentPlain

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape compound closure for `Orbit`.

This is the same source-domain-supplied persistent-plain pattern as
`ExtendDownwind`: one leading `Orbit` plus immediate adjunct tails, no new
authority claim, and explicit current engine consequences.
-/

inductive OrbitCompoundCurrentShapeIssuable
    (world : RouteBearingScopedAviationWorld) :
    StructuredClearance → Prop
  | mk
      {clearance : StructuredClearance}
      {content : CompoundClearanceContent}
      {target : AircraftId}
      {direction : OrbitDirection}
      {tail : List AtcInstruction}
      (hContent : clearance.content = .compound content)
      (hSteps : content.steps = .orbit target direction :: tail)
      (hReady :
        SourceDomainSuppliedPersistentPlainCompoundReady
          world
          (.orbit target direction)
          tail)
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = none) :
      OrbitCompoundCurrentShapeIssuable world clearance

theorem OrbitCompoundCurrentShapeIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : OrbitCompoundCurrentShapeIssuable world clearance) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  cases hIssuable
  rename_i content target direction tail hContent hSteps hReady hDomain hCondition
  exact
    SourceDomainSuppliedPersistentPlainCompoundAdmissionSoundnessTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      (content := content)
      (primary := .orbit target direction)
      (tail := tail)
      (sourceDomain := .runway)
      hReach
      hFresh
      hContent
      hSteps
      hReady
      hDomain
      hCondition

theorem sampleResolvedOrbitContact_requiredCompletionStepIndices :
    sampleManagedResolvedOrbitContactSuppressed.clearSuppression.requiredCompletionStepIndices = [1] := by
  native_decide

theorem sampleResolvedOrbitContact_completes_on_contact :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedOrbitContactSuppressed.clearSuppression
        { currentRole := some .tower }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 1 := by
  native_decide

theorem sampleResolvedOrbitContact_reconcile_transitions_to_terminal :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedOrbitContactSuppressed.clearSuppression]
        { currentRole := some .tower }
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = [] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-ORBIT-CONTACT"] := by
  native_decide

theorem sampleOrbitContact_frequencySupersession_preserves_primary :
    let admission :=
      admitResolvedClearance
        [sampleManagedResolvedOrbitContactSuppressed.clearSuppression]
        sampleResolvedIncomingOrbitContact
    resolvedClearanceIds admission.clearances = ["CLR-ORBIT-CONTACT", "CLR-ORBIT-FREQ"] ∧
      resolvedClearanceIds admission.fullySuperseded = [] ∧
      resolvedClearanceIds admission.partiallySuperseded = ["CLR-ORBIT-CONTACT"] ∧
      findResolvedById admission.clearances "CLR-ORBIT-CONTACT" =
        some (sampleManagedResolvedOrbitContactSuppressed.clearSuppression.suppress
          (UniqueSet.singleton .frequency)) := by
  native_decide

theorem sampleOrbitContact_frequencySupersession_reconcile_transitions_to_terminal :
    let admission :=
      admitResolvedClearance
        [sampleManagedResolvedOrbitContactSuppressed.clearSuppression]
        sampleResolvedIncomingOrbitContact
    let reconciliation :=
      reconcileResolvedClearances
        admission.clearances
        {}
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = ["CLR-ORBIT-FREQ"] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-ORBIT-CONTACT"] := by
  native_decide

end Greenfield
end CertifiedAtc
