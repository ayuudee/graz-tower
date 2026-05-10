# fn-10-complete-remaining-source-window.4 Validate closure and rebuild packages

## Description
Run final validation after ingestion and curation, then rebuild the source-unit packages that supersede the fn-9 current-frame artifacts. The final report must show the three levels requested by the user: source, section, and tactical.

## Acceptance
- [ ] Final status report shows zero ready-to-ingest windows, zero unexplained pending curation records, zero failed retry candidates, and zero high-priority source-window hardening rows.
- [ ] Quote audit, registry reproducibility, quality gates, and override-contract checks pass, or any failure is explicit and blocking.
- [ ] Source packages are rebuilt for the expanded source-unit corpus.
- [ ] Package validation/closure artifacts are regenerated and point to the final queue/report.
- [ ] The final answer includes the status script output or a faithful excerpt containing source, section, and tactical levels.

## Review Considerations
FP / type safety: package/status generators must fail on unknown states and must not coerce missing counts to success.

Test architecture: final evidence must include command output for queue/report generation, validation commands, and package validation.

Impact: these packages become the current structured-output product for existing sources. Historical fn-9 packages remain evidence, not the final claim.

Operational correctness: package claims are source-discrete and cite source documents/sections; Nolan and EPPLS remain excluded future sources unless explicitly onboarded.

## Done summary
TBD

## Evidence
- Commits:
- Tests:
- PRs:
