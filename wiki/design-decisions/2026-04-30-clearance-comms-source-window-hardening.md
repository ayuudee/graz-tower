# Clearance And Communications Source Window Hardening

Date: 2026-04-30

## Decision

Before widening the requirements registry, harden the clearance and
communications seam as exact source windows rather than as broad document or
chapter targets.

Artifact:

- `research/tools/requirements-spike/quality/source_window_hardening/clearance_comms_2026-04-30/clearance_comms_window_hardening.md`

## Result

The first manifest-widening batch is bounded to 24 recommended additions across
SERA, ICAO Doc 4444, ICAO Doc 9432, and CAP 413. It began as 16 additions, but
the combined CAP 413 §§2.54-2.67 window produced 21 candidates during live
ingestion while the pipeline reviews at most 20. The first transfer split
still produced invalid truncated JSON, so CAP 413 transfer material was split
again into smaller windows. CAP 413 §§2.82-2.91 was also split into compliance
timing, air-ground communication failure, and ground-air communication failure
after live ingestion showed the combined window was too heavy and mixed two
procedural topics. The batch deliberately reuses
the existing declared-slice anchors for SERA.8015(e), ICAO Doc 4444 §4.5.7.5,
ICAO Doc 9432 §§2.8.1 and 2.8.3, CAP 413 §§2.68-2.71, and H01 §3.8.

H01 §§3.3, 3.9, and 3.10 remain explicit follow-up candidates rather than first
batch manifest additions. H01 §3.9 has a real boundary issue: the current H01
§3.8.3 manifest window already overlaps the §3.9 heading and early content.

## Follow-up Status

On 2026-05-01, the 2026-04-30 v2 ingestion run was marked quarantined after the
old combined CAP 413 §§2.82-2.91 window timed out and subsequent SERA / ICAO
Doc 4444 / ICAO Doc 9432 calls failed with Ollama connection timeouts.

Clean v3 promotion succeeded for the high-value subset:

- SERA.8015(f), SERA.8020, SERA.8025, SERA.8030, and SERA.8035: 30 accepted
  records landed, 6 records retained pending.
- CAP 413 §§2.82-2.87, §2.88, and §§2.89-2.91: 12 accepted records landed,
  20 records retained pending.

The registry reproducibility audit passed after promotion. The rest of the
24-window batch remains exact-manifested but not yet landed.

## Review Considerations

FP / type safety: no Kotlin/domain code changed.

Test architecture: the next implementation step should use the existing
ingestion/promotion gates and add targeted omission review for every selected
authoritative source window.

Impact: the registry widening now has a reviewable source contract and an
explicit set of deferrals. This reduces false confidence from saying the whole
corpus or whole chapters have been ingested.

Operational correctness: the chosen windows are anchored to SERA.8015(f),
SERA.8020, SERA.8025, SERA.8030, SERA.8035; ICAO Doc 4444 §§4.3, 4.5, 4.11,
4.14, 12.3.1.4, and 12.3.2.1; ICAO Doc 9432 §2.8.2; and CAP 413 §§2.54-2.75
and §§2.82-2.91.
