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
**Pinned at:** pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt::D-PF1 aerodrome requiring startup clearance has REQUEST_STARTUP and AWAIT_STARTUP_APPROVAL
**Blocked on:** LOWS / LOWW / LJLJ-class aerodrome in the fixture, plus the `CLEARANCE_DELIVERY` controller role.
**Why:** After Phase D, `groundDepartureTask` has no `REQUEST_STARTUP` / `AWAIT_STARTUP_APPROVAL` steps — every pilot at every airport skips startup. At aerodromes that require startup clearance (LOWS, LOWW, LJLJ, much of central Europe), a pilot calling for taxi without first obtaining startup is a procedural violation; we deleted the steps because we never built the controller-side `CLEARANCE_DELIVERY` procedure.
**Contract:** An `AirportProcedure.requiresStartupClearance: Boolean` field on the airport manifest, populated from real-world data. `groundDepartureTask(airport)` returns a tree containing `REQUEST_STARTUP` and `AWAIT_STARTUP_APPROVAL` iff the airport requires it. A new `CLEARANCE_DELIVERY` controller role, with a `ClearanceDeliveryStage` and `clearanceDeliveryProcedure()` analogous to `groundTaxiProcedure()`, issues `StartupApproved` in response to `Request(RequestStartup)`. The mission tree branch is determined by airport, never by cockpit type.
**Closes by:** archived when prerequisite lands.

### D-PF.3 — Airborne spawn has a runway-assignment path via FiledPlan
**Status:** blocked
**Pinned at:** pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt::D-PF3 airborne-spawned aircraft with FiledPlan has activeRunway from filed plan
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

## D-AUDIT

### D-AUDIT.2.C-FOLLOWUP — Sim-level integration test for full lost-comms tail
**Status:** blocked
**Pinned at:** sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/DeferredContractsSpec.kt::D-AUDIT2-C full comms tail integration test (query, reissue, blind)
**Blocked on:** Per-message cognitive-delay knob on `PilotInput`.
**Why:** Pass 9 landed the lost-comms state machine and Pass 12 closed three of four follow-ups; the sim-level end-to-end (query at 10 s → reissue 1/2/3 → `LostCommsDeclared` at 5 min → `TransmittingBlind` emission) is the remaining integration assertion. Today a deterministic test can't stage the readback miss without injecting sim-internal time skew.
**Closes by:** archived when per-message cognitive-delay knob lands.

### D-AUDIT.2.F-FOLLOWUP — G0 negative-escalation assertion (instruction-vs-completion)
**Status:** blocked
**Pinned at:** sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/DeferredContractsSpec.kt::D-AUDIT2-F G0 no LostCommsDeclared on un-complied-with instructions
**Blocked on:** "Instruction physically complied with" accessor on `Commitment` or `BeliefState` (distinguishes "readback received but stage not reached" from "no readback").
**Why:** Pass 12 dropped the Pass-9-fold-in "no LostCommsDeclared at end of G0" assertion: it was passing due to the destroyed-on-readback bug (D-AUDIT.2.E follow-on), not because of correctness. The right shape — "no LostCommsDeclared on instructions the aircraft has not physically complied with" — lands when scenario-level coverage of instruction-vs-completion semantics is in place.
**Closes by:** archived when scenario-level coverage of instruction-vs-completion lands.

### D-AUDIT.3.II-FOLLOWUP — Per-step TIMED durations beyond RUN_UP_CHECKS
**Status:** blocked
**Pinned at:** pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt::D-AUDIT3-II MissionStep runUpDurationMs lookup is step-discriminated
**Blocked on:** Second `MissionStep` adopting `CompletionMode.TIMED`.
**Why:** Pass 13 closed D-AUDIT.3 with `AircraftType.runUpDurationMs` for `RUN_UP_CHECKS`. Today only that single step uses TIMED; if other steps adopt TIMED, a flat scalar collapses the per-step semantic into one value. The right surface is a step-discriminated lookup (`MissionStep.runUpDurationMs(type)` or a step-keyed map on `AircraftType`).
**Closes by:** archived when a second step adopts TIMED completion.

