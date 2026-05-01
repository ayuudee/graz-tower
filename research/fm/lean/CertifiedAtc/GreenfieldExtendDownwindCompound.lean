import CertifiedAtc.GreenfieldExtendDownwind
import CertifiedAtc.GreenfieldSourceDomainPersistentPlain

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape compound closure for `ExtendDownwind`.

This widens the single-step slice to one leading `ExtendDownwind` plus
immediate adjunct tails, while keeping the current source-domain-supplied
semantics explicit. Current-shape authority closure for the delivered Phase B
surface now lives separately in `GreenfieldRouteAdjacentAuthority`.
-/

inductive ExtendDownwindCompoundCurrentShapeIssuable
    (world : RouteBearingScopedAviationWorld) :
    StructuredClearance → Prop
  | mk
      {clearance : StructuredClearance}
      {content : CompoundClearanceContent}
      {target : AircraftId}
      {tail : List AtcInstruction}
      (hContent : clearance.content = .compound content)
      (hSteps : content.steps = .extendDownwind target :: tail)
      (hReady :
        SourceDomainSuppliedPersistentPlainCompoundReady
          world
          (.extendDownwind target)
          tail)
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = none) :
      ExtendDownwindCompoundCurrentShapeIssuable world clearance

theorem ExtendDownwindCompoundCurrentShapeIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : ExtendDownwindCompoundCurrentShapeIssuable world clearance) :
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
  rename_i content target tail hContent hSteps hReady hDomain hCondition
  exact
    SourceDomainSuppliedPersistentPlainCompoundAdmissionSoundnessTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      (content := content)
      (primary := .extendDownwind target)
      (tail := tail)
      (sourceDomain := .runway)
      hReach
      hFresh
      hContent
      hSteps
      hReady
      hDomain
      hCondition

theorem sampleResolvedExtendDownwindContact_requiredCompletionStepIndices :
    sampleManagedResolvedExtendDownwindContactSuppressed.clearSuppression.requiredCompletionStepIndices = [1] := by
  native_decide

theorem sampleResolvedExtendDownwindContact_completes_on_contact :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedExtendDownwindContactSuppressed.clearSuppression
        { currentRole := some .tower }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 1 := by
  native_decide

theorem sampleResolvedExtendDownwindContact_reconcile_transitions_to_terminal :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedExtendDownwindContactSuppressed.clearSuppression]
        { currentRole := some .tower }
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = [] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-EXT-DW-CONTACT"] := by
  native_decide

theorem sampleExtendDownwindContact_frequencySupersession_preserves_primary :
    let admission :=
      admitResolvedClearance
        [sampleManagedResolvedExtendDownwindContactSuppressed.clearSuppression]
        sampleResolvedIncomingTowerContact
    resolvedClearanceIds admission.clearances = ["CLR-EXT-DW-CONTACT", "CLR-EXT-DW-FREQ"] ∧
      resolvedClearanceIds admission.fullySuperseded = [] ∧
      resolvedClearanceIds admission.partiallySuperseded = ["CLR-EXT-DW-CONTACT"] ∧
      findResolvedById admission.clearances "CLR-EXT-DW-CONTACT" =
        some (sampleManagedResolvedExtendDownwindContactSuppressed.clearSuppression.suppress
          (UniqueSet.singleton .frequency)) := by
  native_decide

theorem sampleExtendDownwindContact_frequencySupersession_reconcile_transitions_to_terminal :
    let admission :=
      admitResolvedClearance
        [sampleManagedResolvedExtendDownwindContactSuppressed.clearSuppression]
        sampleResolvedIncomingTowerContact
    let reconciliation :=
      reconcileResolvedClearances
        admission.clearances
        {}
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = ["CLR-EXT-DW-FREQ"] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-EXT-DW-CONTACT"] := by
  native_decide

end Greenfield
end CertifiedAtc
