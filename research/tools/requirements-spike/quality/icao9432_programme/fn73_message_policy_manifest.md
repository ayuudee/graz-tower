# fn-73 Message Policy Movement Manifest

Epic: `fn-73-icao-9432-emergency-1f-message`

Scope: ICAO 9432 Chapter 9 emergency message addressing and payload-policy
residuals in `chunk_08_distress_urgency_comms_failure`.

## Movement Rows

| Source unit | Current state | Target state | Evidence intent |
|---|---|---|---|
| `icao9432-extracted::distress_messages_9_2_en::27a450fa3bfcbc0a` | `model-gap + policy-blocked` | `covered-green configured distress-message addressing policy branch` | A structured distress-message policy selects either the station currently in communication with the aircraft or a station responsible for the area. |
| `icao9432-extracted::distress_messages_9_2_en::94e94be0c800c982` | `model-gap + policy-blocked` | `covered-green configured relayed-distress payload-variation policy branch` | A non-distressed transmitting station may vary distress-message elements only when a clear circumstance is explicitly carried in the structured message-policy projection. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::1de475a788206cc8` | `model-gap + policy-blocked` | `covered-green configured urgency-message payload policy branch` | An urgency-message policy selects required distress-message-style elements according to circumstances without claiming rendered order. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::b95d7bb1cb409bca` | `model-gap + policy-blocked` | `covered-green configured urgency addressing and frequency policy branch` | Urgency calls use the frequency in use and address the station in communication or the responsible-area station under explicit policy. |

## Evidence Shape

- Use closed local source-unit projection types in
  `Icao9432EmergencyMessagePolicySourceBackedTest`.
- Keep all policy choices explicit:
  - distress addressed to current station;
  - distress addressed to responsible-area station;
  - relayed/non-distressed variation allowed only when a clear circumstance is
    explicitly carried/stated in structured evidence;
  - urgency payload elements selected as circumstance-required;
  - urgency calls use current frequency and current/responsible station.
- Include wrong-path guards for routine traffic, phraseology-only evidence,
  generic emergency labels without message-policy context, unclear relayed
  variation, distress context reused as urgency evidence, urgency context reused
  as distress evidence, and controller communications-failure workflow evidence.
- Include a non-vacuous urgency payload-policy proof: reject a message missing a
  required circumstance element, and accept omission of an element that the
  configured circumstances do not require.
- Rewrite the remaining emergency message residual gap spec so it describes
  only rendered phraseology/order after these policy rows move.
- Add a reset/recovery witness that clears emergency kind, sender role,
  addressee, responsible-area marker, payload-element selection, variation
  reason, frequency selection, and policy marker.

## Explicit Non-Targets

Remain executable residual gaps:

- Rendered phraseology/order:
  - `13d1c2accd0f7a73`
  - `9907744b4723d14c`
  - `bf04647e26f9c018`
  - `f0e99a4c08ea0cb3`
- Annex 10 conformance:
  - `c30159856a1a5e7a`
  - `c1c14fab53a608c6`
- Any-means distress communication / broader station assistance:
  - `4b37e039e7eb8afa`
- Emergency-descent follow-up specific-instruction necessity:
  - `c71568b00fb1535e`
- Controller communications-failure relay and blind non-clearance workflow:
  - `bb66a050093251c2`
  - `24c806b040f4ef5e`
  - `75055714e70d4560`

## Review Considerations

- FP / type safety: local projections must use closed enum/sealed types and
  typed rejected transitions for type-valid unsupported paths.
- Test architecture: source-backed evidence must prove the policy branch, not
  merely cite the source unit. Exact-union accounting must stay at 46 refs.
- Impact: no production emergency message routing or phraseology renderer is
  implied. Any production change requires fresh impact review.
- Operational correctness: ICAO 9432 Chapter 9 message addressing/payload
  claims remain distinct from rendered MAYDAY/PAN PAN phraseology and Annex 10
  procedure conformance.
