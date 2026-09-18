# ICAO 9432 implementation roadmap review

Status: reviewed and incorporated.

## Review brief

Review `implementation_roadmap.md` for:

- fit to the actual mission: source-mapped regulatory test wall, not verbatim
  implementation of ICAO 9432;
- sequencing and dependency correctness;
- false-green risk;
- hidden coupling to current simulator internals;
- compliance with project commandments and `docs/test-standards.md`;
- whether child epics are sized so test authoring and behavior repair can stay
  firewalled.

## Findings

### High: fn-59 could close before its own deliverables exist

The Flow spec required review/red-team artifacts and child epics, but the first
draft only listed proposed epic names while review files were still pending.

Resolution requirement: fn-59 must not close until review/red-team findings are
incorporated and child epics exist with the relevant constraints encoded.

### High: emergency work was still too large

The first roadmap split `EMERGENCY-1` into only two epics, hiding distinct
failure modes: classification/message payload, priority/radio silence,
emergency descent safeguarding, and communications failure/SSR/blind
transmissions.

Resolution requirement: split emergency work by proof surface and require exact
source-unit subsets from the chunk 08 expected-gap decomposition before
implementation starts.

### High: `POLICY-1` could become a dumping ground

The first roadmap named broad policy families but did not require a
source-unit-to-policy contract.

Resolution requirement: build a policy matrix before any policy-green-out work.
The matrix must include source id, modality, policy owner, allowed configured
alternatives, evidence for the configured branch, and what is not being proven
universally.

### Medium: phraseology architecture needed an obligation taxonomy

Rendered utterance storage and helper assertions are insufficient unless each
phraseology source unit is classified by obligation type.

Resolution requirement: `PHRASE-1A` must define an obligation taxonomy and
structured rendered-token evidence with source/protocol provenance.

### Medium: pushback before vehicles could create throwaway actor concepts

Pushback uses ground-service facts while vehicle work later introduces
non-aircraft actors.

Resolution requirement: pushback must either introduce a minimal shared
ground-service event concept or explicitly scope itself to aircraft movement
plus `GroundCrewSignalReceived`, without pre-designing full vehicle actors.

### Medium: child epics need exact anti-overcoverage gates

The roadmap said to record units that moved green, but did not require exact
guards for units that must not move.

Resolution requirement: every child epic must declare green-targeted,
remain-blocked, and untouched source units, plus wrong-path evidence that must
not satisfy the claim.

## Resolution

Resolved in `implementation_roadmap.md` by adding execution guardrails,
splitting vehicle and emergency work, tightening `PHRASE-1A`, requiring a
policy matrix, and requiring source-unit movement manifests for child epics.
The follow-on Flow epics must carry these constraints in their acceptance
criteria.
