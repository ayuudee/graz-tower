# 2026-04-22: Root Cause Analysis — Go-Around Cluster and Totality Violations

## Context

An ultra review of the pilot route planner found four critical bugs in the go-around path (C1-C4) and five totality violations (F1-F5). All were fixed in commit `c947de0`. This note captures the root causes so the same class of bug doesn't recur.

## The bugs

**Go-around cluster:**
- C1: `hasClearance` not reset after go-around — pilot retains stale landing clearance on subsequent circuit
- C2: IFR go-around uses VFR `goAroundTask()` — pilot doesn't fly published missed approach
- C3: GOING_AROUND transmission dead code — pilot goes around silently, ATC never hears the call
- C4: `replaceChild` matches completed subtrees — go-around on second circuit corrupts first circuit's tree

**Totality violations:**
- F1-F4: `error()` in `derivePilotGoal`, `updateAfterTransmission`, `updateAfterReport`, `skipCompletedSteps` for states that were reachable but not exercised by tests
- F5: `applyFplAmendment` silently discards `AmendmentError` via `.getOrNull()`

## Root Cause 1: Mission state mixes tree structure with flat side-state

The HTN tree tracks what the pilot is doing. But `hasClearance`, `lastReportedLeg`, `activeConstraints`, `routeOverride`, `contactedOnFrequency`, `reportedVacated` track the context of what they're doing. These 6+ fields live as flat booleans/nullables on `PilotMission`, outside the tree.

On the forward path (startup → takeoff → circuit → land), this works: context accumulates and remains valid as steps advance. Go-around is the only **reversal** — the tree is replaced, invalidating approach-phase context. The go-around handler must manually reset every contextual field. Each field was added independently for its own feature. No mechanism forces them to be treated as a unit.

**Symptom**: C1 (hasClearance not reset). C4 is a variant — the tree replacement was also incomplete.

**Fix direction**: Group mission context by phase. An `ApproachContext?` record that is set on approach entry and nulled atomically on go-around. New fields added to approach context automatically get reset. "Shotgun surgery" becomes impossible because the go-around handler nulls one record, not N fields.

## Root Cause 2: The cognitive loop has an undocumented ordering contract

`pilotCognitiveDecide` runs: (1) advance completed steps, (2) generate transmissions for current step. If `isReportComplete` returns `true`, the step completes in phase 1 before phase 2 runs. The transmission is never generated.

`REPORT_READY` avoids this by returning `false` from `isReportComplete` and completing via the transmission trigger pattern. `GOING_AROUND` returned `true` because the developer wanted immediate advancement. The intent was "go around instantly" — the effect was "go around silently."

**Symptom**: C3 (dead transmission).

**Why this is easy to get wrong**: A step's completion behavior is declared in three separate places: `CompletionMode` on the `PrimitiveTask`, `isReportComplete` in the cognitive layer, and `stepTransmission` in a third function. These three declarations must be mutually consistent, but nothing enforces consistency. A REPORTED step where `isReportComplete` returns `true` is a contradiction — the step claims to be report-gated but auto-completes — and nothing catches it.

**Fix direction**: Co-locate completion and transmission declarations. A REPORTED step should carry its completion predicate and its transmission together, so contradictions are structurally impossible.

## Root Cause 3: Forward-path-first development without reversal analysis

Every feature was built and tested for the happy path. Go-around was bolted on after. But the forward path accumulated implicit assumptions:
- `hasClearance = true` means "on approach" (set by ClearedToLand, never unset)
- The first matching `TaskName.Circuit` is the active one (true with one circuit)
- `isReportComplete(GOING_AROUND) = true` means "advance immediately" (wrong in the loop)
- IFR and VFR go-arounds need different task trees (but only one was wired)

Each assumption is reasonable on the forward path. Go-around violates all of them.

**Symptom**: All of C1-C4.

**Why the golden test didn't catch it**: The golden test (2-circuit T&G stand-to-stand) completes both circuits normally. No go-around is triggered. Go-around was tested only at the unit level — "does the subtree get replaced?" — not end-to-end.

**Fix direction**: Any mission type that supports go-around must have a go-around integration test before merge. The test must exercise the full reversal: approach → go-around → re-enter circuit → approach again → land. This catches C1 (stale clearance), C3 (missing transmission), C4 (wrong subtree), and C2 (wrong task tree) in a single test.

## Root Cause 4: `error()` for reachable states

Commandment 8 ("dead programs tell no lies") says: `error()` is correct for provably impossible states. F1-F5 used `error()` for states that were merely **unused**, not impossible:
- `Emergency` transmissions are defined in the protocol. A well-typed caller CAN construct one.
- `TcasRa` reports are defined in the sealed hierarchy. They CAN appear.
- `CircuitTraining` as an active compound is structurally unlikely but not type-prevented.
- Mid-departure phases are valid `PilotPhase` variants.

The developer's mental model was "we'll never see these because no test creates them." But the type system says they're valid. The mismatch between "what the type allows" and "what the developer expects" is where false `error()` sneaks in.

**Fix direction**: The commandment 8 test — "could a well-typed caller construct this input?" If yes, handle it. If you want it to be impossible, make it unrepresentable in the types.

## Actionable changes

| # | Change | Addresses |
|---|--------|-----------|
| 1 | Group mission context by phase (`ApproachContext?`, `DepartureContext?`) | RC1 |
| 2 | Co-locate step completion + transmission declarations | RC2 |
| 3 | Require go-around integration test for every mission type | RC3 |
| 4 | Commandment 8 added to AGENTS.md | RC4 |
| 5 | CI lint: `error()` in pure functions requires a comment proving type-level impossibility | RC4 |
