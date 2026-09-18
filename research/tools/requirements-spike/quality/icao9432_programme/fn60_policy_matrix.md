# fn-60 POLICY-1 policy matrix

Status: corrected task-1 planning artifact for
`fn-60-icao-9432-policy-1-typed-policy`.

Source of truth: `implementation_blocker_manifest.csv` joined to
`classification.csv`. This matrix is overlap-preserving: if a row is blocked by
policy and also by `EMERGENCY-1`, `VEHICLE-1`, `PUSHBACK-1`, or `FN43-GAP-1`,
the larger blocker stays visible and the row is not moved by policy work alone.

Selection rule: include every row whose blocker list contains `POLICY-1` or a
named policy blocker, plus rows whose classification already names a policy
concept even when the blocker list is dominated by model/phraseology work. This
produces 49 auditable rows. One row (`4b103081585bfb71`) is not policy-blocked
in the blocker manifest, but is retained here as adjacent policy support so it
cannot disappear into an "untouched" bucket.

## Movement Manifest

Green-targeted in fn-60:

| Source unit | Policy owner | Configured branch | Required evidence | Wrong-path evidence | Not proven universally |
| --- | --- | --- | --- | --- | --- |
| `icao9432-extracted::taxi_4_4_en::417f64324f7495bf` | LOWG local operations | Departures normally taxi to the runway holding point before runway use. | Typed configured-policy binding for LOWG/RWY 16C plus live trace showing `TaxiToHoldingPoint` destination is a RWY 16C holding point and precedes runway-use permission. | A source test with no configured policy, or a policy branch allowing a non-holding-point limit, must not green this unit. | Other aerodromes may clear to another aerodrome position; this does not prove every taxi clearance limit is a holding point. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::19cfd36a9fce4587` | LOWG local operations | Separate GROUND/TOWER departures are transferred to TOWER at the holding point before runway use. | Typed configured-policy binding for LOWG separate GROUND/TOWER service plus live trace showing GROUND issues `ContactFrequency(role=TOWER)` while the aircraft is holding short at the RWY 16C holding point. | A source test with no configured policy, combined service, or transfer away from the holding point must not green this unit. | Other service shapes or workload conditions may transfer earlier/later; this does not prove a universal transfer point. |

Remain blocked in fn-60:

- Critical-phase radio silence remains covered-red + policy-blocked until
  transmissions can be classified as safety-essential versus routine.
- Route-clearance timing, readability scale, poor-visibility airborne reports,
  rejected take-off assistance, after-landing timing, go-around brevity, and
  tower-frequency retention remain policy-blocked until their scenario evidence
  is explicit enough to distinguish configured policy from universal law.
- Pushback, vehicle, essential-aerodrome-information, and emergency rows remain
  blocked by their model/evidence epics even when they also need policy types.

Untouched in fn-60:

- Rows whose primary blocker is `PUSHBACK-1`, `VEHICLE-1`, `FN43-GAP-1`, or
  `EMERGENCY-1`.
- Rows already classified as phraseology-later or model-gap where policy is
  adjacent support.
- Any source unit not listed in the green-targeted table.

Anti-overcoverage guard:

- A closed enum, configured default, or named policy value cannot move a source
  unit green.
- Green movement requires source-mapped evidence for the configured branch and
  explicit evidence that the configured branch was active.
- Policy defaults must be local/configured facts, not doctrine. A test may bind
  `LOWG uses branch X`; it must not assert `Doc 9432 requires branch X` unless
  the source unit actually says that.

## Full Policy Row Matrix

| # | Source unit | Pre-fn60 state | Policy owner | Allowed configured alternatives | Evidence needed | Wrong-path evidence | fn-60 disposition |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `communications_2_8_1_en::8b0487b183cd02cf` | `policy-blocked` | radio communications policy | general call expects no reply; individually addressed station replies | general-call/addressee evidence and response absence/presence | individual-address reply treated as general-call violation | remain blocked |
| 2 | `readback_2_8_3_en::36e6ad16cffe8726` | `policy-blocked` | clearance timing policy | route clearance before start-up; defer when not possible | before-start-up/taxi timing and "possible" condition | route clearance issued later without impossibility evidence | remain blocked |
| 3 | `aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4` | `covered-red + policy-blocked` | critical-phase transmission policy | routine silence; safety-essential exception | phase windows plus safety-necessity classification | routine transmission inside critical phase | remain blocked |
| 4 | `test_procedures_2_8_4_en::daa4fadde3c06a1f` | `policy-blocked` | readability assessment policy | readability scale 1..5 with station assessment | radio-check/readability report payload | free-text or out-of-scale readability | remain blocked |
| 5 | `pushback_powerback_4_3_en::5980a8f786170b01` | `model-gap + policy-blocked` | local apron/pushback policy | ATC-managed pushback; apron-managed pushback | pushback actor, request, approval authority | approval by wrong authority for configured local procedure | untouched: `PUSHBACK-1` dominates |
| 6 | `taxi_4_4_en::03985c8e2cf3f473` | `policy-blocked` | local taxi routing policy | holding point limit; other aerodrome position limit | taxi clearance limit and local procedure branch | non-holding limit greened under holding-point branch | remain blocked |
| 7 | `taxi_4_4_en::417f64324f7495bf` | `policy-blocked with scenario evidence` | LOWG local operations | normally holding point; alternate local limit | configured LOWG holding-point branch plus live taxi trace | green without policy binding or with alternate branch | green-targeted |
| 8 | `takeoff_procedures_4_5_1_to_4_5_5_en::19cfd36a9fce4587` | `policy-blocked with scenario evidence` | LOWG local operations | transfer at/approaching holding point; other transfer point | configured LOWG separate-function branch plus live transfer trace | green without policy binding or wrong service shape | green-targeted |
| 9 | `takeoff_procedures_4_5_1_to_4_5_5_en::4e0bacdd1c2c06e0` | `policy-blocked` | critical-phase transmission policy | no routine takeoff-process transmissions; emergency exception | takeoff-process window plus emergency/safety classification | routine transmission during takeoff process | remain blocked |
| 10 | `takeoff_procedures_4_5_6_to_4_5_7_en::2660849403bff7de` | `policy-blocked` | conditional-clearance policy | include arriving-aircraft identity; do not issue conditional clearance | dual-traffic sighting and condition text | conditional clearance without identifiable arriving aircraft | remain blocked |
| 11 | `takeoff_procedures_4_5_6_to_4_5_7_en::c386a5865bdd7876` | `policy-blocked` | conditional-clearance policy | type reference sufficient; additional descriptors required | traffic-identification policy and phraseology evidence | type-only reference where policy requires more | remain blocked |
| 12 | `takeoff_procedures_4_5_6_to_4_5_7_en::dd301daf2b69fe83` | `policy-blocked` | visibility/reporting policy | request airborne report in poor visibility; omit otherwise | visibility state and airborne-report request | airborne-report request under no poor-visibility branch | remain blocked |
| 13 | `takeoff_procedures_4_5_8_to_4_5_12_en::0afe0064c4c933af` | `policy-blocked` | rejected-takeoff reporting policy | pilot reports rejected takeoff as soon as practicable | rejected-takeoff event and pilot report timing | rejected-takeoff event with no report | remain blocked |
| 14 | `takeoff_procedures_4_5_8_to_4_5_12_en::2b7c45264775e3e2` | `policy-blocked` | controller intervention policy | assistance/taxi instructions requested/provided when needed | rejected-takeoff aftermath and assistance/taxi request | assistance asserted without need/request evidence | remain blocked |
| 15 | `takeoff_procedures_4_5_8_to_4_5_12_en::2cc8caf62c15688b` | `policy-blocked` | departure-instruction policy | departure instructions with takeoff clearance; separate departure instruction | takeoff clearance with separation-driven departure instruction | departure instruction implied where none issued | remain blocked |
| 16 | `takeoff_procedures_4_5_8_to_4_5_12_en::6bee6c63069d8250` | `policy-blocked` | controller intervention policy | cancel takeoff clearance; no cancellation needed | unexpected traffic/obstruction trigger and cancellation | cancellation asserted without trigger | remain blocked |
| 17 | `takeoff_procedures_4_5_8_to_4_5_12_en::81490161201eb712` | `policy-blocked` | runway-priority intervention policy | vacate/free runway; no intervention | landing-traffic urgency and runway occupancy evidence | runway-freeing instruction without conflicting landing traffic | remain blocked |
| 18 | `aerodrome_traffic_circuit_4_6_part1_en::667985b4a6159d18` | `policy-blocked` | joining-request timing policy | request early enough for planned entry; delayed request accepted/rejected | ETA/traffic/circuit entry timing | join-request sufficiency asserted without traffic context | remain blocked |
| 19 | `aerodrome_traffic_circuit_4_6_part1_en::d65650486b4d1b8f` | `policy-blocked` | local circuit-join policy | join downwind/base/final/other local route | arrival direction, traffic, configured circuit join | universal join route claimed from one procedure | remain blocked |
| 20 | `aerodrome_traffic_circuit_4_6_part2_en::28bea79b8da559cd` | `policy-blocked` | controller sequencing policy | delay orbit/extend; no delay needed | traffic sequencing need and delay instruction | delay instruction asserted without traffic need | remain blocked |
| 21 | `aerodrome_traffic_circuit_4_6_part2_en::34445db09fdd6e0a` | `policy-blocked` | local reporting policy | report points required by local procedure; no extra local reports | local reporting requirements and position reports | missing report under configured required point | remain blocked |
| 22 | `aerodrome_traffic_circuit_4_6_part2_en::b64030acf6ef4bbd` | `policy-blocked` | local straight-in policy | straight-in permitted; circuit join required | traffic situation, arrival direction, local straight-in branch | straight-in treated as universal entitlement | remain blocked |
| 23 | `aerodrome_traffic_circuit_4_6_part2_en::dcf776a1b9c8a303` | `policy-blocked` | local reporting/controller policy | routine reports per local procedure; controller-specific reporting instructions | joined-circuit state and required report points | report obligation asserted without configured local requirement | remain blocked |
| 24 | `after_landing_4_9_en::4a512226eec962cb` | `policy-blocked` | frequency-retention policy | remain on tower until vacated; alternate advice issued | runway-vacated timing and absence/presence of advice | retention asserted despite alternate frequency advice | remain blocked |
| 25 | `after_landing_4_9_en::5d742dc66caa1790` | `policy-blocked` | taxi-instruction timing policy | wait until landing roll complete; absolute-necessity exception | landing-roll completion and necessity classification | taxi instruction during landing roll without necessity | remain blocked |
| 26 | `essential_aerodrome_information_4_10_en::1306eb5cc586df34` | `model-gap + policy-blocked` | essential-information omission policy | omit already-known info; transmit not-known info | aircraft-known-information state and information source | omission greened without already-known evidence | untouched: `FN43-GAP-1` dominates |
| 27 | `essential_aerodrome_information_4_10_en::1aa5cb7e758055bc` | `model-gap + policy-blocked` | essential-information timing policy | pass before start-up/taxi/final where possible; defer if impossible | hazard/info payload, recipient knowledge, timing window | late info without impossibility evidence | untouched: `FN43-GAP-1` dominates |
| 28 | `essential_aerodrome_information_4_10_en::c92651071a9c009e` | `model-gap + policy-blocked` | essential-information relevance policy | open pertinent-information category included; irrelevant info excluded | typed pertinent-info category and relevance branch | arbitrary info treated as essential | untouched: `FN43-GAP-1` dominates |
| 29 | `go_around_4_8_en::6c8993a0519d5d64` | `policy-blocked` | go-around radio-load policy | brief/minimum transmissions; additional safety-essential calls | radio-load/brevity metric and go-around phase | verbose routine transmissions treated as compliant | remain blocked |
| 30 | `aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::0b45b4a4dc2acc0c` | `model-gap + policy-blocked` | vehicle intervention policy | inform/stop vehicle in danger; no intervention needed | vehicle movement-area occupancy and danger relation | stop/inform asserted without dangerous situation | untouched: `VEHICLE-1` dominates |
| 31 | `aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::29be4b26bb851605` | `model-gap + policy-blocked` | towing coordination policy | tow driver states tow details; receiving station already aware/confirmed | tow request actor and station knowledge state | station awareness assumed without evidence | untouched: `VEHICLE-1` dominates |
| 32 | `aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::4b103081585bfb71` | `model-gap + phraseology-later` | towing identification policy | include type/operator; local omission not modelled | towing metadata and rendered phraseology | vehicle phraseology greened without towing actor | untouched: adjacent support, `VEHICLE-1`/`PHRASE-1` dominate |
| 33 | `aerodrome_vehicles_intro_movement_5_1_to_5_2_en::122426242abf3225` | `model-gap + policy-blocked` | vehicle vigilance/local-procedure policy | comply with local procedure and ATC instructions; no aircraft-proximity operation | vehicle actor, proximity, local procedure, ATC instruction | compliance asserted without vehicle/proximity model | untouched: `VEHICLE-1` dominates |
| 34 | `aerodrome_vehicles_intro_movement_5_1_to_5_2_en::606d053954eff037` | `model-gap + policy-blocked` | apron vehicle instruction policy | apron proceed permission includes traffic instructions; plain proceed | apron vehicle permission and other-traffic relation | traffic instruction obligation asserted without traffic | untouched: `VEHICLE-1` dominates |
| 35 | `communications_failure_9_5_en::24c806b040f4ef5e` | `model-gap + policy-blocked` | communications-failure intervention policy | attempt other frequencies/routes; no attempt possible | failed-contact state and alternate-route attempts | failure routing asserted without failed-contact evidence | untouched: `EMERGENCY-1` dominates |
| 36 | `communications_failure_9_5_en::75055714e70d4560` | `model-gap + policy-blocked` | blind-transmission timing policy | transmit blind after failed attempts; do not transmit before threshold | failed attempts and scheduled blind transmission | blind transmission before configured threshold | untouched: `EMERGENCY-1` dominates |
| 37 | `communications_failure_9_5_en::bb66a050093251c2` | `model-gap + policy-blocked` | communications-failure routing policy | use other stations/aircraft to relay; no relay available | failed contact plus relay candidate evidence | relay assertion without candidate station/aircraft | untouched: `EMERGENCY-1` dominates |
| 38 | `distress_messages_9_2_en::27a450fa3bfcbc0a` | `model-gap + policy-blocked` | distress addressee policy | address station communicating with aircraft; area-responsible station | distress message, current station, area responsibility | addressee branch asserted without responsibility evidence | untouched: `EMERGENCY-1` dominates |
| 39 | `distress_messages_9_2_en::94e94be0c800c982` | `model-gap + policy-blocked` | distress relay variation policy | standard distress elements; relay/non-distressed variation | distressed/non-distressed transmitter and message elements | variation greened without circumstance statement | untouched: `EMERGENCY-1` dominates |
| 40 | `distress_urgency_intro_9_1_en::06f7a72397c325ac` | `model-gap + policy-blocked` | emergency radio-discipline policy | suppress superfluous traffic; allow safety-essential assistance | emergency traffic state and transmission relevance | superfluous transmission during distress/urgency | untouched: `EMERGENCY-1` dominates |
| 41 | `distress_urgency_intro_9_1_en::24f94381ed9e8ce1` | `model-gap + policy-blocked` | distress frequency-continuity policy | stay on current frequency; change when better assistance available | distress comms lifecycle and assistance-quality branch | frequency change without better-assistance evidence | untouched: `EMERGENCY-1` dominates |
| 42 | `distress_urgency_intro_9_1_en::82ee7048517d8478` | `model-gap + policy-blocked` | emergency assistance policy | provide necessary advice/information/instructions; withhold irrelevant info | emergency need and advice/instruction payload | assistance asserted without need classification | untouched: `EMERGENCY-1` dominates |
| 43 | `distress_urgency_intro_9_1_en::87b67820c6092a98` | `model-gap + policy-blocked` | pilot emergency-declaration policy | seek assistance when doubtful safety; continue normal when safe | pilot safety doubt/emergency trigger | assistance-seeking asserted without safety doubt | untouched: `EMERGENCY-1` dominates |
| 44 | `distress_urgency_intro_9_1_en::8e9f7818b91d08c3` | `model-gap + policy-blocked` | distress relay policy | intercepting aircraft acknowledges/relays when appropriate; no relay when inappropriate | intercepted distress, no acknowledgement, time/circumstance suitability | relay asserted despite acknowledged distress or unsuitable circumstances | untouched: `EMERGENCY-1` dominates |
| 45 | `distress_urgency_intro_9_1_en::cb12c2f9b97c64b7` | `model-gap + policy-blocked` | emergency call frequency policy | call on frequency in use; other frequency if circumstances require | frequency-in-use and emergency call payload | current-frequency rule asserted without call model | untouched: `EMERGENCY-1` dominates |
| 46 | `urgency_emergency_descent_9_3_to_9_4_en::1de475a788206cc8` | `model-gap + policy-blocked` | urgency message element policy | include required circumstance elements; omit irrelevant elements | urgency context and message element selection | missing required circumstance element | untouched: `EMERGENCY-1` dominates |
| 47 | `urgency_emergency_descent_9_3_to_9_4_en::5df94af7a64c3f5d` | `model-gap + policy-blocked` | urgency traffic-interference policy | other stations avoid interference; safety-essential exception | urgency traffic state and other-station transmission classification | interfering transmission without exception | untouched: `EMERGENCY-1` dominates |
| 48 | `urgency_emergency_descent_9_3_to_9_4_en::b95d7bb1cb409bca` | `model-gap + policy-blocked` | urgency addressee/frequency policy | call station in communication/area responsibility; other station when appropriate | current station, responsibility area, urgency call | addressee asserted without responsibility evidence | untouched: `EMERGENCY-1` dominates |
| 49 | `urgency_emergency_descent_9_3_to_9_4_en::c71568b00fb1535e` | `model-gap + policy-blocked` | emergency-descent instruction policy | broadcast warning then specific instructions as necessary; warning only | emergency-descent broadcast and necessity for follow-up instruction | follow-up omitted/issued without necessity evidence | untouched: `EMERGENCY-1` dominates |

## Review Considerations

FP / type safety:

- Policy branches must be closed types. Do not use strings for configured
  branch names in source-mapped test assertions.
- No catch-all default may stand in for doctrine. If a policy value is added,
  selectors and evidence assertions must handle it explicitly.
- This task adds no production state. If later tasks add state, every copy site
  and reversal path must be audited before implementation.

Test architecture:

- The tests should stay high-level and source-mapped. The configured policy is
  part of the evidence, not the thing being tested by itself.
- A passing test for rows 7 and 8 must prove both the configured policy binding
  and the live LOWG behavior under that binding.
- Remaining rows should continue to fail loudly as expected gaps or blocked
  coverage; no skip list may hide them.

Impact:

- This narrows fn-60 to a policy evidence substrate plus two already-observed
  scenario branches.
- It makes later policy rows easier to add because they must declare owner,
  alternatives, evidence, wrong-path evidence, and non-universal scope before
  moving.
- Failure mode: using configured policy as a universal claim. The movement
  manifest and anti-overcoverage guard are the review tripwire.

Operational correctness:

- Rows 7 and 8 trace to ICAO Doc 9432 §4.4 and §4.5.1 source units extracted in
  the registry. Both are discretionary/normal-practice statements, so the
  evidence can only claim LOWG configured behavior.
- Rows involving emergency, vehicle, pushback, critical-phase transmissions, or
  essential aerodrome information require their own domain evidence before any
  operational claim can be made.
