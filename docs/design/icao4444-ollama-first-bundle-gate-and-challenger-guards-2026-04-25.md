# ICAO 4444 Ollama-First Bundle Gate And Challenger Guards 2026-04-25

This pass closes RR-4 (Ollama challenger ignores source modality) and resolves
the v4 VMC/IMC judge inconsistency that was tracked as a residual concern of
RR-3.

## Spike Sequence

The investigation ran three additive spikes against the Spike 0 baseline (the
v4 transfer-structure-consistency run). Each spike was run on both the readback
family (`icao4444-extracted.txt:3401-3429`) and the transfer family
(`:3086-3131`).

### Spike 1 — Directional Verdict Rename

`wrong_authority` was hard-renamed to `authority_too_high` /
`authority_too_low` with directional semantics in the challenger system prompt.

Outcome: the rename surfaced a deeper bug the v4 single verdict had hidden —
**cross-clause source contamination**. The challenger pulled `should` text from
§4.3.2.1.2 (about communications timing) and used it to argue about candidates
sourced from §4.3.2.1.3.a (departure transfer triggers). Five of eleven
challenger verdicts across both families were wrong this way.

The judge filtered most of these and the final judgment surface stayed
broadly correct, but the challenger was no longer doing real adversarial work.

### Spike 2 — Modality Grounded In Candidate Items

Added required `candidateSourceItemModalities` and `effectiveModality` fields.
The challenger must read each cited `sourceItemId`, identify its modality
marker from the structure, and only then assign a verdict. All `sourceQuotes`
must come from the cited items.

Outcome: cross-clause contamination eliminated. Modality declarations were
consistently correct. `shall` clauses correctly verdicted `supported` for the
first time. Three residual bugs surfaced cleanly:

- **Notes-as-authoritative bias.** The challenger pushed back on
  correctly-classified Note candidates ("should be considered an authoritative
  requirement due to its prescriptive nature"), violating ICAO convention that
  Notes are non-normative explanatory text.
- **Direction inversion on advisories.** On at least one row, the concern said
  "lower the authority" but the verdict said `authority_too_low` (which means
  "raise it"). The terminology was internally inconsistent.
- **Structural concerns mis-labelled as authority verdicts.** On candidates
  spanning a parent clause plus nested conditions, the challenger used
  `authority_too_high` to express bundling concerns ("overreach by implying a
  single authoritative requirement where there are mixed modalities").

A second observation from this spike: reconciler stochasticity produced an
"umbrella" candidate decomposition that crashed transfer accepted-count to
zero. That is independent of challenger quality and underlines that single
runs cannot be compared candidate-for-candidate across spikes.

### Spike 3 — Bundle Gate Plus Two Challenger Guards

Three additive changes:

1. **Bundle gate** — a new validation stage between defense and judge. Per
   candidate, the bundle gate answers a single mechanical question: does the
   parent or governing clause's operative meaning require subordinate items
   that the candidate does not include? Output:
   ```json
   {
     "scopeComplete": true,
     "missingDependencies": [],
     "rationale": "..."
   }
   ```
2. **Notes-as-non-normative challenger guard** — the system prompt now
   instructs that ICAO Notes are non-normative explanatory text unless they
   contain a `shall` verb, and forbids `authority_too_low` against advisory-
   classified Notes that contain only `may`/`should`/no modal.
3. **Verdict-direction sanity check** — the system prompt explicitly maps
   "should sit at LOWER authority" to `authority_too_high` and "should sit at
   HIGHER authority" to `authority_too_low`, and requires the verdict's
   direction and the concern text to agree.

The judge prompt now consumes the bundle gate and uses it mechanically: if
`scopeComplete` is true, the judge does not return `needs_bundle`; if false,
`needs_bundle` is preferred. Structural fragmentation across multiple
separable obligations remains `needs_split` regardless.

## Results

Comparison across all four runs on the same source families.

| Family | v4 (Spike 0) | Spike 1 | Spike 2 | Spike 3 |
|---|---|---|---|---|
| transfer (5 cands) | 2 accepted | 2 accepted | 0 accepted | **3 accepted** |
| readback (7 cands) | 3 accepted | 4 accepted (6 cands) | 4 accepted | **4 accepted** |

Both families reach their cleanest judgement surface so far in Spike 3. The
v4 VMC/IMC judge inconsistency (`req_departure_transfer_vmc` accepted but
`req_departure_transfer_imc` `needs_bundle`) is fully resolved: both rows are
`accepted` with effectively identical rationales.

The judge now references the bundle gate directly. Example rationale from the
Spike 3 IMC run:

> "The bundle gate confirms scope completeness (scopeComplete: true) and
> identifies no missing dependencies. The challenge regarding mixed modalities
> is resolved by the defense and source text..."

## Bug Class Status After Spike 3

| Bug | v4 status | Spike 3 status |
|---|---|---|
| Cross-clause source contamination | hidden | fixed |
| Notes-as-authoritative bias | yes | fixed |
| Direction inversion (advisory rows) | hidden | fixed |
| VMC/IMC judge inconsistency (RR-3 residual) | yes | fixed |
| Structural concerns mis-labelled as authority verdicts | hidden | survives, filtered by bundle gate |

The surviving bug is tracked as RR-5. It does not affect final judged outcomes
in the current source families, but it makes the challenger surface noisier
than it should be.

One legitimate Spike 3 finding: readback `req_flight_crew_readback_safety` got
`needs_bundle` because the bundle gate correctly identified that §4.5.7.5.1's
parent clause (`The following items shall always be read back:`) requires the
subordinate list items (a)(b)(c) to be operatively complete, and the candidate
cited only the parent. This is more defensible than the v4 outcome
(`needs_split` with no specific reason) and more conservative than Spike 2's
`accepted`.

## Architecture Implication

Spike 3 establishes a clean separation of concerns inside per-candidate
adjudication:

- **Challenger** — content and authority; whether the candidate's
  `claimText`, `authorityClass`, and `modality` are consistent with the cited
  items' text.
- **Defender** — source-grounded justification of the candidate.
- **Bundle gate** — pure structural completeness; whether the candidate's
  `sourceItemIds` cover everything its parent's operative meaning requires.
- **Judge** — weighs all of the above. Authority comes from challenger and
  defense; structure comes from the bundle gate.

This split means each stage has a narrower job and its failure modes do not
contaminate other stages. The bundle gate's deterministic structural answer
also breaks the judge's prior dependence on free-form structural reasoning,
which was the root cause of the v4 VMC/IMC inconsistency.

## Review Considerations

- **FP / type safety**: not applicable to domain code. The Python harness
  continues to fail loudly on invalid model JSON or missing required fields.
  The bundle-gate output is schema-validated like every other stage.
- **Test architecture**: meaningful proof remains artifact-level — same two
  source families, same model configuration, four runs, comparable artifacts
  in `/tmp/icao4444-ollama-first-prototype-{readback,transfer}-{...}/`. No
  synthetic unit tests added because the business value is in observed live
  model behaviour.
- **Impact**: the bundle-gate stage adds one Ollama call per candidate. With
  up to eight candidates per run and the existing model latencies, this is
  comfortably inside the latency budget already established for the
  Ollama-first prototype.
- **Operational correctness**: source claims remain grounded in
  [icao4444-extracted.txt](/home/andrew/dev/projects/twr2/research/txt/icao4444-extracted.txt)
  readback and transfer-of-control clauses.
