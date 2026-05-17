package xyz.easiersaid.twr.pilot

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import xyz.easiersaid.twr.protocol.PilotTransmission

/**
 * Pilot-decide dispatch helpers extracted from [pilotDecide] for detekt
 * complexity / file-function-count compliance.
 *
 * **Why a separate file (not private in Pilot.kt):**
 *  - `Pilot.kt` is already at detekt's `TooManyFunctions` threshold (25).
 *    Adding the dispatch + output helpers in-place pushes it over.
 *  - These helpers are co-located in the same package so visibility stays
 *    `internal` (file-private would block `pilotDecide`'s call).
 *  - Conceptually they form `pilotDecide`'s outer-shell dispatch fabric —
 *    the self-init `when` over `PilotEvent` leaves + the final
 *    Plan/Skip/Failed PilotOutput construction. Splitting them out keeps
 *    `pilotDecide` focused on the GA-precedence + mission-fold logic.
 *
 * Both helpers are pure functions; co-occurrence-impossible result slots
 * (e.g., `goAround` vs `densityAltitudeDecline` vs `abortTakeoff` in
 * [SelfInitiatedDispatchResult]) reflect `PilotEvent`'s sealed-leaf
 * disjointness — one event surfaces per call.
 */

/**
 * Result bundle from [dispatchSelfInitiatedEvent]. The three apply-path
 * outcomes are co-occurrence-impossible by `PilotEvent`'s sealed-leaf
 * disjointness (one event surfaces per call), but the bundle's three-slot
 * shape mirrors the elvis-chain precedence in [pilotDecide] for reader
 * clarity.
 */
internal data class SelfInitiatedDispatchResult(
    val goAround: GoAroundResult?,
    val densityAltitudeDecline: DensityAltitudeDeclineResult?,
    val abortTakeoff: AbortTakeoffResult?,
)

/**
 * fn-28 round-detekt extraction: the self-initiated `derivePilotEvent`
 * dispatch. Resolves the per-aerodrome wind + DA typed inputs, calls
 * `derivePilotEvent`, and dispatches the matched leaf to its apply path.
 * Called from [pilotDecide] only when trained-GA and ATC-reactive both
 * did NOT fire (per spec R9c — preserves trigger tick + emission
 * contract).
 *
 * Branch order in the `when` mirrors `derivePilotEvent`'s documented
 * branch order (R21): DA-without-clearance → DA-decline → AbortTakeoff →
 * Tailwind → Crosswind. Functionally order-independent (sealed leaves
 * are disjoint), but the visual alignment aids reader clarity.
 */
internal fun dispatchSelfInitiatedEvent(
    aircraft: AircraftState,
    mission: PilotMission,
    input: PilotInput,
): SelfInitiatedDispatchResult {
    val weather = windForMission(mission, input.weatherByAerodrome)
    val densityAltitudeInput = densityAltitudeInputForMission(
        mission, input.densityAltitudeInputsByAerodrome,
    )
    val pilotEvent = xyz.easiersaid.twr.pilot.observe.derivePilotEvent(
        aircraft, mission, weather, densityAltitudeInput,
    )
    return when (pilotEvent) {
        is xyz.easiersaid.twr.pilot.observe.PilotEvent.DecisionAltitudeWithoutClearance ->
            SelfInitiatedDispatchResult(
                goAround = applySelfInitiatedGoAround(pilotEvent, mission, aircraft, input.now),
                densityAltitudeDecline = null,
                abortTakeoff = null,
            )
        is xyz.easiersaid.twr.pilot.observe.PilotEvent.TailwindLimitExceeded ->
            SelfInitiatedDispatchResult(
                goAround = applyTailwindGoAround(pilotEvent, mission, aircraft, input.now),
                densityAltitudeDecline = null,
                abortTakeoff = null,
            )
        is xyz.easiersaid.twr.pilot.observe.PilotEvent.CrosswindLimitExceeded ->
            SelfInitiatedDispatchResult(
                goAround = applyCrosswindGoAround(pilotEvent, mission, aircraft, input.now),
                densityAltitudeDecline = null,
                abortTakeoff = null,
            )
        is xyz.easiersaid.twr.pilot.observe.PilotEvent.DensityAltitudeDecline ->
            SelfInitiatedDispatchResult(
                goAround = null,
                densityAltitudeDecline = applyDensityAltitudeDecline(pilotEvent, mission, aircraft),
                abortTakeoff = null,
            )
        is xyz.easiersaid.twr.pilot.observe.PilotEvent.AbortTakeoff ->
            SelfInitiatedDispatchResult(
                goAround = null,
                densityAltitudeDecline = null,
                abortTakeoff = applyAbortTakeoff(pilotEvent, mission, aircraft),
            )
        // AtcGoAroundOnFinal is constructed only at the recognition site
        // in `recognizeAtcInitiatedGoAround`; `derivePilotEvent` never
        // produces it. Explicit no-op pins the contract.
        is xyz.easiersaid.twr.pilot.observe.PilotEvent.AtcGoAroundOnFinal, null ->
            SelfInitiatedDispatchResult(goAround = null, densityAltitudeDecline = null, abortTakeoff = null)
    }
}

/**
 * fn-28 round-detekt extraction: post-plan PilotOutput construction.
 *
 * Plan-vs-Skip intent precedence is structurally distinct:
 *  - **Plan branch** (the planner produced a route): only DA-decline /
 *    abort intents can pre-empt the route. GA-path intents do NOT apply
 *    because GAs run in Skip mode (route=None, phase=Final reuses Tick B
 *    Circuit-mode planning, NOT planRoute's airborne path).
 *  - **Skip branch** (no route needed): full GA-path precedence chain
 *    applies — trained-GA → ATC-reactive → DA-decline → abort → self-init
 *    → cognitive override fallback.
 */
@Suppress("LongParameterList")
internal fun buildPilotOutput(
    planOutcome: PlanRouteOutcome,
    kinematicIntent: PilotIntent,
    effectiveMission: PilotMission,
    plannedGoAround: PlannedGoAroundResult?,
    atcGoAroundOutcome: RecognizedAtcGoAround?,
    densityAltitudeDecline: DensityAltitudeDeclineResult?,
    abortTakeoff: AbortTakeoffResult?,
    goAround: GoAroundResult?,
    effectiveCognitiveTransmissions: List<PilotTransmission>,
    goAroundTransmissions: List<PilotTransmission>,
): Either<RoutingError, PilotOutput> = when (planOutcome) {
    is PlanRouteOutcome.Failed -> planOutcome.error.left()
    is PlanRouteOutcome.Plan -> PilotOutput(
        intent = densityAltitudeDecline?.intent
            ?: abortTakeoff?.intent
            ?: planOutcome.intent,
        transmissions = effectiveCognitiveTransmissions + goAroundTransmissions,
        updatedMission = densityAltitudeDecline?.mission
            ?: abortTakeoff?.mission
            ?: planOutcome.mission,
    ).right()
    is PlanRouteOutcome.Skip -> PilotOutput(
        intent = plannedGoAround?.intent
            ?: atcGoAroundOutcome?.intent
            ?: densityAltitudeDecline?.intent
            ?: abortTakeoff?.intent
            ?: goAround?.intent
            ?: applyCognitiveOverrides(kinematicIntent, effectiveMission),
        transmissions = effectiveCognitiveTransmissions + goAroundTransmissions,
        updatedMission = effectiveMission,
    ).right()
}
