# fn-70 Review Resolution

Epic: `fn-70-icao-9432-emergency-1c-emergency`

## Impact Review

Reviewer: `01a0b4f9-d4d1-7933-b1a2-81ad58b970b1`

Findings resolved before implementation:

- `082f9668292ed82c` can move only as structured projection coverage, not
  production safeguarding behavior. Docs and tests keep that boundary explicit.
- `c71568b00fb1535e` cannot move wholesale. Implemented only a split general
  warning projection branch; specific-instruction necessity remains
  `OperationalGuidancePolicy`.
- `ca0c243491ff5d13` needed explicit position uncertainty. Implemented a typed
  position-uncertainty branch before moving it.
- Reset must clear all derived state. Implemented and tested clearing emergency
  aircraft, affected traffic, safeguard actions, and position-question state.
- fn70 must not couple to fn69 radio discipline or fn71 communications failure.
  The test input is a distinct `EmergencyDescentAnnouncement`.

## Completion Review

Reviewer: `01a0b4fe-302c-7743-99bb-dc43a301c203`

Findings resolved:

- **Reset did not prove `positionQuestion` reversal.** Updated the reset fixture
  to start from uncertain position, assert the question exists before
  resolution, and then assert it is cleared after resolution.
- **`source_plan.md` stale wording.** Reworded the boundary to say production
  emergency-descent conflict resolution remains out of scope; structured
  projection safeguarding is now covered.
- **`ca0c243491ff5d13` wording too deterministic.** Reworded docs and test
  witness language from "produces" to "supports" a position-question branch,
  matching the advisory "might be asked" source language.

## Boundary Kept

fn-70 remains structured projection coverage only. It does not claim production
controller conflict resolution, production aircraft kinematics, rendered
phraseology, communications failure, blind transmission, SSR, urgency payload/
addressing policy, or urgency interference suppression policy.

## Post-Resolution Validation

- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432EmergencyDescentSourceBackedTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
