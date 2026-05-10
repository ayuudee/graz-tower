# Red-Team Review: Nolan / EPPLS Gap Plan

Generated: `2026-05-09`

## Findings And Resolutions

| ID | Finding | Severity | Resolution in plan |
| --- | --- | --- | --- |
| RT-1 | EPPLS was assumed present by prior discussion, but no EPPLS-named PDF/text extract exists in `research/pdf/` or `research/txt/`. | High | EPPLS is marked `intake_blocked` until file identity, edition, checksum, and text extract are verified. |
| RT-2 | Nolan is a textbook. Treating it like ICAO/SERA/CAP would overstate legal authority. | High | Nolan default ceiling is `background_support`; `authoritative_requirement` is forbidden unless tied to a primary cited regulation. |
| RT-3 | A single LLM relevance pass can miss sections or invent themes. | Medium | The plan requires deterministic ToC inventory plus bounded LLM ToC review, with disagreements carried as review rows. |
| RT-4 | PDF extraction/page layout can corrupt line windows and quote provenance. | Medium | Intake requires extraction command, text path, line-window manifesting, and post-promotion quote audit. |
| RT-5 | Ollama is the bottleneck; parallel source planning could race registry state or curation provenance. | Medium | The plan allows parallel leg-work but processes one source queue at a time and requires immutable batch/run roots. |
| RT-6 | EPPLS pilot-side phraseology may conflict with controller-side CAP/ICAO/SERA records. | Medium | Packages remain per-source; integration and precedence are explicitly out of scope. |
| RT-7 | The process could drift into another open-ended theme hunt. | Medium | Exit criteria are source-specific and staged: intake, inventory, manifest, processing, curation, validation, package. |

## Remaining Blockers

- EPPLS cannot be planned beyond intake until the file is present in the repo or
  a text extract exists.
- Nolan can be inventoried, but its package must be scoped as conceptual support
  unless a later review identifies exact primary-source citations in the text.

## Red-Team Verdict

The plan is fit to start with Nolan intake/inventory now and EPPLS intake only
after the file is present. It resolves the major failure modes from the current
pipeline: assumed source presence, authority overpromotion, invented themes,
line-window drift, and premature integration.
