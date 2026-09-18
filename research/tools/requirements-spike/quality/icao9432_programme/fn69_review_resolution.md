# fn-69 Review Resolution

Epic: `fn-69-icao-9432-emergency-1b-emergency`

## Impact Review

Reviewer: `01a0b4ed-eb8a-77a0-876b-e995a82d12a0`

Findings resolved before implementation:

- Frequency discipline for `7d35c042421b5b03` must cover both distress and
  urgency traffic. Implemented explicit distress-active and urgency-active
  branches.
- Priority evidence must be tied to production concepts. Implemented priority
  projection from production `EmergencyType.MAYDAY` / `PAN_PAN`.
- Silence release must require controlling-station advice, not a silent
  world-state flag. Implemented a typed advisory transition and no-advisory
  rejection.
- Silence imposition is permission/capability, not mandatory behavior.
  Implemented permitted-authority positives and non-authority negatives.
- Assistance/relay remains out of scope. Model uses only a closed involvement
  predicate and does not implement relay behavior.

## Completion Review

Reviewer: `01a0b4f3-f59c-7073-9dde-abee1d34f4d9`

Findings resolved:

- **All-aircraft silence silenced the distress aircraft itself.** Fixed
  `SilenceRestriction.AllAircraft` so it suppresses aircraft other than the
  distress aircraft. The test now asserts the distress aircraft may transmit
  while interfering aircraft are silenced.
- **Stale `source_plan.md` narrative.** Updated review position, test-plan
  state, and review considerations so fn-69 priority/silence projection
  coverage is no longer described as future work.
- **Named-silence negative needed clearer boundary.** Added an uninvolved
  non-target aircraft check to show named-aircraft silence does not itself
  suppress every aircraft, while ordinary emergency-frequency restraint still
  blocks uninvolved aircraft until advised termination.

## Boundary Kept

fn-69 remains structured projection coverage only. It does not claim production
radio queue preemption, rendered phraseology, assistance/relay behavior,
emergency descent, communications failure, blind transmission, SSR, or
superfluous/interference suppression policy.

## Post-Resolution Validation

- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432EmergencyPrioritySilenceSourceBackedTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
