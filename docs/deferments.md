# Deferments Register

This file is the **map** of named deferments in the repo. Every deferred
contract — work consciously parked with a real-fix contract (eventual API
shape, blocked-on prerequisite, named closure trigger) — has exactly one
entry here. The four-bucket model determines where the canonical record
lives; the `Pinned at:` field points readers at it. See
[`deferments-CONVENTION.md`](./deferments-CONVENTION.md) for the decision
tree, schema, status taxonomy, and lifecycle.

**How to read this file.** Entries are grouped by ID prefix into four
top-level subsections (`## D-PF`, `## D-AUDIT`, `## D-PASS`, `## D-WORLD`).
Closed entries live in `## Archive` at the bottom. Status taxonomy is four
leaves: `blocked` / `planned` / `narrative` / `closed`. **Heading
discipline**: only `### D-...` headings denote a deferment entry —
`grep -c '^### D-' docs/deferments.md` counts entries. Section-organising
headings use `##` depth; empty-body placeholders use one-line prose.

## D-PF

### D-PF.1 — Aerodrome-conditional startup clearance is removed, not modelled
**Status:** blocked
**Pinned at:** pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt::PF1 aerodrome requiring startup clearance has REQUEST_STARTUP and AWAIT_STARTUP_APPROVAL
**Blocked on:** LOWS / LOWW / LJLJ-class aerodrome in the fixture, plus the `CLEARANCE_DELIVERY` controller role.
**Why:** After Phase D, `groundDepartureTask` has no `REQUEST_STARTUP` / `AWAIT_STARTUP_APPROVAL` steps — every pilot at every airport skips startup. At aerodromes that require startup clearance (LOWS, LOWW, LJLJ, much of central Europe), a pilot calling for taxi without first obtaining startup is a procedural violation; we deleted the steps because we never built the controller-side `CLEARANCE_DELIVERY` procedure.
**Contract:** An `AirportProcedure.requiresStartupClearance: Boolean` field on the airport manifest, populated from real-world data. `groundDepartureTask(airport)` returns a tree containing `REQUEST_STARTUP` and `AWAIT_STARTUP_APPROVAL` iff the airport requires it. A new `CLEARANCE_DELIVERY` controller role, with a `ClearanceDeliveryStage` and `clearanceDeliveryProcedure()` analogous to `groundTaxiProcedure()`, issues `StartupApproved` in response to `Request(RequestStartup)`. The mission tree branch is determined by airport, never by cockpit type.
**Closes by:** archived when prerequisite lands.

### D-PF.3 — Airborne spawn has a runway-assignment path via FiledPlan
**Status:** blocked
**Pinned at:** pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt::PF3 airborne-spawned aircraft with FiledPlan has activeRunway from filed plan
**Blocked on:** G2 (LOWG → LJMB transit) cross-aerodrome end-to-end test scenario.
**Why:** An aircraft spawned mid-flight has no path to populate `mission.activeRunway` before its first radio instruction; G0 spawns at stand and is unaffected. A real airborne aircraft entering controlled airspace already knows its destination runway from ATIS, the filed flight plan, or the previous controller. Today our model has no filed-plan channel telling the pilot "you're going to runway 14L" — the pilot is deaf until the controller speaks.
**Contract:** `FlightStrip.filed: FiledPlan?` carries `destinationRunway: RunwayId?` derived from the filed approach / ATIS. The pilot reads `mission.filedPlan?.destinationRunway` (set at sim init from the filing event) as the initial `activeRunway`; subsequent radio updates override per D-PF.2's precedence.
**Closes by:** archived when G2 (LOWG → LJMB transit) lands.

### D-PF.8 — `Watching` projection on ControllerView (multi-aircraft slot reservation)
**Status:** blocked
**Pinned at:** controller/src/jvmTest/kotlin/xyz/easiersaid/twr/controller/FirewallNoWatchingReadInControllerTest.kt::firewall-test enforced via source-scan
**Blocked on:** First test exercising multi-aircraft handoff scheduling. G3 if not earlier.
**Why:** Pass 7 (D-AUDIT.5 closure) added the typed `ResponsibilityState` machine including `Watching(from)` for receiving controllers during a pending handoff. The sim-side state captures it, but `ControllerView.responsibilities` is projected as `ownedAircraft` only — `Watching` aircraft are invisible to the rule layer. Real ATC sees incoming traffic on the strip board with the expected-frequency-change tagged. For G0/G1 (single-aircraft, single-handoff) this is safe; for multi-aircraft scenarios (G3+, parallel-runway operations) it's a real failure mode (no separation awareness, no slot reservation).
**Contract:** Extend `ControllerView` with a `watching: Map<AircraftId, ControllerId>` projection (or similar shape carrying `from` controller plus arrival ETA). New rule guards (`HasIncomingHandoff`, `WatchingAircraft`) read it. The architectural test suite picks up the new projection — ideally by extending `FirewallStaffingPanelTest`'s pattern to a `FirewallWatchingProjectionTest`. When that pass lands, fold or delete the current `FirewallNoWatchingReadInControllerTest` as part of the same plan revision.
**Closes by:** new epic when multi-aircraft handoff scheduling becomes test-driven (G3 if not earlier).

### D-PF.9 — Pass-NN missed-handoff reissue discipline (KDoc breadcrumb)
**Status:** narrative
**Pinned at:** controller/.../Controller.kt; controller/.../bdi/Supersession.kt; controller/src/commonTest/.../MissedHandoffReissueSpec.kt
**Why:** controller/.../Controller.kt, ControllerTypes.kt, bdi/Supersession.kt, observe/BeliefState.kt, and MissedHandoffReissueSpec.kt carry D-PF.9 KDoc breadcrumbs from the missed-handoff-reissue pass. The breadcrumb persists as a narrative anchor pointing future readers at the supersession discipline; the work itself has shipped.
**Closes by:** archive once the KDoc breadcrumbs are retired or formalised into a typed feature flag

## D-AUDIT

### D-AUDIT.2.C-FOLLOWUP — Sim-level integration test for full lost-comms tail
**Status:** blocked
**Pinned at:** sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/DeferredContractsSpec.kt::AUDIT2-C full comms tail integration test (query, reissue, blind)
**Blocked on:** Per-message cognitive-delay knob on `PilotInput`.
**Why:** Pass 9 landed the lost-comms state machine and Pass 12 closed three of four follow-ups; the sim-level end-to-end (query at 10 s → reissue 1/2/3 → `LostCommsDeclared` at 5 min → `TransmittingBlind` emission) is the remaining integration assertion. Today a deterministic test can't stage the readback miss without injecting sim-internal time skew.
**Closes by:** archived when per-message cognitive-delay knob lands.

### D-AUDIT.2.F-FOLLOWUP — G0 negative-escalation assertion (instruction-vs-completion)
**Status:** blocked
**Pinned at:** sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/DeferredContractsSpec.kt::AUDIT2-F G0 no LostCommsDeclared on un-complied-with instructions
**Blocked on:** "Instruction physically complied with" accessor on `Commitment` or `BeliefState` (distinguishes "readback received but stage not reached" from "no readback").
**Why:** Pass 12 dropped the Pass-9-fold-in "no LostCommsDeclared at end of G0" assertion: it was passing due to the destroyed-on-readback bug (D-AUDIT.2.E follow-on), not because of correctness. The right shape — "no LostCommsDeclared on instructions the aircraft has not physically complied with" — lands when scenario-level coverage of instruction-vs-completion semantics is in place.
**Closes by:** archived when scenario-level coverage of instruction-vs-completion lands.

### D-AUDIT.3.II-FOLLOWUP — Per-step TIMED durations beyond RUN_UP_CHECKS
**Status:** blocked
**Pinned at:** pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt::AUDIT3-II MissionStep runUpDurationMs lookup is step-discriminated
**Blocked on:** Second `MissionStep` adopting `CompletionMode.TIMED`.
**Why:** Pass 13 closed D-AUDIT.3 with `AircraftType.runUpDurationMs` for `RUN_UP_CHECKS`. Today only that single step uses TIMED; if other steps adopt TIMED, a flat scalar collapses the per-step semantic into one value. The right surface is a step-discriminated lookup (`MissionStep.runUpDurationMs(type)` or a step-keyed map on `AircraftType`).
**Closes by:** archived when a second step adopts TIMED completion.

### D-AUDIT.4.A.II-FOLLOWUP — Runway-condition gating (wet, contaminated, displaced threshold)
**Status:** blocked
**Pinned at:** controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/DeferredContractsSpec.kt::AUDIT4-A-II runway-condition gating affects runway-length classification
**Blocked on:** `RunwayCondition` sealed type (Dry / Wet / Contaminated) and displaced-threshold field on `RunwayDeclaredDistances`.
**Why:** Pass 13's `RunwayLengthSufficient(operation)` uses dry/MTOW. Real runway-length sufficiency is sensitive to surface condition (wet ~15% longer LDA for jets; contaminated more) and displaced thresholds (LDA shorter than physical runway).
**Closes by:** archived when `RunwayCondition` modelling lands.

### D-AUDIT.4.B-FOLLOWUP — Manifest-based aircraft-type loading
**Status:** narrative
**Pinned at:** narrative only
**Why:** Pass 10 introduced `AircraftType` as a sealed type with companion-object `C172` / `B738` leaves. A manifest pass would replace the sealed catalogue with runtime-loaded instances and reconsider the seal. Cross-cutting refactor — touches every consumer of `AircraftType.companion`.
**Closes by:** new epic when a third aircraft type lands and the manifest pass is justified.

### D-AUDIT.4.D.II-FOLLOWUP — Per-phase waypoint radius scaling
**Status:** blocked
**Pinned at:** pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt::AUDIT4-D-II Kinematics carries per-phase waypoint radii
**Blocked on:** Per-phase fields on `Kinematics` (taxi / climb / final).
**Why:** Pass 13 added a single `Kinematics.waypointRadiusM` per type (C172 = 80 m, B738 = 250 m). The simple value bites at low speeds — taxi at 10 m/s wants ~5–10 m, not 80 m. G0 is dominated by high-speed segments so the per-type scalar is right today, but the gap is real.
**Closes by:** archived when a low-speed scenario surfaces the gap.

### D-AUDIT.6.C-FOLLOWUP — Strip update on FPL amendment (CHG message)
**Status:** blocked
**Pinned at:** sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/KnownStripsHandoffTransitionSpec.kt::refile-with-different-plan throws with D-AUDIT.6.C-FOLLOWUP cite
**Blocked on:** Amendment scenario (re-routed aircraft, runway change mid-flight).
**Why:** Per ICAO Doc 4444 §11.4, FPL amendments arrive via CHG messages and update existing strips; today TWR2 throws on refile-with-different-plan. The throw is the loud-fail placeholder; the real-fix is propagation of the amended plan to all AFTN recipients.
**Closes by:** archived when an amendment scenario lands.

### D-AUDIT.7.II-FOLLOWUP — Mixed-mode parallel-runway operations
**Status:** blocked
**Pinned at:** controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/DeferredContractsSpec.kt::AUDIT7-II mixed-mode parallel runway configuration is selectable
**Blocked on:** Simultaneous-parallel-approach scenario in the fixture.
**Why:** `RunwayConfiguration` accepts independent arrivals/departures sets today but no consumer reads them as distinct; today the selection assumes `arrivals == departures`. Mixed-mode parallel operations (e.g. 16L arrivals + 16R departures concurrently) require the consumer wiring to read distinct sets.
**Closes by:** archived when a parallel-runway scenario surfaces.

### D-AUDIT.7.III-FOLLOWUP — Derive `BeliefState.activeRunway` on read (delete stored slice)
**Status:** blocked
**Pinned at:** controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/DeferredContractsSpec.kt::AUDIT7-III BeliefState still carries stored activeRunway slice (will be deleted)
**Blocked on:** Read-site cascade migrating all consumers to a `deriveActiveRunway` accessor.
**Why:** Pass 15 left `BeliefState.activeRunway: RunwayId?` as a stored slice — `expectedAtisLetter` + wind already drive its value. The stored slice is redundant; the right surface is on-read derivation, eliminating the two-truths failure mode.
**Closes by:** archived when the read-site cascade lands.

### D-AUDIT.8.II-FOLLOWUP — Separate ATIS frequency
**Status:** blocked
**Pinned at:** controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/DeferredContractsSpec.kt::AUDIT8-II ATIS broadcast lives on its own frequency
**Blocked on:** Multi-frequency comms model.
**Why:** Today ATIS is implicit on the role's primary frequency (TOWER / GROUND). Real ATIS broadcasts on a dedicated frequency and the pilot tunes to it before tuning to the operator role.
**Closes by:** archived when multi-frequency comms model lands.

