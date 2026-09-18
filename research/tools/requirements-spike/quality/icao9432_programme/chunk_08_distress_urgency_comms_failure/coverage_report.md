# Chunk 08 Coverage Report

Chunk: ICAO 9432 distress, urgency, emergency descent, and communications
failure.

## Summary

| Final state | Units |
|---|---:|
| `covered-green` / split structured branch | 40 |
| `covered-red` | 0 |
| `model-gap` | 2 |
| `model-gap` + `policy-blocked` | 0 |
| `model-gap` + `phraseology-later` | 4 |

Chunk 08 now has narrow structured fn-68 emergency evidence for distress versus
urgency classification and distress-message payload fields, fn-69 structured
projection evidence for emergency priority ordering and radio-silence
discipline, fn-70 structured projection evidence for emergency descent
safeguarding/warning/position-questioning branches, and fn-71 structured
projection evidence for communications-failure route-appropriate alternate
contacts, blind-transmission scheduling payloads, SSR 7600/7700 distinction,
blind-clearance prohibition/exception handling, and fn-72 structured projection
evidence for emergency assistance actors, intercepted-distress relay, emergency
frequency policy, and distress/urgency interference suppression. fn-73 adds
structured projection evidence for emergency message addressing, relayed
distress-message variation, urgency-message payload selection, and urgency
addressing/frequency policy. fn-74 adds structured projection evidence for
controller-side lost-contact route-aircraft relay, inter-station relay, and
ATC-originated non-clearance blind transmission after failed relay attempts
with believed-listening evidence. fn-75 adds structured projection evidence
for pilot safety-doubt assistance seeking under explicit policy. It still has
split residual facets for emergency-descent specific-instruction necessity
policy and any-means distress communication, and standalone executable gaps for
Annex 10 conformance and rendered emergency phraseology/order.
Ordinary VFR, go-around, or routine radio traces are not emergency-compliance
evidence.

## Coverage Table

