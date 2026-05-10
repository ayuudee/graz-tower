# Commandments

These are non-negotiable. Every agent — main conversation, subagent, review agent — must follow them. **If you are a subagent, confirm you have read the commandments before proceeding.**

## 1. No corners cut

Do not take shortcuts that defer problems, hide failures, or create future surprises. If something cannot be done correctly right now, either do it correctly or make the incomplete state **loudly visible** (throw an exception, fail the test, leave a compile error). Silent workarounds — skip lists, `@Suppress`, `TODO` comments that disable checks, catch-all `else` branches that swallow unexpected cases — are forbidden.

**Test example**: if a new production rule isn't covered by an exhaustive condition-space test, expand the condition space. Do not add the rule ID to an exclusion list.

**Code example**: if a sealed `when` gains a new branch, handle it. Do not add `else -> Unit` or `else -> null`. If the handler isn't written yet, `else -> error("${branch::class.simpleName} not yet handled")`.

## 2. No half-baked work

Every commit must leave the codebase in a state where all tests pass and no known-incorrect behavior is silently accepted. If a feature is partially implemented, the unfinished part must fail loudly — not pass silently by being excluded from checks.

## 3. Throw on the unimplemented

When code encounters a state it genuinely cannot handle AND the state is **provably impossible at the type level** (see Commandment 8), it must throw. `error()` is correct for states a well-typed caller cannot construct. Returning `null`, `emptyList()`, or a no-op is only correct when the absence is a defined, documented part of the domain.

If the type system allows the state but the code doesn't handle it yet, use typed errors (`Either<NotYetImplemented, T>`) or explicitly documented no-ops — not `error()`. The distinction: `error()` means "impossible," `Left(NotYetImplemented)` means "possible but deferred."

## 4. Tests prove the real job

Tests must exercise real behavior, not structural properties that the type system or compiler already guarantees. If a test can only pass by checking something the code already enforces, it adds no confidence and should not exist. When a test needs to exclude cases, that is a signal that the test's scope is wrong — fix the scope, don't carve exceptions.

## 5. The pilot owns the plan

The pilot agent receives a high-level goal and plans how to achieve it. The test harness does not decompose the goal, stitch phases together, or swap goals mid-flight. If the test needs to do this, the pilot's planning capability is incomplete — fix the pilot, don't work around it in the test.

## 6. Protocol is source of truth

Controller and pilot behavior must be traceable to ATC regulations (ICAO, SERA, CAP 413). Every rule carries regulation references. Every pilot transmission follows standard phraseology. Invented behaviors that have no regulatory basis are bugs, not features.

## 7. Cite your sources for law and phraseology

When making claims about ATC law, regulations, or RT phraseology, always provide the specific source: document, edition/date, section/paragraph. For example: "CAP 413 para 4.50" or "ICAO Doc 9432 §5.9.5". Do not state regulatory facts without a citation. Once cited, verify the citation is accurate — check the actual text if available in `research/txt/` or the wiki. An uncited regulatory claim is an unverified claim.

## 9. Plans and designs are review-aware by construction

Any agent producing a plan or design must explicitly address the concerns that review agents will raise — as part of the plan itself, not as a separate step. A plan that ignores reviewable dimensions is incomplete.

Every non-trivial plan or design must include a **Review considerations** section that explicitly covers:

- **FP / type safety**: Are all `when` expressions exhaustive? Are new fields total? Are new state transitions reversible? Are error paths typed (`Either`/`Option`) rather than thrown?
- **Test architecture**: What tests does this require, at what level? What invariants must hold? What scenarios exercise the reversal?
- **Impact**: What does this couple? What does it make harder or easier? What are the failure modes? What reverses it and is that reversal complete?
- **Operational correctness** (when ATC behaviour is involved): Does this match real-world procedure? Which regulation applies?

The section need not be exhaustive if a dimension is genuinely not applicable — but the absence must be deliberate and stated, not an oversight. A plan that silently omits FP, test, or impact concerns will be sent back.

## 8. Dead programs tell no lies