### D-AUDIT.4.A.II-FOLLOWUP — Runway-condition gating (wet, contaminated, displaced threshold)
**Status:** blocked
**Pinned at:** controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/DeferredContractsSpec.kt::D-AUDIT4-A-II runway-condition gating affects runway-length classification
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
**Pinned at:** pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt::D-AUDIT4-D-II Kinematics carries per-phase waypoint radii
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
**Pinned at:** controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/DeferredContractsSpec.kt::D-AUDIT7-II mixed-mode parallel runway configuration is selectable
**Blocked on:** Simultaneous-parallel-approach scenario in the fixture.
**Why:** `RunwayConfiguration` accepts independent arrivals/departures sets today but no consumer reads them as distinct; today the selection assumes `arrivals == departures`. Mixed-mode parallel operations (e.g. 16L arrivals + 16R departures concurrently) require the consumer wiring to read distinct sets.
**Closes by:** archived when a parallel-runway scenario surfaces.

### D-AUDIT.7.III-FOLLOWUP — Derive `BeliefState.activeRunway` on read (delete stored slice)
**Status:** blocked
**Pinned at:** controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/DeferredContractsSpec.kt::D-AUDIT7-III BeliefState still carries stored activeRunway slice (will be deleted)
**Blocked on:** Read-site cascade migrating all consumers to a `deriveActiveRunway` accessor.
**Why:** Pass 15 left `BeliefState.activeRunway: RunwayId?` as a stored slice — `expectedAtisLetter` + wind already drive its value. The stored slice is redundant; the right surface is on-read derivation, eliminating the two-truths failure mode.
**Closes by:** archived when the read-site cascade lands.

### D-AUDIT.8.II-FOLLOWUP — Separate ATIS frequency
**Status:** blocked
**Pinned at:** controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/DeferredContractsSpec.kt::D-AUDIT8-II ATIS broadcast lives on its own frequency
**Blocked on:** Multi-frequency comms model.
**Why:** Today ATIS is implicit on the role's primary frequency (TOWER / GROUND). Real ATIS broadcasts on a dedicated frequency and the pilot tunes to it before tuning to the operator role.
**Closes by:** archived when multi-frequency comms model lands.

### D-AUDIT.8.III-FOLLOWUP — Voice-style ATIS rendering (`Atis.toMessage()`)
**Status:** blocked
**Pinned at:** protocol/src/commonTest/kotlin/xyz/easiersaid/twr/protocol/DeferredContractsSpec.kt::D-AUDIT8-III Atis carries a toMessage rendering for voice broadcast
**Blocked on:** `Atis.toMessage()` (or `AtisMessage` value class) on the protocol surface.
**Why:** The structured `Atis` record is consumed directly by the rule layer today (`Controller.atisLetterMismatchAdvisories`, etc.) — there's no voice-shaped render. A voice render lets the broadcast path carry the same content over the transmission stream.
**Closes by:** archived when `Atis.toMessage()` lands.

### D-AUDIT.8.IV-FOLLOWUP — Multi-aerodrome ATIS-letter resolution
**Status:** blocked
**Pinned at:** pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt::D-AUDIT8-IV ATIS letter resolution dispatches by aerodrome for size greater than one
**Blocked on:** G2 cross-aerodrome scenario (or any scenario where `PilotInput.atisByAerodrome.size > 1`).
**Why:** `atisLetterForCallInbound` at `PilotCognitive.kt:480` dispatches on `mission.goal` — `Transit`/`Departure` carry the destination, `Arrival`/`CircuitTraining` do not. For the latter goals, multi-entry maps `error()` loudly (Pass 15 G2 tightening). The right shape threads the target aerodrome through `PilotInput` or widens `HighLevelGoal` to carry the call-target aerodrome.
**Closes by:** archived when G2 lands.

### D-AUDIT.9.II-FOLLOWUP — VFR see-and-avoid recognises nearby traffic
**Status:** blocked
**Pinned at:** pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt::D-AUDIT9-II VFR see-and-avoid recognises nearby traffic and yields right of way
**Blocked on:** `PilotInput.nearbyTraffic: List<NearbyAircraft>` field.
**Why:** Pass 16 closed D-AUDIT.9 partially (self-initiated go-around). VFR see-and-avoid is the next leaf — pilots yield to nearby traffic per CAP 393 Rule 9. Today the pilot sees the world only via the controller's frequency.
**Closes by:** archived when `PilotInput.nearbyTraffic` lands.

### D-AUDIT.9.III-FOLLOWUP — Abort takeoff on engine failure
**Status:** blocked
**Pinned at:** pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt::D-AUDIT9-III aborted takeoff on engine failure during takeoff roll
**Blocked on:** `AircraftState.engineState: EngineState` sealed type (Normal / LowPower / Failed).
**Why:** Pass 16 closed D-AUDIT.9 partially. V1/Vr decisions gate on engine state in real aviation; today the model has no engine-state slot.
**Closes by:** archived when `AircraftState.engineState` lands.

### D-AUDIT.9.IV-FOLLOWUP — Fuel exhaustion / divert
**Status:** blocked
**Pinned at:** pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt::D-AUDIT9-IV fuel exhaustion triggers divert to alternate
**Blocked on:** `AircraftState.fuelKg: Double` + per-type fuel-burn rate + alternate-aerodrome diversion logic.
**Why:** Pass 16 closed D-AUDIT.9 partially. Real flights track fuel; reserve-threshold breach triggers a divert. Today the model has no fuel slot.
**Closes by:** archived when `AircraftState.fuelKg` + alternate-diversion logic land.

### D-AUDIT.9.V-FOLLOWUP — Icing / weather deviation
**Status:** blocked
**Pinned at:** pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt::D-AUDIT9-V icing or weather deviation replans around hazardous volume
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

## D-PASS

### D-PASS-deferments-map-tooling-automation — Tooling automation over deferments map
**Status:** narrative
**Pinned at:** narrative only
**Why:** v1 of the deferments register ships the human-readable map only; drift detection between `docs/deferments.md`, inline `// D-PASS-*` / `// D-AUDIT-*` / `// D-PF.*` code comments, and `Pinned at:` test paths is currently grep-and-eyeball. A detekt rule or repo-root script that parses `docs/deferments.md`, verifies every `Pinned at:` test or epic exists, and asserts every inline deferment-ID comment appears as an entry would turn that drift into a CI failure.
**Closes by:** new epic when CI tooling lift becomes worthwhile.

### D-PASS-deferments-renumbering-discipline — Mixed ID-scheme cleanup
**Status:** narrative
**Pinned at:** narrative only
**Why:** The current deferment-ID scheme mixes legacy dotted forms (`D-AUDIT.7.II-FOLLOWUP`, `D-PASS-13.3-II-FOLLOWUP`) from the pre-flow-next pass-N tracking with fn-7+ dash-suffixed names (`D-PASS-g3a-react-tailwind-limit`, `D-AUDIT-polygon-ctr`). The mix produces inconsistent grep patterns and visual scan noise. v1 preserves all existing IDs as-is to bound migration scope; a future cleanup pass would settle on the dash-suffixed form (which survives renumbering) and script-rewrite all references.
**Closes by:** new epic when settling on a single ID convention.

### D-PASS-deferments-cross-ref-from-impl-review — Defer flow for review findings
**Status:** narrative
**Pinned at:** narrative only
**Why:** When a code-review agent (RepoPrompt / Codex) surfaces a finding that the principal agent defers, the convention for "this becomes a deferment" is currently a manual sibling-file step — the agent has to remember the four-bucket model, pick the right bucket, and write the entry by hand. A `/flow-next:defer` skill (or similar) that prompts for bucket assignment and writes the record would make the convention's discovery surface match the convention's discipline.
**Closes by:** new epic when a `/flow-next:defer` skill is justified.

### D-PASS-13.3-II-FOLLOWUP — Wire `RunwayLengthFailure` into `DecisionTrace.skippedActions`
**Status:** blocked
**Pinned at:** controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/DeferredContractsSpec.kt::D-PASS-13_3-II RunwayLengthFailure typed surface is plumbed into DecisionTrace
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
**Pinned at:** controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/DeferredContractsSpec.kt::D-PASS-17_2 IFR procedure helpers are aerodrome-scoped (no firstNotNullOfOrNull walks)
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

## D-WORLD

### D-WORLD.1 — `Aerodrome.runwayConfiguration` field in world-candidate JSON
**Status:** blocked
**Pinned at:** protocol/src/commonTest/kotlin/xyz/easiersaid/twr/protocol/DeferredContractsSpec.kt::D-WORLD1 Aerodrome carries a published runwayConfiguration field
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