| Source unit | Final state | Test / blocker |
|---|---|---|
| `icao9432-extracted::distress_urgency_intro_9_1_en::8b3b3b4117c04807` | `covered-green structured distress-classification branch` | `Icao9432EmergencyClassificationPayloadSourceBackedTest` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::c959f0325390e7fe` | `covered-green structured urgency-classification branch` | `Icao9432EmergencyClassificationPayloadSourceBackedTest` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::3b1079aa56df2ce6` | `covered-green structured emergency-priority projection branch` | `Icao9432EmergencyPrioritySilenceSourceBackedTest` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::1a20cd48e58a5693` | `covered-green structured emergency-priority projection branch` | `Icao9432EmergencyPrioritySilenceSourceBackedTest` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::7d35c042421b5b03` | `covered-green structured emergency-frequency discipline projection branch` | `Icao9432EmergencyPrioritySilenceSourceBackedTest` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::9c34a1b8d6d623fa` | `covered-green structured emergency assistance actor branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::24f94381ed9e8ce1` | `covered-green configured emergency frequency-continuity policy branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest`; `OperationalGuidancePolicy` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::2fee92c222323e6a` | `covered-green structured alternate emergency frequency branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::82ee7048517d8478` | `covered-green configured assistance-content policy branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest`; `OperationalGuidancePolicy` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::87b67820c6092a98` | `covered-green configured pilot safety-doubt assistance branch` | `Icao9432PilotSafetyDoubtSourceBackedTest`; `OperationalGuidancePolicy` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::8e9f7818b91d08c3` | `covered-green structured intercepted-distress relay branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest`; `OperationalGuidancePolicy` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::cb12c2f9b97c64b7` | `covered-green configured emergency initial-frequency policy branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest`; `OperationalGuidancePolicy` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::06f7a72397c325ac` | `covered-green configured distress interference-suppression branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest`; `OperationalGuidancePolicy` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::13d1c2accd0f7a73` | `model-gap` + `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `PHRASE-1` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::c30159856a1a5e7a` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::9907744b4723d14c` | `model-gap` + `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `PHRASE-1` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::bf04647e26f9c018` | `model-gap` + `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `PHRASE-1` |
| `icao9432-extracted::distress_urgency_intro_9_1_en::d742970b22d8de26` | `split: protocol emergency-type discriminator mapping covered-green; rendered spoken-word identification phraseology-later` | `Icao9432EmergencyClassificationPayloadSourceBackedTest`; `PHRASE-1` |
| `icao9432-extracted::distress_messages_9_2_en::23c9f447cd6c7814` | `split: all-fields-present structured distress-message payload representation covered-green; rendered wording/order phraseology-later` | `Icao9432EmergencyClassificationPayloadSourceBackedTest`; `PHRASE-1` |
| `icao9432-extracted::distress_messages_9_2_en::f0e99a4c08ea0cb3` | `model-gap` + `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1`; `PHRASE-1` |
| `icao9432-extracted::distress_messages_9_2_en::27a450fa3bfcbc0a` | `covered-green configured distress-message addressing policy branch` | `Icao9432EmergencyMessagePolicySourceBackedTest`; `OperationalGuidancePolicy` |
| `icao9432-extracted::distress_messages_9_2_en::94e94be0c800c982` | `covered-green configured relayed-distress payload-variation policy branch` | `Icao9432EmergencyMessagePolicySourceBackedTest`; `OperationalGuidancePolicy` |
| `icao9432-extracted::distress_messages_9_2_en::4b37e039e7eb8afa` | `split: structured distress SSR 7700 branch covered-green; distress assistance/any-means branch remains model-gap` | `Icao9432CommunicationsFailureSourceBackedTest`; `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |
| `icao9432-extracted::distress_messages_9_2_en::e2902de496f43a95` | `covered-green structured silence-imposition branch` | `Icao9432EmergencyPrioritySilenceSourceBackedTest` |
| `icao9432-extracted::distress_messages_9_2_en::ed898005cd1a4da5` | `covered-green structured silence-obligation branch` | `Icao9432EmergencyPrioritySilenceSourceBackedTest` |
| `icao9432-extracted::distress_messages_9_2_en::c20024dad1b7144e` | `covered-green structured silence-termination branch` | `Icao9432EmergencyPrioritySilenceSourceBackedTest` |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::1de475a788206cc8` | `covered-green configured urgency-message payload policy branch` | `Icao9432EmergencyMessagePolicySourceBackedTest`; `OperationalGuidancePolicy` |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::b95d7bb1cb409bca` | `covered-green configured urgency addressing and frequency policy branch` | `Icao9432EmergencyMessagePolicySourceBackedTest`; `OperationalGuidancePolicy` |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::5df94af7a64c3f5d` | `covered-green configured urgency interference-suppression branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest`; `OperationalGuidancePolicy` |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::082f9668292ed82c` | `covered-green structured emergency-descent safeguarding projection branch` | `Icao9432EmergencyDescentSourceBackedTest` |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::c71568b00fb1535e` | `split: structured emergency-descent warning projection branch covered-green; specific-instruction necessity policy remains model-gap + policy-blocked` | `Icao9432EmergencyDescentSourceBackedTest`; `OperationalGuidancePolicy` |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::ca0c243491ff5d13` | `covered-green structured emergency-descent position-question branch` | `Icao9432EmergencyDescentSourceBackedTest` |
| `icao9432-extracted::communications_failure_9_5_en::f05016444e2b8808` | `covered-green structured communications-failure alternate-frequency branch` | `Icao9432CommunicationsFailureSourceBackedTest` |
| `icao9432-extracted::communications_failure_9_5_en::fcb3a49672165b4f` | `covered-green structured communications-failure alternate-contact branch` | `Icao9432CommunicationsFailureSourceBackedTest` |
| `icao9432-extracted::communications_failure_9_5_en::bc9bb12804033b07` | `split: structured blind-transmission mode/repetition branch covered-green; rendered TRANSMITTING BLIND prefix remains phraseology-later` | `Icao9432CommunicationsFailureSourceBackedTest`; `PHRASE-1` |
| `icao9432-extracted::communications_failure_9_5_en::abbc376a430003b0` | `split: structured blind-transmission addressee branch covered-green; rendered addressee phraseology remains phraseology-later` | `Icao9432CommunicationsFailureSourceBackedTest`; `PHRASE-1` |
| `icao9432-extracted::communications_failure_9_5_en::975a63151706f68f` | `covered-green structured blind-message repetition branch` | `Icao9432CommunicationsFailureSourceBackedTest` |
| `icao9432-extracted::communications_failure_9_5_en::045c2e33f59f5ede` | `covered-green structured blind next-transmission-time branch` | `Icao9432CommunicationsFailureSourceBackedTest` |
| `icao9432-extracted::communications_failure_9_5_en::7900c606e05e509b` | `covered-green structured communications-failure continuation-intention branch` | `Icao9432CommunicationsFailureSourceBackedTest` |
| `icao9432-extracted::communications_failure_9_5_en::78c73a75fab644f4` | `split: structured receiver-failure blind-transmission branch covered-green; rendered receiver-failure prefix remains phraseology-later` | `Icao9432CommunicationsFailureSourceBackedTest`; `PHRASE-1` |
| `icao9432-extracted::communications_failure_9_5_en::91e7d233bf3b64ff` | `covered-green structured radio-failure SSR 7600 branch` | `Icao9432CommunicationsFailureSourceBackedTest` |
| `icao9432-extracted::communications_failure_9_5_en::bb66a050093251c2` | `covered-green configured controller route-aircraft relay request branch` | `Icao9432ControllerLostContactSourceBackedTest`; `ControllerInterventionPolicy` |
| `icao9432-extracted::communications_failure_9_5_en::24c806b040f4ef5e` | `covered-green configured controller inter-station relay request branch` | `Icao9432ControllerLostContactSourceBackedTest`; `ControllerInterventionPolicy` |
| `icao9432-extracted::communications_failure_9_5_en::75055714e70d4560` | `covered-green configured ATC non-clearance blind-transmission branch` | `Icao9432ControllerLostContactSourceBackedTest`; `ClearanceTimingPolicy` |
| `icao9432-extracted::communications_failure_9_5_en::b73dda299970c2f3` | `covered-green structured blind-clearance prohibition/exception branch` | `Icao9432CommunicationsFailureSourceBackedTest` |
| `icao9432-extracted::communications_failure_9_5_en::c1c14fab53a608c6` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `EMERGENCY-1` |

