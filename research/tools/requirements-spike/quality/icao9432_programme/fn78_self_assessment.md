# fn-78 self-assessment

## Principal checks

- Totality: rendered phraseology support is added through sealed
  `when` branches. Unit-only `ContactFrequency` remains a typed unsupported
  outcome rather than an exception.
- Reversal completeness: no state transitions or reversible simulator state
  mutations are introduced.
- Interaction coverage: the test exercises the evidence DSL over a LOWG
  circuit-training run and asserts both controller and pilot rendered
  phraseology surfaces.
- Test coverage for known features: the new source unit is covered by
  `Icao9432PhraseologyEvidenceTest`; catalog and evidence-fact tests cover
  registry and unsupported-rendering behaviour.
- New-field audit: no simulator state data class fields are added.
- Operational correctness: coverage is limited to ICAO Doc 9432 §2.8.2.1
  `CONTACT [unit] [frequency]` and `[frequency] [callsign]` readback.
- Error handling honesty: representable unsupported renderings use typed
  `UnsupportedRenderedPhraseology` / `UnsupportedRenderedPilotReadbackPhraseology`
  outcomes.
- Deferment honesty: residual PHRASE-1 work remains visible in the chunk-01
  report and fn-78 manifest.

## Validation

- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432PhraseologyEvidenceTest' --tests '*.EvidenceSourceCatalogTest' --tests '*.EvidenceDslTest' --tests '*.EvidenceFactsTest'`
- `./gradlew-nix detekt`
- `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`
