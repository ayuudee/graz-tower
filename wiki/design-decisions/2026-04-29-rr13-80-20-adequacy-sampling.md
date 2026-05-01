# RR-13 80/20 Adequacy Sampling

Date: 2026-04-29

## Decision

Use a deterministic fixed-seed review pack as the first independent
statistical adequacy check for the Ollama-first registry.

The pack samples two frames:

- 48 registry records, stratified by lifecycle bucket, source document,
  authority class, modality, testability, and risk tags.
- 12 source sections, stratified by document and source-shape risk, for
  omission review against line-numbered source windows.

The generated pack is
`research/tools/requirements-spike/quality/adequacy/adequacy_2026-04-29-rr13-80-20/`.
It contains review CSVs, a markdown review guide, a manifest, and the
source-window excerpts. This is intentionally not a second extraction
pipeline; it is a bounded independent review lane for deciding whether
the current registry is a full and honest enough translation of the
ingested corpus.

## Rationale

The registry now has reproducible IDs, quote checks, gate audit trails,
and curation ledger rows. Those establish internal consistency, but not
corpus adequacy. A small stratified sample gives fast signal on the two
risks that matter most now: accepted/rejected records being wrong, and
source sections hiding material omissions.

With zero major record errors in 48 records, the rule-of-three upper
bound is about 6.3% for the sampled frame. With zero material omissions
in 12 sections, the section-side bound is about 25%, so the section
sample is a smoke check for systemic omissions rather than a proof of
completeness.

## Review considerations

- FP / type safety: not applicable to domain Kotlin state. The Python
  sampler is deterministic and fails normally on malformed JSON or
  missing source files rather than papering over unreadable inputs.
- Test architecture: focused quality-gate tests now cover risk tagging,
  deterministic record sampling, and source-section sampling. The final
  adequacy verdict is not automated; it comes from the filled review
  CSVs.
- Impact: this adds a review lane and generated quality artifacts only.
  It does not change the registry schema, promotion gates, or downstream
  consumers.
- Operational correctness: the sampler does not assert ATC law or
  phraseology. Reviewers must compare sampled claims to the cited source
  excerpts before treating a record as operationally valid.
