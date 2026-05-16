# fn-20-fn10-source-unit-ingest-close-out-and.4 Promote and audit complete v6 source units

## Description
TBD

## Acceptance
- Complete sections are promoted or packaged only after raw audit passes.
- Existing quote/schema/reproducibility gates are run and results recorded.
- Failed sections remain explicitly non-promoted.


## Done summary
Promoted the final raw root into registry/ollama_first and ran the existing deterministic gates. Reproducibility audit passed, quote audit passed with 2417/2417 quotes matched, quality-gate tests passed, and override-contract tests passed. Pending records remain explicit and are tracked as curation work, not ingest failures.
## Evidence
- Commits:
- Tests:
- PRs: