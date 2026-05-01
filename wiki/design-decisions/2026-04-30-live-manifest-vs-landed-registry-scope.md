# Live Manifest Vs Landed Registry Scope

Date: 2026-04-30

## Decision

Treat `research/tools/requirements-spike/documents/*.json` as the live
ingestion manifest, not as proof that every listed window has accepted
registry coverage.

As of this decision:

- 22 windows have landed in `registry/ollama_first/candidates/`;
- 44 sections are listed in the live document manifests;
- the 22 manifest-only additions are clearance/communications windows selected
  for the next ingestion pass;
- RR-17 adequacy evidence applies only to the landed 22-window frame.

## Consequence

Current registry consumers may rely on the 243 accepted candidate records from
the landed 22 windows, but must not treat the 22 manifest-only additions as
accepted coverage. The additions become registry scope only after the standard
ingest, promote, curate, audit, snapshot, and adequacy-review flow passes.

## Review Considerations

FP / type safety: no Kotlin/domain code changed.

Test architecture: verification is count-based and file-backed: candidate
section directories, live document manifests, source-section ledger summary,
registry reproducibility audit, and accepted quote audit.

Impact: this keeps source widening visible without overstating registry
coverage. Future status docs must state both live-manifest count and landed
registry count when they differ.

Operational correctness: no new ATC rule or phraseology claim is made. The
decision only scopes which extracted source windows are accepted evidence.
