# ICAO 9432 communications, transfer, and readback source-mapped tests

## Goal & Context
Author the first chunk of ICAO 9432 source-mapped tests from the fn-46 programme map. This chunk covers communications establishment, transfer/frequency, readback, and hearback source units from:

- `communications_2_8_1_en`
- `transfer_communications_2_8_2_en`
- `readback_2_8_3_en`
- `readback_continuation_2_8_3_7_to_2_8_3_10_en`

There are 20 accepted source units in scope. The goal is not to make the implementation pass. The goal is to author clear, source-mapped, high-level tests and expected-gap records that define the regulatory target. Failing tests are acceptable when they expose implementation gaps; they must fail loudly and traceably.

This epic is part of the larger ICAO 9432 programme. It must leave the next action obvious: either continue to the next source chunk, or open a named repair/validation epic for failing tests and missing concepts.

## Architecture & Data Models
Inputs:
- fn-46 map artifacts under `research/tools/requirements-spike/quality/icao9432_programme/`.
- Source registry JSON files for the 20 chunk-01 source units.
- Existing evidence DSL/audit core from fn-43/fn-44.
- Existing protocol/controller/sim test suites and source-citation validation.

Outputs:
- Source-mapped tests or explicit expected-gap records for all 20 units.
- A chunk coverage report under `research/tools/requirements-spike/quality/icao9432_programme/chunk_01_comms_readback_transfer/`.
- Follow-on repair epic recommendations for implementation gaps, without fixing those gaps here.

Preferred test shape:
- Use `protocolEvidence` where the rule is about typed instruction/readback semantics and can be tested without a full sim run.
- Use `simEvidence` only where the source claim requires actual radio flow, timing, or controller/pilot interaction.
- Use expected-gap records when the source unit needs missing policy concepts, frequency-transfer facts, or rendered phraseology facts.

## Epic Model / Firewall
This epic writes the source-mapped tests and coverage report. It does not repair controller, pilot, sim, or phraseology behaviour.

Lifecycle for each source chunk:
1. Test-authoring epic writes source-mapped tests and expected gaps.
2. User or separate repair epics make failing tests pass out-of-band.
3. A validation task/epic reruns tests, updates coverage status, and closes the chunk.

If a test cannot be expressed because the evidence DSL itself lacks a necessary authoring primitive, this epic may add the smallest honest harness capability. It may not add production behaviour to make a failing regulatory test pass.

## API Contracts
A source unit in this chunk must end in exactly one coverage state:
- `covered-green`: source-mapped test exists and passes.
- `covered-red`: source-mapped test exists and fails because implementation is incomplete.
- `expected-gap`: no honest executable test can be written yet; missing concept is named.
- `phraseology-later`: requires rendered phraseology facts, tracked by `PHRASE-1`.
- `policy-blocked`: requires explicit policy concept, tracked by `POLICY-1` or a more specific follow-up.
- `not-applicable`: only if the source unit is confirmed non-behavioural for this simulator, with rationale.

Every test or gap record must carry the accepted source-unit id and quote/section reference.

## Edge Cases & Constraints
- Do not disable failing tests or hide them behind skip lists.
- Do not loosen assertions to accommodate current implementation behaviour.
- Do not mark a source unit covered by a scenario unless the asserted evidence proves the source claim.
- Do not make phraseology claims from typed instruction semantics alone.
- Do not hard-code one behaviour for policy-sensitive guidance; model it as blocked by policy until policy is explicit.
- Do not create low-level tests that only prove compiler/type properties.

## Acceptance Criteria
- [ ] All 20 chunk-01 source units are re-checked against their accepted registry JSON and listed in the chunk report.
- [ ] Every source unit has a coverage state from the contract above.
- [ ] Source-mapped tests are authored for all units that can honestly be tested with current evidence primitives.
- [ ] Expected-gap / policy / phraseology records cite the blocking `.plan` item (`FN44-GAP-1`, `FN44-GAP-2`, `POLICY-1`, `PHRASE-1`, or `FN33-MODEL-1`).
- [ ] The chunk report identifies failing tests that should be fixed out-of-band.
- [ ] No production controller/pilot/sim behaviour is changed to make these tests pass.
- [ ] Verification commands are run and recorded; red tests are acceptable only when intentionally produced and documented as `covered-red`.

## Boundaries
In scope:
- Source quote re-check for the 20 chunk-01 units.
- Source-mapped test authoring.
- Expected-gap records.
- Coverage report and repair recommendations.
- Minimal evidence DSL authoring fixes if tests cannot otherwise be expressed.

Out of scope:
- Controller/pilot/sim behaviour fixes.
- Frequency-transfer fact implementation beyond expected-gap records.
- Rendered phraseology implementation.
- Global policy model implementation.
- Non-9432 sources except for precedence/context notes.

## Decision Context
Start with chunk 01 because it is central, partly testable now, and exercises the source-mapped workflow without requiring new emergency, vehicle, or pushback domains. This should prove whether the programme can stay focused: every chunk has a finite source list, a finite coverage report, and explicit handoff to repair/validation work.

## Review Considerations

### FP / type safety
Coverage states and blockers should be closed values in any permanent code. Avoid ad hoc string status checks in tests. If a harness primitive is added, prefer typed source refs and typed gap ids over raw strings.

### Test architecture
Follow `docs/test-standards.md`: high-level tests, believable minimal scenarios, and assertions on real behaviour. Protocol-level tests are acceptable for readback/hearback atoms where the oracle is the cited source and the behaviour is protocol semantics rather than sim timing.

### Impact
This epic deliberately creates pressure on implementation without fixing it. That keeps the regulatory test wall independent, but it means the suite may be red. The coverage report must make that red state actionable and attributable.

### Operational correctness
All operational claims come from accepted ICAO 9432 source units. Policy-sensitive guidance must remain policy-blocked until a typed policy concept exists. Phraseology claims must wait for rendered phraseology evidence or be clearly marked `phraseology-later`.
