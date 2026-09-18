# fn-71 Communications Failure Movement Manifest

Epic: `fn-71-icao-9432-emergency-1d-communications`

Scope: ICAO Doc 9432 Chapter 9 communications-failure, blind-transmission, and
SSR code structured evidence only. This slice does not implement rendered
phraseology, production radio failure detection, production scheduler behavior,
route database lookup, relay tasking, or full Annex 10 conformance.

## Rows To Move

### Communications Failure Routing Projection

| Source unit | Current state | Target state | Evidence required |
|---|---|---|---|
| `icao9432-extracted::communications_failure_9_5_en::f05016444e2b8808` | `model-gap` | `covered-green structured communications-failure alternate-frequency branch` | A typed communications-failure state after failed designated-frequency contact selects another route-appropriate frequency. |
| `icao9432-extracted::communications_failure_9_5_en::fcb3a49672165b4f` | `model-gap` | `covered-green structured communications-failure alternate-contact branch` | After alternate-frequency contact fails, the model selects other aircraft or other aeronautical stations on route-appropriate frequencies. |

Ordinary no-reply, routine missed calls, and ordinary frequency transfer failure
are not communications-failure evidence. fn-71 evidence must start from typed
communications failure after attempted contact on the designated frequency.

### Blind Transmission Projection

| Source unit | Current state | Target state | Evidence required |
|---|---|---|---|
| `icao9432-extracted::communications_failure_9_5_en::bc9bb12804033b07` | `model-gap + phraseology-later` | `split: structured blind-transmission mode/repetition branch covered-green; rendered TRANSMITTING BLIND prefix remains phraseology-later` | Blind-transmission mode emits the intended message twice after contact attempts fail. Rendered prefix wording is not claimed. |
| `icao9432-extracted::communications_failure_9_5_en::abbc376a430003b0` | `model-gap + phraseology-later` | `split: structured blind-transmission addressee branch covered-green; rendered addressee phraseology remains phraseology-later` | Blind-transmission metadata can carry explicit addressees when necessary. Necessity policy and rendered wording are not claimed. |
| `icao9432-extracted::communications_failure_9_5_en::975a63151706f68f` | `model-gap` | `covered-green structured blind-message repetition branch` | Intended message is scheduled with a complete repetition. |
| `icao9432-extracted::communications_failure_9_5_en::045c2e33f59f5ede` | `model-gap` | `covered-green structured blind next-transmission-time branch` | Blind-transmission payload advises the time of the next intended transmission. |
| `icao9432-extracted::communications_failure_9_5_en::7900c606e05e509b` | `model-gap` | `covered-green structured communications-failure continuation-intention branch` | Receiver-failure blind-transmission payload carries PIC intentions for flight continuation only when the aircraft is provided ATC/advisory service. |
| `icao9432-extracted::communications_failure_9_5_en::78c73a75fab644f4` | `model-gap + phraseology-later` | `split: structured receiver-failure blind-transmission branch covered-green; rendered receiver-failure prefix remains phraseology-later` | Receiver-failure mode is distinct from generic blind-transmission mode. Rendered wording is not claimed. |

### SSR Code Projection

| Source unit | Current state | Target state | Evidence required |
|---|---|---|---|
| `icao9432-extracted::communications_failure_9_5_en::91e7d233bf3b64ff` | `model-gap` | `covered-green structured radio-failure SSR 7600 branch` | Radio/communications failure with SSR equipment selects code 7600. |
| `icao9432-extracted::distress_messages_9_2_en::4b37e039e7eb8afa` | `model-gap` | `split: structured distress SSR 7700 branch covered-green; distress assistance/any-means branch remains model-gap` | Distress with SSR equipment can select code 7700. Station assistance and any-means communication behavior remain out of scope. |

### Blind Clearance Projection

| Source unit | Current state | Target state | Evidence required |
|---|---|---|---|
| `icao9432-extracted::communications_failure_9_5_en::b73dda299970c2f3` | `model-gap` | `covered-green structured blind-clearance prohibition/exception branch` | Blind ATC clearances are rejected unless the clearance originator explicitly requests blind transmission. |

