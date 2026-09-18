# fn-62-icao-9432-fn43-gap-1-essential.2 Implement minimal essential-information evidence

## Description
Implement the smallest typed evidence surface and source-mapped tests selected by the fn-62 movement manifest. Keep policy-sensitive and category-wide claims blocked unless explicit evidence and policy binding exist.
## Acceptance
Evidence distinguishes controller event, transmitted information, pilot-known information, and timing where claimed.
Source-mapped tests are high-level scenario/evidence tests.
No policy-sensitive unit moves green without explicit policy binding.
Remaining §4.10 model/category/phraseology gaps still fail loudly as expected gaps.
## Done summary
Implemented typed essential-aerodrome-information category/domain evidence and source-mapped tests for the eight §4.10 category/definition rows. Timing, omission, open pertinence, and phraseology rows remain blocked.
## Evidence
- Commits:
- Tests: ./gradlew-nix :sim:jvmTest --tests '*.EvidenceDslTest' --tests '*.Icao9432EssentialAerodromeInformationEvidenceTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest', ./gradlew-nix detekt, .flow/bin/flowctl validate --epic fn-62-icao-9432-fn43-gap-1-essential, git diff --check
- PRs: