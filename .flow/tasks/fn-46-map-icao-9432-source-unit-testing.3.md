# Draft chunk epics and first implementation order

## Description
Turn the classified map into a basic programme plan: one Flow epic per coherent chunk. Chunks should be sized for test authoring and review, not necessarily by document section. Each chunk should say what tests will be authored, which are expected to fail, what implementation areas will likely need separate repair, and what policy/observation concepts block progress.

The first chunk should be chosen for high learning value and manageable blast radius. Prefer a slice that uses the settled evidence DSL surface and keeps implementation fixes separate.

## Acceptance Criteria
- [ ] Proposed chunk epics cover all accepted ICAO 9432 source units or explicitly defer/exclude them.
- [ ] Each chunk has a title, scope, source-unit count, operational areas, expected test count, likely failure modes, and implementation fallout.
- [ ] The first chunk is nominated with rationale and clear boundaries.
- [ ] Any required `.plan` backlog additions are identified for observation facts, policy concepts, or model gaps.
- [ ] The plan preserves the testing/implementation firewall: test-authoring epics do not include behaviour fixes.
