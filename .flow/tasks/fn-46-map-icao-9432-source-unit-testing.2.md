# Classify source units by area, normativity, policy, and testability

## Description
Classify every accepted ICAO 9432 source unit into an operational area and impact category. The classification should make policy-sensitive rules explicit: if multiple controller behaviours can be correct depending on local or procedural policy, mark the source unit as requiring a typed policy concept rather than hard-coding one expected outcome.

Suggested operational areas: communications establishment, transfer/frequency, readback/hearback, taxi/ground movement, runway entry/line-up/takeoff, aerodrome information/weather, circuit/approach/landing, go-around/missed approach, traffic/separation information, critical-phase radio discipline, phraseology/examples, and out-of-current-sim-scope.

## Acceptance Criteria
- [ ] Every accepted unit has exactly one primary operational area and optional secondary tags.
- [ ] Every accepted unit has a normative kind: must, must-not, may, example, policy, phraseology, information, or model-gap.
- [ ] Every accepted unit has a current testability status: testable-now, needs-observation-fact, needs-policy-type, needs-sim-model, phraseology-later, expected-gap, or not-applicable.
- [ ] Policy-sensitive units name the likely policy concept required in code.
- [ ] The classification avoids overclaiming: no unit is marked testable-now unless the evidence needed to prove it is available or clearly authorable in the current test harness.
