import CertifiedAtc.GreenfieldCompletion
import CertifiedAtc.GreenfieldResolution
import CertifiedAtc.ClearanceEnvelope

namespace CertifiedAtc
namespace Greenfield

/--
Route-bearing widening surface above the closed scoped programme.

This module does not try to pretend that the whole widened route-bearing family
already has one uniform top-level theorem story. Instead it packages the
current truthful state at the resolved boundary:

- `ClearedTo`, `HoldAt`, `ClearedApproach`, and `JoinCircuit` are all first-
  class route-bearing instructions in the current greenfield model
- all four require specific resolution
- route, holding, and circuit-join steps already have honest resolved
  execution/completion stories
- `ClearedApproach` is now also resolved against concrete published approach /
  missed-approach facts, and completes on landing or published missed-approach
  hold entry in the current world-backed model
- authority is only well-defined at the resolved payload layer for the
  instructions whose payload identifies a concrete entity

That makes this module the right foundation for widening without over-claiming
about the still-open route-clearance and holding-plan issuance path.
-/

def RouteBearingCoreInstruction : AtcInstruction → Prop
  | .clearedTo _ _ _ => True
  | .holdAt _ _ _ => True
  | .clearedApproach _ _ _ _ => True
  | .joinCircuit _ _ _ _ => True
  | _ => False

def BridgeableLegacyRouteBearingInstruction : AtcInstruction → Prop
  | .clearedApproach _ _ _ _ => True
  | .joinCircuit _ _ _ _ => True
  | _ => False

theorem routeBearingCoreInstruction_requiresSpecificResolution
    {instruction : AtcInstruction}
    (hRoute : RouteBearingCoreInstruction instruction) :
    instructionNeedsSpecificResolution instruction = true := by
  cases instruction <;>
    simp [RouteBearingCoreInstruction, instructionNeedsSpecificResolution] at hRoute ⊢

theorem bridgeableLegacyRouteBearingInstruction_requiresSpecificResolution
    {instruction : AtcInstruction}
    (hRoute : BridgeableLegacyRouteBearingInstruction instruction) :
    instructionNeedsSpecificResolution instruction = true := by
  exact
    routeBearingCoreInstruction_requiresSpecificResolution
      (by cases instruction <;> simp [BridgeableLegacyRouteBearingInstruction,
        RouteBearingCoreInstruction] at hRoute ⊢)

theorem routeBearingCoreInstruction_metadataDomain_route_or_none
    {instruction : AtcInstruction}
    (hRoute : RouteBearingCoreInstruction instruction) :
    instructionDomain? instruction = some .route ∨
      instructionDomain? instruction = none := by
  cases instruction <;>
    simp [RouteBearingCoreInstruction, instructionDomain?] at hRoute ⊢

def resolvedRouteBearingAuthorityGrant? :
    ResolvedPayload → Option CompileAuthorityGrantView
  | .holding _ =>
      some { entityType := .holdingPattern, operation := .hold }
  | .approach _ =>
      some { entityType := .instrumentApproach, operation := .approachClearance }
  | .circuitJoin _ =>
      some { entityType := .circuitProcedure, operation := .circuit }
  | _ => none

def resolvedStepRouteBearingAuthorityGrant?
    (step : ResolvedStep) : Option CompileAuthorityGrantView :=
  resolvedRouteBearingAuthorityGrant? step.payload

@[simp] theorem resolvedRouteBearingAuthorityGrant?_route
    (clearance : ResolvedRouteClearance) :
    resolvedRouteBearingAuthorityGrant? (.route clearance) = none := by
  rfl

@[simp] theorem resolvedRouteBearingAuthorityGrant?_holding
    (holding : ResolvedHoldingInstruction) :
    resolvedRouteBearingAuthorityGrant? (.holding holding) =
      some { entityType := .holdingPattern, operation := .hold } := by
  rfl

@[simp] theorem resolvedRouteBearingAuthorityGrant?_approach
    (approach : ResolvedApproachClearance) :
    resolvedRouteBearingAuthorityGrant? (.approach approach) =
      some { entityType := .instrumentApproach, operation := .approachClearance } := by
  rfl

@[simp] theorem resolvedRouteBearingAuthorityGrant?_circuitJoin
    (circuit : ResolvedCircuitJoin) :
    resolvedRouteBearingAuthorityGrant? (.circuitJoin circuit) =
      some { entityType := .circuitProcedure, operation := .circuit } := by
  rfl

def sampleResolvedRouteStep : ResolvedStep :=
  compileResolvedStep
    0
    .route
    (.clearedTo "TEST123" "HOLD" (some (.viaSid "SID1")))
    (.route
      { clearanceLimitFix := "HOLD"
        clearanceLimitPoint := "P-HOLD"
        routePoints := ["RWY27", "SID-EXIT", "P-HOLD"]
        clearanceLimitHoldingPattern := some "HOLD-PTN" })
    (by native_decide)

def sampleResolvedHoldingStep : ResolvedStep :=
  compileResolvedStep
    0
    .route
    (.holdAt "TEST123" (.published "HOLD") (some "1200Z"))
    (.holding
      { holdingPattern := "HOLD-PTN"
        fix := "HOLD"
        fixPoint := "P-HOLD"
        loopPoints := ["P-HOLD", "P-HOLD-1", "P-HOLD-2", "P-HOLD"] })
    (by native_decide)

def sampleResolvedApproachStep : ResolvedStep :=
  compileResolvedStep
    0
    .route
    (.clearedApproach "TEST123" .ils "27" none)
    (.approach
      { approach := "ILS27"
        runway := "27"
        waypointPoints := ["IAF-27", "FAF-27", "RWY27"]
        thresholdPoint := "RWY27"
        missedApproachPoints := ["RWY27", "MA-1", "P-HOLD"]
        missedApproachHoldingPattern := "HOLD-PTN" })
    (by native_decide)

def sampleResolvedCircuitJoinStep : ResolvedStep :=
  compileResolvedStep
    0
    .runway
    (.joinCircuit "TEST123" .leftHand .downwind (some "27"))
    (.circuitJoin
      { circuit := "CIRCUIT-27-LH"
        altitude := .altitudeFeet 1200
        entryPoint := "CROSSWIND"
        entryPathPoints := ["JOIN-ENTRY", "CROSSWIND"]
        circuitPoints := ["RWY27", "UPWIND", "CROSSWIND", "DOWNWIND", "BASE", "RWY27"] })
    (by native_decide)

def sampleRouteBearingObservation : CompletionObservation :=
  { position := some "P-HOLD"
    activeCircuits := UniqueSet.singleton "CIRCUIT-27-LH"
    altitude := some (.altitudeFeet 1200) }

def sampleRouteBearingLandingObservation : CompletionObservation :=
  { onGround := true
    runwayTransitions := UniqueSet.singleton "27" }

def sampleRouteBearingMissedHoldObservation : CompletionObservation :=
  { activeHoldingPatterns := UniqueSet.singleton "HOLD-PTN" }

example :
    observedResolvedStepCompletion? sampleRouteBearingObservation sampleResolvedRouteStep =
      some .complete := by
  native_decide

example :
    observedResolvedStepCompletion? sampleRouteBearingObservation sampleResolvedHoldingStep =
      some .notApplicable := by
  native_decide

example :
    observedResolvedStepCompletion? sampleRouteBearingObservation sampleResolvedCircuitJoinStep =
      some .complete := by
  native_decide

example :
    observedResolvedStepCompletion? sampleRouteBearingObservation sampleResolvedApproachStep =
      some .notComplete := by
  native_decide

example :
    observedResolvedStepCompletion? sampleRouteBearingLandingObservation sampleResolvedApproachStep =
      some .complete := by
  native_decide

example :
    observedResolvedStepCompletion? sampleRouteBearingMissedHoldObservation sampleResolvedApproachStep =
      some .complete := by
  native_decide

theorem resolvedApproachStep_stays_incomplete_without_landing_or_missed_hold
    (observation : CompletionObservation) :
    observation.onGround = false →
    "HOLD-PTN" ∉ observation.activeHoldingPatterns →
    observedResolvedStepCompletion? observation sampleResolvedApproachStep = some .notComplete := by
  intro hGround hHold
  simp [sampleResolvedApproachStep, observedResolvedStepCompletion?,
    ResolvedStep.isCompatible, compileResolvedStep, resolutionCompatible,
    hGround, hHold]

example :
    sampleResolvedApproachStep.completionCategory = none := by
  native_decide

example :
    resolvedStepRouteBearingAuthorityGrant? sampleResolvedRouteStep = none := by
  native_decide

example :
    resolvedStepRouteBearingAuthorityGrant? sampleResolvedHoldingStep =
      some { entityType := .holdingPattern, operation := .hold } := by
  native_decide

example :
    resolvedStepRouteBearingAuthorityGrant? sampleResolvedApproachStep =
      some { entityType := .instrumentApproach, operation := .approachClearance } := by
  native_decide

example :
    resolvedStepRouteBearingAuthorityGrant? sampleResolvedCircuitJoinStep =
      some { entityType := .circuitProcedure, operation := .circuit } := by
  native_decide

def sampleResolvedHoldingFromWorld : ResolvedClearance :=
  { source :=
      { id := "CLR-HOLD"
        aircraft := "TEST123"
        content := .single (.holdAt "TEST123" (.published "HOLD") none)
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 2
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .route
          (.holdAt "TEST123" (.published "HOLD") none)
          (.holding
            { holdingPattern := "HOLD-PTN"
              fix := "HOLD"
              fixPoint := "P-HOLD"
              loopPoints := ["P-HOLD", "P-HOLD-1", "P-HOLD-2", "P-HOLD"] })
          (by native_decide) ] }

example :
    ResolvesClearance
      sampleResolutionWorld
      {}
      sampleResolvedHoldingFromWorld.source
      sampleResolvedHoldingFromWorld
      {} := by
  refine ⟨?_, rfl, ?_⟩
  · simp [sampleResolvedHoldingFromWorld, normalizeConditionalEnvelope]
  · apply ResolvesSteps.cons
    · apply ResolvesIndexedStep.holding
      · simp [sampleResolutionWorld, sampleConcreteResolutionWorld,
          ConcreteResolutionWorld.toResolutionWorld]
      · simp [sampleResolutionWorld, sampleConcreteResolutionWorld,
          ConcreteResolutionWorld.toResolutionWorld]
      · simp [sampleResolutionWorld, sampleConcreteResolutionWorld,
          ConcreteResolutionWorld.toResolutionWorld]
    · simpa using ResolvesSteps.nil sampleResolutionWorld {} .route

def sampleResolvedApproachFromWorld : ResolvedClearance :=
  { source :=
      { id := "CLR-APP"
        aircraft := "TEST123"
        content := .single (.clearedApproach "TEST123" .ils "27" none)
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 3
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .route
          (.clearedApproach "TEST123" .ils "27" none)
          (.approach
            { approach := "ILS27"
              runway := "27"
              waypointPoints := ["IAF-27", "FAF-27", "RWY27"]
              thresholdPoint := "RWY27"
              missedApproachPoints := ["RWY27", "MA-1", "P-HOLD"]
              missedApproachHoldingPattern := "HOLD-PTN" })
          (by native_decide) ] }

example :
    ResolvesClearance
      sampleResolutionWorld
      {}
      sampleResolvedApproachFromWorld.source
      sampleResolvedApproachFromWorld
      { currentApproach := some "ILS27" } := by
  refine ⟨?_, rfl, ?_⟩
  · simp [sampleResolvedApproachFromWorld, normalizeConditionalEnvelope]
  · apply ResolvesSteps.cons
    · apply ResolvesIndexedStep.approach
      · simp [sampleResolutionWorld, sampleConcreteResolutionWorld,
          ConcreteResolutionWorld.toResolutionWorld]
      · simp [sampleResolutionWorld, sampleConcreteResolutionWorld,
          ConcreteResolutionWorld.toResolutionWorld]
      · simp [sampleResolutionWorld, sampleConcreteResolutionWorld,
          ConcreteResolutionWorld.toResolutionWorld]
      · simp [sampleResolutionWorld, sampleConcreteResolutionWorld,
          ConcreteResolutionWorld.toResolutionWorld]
    · simpa using
        ResolvesSteps.nil
          sampleResolutionWorld
          { currentApproach := some "ILS27" }
          .route

def sampleResolvedCircuitJoinFromWorld : ResolvedClearance :=
  { source :=
      { id := "CLR-CIRCUIT"
        aircraft := "TEST123"
        content := .single (.joinCircuit "TEST123" .leftHand .downwind (some "27"))
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 4
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .runway
          (.joinCircuit "TEST123" .leftHand .downwind (some "27"))
          (.circuitJoin
            { circuit := "CIRCUIT-27-LH"
              altitude := .altitudeFeet 1200
              entryPoint := "CROSSWIND"
              entryPathPoints := ["JOIN-ENTRY", "CROSSWIND"]
              circuitPoints := ["RWY27", "UPWIND", "CROSSWIND", "DOWNWIND", "BASE", "RWY27"] })
          (by native_decide) ] }

example :
    ResolvesClearance
      sampleResolutionWorld
      {}
      sampleResolvedCircuitJoinFromWorld.source
      sampleResolvedCircuitJoinFromWorld
      { currentCircuit := some "CIRCUIT-27-LH" } := by
  refine ⟨?_, rfl, ?_⟩
  · simp [sampleResolvedCircuitJoinFromWorld, normalizeConditionalEnvelope]
  · apply ResolvesSteps.cons
    · apply ResolvesIndexedStep.joinCircuit
      · simp [sampleResolutionWorld, sampleConcreteResolutionWorld,
          ConcreteResolutionWorld.toResolutionWorld]
      · simp [sampleResolutionWorld, sampleConcreteResolutionWorld,
          ConcreteResolutionWorld.toResolutionWorld]
      · simp [sampleResolutionWorld, sampleConcreteResolutionWorld,
          ConcreteResolutionWorld.toResolutionWorld]
    · simpa using
        ResolvesSteps.nil
          sampleResolutionWorld
          { currentCircuit := some "CIRCUIT-27-LH" }
          .runway

end Greenfield
end CertifiedAtc
