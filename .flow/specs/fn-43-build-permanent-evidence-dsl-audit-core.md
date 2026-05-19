# Build Permanent Evidence DSL Audit Core

## Goal & Context

Build the first permanent-facing evidence-mapped testing foundation. The objective is explicit and narrow: keep test call sites simple and legible while the harness provides serious evidence, provenance, source validation, typed gaps, deterministic sample/fuzz domains, and durable reports.

This epic reconciles FN39-FN42:

- FN39 was right that source-backed conformance needs audit/reporting depth: applicability, adequacy, provenance, typed gaps, durable records.
- FN40/FN41 were right that the public authoring model must stay terse and Kotlin-native.
- FN42 corrected the direction: evidence-mapped tests are the public authoring model, not the whole architecture.

The primary acceptance wall is to re-port the existing 20 FN41 cases through a permanent-facing facade and keep those call sites at least as simple as the spike while producing stronger audit output.

## Non-Negotiable Design Goal

Err on the side of simplicity at the test call site.

Harness complexity is acceptable only when it makes authored tests simpler, more honest, or more reproducible. Harness complexity that leaks into ordinary test authoring is a design failure.

The desired shape is close to:

```kotlin
protocolEvidence("ICAO 9432 structural readback requirements") {
    structuralReadback("takeoff clearance", ClearedForTakeoff(aircraft, runway)) {
        cites(ICAO9432.Readback.RequiredItems)
        requires(ClearedForTakeoffReadback(runway))
    }
}

simEvidence("LOWG touch-and-go then full-stop") {
    observe { lowgCircuit(TouchAndGo, FullStop) }

    source("taxi clearance before runway use") {
        cites(ICAO9432.Taxi.HoldingPointLimit)
        sample("runway", runway)
        expect {
            first<TaxiToHoldingPoint>(aircraft) before
                firstReport<Ready>(aircraft) before
                first<LineUpAndWait>(aircraft)
        }
    }
}
```

If the implementation cannot keep the public API close to this, stop and review rather than papering over the complexity.

## Architecture & Data Models

### Public DSL

Provide two entry points:

- `protocolEvidence(name) { ... }` for pure protocol/structural evidence.
- `simEvidence(name) { observe { ... }; ... }` for high-level sim scenarios.

Provide case forms:

- `source(name) { cites(...); sample(...); expect { ... } }`
- `golden(name, reason) { expect { ... } }`
- `regression(name, issue) { expect { ... } }`
- `invariant(name) { expect { ... } }`
- `structuralReadback(name, instruction) { cites(...); requires(...) }`

The DSL should produce typed audit records. It should not require authors to mention monitors, fact ids, provenance paths, report serialization, capability tags, or activation accounting unless they are writing harness internals.

Every evidence case has a claim kind. Initial claim kinds are:

- structural protocol requirement;
- sim-observed behaviour;
- golden/project behaviour;
- regression;
- invariant;
- expected model/projection gap.

Reports must use claim kind to prevent structural protocol checks from being
presented as rendered phraseology compliance or actual pilot-exchange evidence.

### Source Catalog

Introduce a small typed source catalog for only the source units used in the 20-case suite:

- ICAO 9432 readback source units used by FN37/FN41.
- ICAO 9432 taxi source units used by FN35/FN41.
- ICAO 9432 final approach / landing source units used by FN36/FN41.
- Explicit model/projection-gap source refs used by the expected-gap cases, if retained.

The catalog must be mechanically traceable to existing source-unit ids. Ordinary test authors should not pass raw strings. Raw source ids may appear only inside the catalog or validation fixture.

Catalog validation must be exact, not shape-based. A catalog entry must resolve
to an accepted registry record or a reviewed spike gap source record. Invented
ids that merely match the source-unit id pattern are not acceptable.

### Observation / Fact Layer

Replace or wrap spike `SimObservation` with provenance-bearing evidence facts:

- stable `FactId`;
- scenario id;
- origin: synthetic protocol or sim run;
- stable sequence number;
- sim time where applicable;
- source event/transmission id where available;
- extraction path;
- typed payload: instruction, pilot report, pilot transmission, aircraft summary, sample/domain fact.

Facts are observations, not conformance conclusions. Do not encode claims like "readback compliant" or "clearance lawful" in facts.

Fact ordering must be stable across reruns. The ordering contract is: use the
radio/event trace sequence when available, carry the source transmission/event
id when available, and use a deterministic adapter-local sequence only as a
documented fallback. Timestamp-only ordering is forbidden.

### Query Algebra

Provide typed selectors that make repeated-event assumptions explicit:

- `first<T>(aircraft)`;
- `nth<T>(aircraft, index)`;
- `exactly(n) { instruction<T>(aircraft) }`;
- `none { instruction<T>(aircraft) }`;
- `between(left, right) { ... }`;
- `before` over stable fact order, not only timestamp.

The initial implementation should include only what the re-ported 20 cases need, plus one absence/count primitive if it can be done cleanly. Do not add speculative query helpers.

### Outcomes

Use sealed typed outcomes:

- `Pass`;
- `Fail`;
- `Vacuous`;
- `ExpectedGap`;
- `UnexpectedGap`.

`ExpectedGap` requires a governed typed gap id. A broad Flow epic id is not
enough. Each gap id must carry affected source refs, missing projection/model
concept, closure trigger, and tracked `.plan` or deferment/backlog link. A typed
id with only a short label is not sufficient.

Every source-backed case must record applicability and activation. Structural
protocol checks may record an explicit structural-only applicability reason.
Sim-observed source cases must record the facts that activated the case. A
source case without activation is a failure unless it reports a typed
`Vacuous` or `ExpectedGap` reason.

### Typed Domains / Samples / Fuzzing

Add deterministic domain support behind the facade:

- example sample;
- representative samples;
- generated samples with seed, count, and partition metadata.

First generated domain should be pure protocol, not a full sim scenario. Good candidates: headings, levels, speeds, pressure settings, squawks, or runway identifiers.

Generated failures must be reproducible from report data: case id, source id, domain name, seed, sample index, sample value, partition, evidence facts, outcome.

The generated protocol slice must include non-trivial partition coverage and at
least one negative/omitted-field style readback check. A generated domain with a
single trivial value does not satisfy this epic.

### Reports

Emit durable Markdown and JSON report artifacts for the 20-case suite. Reports must include:

- suite/scenario id;
- case id;
- basis: source/golden/regression/invariant;
- source refs where applicable;
- samples and generated sample metadata;
- outcome;
- typed gap id where applicable;
- fact ids supporting pass/fail;
- applicability, activation, and adequacy record for every source-backed case;
- enough information to reproduce generated failures.

An in-memory formatter is insufficient.

## API Contracts

- Public DSL is Kotlin-native and test-source facing.
- Source catalog entries are typed values; raw source-unit strings are not accepted by normal `cites(...)` calls.
- Fact projection is one-way: sim/protocol output -> evidence facts. Evidence facts cannot mutate or inspect sim internals.
- Report generation is deterministic.
- Generated domains use deterministic seeds and record seed/sample metadata.
- Expected gaps require typed gap ids and must be visible in reports.

## Implementation Plan

### Phase 1: Source catalog and typed ids

- Add typed `EvidenceSourceRef` / catalog entries for FN41 sources.
- Add exact validation against accepted registry records or reviewed spike gap records.
- Replace ordinary call-site raw strings in the re-ported suite.

### Phase 2: Audit records and provenance-bearing facts

- Define fact ids, provenance, origins, stable ordering, and typed fact payloads.
- Define the stable fact-id and global ordering contract.
- Build adapters for synthetic protocol evidence and LOWG sim evidence.
- Ensure facts do not carry conformance conclusions.

### Phase 3: Public DSL shell

- Implement `protocolEvidence` and `simEvidence`.
- Implement source/golden/regression/invariant case forms.
- Implement `structuralReadback` helper with precise naming.
- Keep the public API close to the target shape.

### Phase 4: Query algebra

- Implement stable-order `before` over facts.
- Replace first-match assumptions with explicit `first` selectors.
- Add at least one count or absence primitive if it is needed by a re-ported case or a small targeted negative case.

### Phase 5: Typed domains and one generated protocol slice

- Add example and representative samples.
- Add one deterministic generated domain for a pure protocol readback family with non-trivial partitions.
- Add omitted-field / negative evidence for the generated protocol slice.
- Record seed/sample/partition metadata in reports.

### Phase 6: Durable reports

- Emit Markdown and JSON reports for the suite.
- Assert reports contain source ids, samples, claim kinds, applicability,
  activation, adequacy, outcomes, fact ids, and typed gaps.

### Phase 7: Re-port the 20 FN41 cases

- Re-port the 12 protocol/readback cases.
- Re-port the LOWG scenario ordering/golden/invariant cases.
- Re-port the expected gaps with governed typed gap ids carrying affected source
  refs, missing concept, closure trigger, and tracked backlog/deferment link.
- Verify call sites remain at least as simple as FN41.

### Phase 8: Review and red-team before closing

