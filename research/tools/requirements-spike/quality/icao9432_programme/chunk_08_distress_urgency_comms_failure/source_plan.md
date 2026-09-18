# Chunk 08 Source Plan

Chunk: `chunk-08-distress-urgency-comms-failure`

Scope: ICAO 9432 §9.1 introduction, §9.2 distress messages, §9.3-§9.4
urgency and emergency descent, and §9.5 aircraft communications failure.

## Source Audit

- Accepted source units: 46.
- Sections:
  - `distress_urgency_intro_9_1_en`: 18 units.
  - `distress_messages_9_2_en`: 8 units.
  - `urgency_emergency_descent_9_3_to_9_4_en`: 6 units.
  - `communications_failure_9_5_en`: 14 units.
- Quote audit: all 46 accepted source units were checked against
  `research/txt/icao9432-extracted.txt`. Source text is present in Chapter 9,
  beginning at the extracted-text `Chapter 9` heading around line 8686.

## Review Position

The generated classifier is directionally correct that this chunk is blocked by
`EMERGENCY-1` and `PHRASE-1`. Current simulator traces model ordinary aircraft,
pilots, controllers, radio transmissions, go-arounds, and traffic sequencing.
They now include structured projection evidence for distress/urgency
classification, emergency priority, emergency radio-silence discipline,
emergency-descent structured branches, and communications-failure structured
branches, plus local structured evidence for assistance actors, intercepted
distress relay, emergency frequency policy, and distress/urgency interference
suppression, and emergency message addressing/payload policy. They still do not
model any-means distress communication, production emergency-descent conflict
resolution, controller-side lost-contact relay, ATC-originated blind non-
clearance workflow, Annex 10 conformance, rendered emergency phraseology/order,
or production radio queue preemption. Ordinary radio or VFR scenario traces
must not be reused as emergency-compliance evidence.

## Planned Coverage

