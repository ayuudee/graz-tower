# fn-61 PHRASE-1 rendered phraseology movement manifest

## Scope

fn-61 builds the rendered phraseology evidence architecture and proves it with
a narrow non-emergency proof set. It must not turn `PHRASE-1` into a broad
green-out epic. The first pass only moves source units whose live sim trace
already emits the underlying typed protocol event and whose wording obligation
can be proven by structured rendered tokens.

## Green-targeted source units

| Source unit | Current state | Target state | Obligation type | Evidence surface |
|---|---|---|---|---|
| `icao9432-extracted::final_approach_landing_4_7_en::a4c8fffd8a61adb4` | `phraseology-later with typed-trace evidence` / `PHRASE-1` | `covered-green` | ordered phrase + semantic slots | LOWG touch-and-go trace emits `ClearedTouchAndGo`; rendered tokens must include aircraft callsign, `CLEARED`, `TOUCH`, `AND`, `GO`, and no full-stop landing clearance meaning. |

## Architecture proof but remain-blocked source units

These units may be used to test renderer taxonomy or unsupported-result
behavior, but they must not move green in fn-61.

| Source unit | Reason it remains blocked |
|---|---|
| `icao9432-extracted::communications_2_8_1_en::a685cef087951878` | Full aeronautical station callsign semantics are not yet modeled strongly enough. Controller ids such as `LOWG_TOWER` are not automatically rendered RT station callsigns. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::35ad4a7f3d8d2ead` | Live D-PF.1 start-up lifecycle remains absent; synthetic `RequestStartup` rendering would not prove observed simulator phraseology. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::39a4619cec62a93f` | Live start-up approval and QNH phraseology are not emitted in the current departure flow. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::13264a6ac6d529c3` | Renderer support exists for `ClearedForTakeoff`, but the source unit is classified as support-only / review-only example phraseology after line-up for immediate departure. It is not moved as standalone covered-green in fn-61. |
| `icao9432-extracted::final_approach_landing_4_7_en::1327871f46c1d348` | Low-pass undercarriage observation is not emitted in current live traces. |

## Intentionally untouched

- All emergency phraseology units. Emergency wording stays blocked until
  emergency state and message payload evidence exists.
- All vehicle/tow phraseology units. Vehicle/tow wording stays blocked until
  vehicle/tow actors and metadata exist.
- All pushback phraseology units. Pushback wording stays blocked until the
  pushback model exists.
- Ambiguity/forbidden-meaning units such as taxi phraseology that must not
  imply runway entry or take-off clearance, unless fn-61 implements explicit
  negative semantic classifiers for that exact unit.

## Anti-overcoverage guard

fn-61 completion must fail review if any source unit outside the green-targeted
table is claimed as covered-green. In particular:

- renderer support for a protocol leaf is not source-unit coverage;
- rendered text alone is insufficient without typed protocol provenance;
- substring matching is insufficient for ordered phrase or forbidden-meaning
  obligations;
- synthetic rendering tests may prove renderer mechanics only, not observed
  simulator compliance.

## Review considerations

FP / type safety: rendered phraseology evidence should use closed typed token
kinds and obligation kinds. Unsupported utterance leaves must return typed
absence/unsupported evidence, not empty success.

Test architecture: proof-set tests must be high-level source-mapped sim tests.
Renderer unit tests may cover pure tokenization mechanics, but source movement
requires live trace evidence.

Impact: the renderer is a test-side adapter over `TransmissionRecord`, not a
domain-decision dependency. It must not alter controller, pilot, sim behavior,
or radio timing.

Operational correctness: this pass cites ICAO Doc 9432 source-unit ids only.
It proves the selected ICAO phrase snippets as rendered tokens, not a complete
CAP 413 phraseology system.
