# Complete Remaining Source-Window Hardening Rows

## Goal & Context
Complete the 82 high-priority source-window hardening rows left after the current-frame packaging pass. "Complete" means: exact-window them, add bounded manifests, process through the Ollama-backed section pipeline, promote/curate with provenance, and rebuild validation/closure/package evidence so the current sources no longer carry those hardening rows as unexplained scope gaps.

## Boundaries
This epic is still source-discrete. It does not integrate sources, deduplicate across documents, choose regulatory precedence, or ingest Nolan/EPPLS. It only completes the 82 current-source rows listed in `source_processing_queue_2026-05-09-fn9-final/needs_source_window_hardening.csv`.

## Approach
1. Reconcile each hardening row to exact line windows, splitting broad sections into smaller manifest windows when necessary to avoid candidate-cap and JSON truncation failures.
2. Update document manifests and ledger rows so the queue builder can produce explicit ready-to-ingest batches.
3. Run dry-run validation, then process batches through Ollama at `biggy:11434`, promoting outputs through normal registry gates.
4. Curate pending records only with verified run roots.
5. Re-run quote audit, reproducibility, quality gates, adequacy/closure review, and source package validation.

## Acceptance Criteria
- [ ] The 82-row hardening backlog is reduced to zero or every non-zero row has an explicit blocker/non-claim approved by evidence.
- [ ] New manifest windows have exact source line ranges and authority ceilings.
- [ ] Batch dry-run passes before live Ollama processing.
- [ ] Live processing, promotion, and curation complete without hidden failures.
- [ ] Final status report shows no ready-to-ingest windows, no pending curation records, no failed retry candidates, and no unexplained high-priority hardening rows.
- [ ] Quote audit, registry reproducibility, quality gates, and override contracts pass.
- [ ] Source packages and validation report are rebuilt for the expanded source-unit set.

## Review Considerations
FP / type safety: manifests and package statuses must remain total and explicit. Unknown queue states or malformed package statuses must fail generation.

Test architecture: every stage needs deterministic evidence: dry-run batch validation, registry reproducibility, quote audit, quality gates, override contracts, and package validation.

Impact: this will materially expand the registry and may create many pending/rejected records. Existing fn-9 packages remain historical artifacts; the final product for this epic must supersede them with a new package root.

Operational correctness: all ATC law and phraseology claims must retain exact document/section/line provenance. Authority ceilings must match source type: ICAO/SERA authoritative, CAP/H01/ICAO9432 operational guidance unless a narrower source note justifies otherwise.
