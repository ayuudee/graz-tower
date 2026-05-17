# FN34 EPPLS Chapter 12 contrast artifacts

## Purpose

This directory runs EPPLS Chapter 12 through the same source-unit ledger
workflow as FN33. It was chosen as the second source because it proves a
different facet from ICAO 9432: textbook/training material, standard words,
radio discipline, radar-service definitions, distress/urgency, radio failure,
and explanatory records.

## Artifacts

- `eppls_ch12_ledger.jsonl` - one row per accepted source unit.
- `eppls_ch12_sections.jsonl` - one row per EPPLS Chapter 12 section.
- `eppls_ch12_classification_summary.json` - generated section/status counts.
- `synthesis.md` - contrast findings and recommendation.

## Validation

The ledger was generated mechanically from accepted registry records under:

`research/tools/requirements-spike/registry/ollama_first/candidates/eppls-extracted`

Counts:

```json
{
  "rows": 178,
  "unique": 178,
  "pending": 0,
  "statuses": [
    {
      "status": "blocked_by_model_gap",
      "count": 23
    },
    {
      "status": "duplicate_support",
      "count": 14
    },
    {
      "status": "needs_domain_review",
      "count": 141
    }
  ]
}
```

## Interpretation

EPPLS Chapter 12 is not a good immediate executable-test source. It is useful as
supporting/training evidence and as a pressure test for the workflow's ability
to say "not executable yet" without losing the source unit.

The dominant status is `needs_domain_review` because most records are
review-only or explanatory. The most actionable implementation signal is the
same as FN33's model-gap finding: standard words, callsigns, numbers, time,
radio checks, frequencies, distress/urgency, and radio failure need phraseology
rendering/linting or abnormal-communications scenario modeling before they can
be tested honestly.
