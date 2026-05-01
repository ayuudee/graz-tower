# Clearance And Communications Contract Seam

Date: 2026-04-30

## Intent

The next seam is not "more documents" and not "finish ICAO Doc 4444 Chapter 7".
It is the foundational contract between source law/phraseology and downstream
tower behaviour: what a clearance or instruction means, what the pilot must do
with it, how acknowledgement/readback/correction works, how control and
communications transfer, when reports are required, and what happens when
communications fail.

The goal is to make later runway, taxi, circuit, arrival, departure, and
emergency facts land on stable semantics rather than each operational batch
re-discovering clearance/readback/comms rules ad hoc.

## Why This Seam First

The promoted registry is reliable only for the landed 22-window slice. The
live manifests now include the 22 clearance/communications additions selected
by this plan, but those additions are not accepted coverage until they pass the
normal ingest, promote, curate, audit, snapshot, and adequacy-review flow.
A source-shaped expansion, such as completing ICAO Doc 4444 Chapter 7 first,
would improve table coverage while leaving cross-source contract gaps in
place. That creates false confidence: runway and aerodrome-control facts
depend on clearance validity, readback obligations, controller correction
duties, frequency transfer, position reporting, and communications-failure
handling.

This seam is the smallest product-shaped widening that raises confidence across
later batches.

## Batch Scope

Primary legal/procedural anchors:

- SERA.8015, SERA.8020, SERA.8025, SERA.8030, SERA.8035.
- ICAO Doc 4444 sections 4.3, 4.5, 4.11, 4.14, and phraseology support from
  12.1-12.3 only where needed.

Phraseology and operational guidance:

- ICAO Doc 9432 section 2.8, especially 2.8.2 alongside the existing 2.8.1 and
  2.8.3 anchors.
- CAP 413 Chapter 2 rows for acknowledgement, corrections/repetitions, transfer
  of communications, complying with clearances, and communication failure.
- AIC A 21/23 H01 sections 3.3, 3.8, 3.9, and 3.10.

Not in this batch:

- Full aerodrome/tower chapter completion.
- All phraseology examples.
- Radar/surveillance, area control, CPDLC, military, or administrative material.
- Nolan textbook promotion into requirements.

## Execution Plan

1. Harden source windows before ingestion. For each selected ledger row, record
   exact start/end lines, language boundary, source role, and why it belongs in
   this seam.
2. Add only those hardened windows to `documents/*.json`. Keep the batch around
   10-15 windows.
3. Run the standard ingest, promote, curate, audit, and snapshot flow.
4. Repair obvious quote-support, split/bundle, authority, or omission defects
   directly; do not leave known-bad records silently accepted.
5. Run a targeted adequacy review over all authoritative windows and a sample
   of guidance/phraseology windows, including source windows that produced no
   accepted output.
6. Update `DECLARED_SLICE.md`, the section ledger, registry status, and `.plan`
   only after the audit passes.

## Done Criteria

- No pending records.
- Reproducibility audit passes.
- Accepted quote audit passes.
- Regression snapshot is refreshed.
- Every selected source window has either accepted structured facts or an
  explicit no-action/disposition reason.
- Adequacy review finds no material omissions in authoritative windows.

## Review Considerations

FP / type safety: no domain-code change is required for this plan. If schema
changes become necessary, source role and authority precedence must be explicit
fields or deterministic policy, not inferred from prose.

Test architecture: use the existing quality gates, reproducibility audit,
regression snapshot, and a targeted omission review. The review must cover both
accepted records and selected source windows with no accepted output.

Impact: this seam raises confidence for all later operational batches because
it normalizes the shared clearance/readback/comms contract before expanding
runway or aerodrome-specific rules.

Operational correctness: source precedence is explicit. Binding/legal and ICAO
procedural material from SERA and ICAO Doc 4444 anchors the contract; ICAO Doc
9432, CAP 413, and AIC A 21/23 H01 provide phraseology and operational guidance
without overriding higher-authority sources.
