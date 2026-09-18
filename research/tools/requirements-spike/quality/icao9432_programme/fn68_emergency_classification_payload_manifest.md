# fn-68 Emergency Classification And Payload Manifest

Epic: `fn-68-icao-9432-emergency-1a-emergency`

Source scope: ICAO Doc 9432 Chapter 9 §9.1.2-§9.1.3 and §9.2.1.1,
chunk `chunk-08-distress-urgency-comms-failure`.

## Mission

Move only the source-unit branches that can be honestly proven from typed
emergency pilot transmissions: distress versus urgency classification and
structured emergency-message payload fields. Do not claim emergency priority,
frequency silence, assistance/relay, emergency descent safeguarding,
communications-failure workflow, SSR emergency codes, rendered MAYDAY/PAN PAN
wording, or rendered element ordering.

The existing protocol already has `PilotTransmission.Emergency`,
`EmergencyType.MAYDAY`, `EmergencyType.PAN_PAN`, and `EmergencyDetails`.
fn-68 should add the missing source-mapped evidence surface around those types,
not invent normal-flight emergency behaviour.

## Source-Unit Movement Manifest

| Source unit | ICAO 9432 section | Current state | fn-68 target state | Evidence required |
|---|---|---|---|---|
| `icao9432-extracted::distress_urgency_intro_9_1_en::8b3b3b4117c04807` | §9.1.2(a) | `model-gap` | `covered-green structured distress-classification branch` | A typed emergency transmission classified as distress must carry the semantics "serious/imminent danger" and "immediate assistance required". |
| `icao9432-extracted::distress_urgency_intro_9_1_en::c959f0325390e7fe` | §9.1.2(b) | `model-gap` | `covered-green structured urgency-classification branch` | A typed emergency transmission classified as urgency must carry the semantics "safety concern" and "immediate assistance not required". |
| `icao9432-extracted::distress_urgency_intro_9_1_en::d742970b22d8de26` | §9.1.3 | `model-gap + phraseology-later` | `split: protocol emergency-type discriminator mapping covered-green; rendered spoken-word identification phraseology-later` | `EmergencyType.MAYDAY` must map to distress and `EmergencyType.PAN_PAN` must map to urgency. Repeated initial call, pronunciation/rendering, and textual phraseology remain blocked by `PHRASE-1`. |
| `icao9432-extracted::distress_messages_9_2_en::23c9f447cd6c7814` | §9.2.1.1 | `model-gap + phraseology-later` | `split: all-fields-present structured distress-message payload representation covered-green; rendered wording/order phraseology-later` | The evidence surface can represent an all-fields-present distress message with station addressed, aircraft identification, distress nature, intentions, position, level, heading, and useful information as structured fields. fn-68 does not model field availability, operational omission, or compliance of partial distress messages. Rendered text/order remains blocked. |

Rows deliberately unchanged:

- `f0e99a4c08ea0cb3`: rendered distress-message element order remains
  `PHRASE-1`; structured fields alone do not prove spoken order.
- `bf04647e26f9c018`: repeated initial MAYDAY/PAN PAN is rendered
  phraseology and remains `PHRASE-1`.
- `13d1c2accd0f7a73` and `9907744b4723d14c`: speech rate, distinctness, and
  phraseology adaptation remain phraseology/time-pressure gaps.
- `1de475a788206cc8`: urgency-message payload "as circumstances require"
  remains policy-blocked unless a later pass models urgency payload policy.
- `27a450fa3bfcbc0a`: emergency addressing policy remains policy-blocked.
- All priority, silence, assistance/relay, emergency descent, communications
  failure, SSR, and blind-transmission rows remain for fn-69/fn-70/fn-71.

## Implementation Plan

1. Add a small closed source-unit projection over existing pilot transmission
   facts.
   - Do not add a new global `EvidenceFactPayload` leaf in fn-68.
   - Derive the projection only from `EvidenceFactPayload.PilotTransmissionFact`
     whose transmission is `PilotTransmission.Emergency`.
   - Model `Distress` and `Urgency` as closed classification leaves with
     explicit semantic fields for immediate assistance and safety/danger.

2. Add structured payload evidence.
   - Use the pilot speaker aircraft id for aircraft identification.
   - Read station addressed, nature, intentions, position, level, heading, and
     useful-information fields from `EmergencyDetails`.
   - Treat useful information as a structured aggregate of optional
     persons-on-board, fuel-remaining, and remarks.
   - Do not infer rendered order or spoken wording from the payload.

3. Add source-mapped tests.
   - Distress classification source unit proves immediate assistance and
     serious/imminent danger semantics.
   - Urgency classification source unit proves safety concern without immediate
     assistance.
   - MAYDAY/PAN PAN classification split proves typed mapping only, with
     phraseology tail still blocked.
   - Distress message payload split proves the structured fields with an
     all-fields-present distress case.
   - Negative tests: a routine pilot transmission does not produce emergency
     evidence; urgency does not satisfy the distress-message branch; and a
     MAYDAY/distress message with a missing required structured field does not
     satisfy the full structured-payload proof.

4. Update chunk 08 ledgers and gap specs.
   - Remove moved rows/split branches from model-gap groups.
   - Keep exact-union accounting honest after moving the declared branches.
   - Update `implementation_blocker_manifest.csv`, `source_plan.md`,
     `coverage_report.md`, and `expected_gaps.md`.

## Review Considerations

### FP / Type Safety

- Classification must be closed (`Distress`, `Urgency`), not a free string.
- Optional emergency payload fields are acceptable where ICAO says the message
  contains as many elements as possible; tests for the full structured branch
  should use the all-fields-present case.
- fn-68 deliberately avoids a new `EvidenceFactPayload` leaf. If later fn-69,
  fn-70, or fn-71 needs reusable emergency facts, that later pass must audit
  fact-kind metadata, report formatting, and `FACTS_PER_RECORD` sequence
  assumptions.

### Test Architecture

- Tests should be source-mapped scenarios/trace projections, not constructor
  tests.
- Ordinary `Report`, `Readback`, or normal VFR transmissions must not satisfy
  emergency source units.
- The tests must assert residual blockers for phraseology/order/policy rather
  than silently greening adjacent rows.

### Impact

- This should not change pilot planning, controller rules, radio scheduling,
  priority arbitration, or SSR state.
- Later fn-69/fn-70/fn-71 epics should be able to build on the classification
  and payload evidence without treating it as proof of priority, silence,
  descent, or communications-failure behaviour.

### Operational Correctness

- ICAO Doc 9432 §9.1.2 defines distress as serious/imminent danger requiring
  immediate assistance.
- ICAO Doc 9432 §9.1.2 defines urgency as a safety condition that does not
  require immediate assistance.
- ICAO Doc 9432 §9.1.3 associates MAYDAY with distress and PAN PAN with
  urgency; rendered spoken-word proof remains phraseology work.
- ICAO Doc 9432 §9.2.1.1 says a distress message should contain as many listed
  elements as possible and says "if possible" for order. fn-68 proves the
  all-fields-present representation branch only; it does not judge operational
  compliance of partial distress messages or prove ordering.
