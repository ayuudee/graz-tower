# fn-70 Emergency Descent Movement Manifest

Epic: `fn-70-icao-9432-emergency-1c-emergency`

Scope: ICAO Doc 9432 Chapter 9 emergency descent safeguarding only. This slice
does not implement rendered phraseology, urgency payload/addressing policy,
urgency interference policy, communications failure, blind transmission, SSR,
or production aircraft kinematics.

## Rows To Move

### Emergency Descent Safeguarding

| Source unit | Current state | Target state | Evidence required |
|---|---|---|---|
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::082f9668292ed82c` | `model-gap` | `covered-green structured emergency-descent safeguarding projection branch` | A typed emergency descent announcement creates an active safeguarding state, identifies other affected traffic, assigns a concrete safeguard action, and can be reset when the emergency descent is no longer active. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::c71568b00fb1535e` | `model-gap + policy-blocked` | `split: structured emergency-descent warning projection branch covered-green; specific-instruction necessity policy remains model-gap + policy-blocked` | A general warning action is emitted for affected aircraft after an emergency descent announcement. The separate question of when specific instructions are necessary remains blocked by policy. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::ca0c243491ff5d13` | `model-gap` | `covered-green structured emergency-descent position-question branch` | A typed emergency descent announcement with uncertain position supports a controller position-question branch; known-position emergency descent does not. |

Ordinary descent, arrival, or go-around traces are not emergency-descent
evidence. fn-70 evidence must start from an emergency descent announcement and
show safeguarding action for other aircraft.

## Rows Left Blocked Or Split

| Source unit | State | Reason |
|---|---|---|
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::1de475a788206cc8` | `model-gap + policy-blocked` | Urgency message element selection needs an operational payload policy beyond emergency-descent safeguarding. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::b95d7bb1cb409bca` | `model-gap + policy-blocked` | Urgency addressee/frequency selection needs policy state for current station and area responsibility. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::5df94af7a64c3f5d` | `model-gap + policy-blocked` | Urgency interference suppression remains policy work; fn-69 already covered priority/frequency discipline but not suppression policy. |

## Proposed Evidence Surface

Use a narrow source-backed emergency descent safeguarding scenario with:

- a closed event type distinguishing emergency descent announcement from
  ordinary descent and go-around;
- an active safeguarding state carrying the emergency descent aircraft and
  affected traffic;
- a closed safeguard action for affected aircraft, such as general warning,
  avoid-level-band instruction, or heading/vector avoidance;
- a typed reset transition that clears affected traffic and actions when the
  emergency descent is resolved;
- a typed position-uncertainty branch that can produce a controller position
  question without making that question mandatory when position is known;
- negative evidence proving ordinary descent/go-around events do not activate
  safeguarding;
- negative evidence proving urgency/PAN PAN without an emergency-descent
  announcement and ordinary radio/FIFO ordering do not activate safeguarding.

This can begin as local source-mapped projection evidence in `sim` tests,
because fn-70 is proving the regulatory source-unit surface, not production
controller conflict-resolution behavior. If impact review finds this too
synthetic, promote the closed types to a production adapter before moving rows.

## Review Considerations

### FP / Type Safety

Use sealed or enum classes with exhaustive `when` expressions. Avoid nullable
state flags for active/resolved emergency descent. Reversal/reset must be a
typed transition, and no catch-all branch may silently accept ordinary descent
as emergency descent.

### Test Architecture

Tests should be high-level source-backed scenarios:

- emergency descent announcement activates safeguarding for other aircraft;
- affected traffic receives a concrete safeguard action;
- resolution clears active emergency descent, affected traffic, and actions;
- ordinary descent/go-around cannot satisfy the source unit;
- split movement for `c71568b00fb1535e` proves only the general-warning branch,
  not the policy-dependent necessity of specific instructions;
- position-question movement for `ca0c243491ff5d13` proves only the typed
  position-uncertainty branch, not a universal requirement to ask questions;
- reset checks must prove emergency aircraft, affected traffic, and safeguard
  actions are all cleared.

### Impact

The preferred first implementation should not couple chunk-08 accounting to
the existing aircraft kinematics or controller sequencing engine. Existing
descent, go-around, and radio behavior is not changed by this slice.

Failure modes:

- counting ordinary descent or go-around as emergency descent;
- moving the specific-instruction policy row without a necessity policy;
- treating position questions as mandatory when the emergency aircraft position
  is already known;
- failing to reset affected traffic and safeguard actions after resolution;
- treating PAN PAN/urgency classification alone as an emergency descent
  announcement;
- inventing phraseology wording not covered by this slice.

### Operational Correctness

The regulatory basis is ICAO Doc 9432 Chapter 9 emergency descent text: an
aircraft announcement of emergency descent triggers controller action to
safeguard other aircraft, including a warning to affected aircraft. Follow-up
specific instructions are conditional on necessity, and further pilot questions
are optional position-ascertainment support; those policy-dependent branches
remain blocked unless separately proven.
