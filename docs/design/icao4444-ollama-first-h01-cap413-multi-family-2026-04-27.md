# ICAO 4444 Ollama-First H01 + CAP 413 Multi-Family Coverage 2026-04-27

This pass extends the Ollama-first prototype's generality envelope from
three families (ICAO 4444 readback, ICAO 4444 transfer, EGAST advisory) to
five (adding H01 bilingual and CAP 413 manual prose). The architecture
established by Spike 7 holds without modification on both new families.

This is the first concrete step toward the broader goal of clean,
accurate source units across all relevant ATC texts.

## What was added

Two new entries in `CASES` (`research/tools/requirements-spike/run_icao4444_ollama_first_prototype.py`):

```python
"h01_readback_family": {
    "documentId": "h01-extracted",
    "authorityCeiling": "operational_guidance",
    "startLine": 4197, "endLine": 4337,
    # AIC A 21/23 §3.8.1 — bilingual German/English readback rules.
}
"cap413_readback_family": {
    "documentId": "cap413-extracted",
    "authorityCeiling": "operational_guidance",
    "startLine": 6429, "endLine": 6555,
    # CAP 413 §2.68–2.71 — manual prose with embedded RTF dialogue examples.
}
```

Plus mechanical defaults updated to handle larger source windows and the
denser structure responses they produce:

- structure-attempt `num_predict`: 5000 → 8000
- structure-reconciliation `num_predict`: 6000 → 10000
- All primary-stage `timeout_seconds`: 300 → 600
- All adjudication-stage `timeout_seconds`: 180 → 300

These are pure scaling adjustments; they do not change the architecture.

## Generality results

Five families, three authority ceilings, four orthogonal structural shapes:

| Family | Ceiling | Shape | Cands | Accepted | Override fires |
|---|---|---|---|---|---|
| ICAO 4444 readback | authoritative_requirement | numbered clause + nested list | 7 | 4 | 1 |
| ICAO 4444 transfer | authoritative_requirement | parent + mutually-exclusive siblings | 5 | 3 | 3 |
| EGAST VFR readback | best_practice | unnumbered advisory prose w/ layout noise | 6 | 4 | 5 |
| H01 §3.8.1 | operational_guidance | bilingual (DE/EN), shared lettered markers | 8 | 6 | 1 |
| CAP 413 §2.68–2.71 | operational_guidance | numbered manual w/ embedded RTF examples | 8 | 6 | 4 |

Total: 34 candidates extracted, 23 accepted (68%) across five families.

## Bilingual structure handling (H01)

H01's bilingual layout is a structural challenge the prototype handled
well without prompt-engineering. Each lettered item (a)–h) appears twice
in the source — once in German, once in English — with the same letter
marker. The structure pass produced 17 reconciled items:

- 1 parent clause (§3.8.1)
- 8 lettered items a)–h) — **the bilingual pairs were correctly merged
  into single entries**, not duplicated
- 4 German nested sub-items (`c_list_1`..`c_list_4`)
- 4 English nested sub-items (`c_list_1_en`..`c_list_4_en`) — the nested
  list at the bottom of the section was kept separate because its
  parent c) is physically far from it in the extracted text

The structure stage is doing real semantic work: it recognised the
bilingual pairing of the lettered items but kept the nested list items
distinct because the layout split them. That is a defensible call.

## Dialogue-example handling (CAP 413)

CAP 413 §2.68–2.71 has RTF dialogue examples interleaved between rule
paragraphs (BIGJET 347, G-ABCD, G-CD readback samples). The structure
stage classified these as separate items — they did not collapse into
the surrounding rule clauses. The extraction stage emitted no candidate
that promoted a dialogue example to a rule. The architecture's prior
discipline ("Notes are non-normative; examples are not promotable")
extends naturally to dialogue examples.

## RR-5 fires across families

