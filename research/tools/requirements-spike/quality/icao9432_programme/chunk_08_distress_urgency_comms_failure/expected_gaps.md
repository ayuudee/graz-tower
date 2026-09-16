# Chunk 08 Expected Gaps

Chunk: `chunk-08-distress-urgency-comms-failure`

This file records ICAO 9432 Chapter 9 source units that cannot honestly be
marked covered-green with the current model/evidence surface.

## Summary

| Source-unit state | Units |
|---|---:|
| `model-gap` | 22 |
| `model-gap` + `policy-blocked` | 15 |
| `model-gap` + `phraseology-later` | 9 |
| `phraseology-later` | 0 |

No chunk 08 source unit is covered-green in this pass. `EMERGENCY-1` remains
the dominant blocker; all phraseology rows also need emergency state/payload
surface before they can be honestly tested.

## Model Gaps

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::distress_urgency_intro_9_1_en::8b3b3b4117c04807` | `EMERGENCY-1` | No typed distress condition or immediate-assistance requirement. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::c959f0325390e7fe` | `EMERGENCY-1` | No typed urgency condition distinct from distress. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::3b1079aa56df2ce6` | `EMERGENCY-1` | No distress priority class or emergency radio arbitration. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::1a20cd48e58a5693` | `EMERGENCY-1` | No urgency priority class below distress and above routine traffic. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::7d35c042421b5b03` | `EMERGENCY-1` | No frequency silence state while emergency traffic is active. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::9c34a1b8d6d623fa` | `EMERGENCY-1` | No other-station/other-aircraft emergency assistance actor. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::2fee92c222323e6a` | `EMERGENCY-1` | No alternate emergency frequency selection model. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::c30159856a1a5e7a` | `EMERGENCY-1` | No Annex 10 emergency-procedure conformance model. |
| `icao9432-extracted::distress_messages_9_2_en::4b37e039e7eb8afa` | `EMERGENCY-1` | No distress assistance model or SSR 7700 emergency state. |
| `icao9432-extracted::distress_messages_9_2_en::e2902de496f43a95` | `EMERGENCY-1` | No distress-traffic silence-imposition model. |
| `icao9432-extracted::distress_messages_9_2_en::ed898005cd1a4da5` | `EMERGENCY-1` | No per-aircraft silence obligation and distress-traffic termination state. |
| `icao9432-extracted::distress_messages_9_2_en::c20024dad1b7144e` | `EMERGENCY-1` | No distress-ended state or silence termination workflow. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::082f9668292ed82c` | `EMERGENCY-1` | No emergency descent event, affected traffic set, or safeguard action model. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::ca0c243491ff5d13` | `EMERGENCY-1` | No emergency position-uncertainty or position-questioning model. |
| `icao9432-extracted::communications_failure_9_5_en::f05016444e2b8808` | `EMERGENCY-1` | No communications-failure frequency-search model. |
| `icao9432-extracted::communications_failure_9_5_en::fcb3a49672165b4f` | `EMERGENCY-1` | No route-based alternate station/aircraft contact model. |
| `icao9432-extracted::communications_failure_9_5_en::975a63151706f68f` | `EMERGENCY-1` | No blind-message repetition scheduler. |
| `icao9432-extracted::communications_failure_9_5_en::045c2e33f59f5ede` | `EMERGENCY-1` | No scheduled blind-report timing state. |
| `icao9432-extracted::communications_failure_9_5_en::7900c606e05e509b` | `EMERGENCY-1` | No communications-failure continuation-intention payload. |
| `icao9432-extracted::communications_failure_9_5_en::91e7d233bf3b64ff` | `EMERGENCY-1` | No radio-failure SSR 7600 state. |
| `icao9432-extracted::communications_failure_9_5_en::b73dda299970c2f3` | `EMERGENCY-1` | No blind-clearance prohibition and originator-request exception model. |
| `icao9432-extracted::communications_failure_9_5_en::c1c14fab53a608c6` | `EMERGENCY-1` | No Annex 10 communications-failure conformance model. |

