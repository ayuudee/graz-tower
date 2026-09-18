# fn-62-icao-9432-fn43-gap-1-essential.1 Build §4.10 movement manifest and impact plan

## Description
Create an exact FN43-GAP-1 movement manifest before implementation. Identify which essential-aerodrome-information source units can move in this epic, which remain model/policy/phraseology blocked, and the evidence primitives needed to distinguish controller event, transmitted information, pilot-known information, and timing.
## Acceptance
Movement manifest artifact exists under research/tools/requirements-spike/quality/icao9432_programme/.
Covers all §4.10 FN43-GAP-1 rows.
Names green-targeted, remain-blocked, untouched, and anti-overcoverage guards.
Includes pre-implementation impact assessment.
Review considerations cover FP/type safety, test architecture, impact, and operational correctness.
## Done summary
Built fn62_essential_aerodrome_information_manifest.md. It targets only the §4.10 category/definition rows for typed essential-information evidence and leaves timing, omission, open pertinence, and phraseology blocked.
## Evidence
- Commits:
- Tests:
- PRs: