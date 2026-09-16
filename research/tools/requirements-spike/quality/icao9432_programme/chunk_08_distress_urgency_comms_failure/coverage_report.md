# Chunk 08 Coverage Report

Chunk: ICAO 9432 distress, urgency, emergency descent, and communications
failure.

## Summary

| Final state | Units |
|---|---:|
| `covered-green` | 0 |
| `covered-red` | 0 |
| `model-gap` | 22 |
| `model-gap` + `policy-blocked` | 15 |
| `model-gap` + `phraseology-later` | 9 |

Chunk 08 deliberately produces no covered-green rows. The simulator currently
has no emergency condition state, emergency priority/radio-silence arbitration,
emergency assistance/relay actors, emergency descent safeguarding workflow,
communications-failure mode, blind-transmission scheduler, SSR emergency/code
state, or rendered emergency phraseology. Ordinary VFR, go-around, or routine
radio traces are not emergency-compliance evidence.

## Coverage Table

| Source unit | Final state | Test / blocker |
|---|---|---|
| `icao9432-extracted::distress_urgency_intro_9_1_en::8b3b3b4117c04807` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::c959f0325390e7fe` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::3b1079aa56df2ce6` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::1a20cd48e58a5693` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::7d35c042421b5b03` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::9c34a1b8d6d623fa` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::24f94381ed9e8ce1` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `OperationalGuidancePolicy` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::2fee92c222323e6a` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::82ee7048517d8478` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `OperationalGuidancePolicy` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::87b67820c6092a98` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `OperationalGuidancePolicy` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::8e9f7818b91d08c3` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `OperationalGuidancePolicy` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::cb12c2f9b97c64b7` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `OperationalGuidancePolicy` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::06f7a72397c325ac` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `OperationalGuidancePolicy` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::13d1c2accd0f7a73` | `model-gap` + `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `PHRASE-1` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::c30159856a1a5e7a` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::9907744b4723d14c` | `model-gap` + `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `PHRASE-1` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::bf04647e26f9c018` | `model-gap` + `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `PHRASE-1` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::d742970b22d8de26` | `model-gap` + `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `PHRASE-1` |
| `icao9432-extracted::distress_messages_9_2_en::23c9f447cd6c7814` | `model-gap` + `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `PHRASE-1` |
| `icao9432-extracted::distress_messages_9_2_en::f0e99a4c08ea0cb3` | `model-gap` + `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `PHRASE-1` |
| `icao9432-extracted::distress_messages_9_2_en::27a450fa3bfcbc0a` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `OperationalGuidancePolicy` |
| `icao9432-extracted::distress_messages_9_2_en::94e94be0c800c982` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `OperationalGuidancePolicy` |
| `icao9432-extracted::distress_messages_9_2_en::4b37e039e7eb8afa` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::distress_messages_9_2_en::e2902de496f43a95` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::distress_messages_9_2_en::ed898005cd1a4da5` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::distress_messages_9_2_en::c20024dad1b7144e` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::1de475a788206cc8` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `OperationalGuidancePolicy` |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::b95d7bb1cb409bca` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `OperationalGuidancePolicy` |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::5df94af7a64c3f5d` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `OperationalGuidancePolicy` |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::082f9668292ed82c` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::c71568b00fb1535e` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `OperationalGuidancePolicy` |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::ca0c243491ff5d13` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::communications_failure_9_5_en::f05016444e2b8808` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::communications_failure_9_5_en::fcb3a49672165b4f` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::communications_failure_9_5_en::bc9bb12804033b07` | `model-gap` + `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `PHRASE-1` |
| `icao9432-extracted::communications_failure_9_5_en::abbc376a430003b0` | `model-gap` + `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `PHRASE-1` |
| `icao9432-extracted::communications_failure_9_5_en::975a63151706f68f` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::communications_failure_9_5_en::045c2e33f59f5ede` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::communications_failure_9_5_en::7900c606e05e509b` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::communications_failure_9_5_en::78c73a75fab644f4` | `model-gap` + `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `PHRASE-1` |
| `icao9432-extracted::communications_failure_9_5_en::91e7d233bf3b64ff` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::communications_failure_9_5_en::bb66a050093251c2` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `ControllerInterventionPolicy` |
| `icao9432-extracted::communications_failure_9_5_en::24c806b040f4ef5e` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `ControllerInterventionPolicy` |
| `icao9432-extracted::communications_failure_9_5_en::75055714e70d4560` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `ClearanceTimingPolicy` |
| `icao9432-extracted::communications_failure_9_5_en::b73dda299970c2f3` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::communications_failure_9_5_en::c1c14fab53a608c6` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |

## Verification

- Source-unit provenance: all 46 accepted candidate JSON records are present in
  the registry with `lifecycle.state = accepted`. Source text was checked
  against `research/txt/icao9432-extracted.txt` in Chapter 9.
- Focused verification:
  `./gradlew-nix :sim:jvmTest --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`.
- Full verification:
  `./gradlew-nix :sim:jvmTest`;
  `./gradlew-nix detekt`;
  `.flow/bin/flowctl validate --epic fn-58-icao-9432-chunk-08-distress-urgency`;
  `git diff --check`.

## Review Considerations

- FP / type safety: permanent source refs use typed `EvidenceSourceRef`
  records with `ProjectionGapSource` scope. No production state or evidence
  payload type was added.
- Test architecture: expected-gap specs decompose `EMERGENCY-1` into eight
  missing surfaces and include an exact-union guard for all 46 refs.
- Impact: no controller, pilot, sim behaviour, phraseology rendering, SSR, or
  policy behaviour was changed.
- Operational correctness: ICAO 9432 Chapter 9 emergency and communications-
  failure obligations remain distinct from ordinary radio, VFR, and go-around
  traces.