| Source unit | Source text / claim | Initial classifier | Planned state | Planned evidence / blocker |
|---|---|---|---|---|
| `icao9432-extracted::distress_urgency_intro_9_1_en::8b3b3b4117c04807` | Distress is serious/imminent danger requiring immediate assistance. | `needs-sim-model` | `covered-green structured distress-classification branch` | `Icao9432EmergencyClassificationPayloadSourceBackedTest`; MAYDAY emergency projection carries serious/imminent danger and immediate-assistance semantics. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::c959f0325390e7fe` | Urgency concerns safety but does not require immediate assistance. | `needs-sim-model` | `covered-green structured urgency-classification branch` | `Icao9432EmergencyClassificationPayloadSourceBackedTest`; PAN PAN emergency projection carries safety concern without immediate assistance. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::3b1079aa56df2ce6` | Distress messages have priority over all other transmissions. | `needs-sim-model` | `covered-green structured emergency-priority projection branch` | `Icao9432EmergencyPrioritySilenceSourceBackedTest`; production `EmergencyType.MAYDAY` priority projection outranks urgency and routine traffic. Production radio queue preemption remains out of scope. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::1a20cd48e58a5693` | Urgency messages have priority except over distress. | `needs-sim-model` | `covered-green structured emergency-priority projection branch` | `Icao9432EmergencyPrioritySilenceSourceBackedTest`; production `EmergencyType.PAN_PAN` priority projection outranks routine traffic and remains below distress traffic. Production radio queue preemption remains out of scope. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::7d35c042421b5b03` | Stations shall refrain from using a frequency carrying emergency traffic unless involved. | `needs-sim-model` | `covered-green structured emergency-frequency discipline projection branch` | `Icao9432EmergencyPrioritySilenceSourceBackedTest`; active distress and urgency traffic block uninvolved station transmission until controlling-station termination advice. Assistance/relay behavior remains out of scope. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::9c34a1b8d6d623fa` | If called ground station does not reply, another station/aircraft shall reply and assist. | `needs-sim-model` | `covered-green structured emergency assistance actor branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest`; non-addressed stations and aircraft can reply and assist when the called ground station does not reply. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::24f94381ed9e8ce1` | Distress communications normally continue on current frequency until a better frequency helps. | `needs-sim-model` | `covered-green configured emergency frequency-continuity policy branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest`; `OperationalGuidancePolicy`; configured policy keeps the current frequency unless another frequency better assists. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::2fee92c222323e6a` | Other communication frequencies may be used if necessary or desirable. | `needs-sim-model` | `covered-green structured alternate emergency frequency branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest`; emergency traffic can select another frequency when typed policy marks it necessary or desirable for assistance. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::82ee7048517d8478` | Replying station should provide advice, information, and instructions needed to assist. | `needs-sim-model` | `covered-green configured assistance-content policy branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest`; `OperationalGuidancePolicy`; replying-station assistance carries configured advice, information, and instruction content. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::87b67820c6092a98` | Pilots should seek assistance when flight safety is in doubt. | `needs-sim-model` | `model-gap` + `policy-blocked` | `EMERGENCY-1`; `OperationalGuidancePolicy`; no pilot safety-doubt trigger. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::8e9f7818b91d08c3` | Intercepting aircraft may acknowledge and broadcast unacknowledged distress. | `needs-sim-model` | `covered-green structured intercepted-distress relay branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest`; `OperationalGuidancePolicy`; intercepting aircraft can acknowledge and broadcast unacknowledged distress. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::cb12c2f9b97c64b7` | Distress/urgency call normally uses the frequency in use. | `needs-sim-model` | `covered-green configured emergency initial-frequency policy branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest`; `OperationalGuidancePolicy`; distress and urgency calls initially use the frequency in use under configured current-frequency policy. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::06f7a72397c325ac` | Superfluous transmissions may distract an already busy pilot. | `needs-sim-model` | `covered-green configured distress interference-suppression branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest`; `OperationalGuidancePolicy`; active distress traffic suppresses superfluous uninvolved transmissions under explicit policy. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::13d1c2accd0f7a73` | Pilots should speak slowly and distinctly to avoid repetition. | `needs-sim-model` | `model-gap` + `phraseology-later` | `EMERGENCY-1`; `PHRASE-1`; no speech-rate/distinctness or rendered emergency utterance evidence. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::c30159856a1a5e7a` | Distress and urgency procedures are detailed in Annex 10 Volume II. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no Annex 10 emergency-procedure conformance model. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::9907744b4723d14c` | Pilots should adapt Chapter 9 phraseology to needs/time available. | `phraseology-later` | `model-gap` + `phraseology-later` | `EMERGENCY-1`; `PHRASE-1`; no emergency context/time-pressure phraseology adaptation model. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::bf04647e26f9c018` | MAYDAY or PAN PAN should preferably be spoken three times initially. | `phraseology-later` | `model-gap` + `phraseology-later` | `EMERGENCY-1`; `PHRASE-1`; no rendered initial emergency call. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::d742970b22d8de26` | MAYDAY identifies distress; PAN PAN identifies urgency. | `phraseology-later` | `split: protocol emergency-type discriminator mapping covered-green; rendered spoken-word identification phraseology-later` | `Icao9432EmergencyClassificationPayloadSourceBackedTest`; typed discriminator maps MAYDAY to distress and PAN PAN to urgency. Rendered spoken wording and repeated initial call remain `PHRASE-1`. |
| `icao9432-extracted::distress_messages_9_2_en::23c9f447cd6c7814` | Distress message should contain station addressed, aircraft ID, distress nature, intentions, position, level, heading, and useful information. | `phraseology-later` | `split: all-fields-present structured distress-message payload representation covered-green; rendered wording/order phraseology-later` | `Icao9432EmergencyClassificationPayloadSourceBackedTest`; all-fields-present distress payload representation exposes station addressed, aircraft identification, nature, intentions, position, level, heading, and useful information as structured fields. Field availability, operational omission, partial-message compliance, rendered wording, and order remain out of scope. |
| `icao9432-extracted::distress_messages_9_2_en::f0e99a4c08ea0cb3` | Distress-message elements should be in the shown order if possible. | `phraseology-later` | `model-gap` + `phraseology-later` | `EMERGENCY-1`; `PHRASE-1`; no rendered distress-message ordering. |
| `icao9432-extracted::distress_messages_9_2_en::27a450fa3bfcbc0a` | Distress message normally addresses current station or responsible-area station. | `needs-sim-model` | `covered-green configured distress-message addressing policy branch` | `Icao9432EmergencyMessagePolicySourceBackedTest`; `OperationalGuidancePolicy`; configured policy selects current station or responsible-area station without claiming rendered wording. |
| `icao9432-extracted::distress_messages_9_2_en::94e94be0c800c982` | Non-distressed transmitting station may vary elements if circumstance is clear. | `needs-sim-model` | `covered-green configured relayed-distress payload-variation policy branch` | `Icao9432EmergencyMessagePolicySourceBackedTest`; `OperationalGuidancePolicy`; relayed variation requires a clearly stated circumstance in structured evidence. |
| `icao9432-extracted::distress_messages_9_2_en::4b37e039e7eb8afa` | Aircraft in distress may use any means, including SSR 7700; stations may assist. | `needs-sim-model` | `split: structured distress SSR 7700 branch covered-green; distress assistance/any-means branch remains model-gap` | `Icao9432CommunicationsFailureSourceBackedTest`; distress with SSR equipment can select code 7700. Station assistance and any-means communication behavior remain out of scope. |
| `icao9432-extracted::distress_messages_9_2_en::e2902de496f43a95` | Distress aircraft or station controlling distress traffic may impose silence. | `needs-sim-model` | `covered-green structured silence-imposition branch` | `Icao9432EmergencyPrioritySilenceSourceBackedTest`; distress aircraft and controlling station can impose silence; non-authorities are rejected. |
| `icao9432-extracted::distress_messages_9_2_en::ed898005cd1a4da5` | Aircraft requested to maintain silence shall do so until distress traffic ends. | `needs-sim-model` | `covered-green structured silence-obligation branch` | `Icao9432EmergencyPrioritySilenceSourceBackedTest`; named-aircraft silence remains until controlling-station advice that distress traffic has ended. |
| `icao9432-extracted::distress_messages_9_2_en::c20024dad1b7144e` | Ground station shall terminate distress communication/silence when distress ends. | `needs-sim-model` | `covered-green structured silence-termination branch` | `Icao9432EmergencyPrioritySilenceSourceBackedTest`; controlling-station termination advice clears emergency traffic state and silence obligations. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::1de475a788206cc8` | Urgency message should contain required 9.2.1.1 elements as circumstances require. | `needs-sim-model` | `covered-green configured urgency-message payload policy branch` | `Icao9432EmergencyMessagePolicySourceBackedTest`; `OperationalGuidancePolicy`; configured urgency payload policy rejects missing required elements and permits omission of non-required elements. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::b95d7bb1cb409bca` | Urgency call normally uses current frequency and current/responsible station. | `needs-sim-model` | `covered-green configured urgency addressing and frequency policy branch` | `Icao9432EmergencyMessagePolicySourceBackedTest`; `OperationalGuidancePolicy`; urgency calls use frequency in use and current or responsible-area station under explicit policy. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::5df94af7a64c3f5d` | Other stations should avoid interfering with urgency traffic. | `needs-sim-model` | `covered-green configured urgency interference-suppression branch` | `Icao9432EmergencyAssistanceRelaySourceBackedTest`; `OperationalGuidancePolicy`; active urgency traffic suppresses superfluous other-station transmissions under explicit policy. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::082f9668292ed82c` | On emergency descent announcement, controller takes all possible action to safeguard other aircraft. | `needs-sim-model` | `covered-green structured emergency-descent safeguarding projection branch` | `Icao9432EmergencyDescentSourceBackedTest`; typed emergency descent announcement activates safeguarding for affected traffic and clears derived state on resolution. Production conflict resolution remains out of scope. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::c71568b00fb1535e` | Emergency descent broadcast should be followed by specific instructions as necessary. | `needs-sim-model` | `split: structured emergency-descent warning projection branch covered-green; specific-instruction necessity policy remains model-gap + policy-blocked` | `Icao9432EmergencyDescentSourceBackedTest`; general warning action is covered. Necessity policy for follow-up specific instructions remains `OperationalGuidancePolicy`. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::ca0c243491ff5d13` | Further questions may be asked to help ascertain aircraft position. | `needs-sim-model` | `covered-green structured emergency-descent position-question branch` | `Icao9432EmergencyDescentSourceBackedTest`; uncertain emergency descent position supports a position-question branch, while known position does not. |
| `icao9432-extracted::communications_failure_9_5_en::f05016444e2b8808` | If contact fails on designated frequency, try another route-appropriate frequency. | `needs-sim-model` | `covered-green structured communications-failure alternate-frequency branch` | `Icao9432CommunicationsFailureSourceBackedTest`; typed communications-failure state after failed designated-frequency contact selects another route-appropriate frequency. |
| `icao9432-extracted::communications_failure_9_5_en::fcb3a49672165b4f` | If that fails, try other aircraft or stations on route-appropriate frequencies. | `needs-sim-model` | `covered-green structured communications-failure alternate-contact branch` | `Icao9432CommunicationsFailureSourceBackedTest`; after alternate-frequency contact fails, the model selects other aircraft or other aeronautical stations on route-appropriate frequencies. |
| `icao9432-extracted::communications_failure_9_5_en::bc9bb12804033b07` | If attempts fail, transmit message twice, preceded by TRANSMITTING BLIND. | `phraseology-later` | `split: structured blind-transmission mode/repetition branch covered-green; rendered TRANSMITTING BLIND prefix remains phraseology-later` | `Icao9432CommunicationsFailureSourceBackedTest`; blind-transmission mode emits the intended message twice after contact attempts fail. Rendered prefix wording is not claimed. |
| `icao9432-extracted::communications_failure_9_5_en::abbc376a430003b0` | Blind transmission may include addressee(s) if necessary. | `phraseology-later` | `split: structured blind-transmission addressee branch covered-green; rendered addressee phraseology remains phraseology-later` | `Icao9432CommunicationsFailureSourceBackedTest`; blind-transmission metadata can carry explicit addressees. Necessity policy and rendered wording are not claimed. |
| `icao9432-extracted::communications_failure_9_5_en::975a63151706f68f` | Aircraft shall transmit intended message followed by complete repetition. | `needs-sim-model` | `covered-green structured blind-message repetition branch` | `Icao9432CommunicationsFailureSourceBackedTest`; intended message is scheduled with a complete repetition. |
| `icao9432-extracted::communications_failure_9_5_en::045c2e33f59f5ede` | Aircraft shall advise time of next intended transmission. | `needs-sim-model` | `covered-green structured blind next-transmission-time branch` | `Icao9432CommunicationsFailureSourceBackedTest`; receiver-failure blind transmission carries the next intended transmission time. |
| `icao9432-extracted::communications_failure_9_5_en::7900c606e05e509b` | ATC/advisory aircraft shall transmit PIC intentions for flight continuation. | `needs-sim-model` | `covered-green structured communications-failure continuation-intention branch` | `Icao9432CommunicationsFailureSourceBackedTest`; continuation intention is carried only under ATC/advisory service context. |
| `icao9432-extracted::communications_failure_9_5_en::78c73a75fab644f4` | Receiver failure reports use TRANSMITTING BLIND DUE RECEIVER FAILURE. | `phraseology-later` | `split: structured receiver-failure blind-transmission branch covered-green; rendered receiver-failure prefix remains phraseology-later` | `Icao9432CommunicationsFailureSourceBackedTest`; receiver-failure mode is distinct from generic failed-contact blind-transmission mode. Rendered wording is not claimed. |
| `icao9432-extracted::communications_failure_9_5_en::91e7d233bf3b64ff` | Airborne equipment failure should select SSR code 7600 when equipped. | `needs-sim-model` | `covered-green structured radio-failure SSR 7600 branch` | `Icao9432CommunicationsFailureSourceBackedTest`; radio/communications failure with SSR equipment selects code 7600 and is distinct from distress SSR 7700. |
| `icao9432-extracted::communications_failure_9_5_en::bb66a050093251c2` | Station unable to contact aircraft shall ask route aircraft to call/relay. | `needs-sim-model` | `model-gap` + `policy-blocked` | `EMERGENCY-1`; `ControllerInterventionPolicy`; no lost-contact assistance/relay workflow. |
| `icao9432-extracted::communications_failure_9_5_en::24c806b040f4ef5e` | Station unable to contact aircraft shall ask other stations to call/relay. | `needs-sim-model` | `model-gap` + `policy-blocked` | `EMERGENCY-1`; `ControllerInterventionPolicy`; no inter-station lost-contact relay workflow. |
| `icao9432-extracted::communications_failure_9_5_en::75055714e70d4560` | If station attempts fail, non-clearance messages may be blind-transmitted. | `needs-sim-model` | `model-gap` + `policy-blocked` | `EMERGENCY-1`; `ClearanceTimingPolicy`; no ATC-originated blind non-clearance workflow after failed station attempts while the aircraft is believed listening. |
| `icao9432-extracted::communications_failure_9_5_en::b73dda299970c2f3` | Blind ATC clearances shall not be made except at originator request. | `needs-sim-model` | `covered-green structured blind-clearance prohibition/exception branch` | `Icao9432CommunicationsFailureSourceBackedTest`; blind ATC clearances are rejected unless the clearance originator explicitly requests blind transmission. |
| `icao9432-extracted::communications_failure_9_5_en::c1c14fab53a608c6` | General communications-failure rules are in Annex 10 Volume II. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no Annex 10 communications-failure conformance model. |

## Test Plan State

1. Source-specific specs are grouped by current model surface:
   - fn-68 covers distress/urgency classification and structured distress
     payload projection;
   - fn-69 covers emergency priority and emergency-frequency/radio-silence
     discipline as structured projection evidence;
   - fn-70 covers emergency-descent safeguarding, warning, and
     position-questioning structured branches;
   - fn-71 covers communications-failure route-appropriate alternate contact,
     blind-transmission scheduling payloads, SSR 7600/7700 distinction, and
     blind-clearance prohibition/exception handling as structured projection
     evidence;
   - fn-72 covers emergency assistance actors, assistance content policy,
     emergency frequency policy, intercepted-distress relay, and distress/
     urgency interference suppression as structured projection evidence;
   - fn-73 covers emergency message addressing, relayed distress-message
     variation, urgency-message payload selection, and urgency
     addressing/frequency policy as structured projection evidence;
   - expected-gap specs keep rendered emergency phraseology/order,
     any-means distress communication, emergency-descent specific-instruction
     necessity, controller-side lost-contact relay, ATC-originated blind non-
     clearance workflow, and Annex 10 conformance visibly blocked.
2. A chunk-level exact-union guard proves that source refs cited by chunk 08
   source specs exactly equal
   `ICAO9432.DistressUrgencyCommsFailure.Chunk08Items`.
3. Production radio queue preemption remains out of scope for fn-69, and
   production emergency-descent conflict resolution remains out of scope for
   fn-70; the moved rows are structured projection branches.

## Review Considerations

- FP / type safety: fn-68, fn-69, fn-70, fn-71, fn-72, and fn-73 use closed local
  source-unit projections over existing production concepts where possible, not
  new global evidence payloads. Future emergency support should introduce typed
  emergency/radio-failure concepts rather than overloading ordinary radio
  events.
- Test architecture: source-unit specs make `EMERGENCY-1` concrete and
  auditable. They are not broad skip lists, and an exact-union guard ensures no
  grouped spec silently omits a source unit.
- Impact: this chunk creates a clear future implementation boundary for
  residual emergency modelling without changing current normal-flight behaviour,
  production communications-failure handling, or production radio scheduling.
- Operational correctness: ICAO 9432 Chapter 9 emergency traffic and aircraft
  communications failure are distinct from ordinary VFR, go-around, and
  controller sequencing behaviour.
