# ICAO 9432 implementation roadmap

## Mission

The current ICAO Doc 9432 programme has done the first job: every accepted
`icao9432-extracted` source unit is represented in the source-mapped coverage
wall, either as covered evidence or as a loud blocker. The next job is not to
implement ICAO 9432 verbatim. The next job is to turn the loud blockers into a
sequence of source-mapped tests and simulator capabilities that create a durable
regulatory foundation for the project.

The operating model stays unchanged:

- Test authoring and simulator behavior repairs are separate epics.
- A source unit only turns green when the trace evidence proves the source
  claim at the right level.
- Policy-dependent source units require explicit policy concepts, not hidden
  defaults.
- Phraseology source units require rendered transmission evidence, not only
  typed instruction evidence.
- Model gaps remain expected gaps until the simulator has the domain object
  needed to express the scenario honestly.

## Current programme state

- Accepted ICAO 9432 source units: 166.
- Chunk source-mapped coverage epics complete: fn-47 through fn-58.
- Remaining blocker families by first-pass count:
  - `PHRASE-1`: 56 units.
  - `EMERGENCY-1`: 38 units.
  - `POLICY-1`: 17 units.
  - `FN43-GAP-1`: 11 units.
  - `VEHICLE-1`: 11 units.
  - `PUSHBACK-1`: 3 units.
  - `FN43-GAP-2`: 2 units, with part of the radio-test-signal duration work
    repaired by fn-52 and start-up lifecycle evidence still open.

The counts are useful for scale, but they are not the ordering criterion. The
ordering criterion is dependency shape and false-green risk. Blockers can
overlap, so child epics must use
`implementation_blocker_manifest.csv` as their starting guardrail rather than
treating the family counts above as disjoint work queues.

## Execution guardrails

Every follow-on epic must declare an exact source-unit movement manifest before
implementation starts:

- source units targeted to move green in that epic;
- source units expected to remain blocked, with blocker reason unchanged;
- source units intentionally untouched;
- wrong-path evidence that must not satisfy the claim;
- the command or generated report that will fail if any source unit changes
  state outside the declared manifest.

No child epic may turn a source unit green merely because a generic substrate
exists. A source unit turns green only when that unit's obligation has a direct
assertion over the right evidence surface.

The follow-on epics are executable only after material review and red-team
findings are either incorporated into this roadmap or carried into the child
epic as an explicit acceptance criterion. "Carried" means an executable
constraint, not a prose note.

## Recommended sequence

### Phase 1: evidence substrate before broad greens

#### 1. Rendered phraseology evidence architecture (`PHRASE-1A`)

Goal: make rendered transmission evidence first-class without rewriting every
phrase in the sim at once.

Scope:

- Define where rendered utterance text lives in the radio trace.
- Preserve typed instruction/report atoms alongside the rendered text.
- Add source-mapped assertion helpers that can check mandatory words, call
  signs, runway designators, frequencies, readback content, and absence of
  wrong-path phraseology.
- Define an obligation taxonomy for phraseology source units: mandatory words,
  ordered phrase, semantic slot, example dialogue, readback, ambiguity/absence
  constraint, and forbidden-meaning constraint.
- Represent rendered phraseology as structured tokens with source/protocol
  provenance where a source unit depends on slots or semantic meaning.
- Prove the surface with a small cross-section of phraseology units from
  communications, start-up, takeoff, and landing. Emergency wording is
  deliberately excluded from the initial proof set.

Why first:

- `PHRASE-1` is the biggest blocker family.
- Many later model epics also need phraseology evidence.
- Without this substrate, typed events can falsely appear to prove wording
  obligations they do not actually prove.

Non-goal:

- Do not green all 56 phraseology units in this architecture epic. It should
  create the evidence port and a minimal proof set.
- Do not use substring checks or full-string snapshots to green
  ambiguity-sensitive units where the obligation is that a phrase must not imply
  runway entry, take-off clearance, emergency state, or another forbidden
  operational meaning.
- Do not green emergency phraseology until emergency state and message payload
  evidence exists.

#### 2. Typed policy substrate (`POLICY-1`)

Goal: represent operational discretion explicitly so source-mapped tests can
distinguish universal rules from valid local/controller policy choices.

Scope:

- Build the policy matrix before any policy-green-out work. The matrix must map
  every policy-blocked source unit to source id, modality, policy owner, allowed
  configured alternatives, evidence needed for the configured branch, and what
  is not being proven universally.
- `CriticalPhaseTransmissionPolicy`: routine versus safety-essential
  transmissions during critical flight phases.
- `ClearanceTimingPolicy`: "normally" and "when practicable" clearance timing
  decisions.
- `ControllerInterventionPolicy`: intervene, withhold, or defer advisories
  when pilot/controller responsibility is shared.
- `LocalProcedurePolicy`: aerodrome-local rules, including start-up,
  pushback, and circuit/local sequencing choices.
- `OperationalGuidancePolicy`: non-mandatory guidance such as readability
  classification and controller advice.

Why second:

- It prevents hard-coding one valid controller choice as universal law.
- It gives later test epics a place to bind policy variants deliberately.

Non-goal:

- Do not invent operational doctrine beyond the cited source unit. If a source
  says "normally", the test should bind a policy and prove behavior under that
  policy.
- Do not turn source units green merely because a policy enum exists or because
  a default policy chooses one allowed behavior.

### Phase 2: observation gaps in the existing aircraft/controller world

#### 3. Essential aerodrome information evidence (`FN43-GAP-1`)

Goal: expose trace facts for information given, information known, and timing of
receipt before clearances.

Scope:

- Source units in ICAO 9432 §4.10.
- Current surface: after-landing/go-around/aerodrome-information chunk.
- Evidence should show whether the pilot had the relevant aerodrome
  information before the operational decision, not merely whether a controller
  event existed somewhere in the run.

Why here:

- It is an observation problem in existing domains, so it should be cheaper and
  lower risk than new vehicle/emergency domains.
- It sharpens the evidence DSL before larger model additions.

#### 4. Start-up lifecycle evidence (`FN43-GAP-2`)

Goal: close the remaining start-up observation gap without pretending start-up
phraseology is solved.

Scope:

- Engine start request, start approval, delayed start, and start-at-time
  lifecycle facts.
- Keep rendered start-up wording under `PHRASE-1`.
- Keep "normally indicates" and delayed-departure discretion under
  `POLICY-1`.

Why here:

- It is small and provides a sanity check that the phraseology/policy boundary
  is still being respected.

### Phase 3: ground-domain model gaps

#### 5. Pushback and powerback model (`PUSHBACK-1`)

Goal: add enough ground movement model to express pushback/powerback source
units honestly.

Scope:

- Pushback request and approval lifecycle.
- Ground crew signal or equivalent explicitly modeled event.
- Powerback prohibition/approval policy as local procedure, not universal
  behavior.
- Source-mapped tests for the three blocked pushback units.
- Either introduce a minimal shared ground-service event concept that will
  survive later vehicle work, or explicitly scope pushback to aircraft movement
  plus a typed `GroundCrewSignalReceived` fact. Do not pre-design the full
  vehicle actor model in the pushback epic.

Why before vehicles:

- Smaller than a full vehicle actor model.
- Exercises aircraft ground movement reversal and local procedure policy.

#### 6. Vehicle movement and permission lifecycle (`VEHICLE-1A`)

Goal: represent non-aircraft movement actors well enough to prove first-call
and permission-lifecycle source units.

Scope:

- Vehicle call signs, position, destination, and route.
- Vehicle/tow transmissions in the same radio evidence stream as aircraft
  transmissions.
- Movement permission lifecycle for vehicles on the manoeuvring area.

#### 7. Vehicle runway crossing and occupancy (`VEHICLE-1B`)

Goal: prove vehicle runway crossing and occupancy claims without pretending
vehicle existence alone proves vacated-state obligations.

Scope:

- Runway crossing clearance, hold-short, and vacated reports.
- Runway occupancy/conflict evidence for vehicles.
- Explicit holding-point/extent evidence where the source unit turns on whether
  the vehicle is clear of the runway.

#### 8. Towing metadata and vacated geometry (`VEHICLE-1C`)

Goal: cover towing-specific source units.

Scope:

- Tow metadata where source units require aircraft type/operator.
- Towed-aircraft geometry/extent and vacated-state evidence.
- Phraseology remains blocked unless `PHRASE-1A` or a later phraseology suite
  provides the rendered wording proof.

Why after pushback:

- Vehicle work is a larger actor-domain expansion and should reuse phraseology
  evidence and local procedure concepts. Splitting it prevents "vehicle actor
  exists" from greening runway-vacated or towing claims without the required
  geometry and occupancy evidence.

### Phase 4: emergency and communication failure domain

#### 9. Emergency classification and message payload (`EMERGENCY-1A`)

Goal: represent distress/urgency state and emergency message payload without
trying to model every emergency procedure in one epic.

Scope:

- Distress versus urgency classification.
- Pilot emergency declaration state.
- Emergency message payload fields.
- Rendered emergency wording remains blocked until both this payload exists and
  the phraseology evidence contract can prove emergency-specific wording.

#### 10. Emergency priority and radio silence (`EMERGENCY-1B`)

Goal: prove priority handling and silence obligations separately from message
payload construction.

Scope:

- Emergency priority arbitration.
- Radio silence instructions and release from silence.
- Assistance/relay actors only if the source-unit subset requires them.

#### 11. Emergency descent safeguarding (`EMERGENCY-1C`)

Goal: prove emergency descent warnings and safeguarding.

Scope:

- Emergency descent warning state.
- Controller warning/coordination behavior.
- Conflict/safeguarding evidence distinct from ordinary descent or go-around
  traces.

#### 12. Communications failure, blind transmissions, and SSR (`EMERGENCY-1D`)

Goal: cover communications failure branches that need specific failure-state
and surveillance evidence.

Scope:

- Communications failure state.
- Blind transmissions.
- SSR 7600/7700 where applicable.
- Recovery and no-contact traces that demonstrate the procedure at high level.

Why split:

- Emergency units are numerous and cross-cut pilot autonomy, controller
  priority, radio discipline, SSR, and phraseology.
- Ordinary radio, VFR, or go-around traces must not be treated as emergency
  evidence.
- The exact source-unit subset for each emergency epic must be taken from the
  chunk 08 expected-gap decomposition before implementation starts.

## Execution model for each follow-on epic

Each implementation family should use the same loop:

1. Plan source-unit subset and quote-check the relevant units.
2. Write the exact movement manifest: green-targeted, remain-blocked, and
   untouched source units.
3. Author or update high-level source-mapped tests first.
4. Leave tests failing loudly when the simulator lacks the concept.
5. Repair implementation in a separate task or child epic.
6. Re-run the source-mapped coverage report and record exactly which source
   units moved from blocked/red to green.

The tests should stay high-level: believable scenarios made minimal, with
source-unit assertions over the trace. Unit tests are only appropriate where
there is an independent formal oracle or a local pure helper has meaningful
business value under `docs/test-standards.md`.

## Proposed follow-on Flow epics

1. `ICAO 9432 PHRASE-1 rendered phraseology evidence architecture`
2. `ICAO 9432 POLICY-1 typed policy substrate`
3. `ICAO 9432 FN43-GAP-1 essential aerodrome information evidence`
4. `ICAO 9432 FN43-GAP-2 start-up lifecycle evidence`
5. `ICAO 9432 PUSHBACK-1 pushback and powerback model`
6. `ICAO 9432 VEHICLE-1A vehicle movement and permission lifecycle`
7. `ICAO 9432 VEHICLE-1B vehicle runway crossing and occupancy`
8. `ICAO 9432 VEHICLE-1C towing metadata and vacated geometry`
9. `ICAO 9432 EMERGENCY-1A emergency classification and message payload`
10. `ICAO 9432 EMERGENCY-1B emergency priority and radio silence`
11. `ICAO 9432 EMERGENCY-1C emergency descent safeguarding`
12. `ICAO 9432 EMERGENCY-1D communications failure blind transmissions SSR`

The recommended first execution epic is phraseology evidence architecture,
because it unlocks the largest number of blocked source units and reduces the
risk of typed-protocol evidence being mistaken for spoken-procedure evidence.
The recommended second epic is the policy substrate, because broad phraseology
green-out will otherwise tempt the implementation to bake policy into default
controller behavior.

## Review considerations

FP / type safety:

- New policy concepts should be closed typed vocabularies or sealed classes,
  not strings.
- New evidence records must remain total: optional fields are only acceptable
  where absence is a real domain state.
- Any state transition introduced by pushback, vehicles, or emergency handling
  needs reversal/reset considered before forward behavior is added.
- `error()` is only acceptable for states made impossible by the types.

Test architecture:

- Regulatory tests stay high-level and source-mapped.
- A green source-unit assertion must cite the source unit and prove the actual
  obligation, including rendered wording when wording is the obligation.
- Expected-gap tests or records are acceptable only when they fail loudly and
  identify the missing concept.
- Phraseology matching should prefer structured rendered-token assertions over
  brittle full-string snapshots where the source allows variants.
- Child epic acceptance must fail if source-unit state changes outside the
  declared movement manifest.

Impact:

- Phraseology evidence couples the typed protocol layer to radio rendering, so
  it should be introduced as an adapter/port rather than embedded directly in
  domain decisions.
- Policy concepts make variability explicit but can become a dumping ground if
  policies are vague. Each policy value must have a cited source or local
  aerodrome configuration reason.
- Vehicle and emergency domains expand actor/state space and can destabilize
  existing goldens if they are wired globally without opt-in fixture control.

Operational correctness:

- Every regulatory claim must cite the ICAO Doc 9432 source unit and section.
- CAP 413 or other phraseology sources may supplement only where the simulator
  is intentionally using that phraseology; they do not replace the ICAO 9432
  source unit under test.
- Do not universalize local procedures. Where source text says "normally",
  "when necessary", or "when practicable", bind a policy and test that policy.
