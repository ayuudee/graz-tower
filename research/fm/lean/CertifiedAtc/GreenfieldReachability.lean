import CertifiedAtc.GreenfieldExecution
import CertifiedAtc.GreenfieldResolution

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldReachability` packages the resolved execution invariants into a
small reachable-state story.

The lower execution layer proves that fresh admission and reconciliation
preserve `WellFormedResolvedSet`. This module turns that into an inductive
"reachable active set" boundary so later proofs can assume reachability rather
than re-threading freshness and compatibility side conditions manually.
-/

inductive ReachableResolvedSet : List ManagedResolvedClearance → Prop
  | empty :
      ReachableResolvedSet []
  | admit
      {existing : List ManagedResolvedClearance}
      {incoming : ResolvedClearance}
      (hReach : ReachableResolvedSet existing)
      (hFresh : incoming.source.id ∉ resolvedClearanceIds existing)
      (hCompat : incoming.allStepsCompatible = true) :
      ReachableResolvedSet (admitResolvedClearance existing incoming).clearances
  | reconcile
      {existing : List ManagedResolvedClearance}
      {observation : CompletionObservation}
      {conditionEvaluator : ConditionEvaluator}
      (hReach : ReachableResolvedSet existing) :
      ReachableResolvedSet
        (reconcileResolvedClearances existing observation conditionEvaluator).clearances

theorem ReachableResolvedSet.wellFormed
    {clearances : List ManagedResolvedClearance}
    (hReach : ReachableResolvedSet clearances) :
    WellFormedResolvedSet clearances := by
  induction hReach with
  | empty =>
      constructor
      · simp [UniqueResolvedClearanceIds, resolvedClearanceIds]
      · intro managed hMem
        cases hMem
  | admit hReach hFresh hCompat ih =>
      exact (admitResolvedClearance_preserves_wellFormed_of_fresh_and_compatible _ _ ih hFresh hCompat).1
  | reconcile hReach ih =>
      exact (reconcileResolvedClearances_preserves_wellFormed _ _ _ ih).1

theorem ReachableResolvedSet.uniqueIds
    {clearances : List ManagedResolvedClearance}
    (hReach : ReachableResolvedSet clearances) :
    UniqueResolvedClearanceIds clearances :=
  hReach.wellFormed.1

theorem ReachableResolvedSet.compatible
    {clearances : List ManagedResolvedClearance}
    (hReach : ReachableResolvedSet clearances) :
    AllResolvedCompatible clearances :=
  hReach.wellFormed.2

theorem ReachableResolvedSet.admit_of_resolved
    {existing : List ManagedResolvedClearance}
    {world : ResolutionWorld}
    {initialState finalState : ResolutionState}
    {clearance : StructuredClearance}
    {resolved : ResolvedClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : resolved.source.id ∉ resolvedClearanceIds existing)
    (hResolve : ResolvesClearance world initialState clearance resolved finalState) :
    ReachableResolvedSet (admitResolvedClearance existing resolved).clearances := by
  exact ReachableResolvedSet.admit
    hReach
    hFresh
    (resolvesClearance_allStepsCompatible hResolve)

end Greenfield
end CertifiedAtc
