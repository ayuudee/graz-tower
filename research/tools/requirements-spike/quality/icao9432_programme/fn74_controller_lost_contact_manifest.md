# fn-74 Controller Lost-Contact Movement Manifest

Epic: `fn-74-icao-9432-emergency-1g-controller-lost`

Scope: ICAO 9432 Chapter 9 controller-side aircraft-contact failure residuals
in `chunk_08_distress_urgency_comms_failure`.

## Movement Rows

| Source unit | Current state | Target state | Evidence intent |
|---|---|---|---|
| `icao9432-extracted::communications_failure_9_5_en::bb66a050093251c2` | `model-gap + policy-blocked` | `covered-green configured controller route-aircraft relay request branch` | After failed calls on frequencies the aircraft is believed to be listening on, the controller may ask route aircraft to call or relay to the lost-contact aircraft. |
| `icao9432-extracted::communications_failure_9_5_en::24c806b040f4ef5e` | `model-gap + policy-blocked` | `covered-green configured controller inter-station relay request branch` | After failed calls on frequencies the aircraft is believed to be listening on, the controller may ask other stations to call or relay. |
| `icao9432-extracted::communications_failure_9_5_en::75055714e70d4560` | `model-gap + policy-blocked` | `covered-green configured ATC non-clearance blind-transmission branch` | After relay attempts fail and the aircraft is believed listening, ATC may blind-transmit non-clearance messages. |

## Evidence Shape

- Use closed local source-unit projection types in
  `Icao9432ControllerLostContactSourceBackedTest`.
- Keep the workflow sequence explicit:
  - calls on frequencies the aircraft is believed to be listening on have failed;
  - route-aircraft relay request is available only after that source precondition;
  - inter-station relay request is available only after that source precondition;
  - non-clearance blind transmission is available only after relay failures and
    believed-listening evidence.
- Keep blind clearances separate: fn-74 must reject blind clearance requests;
  fn-71 remains the source-backed blind-clearance prohibition/exception slice.
- Include wrong-path guards for routine traffic, pilot-originated
  communications failure, generic emergency labels, generic direct-contact failure,
  missing relay failures, aircraft not believed listening, blind clearance, and
  phraseology-only evidence.
- Add a reset/recovery witness clearing contact state, requested relay actor,
  relay attempt result, believed-listening marker, blind-message type,
  blind-transmission state, and policy marker.

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
- Any-means distress communication / broader station assistance:
  - `4b37e039e7eb8afa`
- Emergency-descent follow-up specific-instruction necessity:
  - `c71568b00fb1535e`
- Pilot safety-doubt trigger:
  - `87b67820c6092a98`

## Review Considerations

- FP / type safety: local projections must use closed enum/sealed types and
  typed rejected transitions for type-valid unsupported paths.
- Test architecture: source-backed evidence must prove sequencing and guards,
  not merely cite the source unit. Exact-union accounting must stay at 46 refs.
- Impact: no production controller lost-contact scheduler or rendered
  phraseology is implied. Any production change requires fresh impact review.
- Operational correctness: controller lost-contact relay, ATC-originated
  non-clearance blind transmission, pilot-originated blind transmission, blind
  clearances, and Annex 10 conformance are distinct source obligations.
