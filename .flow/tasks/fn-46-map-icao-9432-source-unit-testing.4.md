# Review and red-team the 9432 programme map

## Description
Review the programme map before starting chunk test authoring. The review should look for overclaiming, hidden implementation coupling, missing policy concepts, brittle test granularity, ambiguous source-unit semantics, and any corner-cutting that would undermine the project goal.

This is a planning review, not an implementation review. It should decide whether the map is good enough to begin the first chunk epic and what must be corrected first.

## Acceptance Criteria
- [ ] The review checks total coverage of accepted ICAO 9432 units against the inventory.
- [ ] The review challenges every `testable-now` classification for evidence adequacy.
- [ ] The review identifies source units that are guidance/policy/example rather than mandatory behaviour.
- [ ] The review confirms the first chunk can author tests without requiring implementation fixes in the same epic.
- [ ] The review produces a final recommendation: proceed, revise map, or split/reorder chunks.
