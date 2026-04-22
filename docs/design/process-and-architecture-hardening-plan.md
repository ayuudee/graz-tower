# Process and Architecture Hardening Plan

Source: Root cause analysis of ultra review findings (2026-04-22).

## Problem statement

The ultra review found four critical bugs in the go-around path and five totality violations. Root cause analysis identified three actual causes:

1. AI-generated code is locally correct but globally blind — pattern-matches on happy path without reasoning about reversal invariants
2. New fields added to state classes without auditing existing mutation sites
3. No reversal integration test for the only reversal in the system

This plan addresses the causes structurally (architecture) and procedurally (how the principal agent works).

---

## Part A: Process principles for the principal agent

These go into AGENTS.md as guidance for the principal agent (the main conversation agent, not subagents).

### A1. New field, new audit

When adding a field to any data class that participates in state transitions:
1. Grep for every `.copy(` on that type
2. At each site, decide: does the new field need attention here?
3. Document the decision for non-obvious cases

Mechanical enforcement: for key state types (PilotMission, BeliefState), maintain a transition test that enumerates every field and asserts expected values after each state transition. Adding a field breaks the test.

### A2. Write the reversal before the forward path

When implementing any feature with a state transition pair (set/reset, accumulate/clear, enter/exit):
1. Identify the reversal FIRST
2. Write the reversal handler (or document that no reversal exists)
3. Then write the forward handler
4. Test the reversal end-to-end, not just the forward path

### A3. Re-read code you're building on

When extending existing code, re-read it critically before extending:
1. What assumptions does the existing code make?
2. Does the new feature violate any of those assumptions?
3. What state transitions exist that the new feature interacts with?

### A4. Self-assessment before external review

Before launching review agents, the principal agent must perform a self-assessment of the work against defined criteria (see Part C). This assessment is for the principal agent's own use — it is NOT passed to review agents (clean context principle).

The bar: the self-assessment should be thorough enough that subsequent review does not turn up anything that a staff engineer of reasonable diligence could have been expected to catch. Review agents may still find domain-specific issues (ATC operational details, subtle type-system gaps), but architectural bugs, missing tests for known features, and totality violations should be caught in self-assessment.

---

## Part B: Immediate implementation

### B1. `resetForGoAround()` with exhaustive field test

Create a function that encapsulates the go-around state reset:

```kotlin
fun PilotMission.resetForGoAround(now: SimTime): PilotMission = copy(
    activeConstraints = emptySet(),
    lastReportedLeg = null,
    hasClearance = false,
    reportedVacated = false,
    routeOverride = null,
    stepEnteredAt = now,
    // navigationMode: preserved (pilot is still VFR/IFR)
    // goal: preserved
    // root: handled separately (subtree replacement)
    // contactedOnFrequency: preserved (still on the same frequency)
)
```

Test: enumerate every field of `PilotMission` by name. For each field, assert the expected post-go-around value. When a new field is added, the test fails until the developer decides the go-around behavior.

### B2. Categorize PilotMission fields

Add documentation comments to `PilotMission` categorizing each field:

- **Structural** (set at creation, rarely mutated): `goal`, `root`, `navigationMode`
- **Phase-local** (reset on go-around): `hasClearance`, `lastReportedLeg`, `reportedVacated`, `stepEnteredAt`
- **Cross-cutting** (set by ATC, reset depends on context): `activeConstraints`, `routeOverride`, `contactedOnFrequency`

This categorization informs `resetForGoAround()` and future field additions.

---

## Part C: Principal agent self-assessment criteria

Before any design, plan, or significant implementation is considered complete, the principal agent evaluates against these criteria:

1. **Totality**: Every sealed `when` is exhaustive. No `error()` for type-valid states. No `else` that swallows.
2. **Reversal completeness**: Every state transition that can be reversed (go-around, clearance cancellation, override clear) has been audited for complete state reset.
3. **Interaction coverage**: Code that interacts with other components (cognitive loop + transmission, route planner + kinematic layer) has been traced through the interaction path, not just tested in isolation.
4. **Test coverage for known features**: Every implemented feature has at least one test that exercises its primary behavior. Go-around is tested end-to-end, not just at the subtree level.
5. **New-field audit**: Every field added to a state class has been checked against every mutation site.
6. **Operational correctness**: Routes, transmissions, and clearances match real-world ATC procedures (defer to domain review agents for specifics, but the principal agent should catch obvious errors).
7. **Error handling honesty**: `error()` only for provably impossible states. `Either`/`Option` for everything else. No `.getOrNull()` that discards diagnostic information without documentation.