If the program reaches a state it genuinely cannot handle, it must crash — not silently recover, not return a plausible default, not log and continue. A crash with a clear message is infinitely more useful than silent corruption. `error()` is correct for **provably impossible** states.

However: **if the type system allows a state, it is reachable**. Do not use `error()` for states that are merely unused or not-yet-exercised — that is a lie about impossibility. If you believe a state is impossible, make it **unrepresentable in the types** (sealed hierarchies, non-nullable fields, smart constructors). If the types allow it, handle it — either functionally (`Either`, `Option`) or by documenting the operational semantics of the "unexpected" case.

The test: before writing `error("X should not happen")`, ask: "could a well-typed caller construct this input?" If yes, it CAN happen, and `error()` is wrong.

---

# Principal Agent Responsibilities

The principal agent (the main conversation agent, not subagents) has additional responsibilities beyond the commandments.

## Self-assessment before review

Before launching review agents (via the review orchestrator or directly), the principal agent must perform a self-assessment of the work against these criteria:

1. **Totality**: Every sealed `when` is exhaustive. No `error()` for type-valid states. No `else` that swallows.
2. **Reversal completeness**: Every state transition that can be reversed has complete state reset. The reversal was considered BEFORE the forward path was built.
3. **Interaction coverage**: Code that interacts with other components has been traced through the interaction path, not just tested in isolation.
4. **Test coverage for known features**: Every implemented feature has at least one test that exercises its primary behavior, including reversals.
5. **New-field audit**: Every field added to a state class has been checked against every mutation site.
6. **Operational correctness**: Routes, transmissions, and clearances match real-world ATC procedures.
7. **Error handling honesty**: `error()` only for provably impossible states. `Either`/`Option` for everything else.

The bar: subsequent review should not turn up anything that a staff engineer of reasonable diligence could have been expected to catch. Domain-specific subtleties (ATC operational details, regulatory edge cases) are acceptable review findings. Architectural bugs, missing tests, and totality violations are NOT.

**This self-assessment is for the principal agent's own use. It is NOT passed to review agents.** Review agents receive clean context to avoid confirmation bias.

## Process principles

When implementing features:

- **Impact assessment before implementation**: For every non-trivial plan or design decision, run the impact agent BEFORE writing code. The assessment identifies architectural implications, coupling risks, reversal requirements, and failure modes before they are baked in. This is not optional for non-trivial work. Trivial = a one-liner fix or renaming; everything else warrants impact assessment.
- **New field, new audit**: When adding a field to a state data class, grep for every `.copy(` on that type and check whether the new field needs attention at each mutation site.
- **Reversal before forward path**: When implementing a state transition pair (set/reset), write the reversal handler FIRST.
- **Re-read code you're building on**: When extending existing code, re-read it critically. Ask what assumptions it makes that the new feature might violate.
- **Use review agents at your judgment**: Invoke the review orchestrator when the work warrants it — not at every step, but whenever complexity, risk, or uncertainty suggests external review would add value.

---

# Environment

Nix development shell (`nix-shell` or direnv). JDK 21, Gradle 8, Lean 4, TLA+.
Kotlin Multiplatform targeting JVM. Modules: `protocol`, `core`, `migration`.

# Commands

```
./gradlew :protocol:allTests :core:allTests   # run all tests
./gradlew detekt                               # static analysis
./gradlew :core:jvmTest --tests '*.ActiveClearanceEngineTest'  # single test class
./gradlew build                                # full build
```

# X-Plane Data And Tooling

