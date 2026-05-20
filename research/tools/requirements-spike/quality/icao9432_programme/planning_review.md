# FN46 planning review: ICAO 9432 programme

## Plan
1. Inventory accepted ICAO 9432 source units from the registry by `lifecycle.state = accepted`.
2. Classify each unit by operational area, normative kind, policy sensitivity, testability, evidence target, and blocker.
3. Group units into chunk epics sized for source-mapped test authoring, not implementation repair.
4. Review the map for overclaiming, hidden implementation coupling, missing policy concepts, and weak first-chunk selection.

## Review before implementation
- The plan preserves the user-requested firewall: mapping/test authoring is separate from controller/pilot/sim fixes.
- The plan treats policy as first-class. A source unit whose correct behaviour depends on local/operational policy is marked `needs-policy-type`, not loosened into vague assertions.
- The main risk is heuristic classification. Mitigation: store raw inventory beside classification, make classifications explicit and reviewable, and require chunk epics to re-check citations before writing tests.
- The first chunk should avoid phraseology-heavy and vehicle/emergency-heavy areas; communications/readback/transfer gives breadth while staying close to existing evidence DSL capability.

## Decision
Proceed with the generated inventory/classification and then red-team the output before using it to create chunk test-authoring epics.
