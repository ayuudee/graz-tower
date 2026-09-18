# fn-68-icao-9432-emergency-1a-emergency.1 Build emergency classification and payload movement manifest

## Description
Build the source-unit movement manifest for fn-68. Select only the ICAO 9432
Chapter 9 rows that can honestly move with typed emergency classification and
emergency message payload evidence. Leave priority/silence, assistance/relay,
emergency descent, communications failure, SSR, and rendered emergency
phraseology rows blocked unless the manifest explicitly justifies moving a
split branch.

## Acceptance
- [ ] Manifest lists every candidate fn-68 source unit with current state,
  target state, and required evidence.
- [ ] Distress/urgency classification rows are separated from emergency
  message payload rows.
- [ ] Rendered MAYDAY/PAN PAN wording and message ordering remain
  `PHRASE-1` unless rendered phraseology evidence is implemented.
- [ ] Later fn-69/fn-70/fn-71 source units remain blocked in the manifest.
- [ ] Review considerations cover FP/type safety, test architecture, impact,
  and operational correctness.

## Done summary
Built fn-68 source-unit movement manifest for narrow emergency classification and structured distress payload branches. Impact review recommended local projection over existing PilotTransmissionFact rather than a new EvidenceFactPayload leaf; manifest updated accordingly.
## Evidence
- Commits:
- Tests:
- PRs: