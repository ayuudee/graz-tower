# Pressure-test source-mapped evidence projections

## Goal & Context

FN43 made source-mapped evidence tests viable as a permanent Kotlin-native audit core. FN44 should now pressure-test that core against a deliberately small but broad set of harder source units, and improve the design only where those source units force it.

The objective is not coverage volume. The objective is an opinionated, high-quality path for source-mapped tests whose public call sites remain simple while the harness owns projection, provenance, adequacy, and report complexity.

This epic should prove the direction through the minimal number of new source units needed to exercise different capabilities:

1. ICAO Doc 9432 §4.10 essential aerodrome information timing: `icao9432-extracted::essential_aerodrome_information_4_10_en::1aa5cb7e758055bc`.
2. ICAO Doc 9432 §4.1.2 critical-phase transmission restraint: `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4`.
3. ICAO Doc 9432 §2.8.2 transfer of communications, controller-advised frequency change: `icao9432-extracted::transfer_communications_2_8_2_en::40382df156ad071e`.
4. ICAO Doc 9432 §2.8.2 transfer of communications, pilot notification absent advice: `icao9432-extracted::transfer_communications_2_8_2_en::b49ae03cbbb2d538`.

Those four are intentionally disparate enough to force timing evidence, negative/window evidence, cross-unit communication evidence, and fallback/procedural evidence. Do not add more source units unless one of these proves too weak to exercise a needed design facet.

## Architecture & Data Models

The design should keep the FN43 public shape: tests author `protocolEvidence` and `simEvidence`; source citations are typed; source cases fail if they cite sources but activate no facts; expected gaps are typed and backed by `.plan`.

The implementation should widen the evidence fact vocabulary only where forced by the selected source units. Candidate fact families:

- `AerodromeInformationFact`: aircraft, context (`BeforeTaxi`, `BeforeFinalApproach`), information kind/content, source of knowledge (`ControllerTransmission`, `KnownReceivedElsewhere`, `NotAvailable`), and evidence sequence.
- `CriticalPhaseWindowFact`: aircraft, phase (`Takeoff`, `InitialClimb`, `LateFinal`, `LandingRoll`), start/end evidence sequence or sim-time, and aircraft/runway context.
- `TransmissionNecessityFact`: transmission id, aircraft, phase relation, classification (`Routine`, `SafetyNecessary`, `Emergency`, `Unknown`), and source evidence.
- `FrequencyTransferFact`: aircraft, from/to unit or frequency if available, advised-by-controller vs pilot-notified, acknowledgement/readback evidence, and sequence.

Keep these as test/evidence facts unless a production trace boundary clearly demands promotion. Do not leak monitor vocabulary into public tests. If repeated selectors emerge, add narrow DSL helpers such as `aerodromeInformation(aircraft).beforeTaxi()`, `radioDuringCriticalPhase(aircraft).noneRoutine()`, and `frequencyTransfer(aircraft).controllerAdvised()`.

## API Contracts

The public test API should remain terse and domain-shaped. A target call site should look roughly like:

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

The internal adapter may be more complex, but the test must express the source-unit intent, not the simulator’s implementation details. If a case cannot be honestly proven from current observations, it must remain `ExpectedGap` with a narrower typed gap id and `.plan` item. Do not fake coverage by treating absence of evidence as evidence of absence.

Reports must continue to emit Markdown and JSON with source refs, activated fact ids, generated/sample metadata when present, outcome, and adequacy/applicability fields. For these new source units, reports should make the difference between positive evidence, negative/window evidence, and expected gaps visible.

## Edge Cases & Constraints

- Full `:sim:jvmTest` is currently red in unrelated reactive/emergency goldens (`SIM-RED-1`). FN44 must use focused verification and must not claim broad sim green until `SIM-RED-1` is fixed.
- Existing expected gaps `FN43-GAP-1` and `FN43-GAP-2` should be closed only if the implementation emits real facts that activate source cases. If only a narrower subcase is implemented, split the gap rather than marking it done.
- Critical-phase evidence must distinguish no observed routine transmissions from missing phase-window evidence. Missing window evidence is an expected gap or failure, not a pass.
- Aerodrome-information evidence must distinguish passed information, known receipt from another source, and no observed information. The source text allows an exception when receipt from other sources is known; the evidence model must represent that explicitly if used.
- Frequency transfer tests must not become phraseology-rendering tests unless phraseology support is deliberately added. The initial proof should focus on procedural evidence: advised change, notification before change absent advice, and readback/acknowledgement only where already observable.
- The public DSL should not grow a generic scenario builder in this epic. Add small helpers only after at least two call sites prove they reduce ceremony.

## Acceptance Criteria

- [ ] Four selected source units are added to the typed evidence catalog and validated against the registry.
- [ ] At least two new source units become positive source-backed cases with activated evidence facts.
- [ ] Any selected source unit that cannot become positive remains a typed expected gap with a specific `.plan` entry and report metadata.
- [ ] Public tests stay terse: no raw source-unit strings, no fact ids, no monitor/provenance/report plumbing at the call sites.
- [ ] Reports distinguish positive, negative/window, and expected-gap evidence for the new cases.
- [ ] Focused FN44 tests pass under `nix-shell --run './gradlew :sim:jvmTest ...'`.
- [ ] `nix-shell --run './gradlew detekt'` passes.
- [ ] Completion review explicitly red-teams hidden complexity, fake negative evidence, source/decorative citation, and DSL verbosity.

## Boundaries

In scope:

- test/evidence fact vocabulary needed for the selected source units;
- small DSL helpers driven by those cases;
- report improvements needed to make adequacy/applicability honest;
- focused permanent tests proving the design.

Out of scope:

- broad source-unit coverage expansion;
- full phraseology rendering conformance;
- production certification trace redesign;
- fixing `SIM-RED-1`, except to keep it visible as a verification limitation;
- replacing the FN43 DSL with a new framework.

## Decision Context

This epic should continue the FN43 direction, but with a stricter bar: the DSL is only valuable if it survives source units that require projected state rather than merely observing existing instructions and reports.

The opinionated stance is:

- source-mapped tests are not a regulation wall; they are source-bound behaviour/evidence tests;
- the public API should read like domain intent;
- all complexity belongs in typed facts, adapters, and reports;
- absence-based claims must be proven through explicit windows/classifications, not inferred from empty lists;
- expected gaps are acceptable only when they are typed, tracked, and narrow enough to close later.

## Review considerations

**FP / type safety:** New fact payloads should be sealed or enum-backed where the domain is closed. `when` expressions over new payloads/classifications must be exhaustive. Do not use `else -> Unit` or `else -> null`. If a state is constructible by a well-typed caller, handle it with typed outcome/gap semantics rather than `error()`.

**Test architecture:** Prefer high-level sim evidence tests through the public DSL. Unit tests are justified for pure projection adapters, report serialization, and selector semantics where the oracle is independent. Negative/window evidence requires explicit phase-window facts; tests must fail if the window is missing.

**Impact:** This couples the evidence harness more deeply to sim trace vocabulary. Keep the coupling behind adapters and DSL helpers so production behaviour is not reshaped for tests. The failure mode to watch is a large internal projection model that looks formal but is only partially populated. Completion review must inspect both the public call sites and the report output.

**Operational correctness:** Claims must cite the exact source-unit ids above and, where making regulatory/procedural statements in docs or KDoc, cite ICAO Doc 9432 sections: §4.10 for essential aerodrome information, §4.1.2 for critical-phase transmissions, and §2.8.2 for transfer of communications. Phraseology claims are out of scope unless explicitly backed by the §2.8.2 phraseology source unit.