- Parsers for X-Plane/OFM source formats live under `migration/src/commonMain/kotlin/xyz/easiersaid/twr/migration/{aptdat,cifp,ofmx}/`.
- JVM file-reading helpers currently live under `migration/src/jvmMain/kotlin/xyz/easiersaid/twr/migration/{aptdat,ofmx}/`.
- Small checked-in parser fixtures live under `migration/src/commonTest/resources/airports/`, `migration/src/commonTest/resources/cifp/`, and `migration/src/commonTest/resources/ofmx/`.
- Larger local datasets live under `data/`: `data/cifp/LOWG.dat`, `data/ofm/austria/ofmx_extracted/ofmx_lo/isolated/ofmx_lo.ofmx`, the original OFM zip archives under `data/ofm/austria/`, and reference charts under `data/charts/LOWG/`.
- `data/airports/` exists for raw airport files, but the current checked-in apt.dat samples are in `migration/src/commonTest/resources/airports/`.
- Hand-authored airport geometry work currently lives under `cad/airports/` (`lowg.dxf`, `lowg_circuits.dxf`).
- `migration/src/commonTest/kotlin/xyz/easiersaid/twr/migration/ofmx/OfmxFullFileTest.kt` expects the full Austria OFMX file at `data/ofm/austria/ofmx_extracted/ofmx_lo/isolated/ofmx_lo.ofmx`.

# Code Style

- Functional style. Arrow for typed errors (`Either`, `NonEmptyList`). Pure functions, total functions, exhaustive `when`, immutable data only. No `var`, no mutable collections, no side effects in domain logic. See `detekt.yml` for enforcement.
- Ports and adapters. Focus on good/deep modularisation. 
- When building stuff, we tend to: make it work, make it right, make it fast. Figure out which of those is relevant to a piece of work and decide the appropriate way to go about it. 

# Testing

Follow the principles in `docs/test-standards.md`. In particular:

- Prefer integration tests that exercise the full pipeline over isolated unit tests.
- Unit test only when there is a formal, independent oracle of correctness (e.g. ICAO
  standards for frequency ranges, squawk codes) or clear business value.
- Use the type system to eliminate tests: if the compiler prevents it, don't test it.
- If you can't articulate the business value of a test, don't write it.

## Golden tests (G0, G1, G1 minimal, G2, G3a, G3a-obstruction)

Six integration tests serve as the runtime golden anchors for end-to-end
ATC flow. All follow the same shape: a single `@Test` method, a fixture-
driven load, a deterministic event run, the run is the test, the assertions
are what the run produced.

- **G0 — `LowgGoldenTest` (`sim/jvmTest`)**: single-aerodrome circuit
  training. C172 OE-ABC files at LOWG, taxis to RWY 16C, takes off,
  flies the circuit pattern, lands, taxis back to a stand. Pins the
  intra-aerodrome handoff chain (GROUND → TOWER → arrival → GROUND)
  and the full clearance lifecycle. **Must remain green at all times.**

- **G1 — `G1TwoAircraftCircuitsTest` (`sim/jvmTest`)**: single-aerodrome,
  **two-aircraft** circuit training. Two C172s (OE-ABC + OE-DEF) start
  at adjacent LOWG GA stands and fly two circuits each. B's mission-
  start is offset by 2 sim-minutes so the lead-trail ordering is
  structural; the single-runway duty machine then serializes them
  (A holds the runway across her circuit-training session, releasing
  to B after she vacates). **Green** as of fn-8.3 Phase 4.
  Pins per-aircraft outcomes (both complete + parked), causal
  partial-orders (taxi-clearance order, single-runway gate, A's
  `ClearedToLand` precedes B's, A's vacate precedes B's first
  Downwind), the wake-category sanity (both C172 / L), and the
  fn-8.3-acceptance multi-aircraft commitment-stage closure
  invariants (vacate / `BacktrackRunway` coordinations close;
  `RunwayDutyState.holder` null after both vacate). Time band
  tightened to ±15% of the observed wall (~50 sim minutes) per
  fn-8.3 decision #11. Closure history: A-side wedge closed Phase 2
  round 1 (`33833a2`, `a6249c9`) via sticky
  `touchedDownDuringCommitment` + `pilotReadyDuringCommitment`;
  Phase 3 round 1 (`bddff1b`-`8e0a3ec`) closed B4 (DEP-CIRCUIT-
  COMPLETE wedge) via same-aircraft pilot radio-busy tracking,
  strip-based circuit-traffic recognition, and ARR-LAND default
  flip; Phase 4 closed B5-α via the commitment-scoped
  `observedReportsDuringCommitment` sticky witness gating ARR-LAND /
  ARR-LAND-TNG on the pilot's pre-clearance position call.

