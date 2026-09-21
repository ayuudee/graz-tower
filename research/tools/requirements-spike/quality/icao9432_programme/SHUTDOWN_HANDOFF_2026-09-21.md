# ICAO 9432 Programme Shutdown Handoff - 2026-09-21

This note is the durable resume point for the ICAO Doc 9432 source-mapped
regulatory testing programme after fn-89.

## Repository State

- Branch: `main`.
- Remote state at handoff start: `origin/main` matched local `main`.
- Latest programme commit before this handoff: `176b6d6f fn-89 cover communications phraseology examples`.
- Flow status at handoff start: 3 open epics, 89 done epics, 48 todo tasks, 0 in progress, 0 blocked, 221 done tasks.

## Recent Completed Work

- `fn-87`: covered the helicopter air-taxi rendered phraseology branch.
- `fn-88`: covered ICAO 9432 section 4.10 essential aerodrome information
  example phraseology with synthetic rendered example evidence.
- `fn-89`: covered ICAO 9432 section 2.8.1 communication phraseology examples
  for full-callsign initial contact and ground/aircraft `ALL STATIONS`
  broadcasts with synthetic rendered example evidence.

The current phraseology direction is intentionally narrow: source units that
are example-dialogue rows can use synthetic rendered example evidence, while
live controller/pilot phraseology and operational routing still need trace
backed evidence before they can move green.

## Validation Baseline

The latest implementation epics recorded green validation before commit:

- `fn-88`: focused evidence tests, detekt, broad protocol/core/sim validation,
  flow validation, implementation review, completion review, and diff check
  passed.
- `fn-89`: focused communication phraseology evidence tests, detekt,
  `:protocol:allTests :core:allTests :sim:jvmTest`, flow validation,
  implementation review, completion review, and diff check passed.

This shutdown handoff is docs-only. A future implementation pass should rerun
the relevant focused tests and the programme flow validation for its own epic.

Shutdown-doc validation:

- `git diff --check`: pass.
- `scripts/ralph/flowctl validate --epic fn-89-fn-89-icao-9432-chunk-01-communications --json`: pass.
- `scripts/ralph/flowctl validate --all --json`: fails on historical flow
  metadata in early completed epics, for example done epics with old todo tasks
  or tasks missing newer required headings. This was not introduced by the
  shutdown docs pass and should not be treated as a 9432 programme regression.

## Active Implementation Manifest Snapshot

`implementation_blocker_manifest.csv` currently has 143 implementation-tracking
rows. This is not the full source-unit inventory; the full accepted ICAO 9432
inventory remains 166 units in `README.md`.

Current final-state counts in the implementation manifest:

| Final state | Rows |
|---|---:|
| `phraseology-later` | 28 |
| `policy-blocked` | 22 |
| `covered-structural` | 8 |
| `covered-green` | 7 |
| `model-gap + policy-blocked` | 6 |
| `covered-synthetic rendered example phraseology` | 4 |
| `covered-green rendered phraseology` | 4 |
| `model-gap` | 4 |
| `model-gap + phraseology-later` | 4 |

The remaining states include smaller covered/split rows. Use the CSV as the
source of truth before opening the next epic.

## Remaining Work By Chunk

- `chunk_01_comms_readback_transfer`: 2 `policy-blocked`, 1 plain
  `phraseology-later`, 2 split phraseology residues.
- `chunk_02_radio_procedures_policy`: 11 `phraseology-later`, 1 split
  duration/content row, 1 `policy-blocked`.
- `chunk_03_ground_movement`: 2 `phraseology-later`, 1 `model-gap`, 1 split
  apron-management policy row, 1 `policy-blocked`.
- `chunk_04_runway_departure`: 9 `policy-blocked`, 4 `phraseology-later`, 1
  split takeoff-roll/dangerous-traffic policy row.
- `chunk_05_circuit_arrival_landing`: 10 `phraseology-later`, 6
  `policy-blocked`, 3 split timing/distance/policy rows.
- `chunk_06_go_around_after_landing_aerodrome_info`: 3 `policy-blocked`, 3
  `model-gap + policy-blocked`. Plain phraseology-later rows in this chunk
  have been cleared by fn-84, fn-86, fn-87, and fn-88.
- `chunk_07_vehicles_towing`: 3 `model-gap + policy-blocked`, 1 `model-gap`.
- `chunk_08_distress_urgency_comms_failure`: emergency, communication-failure,
  and blind-transmission rows remain mixed across `model-gap`,
  `model-gap + phraseology-later`, and split covered/remaining states.

## Recommended Next Epic

The next small, high-signal candidate is:

- Source unit:
  `icao9432-extracted::readback_2_8_3_en::fe3b04ca9c3384d9`
- Candidate file:
  `research/tools/requirements-spike/registry/ollama_first/candidates/icao9432-extracted/readback_2_8_3_en/icao9432-extracted::readback_2_8_3_en::fe3b04ca9c3384d9.json`
- Source text:
  `research/txt/icao9432-extracted.txt:3915`
- Citation:
  ICAO Doc 9432, Fourth Edition, 2007, section 2.8.3.3.
- Claim:
  ATC route clearance is not an instruction to take off or enter an active
  runway.

Suggested shape:

- Treat this as structural protocol evidence, not rendered phraseology, if the
  existing protocol surface can honestly prove it.
- Add a typed source ref under the ICAO 9432 readback references.
- Assert that `ClearedTo` remains route-domain/readback evidence and is not a
  runway-entry or takeoff instruction.
- Update chunk 01 docs and the central implementation manifest only if the
  evidence directly proves the source unit.

Review considerations for the next epic:

- FP / type safety: do not add catch-all branches or stringly instruction
  checks; use closed protocol/domain types.
- Test architecture: prefer one high-level source-mapped evidence test over a
  structural test that only repeats compiler facts. If the business value is
  only "this type is this type", do not pretend it proves the source unit.
- Impact: avoid changing controller behavior unless the evidence exposes a real
  behavioral gap. This source unit is likely about preserving the route/runway
  boundary, not introducing new runtime behavior.
- Operational correctness: quote-check section 2.8.3.3 before implementation;
  if the current model cannot express "active runway entry" separately enough,
  leave the row visibly blocked instead of overclaiming.

## Resume Checklist

1. `git pull --ff-only`.
2. Read this file, `AGENT_DIALOGUE.md`, and
   `research/tools/requirements-spike/quality/icao9432_programme/implementation_roadmap.md`.
3. Recompute the manifest snapshot before selecting work:
   `python3` over `implementation_blocker_manifest.csv`, grouped by
   `final_state` and `chunk`.
4. Use flow-next or `scripts/ralph/flowctl` to open the next explicit epic.
5. Plan, review, implement, self-assess, review, validate, commit, and push.
