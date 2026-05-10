# fn-10-complete-remaining-source-window.3 Curate expanded registry outputs

## Description
Review and resolve pending records produced by task 2. Curation must be source-discrete and provenance-preserving: accept, reject, or leave loudly pending with evidence. Do not integrate across sources or deduplicate across documents in this task.

## Acceptance
- [ ] Pending sections from the post-promotion queue are enumerated before curation.
- [ ] Each curated decision is based on the candidate JSON and exact source window.
- [ ] `quality/judgements.csv` is updated only through the established curation path or with an auditable manual-curation artifact.
- [ ] Pending records are reduced to zero, or every remaining pending record has an explicit blocker.
- [ ] A fresh source-processing queue/status report is generated after curation.

## Review Considerations
FP / type safety: curation states must be explicit accepted/rejected/pending; no untyped side-channel skip list.

Test architecture: curation evidence, updated judgements snapshot, and post-curation status report are required.

Impact: mistaken curation directly changes the product corpus. Keep decisions local to each source window and preserve rejected records for audit.

Operational correctness: regulatory/phraseology claims are accepted only when the source window supports them with document/section/line provenance.

## Done summary
TBD

## Evidence
- Commits:
- Tests:
- PRs:
