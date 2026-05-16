# FN20 Pending Registry Curation

## Goal & Context
Resolve the `1356` pending registry records left by the FN20 final v7 promotion. This is a curation pass over already-ingested candidates, not another Ollama extraction run. The source run root is `/Users/andrew/requirements-source-units-fn10-2026-05-11-v6`; the registry root is `research/tools/requirements-spike/registry/ollama_first`.

## Architecture & Data Models
Use the existing curation driver `research/tools/requirements-spike/curate_pending_with_gpt.py` and its audit trail:

- input pending records: `registry/ollama_first/pending/`
- original source/run context: `/Users/andrew/requirements-source-units-fn10-2026-05-11-v6`
- curation audit output: `research/tools/requirements-spike/quality/curation/<runId>/`
- lifecycle ledger: `research/tools/requirements-spike/quality/judgements.csv`
- registry manifest: `research/tools/requirements-spike/registry/ollama_first/manifest.json`

The driver currently requires `OPENAI_API_KEY` and uses OpenAI JSON response mode. No API key is present in `.env` or the current shell as of 2026-05-16.

## API Contracts
Run curation with:

```bash
set -a; source .env; set +a
python3 research/tools/requirements-spike/curate_pending_with_gpt.py \
  --source-run-root /Users/andrew/requirements-source-units-fn10-2026-05-11-v6 \
  --limit 3 --dry-run
```

Then run a non-dry smoke, then the full pass, then refresh the manifest and audits.

## Edge Cases & Constraints
- Do not curate without an explicit audit artifact per record.
- Do not hand-edit identity fields (`claimText`, `exactSourceQuotes`) during curation.
- If API/auth is unavailable, stop loudly with the missing prerequisite rather than pretending curation completed.
- Pending EPPLS records are background-support candidates only; do not promote them as legal or phraseology authority.
- Cross-bucket or duplicate conflicts must remain visible in the promotion/curation audit trail.

## Acceptance Criteria
- [ ] Frontier-model API prerequisite is present and verified with a dry-run smoke.
- [ ] A small non-dry smoke curation completes and passes registry audits.
- [ ] Full curation pass completes or is deliberately paused with exact remaining count.
- [ ] `registry/ollama_first/manifest.json` is refreshed after curation.
- [ ] Registry reproducibility and quote audit pass after curation.
- [ ] Final curation report records action counts, residual pending records, and non-claims.
- [ ] `.plan` and wiki are updated to replace or close `FN20-CUR-1` as appropriate.

## Boundaries
- Do not rerun source ingestion unless curation exposes a true source-window defect.
- Do not claim pending records are accepted source units.
- Do not run multiple curation workers against the same pending tree.

## Decision Context
The final v7 ingest is complete: no ready-to-ingest windows remain and the raw/promotion gates pass. The remaining work is adjudication of conservative demotions. Curation should reduce the pending queue while preserving the audit trail for every decision.

## Review Considerations

**FP / type safety:** Curation actions must be explicit lifecycle transitions: accepted, rejected, or still pending. Corrections are limited to non-identity metadata and must pass schema and authority/modality gates.

**Test architecture:** Required checks are a dry-run smoke, a non-dry smoke with reproducibility and quote audits, then full-pass reproducibility/quote audits. The ledger must remain append-only and latest-row semantics must match registry state.

**Impact:** This changes registry lifecycle state and may promote many records. It increases accepted source-unit coverage but can also add bad records if the curator is over-trusted; the audit trail and gates are the control surface.

**Operational correctness:** Curation must preserve source authority boundaries. CAP/ICAO/SERA/H01 can support operational/legal claims according to their manifest ceilings; EPPLS and Nolan remain background by default.
