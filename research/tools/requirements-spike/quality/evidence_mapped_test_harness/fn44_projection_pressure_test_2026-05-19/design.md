# FN44 projection pressure-test design

## Objective

FN44 should harden the FN43 evidence DSL by forcing it through a small set of
source units that require projected evidence, not just direct observation of
instructions and reports. The target is a better permanent design, not a larger
case count.

The public tests must stay terse and source-shaped. Internal harness complexity
is acceptable only when it keeps the authored tests honest and legible.

## Selected source units

1. `icao9432-extracted::essential_aerodrome_information_4_10_en::1aa5cb7e758055bc`
   - Source: ICAO Doc 9432 §4.10.
   - Claim: essential aerodrome information should be passed before start-up or
     taxi and before final approach when possible, except when the aircraft is
     known to have received the information elsewhere.
   - Design pressure: positive evidence must distinguish passed information,
     known receipt from another source, and missing information.

2. `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4`
   - Source: ICAO Doc 9432 §4.1.2.
   - Claim: controllers should not transmit to an aircraft during take-off,
     initial climb, the last part of final approach, or landing roll unless
     necessary for safety reasons.
   - Design pressure: negative compliance requires explicit phase windows and
     transmission necessity classification. An empty transmission list is not
     enough.

3. `icao9432-extracted::transfer_communications_2_8_2_en::40382df156ad071e`
   - Source: ICAO Doc 9432 §2.8.2.1.
   - Claim: an aircraft shall be advised by the appropriate station to change
     frequency in accordance with agreed procedures.
   - Design pressure: cross-unit/frequency-change evidence without turning the
     source-mapped test into phraseology rendering.

4. `icao9432-extracted::transfer_communications_2_8_2_en::b49ae03cbbb2d538`
   - Source: ICAO Doc 9432 §2.8.2.1.
   - Claim: absent such advice, the aircraft shall notify the station before a
     frequency change.
   - Design pressure: fallback procedural evidence and honest gaps when current
     sim doctrine models release/autonomous contact rather than frequency
     change.

These four are sufficient. Adding more source units in this epic would hide
design weakness behind volume.

## Expected case outcomes

**Essential aerodrome information:** likely starts as a narrower expected gap.
The current LOWG trace emits ATIS-issued events in the setup, but the FN43
evidence facts only project transmissions and final aircraft summary. There is
no fact saying the aircraft received ATIS or that essential aerodrome
information was passed before taxi/final. A positive implementation is only
valid if FN44 adds explicit receipt/passed-information facts from real trace
data.

**Critical-phase transmission restraint:** likely can become a positive
negative/window case if the adapter can derive phase windows from trace or
state snapshots. It must remain a gap if the only available evidence is
transmission absence without observed windows.

**Controller-advised frequency change:** likely depends on whether G2/cross-
aerodrome traces expose a controller transfer instruction or a release-only
event. If only release/autonomous first contact exists, the source can be a
typed gap or a project-doctrine mismatch note, not a fake positive.

**Pilot-notified frequency change absent advice:** probably a typed expected
gap unless a current scenario represents pilot-initiated frequency change. It
is valuable because it forces the report to show a missing scenario/projection
without expanding into phraseology.

The minimum success bar for FN44 is two positive source-backed cases and typed,
narrow expected gaps for the rest. If only one honest positive case is possible
after inspection, the completion review should say the design is not yet strong
enough to scale.

## Projection contract

Add facts only when there is a clear source-unit demand.

Candidate payloads:

- `AerodromeInformation`: aircraft, timing context, information status, and
  evidence source.
- `CriticalPhaseWindow`: aircraft, phase kind, start/end sequence or time, and
  runway/context.
- `TransmissionNecessity`: transmission id, aircraft, phase relation, and
  classification.
- `FrequencyTransfer`: aircraft, advised/notify mode, previous/next unit or
  frequency when known, and acknowledgement evidence when present.

All four should be test/evidence facts first. Do not promote them into
production trace types during FN44 unless existing production code already owns
the data and the test adapter merely exposes it.

## Public DSL shape

The desired call sites are intent-first:

```kotlin
source("essential information before taxi") {
    cites(ICAO9432.AerodromeInformation.EssentialBeforeTaxi)
    expect { aerodromeInformation(aircraft).beforeTaxi().wasPassedOrKnownReceived() }
}

source("no routine critical-phase transmissions") {
    cites(ICAO9432.AerodromeCriticalPhase.NoRoutineTransmission)
    expect { criticalPhase(aircraft).routineControllerTransmissions().none() }
}
```

If a helper would only be used once, prefer a local assertion over a new DSL
primitive. If the same concept appears twice, extract a small helper.

## Impact assessment

**Coupling:** FN44 will couple the evidence harness more deeply to sim trace
shape. Keep that coupling in `EvidenceFactAdapters` and narrow DSL selectors,
not in public tests. The production sim should not be reshaped merely to satisfy
evidence tests.

**Report adequacy:** existing report fields are enough structurally, but the
adequacy strings must become more precise for negative/window evidence. Reports
should show whether a case was supported by positive fact activation, by an
observed empty window, or by a typed expected gap.

**Negative evidence:** this is the highest-risk area. A critical-phase radio
case passes only if the window exists and contains no routine transmissions.
Missing windows are failure/gap, not pass.

**DSL ceremony:** source-mapped tests are useful only if they read like source
intent. Internal complexity is acceptable; call-site ceremony is not. Review
should reject helpers that expose `FactId`, provenance, extraction paths, or
monitor vocabulary.

**Reversal/state impact:** no runtime reversible state transitions are planned.
If implementation discovers it must add runtime state, stop and redesign before
code: reversal completeness then becomes mandatory.

## Review considerations

**FP / type safety:** New payloads and classifications should be sealed or
enum-backed. Exhaustive `when` only. No catch-all `else`, no `error()` for
type-valid but unsupported facts. Unsupported cases become typed gaps or
explicit failure outcomes.

**Test architecture:** High-level source evidence tests should exercise the
public DSL. Unit tests are appropriate only for pure projection helpers,
selector semantics, and report serialization. Negative/window tests must include
one missing-window regression so absence does not pass.

**Impact:** The work should make later source units easier by adding a few
source-shaped primitives. It must not create a parallel simulation model inside
tests. Any fact that cannot be populated from trace data should stay out of the
model or be represented as a typed gap.

**Operational correctness:** Regulatory statements in tests/docs cite ICAO Doc
9432 §4.10, §4.1.2, and §2.8.2. Phraseology is out of scope unless FN44
explicitly adds the §2.8.2 phraseology source unit.

## Implementation order

1. Catalog refs and validation for the four selected source units.
2. Minimal fact payloads and projection helpers.
3. Critical-phase window case first if trace data supports it; otherwise record
   the exact missing projection.
4. Essential aerodrome-information receipt/passed-information case.
5. Transfer-of-communications cases against the smallest honest scenario.
6. Completion review with the explicit question: did the DSL remain simple
   enough to scale?