## Rows Left Blocked

| Source unit | State | Reason |
|---|---|---|
| `icao9432-extracted::communications_failure_9_5_en::bb66a050093251c2` | `model-gap + policy-blocked` | Station request to route aircraft to call/relay needs controller intervention and relay workflow policy. |
| `icao9432-extracted::communications_failure_9_5_en::24c806b040f4ef5e` | `model-gap + policy-blocked` | Station request to other stations to call/relay needs inter-station relay workflow policy. |
| `icao9432-extracted::communications_failure_9_5_en::75055714e70d4560` | `model-gap + policy-blocked` unless implementation proves only blind non-clearance branch | Controller blind non-clearance messages need a clearance-timing policy and production ATC-originated blind-transmission workflow. |
| `icao9432-extracted::communications_failure_9_5_en::c1c14fab53a608c6` | `model-gap` | Annex 10 conformance is an external-procedure conformance claim, not proved by the local 9432 projection model. |

## Proposed Evidence Surface

Use a narrow source-backed communications failure scenario with:

- closed communication state: normal radio, typed communications failure,
  receiver failure, blind transmission, resolved;
- closed contact-attempt progression: designated frequency, alternate
  route-appropriate frequency, other aircraft, other station;
- blind-transmission payload with intended message, repetition count,
  optional addressees, next-transmission time, and continuation intentions;
- distinct failed-contact and receiver-failure blind-transmission modes, so one
  generic blind-message projection cannot satisfy all blind rows;
- explicit SSR code projection for communications failure 7600 and distress
  7700 when equipped;
- blind-clearance decision with prohibition and originator-request exception;
- typed recovery/reset that clears failure mode, contact attempts,
  blind-transmission schedule, and SSR emergency code;
- negatives proving ordinary no-reply, routine missed calls, and frequency
  transfer failure do not satisfy communications-failure evidence.

This can begin as local source-mapped projection evidence in `sim` tests,
because fn-71 is proving source-unit discipline, not production radio failure
detection or message scheduling.

## Review Considerations

### FP / Type Safety

Use sealed or enum classes with exhaustive `when` expressions. Do not encode
communication modes or SSR codes as free strings. Recovery/reset must be a
typed transition that clears all derived failure state.

### Test Architecture

Tests should be high-level source-backed scenarios:

- contact-failure progression from designated frequency to alternate
  frequency and alternate contact;
- failed-contact blind-transmission schedule carries twice-transmitted message
  evidence;
- receiver-failure blind-transmission schedule carries complete repetition,
  next time, addressees, receiver-failure mode, and service-gated continuation
  intentions;
- SSR 7600 and distress 7700 are separate branches;
- blind-clearance prohibition and originator-request exception are both tested;
- negative cases reject ordinary no-reply, missed call, routine frequency
  transfer failure, ordinary radio ordering, non-route-appropriate alternate
  frequencies, urgency/distress traffic without communications failure, and
  continuation intentions without ATC/advisory service.

### Impact

The preferred first implementation should not couple source-unit accounting to
production radio queue behavior, route lookup, controller relay policy, or SSR
runtime state. Existing pilot/controller behavior is not changed.

Failure modes:

- counting ordinary no-reply as communications failure;
- moving phraseology wording rows as rendered coverage;
- moving relay policy rows without relay workflow evidence;
- moving Annex 10 conformance without actual Annex 10 procedure modelling;
- failing to reset blind schedule or SSR emergency code after recovery.
- selecting SSR 7700 for urgency or communications failure, or SSR 7600 for
  distress.

### Operational Correctness

The regulatory basis is ICAO Doc 9432 Chapter 9 communications-failure text:
try the designated frequency, try other route-appropriate frequencies, try
other aircraft/stations, transmit blind messages twice with required blind
metadata, select SSR 7600 for radio failure where equipped, use SSR 7700 for
distress where appropriate, and prohibit blind clearances unless requested by
the clearance originator. Rendered phraseology and operational relay policies
remain blocked unless separately proven.
