# ICAO 9432 chunk 01 — drive expected-gap units to closure

## Conversation Evidence

Recent user turns and prior-conversation artifacts in scope:

> "The idea is that this other agent writes the tests and you will do
>  the work, via flow-next, to complete each chunk. … There are two
>  actors, you are the test completer, the other one is the test
>  writer."  [user]

> Scope: "Chunk-specific blockers only" — tackle the 4 expected-gap
> units; leave PHRASE-1 / POLICY-1 deferred.  [user]

> Role-resolution: "Build blockers + author 4 missing tests" — the
> Test Completer's chunk-completion role explicitly covers building
> named blocker primitives + authoring expected-gap tests.  [user]

AGENT_DIALOGUE.md (amended 2026-05-20): Test Writer authors per
chunk; Test Completer drives to closure. Refined this epic:
chunk-completion includes building blocker primitives + authoring
expected-gap tests, then landing each unit in `covered-green` /
`covered-red`.  [paraphrase]

Chunk 01 final state (fn-47 coverage_report.md): 6 covered-green,
4 expected-gap, 7 phraseology-later, 2 policy-blocked, 1
not-applicable.  [paraphrase]

The 4 expected-gap units and their named blockers:

- `communications_2_8_1_en::0a964f42b6100596` — COMMS-1 — doubt /
  repetition-request (§2.8.1.4; **shall**)
- `readback_2_8_3_en::ac9111d240cfd2c2` — FN33-MODEL-1 — clearance
  pacing / workload (§2.8.3.2; **should** — advisory)
- `transfer_communications_2_8_2_en::40382df156ad071e` —
  FN44-GAP-1 — controller-advised frequency transfer (§2.8.2.1;
  **shall**)
- `transfer_communications_2_8_2_en::b49ae03cbbb2d538` —
  FN44-GAP-2 — pilot-notified frequency change, fallback (§2.8.2.1;
  **shall**)  [paraphrase]

Reuse surfaces (do **not** redeclare):

- `EvidenceFactPayload.FrequencyTransfer(mode = ControllerAdvised |
  PilotNotifiedAbsentAdvice, …)` (`EvidenceFacts.kt:142-148, :206-209`).  [paraphrase]
- `EvidenceGaps.ControllerAdvisedFrequencyTransferProjection` and
  `…PilotNotifiedFrequencyChangeProjection` with typed
  `PlanItem("FN44-GAP-1"/"FN44-GAP-2")`
  (`EvidenceSourceCatalog.kt:215-233`).  [paraphrase]
- `ICAO9432.TransferCommunications.ControllerAdvisedFrequencyChange`
  and `PilotNotifiesAbsentAdvice` (`EvidenceSourceCatalog.kt:152-167`).  [paraphrase]
- `EvidenceAuditOutcome` sealed interface: `Pass` / `Fail` /
  `Vacuous` / `ExpectedGap(EvidenceGapId, reason)`
  (`EvidenceDsl.kt:20-24`). This epic adds a fifth leaf — `Advisory`
  — for FN33-MODEL-1's "should" semantics.  [paraphrase]
- `EvidenceFactSet` return type for adapter projections
  (`EvidenceFacts.kt:233`).  [paraphrase]
- `EvidenceSourceCatalog.All: Set<EvidenceSourceRef>` (line 245) is
  the validation set; `validateAgainstRegistry()` (line 255-258)
  walks `All`. Refs declared but not in `All` are not validated.  [paraphrase]
- `SimEvidenceBuilder` API: `observe { run: () -> EvidenceFactSet }`
  (line 128) + `source(id: String, build:
  AuditEvidenceCaseBuilder.() -> Unit)` (line 132-136). Inside the
  case block, `cites(vararg refs: EvidenceSourceRef)` (line 251-256)
  attaches typed refs; `expect { … -> EvidenceAuditOutcome }`
  (line 263) returns the case outcome. `ProtocolEvidenceBuilder`
  (line 89) has no `source` — only `structuralReadback` and
  `sample`.  [paraphrase]

Catalog gaps to add: COMMS-1 source
`…::communications_2_8_1_en::0a964f42b6100596` and FN33-MODEL-1
source `…::readback_2_8_3_en::ac9111d240cfd2c2`. Both must be added
to `EvidenceSourceCatalog.All` or they won't validate.  [paraphrase]

## Goal & Context

Drive chunk 01 to honest closure as the first Test-Completer epic
under the two-actor model. Resolve the 4 chunk-specific
expected-gap units by building their named blocker primitives,
authoring source-mapped tests, and landing each in `covered-green`
or `covered-red`. PHRASE-1 / POLICY-1 stay deferred as cross-chunk
infrastructure (named, visible debt in `.plan`).  [user, paraphrase]

**Anticipated outcome distribution** (not contractual — both
terminal states are acceptable per R6, but the plan should be
honest about likely landings):

- FN44-GAP-1, FN44-GAP-2 → likely **covered-red**. Sim does not
  currently emit `ContactFrequency` instructions or
  `RequestFrequencyChange` transmissions (per repo-scout; G2 models
  cross-aerodrome progression as release + autonomous first
  contact). Production-repair epics will track the sim modelling.
- COMMS-1 → likely **covered-red**. Sim has no reception-quality
  signal infrastructure; adapter returns empty `EvidenceFactSet`;
  test asserts honest `Fail`. Production-repair adds reception-doubt
  signal modelling.
- FN33-MODEL-1 → likely **covered-green**. Sim has phase signals
  (`TaxiRoll`, `LineUp`, `TakeoffRoll`); adapter observes clearances
  issued during these windows; test produces `Advisory` outcomes
  when violations observed. `assertNoFailures` passes because
  `Advisory` is not `Fail`.

The chunk closure entry records the actual distribution; the
spawned repair epics ARE the named debt that closes the chunk
honestly under "no deferment, no surprises".  [inferred]

This is the dress rehearsal for the chunk workflow. It must leave a
written handoff in AGENT_DIALOGUE.md so the Test Writer can begin
chunk 02 without waiting.  [inferred]

## Quick commands

```bash
# Chunk-01 evidence tests (sim + controller)
./gradlew-nix :sim:jvmTest --tests "*.Icao9432Chunk01*EvidenceTest"
./gradlew-nix :controller:jvmTest --tests "*.Icao9432*ConformanceSpec"

# Source-citation regex scan (SourceUnitRef("...") string literals only)
./gradlew-nix :controller:jvmTest --tests "*.SourceUnitCitationValidationTest"

# Catalog validation (typed EvidenceSourceRef constants registered in All)
./gradlew-nix :sim:jvmTest --tests "*.EvidenceSourceCatalog*"

# Full chunk-01 verification (closure step)
./gradlew-nix :sim:jvmTest :controller:jvmTest --tests "*Icao9432*"

# Closure gate — two explicit commands
./gradlew-nix detekt
./gradlew-nix build
```

## Architecture & Data Models

Inputs:

- Chunk 01 artifacts at
  `research/tools/requirements-spike/quality/icao9432_programme/chunk_01_comms_readback_transfer/`.  [paraphrase]
- `.plan` blocker entries: COMMS-1, FN33-MODEL-1, FN44-GAP-1,
  FN44-GAP-2.  [paraphrase]
- Existing evidence DSL / audit core (fn-43 / fn-44):
  `EvidenceFacts.kt`, `EvidenceDsl.kt`, `EvidenceSourceCatalog.kt`.  [paraphrase]
- Accepted source-unit registry JSON at
  `research/tools/requirements-spike/registry/ollama_first/candidates/icao9432-extracted/<section>/<canonicalId>.json`.  [paraphrase]
- ICAO 9432 4th ed 2007 extracted text at
  `research/txt/icao9432-extracted.txt`.  [paraphrase]

Outputs:

- **FN44-GAP-1 / FN44-GAP-2** — NO new sealed types. Reuses
  existing `FrequencyTransfer` payload, gap IDs, source refs. New:
  adapter projections in `EvidenceFactAdapters`, a
  `frequencyTransfer(aircraftId)` selector on
  `EvidenceExpectContext`, and a paired source-mapped sim test that
  asserts the expected `Fail` outcomes (likely covered-red) or
  `Pass` (if sim emits the relevant transmissions).  [paraphrase]
- **COMMS-1** — One new sealed leaf
  (`EvidenceFactPayload.ReceptionDoubt`) + new `EvidenceFactKind`
  value + new adapter + new selector + new source-mapped sim test.
  The test exercises the actual sim behaviour (no reception-quality
  signals → empty `EvidenceFactSet` → audit reports `Fail`); test
  method asserts the expected `Fail` is in the report (covered-red
  landing).  [paraphrase]
- **FN33-MODEL-1** — One new sealed leaf
  (`EvidenceFactPayload.ClearancePacing`) + new `EvidenceFactKind`
  value + new adapter + new selector + new source-mapped sim test +
  **one new `EvidenceAuditOutcome` sealed leaf**
  (`Advisory(violations: List<AdvisoryViolation>, reason: String)`)
  for the "should" semantics. Audit reporter routes `Advisory`
  through existing report channels; `assertNoFailures()` ignores
  `Advisory`. No external NDJSON or side-effect files.  [paraphrase]
- **`PacingWindow`** is an `enum class` (not sealed) — gives
  `.entries` for free per memory
  `predicate-guards-over-sealed-types-must-2026-05-16`.  [paraphrase]
- **Catalog additions**: two new `EvidenceSourceRef` constants for
  the COMMS-1 and FN33-MODEL-1 canonical IDs, using existing
  `source(canonicalId, title, claimScope)` helper. Both **added to
  `EvidenceSourceCatalog.All`** so `validateAgainstRegistry()`
  walks them.  [paraphrase]
- **Chunk closure**: updated `coverage_report.md`, STRATEGY.md
  sentence (soft contract), AGENT_DIALOGUE closure entry + role
  refinement, `.plan` blocker resolution per the rule, and 0-4
  spawned production-repair epic specs.  [inferred]

Projection result semantics (no new sealed result type):

- Projection emits `EvidenceFactSet` (existing API).
- Absence-of-facts surfaces in the test outcome:
   - `pass(...)` when facts emitted and assertion holds.
   - `fail(reason, ...)` when assertion fails (either because facts
     emitted but contradict the regulation, OR because no facts
     emitted at all so the regulation cannot be satisfied).
   - `vacuous(reason)` when no applicable assertion in this trace.
   - `expectedGap(gap, reason)` for a known projection gap with a
     typed `EvidenceGapId`.
   - `advisory(violations, reason)` (NEW leaf) for FN33-MODEL-1's
     "should" semantics.  [paraphrase]

`covered-red` test method semantics (this is the key contract):

- Test method does NOT call `report.assertNoFailures()` — that
  would propagate the `Fail` outcome into a JUnit failure and turn
  `./gradlew-nix build` red.
- Instead, the test method asserts directly on `report.results`
  that the expected `Fail` outcome is present for the cited source:
  e.g., `assertTrue(report.results.any { it.sources.contains(ref)
  && it.outcome is EvidenceAuditOutcome.Fail })`.
- JUnit test passes (build green); audit honestly reports `Fail`;
  chunk coverage row records `covered-red`; spawned production-repair
  epic is the closure signal.  [inferred]

Epic Model / Firewall:

- Test Writer / Test Completer separation enforced. Completer's
  chunk-completion responsibility covers building blocker primitives
  + authoring expected-gap tests, not solely driving pre-existing
  failing tests.  [user]
- If a primitive's matching test lands `covered-red`, the production
  repair is a separate flow-next epic; this epic only spawns the
  repair spec and records its id. The `.plan` blocker is REPLACED
  with a one-line pointer to the repair epic, NOT deleted —
  backlog visibility preserved.  [paraphrase]
- If a primitive cannot honestly be designed without a regulation
  interpretation we don't hold, halt and raise `QUESTION:` in
  AGENT_DIALOGUE.md.  [paraphrase]

## API Contracts

Each of the 4 chunk-01 expected-gap units ends this epic in one of
two terminal coverage states:

- **`covered-green`** — primitive built, test authored, audit
  report's case for the cited source unit is `Pass` or `Advisory`
  only. `Vacuous` and `ExpectedGap` are NOT terminal closure
  states for these four units — `ExpectedGap` keeps the unit
  classified `expected-gap`; `Vacuous` means the assertion didn't
  exercise (re-author the trace until it does, or land
  `covered-red`). Test method calls `report.assertNoFailures()`
  and passes. `.plan` blocker paragraph deleted (work fully
  closed).
- **`covered-red`** — primitive built, test authored, audit report
  contains expected `Fail` outcomes for the cited source ref(s).
  Test method does NOT call `assertNoFailures()`; it asserts
  directly that the expected `Fail` is in `report.results`. JUnit
  passes; build green. A named flow-next production-repair epic
  spec exists with the failing audit as the closure signal. `.plan`
  blocker paragraph REPLACED (not deleted) with a one-line pointer
  to the repair epic; paragraph retained until that repair epic
  closes.  [paraphrase]

Every new test cites: accepted source-unit id verbatim (matching
registry JSON), ICAO 9432 §section, quoted claim string. Two
distinct validation pathways exist:

- `SourceUnitCitationValidationTest`
  (`controller/src/jvmTest/.../SourceUnitCitationValidationTest.kt`)
  regex-scans for `SourceUnitRef("…")` **string literals** in the
  controller test tree. Sim-tree tests citing via typed
  `EvidenceSourceRef` (the `cites(ICAO9432.…)` call inside
  `source("id") { … }`) do NOT go through this scanner.
- `EvidenceSourceCatalog.validateAgainstRegistry()` walks
  `EvidenceSourceCatalog.All: Set<EvidenceSourceRef>` (line 245).
  Refs declared but not added to `All` are not validated.  [paraphrase]

Primitive obligations:

- **COMMS-1 primitive** — new sealed leaf
  `EvidenceFactPayload.ReceptionDoubt(transmissionRef, doubtSource,
  resolvedBy: SayAgainRef? = null)`. Doubt is modelled as a fact
  about a transmission instance. Trigger (doubt) and response
  (`protocol.SayAgain`) are distinct types linked by a typed ref;
  doubt fact carries optional `resolvedBy: SayAgainRef`.
  **§2.8.1.4 is mandatory ("shall").** Expected outcome:
  `covered-red`.  [paraphrase]
- **FN33-MODEL-1 primitive** — new sealed leaf
  `EvidenceFactPayload.ClearancePacing(clearanceRef, issuedDuring:
  PacingWindow)`. `PacingWindow` is an **`enum class`** with
  entries `ComplicatedTaxi`, `LineUp`, `TakeoffRoll`, `Other`.
  **§2.8.3.2 is advisory ("should").** Violations surface as
  `EvidenceAuditOutcome.Advisory` (the new outcome leaf);
  `assertNoFailures` ignores them. Expected outcome:
  `covered-green` with `Advisory` observations recorded in the
  report.  [paraphrase]
- **FN44-GAP-1 primitive** — adapter projection emitting
  `FrequencyTransfer(mode = ControllerAdvised, …)` from
  `protocol.ContactFrequency` instructions in sim traces.
  **§2.8.2.1 mandatory.** Expected outcome: `covered-red` (sim
  doesn't emit ContactFrequency currently).  [paraphrase]
- **FN44-GAP-2 primitive** — adapter projection emitting
  `FrequencyTransfer(mode = PilotNotifiedAbsentAdvice, …)` from
  `protocol.RequestFrequencyChange` pilot transmissions. **§2.8.2.1
  mandatory.** Expected outcome: `covered-red`.  [paraphrase]

All primitives total, immutable, sealed-typed (or enum where
appropriate). Adapter projections total under property tests
exercising the full speaker × utterance × payload matrix (see R7
matrix below).  [paraphrase]

Adapter property-test variant matrix (R7 obligation; explicit
minimum):

- `{Controller speaker, Pilot speaker} × {Controller utterance,
  Pilot utterance}` × `{payload matches expected adapter input,
  payload doesn't match}` — eight combinations.
- Plus boundary cases: empty transmission record list, single
  unrelated record, multiple records with mixed match/no-match.

Property tests live alongside primitive code (e.g.,
`EvidenceFactsTest.kt`-style).

## Edge Cases & Constraints

- AGENTS.md commandments 1–8 binding. Commandments 1, 2, 3, 4, 6,
  7, 8 deserve specific attention: no skip-lists, no half-baked
  commits, throw on the provably impossible, tests prove real
  behaviour, protocol is source of truth, cite every regulatory
  claim, dead programs tell no lies.  [user]
- No `@Disabled`, `@Suppress`, skip-list, catch-all `else`, or
  check-disabling TODO.  [paraphrase]
- Advisory tests run-and-record via the new
  `EvidenceAuditOutcome.Advisory` outcome; never `@Disabled`, never
  external side-effect files.  [paraphrase]
- Sealed-type additions force re-audit of every `when` consumer —
  grep-walk discipline required.  [paraphrase]
- No production behaviour change inside this epic. `covered-red`
  landings spawn production-repair epics; `.plan` blocker is
  REPLACED (not deleted) with a one-line pointer to the repair epic
  before the red landing is committed.  [paraphrase]
- Every regulatory claim cites ICAO 9432 with edition + §section
  (4th ed 2007). Validated against `research/txt/` or accepted
  registry JSON pre-commit.  [paraphrase]
- If a primitive's design slips into PHRASE-1 or POLICY-1
  territory, halt and raise `QUESTION:` in AGENT_DIALOGUE.md. Do
  not silently expand scope.  [inferred]
- ICAO Annex 10 Vol II not extracted in repo. If a primitive's
  design demands verbatim Annex 10 §5.2 wording, that is a
  `QUESTION:` candidate (external fetch).  [paraphrase]
- Tests that mix `covered-green` and `covered-red` cases in one
  method are forbidden — one source unit per test method (so the
  assertion shape is unambiguous: green calls `assertNoFailures`,
  red asserts on `report.results`).  [inferred]

## Acceptance Criteria

- [ ] **R1** — COMMS-1 evidence primitive built: new
  `EvidenceFactPayload.ReceptionDoubt` sealed leaf, new
  `EvidenceFactKind.ReceptionDoubt`, adapter projection over sim
  traces (returns `EvidenceFactSet`), typed `SayAgainRef` linkage.
  Primitive-level unit tests. `.plan` COMMS-1 closed per rule.
- [ ] **R2** — FN33-MODEL-1 evidence primitive built: new
  `EvidenceFactPayload.ClearancePacing` sealed leaf, new
  `EvidenceFactKind.ClearancePacing`, `PacingWindow` enum class,
  adapter projection (returns `EvidenceFactSet`), NEW
  `EvidenceAuditOutcome.Advisory` sealed leaf routed through
  existing audit report (no external files; `assertNoFailures`
  ignores `Advisory`), `advisory(violations, reason)` helper in
  `EvidenceDsl`. Primitive-level unit tests. `.plan` FN33-MODEL-1
  closed per rule.
- [ ] **R3** — FN44-GAP-1 adapter projection emitting
  `FrequencyTransfer(ControllerAdvised, …)` from
  `protocol.ContactFrequency` (existing payload, existing source
  ref, existing gap ID). Primitive-level unit tests. `.plan`
  FN44-GAP-1 closed per rule.
- [ ] **R4** — FN44-GAP-2 adapter projection emitting
  `FrequencyTransfer(PilotNotifiedAbsentAdvice, …)` from
  `protocol.RequestFrequencyChange`. Primitive-level unit tests.
  `.plan` FN44-GAP-2 closed per rule.
- [ ] **R5** — Each of the 4 chunk-01 expected-gap units has a
  source-mapped test using the real DSL shape:
  `simEvidence("name") { observe { … }; source("case-id") {
  cites(ICAO9432.…); expect { … } } }`. Each test cites its
  accepted source-unit id and quoted §section.
  `SourceUnitCitationValidationTest` green (for any new
  `SourceUnitRef` string literals); `EvidenceSourceCatalog`
  validation green for typed refs.
- [ ] **R6** — Each of the 4 units lands in `{covered-green,
  covered-red}`. Zero remaining `expected-gap`. No new
  programme-level deferments. `covered-red` tests pass at the
  JUnit/Kotest layer by asserting on `report.results` (NOT by
  calling `assertNoFailures()`); `covered-green` tests pass via
  `report.assertNoFailures()`.
- [ ] **R7** — For each `covered-red` landing, a named flow-next
  production-repair epic spec exists (Goal & Context + Acceptance
  Criteria + failing audit as closure signal). The `.plan` blocker
  for that unit is REPLACED with a one-line pointer to the repair
  epic, NOT deleted.
- [ ] **R8** — `chunk_01_coverage_report.md` updated: 4 new
  terminal states; spawned repair-epic ids recorded inline;
  verification run + result included.
- [ ] **R9** — No PHRASE-1 / POLICY-1 work inside this epic. Their
  `.plan` entries remain intact; their 9 affected chunk-01 units
  stay `phraseology-later` / `policy-blocked`.
- [ ] **R10** — AGENT_DIALOGUE.md amended in two ways before
  closure: (a) two-actor model clarified to record that Test
  Completer's chunk-completion role explicitly covers building
  blocker primitives + authoring expected-gap tests, not solely
  driving pre-existing failing tests; (b) a closure entry recording
  focused verification command, result, new coverage tally,
  spawned repair-epic ids, and a "chunk 02 ready for Test Writer"
  signal.
- [ ] **R11** — Principal-agent self-assessment (AGENTS.md §
  Self-assessment before review) performed before commit. Any
  finding not fixed in this epic is filed in `.plan` and (if
  named-contract) in `docs/deferments.md` per the four-bucket
  convention.
- [ ] **R12** — Adapter projections total under property tests over
  the explicit matrix: `{Controller, Pilot} speaker × {Controller,
  Pilot} utterance × {match, non-match} payload` (eight base
  combinations) plus boundary cases (empty input, single
  unrelated, mixed). Tests use `entries` for enum exhaustion.
  (NEW in round 2 — R12 because R-IDs are not renumbered after
  review; original R1-R11 keep their meanings.)

## Early proof point

Task **fn-48-icao-9432-chunk-01-drive-expected-gap.2** (FN44-GAP-1 +
FN44-GAP-2 paired closure) is the pattern-validator. Smallest
surface (no new sealed types). It validates the full flow:
adapter projection → selector → `simEvidence("name") { observe { … };
source("case-id") { cites(…); expect { … } } }` test → covered-red
landing via assert-on-`report.results` → spawn repair epic → `.plan`
pointer replace. If .2 cannot land cleanly, reconsider whether
`EvidenceFactAdapters` / `EvidenceExpectContext` are the right
extension points before building new sealed payload types in .3
(COMMS-1) and .4 (FN33-MODEL-1).

## Boundaries

In scope:

- The 4 chunk-01 expected-gap source units.
- Two new `EvidenceFactPayload` sealed leaves (`ReceptionDoubt`,
  `ClearancePacing`) + their `EvidenceFactKind` enum values.
- One new `EvidenceAuditOutcome` sealed leaf (`Advisory`) + the
  `advisory(...)` builder helper.
- One new `enum class PacingWindow`.
- Four adapter projections in `EvidenceFactAdapters` + selectors on
  `EvidenceExpectContext`.
- Source-mapped sim tests for the 4 units using the real DSL shape.
- Two new `ICAO9432.*` source-ref constants in
  `EvidenceSourceCatalog`, both added to `EvidenceSourceCatalog.All`.
- Coverage-report update, AGENT_DIALOGUE.md role-clarification +
  closure entry, `.plan` accounting per the covered-green /
  covered-red rule.
- 0-4 named production-repair epic specs for `covered-red`
  landings (drafted, not implemented).
- STRATEGY.md chunk-01 closure sentence (soft contract).

Out of scope:

- PHRASE-1 phraseology infrastructure.
- POLICY-1 typed policy concepts.
- Implementing production controller / pilot / sim repairs for
  `covered-red` landings.
- Re-authoring the 6 already `covered-green` tests.
- Chunks 02 and later.
- Annex 10 Vol II reconciliation.
- External NDJSON / file-side-effect machinery for advisory
  reporting.

## Strategy Alignment

Active tracks served:

- **Requirements registry** — closes chunk-01 of the declared
  46-window slice with 4 terminal-state landings (anticipated 3
  covered-red + 1 covered-green) and 0-4 spawned production-repair
  epics. All 4 chunk-specific named blockers resolved or
  superseded by named repair epics.
- **Runtime simulator** — extends evidence-DSL surface with two new
  sealed payload leaves + one new sealed audit-outcome leaf + one
  enum + four adapter projections, all bound to cited ICAO 9432
  §sections.
- **Reviewer / agent infrastructure** — refines the two-actor model
  in doctrine + first concrete exercise; closes the chunk under
  the principal-agent self-assessment gate.

No strategy drift detected.

## Decision Context

- **Two-actor model** refined this epic: Completer builds blocker
  primitives + authors expected-gap tests, then lands each unit.  [user]
- **No-deferment / no-surprises** taken literally: every
  chunk-specific named blocker closes (deleted on green; replaced
  with named repair-epic pointer on red). PHRASE-1 / POLICY-1 stay
  tracked in `.plan` as named, visible cross-chunk debt.  [user]
- **covered-red test semantics — assert on `report.results`, not
  via `assertNoFailures`**: lets the test pass (build green) while
  the audit honestly reports the `Fail`. Spawned production-repair
  epic is the closure signal. Without this distinction, "covered-red
  test in repo" would force the build red — incompatible with
  AGENTS.md commandment 2 (no half-baked commits).  [inferred]
- **Synthetic-projected-payloads is NOT a path to
  covered-green**: per AGENTS.md commandment 4 (tests prove the
  real job), an honest source-unit landing requires the sim to
  actually produce the relevant observation. If the sim doesn't
  yet model the input (reception quality, ContactFrequency
  emission, RequestFrequencyChange emission), the honest landing
  is `covered-red` + production-repair spawn — not a synthetic
  fake-green.  [inferred]
- **Advisory outcome as a typed sealed leaf**: extends existing
  `EvidenceAuditOutcome` rather than introducing external NDJSON
  side effects. Keeps audit-report single-pathway.
  `assertNoFailures()` continues to treat only `Fail` as failing;
  `Advisory` is reported and counted but does not block.  [inferred]
- **`PacingWindow` as `enum class`**: gives `.entries` for free,
  matches `predicate-guards-over-sealed-types-must-2026-05-16`
  memory entry. Sealed sub-type wouldn't.  [inferred]
- **Pattern-validator first** (FN44-GAP-1/2): smallest surface
  (no new sealed types). Locks the adapter / selector /
  covered-red-test pattern before COMMS-1 and FN33-MODEL-1 add new
  sealed leaves.  [inferred]
- **EvidenceSourceCatalog.All membership**: declaring a constant
  isn't enough — must add to `All` for `validateAgainstRegistry`
  to walk it. Task .1 acceptance pins this explicitly.  [inferred]

## Review Considerations

**FP / type safety.** Two new sealed leaves on `EvidenceFactPayload`
(`ReceptionDoubt`, `ClearancePacing`); one new leaf on
`EvidenceAuditOutcome` (`Advisory`); one new enum (`PacingWindow`);
two new `EvidenceFactKind` enum values. Every `when` over each
modified sealed type is exhaustive at introduction — grep all
consumers. No `else -> Unit`. Smart constructors validate
source-unit ids; adapter projections return `EvidenceFactSet`;
totality under explicit property-test matrix (R7).  [inferred]

**Test architecture.** All four source-mapped tests use the real
`simEvidence("name") { observe { … }; source("case-id") {
cites(ICAO9432.…); expect { … } } }` shape. `ProtocolEvidenceBuilder`
has no `source` function. Test method assertion shape depends on
landing: covered-green calls `report.assertNoFailures()`;
covered-red asserts directly on `report.results` for the expected
`Fail` outcome. One source unit per test method. Primitive-level
unit tests live alongside primitive code; chunk-01 source-mapped
tests cite source units.  [inferred]

**Impact.** Multiple sealed-type extensions ripple to every
`when`-consumer:
- `EvidenceFactPayload` consumers: `EvidenceFactKind` mapper,
  adapters, pretty-printers, serializers. Grep on each new leaf.
- `EvidenceAuditOutcome` consumers: `assertNoFailures()` (must
  ignore `Advisory`), report formatter (must render `Advisory`
  distinctly), test-DSL helpers (add `advisory(...)` at
  `EvidenceDsl.kt:311-323`).
- `EvidenceFactKind` consumers: enum-switch logic in adapters /
  selectors.
- `EvidenceSourceCatalog.All`: new refs added so validation walks
  them.
Reversal: removing a primitive must restore chunk 01 to its prior
`expected-gap` state without leaking partial test references.  [inferred]

**Operational correctness.** Each primitive maps back to the cited
ICAO 9432 §section (4th ed 2007). §2.8.1.4 "shall" (mandatory —
detectable + failing); §2.8.2.1 "shall" twice (both mandatory);
§2.8.3.2 "should" (advisory — observable + reported via
`Advisory` outcome, not failing).  [inferred]

## Requirement coverage

| Req | Description | Task(s) | Gap justification |
|-----|-------------|---------|-------------------|
| R1  | COMMS-1 primitive (new payload + adapter + selector + typed SayAgainRef) + .plan closure | fn-48-icao-9432-chunk-01-drive-expected-gap.3 | — |
| R2  | FN33-MODEL-1 primitive + new `Advisory` outcome leaf + `PacingWindow` enum + .plan closure | fn-48-icao-9432-chunk-01-drive-expected-gap.4 | — |
| R3  | FN44-GAP-1 adapter + selector (existing payload) + .plan closure | fn-48-icao-9432-chunk-01-drive-expected-gap.2 | — |
| R4  | FN44-GAP-2 adapter + selector (existing payload) + .plan closure | fn-48-icao-9432-chunk-01-drive-expected-gap.2 | — |
| R5  | 4 source-mapped `simEvidence { source("id") { cites; expect } }` tests | fn-48-icao-9432-chunk-01-drive-expected-gap.2, .3, .4 | — |
| R6  | Terminal states for all 4; zero expected-gap; covered-red tests pass via report.results assertion | fn-48-icao-9432-chunk-01-drive-expected-gap.2, .3, .4, .5 | — |
| R7  | Named repair-epic spawns for covered-red; .plan replaced with pointer | fn-48-icao-9432-chunk-01-drive-expected-gap.2, .3, .4 | Conditional on red landing (expected for 3 of 4). |
| R8  | coverage_report.md updated with terminal states + verification | fn-48-icao-9432-chunk-01-drive-expected-gap.5 | — |
| R9  | No PHRASE-1 / POLICY-1 changes | fn-48-icao-9432-chunk-01-drive-expected-gap.1, .2, .3, .4, .5 | Negative invariant verified in .5. |
| R10 | AGENT_DIALOGUE role-clarification + closure entry | fn-48-icao-9432-chunk-01-drive-expected-gap.1, .5 | .1 amends doctrine; .5 writes closure entry. |
| R11 | Principal-agent self-assessment before commit | fn-48-icao-9432-chunk-01-drive-expected-gap.5 | — |
| R12 | Adapter projection total under explicit speaker × utterance × payload matrix property tests (NEW in round 2) | fn-48-icao-9432-chunk-01-drive-expected-gap.2, .3, .4 | — |