- Run focused tests and detekt.
- Run a plan/implementation review that explicitly looks for cut corners, hidden complexity, false confidence, raw-string leakage, weak source validation, weak gap governance, brittle selectors, and fuzzing/report reproducibility gaps.
- Do not close this epic if review identifies a staff-engineer-catchable architecture/test issue that remains unresolved or untracked.

## Acceptance Criteria

- [ ] Public call sites are at least as simple and legible as FN41's 20-case spike.
- [ ] Ordinary tests cite typed catalog entries, not raw source-unit strings.
- [ ] Catalog entries validate exactly against accepted registry records or reviewed spike gap records.
- [ ] Evidence facts carry stable ids, provenance, origin, order, and typed payloads.
- [ ] Fact ordering is stable by trace/event sequence, not timestamp-only ordering.
- [ ] Every source-backed case records applicability and activation or a typed vacuous/gap reason.
- [ ] Query selectors make repeated-event assumptions explicit.
- [ ] Expected gaps use typed gap ids and appear in reports.
- [ ] Every typed gap id carries affected source refs, missing concept, closure trigger, and tracked `.plan` or deferment/backlog link.
- [ ] Structural protocol checks carry claim-kind metadata and cannot be reported as phraseology or actual exchange compliance.
- [ ] At least one deterministic generated protocol domain is exercised with non-trivial partitions and omitted-field / negative evidence.
- [ ] Markdown and JSON reports are emitted and tested.
- [ ] Reports include applicability, activation, adequacy, source refs, samples, outcomes, fact ids, typed gaps, and generated-sample reproduction metadata.
- [ ] The 20 existing cases are re-ported through the permanent-facing facade.
- [ ] Ordinary test call sites contain no raw source ids, no fact/provenance/report plumbing, no manual trace scans, and no monitor vocabulary.
- [ ] Focused tests pass.
- [ ] `./gradlew detekt` passes.
- [ ] Review/red-team concerns are addressed or loudly tracked in `.plan`.

## Boundaries

In scope:

- test-source implementation of the permanent-facing harness;
- the 20 existing cases;
- one generated protocol domain;
- durable reports for the suite;
- typed source catalog for already-used source units.

Out of scope:

- full corpus coverage dashboard;
- complete phraseology verification;
- broad sim fuzzing;
- replacing existing golden tests;
- exposing monitor vocabulary in public tests;
- adding new regulatory source areas beyond the existing 20-case set.

## Decision Context

This epic is the implementation answer to FN42. It deliberately does not choose between "monitor" and "evidence" as visible user concepts. The public concept is evidence-mapped tests. The internals may grow monitor-like if that helps reporting and reuse, but the call-site DSL remains the design artifact.

## Review Instructions For Plan Review

The reviewer must be adversarial. They should pay particular attention to:

- any corner that could be cut silently;
- hidden complexity that might leak into test authoring;
- anything that undermines simple, legible tests;
- anything that creates false source-backed confidence;
- raw string source ids escaping the catalog;
- expected gaps that are broad, vague, or too easy to bless;
- selectors that false-pass repeated-event or same-time cases;
- fuzzing that is not reproducible from reports;
- reports that look complete but do not prove applicability/activation;
- any helper that inspects raw sim internals;
- monitor/framework vocabulary leaking into public tests;
- any mismatch with `docs/test-standards.md`.
- any catalog validation that only checks source-id shape rather than registry existence;
- any report that omits applicability/activation for source-backed cases;
- any structural readback report that overclaims operational exchange or phraseology compliance.

The reviewer should reject the plan if it can achieve green tests while failing the real goal: simple authored tests backed by honest, reproducible evidence.

## Review Considerations

### FP / type safety

Use sealed outcome types, typed source refs, typed gap ids, typed fact payloads, and typed domains. Avoid raw strings except inside source catalog construction and report rendering. Avoid nullable selectors as public API; prefer typed absence/outcome values. No catch-all `else` for outcome handling.

### Test architecture

The suite should remain high-level or pure-protocol where appropriate. Protocol structural readback checks are acceptable because `requiredReadbackAtoms` is a pure protocol oracle; sim behaviour checks should use high-level scenario observations rather than implementation internals.

### Impact

The harness creates a lasting test-authoring API. That is worth doing only if the public API remains simple and the internals prevent false confidence. The main failure modes are helper sprawl, hidden monitor complexity, weak reports, and source-id ceremony.

### Operational correctness

Every regulatory claim must cite typed source catalog entries tied to existing source units. Structural readback requirements must not claim rendered phraseology compliance. Phraseology assertions remain out of scope unless backed by explicit phraseology source units.
