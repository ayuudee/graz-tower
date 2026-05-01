# Ollama-first registry — stability contract

## Pre-1.0 (`formatVersion: 2026-04-28-v1`)

The schema and `canonicalId` derivation are **not yet a stable public
contract**. Specifically:

- The first registry build is a curation event; pre-Phase-E `canonicalId`
  strings should not be assumed durable. If Phase E surfaces a bug in
  the prompts, gates, or normalisation, the entire registry may be
  rebuilt with new IDs.
- **In-place canonicalId edits are forbidden, even pre-1.0.** The
  registry's reproducibility audit
  (`audit_registry_reproducibility.py --dry-run-only`) hard-fails on
  `canonical_id_recompute_mismatch`; that is the load-bearing
  invariant. The only safe re-issuance procedure is **wipe and
  re-promote from the source `/tmp` run**:
  ```
  rm -rf registry/ollama_first/{candidates,pending,rejected}/*
  python3 promote_to_registry.py --source-run-root /tmp/<latest>
  ```
  Any attempt to hand-edit `canonicalId`, `claimText`, or
  `exactSourceQuotes` will surface as an audit failure on the next CI
  run.
- Downstream consumers should NOT begin to dereference `canonicalId`
  values from this registry until at least the first full corpus
  promotion has settled and human curation of `pending/` has completed.
- The schema-version policy is **strict equality**: any record carrying
  a non-current `formatVersion` is rejected by `validate_registry_record`.
  Bumping the schema therefore requires either a migration script
  (recommended once we have downstream consumers) or a fresh re-promote.

## What IS stable from day one

- Per-record provenance: `provenance.{sourcePath, sourceSha256, startLine,
  endLine}` is captured on every promoted record.
- Verbatim-quote audit: every `exactSourceQuotes` entry must appear
  verbatim in the source-line window after whitespace normalisation
  (`audit_quotes.normalize`).
- Promotion lifecycle: a record never moves between buckets silently.
  Cross-bucket conflicts and source drift surface as structured
  `conflicts` records; the curator decides resolution.

## How to introduce a downstream consumer safely

1. Run `audit_registry_reproducibility.py` (dry-run) and confirm `status: pass`.
2. Snapshot the registry: tag the commit and copy `manifest.json`'s
   `counts` block into the consumer's vendoring metadata.
3. Reference records by `canonicalId` and check the `claimSha256` join
   field on every read — if `claimSha256` is the join key (e.g. an
   outcome CSV), a `canonicalId` can change while `claimSha256` stays
   stable, which is the safer dereference for cross-run joins.
4. Do not treat `originalCandidateId` as identity — the LLM chooses it
   non-deterministically.

## What forces a `canonicalId` change

A `canonicalId` is `{documentId}::{sectionId}::sha256({normalised claim,
sorted normalised quotes, documentId, sectionId})[:16]`. It changes
when:

- the claim text materially changes (more than whitespace / case / outer
  quotes),
- the set of `exactSourceQuotes` changes,
- `documentId` or `sectionId` is re-keyed.

It does NOT change when:

- only rationale changes,
- only modality / authorityClass labels change,
- the model emits a different `originalCandidateId`.
