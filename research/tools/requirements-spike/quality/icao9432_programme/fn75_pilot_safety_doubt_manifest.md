# fn-75 Pilot Safety-Doubt Movement Manifest

Epic: `fn-75-icao-9432-emergency-1h-pilot-safety`

Scope: ICAO 9432 Chapter 9 pilot-side safety-doubt assistance trigger.

## Movement Rows

| Source unit | Current state | Target state | Evidence intent |
|---|---|---|---|
| `icao9432-extracted::distress_urgency_intro_9_1_en::87b67820c6092a98` | `model-gap + policy-blocked` | `covered-green configured pilot safety-doubt assistance branch` | When the pilot has a typed doubt about flight safety and explicit policy says assistance is warranted, the pilot seeks assistance. |

## Evidence Shape

- Use closed local source-unit projection types in a focused source-backed test.
- Keep the workflow explicit:
  - a pilot-observed safety-doubt condition exists;
  - an explicit operational policy authorizes seeking assistance;
  - the resulting assistance request carries the safety-doubt reason/source
    witness.
- Keep generic emergency classification separate: MAYDAY/PAN PAN labels alone
  do not prove this source unit unless tied to the safety-doubt trigger.
- Keep rendered phraseology separate: the test must not claim spoken wording,
  repetition, speech rate, or emergency-message order.

## Explicit Non-Targets

Remain executable residual gaps:

- Annex 10 conformance:
  - `c30159856a1a5e7a`
  - `c1c14fab53a608c6`
- Rendered emergency and communications-failure phraseology/order:
  - `13d1c2accd0f7a73`
  - `9907744b4723d14c`
  - `bf04647e26f9c018`
  - `f0e99a4c08ea0cb3`

## Review Considerations

- FP / type safety: local projection types must be closed and total; wrong
  branches return typed rejection.
- Test architecture: source-backed evidence must prove the safety-doubt trigger
  and explicit policy gate, not merely cite the source unit.
- Impact: no production pilot decision engine or rendered phraseology is
  implied. Any production change requires fresh impact review.
- Operational correctness: fn-75 claims only ICAO Doc 9432 §9.1.7's advice to
  seek assistance when flight safety is in doubt.
