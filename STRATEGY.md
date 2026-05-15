---
name: graz-tower
last_updated: 2026-05-15
generator: flow-next-strategy
---

# graz-tower Strategy

## Target problem

ATC simulators teach by approximation: behaviour looks plausible, but few claims trace to a specific regulation and even fewer survive a reversal — a go-around that doesn't reset stale clearance, a "cleared for takeoff" issued while the runway isn't vacated, an extend-downwind that quietly deadlocks. Off-the-shelf training tools optimise for surface realism, not auditability; formal-methods work in this domain optimises for one giant theorem, not for the discrete safety claims a working sim actually has to defend. The gap is a sim where every controller behaviour cites a regulation, every safety-critical decision is either Lean-certified or fail-loud, and every state transition has its reversal tested before its forward path ships.

## Our approach

Build a regulation-grounded ATC simulator (Kotlin multiplatform, hybrid DES + physics-tick) underpinned by split local certifiers (Lean: runway, surface, air-path, separation) that mechanise discrete guarantees with explicit ownership boundaries — never one mega-theorem. Author worlds by hand via CAD/GIS rather than translating apt.dat / OFMX automatically. Keep `error()` for provably-impossible states only and prefer typed errors (`Either`/`Option`) elsewhere; let unfinished work fail loudly rather than silently. Phase widening is incremental and frozen (don't reopen closed families) — and the runtime is allowed to lead the proof boundary, never the reverse.

## Who it's for

**Primary:** the project's working researcher — building a defensible ATC simulator from first principles where every behaviour cites a regulation and every safety claim either has a Lean proof or a loud fail-loud test. They're hiring graz-tower to be the reference implementation that a training-grade product can later be built on without re-deriving its operational model.

**Secondary:** formal-methods practitioners using ATC as a domain for split-kernel proof architecture — `research/fm` is intended to be readable as a standalone proof project, not just internal infrastructure.

## Key metrics

- **Lean tracked build green** — `lake build` over `research/fm/lean` (98 modules) compiles without `sorry`; `parity_inventory.md` and `refinement_inventory.md` stay aligned with the tracked theorem surface; no delivered family regresses below `CURRENT_SHAPE_COMPLETE` or `WORLD_BACKED_COMPLETE`.
- **Golden tests continuously green** — all nine anchors per AGENTS.md: `LowgGoldenTest` (G0, single-aerodrome circuit training), `G1TwoAircraftCircuitsTest` (G1, two-aircraft same-aerodrome circuits=2), `G1TwoAircraftMinimalSpec` (G1-min, two-aircraft circuits=1), `G2CrossAerodromeVfrTest` (G2, LOWG → LJMB cross-aerodrome VFR transit), `G3aPilotTrainedGoAroundTest` (G3a, pilot-trained planned GA), `G3aRunwayObstructionTest` (G3a-obstruction, ATC-instructed reactive GA on world-authored runway obstruction), `G3aRunwayObstructionContinueApproachTest` (G3a-obstruction-continue-approach, pre-clearance CONTINUE APPROACH on world-authored runway obstruction that clears in time), `G3aPilotReactiveCrosswindTest` (G3a-react, pilot-reactive GA off world-authored crosswind exceedance per the aircraft type's POH `maxCrosswindKnots`), and `G3aPilotReactiveTailwindTest` (G3a-react-tailwind, pilot-reactive GA off world-authored tailwind exceedance per the aircraft type's `maxTailwindKnots` — C172 = 10 kt FAA AFH-advisory, B738 = 15 kt FCOM hard limit; per-type doctrinal severity asymmetry surfaces only on the tailwind axis) must remain green at all times; failures are loud, never `@Disabled`.
- **Multi-module Gradle build + detekt clean** — `./gradlew build detekt` passes on `protocol / core / migration / controller / pilot / sim` with zero new detekt violations beyond the pre-existing baseline.
- **Citation discipline** — every `AtcRule` and `Regulation` carries a `(doc, edition, section)` triple anchored to ICAO Doc 4444 17th ed., SERA, ICAO Annex 2/11, Doc 9432, CAP 413, ICAO Doc 8168, or local AIP; orphaned rules fail CI.
- **Requirements-registry mechanical integrity** — declared 46-window slice (ICAO 4444 + 9432 + CAP 413 + EGAST + H01) lands with zero hard regression warnings on the post-clearance-comms baseline; reproducibility audit and accepted-quote audit pass with zero misses.

## Tracks

### FM / Lean proof program (`research/fm`)

Split local certifiers — runway, surface, air-path, separation — plus an optional single-issuer orchestration layer. `Safety-complete (N₀)` and `Full-brief complete` are now closed for the scoped surface; phases 1-4 widening closures are frozen. The active surface is the next deliberate semantic widening branch beyond the current models — polygonal airspace, deeper route-bearing, multi-unit comms, richer heading, or richer operational modes.

_Why it serves the approach:_ Mechanises the discrete guarantees the simulator must defend (every issued safety-critical command passes through a certification path; assumption failures are first-class) without collapsing everything into one giant theorem.

### Runtime simulator (`protocol / core / sim / controller / pilot / migration`)

Pure-fold DES engine (`step(SimState, SimEvent) → (SimState, List<SimEvent>)`) with seeded PRNG; obligation-driven BDI controller across TWR / GND / APP / AREA / AFIS roles; pilot agent separate from day one (AI now, human session later); three-layer transmission/reception (physics / interpretation / controller); world model anchored by `path-network-design.md` (entities reference geometry, not lists of node IDs). G0 / G1 / G2 / G3a (pilot-trained GA) / G3a-obstruction (ATC-instructed reactive GA on world-state runway obstruction) / G3a-obstruction-continue-approach (pre-clearance CONTINUE APPROACH on world-state obstruction that clears in time) / G3a-react (pilot-reactive GA on world-state crosswind exceeding POH `maxCrosswindKnots`) / G3a-react-tailwind (pilot-reactive GA on world-state tailwind exceeding `maxTailwindKnots` — C172 AFH-advisory / B738 FCOM hard limit) are the golden anchors. The pre-clearance approach decision space remains **quadruple-covered** end-to-end: clear (`ARR-LAND` direct), continue-traffic (existing `ARR-CONTINUE` for preceding-traffic / runway-access delays), continue-obstruction (`ARR-CONTINUE-APPROACH-OBSTRUCTION` per fn-13 when `RunwayObstructed AND ObstructionClearsInTime`), and go-around (`ARR-GO-AROUND-RUNWAY-OBSTRUCTED` per fn-12 when `RunwayObstructed AND Not(ObstructionClearsInTime)`). Reactive go-around is now **quintuple-covered**: self-initiated (pilot-side fn-10 era — DA-without-clearance), pilot-trained (G3a via fn-11 typed `CircuitOutcome.GoAround` outcome), ATC-instructed-obstruction (G3a-obstruction via fn-12), **pilot-reactive crosswind (G3a-react via fn-14 — pilot-side recognition off world weather, the first reactive path driven by world state directly observed via a new pilot sensing channel)**, and **pilot-reactive tailwind (G3a-react-tailwind via fn-15 — the second pilot-reactive POH/AFH recognition axis; surfaces the per-type doctrinal severity asymmetry — C172 AFH-advisory tailwind vs B738 FCOM hard-limit tailwind — as a first-class modelling distinction backed by a distinct event leaf, distinct applier, and distinct `RegulationDatabase` anchors)**. This closes the G3a trilogy plus its second pilot-reactive recognition axis. The rich-world-domain principle that landed `Runway.obstruction` (fn-12) now applies to weather: fn-16 migrated `SimState.weatherByAerodrome` to `Aerodrome.weather` atomically (hard cutover, no shim) — time-varying state lives on the entity it concerns. fn-17 reconciled CAP 413 numbering to Edition 24 (effective 1 July 2026): the `CAP_413_EDITION` constant in `RegulationDatabase` is the single edition anchor, and all `CAP413_*` entries cite Ed 24 numbering except two retained Ed 23 inline literals gated by open principle-cite-audit deferments. fn-19 closed the long-standing D-WORLD.2 pre-existing failure by reshaping `LjmbWorldCandidateValidationTest` to a coverage + structural-validity decomposition against the current 5-SID CIFP cycle — `./gradlew build` is green on this path. IFR wiring (IFR-1..6) and approach sequencing are the next live verticals.

_Why it serves the approach:_ The runtime is the thing students see; it must be the place where citation discipline, totality, and reversal-aware testing are visible — the proof program is upstream evidence, not the user-facing artefact.

### Requirements registry (`research/tools/requirements-spike`)

Ollama-first ingestion of ICAO / SERA / CAP / EGAST / H01 regulations into a typed, source-grounded registry. Declared 46-window slice now landed (CAP 413: 8, ICAO 4444: 7, ICAO 9432: 1, plus EGAST + H01). Four-stage adjudication (challenger → defender → bundle gate → judge) generalises across five source families with only mechanical budget bumps. Section-disposition ledger keeps scope explicit.

_Why it serves the approach:_ Without a typed, citation-grounded source for "what does the regulation actually say", the controller's regulation database drifts into folklore; the registry is the load-bearing input that makes citation discipline real.

### Reviewer / agent infrastructure (`agents/`, `AGENTS.md`)

Non-negotiable commandments (no corners cut, no half-baked work, totality, dead programs tell no lies, plans review-aware by construction). Multi-agent review pattern: FP review, ATC general ops, ATC law, ATC phraseology, test architecture — clean context per agent. Principal-agent self-assessment before review. fn-18 reorganized the deferment register into a four-bucket model: `docs/deferments.md` is the in-repo CI-visible canonical surface (replacing the external `~/.claude/plans/pilot-firewall.md` register); `docs/deferments-CONVENTION.md` is the decision-tree doc; entries land in one of four buckets — (1) test contract / API exists today, (2) API gap / commented-out future, (3) multi-task / flow-next epic, (4) narrative / cross-cutting / blocked-on-real-world. 98 active + 37 archive entries at fn-18 close. Autonomous adversarial loop (OR-3) flagged in `.plan` as not-started.

_Why it serves the approach:_ AI-generated code is locally correct and globally blind; this project hardens against that by making reversal invariants and global-state interactions an explicit review concern, not an afterthought.

## Not working on

- Mass automated airport ingestion — worlds are hand-authored via CAD/GIS (decision 2026-04-14); apt.dat / OFMX / CIFP parsers are reference tools, not a translation pipeline.
- Voice / audio NLU interpretation — three-layer architecture is designed (`2026-04-16-transmission-reception-architecture.md`); the interpretation layer is built only when human voice input arrives.
- Generic large-airport scaling — design envelope is 1-2 runways, ~10-30 path segments, ≤10 controlled entities; the formal abstractions are parameterised but the proof techniques target this envelope.
- Performance-based navigation (RNP/RNAV curved segments) — pilot follows published waypoints; autoland and CAT II/III approach automation are out of scope.
- Wake-turbulence separation, LVPs, intersection departures, IFR ground flow (CB-2..7) — controller backlog beyond the current `Safety-complete (N₀)` claim and the scoped envelope.
- TWR1 oracle (50-tick forward sim) and tick-model patches (`instantKinematics`, `radioChannelResolution`) — explicitly retired in the DES engine design.
