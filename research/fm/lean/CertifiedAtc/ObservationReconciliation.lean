namespace CertifiedAtc

/-!
# Observation Reconciliation

Lean model of the observation-driven stage reconciliation functions from
`controller/.../procedure/{Departure,Arrival,Ground}Reconciliation.kt`.

Each reconciliation function is modelled as a total pure function
`reconcile : Stage → Position → Stage` on finite enumerated types,
mirroring the exhaustive Kotlin `when` expressions.  `TransitionKind` is
omitted — it is an action-layer annotation irrelevant to the structural
properties proved here.

## Properties proved

For each function we establish:

1. **Terminality** — the complete stage is a fixed point of reconciliation:
   `∀ p, reconcile .complete p = .complete`

2. **Monotonicity** — the stage ordinal never decreases (departure and ground
   functions, which have no legitimate regressions):
   `∀ s p, s.toNat ≤ (reconcile s p).toNat`

3. **Monotonicity with named exceptions** (arrival only) — forward-only except
   for the three go-around regression paths (ICAO 4444 §7.10.2):
   `∀ s p, s.toNat ≤ (reconcileArrival s p).toNat ∨ isGoAroundRegression s p`

4. **Airborne correspondence** (departure) — an airborne position observation
   always produces at least `awaitTakeoffObserved`, regardless of current stage:
   `∀ s p, p.isAirborne → awaitTakeoffObserved.toNat ≤ (reconcileDeparture s p).toNat`

5. **On-runway correspondence** (arrival) — observing the aircraft on the runway
   always produces at least `awaitLandedObserved` for any non-complete stage:
   `∀ s, s ≠ .complete → awaitLandedObserved.toNat ≤ (reconcileArrival s .onRunway).toNat`

All proofs are closed by exhaustive case analysis (`cases s <;> cases p`).
The finite types carry `Fintype` and `DecidableEq` so `decide` evaluates every
ground proposition. The runtime Kotlin tests and these proofs are dual
representations of the same specification; the `toNat` functions mirror Kotlin's
`enum.ordinal` (zero-indexed declaration order).
-/

-- ── Departure ──────────────────────────────────────────────────────────────

inductive DepartureStage
  | awaitReady
  | awaitLineUpObserved
  | takeoffClearanceIssued
  | awaitTakeoffObserved
  | complete
  deriving DecidableEq, Fintype, Repr

/-- Ordinal mirroring Kotlin `enum.ordinal` (declaration order, zero-indexed). -/
def DepartureStage.toNat : DepartureStage → Nat
  | .awaitReady             => 0
  | .awaitLineUpObserved    => 1
  | .takeoffClearanceIssued => 2
  | .awaitTakeoffObserved   => 3
  | .complete               => 4

inductive DeparturePosition
  | atHolding
  | onRunway
  | onRunwayRolling
  | airborneOverRunway
  | onClimbout
  | elsewhere
  deriving DecidableEq, Fintype, Repr

/-- True when the position observation confirms the aircraft is airborne. -/
def DeparturePosition.isAirborne : DeparturePosition → Bool
  | .airborneOverRunway | .onClimbout => true
  | _                                 => false

/-- Total reconciliation for tower departures.  Mirrors `reconcileDepartureStage`. -/
def reconcileDeparture (s : DepartureStage) (p : DeparturePosition) : DepartureStage :=
  match s, p with
  -- AwaitReady: incursion/anomalous paths advance the stage; holding/elsewhere stay
  | .awaitReady, .atHolding          => .awaitReady
  | .awaitReady, .onRunway           => .awaitLineUpObserved
  | .awaitReady, .onRunwayRolling    => .awaitLineUpObserved
  | .awaitReady, .airborneOverRunway => .awaitTakeoffObserved
  | .awaitReady, .onClimbout         => .awaitTakeoffObserved
  | .awaitReady, .elsewhere          => .awaitReady
  -- AwaitLineUpObserved: airborne advances; ground stays
  | .awaitLineUpObserved, .airborneOverRunway
  | .awaitLineUpObserved, .onClimbout         => .awaitTakeoffObserved
  | .awaitLineUpObserved, _                   => .awaitLineUpObserved
  -- TakeoffClearanceIssued: airborne advances; ground stays (clearance awaiting execution)
  | .takeoffClearanceIssued, .airborneOverRunway
  | .takeoffClearanceIssued, .onClimbout         => .awaitTakeoffObserved
  | .takeoffClearanceIssued, _                   => .takeoffClearanceIssued
  -- AwaitTakeoffObserved and Complete: terminal/stable
  | .awaitTakeoffObserved, _ => .awaitTakeoffObserved
  | .complete, _             => .complete

