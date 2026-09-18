# Chunk 08 Expected Gaps

Chunk: `chunk-08-distress-urgency-comms-failure`

This file records ICAO 9432 Chapter 9 source units that cannot honestly be
marked covered-green with the current model/evidence surface.

## Summary

| Source-unit state | Units |
|---|---:|
| `covered-green` / split structured branch | 40 |
| `model-gap` | 2 |
| `model-gap` + `policy-blocked` | 0 |
| `model-gap` + `phraseology-later` | 4 |
| `phraseology-later` | 0 |

fn-68 moved the narrow structured emergency-classification and distress-message
payload branches to source-backed coverage. fn-69 moved the narrow structured
emergency-priority and radio-silence projection branches to source-backed
coverage. fn-70 moved the narrow emergency-descent safeguarding, warning, and
position-question projection branches to source-backed coverage. fn-71 moved
structured communications-failure contact routing, blind-transmission payload,
SSR-code, and blind-clearance policy branches to source-backed coverage.
fn-72 moved emergency assistance actor, intercepted-distress relay, emergency
frequency-policy, and distress/urgency interference-suppression branches to
source-backed coverage. fn-73 moved emergency message addressing, relayed
distress-message variation, urgency-message payload selection, and urgency
addressing/frequency policy branches to source-backed coverage. fn-74 moved
controller-side lost-contact relay and non-clearance blind-
transmission branches to source-backed coverage. fn-75 moved pilot
safety-doubt assistance seeking under explicit policy to source-backed
coverage. `EMERGENCY-1` remains the dominant blocker for split residual facets
around emergency-descent specific-instruction necessity policy and any-means
distress communication, plus standalone Annex 10 conformance and emergency
phraseology/order work.

## Model Gaps

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::distress_urgency_intro_9_1_en::c30159856a1a5e7a` | `EMERGENCY-1` | No Annex 10 emergency-procedure conformance model. |
| `icao9432-extracted::communications_failure_9_5_en::c1c14fab53a608c6` | `EMERGENCY-1` | No Annex 10 communications-failure conformance model. |

## Model Gaps With Policy

No standalone chunk-08 source units remain in this bucket. Split covered rows
still document residual policy facets where only part of the source unit is
covered.

## Model Gaps With Phraseology

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::distress_urgency_intro_9_1_en::13d1c2accd0f7a73` | `EMERGENCY-1`; `PHRASE-1` | No speech-rate/distinctness or rendered emergency utterance evidence. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::9907744b4723d14c` | `EMERGENCY-1`; `PHRASE-1` | No emergency context/time-pressure phraseology adaptation model. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::bf04647e26f9c018` | `EMERGENCY-1`; `PHRASE-1` | No rendered initial emergency call with repeated MAYDAY/PAN PAN. |
| `icao9432-extracted::distress_messages_9_2_en::f0e99a4c08ea0cb3` | `EMERGENCY-1`; `PHRASE-1` | No rendered distress-message element order. |

## Covered Or Split Rows

| Source unit | State | Evidence |
|---|---|---|
| `icao9432-extracted::distress_urgency_intro_9_1_en::8b3b3b4117c04807` | `covered-green structured distress-classification branch` | `Icao9432EmergencyClassificationPayloadSourceBackedTest`; MAYDAY emergency projection carries serious/imminent danger and immediate-assistance semantics. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::c959f0325390e7fe` | `covered-green structured urgency-classification branch` | `Icao9432EmergencyClassificationPayloadSourceBackedTest`; PAN PAN emergency projection carries safety concern without immediate assistance. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::d742970b22d8de26` | `split: protocol emergency-type discriminator mapping covered-green; rendered spoken-word identification phraseology-later` | `Icao9432EmergencyClassificationPayloadSourceBackedTest`; typed discriminator maps MAYDAY to distress and PAN PAN to urgency. Rendered spoken wording and repeated initial call remain `PHRASE-1`. |
| `icao9432-extracted::distress_messages_9_2_en::23c9f447cd6c7814` | `split: all-fields-present structured distress-message payload representation covered-green; rendered wording/order phraseology-later` | `Icao9432EmergencyClassificationPayloadSourceBackedTest`; all-fields-present distress payload representation exposes station addressed, aircraft identification, nature, intentions, position, level, heading, and useful information as structured fields. Field availability, operational omission, partial-message compliance, rendered wording, and order remain out of scope. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::3b1079aa56df2ce6` | `covered-green structured emergency-priority projection branch` | `Icao9432EmergencyPrioritySilenceSourceBackedTest`; priority projection derived from production `EmergencyType.MAYDAY` proves distress traffic outranks urgency and routine traffic. Production radio queue preemption remains out of scope. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::1a20cd48e58a5693` | `covered-green structured emergency-priority projection branch` | `Icao9432EmergencyPrioritySilenceSourceBackedTest`; priority projection derived from production `EmergencyType.PAN_PAN` proves urgency traffic outranks routine traffic and remains below distress traffic. Production radio queue preemption remains out of scope. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::7d35c042421b5b03` | `covered-green structured emergency-frequency discipline projection branch` | `Icao9432EmergencyPrioritySilenceSourceBackedTest`; active distress and active urgency traffic block uninvolved station transmission, allow directly involved participants, and release after controlling-station termination advice. Assistance/relay behavior remains out of scope. |
| `icao9432-extracted::distress_messages_9_2_en::e2902de496f43a95` | `covered-green structured silence-imposition branch` | `Icao9432EmergencyPrioritySilenceSourceBackedTest`; distress aircraft and controlling station can impose all-aircraft or named-aircraft silence, while non-authorities are rejected. |
| `icao9432-extracted::distress_messages_9_2_en::ed898005cd1a4da5` | `covered-green structured silence-obligation branch` | `Icao9432EmergencyPrioritySilenceSourceBackedTest`; named aircraft remains silenced until controlling-station advice that distress traffic has ended. |
| `icao9432-extracted::distress_messages_9_2_en::c20024dad1b7144e` | `covered-green structured silence-termination branch` | `Icao9432EmergencyPrioritySilenceSourceBackedTest`; controlling-station termination advice clears emergency traffic state and silence obligations. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::082f9668292ed82c` | `covered-green structured emergency-descent safeguarding projection branch` | `Icao9432EmergencyDescentSourceBackedTest`; typed emergency descent announcement activates safeguarding for affected traffic and resets all derived state after resolution. Production conflict resolution remains out of scope. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::c71568b00fb1535e` | `split: structured emergency-descent warning projection branch covered-green; specific-instruction necessity policy remains model-gap + policy-blocked` | `Icao9432EmergencyDescentSourceBackedTest`; emergency descent announcement emits general warning action for affected traffic. Necessity policy for follow-up specific instructions remains `OperationalGuidancePolicy`. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::ca0c243491ff5d13` | `covered-green structured emergency-descent position-question branch` | `Icao9432EmergencyDescentSourceBackedTest`; uncertain emergency descent position supports a controller position-question branch, while known position does not. |
| `icao9432-extracted::distress_messages_9_2_en::4b37e039e7eb8afa` | `split: structured distress SSR 7700 branch covered-green; distress assistance/any-means branch remains model-gap` | `Icao9432CommunicationsFailureSourceBackedTest`; distress with SSR equipment can select code 7700. Station assistance and any-means communication behavior remain out of scope. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::9c34a1b8d6d623fa` | `covered-green structured emergency assistance actor branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest`; a non-addressed station or aircraft can reply and assist when the called ground station does not reply. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::2fee92c222323e6a` | `covered-green structured alternate emergency frequency branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest`; emergency communications can select another frequency when typed policy marks it necessary or desirable for assistance. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::24f94381ed9e8ce1` | `covered-green configured emergency frequency-continuity policy branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest`; configured policy keeps distress communications on the current frequency unless another frequency better assists. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::82ee7048517d8478` | `covered-green configured assistance-content policy branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest`; replying station assistance carries configured advice, information, and instruction content. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::87b67820c6092a98` | `covered-green configured pilot safety-doubt assistance branch` | `Icao9432PilotSafetyDoubtSourceBackedTest`; pilot safety doubt plus explicit policy yields an assistance request without claiming rendered phraseology or complete safety-doubt taxonomy. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::8e9f7818b91d08c3` | `covered-green structured intercepted-distress relay branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest`; intercepting aircraft can acknowledge and broadcast unacknowledged distress. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::cb12c2f9b97c64b7` | `covered-green configured emergency initial-frequency policy branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest`; distress and urgency calls initially use the frequency in use under configured current-frequency policy. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::06f7a72397c325ac` | `covered-green configured distress interference-suppression branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest`; active distress traffic suppresses superfluous uninvolved transmissions under explicit policy. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::5df94af7a64c3f5d` | `covered-green configured urgency interference-suppression branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest`; active urgency traffic suppresses superfluous other-station transmissions under explicit policy. |
| `icao9432-extracted::distress_messages_9_2_en::27a450fa3bfcbc0a` | `covered-green configured distress-message addressing policy branch` | `Icao9432EmergencyMessagePolicySourceBackedTest`; configured policy selects the current station or responsible-area station without claiming rendered wording. |
| `icao9432-extracted::distress_messages_9_2_en::94e94be0c800c982` | `covered-green configured relayed-distress payload-variation policy branch` | `Icao9432EmergencyMessagePolicySourceBackedTest`; relayed distress payload variation requires a clearly stated circumstance in structured evidence. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::1de475a788206cc8` | `covered-green configured urgency-message payload policy branch` | `Icao9432EmergencyMessagePolicySourceBackedTest`; urgency payload policy rejects missing required elements and allows omission of non-required elements. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::b95d7bb1cb409bca` | `covered-green configured urgency addressing and frequency policy branch` | `Icao9432EmergencyMessagePolicySourceBackedTest`; urgency calls use the frequency in use and current or responsible-area station under explicit policy. |
| `icao9432-extracted::communications_failure_9_5_en::f05016444e2b8808` | `covered-green structured communications-failure alternate-frequency branch` | `Icao9432CommunicationsFailureSourceBackedTest`; typed failed designated-frequency contact selects another route-appropriate frequency. |
| `icao9432-extracted::communications_failure_9_5_en::fcb3a49672165b4f` | `covered-green structured communications-failure alternate-contact branch` | `Icao9432CommunicationsFailureSourceBackedTest`; after alternate-frequency contact fails, the model selects other aircraft or stations on route-appropriate frequencies. |
| `icao9432-extracted::communications_failure_9_5_en::bc9bb12804033b07` | `split: structured blind-transmission mode/repetition branch covered-green; rendered TRANSMITTING BLIND prefix remains phraseology-later` | `Icao9432CommunicationsFailureSourceBackedTest`; failed-contact blind-transmission mode repeats the intended message twice. Rendered prefix wording remains `PHRASE-1`. |
| `icao9432-extracted::communications_failure_9_5_en::abbc376a430003b0` | `split: structured blind-transmission addressee branch covered-green; rendered addressee phraseology remains phraseology-later` | `Icao9432CommunicationsFailureSourceBackedTest`; blind-transmission metadata carries explicit addressees. Necessity policy and rendered wording remain out of scope. |
| `icao9432-extracted::communications_failure_9_5_en::975a63151706f68f` | `covered-green structured blind-message repetition branch` | `Icao9432CommunicationsFailureSourceBackedTest`; intended message is scheduled with a complete repetition. |
| `icao9432-extracted::communications_failure_9_5_en::045c2e33f59f5ede` | `covered-green structured blind next-transmission-time branch` | `Icao9432CommunicationsFailureSourceBackedTest`; receiver-failure blind transmission carries the next intended transmission time. |
| `icao9432-extracted::communications_failure_9_5_en::7900c606e05e509b` | `covered-green structured communications-failure continuation-intention branch` | `Icao9432CommunicationsFailureSourceBackedTest`; continuation intention is present only under ATC/advisory service context. |
| `icao9432-extracted::communications_failure_9_5_en::78c73a75fab644f4` | `split: structured receiver-failure blind-transmission branch covered-green; rendered receiver-failure prefix remains phraseology-later` | `Icao9432CommunicationsFailureSourceBackedTest`; receiver-failure blind mode is distinct from failed-contact blind mode. Rendered prefix wording remains `PHRASE-1`. |
| `icao9432-extracted::communications_failure_9_5_en::91e7d233bf3b64ff` | `covered-green structured radio-failure SSR 7600 branch` | `Icao9432CommunicationsFailureSourceBackedTest`; communications failure with SSR equipment selects code 7600, distinct from distress SSR 7700. |
| `icao9432-extracted::communications_failure_9_5_en::b73dda299970c2f3` | `covered-green structured blind-clearance prohibition/exception branch` | `Icao9432CommunicationsFailureSourceBackedTest`; blind ATC clearances are rejected unless the clearance originator explicitly requests blind transmission. |
| `icao9432-extracted::communications_failure_9_5_en::bb66a050093251c2` | `covered-green configured controller route-aircraft relay request branch` | `Icao9432ControllerLostContactSourceBackedTest`; after calls fail on frequencies the aircraft is believed to be listening on, a route-aircraft call/relay request is available under explicit policy. |
| `icao9432-extracted::communications_failure_9_5_en::24c806b040f4ef5e` | `covered-green configured controller inter-station relay request branch` | `Icao9432ControllerLostContactSourceBackedTest`; after calls fail on frequencies the aircraft is believed to be listening on, an other-station call/relay request is available under explicit policy. |
| `icao9432-extracted::communications_failure_9_5_en::75055714e70d4560` | `covered-green configured ATC non-clearance blind-transmission branch` | `Icao9432ControllerLostContactSourceBackedTest`; non-clearance blind transmission requires failed relay attempts and believed-listening evidence. |
