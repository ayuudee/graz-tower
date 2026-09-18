# fn-64 pushback manifest and implementation plan

Epic: `fn-64-icao-9432-pushback-1-pushback-and`

Status: implementation complete pending final review.

Source of truth:

- `chunk_03_ground_movement/source_plan.md`
- `chunk_03_ground_movement/expected_gaps.md`
- ICAO Doc 9432 §4.3.1-4.3.3 (`research/txt/icao9432-extracted.txt`
  lines 4819-4915)

## Movement Manifest

Green-target candidates:

| Source unit | Claim | Planned movement |
| --- | --- | --- |
| `icao9432-extracted::pushback_powerback_4_3_en::5980a8f786170b01` | Requests for push-back or power-back are made to ATC or apron management depending on local procedures. | Green only the ATC/local-procedure branch with explicit `LocalProcedurePolicy` metadata. Apron-management remains blocked until an apron-management actor exists. |
| `icao9432-extracted::pushback_powerback_4_3_en::b3652213a568f55f` | Ground crew gives a visual signal after the manoeuvre to indicate the aircraft is free to taxi. | Add a typed pushback lifecycle fact: request, ATC approval, pushback manoeuvre, ground-crew visual free-to-taxi signal, then taxi request. |

Remain blocked:

| Source unit | Reason |
| --- | --- |
| `icao9432-extracted::pushback_powerback_4_3_en::1aae1f61b91984e8` | Power-back is a distinct reverse-by-engine manoeuvre. It should not be greened by a tug pushback model. Keep as a model gap unless fn-64 deliberately implements a typed `Powerback` manoeuvre. |
| `icao9432-extracted::pushback_powerback_4_3_en::da5fd317668b375a` | Requires rendered stop-pushback phraseology (`PHRASE-1`). |
| `icao9432-extracted::pushback_powerback_4_3_en::fc3dfdf7cc913637` | Requires rendered pilot / ground-crew coordination phraseology (`PHRASE-1`). |

Out of scope:

- Vehicle actor modelling. Tugs and apron vehicles belong to the later
  VEHICLE-1 epics unless a tiny typed ground-service signal is needed as
  evidence.
- Apron-management radio actor and frequency model. This row should remain
  only partially covered under the ATC local-procedure branch.
- Powerback physics unless deliberately selected after review.

## Proposed Design

Minimal domain shape:

- Use a scenario-authored ATC/GROUND pushback branch for the source-backed
  proof. Do not claim a generalized runtime `PushbackResponsibilityPolicy` yet;
  apron-management routing remains blocked by `LocalProcedurePolicy`.
- Add pushback-specific mission steps before taxi only for a test mission or
  airport procedure that requires pushback:
  `REQUEST_PUSHBACK`, `AWAIT_PUSHBACK_APPROVAL`, `PUSHBACK_MANOEUVRE`,
  `AWAIT_GROUND_CREW_SIGNAL`.
- Add controller observation/event support for `RequestPushback` and a GROUND
  rule that emits `PushbackApproved` only when the configured local-procedure
  policy routes pushback to ATC/GROUND.
- Add explicit pre-taxi ground-departure stages rather than folding pushback
  into `AwaitTaxiRequest`: at minimum, request-pushback, await manoeuvre
  completion / ground-crew signal, and only then await taxi request.
- Add explicit sim/evidence lifecycle facts for `PushbackRequested`,
  `PushbackApprovedByAtc`, and one typed
  `GroundCrewPushbackComplete` event representing the post-manoeuvre ground-
  crew visual free-to-taxi signal.
- Keep the tug path geometrically minimal if needed, but do not let
  `PushbackApproved` alone count as the manoeuvre. The taxi request must be
  impossible from the pushback-required mission branch until the typed
  ground-crew signal has advanced the pilot past `AWAIT_GROUND_CREW_SIGNAL`.

High-level source-mapped scenario:

1. Aircraft starts at a stand in a pushback-required local-procedure branch.
2. Pilot requests pushback from GROUND.
3. GROUND approves pushback.
4. The sim records a pushback manoeuvre completion and typed ground-crew visual
   free-to-taxi signal.
5. Only after that signal does the pilot mission advance to request taxi.
6. The existing taxi path continues to holding point.

Evidence assertions:

- `RequestPushback` occurs before `PushbackApproved`.
- `PushbackApproved` occurs before `GroundCrewVisualSignalFreeToTaxi`.
- Taxi request occurs after the ground-crew signal and cannot be emitted before
  it in the pushback-required branch.
- The source result for `5980...` is explicitly branch-scoped to ATC/GROUND
  local procedure and does not claim apron-management coverage.
- `1aae...` remains blocked unless a true powerback lifecycle is added.

## Rejected Shortcuts

- Do not green `b365...` from an observed `PushbackApproved` instruction alone.
  The source requires a ground-crew visual signal after the manoeuvre.
- Do not use `TaxiToHoldingPoint` from a stand as implicit pushback evidence.
  Taxi is already a separate §4.4 behaviour.
- Do not make all departures request pushback. ICAO Doc 9432 §4.3.1 scopes the
  need to many large-aircraft nose-in aerodromes, not every GA stand.
- Do not model apron management as `GROUND` under another name.

## Review Considerations

FP / type safety:

- If a new pushback lifecycle field is added to runtime state, audit every
  `.copy(` on the containing state type.
- Mission-step additions must update every exhaustive `MissionStep` dispatch:
  completion, transmissions, physical completion, process-instruction handling,
  and `skipCompletedSteps`.
- Avoid nullable "maybe pushback" fields where a sealed/local-procedure policy
  would make the supported branch explicit.

Test architecture:

- Prefer one high-level source-backed sim scenario over low-level helper tests.
- Add low-level tests only for pure policy classification or lifecycle
  projection helpers if they have an independent oracle.