## Verification

- Source-unit provenance: all 46 accepted candidate JSON records are present in
  the registry with `lifecycle.state = accepted`. Source text was checked
  against `research/txt/icao9432-extracted.txt` in Chapter 9.
- Focused verification:
  `./gradlew-nix :sim:jvmTest --tests '*.Icao9432EmergencyClassificationPayloadSourceBackedTest' --tests '*.Icao9432EmergencyPrioritySilenceSourceBackedTest' --tests '*.Icao9432EmergencyDescentSourceBackedTest' --tests '*.Icao9432CommunicationsFailureSourceBackedTest' --tests '*.Icao9432EmergencyAssistanceRelaySourceBackedTest' --tests '*.Icao9432EmergencyMessagePolicySourceBackedTest' --tests '*.Icao9432ControllerLostContactSourceBackedTest' --tests '*.Icao9432PilotSafetyDoubtSourceBackedTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`.
- Full verification:
  `./gradlew-nix :sim:jvmTest`;
  `./gradlew-nix detekt`;
  `.flow/bin/flowctl validate --epic fn-58-icao-9432-chunk-08-distress-urgency`;
  `git diff --check`.

## Review Considerations

- FP / type safety: permanent source refs use typed `EvidenceSourceRef`
  records with `ProjectionGapSource` scope. fn-68 adds a closed local
  source-unit projection over existing `PilotTransmissionFact`; fn-69 adds a
  closed local emergency radio-discipline projection derived from production
  `EmergencyType`; fn-71 adds closed local communications-failure projection
  types for contact attempts, blind-transmission payloads, SSR code selection,
  and blind-clearance policy; fn-72 adds closed local assistance, relay,
  frequency-policy, and suppression projections; fn-73 adds closed local
  emergency message addressing and payload-policy projections; fn-74 adds
  closed local controller lost-contact relay and non-clearance blind-
  transmission projections; fn-75 adds a closed local pilot safety-doubt
  assistance projection.
- Test architecture: source-backed tests cover the narrow structured branches;
  expected-gap specs still decompose residual `EMERGENCY-1` into missing
  surfaces and include an exact-union guard for all 46 refs.
- Impact: no controller, pilot, sim scheduler, phraseology rendering, SSR, or
  policy behaviour was changed. fn-69, fn-70, fn-71, fn-72, fn-73, fn-74, and fn-75 are
  structured projection evidence, not production radio queue preemption,
  production emergency descent conflict-resolution behavior, production
  communications-failure workflow, global emergency assistance scheduling, or
  rendered emergency message phraseology. fn-75 does not add a production pilot
  emergency decision engine or complete safety-doubt taxonomy.
- Operational correctness: ICAO 9432 Chapter 9 emergency and communications-
  failure obligations remain distinct from ordinary radio, VFR, and go-around
  traces.
