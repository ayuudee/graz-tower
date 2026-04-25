# ICAO 4444 Ollama-First Architecture Generalises To EGAST 2026-04-25

This pass tests whether the four-stage adjudication architecture established
for `ICAO 4444` (challenger → defender → bundle gate → judge) holds up on a
structurally very different source family.

## Slice

A new case `egast_readback_family` was added to
`run_icao4444_ollama_first_prototype.py`:

- `documentId` — `egast-vfr-extracted`
- `familyId` — `egast_vfr_readback_family`
- `authorityCeiling` — `best_practice` (instead of `authoritative_requirement`)
- source — `research/txt/egast-vfr-extracted.txt:539-608`
- structure — unnumbered advisory prose with inline page-layout noise, orphan
  bullet markers, section sidebars, and section headings interleaved mid-list.

The readback advisory family covers:

- a "Messages containing the following must be read back:" bullet list
  spanning page boundaries
- the "Acknowledgement by Callsign" rule
- "Items to be Read back" heading
- "Wilco" usage rule
- when a read-back is required and how to perform it
- background rationale for why read-back matters

A small mechanical change to support per-case source paths: the case dict now
carries an optional `sourceOverride`, and `main()` resolves it when the CLI
flag is left at default.

## Token Budget

EGAST exposed a budget shortfall in the extraction stages. On readback runs
the model sometimes produced a single "umbrella" candidate concatenating all
11 readback items into one claim, which truncated the response mid-JSON. The
script correctly failed loudly rather than accept a partial structure, but to
get a complete run we needed:

- `num_predict` for extraction attempts: 3500 → 8000
- `num_predict` for extraction reconciliation: 3500 → 8000
- default `num_ctx`: 12288 → 16384

These are now defaults. They don't penalise `ICAO 4444` runs because the
budget is only spent when the response actually grows.

## Result

After three runs (two early ones killed by the budget shortfall before the
bumps landed), the architecture produced a coherent run on EGAST:

- 6 candidates, 3 accepted, 3 advisory_only
- structure stage: 14 reconciled items
- all candidates classified at `best_practice` or `background_support` —
  family ceiling held
- bundle gate returned `scopeComplete=true` for all six candidates (sensible
  for largely independent advisory items)

Detailed table:

| Candidate | Authority class | Promote hint | Bundle gate | Challenger verdict | Final |
|---|---|---|---|---|---|
| `req_readback_items` | best_practice | promote | scope=true | authority_too_high | **accepted** |
| `req_ack_callsign` | best_practice | promote | scope=true | supported | **accepted** |
| `req_readback_completeness` | best_practice | promote | scope=true | authority_too_high | **accepted** |
| `req_wilco_usage` | best_practice | promote | scope=true | authority_too_high | advisory_only |
| `req_request_clarification` | best_practice | promote | scope=true | authority_too_high | advisory_only |
| `supp_readback_benefits` | background_support | support_only | scope=true | authority_too_low | advisory_only |

## Generality Findings

**The architecture generalises.** Concretely:

- the structure pass produced reconcilable structure even from layout-noisy
  unnumbered prose;
- the bundle gate produced sensible scope answers on a flat advisory
  hierarchy (scope=true everywhere is the right answer when items are
  independent);
- the family-level authority ceiling (`best_practice`) was respected by all
  six candidates and the judge — no advisory item was promoted to
  `operational_guidance` or `authoritative_requirement`.

This is the most important generality test the platform document predicted —
that the architecture should not collapse on best-practice prose. It did not.

## RR-5 Hits Harder On Advisory Source Families

The known challenger bug — labelling structural concerns as
`authority_too_high` for mixed-modality candidates — hits EGAST harder than
it hit `ICAO 4444`.

On `ICAO 4444`, the judge filters RR-5 noise cleanly because most good
candidates have `shall` anchors. The judge sees the `shall` and overrides
the bad challenger verdict.

On EGAST, the judge cannot lean on `shall` because the family ceiling is
`best_practice` and the source uses `should`, `must` in best-practice
context, and unmarked imperatives. Two of five promotable candidates
(`req_wilco_usage`, `req_request_clarification`) were demoted from
`accepted` to `advisory_only` because the judge took the bad
`authority_too_high` verdict at face value.

`.plan` upgrades RR-5 impact from L to M as a result. The bug now damages
registry-surface outcomes on advisory source families, not just the
challenger surface. The likely fixes remain the same:

- forbid `authority_too_high` when the bundle gate has already reported
  `scopeComplete=true` on the same items;
- add a verdict-classifier post-step that re-routes mis-typed challenger
  concerns into the structural verdict slots
  (`wrong_split` / `overbroad` / `underspecified`).

## Review Considerations

- **FP / type safety**: not applicable. The Python harness still fails loudly
  on invalid model JSON or missing required fields. The token-budget
  shortfall surfaced exactly that way — the script refused the partial JSON
  rather than continue with a truncated candidate set.
- **Test architecture**: the meaningful proof is the three-source-family
  artifact comparison
  (`/tmp/icao4444-ollama-first-prototype-{readback,transfer,spike-4-egast-readback}/`).
  No synthetic unit tests were added; the architecture's behaviour on real
  advisory prose is the oracle.
- **Impact**: the four-stage architecture is now demonstrated on at least
  one source family per published authority class
  (`authoritative_requirement`, `best_practice`, with `operational_guidance`
  not yet probed but a much smaller delta from EGAST than from ICAO).
- **Operational correctness**: source claims grounded in
  [egast-vfr-extracted.txt](/home/andrew/dev/projects/twr2/research/txt/egast-vfr-extracted.txt).
