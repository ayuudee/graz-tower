# Current Source-Unit Closure Review

Generated: `2026-05-09T11:09:16Z`

Status: `pass_with_scoped_nonclaims`

## Gates

- qualityGates: `pass: all 91 quality-gate tests passed`
- overrideContracts: `pass: all 13 contract tests passed`
- registryReproducibility: `pass`
- quoteAudit: `pass`
- flowValidation: `pass`

## Source Classifications

| Source | Readiness | Sections | Accepted | Pending | Hardening | Reason |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| cap413-extracted | scoped-package-ready | 12/12 | 79 | 0 | 4 | manifested windows are landed, but high-priority source-window hardening remains outside current package scope |
| egast-vfr-extracted | package-ready | 1/1 | 17 | 0 | 0 | manifested windows landed, no pending records, no source-window hardening backlog |
| h01-extracted | scoped-package-ready | 4/4 | 24 | 0 | 10 | H01 is English-side scoped and not full-document complete; see H01 source readiness review |
| icao4444-extracted | scoped-package-ready | 15/15 | 186 | 0 | 47 | manifested windows are landed, but high-priority source-window hardening remains outside current package scope |
| icao9432-extracted | scoped-package-ready | 4/4 | 22 | 0 | 13 | manifested windows are landed, but high-priority source-window hardening remains outside current package scope |
| safetysense22-extracted | package-ready | 1/1 | 15 | 0 | 0 | manifested windows landed, no pending records, no source-window hardening backlog |
| sera-923-2012-extracted | scoped-package-ready | 9/9 | 73 | 0 | 8 | manifested windows are landed, but high-priority source-window hardening remains outside current package scope |
| slovenia-vfr-extracted | package-ready | 1/1 | 15 | 0 | 0 | manifested windows landed, no pending records, no source-window hardening backlog |

## Adequacy Sample

- Records reviewed: `48`; verdicts: `{'correct': 48}`
- Sections reviewed: `12`; verdicts: `{'no_material_omission': 12}`
- Quote audit: `546` quotes, `0` misses

## Residual Gaps

- High-priority source-window hardening rows: `82`
- Ready-to-ingest manifest windows: `0`
- Pending curation records: `0`
- Failed-window retry candidates: `0`
- Nolan and EPPLS remain excluded future sources for this current-frame package.

## Interpretation

The current-frame registry is ready to package with scoped non-claims. Sources with hardening backlog are not full-document-complete; their packages must state the exact manifested-section scope. No current-frame source is blocked by pending records, failed manifest windows, reproducibility mismatch, or quote/source misses.
