# fn-63 start-up lifecycle manifest

Epic: `fn-63-icao-9432-fn43-gap-2-start-up-lifecycle`

Status: reviewed closeout artifact. No source unit moves green in fn-63.

Source of truth:

- `chunk_02_radio_procedures_policy/source_plan.md`
- `chunk_02_radio_procedures_policy/fn43_gap2_scout.md`
- `docs/deferments.md` D-PF.1

## Movement Manifest

Green-target candidate:

| Source unit | Claim | Required evidence | Current state |
| --- | --- | --- | --- |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::95034efc191fa9cd` | After ATC approval, the pilot starts engines assisted as necessary by ground crew. | A real lifecycle trace showing `StartupApproved` received before an explicit engine-start observation. Mission-step completion and default `AircraftState.engineRunning == true` are not valid substitutes. | Blocked by D-PF.1 until the sim has airport-conditional start-up clearance and an orderable engine-start lifecycle event/fact. |

Closeout decision:

- `95034efc191fa9cd` remains blocked. ICAO Doc 9432 §4.2.3 requires the
  sequence "having received ATC approval" then engine start; the current sim
  has no orderable engine-start event after approval.
- No structural or synthetic evidence fact was added. That would repeat the
  exact false-green path rejected in `fn43_gap2_scout.md`.
- The correct implementation is a D-PF.1-sized feature: airport-conditional
  startup requirement, real request/approval workflow, and a typed engine-start
  lifecycle observation.

Remain blocked in fn-63 unless a larger D-PF.1 implementation lands:

| Source unit | Reason |
| --- | --- |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::35ad4a7f3d8d2ead` | Start-up request examples require rendered phraseology (`PHRASE-1`). |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::39a4619cec62a93f` | Start-up approval with QNH requires rendered phraseology (`PHRASE-1`). |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::86ba1c63169eceff` | Start-up-at-time approval requires rendered phraseology (`PHRASE-1`). |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::b6a69b358e0a53f2` | Expected-departure / start-up-at-own-discretion requires rendered phraseology (`PHRASE-1`). |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::bba49998378e3b31` | No-ATIS current-aerodrome-information request is phraseology/policy work, not lifecycle proof. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::c43cb2d0a82356bd` | Expected start-up time requires rendered phraseology (`PHRASE-1`). |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::d822c98e298fcbfd` | Location and ATIS acknowledgement in request require rendered phraseology (`PHRASE-1`). |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::f089b47d46d9653d` | Delayed-departure "normally indicates" behavior requires phraseology plus operational policy. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4` | Critical-phase radio discipline already has covered-red observation; safety exception remains policy-blocked. Not start-up lifecycle work. |

Out of scope:

- Ground-station radio-test signal duration/content rows: already split by
  fn-52.3 and `PHRASE-1`.
- Readback route-clearance-before-start-up row in chunk 01: timing policy
  (`ClearanceTimingPolicy`), not engine-start lifecycle.
- Essential-aerodrome-information-before-start-up row in chunk 06:
  information-receipt/timing policy, not engine-start lifecycle.

## Impact Assessment

Decision point:

- A narrow evidence-only patch would be a false green. The current model has
  `StartupApproved`, `RequestStartup`, `MissionStep.REQUEST_STARTUP`, and
  `MissionStep.AWAIT_STARTUP_APPROVAL`, but the live `groundDepartureTask()`
  deliberately omits start-up under D-PF.1.
- `AircraftState.engineRunning` starts true and is already coupled to
  engine-failure / abort-takeoff physics. Treating it as "pilot started
  engines" would corrupt the evidence model.

Viable implementation shape:

- Add a real start-up lifecycle only behind an airport/aerodrome procedure
  flag. LOWG should remain on the existing taxi-first path unless a fixture
  explicitly marks start-up clearance required.
- The pilot mission tree for a start-up-required aerodrome must include
  `REQUEST_STARTUP` and `AWAIT_STARTUP_APPROVAL` before `REQUEST_TAXI`.
- A controller procedure must issue `StartupApproved` in response to
  `Request(RequestStartup)` for that scenario. If full `CLEARANCE_DELIVERY`
  is too broad for fn-63, the implementation must not fake it with an
  unrelated GROUND/TOWER side effect unless that is explicitly modelled as the
  aerodrome's published service shape.
- Evidence must include a typed engine-start lifecycle fact emitted after
  approval, not inferred from default engine state.

Failure modes:

- False green by mission-step completion: `AWAIT_STARTUP_APPROVAL` completion
  proves approval was processed, not that engines started.
- False green by default `engineRunning == true`: this field is not an
  orderable start event.
- Scope creep into phraseology: request/approval wording examples remain
  `PHRASE-1`.
- Scope creep into policy: delayed start-up, "normally indicates", and
  route-clearance timing remain policy work.

## Review Considerations

FP / type safety:

- Do not add nullable lifecycle fields to broad state without auditing every
  `.copy(` mutation site.
- If an explicit engine lifecycle state is added, all transitions must be
  total and reversible where the sim supports reversal.
- If the type system allows a "start required but no startup actor" fixture,
  that must return a typed setup error or fail loudly in the test fixture.

Test architecture:

- Use a high-level source-backed scenario for the green candidate only if the
  scenario has real request, approval, and engine-start observations.
- Keep existing expected-gap tests for phraseology/policy rows.
- Add a negative test that default engine-running state alone cannot satisfy
  the source unit.

Impact:

- D-PF.1 touches pilot mission authoring, controller service shape, fixture
  staffing, and evidence projection.
- The least risky first implementation is a dedicated fixture/scenario for an
  aerodrome requiring start-up clearance, not a global change to LOWG goldens.

Operational correctness:

- ICAO 9432 §4.2 describes start-up approval workflows at aerodromes whose
  procedures require approval. The implementation must be aerodrome/procedure
  conditional, not a universal rule for every departure.
- Rendered start-up wording is not asserted until phraseology evidence exists.

## Reviewed Outcome

Independent review agreed that fn-63 cannot honestly green the lifecycle row
without D-PF.1:

- `CLEARANCE_DELIVERY` is present as a protocol role but currently unmodelled
  by controller commitment reconciliation.
- `StartupApproved` currently leaves sim state unchanged.
- `AircraftState.engineRunning` defaults true and is already used by
  engine-failure / abort-takeoff logic, so it is not an engine-start event.
- The least risky future implementation is a dedicated startup-required
  fixture/scenario with existing LOWG/LJMB goldens left on the taxi-first path.

Therefore fn-63 closes as a reviewed explicit block, not implementation
coverage.
