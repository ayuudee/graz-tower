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
They do not model distress/urgency conditions, emergency traffic priority,
emergency radio silence, assistance/relay actors, emergency descent conflict
safeguarding, communications-failure mode, blind transmissions, scheduled blind
reports, or SSR emergency/failure code selection. Ordinary radio or VFR
scenario traces must not be reused as emergency-compliance evidence.

## Planned Coverage

| Source unit | Source text / claim | Initial classifier | Planned state | Planned evidence / blocker |
|---|---|---|---|---|
| `icao9432-extracted::distress_urgency_intro_9_1_en::8b3b3b4117c04807` | Distress is serious/imminent danger requiring immediate assistance. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no typed distress condition or assistance-requirement state. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::c959f0325390e7fe` | Urgency concerns safety but does not require immediate assistance. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no typed urgency condition distinct from distress. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::3b1079aa56df2ce6` | Distress messages have priority over all other transmissions. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no emergency priority class or radio arbitration. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::1a20cd48e58a5693` | Urgency messages have priority except over distress. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no urgency priority below distress and above routine traffic. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::7d35c042421b5b03` | Stations shall refrain from using a frequency carrying emergency traffic unless involved. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no emergency-traffic frequency silence state. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::9c34a1b8d6d623fa` | If called ground station does not reply, another station/aircraft shall reply and assist. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no non-addressed station/aircraft assistance actor. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::24f94381ed9e8ce1` | Distress communications normally continue on current frequency until a better frequency helps. | `needs-sim-model` | `model-gap` + `policy-blocked` | `EMERGENCY-1`; `OperationalGuidancePolicy`; no emergency frequency-continuity policy. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::2fee92c222323e6a` | Other communication frequencies may be used if necessary or desirable. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no alternate emergency frequency selection model. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::82ee7048517d8478` | Replying station should provide advice, information, and instructions needed to assist. | `needs-sim-model` | `model-gap` + `policy-blocked` | `EMERGENCY-1`; `OperationalGuidancePolicy`; no emergency assistance policy/actions. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::87b67820c6092a98` | Pilots should seek assistance when flight safety is in doubt. | `needs-sim-model` | `model-gap` + `policy-blocked` | `EMERGENCY-1`; `OperationalGuidancePolicy`; no pilot safety-doubt trigger. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::8e9f7818b91d08c3` | Intercepting aircraft may acknowledge and broadcast unacknowledged distress. | `needs-sim-model` | `model-gap` + `policy-blocked` | `EMERGENCY-1`; `OperationalGuidancePolicy`; no intercepted distress relay actor/state. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::cb12c2f9b97c64b7` | Distress/urgency call normally uses the frequency in use. | `needs-sim-model` | `model-gap` + `policy-blocked` | `EMERGENCY-1`; `OperationalGuidancePolicy`; no emergency initial-frequency policy. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::06f7a72397c325ac` | Superfluous transmissions may distract an already busy pilot. | `needs-sim-model` | `model-gap` + `policy-blocked` | `EMERGENCY-1`; `OperationalGuidancePolicy`; no emergency distraction/suppression policy. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::13d1c2accd0f7a73` | Pilots should speak slowly and distinctly to avoid repetition. | `needs-sim-model` | `model-gap` + `phraseology-later` | `EMERGENCY-1`; `PHRASE-1`; no speech-rate/distinctness or rendered emergency utterance evidence. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::c30159856a1a5e7a` | Distress and urgency procedures are detailed in Annex 10 Volume II. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no Annex 10 emergency-procedure conformance model. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::9907744b4723d14c` | Pilots should adapt Chapter 9 phraseology to needs/time available. | `phraseology-later` | `model-gap` + `phraseology-later` | `EMERGENCY-1`; `PHRASE-1`; no emergency context/time-pressure phraseology adaptation model. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::bf04647e26f9c018` | MAYDAY or PAN PAN should preferably be spoken three times initially. | `phraseology-later` | `model-gap` + `phraseology-later` | `EMERGENCY-1`; `PHRASE-1`; no rendered initial emergency call. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::d742970b22d8de26` | MAYDAY identifies distress; PAN PAN identifies urgency. | `phraseology-later` | `model-gap` + `phraseology-later` | `EMERGENCY-1`; `PHRASE-1`; no rendered MAYDAY/PAN PAN emergency classification. |
| `icao9432-extracted::distress_messages_9_2_en::23c9f447cd6c7814` | Distress message should contain station addressed, aircraft ID, distress nature, intentions, position, level, heading, and useful information. | `phraseology-later` | `model-gap` + `phraseology-later` | `EMERGENCY-1`; `PHRASE-1`; no emergency-message payload or rendered ordering. |
| `icao9432-extracted::distress_messages_9_2_en::f0e99a4c08ea0cb3` | Distress-message elements should be in the shown order if possible. | `phraseology-later` | `model-gap` + `phraseology-later` | `EMERGENCY-1`; `PHRASE-1`; no rendered distress-message ordering. |
| `icao9432-extracted::distress_messages_9_2_en::27a450fa3bfcbc0a` | Distress message normally addresses current station or responsible-area station. | `needs-sim-model` | `model-gap` + `policy-blocked` | `EMERGENCY-1`; `OperationalGuidancePolicy`; no emergency addressing policy. |
| `icao9432-extracted::distress_messages_9_2_en::94e94be0c800c982` | Non-distressed transmitting station may vary elements if circumstance is clear. | `needs-sim-model` | `model-gap` + `policy-blocked` | `EMERGENCY-1`; `OperationalGuidancePolicy`; no relayed/non-distressed distress-message variant model. |
| `icao9432-extracted::distress_messages_9_2_en::4b37e039e7eb8afa` | Aircraft in distress may use any means, including SSR 7700; stations may assist. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no distress assistance model or SSR 7700 state. |
| `icao9432-extracted::distress_messages_9_2_en::e2902de496f43a95` | Distress aircraft or station controlling distress traffic may impose silence. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no emergency silence-imposition model. |
| `icao9432-extracted::distress_messages_9_2_en::ed898005cd1a4da5` | Aircraft requested to maintain silence shall do so until distress traffic ends. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no per-aircraft silence obligation and termination state. |
| `icao9432-extracted::distress_messages_9_2_en::c20024dad1b7144e` | Ground station shall terminate distress communication/silence when distress ends. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no distress-ended state or silence termination. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::1de475a788206cc8` | Urgency message should contain required 9.2.1.1 elements as circumstances require. | `needs-sim-model` | `model-gap` + `policy-blocked` | `EMERGENCY-1`; `OperationalGuidancePolicy`; no urgency-message payload policy. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::b95d7bb1cb409bca` | Urgency call normally uses current frequency and current/responsible station. | `needs-sim-model` | `model-gap` + `policy-blocked` | `EMERGENCY-1`; `OperationalGuidancePolicy`; no urgency addressing/frequency policy. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::5df94af7a64c3f5d` | Other stations should avoid interfering with urgency traffic. | `needs-sim-model` | `model-gap` + `policy-blocked` | `EMERGENCY-1`; `OperationalGuidancePolicy`; no urgency-interference suppression policy. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::082f9668292ed82c` | On emergency descent announcement, controller takes all possible action to safeguard other aircraft. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no emergency descent event, affected traffic set, or safeguard action model. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::c71568b00fb1535e` | Emergency descent broadcast should be followed by specific instructions as necessary. | `needs-sim-model` | `model-gap` + `policy-blocked` | `EMERGENCY-1`; `OperationalGuidancePolicy`; no broadcast-plus-specific-instructions workflow. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::ca0c243491ff5d13` | Further questions may be asked to help ascertain aircraft position. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no emergency position-uncertainty/questioning model. |
| `icao9432-extracted::communications_failure_9_5_en::f05016444e2b8808` | If contact fails on designated frequency, try another route-appropriate frequency. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no communications-failure frequency-search model. |
| `icao9432-extracted::communications_failure_9_5_en::fcb3a49672165b4f` | If that fails, try other aircraft or stations on route-appropriate frequencies. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no route-based alternate station/aircraft contact model. |
| `icao9432-extracted::communications_failure_9_5_en::bc9bb12804033b07` | If attempts fail, transmit message twice, preceded by TRANSMITTING BLIND. | `phraseology-later` | `model-gap` + `phraseology-later` | `EMERGENCY-1`; `PHRASE-1`; no blind-transmission mode or rendered prefix. |
| `icao9432-extracted::communications_failure_9_5_en::abbc376a430003b0` | Blind transmission may include addressee(s) if necessary. | `phraseology-later` | `model-gap` + `phraseology-later` | `EMERGENCY-1`; `PHRASE-1`; no blind-transmission addressee policy/rendering. |
| `icao9432-extracted::communications_failure_9_5_en::975a63151706f68f` | Aircraft shall transmit intended message followed by complete repetition. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no blind-message repetition scheduler. |
| `icao9432-extracted::communications_failure_9_5_en::045c2e33f59f5ede` | Aircraft shall advise time of next intended transmission. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no scheduled blind-report timing state. |
| `icao9432-extracted::communications_failure_9_5_en::7900c606e05e509b` | ATC/advisory aircraft shall transmit PIC intentions for flight continuation. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no comms-failure continuation-intention payload. |
| `icao9432-extracted::communications_failure_9_5_en::78c73a75fab644f4` | Receiver failure reports use TRANSMITTING BLIND DUE RECEIVER FAILURE. | `phraseology-later` | `model-gap` + `phraseology-later` | `EMERGENCY-1`; `PHRASE-1`; no receiver-failure mode or rendered prefix. |
| `icao9432-extracted::communications_failure_9_5_en::91e7d233bf3b64ff` | Airborne equipment failure should select SSR code 7600 when equipped. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no radio-failure SSR 7600 state. |
| `icao9432-extracted::communications_failure_9_5_en::bb66a050093251c2` | Station unable to contact aircraft shall ask route aircraft to call/relay. | `needs-sim-model` | `model-gap` + `policy-blocked` | `EMERGENCY-1`; `ControllerInterventionPolicy`; no lost-contact assistance/relay workflow. |
| `icao9432-extracted::communications_failure_9_5_en::24c806b040f4ef5e` | Station unable to contact aircraft shall ask other stations to call/relay. | `needs-sim-model` | `model-gap` + `policy-blocked` | `EMERGENCY-1`; `ControllerInterventionPolicy`; no inter-station lost-contact relay workflow. |
| `icao9432-extracted::communications_failure_9_5_en::75055714e70d4560` | If station attempts fail, non-clearance messages may be blind-transmitted. | `needs-sim-model` | `model-gap` + `policy-blocked` | `EMERGENCY-1`; `ClearanceTimingPolicy`; no controller blind-transmission policy or clearance exclusion. |
| `icao9432-extracted::communications_failure_9_5_en::b73dda299970c2f3` | Blind ATC clearances shall not be made except at originator request. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no blind-clearance prohibition/request exception model. |
| `icao9432-extracted::communications_failure_9_5_en::c1c14fab53a608c6` | General communications-failure rules are in Annex 10 Volume II. | `needs-sim-model` | `model-gap` | `EMERGENCY-1`; no Annex 10 communications-failure conformance model. |

## Planned Tests

1. Add source-specific expected-gap specs grouped by missing model surface:
   - distress/urgency classification;
   - emergency priority and emergency-frequency silence;
   - emergency message payload, addressing, and rendered emergency phraseology;
   - emergency assistance, relay, silence imposition, and silence termination;
   - emergency descent safeguarding and urgency traffic interference;
   - communications-failure frequency search and route-based contact routing;
   - blind transmission, repetition, scheduled reports, receiver-failure
     phraseology, and blind addressee handling;
   - SSR 7600/7700 and blind-clearance prohibition/exception behaviour.
2. Add a chunk-level exact-union guard proving that the source refs cited by
   chunk 08 gap specs exactly equal
   `ICAO9432.DistressUrgencyCommsFailure.Chunk08Items`.
3. Do not add covered-green scenario tests. There is no current emergency or
   communications-failure trace to prove these source units.

## Review Considerations

- FP / type safety: no production state changes are planned. Future emergency
  support should introduce typed emergency/radio-failure/priority concepts
  rather than overloading ordinary radio events. Chunk 08 catalog refs should
  use `ProjectionGapSource` to avoid implying covered scenario evidence.
- Test architecture: source-unit gap specs should make `EMERGENCY-1` concrete
  and auditable. They should not be broad skip lists, and an exact-union guard
  should ensure no grouped spec silently omits a source unit.
- Impact: this chunk creates a clear future implementation boundary for
  emergency modelling without changing current normal-flight behaviour.
- Operational correctness: ICAO 9432 Chapter 9 emergency traffic and aircraft
  communications failure are distinct from ordinary VFR, go-around, and
  controller sequencing behaviour.
