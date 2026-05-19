# fn-43-build-permanent-evidence-dsl-audit-core.3 Implement protocolEvidence and simEvidence DSL shell

## Description
Implement the public authoring facade.

Build:
- `protocolEvidence(name) { ... }`;
- `simEvidence(name) { observe { ... }; ... }`;
- `source`, `golden`, `regression`, and `invariant` case forms;
- `structuralReadback` helper with honest naming;
- case builders that produce typed audit records without exposing fact ids,
  provenance, report serialization, or monitor vocabulary to ordinary tests.
- claim-kind metadata for structural protocol, sim-observed source behaviour,
  golden, regression, invariant, and expected-gap cases.

Call-site simplicity is the primary design artifact.

## Acceptance
- [ ] Public call sites can express at least one protocol readback case and one LOWG ordering case.
- [ ] Public case forms produce typed audit records.
- [ ] Every case records a claim kind.
- [ ] Structural readback reports cannot be worded as phraseology compliance or actual pilot-exchange evidence.
- [ ] Ordinary source cases use typed catalog refs from task 1.
- [ ] Monitor/framework vocabulary is absent from test call sites.
- [ ] A failing/missing `observe` block fails loudly.

## Done summary
TBD

## Evidence
- Commits:
- Tests:
- PRs:
