# RR-13 Adequacy Assessment (Pre-Repair)

Date: 2026-04-29

Reviewer: Codex same-agent first pass. This is less independent than an
external audit, but it uses the frozen fixed-seed sample and records the
verdicts directly in the review CSVs.

## Verdict

At the time of this review, the registry was traceable but not yet fit
as a complete actionable translation of the ingested source corpus.

The sampled blockers were later repaired directly in the structured
registry output. See `repair_summary.md`.

## Sample Results

Record frame: 48 sampled records from a 243-record population.

- Correct: 35/48
- Major bad quote support: 6/48
- Wrong lifecycle / under-promoted source-supported fact: 6/48
- Minor metadata issue: 1/48

Major-or-wrong record issue rate: 12/48 = 25.0%.
Wilson 95% interval: about 14.9% to 38.8%.

Any record issue rate: 13/48 = 27.1%.
Wilson 95% interval: about 16.6% to 41.0%.

Section frame: 12 sampled source windows from a 22-section population.

- No material omission: 9/12
- Material omission: 3/12
- Estimated missing actionable requirements in sampled windows: 19

Material-omission section rate: 3/12 = 25.0%.
Wilson 95% interval: about 8.9% to 53.2%.

## Main Failure Modes

1. Verbatim quote is not enough.

G1 proves each quoted string appears in the source, but it does not prove
that the quote set supports the claim. Several accepted records cite
only a list item or table row while the claim inherits obligation from a
parent clause. Sampled examples include ICAO Doc 4444 §§7.9.2, 7.11.6,
and 5.8.2.1.1; SERA.8015(e); UK CAA SafetySense22 Readbacks; and the
Slovenia VFR readback guide.

2. Quote-shape failures cause real under-promotion.

Some source-supported facts are rejected because exact quotes contain
typographic, line-break, ellipsis, or punctuation drift. That is honest
for the malformed record, but incomplete for downstream consumers when
no corrected accepted replacement exists. Sampled examples include ICAO
Doc 4444 §7.9.1, ICAO Doc 4444 §5.8, SERA.8015(d), and Slovenia VFR
readback guidance.

3. One section window is contaminated.

`icao9432-extracted/taxi_4_4_en` includes adjacent Polish/pushback
material under an English taxi section, and accepted records from that
contamination sit under the taxi section.

## Files

- `record_review.csv` contains per-record verdicts and notes.
- `section_omission_review.csv` contains section omission verdicts.
- `sample_manifest.json` records the sample seed and sampled IDs.
