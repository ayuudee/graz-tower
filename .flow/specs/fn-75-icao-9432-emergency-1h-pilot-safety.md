# fn-75-icao-9432-emergency-1h-pilot-safety ICAO 9432 EMERGENCY-1H pilot safety-doubt assistance trigger

## Overview

Close the final non-phraseology, non-Annex-10 policy residual in ICAO 9432
chunk 08: §9.1.7's pilot-side safety-doubt assistance trigger.

The target source unit is:

- `icao9432-extracted::distress_urgency_intro_9_1_en::87b67820c6092a98`
  — pilots should seek assistance whenever flight safety is in doubt.

The intended implementation is a closed local source-unit projection proving a
typed safety-doubt condition can produce a seek-assistance action under explicit
operational policy. This epic must not claim production emergency decision
making, rendered emergency phraseology, Annex 10 conformance, or a complete
catalogue of every possible cause of safety doubt.

## Scope

In scope:

- Exact one-row movement manifest for `87b67820c6092a98`.
- Source-backed evidence that:
  - a pilot-observed safety-doubt condition is distinct from routine concern,
    passenger convenience, generic urgency classification, and ATC instruction;
  - assistance seeking is available only under explicit policy that says the
    doubt warrants assistance;
  - the assistance request carries the reason/source witness, without claiming
    rendered wording.
- Wrong-path guards preventing phraseology-only, generic emergency labels,
  routine operational preference, and no-policy cases from satisfying the row.
- Ledger updates moving only this row from `model-gap + policy-blocked` to a
  covered configured branch.

Out of scope:

- Annex 10 emergency procedure conformance rows.
- Rendered emergency speech-rate, adaptation, repeated MAYDAY/PAN PAN, and
  distress-message order rows.
- Production pilot decision engine changes.
- Complete modelling of all safety-doubt causes.

## Approach

1. Build `fn75_pilot_safety_doubt_manifest.md` listing the single source-unit
   movement and residual non-targets.
2. Run plan review and impact review before implementation.
3. Implement a local source-backed test, likely
   `Icao9432PilotSafetyDoubtSourceBackedTest`, with closed projection types:
   `PilotCondition`, `SafetyDoubtPolicy`, `AssistanceAction`, and typed
   accepted/rejected transitions.
4. Add source-unit evidence for the positive path and negative guards:
   - positive: safety doubt + seek-assistance policy -> assistance request;
   - negative: routine preference, generic urgency/distress label without a
     safety-doubt witness, passenger convenience, ATC instruction, no-policy,
     and phraseology-only evidence.
5. Remove `87b67820c6092a98` from the expected-gap grouping and add it to
   covered/split accounting.
6. Update `source_plan.md`, `expected_gaps.md`, `coverage_report.md`, and
   `implementation_blocker_manifest.csv`.
7. Run principal self-assessment, completion review, focused tests, detekt,
   broad validation, Flow validation, and diff checks before commit.

## Quick commands

- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432PilotSafetyDoubtSourceBackedTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
- `./gradlew-nix detekt`
- `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`
- `.flow/bin/flowctl validate --epic fn-75-icao-9432-emergency-1h-pilot-safety`
- `git diff --check`

## Acceptance

- [ ] Exact one-row movement manifest exists before implementation.
- [ ] Plan review and impact review are complete before implementation.
- [ ] Source-backed evidence directly covers `87b67820c6092a98` without
  over-claiming production emergency decision making or rendered phraseology.
- [ ] Assistance seeking is gated by a typed safety-doubt witness and explicit
  policy, not by a generic emergency label alone.
- [ ] Wrong-path guards reject routine preference, passenger convenience,
  phraseology-only evidence, no-policy cases, and ATC-originated prompts.
- [ ] Chunk 08 exact-union accounting remains at 46 refs.
- [ ] Remaining standalone chunk-08 residual buckets show no
  `model-gap + policy-blocked` row; Annex 10 and phraseology/order rows remain
  executable gaps, while previously split covered rows may still document
  residual policy/any-means facets.
- [ ] Review considerations below are satisfied.

## Review Considerations

FP / type safety:

- Use closed local projection types. All `when` expressions must be exhaustive.
- Type-valid unsupported paths return typed rejection, not `error()`, null, or
  silent no-op.
- If reset/recovery state is introduced, it must clear every derived field.

Test architecture:

- Prove the source-unit trigger and policy gate, not just that emergency
  classifications exist.
- Keep this as a source-mapped projection test. Do not reuse ordinary VFR traces
  or rendered phraseology as emergency-compliance evidence.
- Update exact-union accounting; no skip lists or uncited residual movement.

Impact:

- Production changes are not expected. If impact review identifies a production
  change, implement only with a fresh type/interaction audit.
- This should reduce the residual ledger by exactly one row.

Operational correctness:

- Claim only ICAO Doc 9432 §9.1.7: pilot seeks assistance when flight safety is
  in doubt.
- Do not claim Annex 10 procedure conformance, emergency message wording,
  speech quality, or a complete operational taxonomy of safety-doubt causes.

## References

- `research/txt/icao9432-extracted.txt` §9.1.7, around line 8717.
- `research/tools/requirements-spike/quality/icao9432_programme/chunk_08_distress_urgency_comms_failure/source_plan.md`