-- ── Theorems: departure ───────────────────────────────────────────────────

/-- The complete stage is a fixed point of departure reconciliation. -/
theorem dep_terminal (p : DeparturePosition) :
    reconcileDeparture .complete p = .complete := by
  cases p <;> rfl

/-- Departure reconciliation never decreases the stage ordinal (no regressions). -/
theorem dep_monotone (s : DepartureStage) (p : DeparturePosition) :
    s.toNat ≤ (reconcileDeparture s p).toNat := by
  cases s <;> cases p <;> decide

/-- An airborne position always advances to at least `awaitTakeoffObserved`. -/
theorem dep_airborne_advances (s : DepartureStage) (p : DeparturePosition)
    (h : p.isAirborne = true) :
    DepartureStage.awaitTakeoffObserved.toNat ≤ (reconcileDeparture s p).toNat := by
  cases p <;> cases s <;> simp_all [reconcileDeparture, DepartureStage.toNat,
                                     DeparturePosition.isAirborne]

-- ── Arrival ────────────────────────────────────────────────────────────────

inductive ArrivalStage
  | awaitDownwind
  | awaitApproach
  | landingClearanceIssued
  | awaitLandedObserved
  | awaitVacating
  | complete
  deriving DecidableEq, Fintype, Repr

def ArrivalStage.toNat : ArrivalStage → Nat
  | .awaitDownwind          => 0
  | .awaitApproach          => 1
  | .landingClearanceIssued => 2
  | .awaitLandedObserved    => 3
  | .awaitVacating          => 4
  | .complete               => 5

inductive ArrivalPosition
  | onDownwind
  | onBase
  | onFinal
  | onApproach
  | onRunway
  | clearOfRunway
  | airborneElsewhere
  | elsewhere
  deriving DecidableEq, Fintype, Repr

/-- Go-around regression: aircraft observed back on downwind after being on approach
    or later in the sequence.  Standard operation (ICAO 4444 §7.10.2). -/
def isGoAroundRegression (s : ArrivalStage) (p : ArrivalPosition) : Bool :=
  match s, p with
  | .awaitApproach,         .onDownwind
  | .landingClearanceIssued, .onDownwind
  | .awaitLandedObserved,   .onDownwind => true
  | _, _                                => false

/-- Total reconciliation for tower arrivals.  Mirrors `reconcileArrivalStage`. -/
def reconcileArrival (s : ArrivalStage) (p : ArrivalPosition) : ArrivalStage :=
  match s, p with
  -- AwaitDownwind
  | .awaitDownwind, .onDownwind
  | .awaitDownwind, .airborneElsewhere
  | .awaitDownwind, .elsewhere          => .awaitDownwind
  | .awaitDownwind, .onBase
  | .awaitDownwind, .onFinal
  | .awaitDownwind, .onApproach         => .awaitApproach
  | .awaitDownwind, .onRunway           => .awaitLandedObserved
  | .awaitDownwind, .clearOfRunway      => .awaitVacating
  -- AwaitApproach
  | .awaitApproach, .onDownwind         => .awaitDownwind   -- go-around
  | .awaitApproach, .onBase
  | .awaitApproach, .onFinal
  | .awaitApproach, .onApproach
  | .awaitApproach, .airborneElsewhere
  | .awaitApproach, .elsewhere          => .awaitApproach
  | .awaitApproach, .onRunway           => .awaitLandedObserved
  | .awaitApproach, .clearOfRunway      => .awaitVacating
  -- LandingClearanceIssued
  | .landingClearanceIssued, .onDownwind => .awaitDownwind  -- go-around
  | .landingClearanceIssued, .onBase
  | .landingClearanceIssued, .onFinal
  | .landingClearanceIssued, .onApproach
  | .landingClearanceIssued, .airborneElsewhere
  | .landingClearanceIssued, .elsewhere  => .landingClearanceIssued
  | .landingClearanceIssued, .onRunway   => .awaitLandedObserved
  | .landingClearanceIssued, .clearOfRunway => .awaitVacating
  -- AwaitLandedObserved
  | .awaitLandedObserved, .onDownwind   => .awaitDownwind   -- late go-around
  | .awaitLandedObserved, .onBase
  | .awaitLandedObserved, .onFinal
  | .awaitLandedObserved, .onApproach
  | .awaitLandedObserved, .onRunway
  | .awaitLandedObserved, .airborneElsewhere
  | .awaitLandedObserved, .elsewhere    => .awaitLandedObserved
  | .awaitLandedObserved, .clearOfRunway => .awaitVacating
  -- AwaitVacating and Complete: stable
  | .awaitVacating, _ => .awaitVacating
  | .complete,      _ => .complete