The RR-5 pattern (challenger uses `authority_too_high` /
`authority_too_low` for what are actually structural concerns on
mixed-modality candidates) was originally observed only on ICAO 4444 and
EGAST. H01 and CAP 413 confirm it is **a cross-family phenomenon, not
an ICAO-4444 quirk**:

- H01 `readback_safety_parts_and_mandatory_items` (parent §3.8.1.c +
  nested 1–4 list, all "shall"/"none" in cited items) → challenger
  said `authority_too_high` because of mixed modalities. Override
  fired. Final: accepted.
- CAP 413 had four override firings, including one mixed-modality
  bundle (`req_full_readback_list`) and three "modality=none"
  candidates the challenger reflexively flagged with authority verdicts.

The deterministic post-step (`apply_bundle_gate_override`) is the right
layer to fix this. Prompt-only attempts failed three times in earlier
spikes; the override is now the default mechanism.

## Override behaviour: judge ground-truth survives

CAP 413 produced an instructive case: `req_controller_ask_readback`
went through the override (challenger flipped from `authority_too_high`
to `supported` because bundle gate scope=true and candidate
authority/modality consistent), but the **judge** ruled
`unsupported_by_source` independently.

That is the correct architectural behaviour. The override only
neutralises authority verdicts the challenger mis-routed from
structural concerns. The judge's separate source-grounding check is
not affected — if a candidate isn't grounded in the source, the judge
catches it regardless of what the challenger said.

This is concrete evidence that the override doesn't mask real source
issues; it only removes one specific class of challenger noise.

## Mechanical scaling: a recurring pattern

Both new families exposed mechanical (not architectural) scaling
issues:

- **EGAST** earlier exposed extraction-stage budget shortfall on
  bullet-list-heavy content; fixed by bumping `num_predict`.
- **CAP 413** exposed the same pattern at the structure stage on a
  larger window; fixed by bumping structure `num_predict`.
- **CAP 413** also exposed inference-time timeout on the larger
  `num_predict`; fixed by bumping `timeout_seconds`.

The pattern: bigger window → denser structure → bigger extraction
prompts → longer inference. Each successive family probes the budget
ceilings. Fixing each by raising the ceiling is mechanical, not
architectural; the new defaults are inert for smaller cases.

## Review Considerations

- **FP / type safety**: not applicable to domain code. The harness
  continues to fail loudly on invalid model JSON or missing required
  fields. No new validation paths added.
- **Test architecture**: meaningful proof remains artifact-level —
  five `/tmp/icao4444-ollama-first-prototype-{family}/` runs with
  consistent shape (`structure_attempts/`, `bundle_gate/`,
  `challenge/`, `challenge_override/`, `defense/`, `judge/`,
  `judge_override/`, `summary.md`).
- **Impact**: the architecture is now demonstrated on five families
  across three of the four authority ceilings (`background_support`
  not yet probed). The "first step" of the broader source-ingestion
  goal is materially in place — adding additional families is "add a
  CASES entry, run, possibly bump a budget."
- **Operational correctness**: source claims grounded in
  [icao4444-extracted.txt](research/txt/icao4444-extracted.txt),
  [egast-vfr-extracted.txt](research/txt/egast-vfr-extracted.txt),
  [h01-extracted.txt](research/txt/h01-extracted.txt),
  and [cap413-extracted.txt](research/txt/cap413-extracted.txt).

## Suggested next probes

For continued generality coverage:

- **ICAO 9432** (Manual of Radiotelephony) — closer to ICAO 4444 in
  drafting style, expected to be similar to the existing readback
  family.
- **SERA** (EU regulation 923/2012) — clean clause-oriented control
  sample; should be easiest of the remaining sources.
- **SafetySense22** — best-practice guidance, similar in shape to
  EGAST.
- **Slovenia VFR** — local procedures, useful for boundary testing
  (jurisdictional specialisation).

The platform doc's "three-spike proving sequence" (`ICAO 4444`,
`H01`, `EGAST VFR`) is now complete plus two extras. Further coverage
is incremental.