- **G1 minimal — `G1TwoAircraftMinimalSpec` (`sim/jvmTest`)**: scope-
  narrower for G1, two C172s with `circuits=1` (full-stop only — no
  T&G mid-flip). Pins the multi-aircraft commitment-stage closure
  invariants (vacate coordinations close, runway-duty holder
  released, B receives a runway slot, A's `ClearedToLand` precedes
  B's) at the smaller scenario shape. Catches regressions to the
  multi-aircraft serialization path before they reach G1's
  `circuits=2` scope.

- **G2 — `G2CrossAerodromeVfrTest` (`sim/jvmTest`)**: cross-aerodrome
  VFR transit. C172 OE-XYZ files VFR LOWG → LJMB, taxis at LOWG, takes
  off, follows the published transit route to LJMB's first contact REP
  (OSMOT), autonomously contacts LJMB Tower, joins the LJMB pattern,
  lands, taxis to a stand. Cross-aerodrome handoff is modelled as
  **release + procedure-following + autonomous initial contact** — not
  a peer handoff. The doctrine is enforced by
  `FirewallNoCrossAerodromeHandoffTest` (HandoffTarget sealed leaves
  pinned to `{Peer, Released}` via reflection) and the
  cross-aerodrome staffing shape is pinned by
  `FixtureAerodromeStaffingDoctrineSpec`.

- **G3a — `G3aPilotTrainedGoAroundTest` (`sim/jvmTest`)**: single-
  aerodrome, single-aircraft VFR pilot-trained go-around as circuit-
  training outcome. C172 OE-ABC at LOWG flies a two-circuit mission
  with `HighLevelGoal.CircuitTraining(outcomes = listOf(GoAround,
  FullStop))` — circuit 1 is explicitly authored as a planned
  go-around at short-final, circuit 2 is a full-stop landing. The
  trigger is mission-tree authorship at compile time (per
  `feedback_world_only_test_triggers.md`) — no event injection, no
  rigged decision. Pins per fn-11.2's three-layer pattern:
  **causal partial-order** (`ClearedToLand(c1) ≺ Report(GoingAround)
  ≺ ClearedToLand(c2) ≺ Report(RunwayVacated)`),
  **sticky-witness regression via GA-POST-CLEAR** (exactly one
  stage transition `{LandingClearanceIssued, AwaitLandedObserved}
  → AwaitDownwind` observed; post-regression
  `touchedDownDuringCommitment` and `observedReportsDuringCommitment`
  are reset per fn-8.3's witness-reset machinery), **kinematic
  non-event** (no `LandingRoll` phase before the GoingAround
  transmission), plus the R7 vacate-coordination closure pin
  (no leftover `AfterLandingVacateVia` / `BacktrackRunway` entries
  after circuit 2's landing). Time band tightened to ±15% of the
  observed wall (~1393 s = ~23.2 sim minutes) per fn-8.3 decision
  #11. Closes `wiki/design-decisions/2026-04-22-root-cause-go-around-
  and-totality.md`'s open ask: "Any mission type that supports
  go-around must have a go-around integration test before merge."
  Doctrinally faithful to CAP 413 §4.66/§4.67/§4.68 and ICAO Doc
  4444 §12.3.4.18.

- **G3a-obstruction — `G3aRunwayObstructionTest` (`sim/jvmTest`)**:
  single-aerodrome, single-aircraft VFR ATC-instructed go-around
  triggered by a **world-authored runway obstruction**. C172 OE-ABC at
  LOWG flies a single planned circuit
  (`HighLevelGoal.CircuitTraining(outcomes = listOf(FullStop))`);
  the recovery circuit is provided by `handleGoAround`'s mission-tree
  rewrite. The test authors `runway.obstruction =
  RunwayObstruction(clearsAt = now + 60.seconds)` one-shot via
  `runUntilWithStateTrace`'s `onAfterEvent` hook when the aircraft is
  on `phase=Final` with a post-clearance commitment stage
  (`LandingClearanceIssued` or `AwaitLandedObserved`). The sim's
  per-cycle world-diff producer derives a
  `ControllerEvent.RunwayObstructionDetected`, the tower's belief folds
  it into `BeliefState.runwayObstructions`, the reactive
  `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` rule fires with `Immediate`
  advancement back to `AwaitDownwind`, the controller transmits
  `GoAround` + the mandatory `RunwayObstructionInformation` companion
  (reason-on-radio per ICAO §7.4.1.4.1(c)), and the pilot's
  ATC-initiated GA recognition + Tick A / Tick B (fn-12.2) executes
  the recovery. Pins per fn-11.2's three-layer pattern extended with
  separated decision-cycle / transmission-start timestamps:
  **decision-cycle causal partial-order** (`Detected.decisionTime <=
  GoAround.decisionTime == Stage_regression(<from-stage> →
  AwaitDownwind).time`; `Cleared.decisionTime <
  ClearedToLand(recovery).decisionTime`),
  **radio-transmission partial-order**
  (`ClearedToLand(c1) ≺ GoAround.txStart < RunwayObstructionInformation
  .txStart < Report(GoingAround).txStart < ClearedToLand(recovery) <
  Report(RunwayVacated)` — strict `<` between GoAround and companion
  because `applyControllerOutputs` serializes outputs on the same
  frequency), **sticky-witness regression via `Immediate` advancement**
  (NOT via `GA-POST-CLEAR` interrupt — by the time `Report(GoingAround)`
  arrives, the stage is already `AwaitDownwind`; exactly one
  `<from-stage> → AwaitDownwind` transition with `<from-stage> ∈
  {LandingClearanceIssued, AwaitLandedObserved}`; post-regression
  `touchedDownDuringCommitment` and `observedReportsDuringCommitment`
  reset; `obstructionGoAroundIssuedThisAttempt` no-refire witness set),
  **kinematic non-event** (no `LandingRoll` or `Vacating` phase before
  `Report(GoingAround)`), **per-controller event scoping** (exactly
  one `None → Some(...)` and one `Some → None` transition in the
  TOWER's `runwayObstructions[16C]` belief slice per fn-12 Decision
  #3), **companion transmission** (`RunwayObstructionInformation`
  emitted in same controller-output cycle as `GoAround` with matching
  `runway` + `clearsAt`), R7 vacate-coordination closure after the
  recovery landing, time band ±15% of the observed wall (~1399 s =
  ~23.3 sim minutes). World-only test trigger per
  `feedback_world_only_test_triggers.md` — no
  `ControllerEvent.RunwayObstructionDetected` injection, no
  `BeliefState` mutation. Doctrinally faithful to ICAO Doc 4444
  §7.4.1.4.1 (runway obstruction GA mandate) + §8.9.6.1.8 (reason on
  radio) + CAP 413 §4.65 (missed-approach phraseology). Closes the
  third reactive-GA path: alongside G3a-trained (pilot-trained) and
  the existing self-initiated GA (fn-10 era), G3a-obstruction
  exercises the ATC-instructed reactive path.

All six tests follow the no-corners-cut rule: a failing golden test is
documented in its KDoc with the specific blocker and stays loudly
failing. No `@Disabled`, skip-list, or exclusion set.

# Project Structure

```
protocol/   Domain types, instructions, smart constructors, instruction metadata
core/       Clearance resolution, completion evaluation, supersession, world model
migration/  Data parsers (apt.dat, OFMX, CIFP) and world-building tools
research/   Formal methods (Lean proofs, TLA+ specs) — not built by Gradle
docs/       Design documents and standards
wiki/       Shared knowledge base — domain knowledge, data sources, design decisions
```

# FM Notes

- When changing `research/fm`, keep `research/fm/README.md`, `research/fm/PROJECT_STATUS.md`, and the active scope note aligned with the actual theorem status.
- Prefer widening FM by small closed slices on the current-shape greenfield boundary; treat older atomic/legacy bridge work as opt-in, not the default path.
- For recurring `research/tools/r1` overnight FM runs, treat `research/fm/r1-smoke/` as the local ignored operations workspace: check the current frontier against `research/fm/lean/`, refresh the local seed snapshot from the current Lean tree, regenerate queue artifacts instead of hand-editing stale queue files, launch the queue detached, watch the first 10-15 minutes for infrastructure or repeated early failures, then leave it alone. Preserve historical `runs/` unless there is a specific reason to reset them.

# Project Plan

`.plan` in the repo root is the canonical backlog of known issues and deferred work.

**On every commit:**
- Scan `.plan` for items resolved by the commit and mark them `DONE`.
- If the commit defers something or surfaces a new known issue, add it to `.plan` before committing.

**During implementation:**
- When a review agent raises a finding that won't be fixed immediately, add it to `.plan` and note the reason for deferral.
- When a design decision explicitly parks something, add it to `.plan` in the appropriate section (IFR wiring gaps, controller backlog, etc.).
- When an issue is confirmed as by-design rather than a gap, move it to the "By design / accepted" section with a one-line rationale.

**Format:** each item has a short ID (e.g. `B3`, `IFR-1`), a one-paragraph description of what's wrong and where, and `Impact: H/M/L | Effort: H/M/L`.

# Wiki

The `wiki/` directory is a shared knowledge base maintained by both human and AI contributors.

- **Update the wiki** when: completing a stream of work, making a design decision, discovering domain knowledge, ending a session, or committing significant changes.
- **Domain pages** (`wiki/domain/`) are living documents — keep them current.
- **Design decisions** (`wiki/design-decisions/`) are point-in-time records, dated, not updated retroactively. New decisions supersede old ones.
- **Data source pages** (`wiki/data-sources/`) document what we have, what it contains, and known gaps.
- When committing, include wiki updates as part of the commit if relevant content changed.

<!-- BEGIN FLOW-NEXT -->
## Codex Flow-Next

Codex sessions in this project use Flow-Next for task tracking. Use `.flow/bin/flowctl` instead of ad hoc markdown task lists for Flow-Next epics and tasks.

Project-specific note: `.plan` remains the canonical backlog for known issues and deferred work. `.flow/` is the execution state for planned Flow-Next work.

**Quick commands:**
```bash
.flow/bin/flowctl list # List all epics + tasks
.flow/bin/flowctl epics # List all epics
.flow/bin/flowctl tasks --epic fn-N # List tasks for epic
.flow/bin/flowctl ready --epic fn-N # What's ready
.flow/bin/flowctl show fn-N.M # View task
.flow/bin/flowctl start fn-N.M # Claim task
.flow/bin/flowctl done fn-N.M --summary-file s.md --evidence-json e.json
```

**Creating a spec** ("create a spec", "spec out X", "write a spec for X"):

A spec = an epic. Create one directly; planning commands then break the epic into executable tasks.

```bash
.flow/bin/flowctl epic create --title "Short title" --json
.flow/bin/flowctl epic set-plan <epic-id> --file - --json <<'EOF'
# Title

## Goal & Context
Why this exists, what problem it solves.

## Architecture & Data Models
System design, data flow, key components.

## API Contracts
Endpoints, interfaces, input/output shapes.

## Edge Cases & Constraints
Failure modes, limits, performance requirements.

## Acceptance Criteria
- [ ] Testable criterion 1
- [ ] Testable criterion 2

## Boundaries
What's explicitly out of scope.

## Decision Context
Why this approach over alternatives.
EOF
```

After creating a spec, choose the next step:
- `$flow-next-plan <epic-id>` - research and break into tasks
- `$flow-next-interview <epic-id>` - deep Q&A to refine the spec

**Rules:**
- Use `.flow/bin/flowctl` for all Flow-Next task tracking.
- Re-anchor by reading the current spec and task status before each task.

**More info:** `.flow/bin/flowctl --help` or read `.flow/usage.md`
<!-- END FLOW-NEXT -->
