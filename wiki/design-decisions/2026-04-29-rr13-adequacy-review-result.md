# RR-13 Adequacy Review Result

Date: 2026-04-29

## Decision

Treat the current Ollama-first registry as traceable but not yet fit as
a complete actionable translation of the ingested source corpus.

The same-agent first-pass review of
`research/tools/requirements-spike/quality/adequacy/adequacy_2026-04-29-rr13-80-20/`
found:

- 35/48 sampled records clean.
- 6/48 sampled records with major bad quote support.
- 6/48 sampled records under-promoted despite source support, usually
  because quote-shape defects forced rejection and no corrected
  replacement exists.
- 1/48 sampled record with overstated authority/modality metadata.
- 3/12 sampled source windows with material omissions, covering an
  estimated 19 missing actionable facts.

This is enough signal to remediate before exposing the registry as a
complete downstream fact source.

## Findings

The main failure mode is not random hallucination. It is quote/structure
handling:

- Some accepted records cite only a list item or table row while the
  claim inherits the obligation from a parent clause. Examples came from
  ICAO Doc 4444 §§7.9.2, 7.11.6 and 5.8.2.1.1; SERA.8015(e); UK CAA
  SafetySense22 Readbacks; and the Slovenia VFR readback guide.
- Some rejected records are source-supported but were rejected because
  the exact quote carried typographic, line-break, ellipsis, or
  punctuation drift. Examples came from ICAO Doc 4444 §7.9.1,
  ICAO Doc 4444 §5.8, SERA.8015(d), and the Slovenia VFR readback guide.
- One section manifest is contaminated: `icao9432-extracted/taxi_4_4_en`
  includes adjacent Polish/pushback material under an English taxi
  section.

Follow-up is tracked in `.plan` as RR-14, RR-15, and RR-16.

## Repair

The sampled blockers were repaired directly in the registry output on
2026-04-29, without adding a general-purpose remediation pipeline.

- RR-14: accepted records with insufficient quote context were repaired
  by adding parent/list/table quotes and recomputing canonical IDs.
- RR-15: source-supported quote-shape rejects were replaced with
  corrected accepted records; missing EGAST and Slovenia VFR records were
  added from the reviewed source windows.
- RR-16: three Polish/pushback contamination records were rejected from
  the accepted English ICAO 9432 taxi section.

The repaired registry has 237 candidates, 0 pending, and 16 rejected
records across 253 auditable records. The repaired ledger snapshot is
`research/tools/requirements-spike/quality/snapshots/judgements-2026-04-29-post-rr13-repair.csv`.

## Review considerations

- FP / type safety: no Kotlin domain state changed. Python review tooling
  now has a regression test for newline-based source-window line numbers
  so PDF form-feed characters cannot shift reviewed excerpts.
- Test architecture: quality-gate tests cover the source-window line
  numbering bug and the sample-generation contract. New adequacy
  failures need production gates or corpus repair tests, not only review
  notes.
- Impact: downstream consumers should not yet treat the registry as a
  complete audited source, because the repair is still same-agent rather
  than external. The sampled blockers themselves have been repaired.
- Operational correctness: source-backed operational claims must cite
  the relevant source section and include enough parent/list context in
  `exactSourceQuotes` to support the claim's obligation.
