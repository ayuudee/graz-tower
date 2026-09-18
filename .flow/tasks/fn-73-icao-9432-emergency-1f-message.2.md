# fn-73-icao-9432-emergency-1f-message.2 Implement emergency message policy source-backed evidence

## Description
Implement the fn-73 source-backed evidence and ledger movement for emergency
message addressing and payload-policy rows.

## Acceptance
- [ ] Add `Icao9432EmergencyMessagePolicySourceBackedTest` or equivalent
  source-backed test with closed local projection types.
- [ ] Distress addressing policy covers current station and responsible-area
  station without claiming rendered wording.
- [ ] Relayed/non-distressed distress-message variation is gated by clear
  circumstances explicitly carried in structured evidence.
- [ ] Urgency payload policy covers circumstance-required payload element
  selection, including rejection of a missing required element and permitted
  omission of a non-required element.
- [ ] Urgency addressing/frequency policy covers current frequency and current
  or responsible station.
- [ ] Wrong-path guards reject routine traffic, phraseology-only evidence,
  generic emergency labels, cross-use of distress/urgency contexts, unclear
  relay variation, and production communications-failure workflow evidence.
- [ ] `Icao9432ModelGapSourceUnitSpecTest` residual groups remain executable
  and exact-union coverage remains green.
- [ ] The residual emergency message gap test is rewritten to describe only
  rendered phraseology/order after the four policy rows move.
- [ ] Coverage report, expected gaps, source plan, and blocker manifest are
  updated only for moved rows.

## Done summary
Implemented fn-73 emergency message addressing and payload-policy source-backed evidence; updated chunk 08 residual grouping and ledgers.
## Evidence
- Commits:
- Tests:
- PRs: