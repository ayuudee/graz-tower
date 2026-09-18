# fn-72-icao-9432-emergency-1e-assistance-relay.2 Implement assistance relay source-backed evidence

## Description
Implement the approved fn-72 evidence surface. Prefer local/source-mapped
projection evidence unless impact review requires production state. Add
high-level source-backed tests that distinguish emergency assistance/relay,
frequency-policy, and interference-suppression behavior from ordinary radio,
generic emergency, phraseology-only evidence, message payload/addressing
evidence, and controller lost-contact workflow.

## Acceptance
- [ ] Source-backed tests cite only rows declared in the manifest.
- [ ] Assistance/relay actors are explicit and distinct from the distressed
  aircraft and originally called station.
- [ ] Emergency frequency-continuity and alternate-frequency branches are
  policy-bound and reset cleanly.
- [ ] Interference/superfluous-transmission suppression is represented as a
  policy branch, not a universal hidden default.
- [ ] Wrong-path guards reject ordinary radio/no-reply/frequency-transfer,
  generic emergencies, non-emergency relays, phraseology-only evidence,
  message-addressing-only evidence, and communications-failure controller
  workflow evidence.
- [ ] Communications-failure relay and ATC-originated blind non-clearance rows
  remain blocked and are not moved by this epic.
- [ ] Chunk-08 expected-gap exact-union test remains green.
- [ ] Focused tests pass.

## Done summary
Implemented fn-72 source-backed assistance, relay, frequency-policy, and interference-suppression evidence; updated chunk 08 ledgers and residual gap groups.
## Evidence
- Commits:
- Tests:
- PRs: