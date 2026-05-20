# Map ICAO 9432 source-unit testing programme

## Goal & Context
Work through all accepted ICAO Doc 9432 source units as a regulatory testing programme. The first deliverable is a loose but useful map of the source-unit space: operational areas, semantic clusters, policy choices, observation gaps, and proposed implementation epics. Subsequent epics will take one mapped chunk at a time and author source-mapped tests. Most of those tests are expected to fail initially; fixing controller, pilot, sim, or phraseology implementation belongs to separate implementation epics to keep the testing programme as black-box and independent as possible.

This supersedes the earlier harness-design spike posture. The source-mapped surface is now settled enough that the work should be managed as regulatory coverage: source -> area -> impact map -> test epic -> separate implementation repair.

## Architecture & Data Models
Inputs:
- Accepted ICAO 9432 source units in `research/tools/requirements-spike/registry/ollama_first/`.
- Existing source-unit coverage documents under `research/tools/requirements-spike/quality/coverage/`.
- Evidence DSL and source-citation validation work from fn-43/fn-44.
- Existing simulator/controller/pilot golden tests as high-level behavioural anchors.

Primary output is a machine-usable mapping artifact plus human-readable plan, likely under `research/tools/requirements-spike/quality/icao9432_programme/`:
- `source_unit_id`
- document/section/window
- short semantic summary
- operational area
- cluster/chunk id
- normative kind: must / must-not / may / example / policy / phraseology / information / model-gap
- current testability: testable-now / needs-observation-fact / needs-policy-type / needs-sim-model / phraseology-later / not-applicable
- likely target: protocolEvidence / simEvidence / monitor/report-only / expected-gap
- proposed epic id/title
- blocking backlog item, if any

Policy-sensitive rules must be mapped differently from mandatory rules. If ICAO 9432 permits controller behaviour A or B depending on operating policy, the test should not hard-code one as globally correct. The map must call this out as requiring an explicit policy concept in code, so later implementation can model the choice as a typed controller/airport/procedure policy rather than as an accidental branch.

## API Contracts
This epic does not define production APIs. It defines project contracts:

1. A mapped source unit is not covered until it has an explicit status and a target epic or explicit exclusion reason.
2. A source-mapped test may fail, but it must fail loudly and traceably: source id, scenario id, expected evidence, observed evidence, and gap category.
3. Implementation fixes must not be smuggled into the mapping/test epic except for test-harness defects that prevent writing the test at all.
4. Policy variance must be represented as policy, not as arbitrary controller behaviour or test looseness.

## Edge Cases & Constraints
- Do not overclaim coverage from a broad scenario if the asserted evidence does not actually prove the source unit.
- Do not collapse mandatory rules, permissions, examples, and local-policy options into the same assertion style.
- Do not convert source units into one tiny low-level unit test per sentence when a believable high-level scenario can cover the behaviour.
- Do not silently skip unmodelled concepts. Mark expected gaps or create `.plan` items.
- Keep citations exact: document, section, and accepted source-unit id.
- Phraseology-only rules are valid programme items, but may map to a later phraseology layer if current rendered-transmission support is insufficient.

## Acceptance Criteria
- [ ] All currently accepted `icao9432-extracted` source units are inventoried.
- [ ] Each source unit is assigned to an operational area and a proposed chunk/epic.
- [ ] Each source unit has an impact classification: testable now, needs observation fact, needs policy concept, needs sim/controller/pilot model work, phraseology later, expected gap, or not applicable.
- [ ] Policy-sensitive source units are explicitly marked and include the policy concept likely needed in code.
- [ ] A first pass of chunk epics is proposed, with order, scope, expected test count, and expected implementation fallout.
- [ ] The plan identifies the first chunk to test-author, with clear boundaries and expected failing tests.
- [ ] Review considerations are addressed before any chunk epic proceeds to test authoring.

## Boundaries
In scope:
- ICAO 9432 accepted source-unit inventory and mapping.
- Programme/chunk planning.
- Test-authoring strategy and failure taxonomy.
- Identification of policy concepts and observation gaps.

Out of scope for this epic:
- Fixing controller/pilot/sim behaviour to make failing tests pass.
- Building a full phraseology renderer/linter unless required to classify the map.
- Re-curating pending/rejected source units.
- Treating non-9432 sources as part of this programme, except as precedence/context notes.

## Decision Context
The chosen workflow is chunked source mapping followed by test authoring, with implementation repair separate. This preserves the firewall between regulatory test intent and implementation details. It also gives progress visibility: we can say what part of ICAO 9432 is covered, blocked, policy-dependent, or out of current scope.

The project should err on simple, legible tests even if the harness/reporting layer is complex. The authored tests are a regulatory evidence wall, not an interpreter for the source-unit corpus.

## Review Considerations

### FP / type safety
Mapping statuses and normative kinds should become closed vocabularies when promoted into code or generated artifacts. Avoid stringly statuses in permanent test logic. Policy concepts should become typed values, not booleans or loose strings. If a later implementation adds state fields for policy or evidence, audit all copy/mutation sites and reversal paths.

### Test architecture
Tests should be high-level, black-box, and believable, following `docs/test-standards.md`. Source ids provide provenance; assertions must still exercise real sim/protocol behaviour. Expected failing tests are acceptable only if they fail explicitly as part of a known implementation-repair epic, not by being disabled or skipped.

### Impact
This programme will couple regulatory coverage to the evidence DSL and observation facts. That is intended, but the mapping must prevent accidental coupling to current controller internals. The largest risks are overclaiming coverage, making tests too verbose, and hiding policy choices inside assertions. Reversal is possible if each chunk epic is isolated and source-unit status is tracked independently.

### Operational correctness
All operational claims must cite ICAO Doc 9432 source-unit ids and sections. Where ICAO 9432 gives guidance rather than hard law, the map must distinguish guidance, phraseology, example dialogue, and operational policy. If a source unit is better governed by ICAO Doc 4444, SERA, or CAP 413 precedence, note that rather than pretending 9432 alone settles the behaviour.
