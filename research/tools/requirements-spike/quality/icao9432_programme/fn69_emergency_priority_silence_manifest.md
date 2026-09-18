# fn-69 Emergency Priority And Silence Movement Manifest

Epic: `fn-69-icao-9432-emergency-1b-emergency`

Scope: ICAO Doc 9432 Chapter 9 emergency priority and radio-silence discipline
only. This slice does not implement rendered phraseology, assistance/relay
actors, emergency descent, communications failure, blind transmission, SSR, or
general superfluous-transmission suppression policy.

## Rows To Move

### Priority Ordering

| Source unit | Current state | Target state | Evidence required |
|---|---|---|---|
| `icao9432-extracted::distress_urgency_intro_9_1_en::3b1079aa56df2ce6` | `model-gap` | `covered-green structured emergency-priority projection branch` | Closed priority ordering derived from production `EmergencyType.MAYDAY` proves distress traffic outranks urgency and routine traffic. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::1a20cd48e58a5693` | `model-gap` | `covered-green structured emergency-priority projection branch` | Closed priority ordering derived from production `EmergencyType.PAN_PAN` proves urgency traffic outranks routine traffic but remains below distress traffic. |

Ordinary radio serialization is not priority evidence. A routine call being
queued or transmitted before another routine call says nothing about ICAO 9432
Chapter 9 emergency priority. fn-69 evidence must classify messages first and
then compare by the emergency priority class.

### Radio-Silence Lifecycle

| Source unit | Current state | Target state | Evidence required |
|---|---|---|---|
| `icao9432-extracted::distress_urgency_intro_9_1_en::7d35c042421b5b03` | `model-gap` | `covered-green structured emergency-frequency discipline projection branch` | Active distress or urgency traffic on a frequency blocks uninvolved stations while allowing stations directly involved in rendering assistance, and releases the frequency after advised termination. |
| `icao9432-extracted::distress_messages_9_2_en::e2902de496f43a95` | `model-gap` | `covered-green structured silence-imposition branch` | A distress aircraft or a station controlling distress traffic can impose silence on all aircraft or on a named interfering aircraft. |
| `icao9432-extracted::distress_messages_9_2_en::ed898005cd1a4da5` | `model-gap` | `covered-green structured silence-obligation branch` | A silenced aircraft remains unable to transmit while distress traffic is active and is released only when the controlling station advises that distress traffic has ended. |
| `icao9432-extracted::distress_messages_9_2_en::c20024dad1b7144e` | `model-gap` | `covered-green structured silence-termination branch` | The ground station controlling distress traffic terminates the distress communication and clears silence state when it is aware the aircraft is no longer in distress. |

Reversal is load-bearing. The termination branch must be represented before the
forward silence obligations are counted covered: active emergency state,
silenced-aircraft obligations, and uninvolved-station frequency restrictions
must all reset when the controlling station advises that the distress traffic
has ended. A plain world-state flag is insufficient.

## Rows Left Blocked

| Source unit | State | Reason |
|---|---|---|
| `icao9432-extracted::distress_urgency_intro_9_1_en::06f7a72397c325ac` | `model-gap + policy-blocked` | This is not satisfied by priority ordering. It requires a policy for identifying and suppressing superfluous transmissions that may distract a busy emergency pilot. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::5df94af7a64c3f5d` | `model-gap + policy-blocked` | Urgency-interference suppression needs a relevance/interference policy beyond the fn-69 closed priority and silence lifecycle model. |
| assistance/relay, alternate frequency, emergency descent, communications failure, blind transmission, SSR rows | unchanged | Out of fn-69 scope; tracked by later emergency epics. |

## Proposed Evidence Surface

Use a narrow source-backed emergency radio discipline scenario with:

- A closed message-priority type: routine, urgency, distress, derived from
  production `EmergencyType` for emergency transmissions.
- A total comparator: distress > urgency > routine.
- A closed emergency-traffic state: active versus terminated.
- A closed silence scope: all aircraft or a named aircraft.
- A closed silence authority: distress aircraft or controlling station.
- A total `mayTransmit` decision for involved, uninvolved, and silenced
  participants.
- A termination/advisory operation by the controlling station that clears active
  emergency traffic and all silence obligations.

This can begin as local source-mapped projection evidence in `sim` tests,
because fn-69 is currently proving the regulatory surface, not changing
production controller or pilot behavior. The tests must still wire emergency
priority to production `EmergencyType.MAYDAY` / `PAN_PAN`; otherwise they prove
only a synthetic test model.

## Review Considerations

### FP / Type Safety

The evidence model should use sealed or enum classes with exhaustive `when`
expressions. No `else` branch may swallow future emergency classes, authorities,
or silence scopes. Termination must be a total state transition, not a nullable
flag.

### Test Architecture

Tests should be high-level source-backed scenarios:

- priority scenario covers distress over urgency and routine, plus urgency over
  routine and under distress;
- frequency-discipline scenario covers active distress and active urgency
  traffic, involved versus uninvolved stations, and advised termination reset;
- silence lifecycle scenario covers imposition by both permitted authorities,
  per-aircraft obligation, and full controlling-station advisory reset;
- negative scenario proves ordinary radio ordering/collision is not accepted as
  priority evidence, urgency never outranks distress, non-authorities cannot
  impose distress silence, named-aircraft silence does not suppress unrelated
  aircraft, and termination without controlling-station advice does not release
  silence.

### Impact

The preferred first implementation should not couple emergency source-unit
accounting to the existing radio queue. Existing radio queue behavior is not
changed by this slice, and the moved rows are structured projection evidence
rather than end-to-end radio scheduler enforcement. If later epics need
production scheduling, the closed discipline model can become a port/adapter
boundary.

Failure modes:

- counting ordinary serialization as priority arbitration;
- failing to reset silenced participants after distress termination;
- releasing a silenced aircraft on a raw distress-ended state without the
  controlling station's advisory;
- using an open stringly typed participant role that can silently accept
  unsupported actors;
- accidentally moving superfluous-transmission policy rows without a policy.

### Operational Correctness

The regulatory basis is ICAO Doc 9432 Chapter 9: distress priority, urgency
priority except over distress, emergency-frequency restraint unless involved or
after emergency traffic termination, permitted silence-imposition authorities,
silence maintenance until advised that distress traffic has ended, and
ground-station termination of the distress/silence condition. This slice
intentionally does not claim phraseology wording or relay assistance behavior.