## Model Gaps With Policy

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::distress_urgency_intro_9_1_en::24f94381ed9e8ce1` | `EMERGENCY-1`; `OperationalGuidancePolicy` | No emergency frequency-continuity policy. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::82ee7048517d8478` | `EMERGENCY-1`; `OperationalGuidancePolicy` | No emergency advice/information/instruction assistance policy. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::87b67820c6092a98` | `EMERGENCY-1`; `OperationalGuidancePolicy` | No pilot safety-doubt trigger. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::8e9f7818b91d08c3` | `EMERGENCY-1`; `OperationalGuidancePolicy` | No intercepted-distress relay actor/state. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::cb12c2f9b97c64b7` | `EMERGENCY-1`; `OperationalGuidancePolicy` | No emergency initial-frequency policy. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::06f7a72397c325ac` | `EMERGENCY-1`; `OperationalGuidancePolicy` | No emergency distraction/superfluous-transmission suppression policy. |
| `icao9432-extracted::distress_messages_9_2_en::27a450fa3bfcbc0a` | `EMERGENCY-1`; `OperationalGuidancePolicy` | No distress-message addressing policy. |
| `icao9432-extracted::distress_messages_9_2_en::94e94be0c800c982` | `EMERGENCY-1`; `OperationalGuidancePolicy` | No relayed/non-distressed distress-message variant model. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::1de475a788206cc8` | `EMERGENCY-1`; `OperationalGuidancePolicy` | No urgency-message payload policy. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::b95d7bb1cb409bca` | `EMERGENCY-1`; `OperationalGuidancePolicy` | No urgency addressing/frequency policy. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::5df94af7a64c3f5d` | `EMERGENCY-1`; `OperationalGuidancePolicy` | No urgency-interference suppression policy. |
| `icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::c71568b00fb1535e` | `EMERGENCY-1`; `OperationalGuidancePolicy` | No emergency descent broadcast-plus-specific-instructions policy. |
| `icao9432-extracted::communications_failure_9_5_en::bb66a050093251c2` | `EMERGENCY-1`; `ControllerInterventionPolicy` | No lost-contact aircraft-assistance and relay workflow. |
| `icao9432-extracted::communications_failure_9_5_en::24c806b040f4ef5e` | `EMERGENCY-1`; `ControllerInterventionPolicy` | No lost-contact inter-station assistance and relay workflow. |
| `icao9432-extracted::communications_failure_9_5_en::75055714e70d4560` | `EMERGENCY-1`; `ClearanceTimingPolicy` | No controller blind-transmission policy or clearance exclusion. |

## Model Gaps With Phraseology

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::distress_urgency_intro_9_1_en::13d1c2accd0f7a73` | `EMERGENCY-1`; `PHRASE-1` | No speech-rate/distinctness or rendered emergency utterance evidence. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::9907744b4723d14c` | `EMERGENCY-1`; `PHRASE-1` | No emergency context/time-pressure phraseology adaptation model. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::bf04647e26f9c018` | `EMERGENCY-1`; `PHRASE-1` | No rendered initial emergency call with repeated MAYDAY/PAN PAN. |
| `icao9432-extracted::distress_urgency_intro_9_1_en::d742970b22d8de26` | `EMERGENCY-1`; `PHRASE-1` | No rendered MAYDAY/PAN PAN emergency classification. |
| `icao9432-extracted::distress_messages_9_2_en::23c9f447cd6c7814` | `EMERGENCY-1`; `PHRASE-1` | No emergency-message payload or rendered element ordering. |
| `icao9432-extracted::distress_messages_9_2_en::f0e99a4c08ea0cb3` | `EMERGENCY-1`; `PHRASE-1` | No rendered distress-message element order. |
| `icao9432-extracted::communications_failure_9_5_en::bc9bb12804033b07` | `EMERGENCY-1`; `PHRASE-1` | No blind-transmission mode or rendered TRANSMITTING BLIND prefix. |
| `icao9432-extracted::communications_failure_9_5_en::abbc376a430003b0` | `EMERGENCY-1`; `PHRASE-1` | No blind-transmission addressee policy/rendering. |
| `icao9432-extracted::communications_failure_9_5_en::78c73a75fab644f4` | `EMERGENCY-1`; `PHRASE-1` | No receiver-failure mode or rendered TRANSMITTING BLIND DUE RECEIVER FAILURE prefix. |
