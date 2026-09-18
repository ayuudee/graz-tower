# fn-62 essential aerodrome information manifest

Status: task-1 planning and impact artifact for
`fn-62-icao-9432-fn43-gap-1-essential`.

Source of truth: §4.10 rows in `classification.csv` joined to
`implementation_blocker_manifest.csv`.

## Movement Manifest

Green-targeted in fn-62:

| Source unit | Claim | Required evidence | Not proven |
| --- | --- | --- | --- |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::18288908932d5ee8` | Essential aerodrome information concerns the movement area and associated facilities necessary for safe operation. | Structural vocabulary for movement-area / associated-facility domains and safety relevance. | It does not prove live sim projection, timing, receipt, phraseology, or every possible local pertinence decision. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::01c0a4bc62b1e926` | Water on runway, taxiway, or apron. | Structural `WaterOnMovementArea` vocabulary plus runway/taxiway/apron facets. | It does not prove the water was transmitted to a pilot in a live scenario. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::502221a46fcc2879` | Rough or broken movement-area surfaces. | Structural `RoughOrBrokenSurface` vocabulary plus runway/taxiway/apron facets. | Live projection/timing/receipt remains separate. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::66196c8442372a96` | Construction or maintenance on or adjacent to movement area. | Structural `ConstructionOrMaintenance` vocabulary with on/adjacent movement-area facets. | Live projection/timing/receipt remains separate. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::736cc42a00337fee` | Snow banks or drifts adjacent to movement area. | Structural `SnowBankOrDrift` vocabulary with adjacent runway/taxiway/apron facets. | Live projection/timing/receipt remains separate. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::9b6c9dbc2af2b5b0` | Other temporary hazards, including parked aircraft and birds. | Structural `TemporaryHazard` vocabulary with parked-aircraft and bird facets. | Open-ended pertinence policy remains separate. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::a531dea421075380` | Failure or irregular operation of aerodrome lighting systems. | Structural `LightingSystemFailureOrIrregularOperation` vocabulary with lighting-system facet. | Live projection/timing/receipt remains separate. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::c874e24413f4cdee` | Snow, slush, or ice on runway, taxiway, or apron. | Structural `WinterContamination` vocabulary plus runway/taxiway/apron facets. | Live projection/timing/receipt remains separate. |

Remain blocked in fn-62:

| Source unit | Reason |
| --- | --- |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::1306eb5cc586df34` | Omission when already known requires aircraft-known-information state plus omission/source policy. Existing ATIS-known projection is support evidence only. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::1aa5cb7e758055bc` | Timing before start-up/taxi/final requires information receipt/transmission evidence plus "whenever possible" timing policy. Existing ATIS-known-before-taxi projection is support evidence only. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::c92651071a9c009e` | "Any other pertinent information" requires open-category pertinence policy. |

Untouched in fn-62:

| Source unit | Reason |
| --- | --- |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::7b81f87f5c2b4d75` | Phraseology example remains under `PHRASE-1`. |

Anti-overcoverage guard:

- A typed category enum is not enough to green a row. Each covered-structural
  row needs a source-mapped evidence-vocabulary case that activates concrete
  information payload facets and safety relevance for the relevant source
  semantics.
- Category rows do not imply timing, receipt, omission, or phraseology
  compliance.
- The open "other pertinent information" row is not greened by adding an
  `Other` catch-all.

## Impact Assessment

Decision:

- Add a small closed essential-aerodrome-information structural vocabulary to
  the evidence/test surface.
- Keep current live ATIS-known timing support as support-only; do not move
  timing/omission policy rows green in this epic.
- Avoid runtime controller behavior changes.

Coupling:

- Evidence DSL gains a selector over typed essential-aerodrome-information
  structural vocabulary facts.
- Category facts are evidence payloads, not world model state.
- Later runtime work can project these facts from real hazards without changing
  source refs.

Failure modes:

- False green by enum existence: prevented by requiring activated payload facts.
- Category-to-timing leakage: prevented by leaving timing/omission rows blocked.
- Open-category catch-all: explicitly deferred to policy work for
  `c92651071a9c009e`.

## Review Considerations

FP / type safety:

- Use closed categories and explicit movement-area/facility domains.
- Selectors must handle empty evidence as failure, not vacuous pass.
- Do not add runtime state fields in this epic.

Test architecture:

- Source-mapped tests should be structural evidence-vocabulary tests for
  category/domain/facet representability, not low-level enum tests or live
  scenario-observation tests.
- Existing `EvidenceProjectionPressureTest` remains support coverage for
  ATIS-known-before-taxi but does not close timing policy.

Impact:

- Low runtime impact: no controller/pilot behavior change.
- Medium evidence-surface impact: later projections can emit the same typed
  payloads from live hazards.

Operational correctness:

- ICAO Doc 9432 §4.10 category rows are represented as structural vocabulary
  for categories, domains, facets, and safety relevance only.
- The "shall be passed" timing/receipt rule and the "may be omitted when known"
  rule remain separate because they need pilot-known state and policy evidence.