- Keep the existing model-gap test for powerback and unsupported
  apron-management coverage.

Impact:

- Touches protocol event derivation, pilot mission authoring, controller GROUND
  procedure, sim/evidence projection, and source-mapped tests.
- Existing LOWG goldens must remain unchanged; pushback must be opt-in for the
  scenario/fixture.
- The design should leave room for VEHICLE-1 tug/apron actors without
  pretending they exist today.
- The existing GROUND taxi state machine currently seeds directly to
  `AwaitTaxiRequest`; fn-64 must not let reconciliation absorb pushback as
  ordinary taxiing before taxi has been requested.

Operational correctness:

- ICAO Doc 9432 §4.3.1 says pushback/powerback requests go to ATC or apron
  management depending on local procedures.
- ICAO Doc 9432 §4.3.3 says the ground crew gives a visual signal when the
  manoeuvre is complete and the aircraft is free to taxi.
- Phraseology examples in §4.3.1-4.3.2 remain under `PHRASE-1`.

## Impact Review Resolution

Reviewer: Popper (`01a0b457-17ad-79b0-a985-5cef9baef181`)

Findings resolved in this plan:

- Powerback remains blocked unless a typed reverse-by-engine lifecycle is
  implemented. Tug pushback does not satisfy the powerback source unit.
- Existing `RequestPushback` / `PushbackApproved` protocol leaves are inert;
  fn-64 implementation must wire a real lifecycle before moving any row green.
- `b365...` requires post-manoeuvre ground-crew visual signal. Taxi must be
  gated on that signal in the pushback-required mission branch.
- Pushback must have explicit pre-taxi ground stages / reconciliation handling
  rather than being silently absorbed by `AwaitTaxiRequest`.
- ATC/GROUND-only coverage is acceptable only as a clearly scoped
  local-procedure branch; apron-management remains unsupported.
- Mission-step additions have broad exhaustive-consumer blast radius and must
  be audited in task 2.

## Implementation Outcome

Implemented coverage:

- `5980a8f786170b01`: covered only for the scenario-authored ATC/GROUND
  branch. The test proves a pushback-required LOWG departure requests pushback
  from GROUND and receives `PushbackApproved`. The apron-management branch
  remains policy-blocked because there is no runtime local-procedure policy or
  apron-management actor yet.
- `b3652213a568f55f`: covered for tug-style pushback by proving
  `GroundCrewPushbackComplete` occurs after `PushbackApproved` and before the
  subsequent taxi clearance. The event is intentionally a combined minimal
  fact: manoeuvre complete and ground crew visual free-to-taxi signal.

Remaining blockers:

- `1aae1f61b91984e8`: remains a `PUSHBACK-1` model gap because powerback is
  reverse movement using engine power, not a tug pushback lifecycle.
- `da5fd317668b375a` and `fc3dfdf7cc913637`: remain `PHRASE-1` because
  rendered stop-pushback and pilot/ground-crew phraseology are not yet in
  scope.

Implementation details:

- Pilot mission authoring now supports an opt-in pushback-required ground
  departure branch: `REQUEST_PUSHBACK`, `AWAIT_PUSHBACK_APPROVAL`,
  `AWAIT_GROUND_CREW_SIGNAL`, then the existing taxi/departure sequence.
- Controller observation folds `RequestPushback` into `PushbackRequested`; the
  GROUND taxi procedure has an explicit `AwaitPushbackCompletion` stage, a
  `GND-PUSHBACK-APPROVE` rule, and a post-pushback taxi rule gated by typed
  `GroundCrewPushbackComplete` belief.
- `PushbackApproved` is certified as a surface instruction. `StartupApproved`
  remains unsupported by certification, preserving the fn-63/D-PF.1 block.
- The sim schedules a typed `GroundCrewPushbackComplete` event after
  `PushbackApproved`; that event advances the pilot mission and is projected to
  controller beliefs before the later taxi clearance can issue.
- `ICAO9432_PUSHBACK_POWERBACK` now cites ICAO Doc 9432 §4.3 for pushback and
  powerback traces.

Validation performed:

- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432Chunk03GroundMovementEvidenceTest' --tests '*.Icao9432TaxiSourceBackedScenarioTest' --tests '*.Icao9432PushbackSourceBackedScenarioTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
- `./gradlew-nix :pilot:jvmTest --tests '*.GroundDepartureTaskShapeSpec' --tests '*.ProcessInstructionMissionStateSpec'`
- `./gradlew-nix :controller:jvmTest --tests '*.CertificationBoundarySpec'`
- `./gradlew-nix :controller:compileKotlinJvm :sim:compileKotlinJvm`
- `./gradlew-nix detekt`
- `git diff --check`

## Review Resolution

Reviewer: Pasteur (`01a0b468-48fb-7fc2-abae-910dff918d6e`)

- Taxi-before-signal hole: fixed by projecting `GroundCrewPushbackComplete` to
  `ControllerView.worldEvents`, folding it into `BeliefState.pushbackCompleted`,
  and gating `GND-TAXI-AFTER-PUSHBACK` on both `TaxiRequested` and
  `PushbackCompleted`. Added a regression test that injects a premature
  `RequestTaxi` after `PushbackApproved` and proves no taxi clearance issues
  before the ground-crew signal.
- Policy overclaim: docs now describe the greened branch as scenario-authored
  ATC/GROUND coverage. No generalized local-procedure policy is claimed.
- Manoeuvre/signal split: docs now call `GroundCrewPushbackComplete` a minimal
  combined fact for "manoeuvre complete and visual free-to-taxi signal".
- Certification scope: certification is documented as surface-safety emission
  support, not proof of the whole ICAO 9432 §4.3 local-procedure claim.
