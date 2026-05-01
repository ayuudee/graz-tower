# Clearance And Communications Ingestion Status

Date: 2026-05-01

## Summary

The 2026-04-30 v2 overnight ingestion run is quarantined. It produced useful
diagnostic evidence, but it timed out on the pre-split CAP 413 §§2.82-2.91
window and then every SERA / ICAO Doc 4444 / ICAO Doc 9432 section failed with
Ollama connection timeouts. Do not promote v2 artifacts.

Clean v3 ingestion and promotion succeeded for the highest-value 80/20 subset:

- SERA.8015(f), SERA.8020, SERA.8025, SERA.8030, and SERA.8035.
- CAP 413 §§2.82-2.87, §2.88, and §§2.89-2.91.

The remaining first-batch windows are still manifest-only. The v2 CAP 413
§§2.54-2.75 outputs are not used because the run tree was affected by the
pre-split retry/race and later timeout state.

## v2 Failure

Run root:

- `/tmp/clearance-comms-2026-04-30-ingest-v2`

Observed failures:

- CAP 413 `compliance_and_communication_failure_2_82_to_2_91`: timed out.
- ICAO Doc 4444 first-batch windows: `URLError` / connection timeout.
- ICAO Doc 9432 §2.8.2: `URLError` / connection timeout.
- SERA first-batch windows: `URLError` / connection timeout.

Conclusion: v2 is a diagnostic run only. It proved the combined CAP 413
§§2.82-2.91 window was too broad and helped justify splitting it, but it is not
a promotion source.

## v3 Promoted Runs

Run root:

- `/tmp/clearance-comms-2026-04-30-ingest-v3`

Promotions:

- `promote_2026-05-01T10-20-45Z_e8df`: CAP 413 §§2.82-2.91 split replacement.
  - Pipeline: 3 sections, 32 candidates judged.
  - Promoter: 12 accepted landed, 20 pending, 0 conflicts, 0 parse failures.
- `promote_2026-05-01T10-49-29Z_6dc4`: SERA clearance/comms binding windows.
  - Pipeline: 5 sections, 36 candidates judged.
  - Promoter: 30 accepted landed, 6 pending, 0 conflicts, 0 parse failures.

Post-promotion registry audit:

- `audit_registry_reproducibility.py`: 327 records audited, 0 unexpected files,
  0 mismatches, status `pass`.

## Pending Records

CAP 413 pending records are mostly advisory-only checklist/example/support
items. They were retained as pending rather than promoted because the judge did
not classify them as accepted requirements.

SERA pending records are higher priority:

- 3 quote-audit or authority/modality failures where the judge accepted the
  candidate but the promoter correctly withheld it.
- 3 bundle/scope cases where the candidate is not standalone enough for direct
  promotion.

These pending records should be curated before claiming the SERA part of the
seam is closed.

## Still Manifest-Only

The following recommended first-batch windows have exact source ranges in the
document manifests but are not yet landed in the registry:

- ICAO Doc 4444 §4.3.1.
- ICAO Doc 4444 §§4.3.3-4.3.5.
- ICAO Doc 4444 §§4.5.1-4.5.7.4.
- ICAO Doc 4444 §§4.11.1-4.11.3.
- ICAO Doc 4444 §4.14.
- ICAO Doc 4444 §12.3.1.4.
- ICAO Doc 4444 §12.3.2.1.
- ICAO Doc 9432 §2.8.2.
- CAP 413 §§2.54-2.55.
- CAP 413 §2.56.
- CAP 413 §§2.57-2.59.
- CAP 413 §§2.60-2.61.
- CAP 413 §§2.62-2.63.
- CAP 413 §2.64.
- CAP 413 §§2.65-2.67.
- CAP 413 §§2.72-2.75.

## Review Considerations

FP / type safety: no Kotlin/domain code changed. The status change is registry
data and quality artifacts only.

Test architecture: the promotion gate and reproducibility audit passed after
each promoted subset. Pending records are retained visibly and should either be
curated or left pending with rationale.

Impact: downstream consumers may use the landed SERA and CAP 413 §§2.82-2.91
records, but must not treat the whole 24-window clearance/comms batch as
complete.

Operational correctness: promoted source windows are tied to SERA.8015(f),
SERA.8020, SERA.8025, SERA.8030, SERA.8035, and CAP 413 §§2.82-2.91. The
ICAO Doc 4444 / ICAO Doc 9432 / remaining CAP 413 guidance windows are not yet
registry facts.
