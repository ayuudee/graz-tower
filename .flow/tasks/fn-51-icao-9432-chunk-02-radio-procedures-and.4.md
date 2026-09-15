# fn-51-icao-9432-chunk-02-radio-procedures-and.4 Author chunk 02 source-mapped tests or expected-gap records

## Description

Author only the chunk-02 tests that are honestly expressible after task .3
using already-existing evidence. Everything blocked by missing phraseology,
policy, or observation facts gets an expected-gap record with a named
blocker/repair epic.

## Acceptance

- [x] Source-mapped tests cite typed `EvidenceSourceRef` constants for any rows moved to covered-green/covered-red.
- [x] Tests use high-level evidence DSL entry points (`simEvidence` or `protocolEvidence`) and assert report outcomes.
- [x] No test relies on broad string matching of rendered RT phraseology.
- [x] No test encodes a universal safety-exception or operational-guidance policy.
- [x] No new evidence payload, selector, adapter, controller behaviour, pilot behaviour, or sim behaviour is added in this task.
- [x] Expected-gap rows include blocker id, reason, and next repair epic if chunk-specific.
- [x] Focused verification for authored tests passes.

## Review Considerations

FP / type safety: adding catalog refs requires updating the validation set so registry validation exercises them.

Test architecture: source-mapped tests should be readable at intent level: source, observed facts, expected evidence. Avoid ceremony and helper internals.

Impact: if a chunk-specific repair epic is spawned, update `.plan` or the chunk report with the exact pointer.

Operational correctness: cite ICAO 9432 §2.8.4 or §4.1-§4.2 source ids directly in each test case.

## Done summary
Authored expected-gap records instead of tests because no chunk-02 row is honestly coverable-green with current evidence without crossing PHRASE-1, POLICY-1, or missing observation surfaces. Created repair epic fn-52-implement-icao-9432-chunk-02 for critical-phase transmission projection, start-up approval/start evidence, and radio-test-signal duration identity.
## Evidence
- Commits:
- Tests: No source-mapped tests authored: all candidate rows require missing evidence, phraseology, or policy infrastructure.
- PRs: research/tools/requirements-spike/quality/icao9432_programme/chunk_02_radio_procedures_policy/expected_gaps.md, .flow/specs/fn-52-implement-icao-9432-chunk-02.md