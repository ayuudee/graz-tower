package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftIntent
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.HighLevelGoal
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.IcaoTypeDesignator

/**
 * Pre-briefing data the controller has on scheduled traffic, modeling the
 * paper or electronic flight strip. The **only** non-radio, non-sensor
 * channel through which sim state can reach the controller.
 *
 * The strip carries the FILED PLAN — the data an AFTN-distributed strip
 * would carry: callsign, broad service intent (from the filed nature:
 * departure / arrival / transit). It is set ONCE at filing and never
 * re-reads pilot mission state.
 *
 * **The strip is NOT dynamic.** Earlier drafts read
 * `mission.root.activeCompound()` per cycle to mirror "rolling annotation
 * by the controller." That was a back-channel: real annotation happens by
 * controller observation (radio + visual), not by reading the pilot's
 * mind. The Departing → Arriving transition for circuit-training aircraft
 * post-landing happens via [xyz.easiersaid.twr.controller.observe.ControllerEvent.AircraftArrivalCommitted]
 * folded into [xyz.easiersaid.twr.controller.observe.BeliefState.aircraftIntent]
 * from a real radio observation (`Report(RunwayVacated)`).
 *
 * Single production site: [toFlightStrip]. The architectural test
 * `FirewallStripStaticTest` enforces that this file reads only filed-plan
 * attributes from `pilotMission` (`goal`, `navigationMode`) — any access
 * to runtime mission state is a firewall regression.
 *
 * Deferment **D-AUDIT.14** records the deeper question: whether
 * [AircraftIntent] should be a stored enum at all, or computed on demand
 * from primary sources (strip + position + radio history). That refactor
 * is outside this file's scope.
 */
data class FlightStrip(
    val aircraft: AircraftId,
    val callsign: Callsign,
    val intent: AircraftIntent,
    /**
     * ICAO Doc 8643 type designator. Pass 10 (D-AUDIT.4): on the
     * controller's strip; the controller sees the type without needing
     * radio. Nullable so VFR flights without a filed plan can still
     * produce a strip projection.
     */
    val icaoTypeDesignator: IcaoTypeDesignator?,
)

/**
 * The sole [AircraftState] → [FlightStrip] projection. Reads only:
 *  - [AircraftState.id], [AircraftState.callsign] — flight-plan callsign;
 *  - [AircraftState.pilotMission].goal — the filed plan's nature.
 *
 * Pre-flight schedule analogue: the strip-issuing system reads the filed
 * flight plan's nature (departure, arrival, transit) and gives the
 * controller a one-line summary. Reading `pilotMission.goal` here is the
 * sim equivalent of reading the filed flight plan, not the pilot's
 * cockpit decisions.
 *
 * G2 Phase F: [observerAerodrome] disambiguates the local service kind for
 * cross-aerodrome Transit flights. A `HighLevelGoal.Transit(destination=B)`
 * flight at aerodrome A is locally Departing; at aerodrome B it is
 * Arriving. Without this context, both controllers see an ambiguous
 * `Transit` intent and the dispatch in `serviceKindForGround` /
 * `serviceKindForTower` falls into the wrong arm (treats the cross-
 * aerodrome flight as already-arrived at the departure aerodrome). The
 * AerodromeId is doctrine-shaped data — the same kind of context the
 * controller's strip board carries about which station the flight is
 * filed to/from.
 */
internal fun AircraftState.toFlightStrip(observerAerodrome: AerodromeId): FlightStrip = FlightStrip(
    aircraft = id,
    callsign = callsign,
    intent = inferIntentFromGoal(pilotMission?.goal, observerAerodrome),
    // Pass 10 (D-AUDIT.4): the controller's strip carries the ICAO type.
    // Reading `state.type.icaoDesignator` is doctrine-shaped data, not
    // pilot-internal — same channel as wakeCategory on SensorReading.
    icaoTypeDesignator = type.icaoDesignator,
)

/**
 * Project a [HighLevelGoal] (the filed plan's nature) into the broad
 * service intent the controller's strip carries, relative to the
 * controller's aerodrome ([observerAerodrome]). Pure function over the
 * goal and observer alone — never reads mission tree, active compound,
 * or any other runtime state.
 *
 * Mapping reflects the filed plan, observed locally:
 *  - `Departure` / `CircuitTraining` → `Departing` (the flight starts as
 *    a departure; circuit training is "departure with full-stop later" —
 *    filed as departure-flow until landing observed by radio).
 *  - `Arrival` → `Arriving`.
 *  - `Transit(destination = obs)` → `Arriving` (this controller's
 *    aerodrome is the destination of the transit leg; the post-landing
 *    radio fold will replace strip intent via `AircraftArrivalCommitted`,
 *    but the strip itself reads the filed plan as if the aircraft is
 *    arriving here).
 *  - `Transit(destination ≠ obs, or null)` → `Departing` (this
 *    controller's aerodrome is the *origin* of the transit leg, so
 *    operationally the aircraft is departing from here).
 *  - null (no mission) → `Transit` as a safe default for unscheduled
 *    traffic.
 */
private fun inferIntentFromGoal(
    goal: HighLevelGoal?,
    observerAerodrome: AerodromeId,
): AircraftIntent = when (goal) {
    null -> AircraftIntent.Transit
    is HighLevelGoal.Arrival -> AircraftIntent.Arriving
    is HighLevelGoal.Departure -> AircraftIntent.Departing
    is HighLevelGoal.CircuitTraining -> AircraftIntent.Departing
    is HighLevelGoal.Transit ->
        if (goal.destination == observerAerodrome) AircraftIntent.Arriving
        else AircraftIntent.Departing
}
