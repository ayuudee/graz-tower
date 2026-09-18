# fn-72 Assistance Relay Frequency Manifest

Scope: ICAO 9432 Chapter 9 emergency assistance, emergency relay, emergency
frequency-policy, and emergency interference-suppression structured projection
evidence.

## Targeted Movement

| Source unit | Current state | Target state | Evidence required |
|---|---|---|---|
| `icao9432-extracted::distress_urgency_intro_9_1_en::9c34a1b8d6d623fa` | `model-gap` | `covered-green structured emergency assistance actor branch` | A non-addressed station or aircraft can reply and assist when the called ground station does not reply. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::2fee92c222323e6a` | `model-gap` | `covered-green structured alternate emergency frequency branch` | Emergency communications can select another frequency only when a typed assistance/frequency policy says it is necessary or desirable. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::24f94381ed9e8ce1` | `model-gap + policy-blocked` | `covered-green configured emergency frequency-continuity policy branch` | Configured policy normally keeps distress communications on the current frequency and moves only when another frequency better assists the emergency. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::82ee7048517d8478` | `model-gap + policy-blocked` | `covered-green configured assistance-content policy branch` | A replying station's assistance action carries advice, information, and/or instructions selected by an explicit assistance-content policy. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::8e9f7818b91d08c3` | `model-gap + policy-blocked` | `covered-green structured intercepted-distress relay branch` | An intercepting aircraft can acknowledge and broadcast an unacknowledged distress message as a distinct relay actor. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::cb12c2f9b97c64b7` | `model-gap + policy-blocked` | `covered-green configured emergency initial-frequency policy branch` | Distress and urgency calls initially use the frequency in use under the configured current-frequency policy. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::06f7a72397c325ac` | `model-gap + policy-blocked` | `covered-green configured distress interference-suppression branch` | During active distress traffic, superfluous uninvolved transmissions are suppressed under an explicit operational-guidance policy. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::5df94af7a64c3f5d` | `model-gap + policy-blocked` | `covered-green configured urgency interference-suppression branch` | During active urgency traffic, other-station interference is suppressed under the same explicit policy without claiming distress priority. |

## Explicit Non-Targets

These rows stay blocked in fn-72:

- Annex 10 conformance: `c30159856a1a5e7a`, `c1c14fab53a608c6`.
- Emergency rendered phraseology: `13d1c2accd0f7a73`,
  `9907744b4723d14c`, `bf04647e26f9c018`, `f0e99a4c08ea0cb3`, plus split
  phraseology residuals already tracked from fn-68/fn-71.
- Distress/urgency message addressing and payload-policy rows:
  `27a450fa3bfcbc0a`, `94e94be0c800c982`, `1de475a788206cc8`,
  `b95d7bb1cb409bca`.
- Emergency-descent specific-instruction necessity: `c71568b00fb1535e`.
- Communications-failure controller lost-contact workflow:
  `bb66a050093251c2`, `24c806b040f4ef5e`, `75055714e70d4560`.
- Distress assistance/any-means residual from `4b37e039e7eb8afa`; fn-72 does
  not add a general "any means" model.

## Wrong-Path Guards

- Ordinary routine traffic must not activate emergency assistance or frequency
  policy.
- Routine no-reply or frequency-transfer failures must not satisfy emergency
  assistance rows.
- Generic MAYDAY/PAN PAN without a no-reply, assisting actor, or active
  emergency traffic state must not satisfy assistance or relay rows.
- A distressed aircraft assisting itself must not satisfy non-addressed
  station/aircraft assistance rows.
- The originally called ground station must not satisfy "another station or
  aircraft" assistance evidence.
- Non-emergency relays must not satisfy intercepted-distress relay evidence.
- Phraseology-only evidence must not satisfy assistance, relay, frequency, or
  suppression claims.
- Communications-failure controller relay / blind non-clearance evidence must
  not satisfy emergency assistance/frequency rows.

## Reset / Reversal Requirements

Emergency recovery must clear:

- active emergency traffic marker;
- called station / no-reply marker;
- assisting actor;
- selected emergency frequency and frequency-change reason;
- suppressed-transmission records;
- intercepted-distress relay state;
- configured assistance-content action.

## Review Considerations

FP / type safety:

- Use closed enums/sealed types for emergency type, actor role, frequency
  policy, assistance action, and suppression result.
- No catch-all `else`; unsupported but type-valid states must return typed
  rejection in the local projection.

Test architecture:

- Tests are high-level source-mapped projection tests and cite only the eight
  rows above.
- Expected-gap exact-union coverage must remain green for all 46 chunk-08
  source units.

Impact:

- fn-72 should not require production behavior changes. If implementation
  discovers that production state is necessary, stop and run a fresh impact
  review before broadening scope.
- Policy branches are configured evidence, not universal law.

Operational correctness:

- Claims are limited to ICAO 9432 Chapter 9 source units. No CAP 413 or Annex
  10 substitution is used to green these rows.