-- ── Theorems: arrival ─────────────────────────────────────────────────────

/-- The complete stage is a fixed point of arrival reconciliation. -/
theorem arr_terminal (p : ArrivalPosition) :
    reconcileArrival .complete p = .complete := by
  cases p <;> rfl

/-- Arrival reconciliation is forward-only except for the three named go-around
    regressions (AwaitApproach/LandingClearanceIssued/AwaitLandedObserved × OnDownwind). -/
theorem arr_monotone_or_goaround (s : ArrivalStage) (p : ArrivalPosition) :
    s.toNat ≤ (reconcileArrival s p).toNat ∨ isGoAroundRegression s p = true := by
  cases s <;> cases p <;> decide

/-- Observing the aircraft on the runway always advances to at least
    `awaitLandedObserved` when the stage is not already complete. -/
theorem arr_runway_advances (s : ArrivalStage) (hs : s ≠ .complete) :
    ArrivalStage.awaitLandedObserved.toNat ≤ (reconcileArrival s .onRunway).toNat := by
  cases s <;> first | exact absurd rfl hs | decide

-- ── Ground departure ───────────────────────────────────────────────────────

inductive GroundDepartureStage
  | awaitTaxiRequest
  | awaitAtHolding
  | complete
  deriving DecidableEq, Fintype, Repr

def GroundDepartureStage.toNat : GroundDepartureStage → Nat
  | .awaitTaxiRequest => 0
  | .awaitAtHolding   => 1
  | .complete         => 2

inductive GroundPosition
  | atStand
  | taxiing
  | atHoldingPoint
  | onRunway
  | elsewhere
  deriving DecidableEq, Fintype, Repr

/-- Total reconciliation for ground departure taxi.  Mirrors `reconcileGroundDepartureStage`. -/
def reconcileGroundDeparture (s : GroundDepartureStage) (p : GroundPosition) : GroundDepartureStage :=
  match s, p with
  | .awaitTaxiRequest, .atHoldingPoint
  | .awaitTaxiRequest, .onRunway      => .awaitAtHolding   -- advanced / anomalous incursion
  | .awaitTaxiRequest, _              => .awaitTaxiRequest
  | .awaitAtHolding, _                => .awaitAtHolding
  | .complete, _                      => .complete

-- ── Theorems: ground departure ────────────────────────────────────────────

theorem grdDep_terminal (p : GroundPosition) :
    reconcileGroundDeparture .complete p = .complete := by
  cases p <;> rfl

theorem grdDep_monotone (s : GroundDepartureStage) (p : GroundPosition) :
    s.toNat ≤ (reconcileGroundDeparture s p).toNat := by
  cases s <;> cases p <;> decide

-- ── Ground arrival ─────────────────────────────────────────────────────────

inductive GroundArrivalStage
  | taxiToStand
  | awaitParked
  | complete
  deriving DecidableEq, Fintype, Repr

def GroundArrivalStage.toNat : GroundArrivalStage → Nat
  | .taxiToStand => 0
  | .awaitParked => 1
  | .complete    => 2

/-- Total reconciliation for ground arrival taxi.  Mirrors `reconcileGroundArrivalStage`. -/
def reconcileGroundArrival (s : GroundArrivalStage) (p : GroundPosition) : GroundArrivalStage :=
  match s, p with
  | .taxiToStand, .atStand => .awaitParked   -- already at stand: advance
  | .taxiToStand, _        => .taxiToStand
  | .awaitParked, _        => .awaitParked
  | .complete, _           => .complete

-- ── Theorems: ground arrival ──────────────────────────────────────────────

theorem grdArr_terminal (p : GroundPosition) :
    reconcileGroundArrival .complete p = .complete := by
  cases p <;> rfl

theorem grdArr_monotone (s : GroundArrivalStage) (p : GroundPosition) :
    s.toNat ≤ (reconcileGroundArrival s p).toNat := by
  cases s <;> cases p <;> decide

end CertifiedAtc