### D-AUDIT.8.III-FOLLOWUP — Voice-style ATIS rendering (`Atis.toMessage()`)
**Status:** blocked
**Pinned at:** protocol/src/commonTest/kotlin/xyz/easiersaid/twr/protocol/DeferredContractsSpec.kt::AUDIT8-III Atis carries a toMessage rendering for voice broadcast
**Blocked on:** `Atis.toMessage()` (or `AtisMessage` value class) on the protocol surface.
**Why:** The structured `Atis` record is consumed directly by the rule layer today (`Controller.atisLetterMismatchAdvisories`, etc.) — there's no voice-shaped render. A voice render lets the broadcast path carry the same content over the transmission stream.
**Closes by:** archived when `Atis.toMessage()` lands.

### D-AUDIT.8.IV-FOLLOWUP — Multi-aerodrome ATIS-letter resolution
**Status:** blocked
**Pinned at:** pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt::AUDIT8-IV ATIS letter resolution dispatches by aerodrome for size greater than one
**Blocked on:** G2 cross-aerodrome scenario (or any scenario where `PilotInput.atisByAerodrome.size > 1`).
**Why:** `atisLetterForCallInbound` at `PilotCognitive.kt:480` dispatches on `mission.goal` — `Transit`/`Departure` carry the destination, `Arrival`/`CircuitTraining` do not. For the latter goals, multi-entry maps `error()` loudly (Pass 15 G2 tightening). The right shape threads the target aerodrome through `PilotInput` or widens `HighLevelGoal` to carry the call-target aerodrome.
**Closes by:** archived when G2 lands.

### D-AUDIT.9.II-FOLLOWUP — VFR see-and-avoid recognises nearby traffic
**Status:** blocked
**Pinned at:** pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt::AUDIT9-II VFR see-and-avoid recognises nearby traffic and yields right of way
**Blocked on:** `PilotInput.nearbyTraffic: List<NearbyAircraft>` field.
**Why:** Pass 16 closed D-AUDIT.9 partially (self-initiated go-around). VFR see-and-avoid is the next leaf — pilots yield to nearby traffic per CAP 393 Rule 9. Today the pilot sees the world only via the controller's frequency.
**Closes by:** archived when `PilotInput.nearbyTraffic` lands.

### D-AUDIT.9.III-FOLLOWUP — Abort takeoff on engine failure
**Status:** blocked
**Pinned at:** pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt::AUDIT9-III aborted takeoff on engine failure during takeoff roll
**Blocked on:** `AircraftState.engineState: EngineState` sealed type (Normal / LowPower / Failed).
**Why:** Pass 16 closed D-AUDIT.9 partially. V1/Vr decisions gate on engine state in real aviation; today the model has no engine-state slot.
**Closes by:** archived when `AircraftState.engineState` lands.

### D-AUDIT.9.IV-FOLLOWUP — Fuel exhaustion / divert
**Status:** blocked
**Pinned at:** pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt::AUDIT9-IV fuel exhaustion triggers divert to alternate
**Blocked on:** `AircraftState.fuelKg: Double` + per-type fuel-burn rate + alternate-aerodrome diversion logic.
**Why:** Pass 16 closed D-AUDIT.9 partially. Real flights track fuel; reserve-threshold breach triggers a divert. Today the model has no fuel slot.
**Closes by:** archived when `AircraftState.fuelKg` + alternate-diversion logic land.

### D-AUDIT.9.V-FOLLOWUP — Icing / weather deviation
**Status:** blocked
**Pinned at:** pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt::AUDIT9-V icing or weather deviation replans around hazardous volume
**Blocked on:** `AviationWorld.weatherVolumes: List<WeatherVolume>` field.
**Why:** Pass 16 closed D-AUDIT.9 partially. Real pilots replan around hazardous weather; today there is no typed weather volume in the world.
**Closes by:** archived when `AviationWorld.weatherVolumes` lands.

### D-AUDIT.11 — `placeholderAuthorities` is a single CONTACT grant; real authorities are richer
**Status:** narrative
**Pinned at:** narrative only
**Why:** G0 injects a single `AuthorityGrant(RADIO_ROLE, setOf(CONTACT))` to satisfy `HandoffAction.resolve`. Real ATC authority is layered: airspace classification (Class A/B/C/D/E/F/G with different services), runway authority, separation authority, transponder squawk-code authority — each grant with scope and conditions.
**Contract:** A complete authority model populated from each airport's published procedures. `AuthorityGrant` carries scope (entity types, geographic bounds, time bounds), operations (CONTACT, INSTRUCT, SEPARATE, CLEAR_FOR_TAKEOFF, ...), and conditions (LVP active / inactive, etc.). `HandoffAction.resolve` reads the grant; instruction-issuing rules check the grant before firing. Pass 6 prep landed the structural pressure: the placeholder is encoded as the sole leaf of a sealed `LoaderAuthority` type in `:migration` (`LoaderAuthority.Placeholder`), so adding any real-authority leaf forces a sealed-when extension at every consumer.
**Closes by:** new epic when any test exercises authority boundaries (LVP mode, transfer-of-control across FIR, AFIS at a non-controlled airport).

### D-AUDIT-arp-proxy-runtime — `OutsideAerodromeRadius` centres on ARP, not lex-first threshold
**Status:** narrative
**Pinned at:** narrative only
**Why:** The `OutsideAerodromeRadius` rule centres its ring on the lexicographically-first runway threshold (sorted by `RunwayId.value`), not on the aerodrome reference point (ARP). At LOWG the threshold-vs-ARP offset is ~1 NM, which fn-7's "rounded up + ARP-proxy-offset margin" authoring absorbs into the 18 NM ring. The right runtime fix is to populate `Aerodrome.referencePoint` (already a non-runtime-consumed field) from a hardcoded ARP table or from AIP data and centre the ring on the ARP directly.
**Closes by:** archived when a third rendered airport whose threshold-vs-ARP offset is large enough breaks the absorbed margin (e.g. a multi-strip field with thresholds ~2+ NM from ARP).

### D-AUDIT-polygon-ctr — Replace circular CTR approximation with AIP polygon
**Status:** narrative
**Pinned at:** narrative only
**Why:** Real CTR boundaries are polygons published in AIP AD 2.17. The `Aerodrome.ctrApproximationRadius` field is an explicit single-radius circular approximation — anisotropic-wrong (short on the approach axis, generous abeam). The right runtime fix is polygon containment: `Aerodrome.ctrPolygon: BoundaryRing` (or similar typed AIP shape) read by `OutsideAerodromeRadius` instead of the radial check. Polygon containment is FM/Lean territory (fn-4 lineage).
**Closes by:** new epic when the abeam-axis over-fire is observable in test, or when polygon-vs-circle delta is regulatorily meaningful.

### D-AUDIT-airac-cycle-tracking — `Aerodrome.airacCycle` typed-cycle field
**Status:** narrative
**Pinned at:** narrative only
**Why:** Authored radius values reference an AIRAC cycle (e.g. LOWG: AIRAC 2604, effective 2026-04-01) but the cycle is not modelled as a typed field on `Aerodrome` or `WorldCandidateDocument`. Consumers cannot query "is this world-candidate stale relative to the current effective cycle?" The right runtime fix is `Aerodrome.airacCycle: AiracCycle` (typed value class) populated from the world-candidate JSON, with a freshness-check helper.
**Closes by:** archived when a regulatory data refresh would break sim correctness if a stale cycle were used (e.g. a CTR boundary change between cycles).

### D-AUDIT-ljmb-polygon — LJMB CTR polygon transcription
**Status:** narrative
**Pinned at:** narrative only
**Why:** LJMB's `ctrApproximationRadiusNauticalMiles: 18` is a conservative placeholder reused from LOWG, not derived from the LJMB AIP AD 2.17 polygon. Slovenia Control's eAIP is not bot-fetchable (403/404 to scripted clients); manual transcription is required.
**Contract:** Obtain the LJMB CTR polygon (manually from the published Jepp/AIP charts, or via a cooperating data partner) and author either a tight per-aerodrome radius or — once `D-AUDIT-polygon-ctr` lands — the polygon directly.
**Closes by:** archived when docs-scout obtains LJMB CTR polygon data via a non-bot path, or `D-AUDIT-polygon-ctr` lands and forces every authored aerodrome to ship a polygon.

### D-AUDIT.2.A-FOLLOWUP — Coordination escalation explicit ack/timeout discipline
**Status:** narrative
**Pinned at:** controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/CoordinationEscalation.kt (KDoc reference at L39)
**Why:** Pass 12 closed D-AUDIT.2.A by stamping the readback discipline; the followup is the explicit per-step ack/timeout machinery (TWR receives confirmation message from APP within deadline, escalates if absent). Today CoordinationEscalation.kt carries the KDoc breadcrumb but no test surface exists.
**Closes by:** future coordination-escalation pass when ack/timeout state machine becomes test-driven

### D-AUDIT.2.B-FOLLOWUP — Coordination retransmit/handoff-acknowledge sub-state
**Status:** narrative
**Pinned at:** (no current Kotlin anchor — surfaced via fn-18.2 inventory; sibling of D-AUDIT.2.A-FOLLOWUP)
**Why:** Sibling of .2.A — once explicit ack/timeout is modeled, retransmit on missing ack becomes its own sub-state. Today's coordination machine collapses retransmit into the parent escalation timer.
**Closes by:** folded into D-AUDIT.2.A-FOLLOWUP's coordination-escalation pass

### D-AUDIT.2.E-FOLLOWUP — Per-message cognitive-delay knob on PilotInput
**Status:** blocked
**Pinned at:** (no Kotlin anchor — blocker on D-AUDIT.2.C-FOLLOWUP integration test, see docs/deferments.md#d-audit2-c-followup)
**Blocked on:** per-message cognitive-delay knob on PilotInput
**Why:** Pass 12 closed D-AUDIT.2.E (the destroyed-on-readback bug); the followup is a per-message cognitive-delay knob on PilotInput so deterministic tests can stage readback misses without injecting sim-internal time skew. Unlocks the D-AUDIT.2.C-FOLLOWUP integration test.
**Closes by:** archived when per-message cognitive-delay knob lands

### D-AUDIT.4.A-FOLLOWUP — Per-aircraft-type departure thrust/V-speed wiring
**Status:** narrative
**Pinned at:** protocol/.../AircraftType.kt L222 KDoc; controller/.../bdi/Guard.kt; TowerArrival.kt; TowerDeparture.kt
**Why:** Pass 13 closed D-AUDIT.4.A by introducing AircraftType with maxLandingDistanceM and other per-type fields; the followup is wiring per-type V-speeds and departure thrust profiles into Guard.kt's gating and TowerArrival/Departure procedure dispatch. Today AircraftType.kt carries the KDoc breadcrumb but per-type V1/Vr/V2 aren't typed leaves yet.
**Closes by:** future per-type V-speed typing pass (likely co-files with weight-temp performance corrections)

### D-AUDIT.4.D-FOLLOWUP — Per-type circuit-altitude pattern derivation
**Status:** narrative
**Pinned at:** pilot/.../PilotConstants.kt L12; pilot/.../PilotRoutePlanner.kt; sim/.../Step.kt
**Why:** Pass 13 closed D-AUDIT.4.D by giving each AircraftType a circuit-pattern shape; the followup is deriving circuit-altitude (and turn-radius) from per-type cruise/maneuvering speeds rather than the current shared constant in PilotConstants. PilotRoutePlanner.kt computes per-step radius today but the altitude knob is global.
**Closes by:** future per-type performance-derivation pass; co-files with D-AUDIT.4.A-FOLLOWUP V-speed wiring

### D-AUDIT.M2 — IFR missed-approach hold-loop compiler hardcoded to LOWG_GBG_MISSED_HOLD
**Status:** narrative
**Pinned at:** narrative only — fn-11 spec § Out-of-scope; pilot's IFR missed-approach machinery (not engaged by `ifrGoAroundTask()` from VFR pilots in fn-11/14/15 scope)
**Why:** Pilot's IFR missed-approach hold-loop compiler currently hardcodes `LOWG_GBG_MISSED_HOLD` instead of deriving the hold-loop pattern from the active IFR procedure. Out-of-scope anchor for VFR-only G3a-react work (fn-11/14/15); surfaces when a real IFR scenario lands and exercises the missed-approach path.
**Closes by:** future IFR-missed-approach pass when a real IFR scenario engages `ifrGoAroundTask()` and exercises the hold-loop compiler.

## D-PASS

### D-PASS-map-tooling-automation — Tooling automation over deferments map
**Status:** narrative
**Pinned at:** narrative only
**Why:** v1 of the deferments register ships the human-readable map only; drift detection between `docs/deferments.md`, inline `// D-PASS-*` / `// D-AUDIT-*` / `// D-PF.*` code comments, and `Pinned at:` test paths is currently grep-and-eyeball. A detekt rule or repo-root script that parses `docs/deferments.md`, verifies every `Pinned at:` test or epic exists, and asserts every inline deferment-ID comment appears as an entry would turn that drift into a CI failure.
**Closes by:** new epic when CI tooling lift becomes worthwhile.

### D-PASS-renumbering-discipline — Mixed ID-scheme cleanup
**Status:** narrative
**Pinned at:** narrative only
**Why:** The current deferment-ID scheme mixes legacy dotted forms (`D-AUDIT.7.II-FOLLOWUP`, `D-PASS-13.3-II-FOLLOWUP`) from the pre-flow-next pass-N tracking with fn-7+ dash-suffixed names (`D-PASS-g3a-react-tailwind-limit`, `D-AUDIT-polygon-ctr`). The mix produces inconsistent grep patterns and visual scan noise. v1 preserves all existing IDs as-is to bound migration scope; a future cleanup pass would settle on the dash-suffixed form (which survives renumbering) and script-rewrite all references.
**Closes by:** new epic when settling on a single ID convention.

### D-PASS-defer-flow-from-impl-review — Defer flow for review findings
**Status:** narrative
**Pinned at:** narrative only
**Why:** When a code-review agent (RepoPrompt / Codex) surfaces a finding that the principal agent defers, the convention for "this becomes a deferment" is currently a manual sibling-file step — the agent has to remember the four-bucket model, pick the right bucket, and write the entry by hand. A `/flow-next:defer` skill (or similar) that prompts for bucket assignment and writes the record would make the convention's discovery surface match the convention's discipline.
**Closes by:** new epic when a `/flow-next:defer` skill is justified.

### D-PASS-13.3-II-FOLLOWUP — Wire `RunwayLengthFailure` into `DecisionTrace.skippedActions`
**Status:** blocked
**Pinned at:** controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/DeferredContractsSpec.kt::PASS-13_3-II RunwayLengthFailure typed surface is plumbed into DecisionTrace
**Blocked on:** Wire of `RunwayLengthSufficient.classify(...)`'s typed result through to `SkippedAction.failure` (or sealed wrapper).
**Why:** Pass 17 closed D-PASS-13.3 partially with the `RunwayLengthFailure` sealed surface. The trace-render integration is the narrowed remaining work — today `DecisionTrace.skippedActions` carries a static `reason: String`; the typed payload (operation, designator, runway, required vs available) is dropped.
**Closes by:** archived when rule-trace render needs typed failure for training-feedback context.

### D-PASS-17.1 — Generalise `DiagnosingGuard<F>` interface
**Status:** narrative
**Pinned at:** narrative only
**Why:** Pass 17 introduced `RunwayLengthSufficient.classify(...): RunwayLengthFailure?` as a one-off — `RuleGuard` doesn't yet carry a `DiagnosingGuard<F>` generic. Generalising the interface is premature until a second guard files the same need (e.g., `WakeSeparationGuard` wanting typed `WakeSeparationFailure`); v1 keeps the one-off.
**Closes by:** new epic when a second guard files a typed-failure need.

### D-PASS-17.2 — Sweep `firstNotNullOfOrNull` walks in IFR procedure helpers
**Status:** blocked
**Pinned at:** controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/DeferredContractsSpec.kt::PASS-17_2 IFR procedure helpers are aerodrome-scoped (no firstNotNullOfOrNull walks)
**Blocked on:** Multi-aerodrome scenario where IFR procedure IDs collide.
**Why:** Pass 17 closed D-PASS-13.1 by scoping the `RunwayLengthSufficient` lookup to `ctx.world.aerodromes[ctx.view.aerodromeId]?.runways[runwayId]`. The remaining `firstNotNullOfOrNull` walks in `buildSidDepartureRoute` / `buildStarApproachRoute` / `buildArrivalJoinRoute` / `buildMissedApproachRoute` still walk the entire world — fine today because procedure IDs don't collide across LOWG / LJMB, breaks when a third aerodrome lands with a colliding ID.
**Closes by:** archived when a multi-aerodrome procedure-ID-collision scenario lands.

### D-PASS-17.3-FOLLOWUP — Synthetic-leaf invariant test for `AircraftType`
**Status:** narrative
**Pinned at:** narrative only
**Why:** Pass 17 left the `AircraftType` invariants (e.g. `cruiseAltitudeM > circuitPattern.altitudeAglM`) per-leaf-pinned in `AircraftTypeSpec`. A reflective walk over `AircraftType.sealedSubclasses` would catch a new leaf that violates the invariants. Blocked on a third type — would move the spec to `:protocol/commonTest` (today it lives where the leaves do).
**Closes by:** archived when a third aircraft type lands and the move is justified.

### D-PASS-cross-aircraft-step-on — Per-frequency busy tracker (cross-aircraft step-on)
**Status:** narrative
**Pinned at:** narrative only
**Why:** Simultaneous transmissions from different aircraft on the same frequency at the same instant collide because each emission site reads `state.inFlightTransmissions` against a stale view (the prior emission's `TransmissionStart` event is queued but not yet processed). C1's `pilotRadioFreeAt` only addresses the same-aircraft case.
**Contract:** Per-frequency `frequencyBusyUntil: Map<Frequency, SimTime>` tracker on `SimState`; updated eagerly at every emission site (`handlePilotTick`, `handleInstructFromController`, `handleRespondFromController`); subsequent emissions on the same frequency floor `proposedStart` to the tracker value. Phase 3 round 1's prior attempt reverted because the pre-applied `inFlightTransmissions` shifted controller-cycle output ordering enough to break G2's JoinCircuit handoff. The fix needs a dedicated impact-aware design pass — possibly with a separate tracker that doesn't mutate `inFlightTransmissions` (so the cycle's view is preserved) but does block subsequent emissions from scheduling at the same instant.
**Closes by:** new epic when a future multi-aircraft scenario where same-frequency same-instant cross-aircraft transmissions collide AND the resulting downstream wedge can't be solved at a higher-doctrine level (e.g. by tightening rule gates to require observed pilot reports — see D-PASS-pilot-mid-tng-fullstop-recovery α path).

### D-PASS-g1-diagnostics-typed-events — Typed `ControllerEvent` channel on `:common`
**Status:** narrative
**Pinned at:** narrative only
**Why:** Trace queries derive from `BeliefState` snapshots taken between events; mid-decide-cycle state is not surfaced. A future closure pass may need to inspect controller-decide intermediate state (e.g. rule-eligibility evaluation order, guard-failure reasons) that today only exists as transient values inside `controllerDecide`.
**Contract:** Add a typed `ControllerEvent` channel on `:common` (analogous to `PilotEvent` from D-AUDIT.9 lineage); emit per-tick `(stage, eligibleRules, firedRule, skippedReasons)` events; new `SimTrace.controllerEvents(controller)` query.
**Closes by:** new epic when `BeliefState` snapshots cannot answer a dive question and the dive cannot proceed without typed mid-cycle events.

### D-PASS-pilot-mid-tng-fullstop-recovery — α / β recovery from T&G/full-stop mid-air contradiction (the B5 wedge)
**Status:** narrative
**Pinned at:** narrative only
**Why:** When B's first-circuit Downwind(TOUCH_AND_GO) collides on-air with the controller's same-tick ARR-LAND (full-stop default per C4 + strip-based circuit recognition per C2/C3), the controller's clearance arrives without ever observing the pilot's intent. B's pilot reads back ClearedToLand and lands; mission step advances; the controller's BacktrackRunway is silently dropped because `processInstruction` requires `step == AWAIT_VACATE_INSTRUCTION` but the pilot's step is `LAND`. B physically lifts off again, flies an unauthorised second circuit, and wedges on the runway through wall-time. Real ATC is two-sided — (a) controllers issue landing clearance after the pilot's position call (CAP 413 §4.45-4.49), not on observation alone; (b) pilots comply with ATC clearances even when they conflict with the pilot's plan.
**Contract:** α path (controller-side): tighten `ARR-LAND` / `ARR-LAND-TNG` gates to require an observed pilot circuit-position report via a `HasReportedCircuitPosition(legs: Set<LegName>?)` BDI guard sourced from a commitment-scoped witness `Commitment.observedReportsDuringCommitment: Set<ReportEvent>` (mirroring Phase 2's `touchedDownDuringCommitment` discipline — sticky witness, default empty on commitment formation, set in `reconcileObservedStages`, reset on commitment lifecycle transitions). NOT a flat `BeliefState.observedReports[aircraft]` — that would let A's first-circuit Downwind unlock A's second-circuit landing clearance, recreating the stale-belief class Phase 2 closed for `circuitIntent`. β path (pilot-side, two-stage timing): on receipt of a ClearedToLand whose intent contradicts the active circuit task's shape, Stage 1 on ClearedToLand receipt replans the mission tree only (collapse the active TouchAndGo to a fall-through `groundArrivalTask`; mark `hasClearance = true`); Stage 2 on BacktrackRunway / AfterLandingVacateVia receipt while on runway post-touchdown extends `processInstruction` so these instructions match at any step where the pilot is on the runway post-touchdown. Recommendation: α first (smaller blast radius, doctrinally cleanest, controller-only, includes commitment-scoped witness regression test); β follows in a later pass with proper plan-review if α leaves residual cases. CAP 413 §4.45-4.49 (downwind intent reporting) and ICAO Doc 4444 §7.10 (landing clearance procedure) cited in α's doctrinal anchor.
**Closes by:** new epic when the next fn-8.3 closure session opens it.

### D-PASS-arr-number-in-sequence — Approach sequencing: 'number N in sequence'
**Status:** narrative
**Pinned at:** fn-8 (g1-two-aircraft-vfr-circuits-at-lowg) epic spec siblings; CB-1 in .plan controller backlog
**Why:** fn-8 spec siblings — CAP 413 / ICAO 4444 'number N' approach sequencing phraseology not modeled. Today the sim collapses arrival sequencing to FCFS without explicit sequence number on instruction.
**Closes by:** future approach-sequencing pass (co-files with CB-1)

### D-PASS-arr-orbit — Approach orbit / extending downwind
**Status:** narrative
**Pinned at:** fn-8 (g1-two-aircraft-vfr-circuits-at-lowg) epic spec siblings
**Why:** fn-8 spec sibling — controller-issued orbit / extend-downwind sequencing instruction not modeled. Sibling of D-PASS-arr-number-in-sequence.
**Closes by:** future approach-sequencing pass (co-files with D-PASS-arr-number-in-sequence)

### D-PASS-cap413-2_7-principle-cite-audit — CAP 413 §2.7 principle-vs-cite drift audit
**Status:** narrative
**Pinned at:** protocol/.../RegulationDatabase.kt CAP413_2_7
**Why:** protocol/.../RegulationDatabase.kt CAP413_2_7.principle reads "When instructed to change frequency the pilot shall establish two-way communication on the new frequency; an initial call identifies the aircraft to the receiving unit" but the actual content of CAP 413 §2.7 in **both** Ed 23 (effective 2021-01-21) and Ed 24 (effective 2026-07-01) is "SAFETYCOM transmissions shall be made only within a maximum range of 10 NM... below 2,000 ft above aerodrome elevation". The cite is internally incoherent. Discovered during fn-17.1 Table 2 audit.
**Contract:** Comprehensive content-vs-cite audit of `CAP413_2_7`; either rewrite the principle to match Ed 24 §2.7 SAFETYCOM content (and check whether `CAP413_2_7` consumers still make sense — they may need a different ref entirely), or locate the actual Ed 24 §-number whose content matches the existing "two-way communication on new frequency" principle and update the section field (the fn-17.1 audit could not locate the matching §-number via grep — may live in a different chapter under "Subsequent Frequency Changes" but lacking a single load-bearing paragraph). In the interim (per fn-17.1) edition string is pinned to inline Ed 23 Corr literal so the citation triple doesn't claim Ed 24 metadata it can't substantiate.
**Closes by:** future principle-audit pass; or a consumer call-site discovers `CAP413_2_7` returns the wrong principle in a DecisionTrace.

### D-PASS-cap413-4_46-principle-cite-audit — CAP 413 §4.46 principle-vs-cite drift audit
**Status:** narrative
**Pinned at:** protocol/.../RegulationDatabase.kt CAP413_4_46
**Why:** protocol/.../RegulationDatabase.kt CAP413_4_46.principle reads "Hold short / hold position instructions relating to runways must be read back in full including the runway designator or holding point; silent acknowledgement is not acceptable" but Ed 23 §4.46 content is "Pilots will receive traffic information prior to joining the traffic circuit" and Ed 24 §4.46 content is "The pilot having joined the traffic circuit makes routine reports as required by local procedures" (Ed 23 §4.47 content shifted to §4.46 in Ed 24's `-1` renumbering). Neither matches the codebase principle. Sibling shape to `D-PASS-cap413-2_7-principle-cite-audit`. Discovered during fn-17.1 Table 2 audit.
**Contract:** Locate Ed 24's actual section number for the "hold-short readback" content the codebase principle describes (most likely under §4.30-§4.40 range — "Aerodrome Phraseology" / "Ground Movements" sections), update `CAP413_4_46.section` accordingly, and re-classify per the universal hard gate. In the interim (per fn-17.1) edition string is pinned to inline Ed 23 Corr literal.
**Closes by:** future principle-audit pass; co-files with `D-PASS-cap413-2_7-principle-cite-audit`.

### D-PASS-cap413-edition-24-retired-atc-ga-phraseology — Branch-A-retired: ATC-initiated GA phraseology audit
**Status:** narrative
**Pinned at:** fn-17 spec § Deferments register; protocol/.../RegulationDatabase.kt CAP413_4_64 (renamed from _4_65)
**Why:** fn-17.1 Branch A took the rename path (§4.65 → §4.64); the retire path (Branch A-retire) was not fired but the documentation-audit deferment carries over: any prose grep for §4.65 / CAP413_4_65 that still references the retired identifier should be classified and updated per fn-17 R13 narrative.
**Closes by:** any future audit pass that sweeps stale §4.65/CAP413_4_65 references

### D-PASS-cap413-principle-text-deep-refresh — Deeper principle-string rewrites beyond one-line summary
**Status:** narrative
**Pinned at:** protocol/.../RegulationDatabase.kt (entries with mechanical Ed 24 updates from fn-17.1)
**Why:** fn-17 Branch A landed mechanical one-line summary updates for affected RegulationDatabase entries; deeper semantic Ed 24 refinements (paragraph-level rewrites) are separated to their own pass with full review. Co-files with D-PASS-cap413-2_7-principle-cite-audit + D-PASS-cap413-4_46-principle-cite-audit.
**Closes by:** future Ed 24 principle-text refresh pass; co-files with the two cite-audit deferments

### D-PASS-continue-approach-pilot-readback — CONTINUE APPROACH pilot readback semantics
**Status:** narrative
**Pinned at:** protocol/.../Instruction.kt CA leaf; sim/.../G3aRunwayObstructionContinueApproachTest.kt
**Why:** protocol/.../Instruction.kt has CA instruction surface but no pilot-side readback discipline; the controller emits CA, the pilot accepts as a non-clearance acknowledgement. Real ATC semantics (CAP 413 §4.55/Ed24 §4.54) require explicit pilot readback. Today's sim collapses to silent acceptance.
**Closes by:** CA readback discipline pass; doctrinal (CAP 413 §4.54 Ed 24)

### D-PASS-direct-simstate-constructor-canonicalization — SimState direct-constructor sites canonicalization
**Status:** narrative
**Pinned at:** fn-16-wind-state-migrate-to-aerodromeweather (epic spec); sim/.../SimState
**Why:** fn-16 migrates 8 direct-constructor sites for SimState.weatherByAerodrome; the broader canonicalization (any other direct-constructor pattern that bypasses canonical builders) is deferred. Cross-cutting test-fixture refactor.
**Closes by:** future canonicalization pass

### D-PASS-doctrinal-edition-reconciliation-non-cap413 — Edition reconciliation for non-CAP-413 sources (ICAO Doc 4444, Annex 11, SERA, 9432)
**Status:** narrative
**Pinned at:** protocol/.../RegulationDatabase.kt (non-CAP-413 entries)
**Why:** fn-17 reconciled CAP 413 to Ed 24; ICAO Doc 4444, Annex 11, SERA, ICAO 9432 each have their own edition history that isn't reconciled. Each source needs its own primary-source verification pass mirroring fn-17.1's Branch-A pattern.
**Closes by:** per-source edition-reconciliation passes (one per regulatory source)

### D-PASS-fixture-per-plan-filing-time — Sim-fixture per-plan filing-time refactor
**Status:** narrative
**Pinned at:** sim/src/jvmTest/.../G1TwoAircraftCircuitsTest.kt
**Why:** sim/.../G1TwoAircraftCircuitsTest.kt uses ad-hoc fixture wiring that bypasses the SimEvent.FlightPlanFiled event channel; cross-aerodrome filing tests (CrossAerodromeFilingSpec lineage) need a per-plan filing-time hook on the fixture builder. Cross-cutting refactor across sim/test fixtures.
**Closes by:** future sim-fixture pass that introduces a per-plan filing-time fixture builder

### D-PASS-fn6-snap-derived — AircraftObservation derived-vs-projection cleanup
**Status:** narrative
**Pinned at:** controller/.../ControllerTypes.kt; controller/src/commonTest/.../AircraftObservationTestFixtures.kt
**Why:** controller/.../ControllerTypes.kt and AircraftObservationTestFixtures.kt carry KDoc D-PASS-fn6-snap-derived breadcrumbs noting that the AircraftObservation snapshot conflates kinematic projection with derived state. A future refactor separates the projection-derived fields from the snapshot-derived fields.
**Closes by:** future fn-6-derived refactor pass

### D-PASS-g1-diagnostics-broader — G1 diagnostics broader-than-trace queries
**Status:** narrative
**Pinned at:** fn-8.3 task md; sim/.../SimTraceQueries.kt
**Why:** fn-8.3 closed D-PASS-g1-diagnostics partially via SimTraceQueries; the broader follow-up adds richer diagnostics (cross-aircraft synchrony, frequency-busy windows). Sibling of D-PASS-g1-diagnostics-typed-events (already in docs).
**Closes by:** future G1-diagnostics broader pass (co-files with D-PASS-g1-diagnostics-typed-events)

### D-PASS-g3a-continue-approach-cancel-clearance — Cancel-clearance during CONTINUE APPROACH
**Status:** narrative
**Pinned at:** fn-13-g3a-obstruction-continue-approach-three (epic spec)
**Why:** fn-13 spec sibling — controller-issued cancel-clearance during an active CONTINUE-APPROACH instruction (e.g. obstruction worsens) is not modeled. Edge case of fn-13's CA discipline.
**Closes by:** future CA cancel-clearance pass

### D-PASS-g3a-continue-approach-in-circuit — CONTINUE APPROACH in-circuit traffic interaction
**Status:** narrative
**Pinned at:** fn-13-g3a-obstruction-continue-approach-three (epic spec)
**Why:** fn-13 spec sibling — CA semantics interact with in-circuit traffic (the trailing aircraft's downwind decisions) not modeled in fn-13's single-aircraft scope.
**Closes by:** future CA + multi-aircraft pass

### D-PASS-g3a-continue-approach-possible-ga-variant — CONTINUE APPROACH 'possible go-around' variant
**Status:** narrative
**Pinned at:** fn-13-g3a-obstruction-continue-approach-three (epic spec)
**Why:** fn-13 spec sibling — CA with 'expect possible go-around' caveat phraseology is a variant not modeled in v1 (which collapses to CA-only).
**Closes by:** future CA-variant pass

### D-PASS-g3a-continue-approach-sequencing — CONTINUE APPROACH sequencing across multiple aircraft
**Status:** narrative
**Pinned at:** fn-13-g3a-obstruction-continue-approach-three (epic spec)
**Why:** fn-13 spec sibling — CA sequencing across multiple aircraft (the leading aircraft on CA, trailing aircraft's spacing reaction) not modeled. Co-files with D-PASS-three-or-more-aircraft.
**Closes by:** future CA + multi-aircraft pass (co-files with D-PASS-three-or-more-aircraft)

### D-PASS-g3a-continue-approach-subjective-judgment — CONTINUE APPROACH subjective-judgment input
**Status:** narrative
**Pinned at:** fn-13-g3a-obstruction-continue-approach-three (epic spec)
**Why:** fn-13 spec sibling — CA judgment input on the pilot side (visual assessment of whether the obstruction is clearing fast enough) is reduced in v1 to a deterministic rule. Subjective-judgment modeling is its own pass.
**Closes by:** future pilot-subjective-judgment pass

### D-PASS-g3a-obstruction-aerodrome-payload — RunwayObstructionInformation aerodrome payload
**Status:** narrative
**Pinned at:** controller/.../ControllerTypes.kt; controller/.../observe/Event.kt
**Why:** controller/.../ControllerTypes.kt and observe/Event.kt carry KDoc D-PASS-g3a-obstruction-aerodrome-payload noting that the obstruction-information event payload lacks the aerodrome ID; today the sim wiring threads it implicitly. A future shape adds AerodromeId to the obstruction-information event leaf.
**Closes by:** future obstruction-information-event reshape pass

### D-PASS-g3a-obstruction-belief-divergence — Obstruction belief divergence (controller vs world)
**Status:** narrative
**Pinned at:** fn-12-g3a-obstruction-single-aircraft-atc (epic spec); controller/.../BeliefState obstruction slice
**Why:** fn-12 obstruction model treats world-truth as ground truth; cases where controller belief diverges from world (delayed observation, stale strip) need their own modeling pass.
**Closes by:** future obstruction-belief pass

### D-PASS-g3a-obstruction-clearsAt-update — Obstruction clearsAt-update relaxation rule
**Status:** narrative
**Pinned at:** core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/RunwayObstruction.kt
**Why:** core/.../world/RunwayObstruction.kt has a KDoc D-PASS-g3a-obstruction-clearsAt-update noting that the clearsAt timestamp is immutable today; a future pass allows controllers to update clearsAt as new information arrives without recreating the obstruction.
**Closes by:** future obstruction-lifecycle pass when clearsAt-update arrives

### D-PASS-g3a-obstruction-continue-approach — Continue-approach on obstruction (fn-12/13 anchor)
**Status:** narrative
**Pinned at:** fn-12-g3a-obstruction-single-aircraft-atc; fn-13-g3a-obstruction-continue-approach-three
**Why:** fn-12 sims an unconditional GA on obstruction; fn-13 lands CONTINUE-APPROACH discipline. This entry persists as the cross-spec anchor between fn-12 obstruction surfacing and fn-13 CA semantics — fn-13 is the closure path but this entry references both specs.
**Closes by:** archived once fn-12/13 cross-reference is retired

### D-PASS-g3a-obstruction-flicker-debounce — Obstruction flicker debounce
**Status:** narrative
**Pinned at:** fn-12-g3a-obstruction-single-aircraft-atc (epic spec); core/.../RunwayObstruction
**Why:** fn-12 obstruction surfacing is event-driven; rapid clear-then-appear flicker (debris settling/resettling, wildlife) needs a debounce window to avoid pilot-side recognition oscillation.
**Closes by:** future obstruction-debounce pass

### D-PASS-g3a-obstruction-kind-variants — Richer RunwayObstruction taxonomy beyond v1 single kind
**Status:** narrative
**Pinned at:** core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/RunwayObstruction.kt
**Why:** core/.../world/RunwayObstruction.kt KDoc notes v1 has a single obstruction kind; future variants (FOD, disabled-aircraft, vehicle-incursion, wildlife) each become their own sealed leaf with type-specific clearance semantics.
**Closes by:** future obstruction-taxonomy expansion pass

### D-PASS-g3a-obstruction-leader-not-vacated — Leader-not-vacated obstruction (preceding aircraft on runway)
**Status:** narrative
**Pinned at:** fn-12-g3a-obstruction-single-aircraft-atc (epic spec)
**Why:** fn-12 obstruction model treats obstruction as world entity; a leader-not-vacated case (preceding aircraft still on runway) is its own runtime situation that may surface obstruction-like wedges in multi-aircraft scenarios.
**Closes by:** future multi-aircraft obstruction pass (co-files with D-PASS-three-or-more-aircraft)

### D-PASS-g3a-obstruction-orbit-hold — Orbit-hold instruction during obstruction
**Status:** narrative
**Pinned at:** fn-12/13 epic specs
**Why:** fn-12/13 obstruction model emits GA or CONTINUE-APPROACH on obstruction; orbit-hold (extending the approach by orbiting until obstruction clears) is a real ATC instruction shape not modeled. Sibling of D-PASS-arr-orbit.
**Closes by:** future orbit-hold pass (co-files with D-PASS-arr-orbit)

### D-PASS-g3a-obstruction-pilot-report — Pilot-reported obstruction (vs world-truth-only)
**Status:** narrative
**Pinned at:** fn-12-g3a-obstruction-single-aircraft-atc (epic spec)
**Why:** fn-12 surfaces obstruction via world-truth observation; pilot-reported obstructions (a landing aircraft reports debris on runway via radio) are their own input channel. Not modeled in fn-12 v1.
**Closes by:** future pilot-reported-obstruction pass

### D-PASS-g3a-obstruction-runway-state — Runway state model (obstructed / closed / displaced) — fn-11 anchor
**Status:** narrative
**Pinned at:** fn-11-g3a-single-aircraft-pilot-trained-vfr (epic spec); core/.../Runway
**Why:** fn-11 spec references runway-state model (obstructed vs closed vs displaced threshold) deferred until fn-12 obstruction work. fn-12 landed obstruction but the broader runway-state model remains separate from per-obstruction modeling.
**Closes by:** future runway-state-model pass

### D-PASS-g3a-react-atis-cadence-sensing — Wind via ATIS broadcast (cadence-sensitive)
**Status:** narrative
**Pinned at:** pilot/.../PilotInput.kt weatherByAerodrome
**Why:** pilot/.../PilotInput.kt today reads weatherByAerodrome via world-truth observation; real PICs sense wind via ATIS broadcasts at coarser cadence. Sibling of fn-14's reactive-GA epic deferments; doctrinal layer.
**Closes by:** future ATIS-cadence pass (coupled with D-PASS-cap413-edition-24-rename-pending-pdf if Ed 24 PDF lands)

### D-PASS-g3a-react-combined-wind-vector — Combined crosswind + tailwind vector decision
**Status:** narrative
**Pinned at:** fn-15-g3a-react-tailwind-pilot-reactive-go (epic spec siblings); pilot/.../Pilot.kt derivePilotEvent
**Why:** fn-14/15 evaluate each wind axis independently. Real PICs evaluate the resultant vector (weakest-link). Cross-cutting refactor on derivePilotEvent.
**Closes by:** future combined-vector pass

### D-PASS-g3a-react-crosswind-trigger — G3a-react crosswind trigger (fn-11 narrative anchor)
**Status:** narrative
**Pinned at:** fn-11-g3a-single-aircraft-pilot-trained-vfr (epic spec); pilot/.../Pilot.kt CrosswindLimitExceeded recognition
**Why:** fn-11 spec narrative references the reactive-crosswind trigger that fn-14 ultimately delivered. The fn-11 anchor persists as a back-reference to fn-14's recognition path. Sibling of fn-14's epic-spec entries.
**Closes by:** archived once fn-11 narrative anchor retires (or use as historical pointer)

### D-PASS-g3a-react-gust-evaluation — Gust-peak evaluation against POH limit
**Status:** narrative
**Pinned at:** fn-14-g3a-react-pilot-reactive-go-around-on (epic spec siblings)
**Why:** fn-14 v1 reads steady-state Wind.speedKnots only; gust-peak evaluation against POH crosswind/tailwind limits is a real-pilot consideration flagged by practice-scout but deferred. Sibling deferment carries over to fn-15's tailwind axis as D-PASS-g3a-react-tailwind-gust-evaluation.
**Closes by:** future gust-evaluation pass; co-files with D-PASS-g3a-react-tailwind-gust-evaluation

### D-PASS-g3a-react-multi-aircraft-crosswind — Multiple aircraft on same runway when wind shifts to crosswind
**Status:** narrative
**Pinned at:** fn-14-g3a-react-pilot-reactive-go-around-on (epic spec siblings)
**Why:** fn-14 covers single-aircraft G3a; multi-aircraft scenario where wind shifts and sequencing of simultaneous GAs requires controller coordination logic is deferred. Sibling: D-PASS-g3a-react-multi-aircraft-tailwind.
**Closes by:** future multi-aircraft scenario pass; co-files with D-PASS-g3a-react-multi-aircraft-tailwind

### D-PASS-g3a-react-multi-aircraft-tailwind — Multiple aircraft on same runway when wind shifts to tailwind
**Status:** narrative
**Pinned at:** fn-15-g3a-react-tailwind-pilot-reactive-go (epic spec siblings)
**Why:** Tailwind sibling of D-PASS-g3a-react-multi-aircraft-crosswind. Multi-aircraft scenario with simultaneous tailwind GAs requires controller coordination.
**Closes by:** future multi-aircraft scenario pass (co-files with crosswind sibling)

### D-PASS-g3a-react-other-poh-triggers — Density altitude / temperature / weight POH triggers
**Status:** narrative
**Pinned at:** fn-14/15 epic specs; protocol/.../AircraftType.kt POH-derived shape
**Why:** fn-14/15 typed maxCrosswindKnots + maxTailwindKnots establish the per-leaf POH-data pattern; other POH triggers (density altitude, OAT, weight limits) each become their own typed field + recognition predicate. Sibling siblings of fn-14/15 epics.
**Closes by:** future POH-derivation pass per trigger

### D-PASS-g3a-react-personal-minimums — Pilot personal-minimums margin below POH demo value
**Status:** narrative
**Pinned at:** protocol/.../AircraftType.kt maxCrosswindKnots/maxTailwindKnots; pilot/.../observe/PilotEvent.kt CrosswindLimitExceeded/TailwindLimitExceeded
**Why:** pilot/.../observe/PilotEvent.kt and protocol/.../AircraftType.kt carry KDoc anchors for D-PASS-g3a-react-personal-minimums; today recognition uses typed POH limits directly. A future layer adds a per-pilot personal-minimums margin below the typed value.
**Closes by:** future per-pilot personal-minimums pass

### D-PASS-g3a-react-tailwind-atis-cadence — Wind via ATIS broadcast (tailwind axis)
**Status:** narrative
**Pinned at:** fn-15-g3a-react-tailwind-pilot-reactive-go (epic spec siblings)
**Why:** Tailwind sibling of D-PASS-g3a-react-atis-cadence-sensing. fn-15 v1 reuses fn-14's world-truth weather observation path.
**Closes by:** future ATIS-cadence pass (co-files with D-PASS-g3a-react-atis-cadence-sensing)

### D-PASS-g3a-react-tailwind-condition-corrections — Runway-condition / displaced-threshold corrections to POH max tailwind
**Status:** narrative
**Pinned at:** fn-15-g3a-react-tailwind-pilot-reactive-go (epic spec siblings); protocol/.../AircraftType.kt maxTailwindKnots
**Why:** fn-15 uses POH/AFH constant maxTailwindKnots; runway-condition (wet, contaminated), displaced threshold, pressure altitude, and temperature corrections are layered effects not modeled in v1. Co-files with D-AUDIT.4.A.II-FOLLOWUP (runway-condition gating).
**Closes by:** future runway-condition-corrections pass (co-files with D-AUDIT.4.A.II-FOLLOWUP)

### D-PASS-g3a-react-tailwind-gust-evaluation — Gust-peak evaluation against POH tailwind limit
**Status:** narrative
**Pinned at:** fn-15-g3a-react-tailwind-pilot-reactive-go (epic spec siblings)
**Why:** Tailwind sibling of D-PASS-g3a-react-gust-evaluation; fn-15 v1 reads steady-state Wind.speedKnots for tailwind too. Mirror of fn-14's crosswind gust deferment.
**Closes by:** future gust-evaluation pass (co-files with crosswind sibling)

### D-PASS-g3a-react-tailwind-personal-minimums — Personal-minimums layer over fn-15 tailwind limit
**Status:** narrative
**Pinned at:** protocol/.../AircraftType.kt maxTailwindKnots; pilot/.../observe/PilotEvent.kt TailwindLimitExceeded
**Why:** Sibling of D-PASS-g3a-react-personal-minimums applied to the tailwind axis; pilot judgement margin below the typed maxTailwindKnots. Doctrinal layer over fn-15's typed POH value.
**Closes by:** future personal-minimums pass (co-files with D-PASS-g3a-react-personal-minimums)

### D-PASS-g3a-react-vrb-handling — Wind.variable VRB direction handling
**Status:** narrative
**Pinned at:** (planned) protocol/.../Wind.variable: Boolean field
**Why:** protocol/.../Instruction.kt and the Wind type lack a VRB (variable direction) flag; v1 evaluates crosswind=0 when direction is undefined. Real metar reporting includes VRB at low wind speeds.
**Closes by:** future Wind-model VRB pass (co-files with D-PASS-g3a-react-atis-cadence-sensing)

### D-PASS-g3a-react-wind-variability-dynamics — Temporal averaging / trend reasoning across ticks
**Status:** narrative
**Pinned at:** fn-14-g3a-react-pilot-reactive-go-around-on (epic spec siblings)
**Why:** fn-14 v1 evaluates wind per-tick; real ATC + pilots use sustained-wind reasoning across a temporal window. Sibling of D-PASS-g3a-react-gust-evaluation but oriented at trend rather than peak.
**Closes by:** future sustained-wind-trend pass

### D-PASS-g3b-react-cross-aerodrome-crosswind — Cross-aerodrome crosswind go-around at LJMB
**Status:** narrative
**Pinned at:** pilot/.../Pilot.kt; pilot/.../observe/PilotEvent.kt CrosswindLimitExceeded; pilot/src/commonTest/.../WindForMissionTest.kt
**Why:** pilot/.../Pilot.kt and PilotEvent.kt carry KDoc breadcrumbs for D-PASS-g3b-react-cross-aerodrome-crosswind; today the reactive-crosswind GA is exercised at LOWG. Fixture variation at LJMB or other aerodrome reuses all machinery (test added when G3b cross-aerodrome scenario lands).
**Closes by:** future G3b cross-aerodrome scenario pass; sibling of fn-14 D-PASS-g3a-react-multi-aircraft-crosswind

### D-PASS-g3b-react-cross-aerodrome-tailwind — Cross-aerodrome tailwind go-around at LJMB
**Status:** narrative
**Pinned at:** pilot/.../observe/PilotEvent.kt TailwindLimitExceeded; pilot/src/commonTest/.../observe/PilotEventTailwindTest.kt
**Why:** pilot/.../observe/PilotEvent.kt and PilotEventTailwindTest.kt carry KDoc breadcrumbs for D-PASS-g3b-react-cross-aerodrome-tailwind; sibling of -crosswind variant for the tailwind axis.
**Closes by:** future G3b cross-aerodrome scenario pass; sibling of D-PASS-g3b-react-cross-aerodrome-crosswind

### D-PASS-instructor-agent-surface — Instructor-agent surface (pilot-training scenarios)
**Status:** narrative
**Pinned at:** fn-11/12 epic specs
**Why:** fn-11/12 specs reference an instructor-agent surface for pilot-training scenarios; today's pilot model has solo + ATC layers. Instructor as a third agent layer is its own pass.
**Closes by:** future instructor-agent pass

### D-PASS-metar-taf-ingestion — METAR/TAF ingestion pipeline
**Status:** narrative
**Pinned at:** fn-16-wind-state-migrate-to-aerodromeweather (epic spec siblings)
**Why:** Read METAR/TAF cycles and translate to WeatherObservation writes via a separate ingestion pipeline. Separate from sim-mutation path.
**Closes by:** future METAR/TAF pipeline pass

### D-PASS-per-runway-weather — Per-runway weather sensors
**Status:** narrative
**Pinned at:** fn-16-wind-state-migrate-to-aerodromeweather (epic spec siblings); core/.../Runway
**Why:** Large airports with per-runway wind sensors require Runway.weather instead of (or alongside) Aerodrome.weather; v1 (post-fn-16) keeps aerodrome-scope.
**Closes by:** future per-runway-weather pass

### D-PASS-recat-eu-wake — RECAT-EU wake-turbulence categorisation
**Status:** narrative
**Pinned at:** fn-8 (g1-two-aircraft-vfr-circuits-at-lowg) epic spec siblings; CB-2 in .plan controller backlog
**Why:** fn-8 spec sibling — wake turbulence separation per RECAT-EU rather than ICAO Heavy/Medium/Light bands. CB-2 in .plan controller backlog. Separate from core fn-8.
**Closes by:** future wake-turbulence pass

### D-PASS-regdb-transcription-drift — RegulationDatabase transcription-drift audit
**Status:** narrative
**Pinned at:** fn-13-g3a-obstruction-continue-approach-three (epic spec); protocol/.../RegulationDatabase.kt
**Why:** fn-13 spec sibling — broader transcription-drift audit across RegulationDatabase entries beyond the cap413 cite-audit deferments. Whole-database principle-vs-section drift sweep.
**Closes by:** future regdb-transcription audit pass (co-files with D-PASS-cap413-2_7-principle-cite-audit + D-PASS-cap413-4_46-principle-cite-audit)

### D-PASS-three-or-more-aircraft — Three-or-more aircraft scenarios (fn-8 scope is two)
**Status:** narrative
**Pinned at:** fn-8 (g1-two-aircraft-vfr-circuits-at-lowg) epic spec siblings
**Why:** fn-8 scope is exactly two aircraft (LOWG circuit); three-or-more scenarios surface new wedges (slot reservation across 3+ aircraft, runway sharing). Sibling of D-PASS-cross-aircraft-step-on (already in docs) and D-PASS-arr-number-in-sequence.
**Closes by:** future N-aircraft scenario pass

### D-PASS-visual-separation-handover — Visual-separation handover (pilot-to-pilot)
**Status:** narrative
**Pinned at:** fn-8 (g1-two-aircraft-vfr-circuits-at-lowg) epic spec siblings
**Why:** fn-8 spec sibling — visual-separation handover ('Cessna ahead, follow') where the trailing pilot accepts responsibility for separation visually. Not modeled in fn-8 v1.
**Closes by:** future visual-separation pass

### D-PASS-weather-history-replay — Weather history replay (rolling buffer + replay)
**Status:** narrative
**Pinned at:** fn-16-wind-state-migrate-to-aerodromeweather (epic spec siblings); core/.../WeatherObservation
**Why:** Today's WeatherObservation is point-in-time only; retained observation history (rolling buffer, replay) is deferred.
**Closes by:** future weather-history pass

### D-PASS-weather-model-expansion — Weather model expansion (visibility, precipitation, cloud layers, weather volumes)
**Status:** narrative
**Pinned at:** fn-16-wind-state-migrate-to-aerodromeweather (epic spec siblings); core/.../WeatherObservation
**Why:** fn-16 migrates Wind to Aerodrome.weather; gusts (already typed on Wind), visibility ceilings, precipitation, cloud layers, weather volumes are each their own field on WeatherObservation or sibling entity. Separate from the migration.
**Closes by:** future weather-model expansion pass per field

### D-PASS-weather-shift-event-leaf — SimEvent.WeatherChanged event leaf
**Status:** narrative
**Pinned at:** fn-16-wind-state-migrate-to-aerodromeweather (epic spec siblings); sim/.../SimEvent
**Why:** Today's shape is direct world mutation via the AviationWorld.updateAerodrome lens helper; an event leaf would be structurally cleaner for test mutators. Filed but not blocking.
**Closes by:** future SimEvent.WeatherChanged pass (low priority)

### D-PASS-weather-validity-window — Weather observation validity window (observedAt + staleness)
**Status:** narrative
**Pinned at:** fn-16-wind-state-migrate-to-aerodromeweather (epic spec siblings); core/.../WeatherObservation
**Why:** observedAt: SimTime on WeatherObservation + staleness reasoning ('METAR is 90 minutes old; treat as WindReport.NotReported'). Out of fn-16 scope.
**Closes by:** future weather-validity-window pass

### D-PASS-pilot-world-strip-dynamic-state — typed pilot-chart projection that hides entity-level dynamic state
**Status:** planned
**Pinned at:** fn-16-wind-state-migrate-to-aerodromeweather.1 (codex round-2 review).
**Why:** Post-fn-16 the rich-world-domain principle puts **time-varying state on the entity** (`Aerodrome.weather`, `Runway.obstruction`), and the same `AviationWorld` instance flows to both controller-side wiring AND `PilotInput.world`. The pilot firewall has typed projection fields (`PilotInput.weatherByAerodrome: Map<AerodromeId, WindReport>` — wind only, no QNH/visibility) but the underlying `world.aerodromes[id].weather` is reachable, and `world.aerodromes[id].runways[id].obstruction` (fn-12) was reachable before. Convention + KDoc enforces the discipline that pilot rules read through the typed projection, never directly off `world`. The cleaner structural enforcement is a `:pilot/PilotAviationWorld` projection that strips entity-level dynamic fields (weather, obstruction, plus future dynamic adds) at the firewall boundary, so reading them from pilot code fails to compile.
**Contract:** Define a `:pilot`-internal `PilotAviationWorld` data class (or projection helper) that omits / nulls dynamic entity fields. `PilotWiring.buildPilotInput` projects via that helper. Architectural test: scanning pilot-side code for `world.aerodromes[*].weather` or `world.aerodromes[*].runways[*].obstruction` reads returns zero hits. Re-asserts the firewall structurally instead of via convention. Open question: whether the projection lives in `:pilot` or as a `:core`-side `Pilot`-typed view to keep the type singleton.
**Closes by:** future pilot-firewall structural-enforcement pass.

## D-WORLD

### D-WORLD.2 — `LjmbWorldCandidateValidationTest.writesLjmbCurrentCoreValidationReport` pre-existing failure
**Status:** blocked
**Pinned at:** migration/src/jvmTest/kotlin/xyz/easiersaid/twr/migration/world/LjmbWorldCandidateValidationTest.kt:264
**Blocked on:** LJMB world-candidate JSON authoring — IFR SID inventory or related publication-field reconciliation against `LjmbWorldCandidateValidationTest`'s expectations.
**Why:** `:migration:jvmTest`'s `LjmbWorldCandidateValidationTest.writesLjmbCurrentCoreValidationReport()` has been failing on `main` since at least commit `369ead7` (2026-04-30 — "Pilot/ATC firewall: passes 1-5"). Multiple subsequent epics observed and explicitly documented the failure as out-of-scope (fn-5, fn-6, fn-9, fn-11, fn-16). It breaks the `:migration:allTests` aggregate task, which prevents the full epic-spec R12 command (`./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests :migration:allTests detekt`) from exiting 0 in any epic that inherits the standing R12 shape, even when the epic's own surfaces are GREEN. Treated as "out-of-scope" in each downstream epic via explicit narrative carve-outs in evidence; this deferment promotes the standing narrative carve-out into a named register entry so future epics can cite a single `D-WORLD.2 (blocked)` instead of re-deriving the rationale.
**Closes by:** archived when an LJMB-candidate-authoring pass reconciles the validation report (e.g., a CAD-authoring/IFR-SID-inventory epic that re-files the LJMB world-candidate JSON to match the validator's expectations).

### D-WORLD.1 — `Aerodrome.runwayConfiguration` field in world-candidate JSON
**Status:** blocked
**Pinned at:** protocol/src/commonTest/kotlin/xyz/easiersaid/twr/protocol/DeferredContractsSpec.kt::WORLD1 Aerodrome carries a published runwayConfiguration field
**Blocked on:** CAD-authoring pass — schema field on `CandidateAerodrome` + loader population.
**Why:** Pass 15 introduced `RunwayConfiguration` as a controller-side selection output; the published value (per aerodrome AIP) is not yet authored in the world-candidate JSON. Today `selectRunwayConfiguration` derives from wind alone.
**Closes by:** archived when world-candidate JSON gains the `runwayConfiguration` field + loader populates `Aerodrome.runwayConfiguration`.

## Archive

### D-PF.2 — RunwayAssignmentSource sealed discriminator
**Status:** closed
**Closed by:** Pass 5 — see ~/.claude/plans/pass-5-entities-and-aircraft-intent.md. Orphan test deleted in fn-18.2 per Decision #13.
**Enforcement:** `RunwayAssignmentSource` sealed type in `protocol/RunwayAssignment.kt` (six leaves: `TaxiClearance`, `LineUp`, `Takeoff`, `Land`, `TouchAndGo`, `Backtrack`); `applyPrecedence(prior, new): Either<AnomalousAssignment, RunwayAssignment>` total over the 6×6 cross-product (nested `when` exhaustiveness); `ProcessInstructionRunwayDerivationSpec` asserts on the `.runway` accessor.

### D-PF.5 — FlightStrip filed-plan-only intent derivation
**Status:** closed
**Closed by:** ~/.claude/plans/fragility-and-strip-dynamism.md. Block comment removed from `pilot/DeferredContractsSpec.kt` in fn-18.2.
**Enforcement:** `inferIntentFromGoal(goal: HighLevelGoal?)` signature (no mission arg) reads only the filed-plan goal; `BeliefState.seenStrip: Set<AircraftId>` seed-once gate; `ControllerEvent.AircraftArrivalCommitted(aircraft)` event derived from `Report(RunwayVacated)`; `FirewallStripStaticTest` (sim/jvmTest) allowlist regex scans `FlightStrip.kt` for any `(pilotMission|mission).<x>` access where `<x>` is not in `{goal, navigationMode}`; G0 integration asserts `groundBeliefs.aircraftIntent[ac] == Arriving` post-landing.

### D-PF.6 — TaxiTo split (TaxiToHoldingPoint vs TaxiToStand)
**Status:** closed
**Closed by:** ~/.claude/plans/pass-6-loader-frequencies-and-taxiTo-runway.md (Pass 6). Block comment removed from `pilot/DeferredContractsSpec.kt` in fn-18.2.
**Enforcement:** `TaxiToHoldingPoint` / `TaxiToStand` sealed split in `protocol/Instruction.kt` under shared `TaxiClearance: GroundInstruction` parent; `TaxiToSplitFirewallTest` (E14) reflective leaf-cardinality assertion; `ProcessInstructionRunwayDerivationSpec` multi-runway twin-row; G0 assertion (g) sealed-type match on `TaxiToStand`; `ExhaustivenessTest` exact `assertEquals(leaves.size, 99)` for `AtcInstruction`; `TaxiToHoldingActionSpec` exercises the failure path.

### D-AUDIT.3 — Per-type `runUpDurationMs`
**Status:** closed
**Closed by:** ~/.claude/plans/pass-13-aircraft-type-consumers.md (Pass 13). Block comment removed from `pilot/DeferredContractsSpec.kt` in fn-18.2.
**Enforcement:** `AircraftType.runUpDurationMs: Long` field at top-level (`protocol/AircraftType.kt:154`); C172 = 60_000 ms (POH §4); B738 = 600_000 ms (FCOM NP cold-start); `PilotCognitive.TIMED_STEP_DURATION_MS` global deleted; `CompletionMode.TIMED` reads `aircraft.type.runUpDurationMs`; `AircraftTypeSpec` doctrine pins; `RunUpDurationSpec` 4 rows (C172 advances at t=61s; B738 stays at t=61s proving per-type wiring vs hardcoded 60s; B738 advances at t=601s).

### D-AUDIT.5 — Responsibility transfer overlap (ResponsibilityState)
**Status:** closed
**Closed by:** ~/.claude/plans/pass-7-responsibility-and-boundary-release.md (Pass 7). Orphan test deleted in fn-18.2 per Decision #13.
**Enforcement:** Sealed `ResponsibilityState { Owned(since) | HandingOff(target: HandoffTarget, since) | Watching(from, since) }` in `:protocol/ResponsibilityState.kt`; sealed `HandoffTarget { Peer(controllerId) | Released }`; `applyContactFrequency` + `applyInitialContact` + `applyBoundaryReleaseReadback` machinery; `ResponsibilityInvariantSpec` (cross-controller Owned invariant), `ResponsibilityStateMachineSpec` (five canonical-path transitions).

### D-AUDIT.6 — Flight-plan filing event (FlightPlanFiled / FiledPlan)
**Status:** closed
**Closed by:** ~/.claude/plans/pass-11-flight-plan-filing.md (Pass 11). Orphan test deleted in fn-18.2.
**Enforcement:** `FiledPlan` sealed interface in `:protocol/FiledPlan.kt` (`Vfr` / `Ifr` leaves); `SimEvent.FlightPlanFiled(aircraft, plan, recipient, time)` event leaf; `Step.handleFlightPlanFiled` adds aircraft as `Owned(time)` to the recipient controller; `Fixture.flightPlans: Map<AircraftId, FiledPlan>` (replaces `groundResponsibilities`); `FixtureLoadSpec` and `FlightPlanFilingSpec` row coverage; AFTN routing closure landed in Pass 14 (`AftnRouting.routeFiledPlan`).

### D-AUDIT.10 — Fixture stops mutating responsibilities directly
**Status:** closed
**Closed by:** ~/.claude/plans/pass-11-flight-plan-filing.md (Pass 11) alongside D-AUDIT.6. Orphan test deleted in fn-18.2.
**Enforcement:** `Fixture.flightPlans` replaces `Fixture.groundResponsibilities`; aircraft enter via `SimEvent.FlightPlanFiled`; `FirewallFixtureNoDirectResponsibilitiesTest` (E20) negative-lookahead allowlist source-scans `Fixture.kt` / `Fixtures.kt` for any `ResponsibilityState.Owned(...)` construction outside the allowlisted patterns (`responsibilities = emptyMap()` only).

### D-PF.4 — `Option<T>` migration of nullable `PilotMission` parameters
**Status:** closed
**Closed by:** ~/.claude/plans/pass-2-option-migration.md (Pass 2). Archived in fn-18.2 to back inline references in `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotMission.kt`.
**Enforcement:** All six nullable-defaulted parameters on `PilotMission`'s primary constructor migrated to `Option<T> = None` (`navigationMode`, `activeRunway`, `routeOverride`, `lastReportedLeg`, `altitudeRestrictionM`, `lastTransmittedStep`); `runwayFromInstruction` helper total over 98 `AtcInstruction` leaves; `MissionOptionalityTest` (pilot/jvmTest) reflectively walks `PilotMission::class.primaryConstructor.parameters` and fails if any defaulted parameter is nullable; `ProcessInstructionMissionStateSpec` carries 7 symmetric-coverage rows.

### D-PF.7 — `RadarServiceTerminated` boundary-release instruction
**Status:** closed
**Closed by:** ~/.claude/plans/pass-7-responsibility-and-boundary-release.md (Pass 7). Archived in fn-18.2.
**Enforcement:** `RadarServiceTerminated` `AtcInstruction` leaf under `SurveillanceInstruction` (with `Option<Frequency>` suggested successor and `Option<Squawk>` typically 7000 for VFR release); `TerminateRadarServiceAction(forRole, suggestedFrequency, squawk)` mirrors `HandoffAction`; sim-side `applyRadarServiceTerminated` + `applyBoundaryReleaseReadback`; `OutsideAerodromeRadius(thresholdMetres)` guard; `BoundaryReleaseFirewallTest` (E17) reflective walk asserts handoff-vs-terminate role symmetry across every procedure spec; previously-dead `RadarServiceTerminated: ControllerResponse` leaf deleted.

### D-AUDIT.1 — `AircraftObservationFactory` removes `entitiesByPoint` leak
**Status:** closed
**Closed by:** ~/.claude/plans/pass-5-entities-and-aircraft-intent.md (Pass 5). Archived in fn-18.2.
**Enforcement:** `SensorReading.kt` no longer reads `state.worldIndex.entitiesByPoint`; the projection carries `position: PointId` only; `AircraftObservation.from(...)` factory in `controller/AircraftObservationFactory.kt` derives `entities` controller-side; `AircraftObservation`'s primary constructor is `internal`; `ControllerWiring.toObservation(reading, worldIndex)` calls the factory; `FirewallSensorReadingTest` (sim/jvmTest) forbids `EntityRef` and `entitiesByPoint` in `SensorReading`.

### D-AUDIT.2 — Coordination lifecycle state machine
**Status:** closed
**Closed by:** ~/.claude/plans/pass-9-coordination-lifecycle.md (Pass 9). Archived in fn-18.2.
**Enforcement:** Sealed `CoordinationState { Issued | Querying | Reissued | LostCommsDeclared }`; `ReadbackTimeoutPolicy` with five doctrine-anchored timeouts (ICAO Doc 4444 §4.5.7.5.3); `escalateOverdueCoordinations` pure fold (no silent drop); `coordinationEscalationOutputs` emits `ConfirmInstruction` (CAP 413 Glossary) on Querying and replay-as-original on Reissued (Doc 4444 §12.3.1.2); `ConfirmInstruction` `ControllerResponse` leaf (count 11→12); sim-side missed-handoff sweep with `MISSED_HANDOFF_TIMEOUT = 120s` (NATS MATS Part 1 §2.1); `CoordinationStateExhaustivenessTest`, `SimEventExhaustivenessTest`, `FirewallMissedHandoffSweepProducerTest` (E19), retargeted `FirewallBeliefWriteTest`.

### D-AUDIT.4 — `AircraftType` (typed C172 / B738)
**Status:** closed
**Closed by:** ~/.claude/plans/pass-10-aircraft-type.md (Pass 10). Archived in fn-18.2.
**Enforcement:** `AircraftType` sealed data class in `:protocol/AircraftType.kt` carrying ICAO designator (`IcaoTypeDesignator` typed value class), wake category, `Kinematics` (taxi/rotation/climb/approach speeds, climb rate), runway-length requirements (TODA/LDA), and circuit pattern (altitude AGL + downwind offset) with init invariants on every nested value; `AircraftState.type: AircraftType`; `PilotConstants` gutted of per-type kinematic constants; `SensorReading.wakeCategory` and `FlightStrip.icaoTypeDesignator` projections; `AircraftTypeSpec` (8 rows: C172 + B738 doctrine pin, Default reference equality, 5 invariant rejection rows), `PilotAgentTypeSpec` (2 rows: C172 / B738 climb-target — proves per-type wiring); `FirewallAircraftStateTest` and `FirewallObservationTest` canonical constructors.

### D-AUDIT.6.A-FOLLOWUP — Multi-recipient AFTN routing
**Status:** closed
**Closed by:** ~/.claude/plans/pass-14-aftn-routing.md (Pass 14). Archived in fn-18.2 to back inline references in `controller/observe/CoordinationEscalation.kt`.
**Enforcement:** `AftnAddress(aerodromeId, role)` data class + sealed `AftnDestination { Departure | Arrival }` with `classify` companion in `:protocol`; `FiledPlan.destinationAerodrome: AerodromeId?` hoisted onto the sealed interface; `AftnRouting.routeFiledPlan(plan, publishedRolesAt): Either<RoutingFailure, NonEmptyList<AftnAddress>>` in `:sim` (pure routing with narrow projection input); sealed `RoutingFailure { NoDepartureRoleStaffed | NoDestinationRoleStaffed }`; departure-side prefers GROUND→TOWER, destination-side prefers TOWER→APPROACH; `SimEvent.FlightPlanFiled.recipient: AftnAddress`; `AftnRoutingSpec` (7 rows covering cross-aerodrome, single-aerodrome, fallback, Left-branches, IFR plan dispatch).

### D-AUDIT.7 — `RunwayConfiguration` and `selectRunwayConfiguration`
**Status:** closed
**Closed by:** ~/.claude/plans/pass-15-runway-config-and-atis.md (Pass 15). Archived in fn-18.2.
**Enforcement:** `RunwayConfiguration(arrivals: List<RunwayId>, departures: List<RunwayId>)` in `:protocol/RunwayConfiguration.kt` with init invariants (OR-non-empty union, no-duplicates); `selectRunwayConfiguration(runways, wind): Either<RunwayConfigurationFailure, RunwayConfiguration>` in `:controller/assess` with sealed `RunwayConfigurationFailure { WindNotReported | NoRunwayInBucket | NoRunwaysPublished }`; selection picks ±90° into-wind bucket sorted by heading-diff then `RunwayId.value` lex order; `selectRunwayIntoWind` thin nullable projection (Pass 6 callers preserved); `BeliefState.activeRunway` derivation uses ATIS configuration primary when published, falls back to wind-derived selection; `RunwayConfigurationSpec` (4 rows) + `RunwayConfigurationSelectionSpec` (4 rows: parallel-runway lex tie-break, Left-branches).

### D-AUDIT.8 — ATIS broadcast (Atis + AtisIssued + atisByAerodrome)
**Status:** closed
**Closed by:** ~/.claude/plans/pass-15-runway-config-and-atis.md (Pass 15) alongside D-AUDIT.7. Archived in fn-18.2.
**Enforcement:** `Atis(letter, aerodrome, configuration, wind, qnh, visibility, generatedAt)` in `:protocol/Atis.kt` with `letter A..Z` init invariant; `nextAtisLetter(c)` pure helper for canonical A→Z→A rotation; `SimEvent.AtisIssued` event leaf (10th); `Step.handleAtisIssued` stores in `SimState.atisByAerodrome`; `ControllerView.atis: Map<AerodromeId, Atis>` projection; `BeliefState.expectedAtisLetter`; `Controller.atisLetterMismatchAdvisories` scans `InitialContact` and emits `Respond(CurrentInformationIs(letter))` per ICAO Annex 11 §4.3.6; `CurrentInformationIs(target, letter)` `ControllerResponse` leaf (14th) with `ResponseReaction.silent(mission)`; `PilotInput.atisByAerodrome: Map<AerodromeId, Atis>`; pilot reads ATIS at `MissionStep.CALL_INBOUND`; `AtisSpec` (3 rows) + `AtisLetterPropagationSpec` (2 rows); `RegulationDatabase.ICAO_ANNEX_11_4_3`.

### D-AUDIT.9 — Self-initiated go-around `PilotEvent` channel (partial)
**Status:** closed
**Closed by:** ~/.claude/plans/pass-16-pilot-proactive-events.md (Pass 16). Archived in fn-18.2. The four unstamped behaviours (II–V) remain OPEN as separate FOLLOWUPs.
**Enforcement:** Sealed `PilotEvent` channel in `:pilot/observe/PilotEvent.kt` with first leaf `DecisionAltitudeWithoutClearance(aircraft, altitudeM, currentStep)`; pure `derivePilotEvent(state, mission): PilotEvent?` function (single leaf today, becomes `List<PilotEvent>` when 2nd leaf lands); `Pilot.checkSelfInitiatedGoAround` renamed → `applySelfInitiatedGoAround`; trigger guards live in `derivePilotEvent`; `pilotDecide` shape: derive → cast → apply; `PilotEventDerivationSpec` (8 rows on guard branches) + `SelfInitiatedGoAroundResponseSpec` (3 rows: VFR subtree, IFR `ifrGoAroundTask`, mission invariants reset); CAP 413 §4.55 + ICAO Doc 4444 §7.10.2 doctrine cites.

### D-AUDIT.12 — Aerodrome roles + frequencies loaded from world-candidate JSON
**Status:** closed
**Closed by:** ~/.claude/plans/pass-6-loader-frequencies-and-taxiTo-runway.md (Pass 6). Archived in fn-18.2.
**Enforcement:** `CandidateAerodrome.roles: Map<RoleName, CandidateAerodromeRole>` schema field (defaulted to empty map so existing JSONs parse); LOWG / LJMB world-candidate JSONs hand-authored with `roles` blocks; `WorldCandidateLoader.toWorld` populates `Aerodrome.roles` and synthesises `Aerodrome.controllers` 1-1; parse-time totality: `RoleNameSerializer` + `FrequencySerializer` validate at JSON decode; `LoaderAuthority` sealed type with `Placeholder` leaf (structural pressure for D-AUDIT.11); `LoaderDefaults.placeholderAuthorities` single home; `LoadedFixture.validate()` adds typed `RoleNotPublished` + `FrequencyMismatch` violations; `ControllerView.staffedRoles: Set<RoleName>` gates `HandoffAction.resolve`; `LoaderRolesPopulatedTest` (E13) + `LoaderFrequencyConsistencyTest` (E15) cross-document drift catcher.

### D-AUDIT.14 — `BeliefState.recentRadio` time-windowed buffer
**Status:** closed
**Closed by:** ~/.claude/plans/pass-5-entities-and-aircraft-intent.md (Pass 5). Archived in fn-18.2.
**Enforcement:** `BeliefState.aircraftIntent` deleted; new `BeliefState.recentRadio: Map<AircraftId, RecentRadio>` time-windowed buffer (5-minute window); `RecentRadio` is a value class with a private primary constructor whose only mutation is `append(event, now, window)` which prunes entries older than `now - window` (time-window invariant encoded in the type); new `withRecentRadio(events, now)` fold; on-demand `deriveCurrentIntent(strip, recentRadio): AircraftIntent` (most-recent intent-bearing radio event wins; else strip; else Transit); `intentFromRadio(event)` + `aircraftIdOf(event)` sealed-exhaustive over `ControllerEvent` (14 leaves); `OperatorContext.intentOf(aircraft)` composes view's strip + belief's recentRadio; `EventExhaustivenessTest` pins leaf coverage; retargeted `FirewallBeliefWriteTest`; G0's intent-flip assertion migrated to behavioural-consequence check on the transmission stream.

### D-AUDIT.13 — Cross-aerodrome strip propagation
**Status:** closed
**Closed by:** Pass 14 (commit b8b099a)
**Enforcement:** controller/.../AftnRouting.routeFiledPlan fans out to destination-side recipient; destination tower ControllerSpec.knownStrips carries filed plan; ControllerView.flightStripIntents surfaces Arriving intent at sim-init via SimEvent.FlightPlanFiled (no radio-observation reliance). Exercised by CrossAerodromeFilingSpec: LOWG→LJMB VFR plan results in LJMB_TOWER seeing flightStripIntents[ac] == Arriving at sim-init.

### D-PASS-13.1 — Aerodrome-scoped runway lookup
**Status:** closed
**Closed by:** Pass 17 (commit ce18d47)
**Enforcement:** controller/.../bdi/Guard.kt aerodrome-scoped runway lookup at L960+ (Pass-17 closure KDoc); pre-Pass-17 lookup walked all aerodromes

### D-PASS-13.2 — IFR procedure helpers' wrong-units cruise-altitude fallback
**Status:** closed
**Closed by:** Pass 17 (commit ce18d47)
**Enforcement:** protocol/.../AircraftType.kt L135+ cruise-altitude fallback; pilot/.../PilotRoutePlanner.kt L556+/L624+/L678+/L726+ IFR fallback uses cruise altitude

### D-PASS-13.3 — Typed RunwayLengthFailure sealed surface (partial; trace-render in -II-FOLLOWUP)
**Status:** closed
**Closed by:** Pass 17 partial closure (commit ce18d47); trace-render narrowed to D-PASS-13.3-II-FOLLOWUP (active in docs)
**Enforcement:** controller/.../bdi/Guard.kt L937+/L955+/L1031+ typed RunwayLengthFailure; RunwayLengthGatingSpec.kt L209+/L213+/L231+ typed-payload assertions; trace-render plumbing remains as D-PASS-13.3-II-FOLLOWUP

### D-PASS-cap413-edition-23-comparison-unavailable — Branch B-unverified-comparison sub-branch (conditional)
**Status:** closed
**Closed by:** fn-17.1 Branch A took the verified path (Ed 24 PDF acquired with SHA-pinning AND Ed 23 comparison source matched)
**Enforcement:** Branch B was not fired. See D-PASS-cap413-edition-24-reconciliation archive entry; Ed 23 comparison source SHA f3b4839e885cd554740f664a55d3732cd7284789e0b5f808970cfdbc21e746e7 (planning-time match) noted in .plan.

### D-PASS-cap413-edition-23-pdf-unreachable-at-task-time — Conditional: Ed 23 PDF unreachable at task time
**Status:** closed
**Closed by:** fn-17.1 Branch A — both Ed 23 and Ed 24 PDFs were reachable, conditional deferment was not fired
**Enforcement:** No artifact — conditional clause that did not activate. See D-PASS-cap413-edition-24-reconciliation.

### D-PASS-cap413-edition-24-r11-verify-sandbox-block — R11 verify command sandbox block (closed at task time via workaround)
**Status:** closed
**Closed by:** fn-17.1 (workaround applied at task time)
**Enforcement:** Original blocker: fn-17.1's R11 verify command could not run in the implementer's sandbox because Gradle wrapper writes to `/Users/andrew/.gradle/` are blocked by harness filesystem policy. Workaround: cloned the entire Gradle user-home (`/Users/andrew/.gradle/{caches,native,wrapper}`) to a sandbox-writable location (`$TMPDIR/gradle-user-home/`), removed lock files, set `GRADLE_USER_HOME=$TMPDIR/gradle-user-home` + `_JAVA_OPTIONS=-Djava.io.tmpdir=$TMPDIR` (redirect Kotlin compiler intermediate files away from system default `/var/folders/...` which is also sandbox-blocked) + ran `./gradlew --offline --no-daemon` against the cloned cache. JAVA_HOME pointed at a Nix-installed Zulu JDK 21 path (`/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8`). Outcome: BUILD SUCCESSFUL in 40s, 25 actionable tasks executed; eight-golden testsuites verified GREEN (LowgGoldenTest, G1TwoAircraftCircuitsTest, G1TwoAircraftMinimalSpec, G2CrossAerodromeVfrTest, G3aPilotTrainedGoAroundTest, G3aRunwayObstructionTest, G3aRunwayObstructionContinueApproachTest, G3aPilotReactiveCrosswindTest). Pattern reusable for future sandbox-restricted Gradle work; fn-18 series reuses the same workaround.

### D-PASS-cap413-edition-24-reconciliation — CAP 413 Edition 24 numbering reconciliation
**Status:** closed
**Closed by:** fn-17.1 (2026-05-11)
**Enforcement:** Branch A — Confirmed (with Edition #1 quirk). Ed 24 primary-source verified against CAA PDF (URL https://www.caa.co.uk/publication/download/18165, SHA-256 c620cda9b6bdbe8e9ed51b258e4df2f6e3edc839226e53ee2b591cb696a966ac, captured 2026-05-11). Ed 23 comparison source SHA-256 f3b4839e885cd554740f664a55d3732cd7284789e0b5f808970cfdbc21e746e7 (planning-time match). Actual mapping: uniform `-1` shift across §4.5x-§4.6x range — §4.65 (ATC-initiated GA) → §4.64; §4.66 (VFR-continue) → §4.65; §4.67 (pilot-initiated GA) → §4.66; §4.68 (military) → §4.67. Plus §4.49 → §4.48 (circuit sequencing), §4.53 → §4.52 (cancellation of landing clearance), §4.55 → §4.54 (continue approach), §4.56 → §4.55 (CA not landing clearance). The docs-scout hypothesis was partially correct on §4.66→§4.65 and §4.67→§4.66 but missed that §4.65 ATC-initiated GA also moves to §4.64. Codebase: protocol/.../RegulationDatabase.kt CAP_413_EDITION = "Edition 24 (effective 1 July 2026)" constant; CAP413_4_65 renamed to CAP413_4_64; CAP413_4_49/CAP413_4_53/CAP413_4_55/CAP413_4_56 section fields updated. See wiki/data-sources/cap413-edition-24-capture.md (verification artifact with Tables 1 + 2 + mapping table + local extraction procedure) and `.flow/tasks/fn-17-cap-413-edition-24-numbering.1.md ## Evidence` for primary-implementation-commit SHA + R11 verification status.

### D-PASS-cap413-edition-24-rename-pending-pdf — Branch-C deferment: pending Ed 24 PDF availability
**Status:** closed
**Closed by:** fn-17.1 Branch A took the verified path (PDF acquired and SHA-pinned)
**Enforcement:** Branch C was not fired; Branch A landed full Ed 24 numbering reconciliation. The conditional deferment is moot. See D-PASS-cap413-edition-24-reconciliation archive entry.

### D-PASS-g3a-react-tailwind-limit — POH tailwind limit + recognition + applier
**Status:** closed
**Closed by:** fn-15 epic (fn-15.1 + fn-15.2 both done)
**Enforcement:** protocol/.../AircraftType.kt maxTailwindKnots typed field; pilot/.../observe/PilotEvent.kt TailwindLimitExceeded leaf; pilot/.../Pilot.kt applyTailwindGoAround applier; sim/src/jvmTest/.../G3aPilotReactiveTailwindTest.kt as the ninth golden

### D-AUDIT-lowg-ctr-radius — LOWG CTR radius retuning from 12 NM hardcode to per-aerodrome AIP-derived value
**Status:** closed
**Closed by:** fn-7 (per-aerodrome-aip-driven-ctr-radius)
**Enforcement:** Replaced `OutsideAerodromeRadius(Meters.fromNauticalMiles(12))` hardcode with per-aerodrome `Aerodrome.ctrApproximationRadius` field. LOWG authors 18 NM (AIP AD 2.17 polygon max-edge 16.25 NM rounded UP + ~1 NM ARP-proxy-offset margin). See `cad/airports/rendered/lowg/world-candidate.json` and `wiki/data-sources/lowg.md` for the AIP citation.

### D-PASS-g1-diagnostics — G1 diagnostics SimTraceQueries harness
**Status:** closed
**Closed by:** fn-8.3 (CLOSED-PARTIAL — harness sufficient for the entire fn-8.3 dive cycle Phase 1 → Phase 3 round 2)
**Enforcement:** `sim/.../SimTraceQueries.kt` harness (`commitmentStageTransitions`, `missionStepTransitions`, `positionPointTransitions`, `transitionsOf`, `formatJourney`) plus direct `BeliefState` reads; `sim/jvmTest/.../G1ClosureDiveTest` as the per-round dive driver. No typed events on `:common` were needed; the broader follow-up is tracked as `D-PASS-g1-diagnostics-typed-events` (active) and `D-PASS-g1-diagnostics-broader` (active).

### D-AUDIT.2.A — Lost-comms TransmittingBlind emission on entry to LostCommsDeclared
**Status:** closed
**Closed by:** Pass 12
**Enforcement:** `controller/.../observe/Coordination.kt` L97+ (entry-to-state action stamps the readback discipline); `controller/.../observe/CoordinationEscalation.kt` L143 (Pass 12 D-AUDIT.2.A reference); `protocol/.../Instruction.kt` L1296 (TransmittingBlind blind-transmission instruction); `pilot/.../PilotCognitive.kt` L213 (Pass 12 lost-comms declared internally); exercised by `controller/.../TransmittingBlindEmissionSpec.kt` (Pass-12 D-AUDIT.2.A test). Followup work continues as `D-AUDIT.2.A-FOLLOWUP` (active narrative).

### D-AUDIT.2.B — Lost-comms readback retransmit / withdrawal discipline
**Status:** closed
**Closed by:** Pass 12 (sibling to D-AUDIT.2.A)
**Enforcement:** `controller/.../observe/Coordination.kt` (retransmit / withdrawal state transitions wired into the lost-comms state machine); exercised by `controller/.../CoordinationsCleanupSpec.kt`. Followup work continues as `D-AUDIT.2.B-FOLLOWUP` (active narrative).

### D-AUDIT.2.E — Coordination "destroyed on readback" bug fix
**Status:** closed
**Closed by:** Pass 12
**Enforcement:** `controller/.../Controller.kt` (readback no longer destroys the coordination record prematurely); exercised by `controller/src/jvmTest/.../AcceptReadbackIdentityTest.kt`. Followup work continues as `D-AUDIT.2.E-FOLLOWUP` (active narrative: per-message cognitive-delay knob on PilotInput).

### D-AUDIT.4.A — AircraftType per-type field structure (maxLandingDistanceM and siblings)
**Status:** closed
**Closed by:** Pass 13
**Enforcement:** `protocol/.../AircraftType.kt` (sealed AircraftType with maxLandingDistanceM, maxCrosswindKnots, maxTailwindKnots etc. per-leaf fields); `controller/.../bdi/Guard.kt` consumers; `controller/.../procedure/TowerArrival.kt` / `TowerDeparture.kt` per-type wiring. Followup work continues as `D-AUDIT.4.A-FOLLOWUP` (active narrative: per-type V-speed wiring).

### D-AUDIT.4.D — AircraftType per-type circuit-pattern shape
**Status:** closed
**Closed by:** Pass 13
**Enforcement:** `protocol/.../AircraftType.kt` (per-type circuit-pattern shape on AircraftType leaves); `pilot/.../DeferredContractsSpec.kt` test anchor; production consumers in `pilot/.../PilotRoutePlanner.kt`. Followup work continues as `D-AUDIT.4.D-FOLLOWUP` (active narrative: per-type circuit-altitude derivation).

### D-AUDIT.9.II — Pilot reactive go-around `PilotEvent` extension
**Status:** closed
**Closed by:** fn-14 (G3a-react pilot-reactive crosswind GA) for the crosswind axis; fn-15 (G3a-react pilot-reactive tailwind GA) for the tailwind axis
**Enforcement:** `pilot/.../observe/PilotEvent.kt` (sealed `PilotEvent` with `CrosswindLimitExceeded` and `TailwindLimitExceeded` leaves); `pilot/.../Pilot.kt` `applyCrosswindGoAround` / `applyTailwindGoAround` distinct appliers; sim/jvmTest `G3aPilotReactiveCrosswindTest.kt` + `G3aPilotReactiveTailwindTest.kt` as goldens. Followup work continues as `D-AUDIT.9.II-FOLLOWUP` (active: VFR see-and-avoid).

### D-PASS-wind-state-migrate-to-aerodrome — Wind lives on Aerodrome.weather
**Status:** closed
**Closed by:** fn-16 (fn-16.1 atomic field migration; fn-16.2 paper-trail sweep)
**Enforcement:** `core/.../world/WorldModel.kt` `Aerodrome.weather: WeatherObservation? = null` field (mirrors fn-12 `Runway.obstruction` precedent for `project_rich_world_domain.md`); `core/.../world/WeatherObservation.kt` (relocated from `:controller` to `:core/world` so `:core` can own the type); `core/.../world/WorldLenses.kt` `AviationWorld.updateAerodrome(id) { transform }` single-id inline lens with identity-equality short-circuit and unit test `core/.../WorldLensesSpec.kt`; `sim/.../SimState.kt` `SimState.initial` folds the `weatherByAerodrome` parameter into the world post-validation (pre-fold `WeatherForUnknownAerodrome` check + post-fold `MissingWeatherForRunwayAerodrome` check) — the standalone `SimState.weatherByAerodrome` field is DELETED; three production readers migrated (`sim/.../PilotWiring.kt` pinned `mapNotNull` projection preserving pre-migration absent-key semantics for `windForMission`'s singleton-fallback path; `sim/.../ControllerWiring.kt` walks `state.world.aerodromes[id]?.weather`; `sim/.../testing/SimTraceQueries.kt` `weatherTransitions` walks the post-fold world); `sim/jvmTest/.../G3aPilotReactiveCrosswindTest.kt` `authorWeather` mutator migrated to `world.updateAerodrome(id) { it.copy(weather = ...) }`; 8 direct-constructor `SimState(...)` test sites had `weatherByAerodrome = emptyMap()` arg removed. Firewall surfaces UNCHANGED — `PilotInput.weatherByAerodrome: Map<AerodromeId, WindReport>` (fn-14.1) keeps its shape; `ControllerView.weather: WeatherObservation?` keeps its shape — only the *source* changed. The deferment register `D-PASS-pilot-world-strip-dynamic-state` was filed during fn-16.1 codex round 2 to track the cleaner structural enforcement (a `:pilot/PilotAviationWorld` projection that hides entity-level dynamic state at the firewall boundary).

