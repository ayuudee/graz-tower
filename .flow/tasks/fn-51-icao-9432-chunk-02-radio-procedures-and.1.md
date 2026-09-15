# fn-51-icao-9432-chunk-02-radio-procedures-and.1 Re-check chunk 02 source units and quote provenance

## Description

Build the chunk-local inventory for all 15 source units in
`chunk-02-radio-procedures-and-policy`. Re-check each accepted candidate JSON
and source text line range before relying on the first-pass classification.

Inputs:

- `research/tools/requirements-spike/quality/icao9432_programme/classification.json`
- `research/tools/requirements-spike/registry/ollama_first/candidates/icao9432-extracted/...`
- `research/txt/icao9432-extracted.txt`

Output:

- `research/tools/requirements-spike/quality/icao9432_programme/chunk_02_radio_procedures_policy/source_plan.md`

## Acceptance

- [x] All 15 `chunk-02-radio-procedures-and-policy` rows are copied into the chunk-local plan with canonical source-unit ids.
- [x] Each row records section id, claim, current first-pass classification, blocker, source text line range, and registry path.
- [x] Each accepted source-unit quote is checked against both `research/txt/icao9432-extracted.txt` and the candidate JSON exact quote metadata where both exist.
- [x] If source text or candidate metadata is unavailable or mismatched, the row records the mismatch explicitly instead of treating either source alone as sufficient.
- [x] Any mismatch between first-pass classification and current evidence reality is recorded loudly for task .2.
- [x] The task does not modify production code or tests.

## Review Considerations

FP / type safety: this task is artifact-only. Preserve machine identifiers exactly; do not normalize ids by string rewriting.

Test architecture: no tests are authored here. The output is the source-accounting basis for later source-mapped tests.

Impact: incorrect inventory will poison all chunk-02 downstream coverage claims. Prefer "needs re-check" over silent acceptance.

Operational correctness: every claim must remain tied to ICAO Doc 9432 section/source-unit identity.

## Done summary
Built chunk-02 source_plan.md for all 15 accepted ICAO 9432 rows. Verified every candidate is accepted, registry quote audit passes, and all exactSourceQuotes match research/txt/icao9432-extracted.txt after whitespace normalization. Recorded reclassification notes for critical-phase policy, engine-start observability, radio-test duration split, PHRASE-1, and POLICY-1.
## Evidence
- Commits:
- Tests: python3 normalized quote/provenance check over 15 chunk-02 candidate JSON files: accepted + quote pass + normalized source-text hits for every exactSourceQuote
- PRs: research/tools/requirements-spike/quality/icao9432_programme/chunk_02_radio_procedures_policy/source_plan.md