The principal agent records this self-assessment internally. It is NOT shared with review agents — review agents receive clean context to avoid confirmation bias.

---

## Part D: Review orchestration

### D1. Review orchestrator agent

A new agent type (`review-orchestrator`) whose job is to select and execute the appropriate review type(s) for a given piece of work. The principal agent invokes it when judgment says review is needed — not at every step.

Available review types and when to use them:

| Type | When | What it checks |
|------|------|----------------|
| **ATC law** | Regulatory claims, clearance logic, separation rules | ICAO/SERA/CAP 413 compliance, citation accuracy |
| **ATC phraseology** | Transmission content, readback structure | RT correctness, CAP 413 phraseology |
| **ATC general knowledge** | Operational model, sequencing, handoffs | Does the sim match how ATC actually works? |
| **FP** | Code changes, type design, API design | Purity, totality, Arrow usage, algebraic design |
| **Test** | Test changes, coverage questions | Do tests provide real confidence? Gaps? |
| **Adversarial** | After implementation, looking for bugs | What inputs break this? What state combinations are dangerous? |
| **Red-team** | After analysis or design, challenging conclusions | Are the conclusions right? What's the alternative explanation? |
| **Impact** | Before/after significant changes (new features, refactors, design decisions) | Exhaustive impact analysis — see D2 |

The orchestrator selects types based on the nature of the work:
- Pure code change → FP + Test
- ATC-visible behavior change → ATC GK + Law + Phraseology
- New feature or refactor → Impact (expensive, used sparingly)
- Post-implementation quality gate → Adversarial
- Post-analysis validation → Red-team

### D2. Impact review

The most thorough review type. Used sparingly for significant changes. The impact reviewer examines:

1. **Against project goals**: Does this change advance or hinder the project's objectives? Does it create technical debt? Is it aligned with the architectural direction?
2. **Against system architecture**: Does this change respect module boundaries? Does it introduce coupling? Does it create new implicit invariants? Does it make future work harder?
3. **Against operational correctness**: Does this change affect how simulated ATC works? Could it produce operationally dangerous states?
4. **Against test architecture**: Are the tests for this change testing the right things? Do they provide confidence proportional to the risk?
5. **Reversal and interaction analysis**: What state does this change create/modify? What operations reverse that state? Are the reversals complete? What other components interact with the changed state?
6. **Failure mode analysis**: What happens when this change fails? Are errors propagated, logged, or swallowed? Is the failure mode safe (dead program) or dangerous (silent corruption)?

The impact reviewer operates with full codebase access and should read all affected files, not just the diff.

---

## Part E: Future architecture (documented, not implemented now)

### E1. Clearance token scoped to tree node

Instead of `hasClearance` as a flat boolean on PilotMission, model it as a token attached to the circuit compound task node. When the compound is replaced (go-around), the token is automatically discarded. Generalizes: any state meaningful only within a subtree should be scoped to that subtree.

Implementation sketch:
```kotlin
data class CompoundTask(
    val name: TaskName,
    val children: List<TaskNode>,
    val context: TaskContext = TaskContext.EMPTY,  // phase-local state
)

data class TaskContext(
    val hasClearance: Boolean = false,
    val lastReportedLeg: LegName? = null,
    // ... other phase-local fields
) {
    companion object { val EMPTY = TaskContext() }
}
```

Go-around replaces the compound task → context is discarded automatically. No `resetForGoAround()` needed — the type system prevents the bug.

Trade-off: every `copy()` that modifies phase-local state must navigate to the active compound's context. More complex at each mutation site, but eliminates the entire class of "forgot to reset on reversal" bugs.

### E2. Step completion and transmission co-declaration

For REPORTED steps, co-locate the completion predicate and transmission:

```kotlin
data class ReportedStepSpec(
    val step: MissionStep,
    val transmit: (AircraftState, PilotMission) -> PilotTransmission?,
    val completeWhen: (PilotMission) -> Boolean,
)
```

The cognitive loop consults the spec, ensuring completion and transmission are consistent. A step that auto-completes (`completeWhen` returns true) cannot also have a transmission — the spec makes this contradiction visible at construction time.

---

## Verification

After implementing B1 and B2:
```bash
./gradlew :sim:cleanJvmTest :sim:jvmTest
```

The exhaustive field test should break if any new field is added to PilotMission without updating the test.
