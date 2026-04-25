# ICAO 4444 Ollama-First Structure Reconciliation Pass 2026-04-24

This pass tested a heavier Ollama-first workflow:

1. three independent structure attempts
2. one structure reconciliation pass
3. three independent extraction attempts
4. one extraction reconciliation pass
5. challenger / defender / judge per reconciled candidate

The code remains orchestration only: source windowing, schema checks, artifact
capture, and small JSON-shape normalization for wrapped model outputs.

## Artifacts

Readback family:

- [summary.md](/tmp/icao4444-ollama-first-prototype-readback-structure-consistency/summary.md)
- [run_manifest.json](/tmp/icao4444-ollama-first-prototype-readback-structure-consistency/run_manifest.json)
- [judged_candidates.json](/tmp/icao4444-ollama-first-prototype-readback-structure-consistency/judged_candidates.json)

Transfer family:

- [summary.md](/tmp/icao4444-ollama-first-prototype-transfer-structure-consistency-v4/summary.md)
- [run_manifest.json](/tmp/icao4444-ollama-first-prototype-transfer-structure-consistency-v4/run_manifest.json)
- [judged_candidates.json](/tmp/icao4444-ollama-first-prototype-transfer-structure-consistency-v4/judged_candidates.json)
- [structure_reconciliation_response.json](/tmp/icao4444-ollama-first-prototype-transfer-structure-consistency-v4/structure_reconciliation_response.json)

## Results

Readback remained stable:

- candidates: `7`
- accepted: `3`
- `needs_split`: `1`
- `needs_bundle`: `1`
- `advisory_only`: `2`

Transfer improved materially:

- candidates: `5`
- accepted: `2`
- `needs_bundle`: `1`
- `advisory_only`: `2`

The key transfer improvement was structural. The prior single-structure pass
compressed the arriving-aircraft enumeration and lost the separate `b)` branch.
The new structure reconciliation preserved:

- `4.3.2.1.1_a`
- `4.3.2.1.1_a_1`
- `4.3.2.1.1_a_2`
- `4.3.2.1.1_b`
- `4.3.2.1.1_c`
- `4.3.2.1.3_a_1`
- `4.3.2.1.3_a_2`
- `4.3.2.1.3_a_3`
- `4.3.2.1.3_b_1`
- `4.3.2.1.3_b_2`

That is the strongest evidence so far that repeated model attempts plus
reconciliation is a useful technique for the source-structure stage.

## Remaining Problems

The challenger still has a recurring authority-class weakness:

- it challenged a `should` clause as if it should be authoritative
- it challenged a note as if the document-level authority ceiling made the note
  authoritative

The judge corrected both in the final transfer run, but the challenger behavior
is still noise. The next workflow improvement should add a modal/authority review
pass or make the challenger explicitly evaluate source modality before assigning
`wrong_authority`.

There is also still some inconsistency in how the judge treats bundled nested
conditions:

- arrival transfer was accepted
- VMC departure transfer was accepted
- IMC departure transfer was marked `needs_bundle`

That may be defensible conservatism, but it is not yet stable enough to treat as
production-quality promotion.

## Review Considerations

- FP / type safety: Not applicable to domain code. The Python harness now fails
  loudly on invalid model JSON and missing required fields, while preserving raw
  outputs for inspection.
- Test architecture: The meaningful proof here is artifact-level comparison
  across two source families. No synthetic unit tests were added because the
  business value is in live model behavior on real source text.
- Impact: Repeated structure attempts improved fidelity on nested enumeration
  without introducing deterministic source interpretation. It increases run time
  and prompt size, which is acceptable for this background research pipeline.
- Operational correctness: The source claims are grounded in `ICAO Doc 4444`
  readback and transfer-of-control clauses from
  [icao4444-extracted.txt](/home/andrew/dev/projects/twr2/research/txt/icao4444-extracted.txt).
