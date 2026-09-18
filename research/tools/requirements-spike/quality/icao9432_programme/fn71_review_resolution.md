# fn-71 Review Resolution

Independent completion review: `01a0b54e-ba94-7682-a938-f79414423fa5`.

## Findings Resolved

1. Failed-contact blind transmission was accepted too early.
   - Fixed by requiring the ordered failed-contact attempt sequence before
     `enterFailedContactBlindTransmission` can accept:
     designated frequency, route-appropriate alternate frequency, other
     aircraft, then other station.
   - The blind-transmission witness now follows that sequence before claiming
     the structured repetition/addressee branch.

2. Contact-attempt ordering was not enforced.
   - Fixed by mode-gating and sequence-gating `tryAlternateFrequency`,
     `tryOtherAircraft`, and `tryOtherStation`.
   - Receiver-failure states now reject failed-contact routing attempts.
   - Reset coverage was split into failed-contact and receiver-failure reset
     paths rather than constructing an impossible mixed flow.

3. `4b37e039e7eb8afa` residual assistance/any-means blocker was documented but
   not executable.
   - Fixed by placing the source unit in the assistance/relay model-gap group
     while keeping the SSR 7700 branch covered by
     `Icao9432CommunicationsFailureSourceBackedTest`.
   - The model-gap text was narrowed so it no longer says SSR 7700 is missing.

4. Generic emergency negative guard only covered PAN PAN.
   - Fixed by adding a MAYDAY negative guard for generic emergency without
     communications failure.

## Verification After Fixes

- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432CommunicationsFailureSourceBackedTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
- `./gradlew-nix detekt`

## Emergency-Group Adversarial Review

Independent adversarial review: `01a0b553-acf0-75e0-9209-a282b5ce7159`.

Additional fixes:

- Added executable residual gap coverage for `c71568b00fb1535e` so the
  emergency-descent specific-instruction necessity policy remains loud.
- Added executable residual gap coverage for blind-transmission phraseology
  split rows `bc9bb12804033b07`, `abbc376a430003b0`, and `78c73a75fab644f4`.
- Added the missing fn-70 reset assertion that emergency-descent recovery
  clears `positionQuestion`.
