package xyz.easiersaid.twr.pilot

import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.pilot.world.PilotAviationWorld
import xyz.easiersaid.twr.protocol.PilotTransmission
import xyz.easiersaid.twr.protocol.SimTime

/**
 * The complete and exclusive input to the pilot's decision function.
 *
 * Every field of [PilotInput] must map to a real-world cockpit input: own
 * kinematic state, own filed plan, own visual observation, world geometry
 * (chart). The named-argument constructor below is the FIREWALL ALLOWLIST.
 * Adding a field requires it to map to a real-world cockpit input AND a
 * deliberate update of `FirewallPilotInputTest`. A widening that smuggles
 * in controller-side state (BeliefState, ControllerSpec, ControllerView, or
 * anything reachable from them) is a firewall regression and is forbidden
 * by the no-suppression rule.
 *
 * **Note on radio reception**: received [xyz.easiersaid.twr.protocol.AtcInstruction]s
 * are folded into [PilotMission] via `processInstruction` *outside* the
 * pilot-decision tick, at the moment of delivery (`Step.handlePilotProcessingComplete`).
 * By the time `pilotDecide` runs, every radio fact the pilot needs has
 * already been recorded on `PilotMission` (activeRunway, contactedOnFrequency,
 * routeOverride, hasClearance, etc.). [PilotInput] therefore carries no
 * radio inbox — there is nothing for the tick to read that is not already
 * on `aircraft.pilotMission`.
 *
 * **No-suppression rule:** an architectural test failure that prevents
 * widening this type is never resolved by `@Suppress`, `@Disabled`, or
 * test removal. Resolve by either justifying the widening as a real-world
 * cockpit input (and updating the firewall test deliberately) or by
 * routing the data through `processInstruction` if it is radio-derived.
 */
data class PilotInput(
    val aircraft: AircraftState,
    val worldIndex: WorldIndex,
    /**
     * fn-24 (closes `D-PASS-pilot-world-strip-dynamic-state`): the
     * pilot reads the chart-equivalent world via the typed
     * [xyz.easiersaid.twr.pilot.world.PilotAviationWorld] projection.
     * The projection omits entity-level dynamic state
     * ([xyz.easiersaid.twr.core.world.Aerodrome.weather] and
     * [xyz.easiersaid.twr.core.world.Runway.obstruction]) so reading
     * those fields from pilot code fails to compile. Structural
     * enforcement replaces the prior convention-via-KDoc discipline.
     * `:sim` projects via
     * [xyz.easiersaid.twr.pilot.world.toPilotView] at the firewall
     * boundary ([xyz.easiersaid.twr.sim.PilotWiring.buildPilotInput]).
     */
    val world: PilotAviationWorld,
    val now: SimTime,
    /**
     * Pass 15 (D-AUDIT.8 closure): per-aerodrome ATIS broadcast as
     * heard by the pilot. Real pilots tune the ATIS frequency before
     * first contact and read the letter into the cockpit; the
     * `InitialContact` transmission embeds the letter so the
     * controller can verify currency.
     *
     * Lazy-read shape (post-impl review M2): the pilot reads the
     * letter at the moment of `MissionStep.CALL_INBOUND` transmission,
     * not at spawn. This eliminates Spawn-vs-AtisIssued ordering
     * concerns — whatever ATIS is published at the moment of first
     * contact is what the pilot acknowledges.
     *
     * **Doctrine**: ICAO Annex 11 §4.3 (ATIS service); Doc 4444 §4.5.5
     * (broadcast content).
     */
    val atisByAerodrome: Map<xyz.easiersaid.twr.protocol.AerodromeId, xyz.easiersaid.twr.protocol.Atis> = emptyMap(),
    /**
     * fn-14.1 (G3a-react R3): per-aerodrome wind state as observed by
     * the pilot — windsock crosscheck + ASI + instrument scan. The
     * sim projects `world.aerodromes[aerodrome].weather?.wind` into
     * this map (only the [xyz.easiersaid.twr.protocol.WindReport]
     * slice; the full
     * [xyz.easiersaid.twr.core.world.WeatherObservation] triple stays
     * on the entity — QNH/visibility are not pilot crosswind inputs
     * in v1).
     *
     * Read by the pilot's reactive-GA recognition for POH crosswind-
     * limit exceedance (`derivePilotEvent`'s crosswind branch). v1
     * sources from real-time world weather; the ATIS-cadence sensing
     * path is filed as `D-PASS-g3a-react-atis-cadence-sensing`. fn-16
     * closed `D-PASS-wind-state-migrate-to-aerodrome` by hoisting
     * weather onto [xyz.easiersaid.twr.core.world.Aerodrome.weather];
     * the pilot firewall surface (this field's `Map<AerodromeId,
     * WindReport>` shape) is unchanged — only the source migrates.
     *
     * **Firewall**: a real-world cockpit input (visual / instrument
     * sensing). `FirewallPilotInputTest` enumerates the allowlist
     * (canonical-constructor entry + reflection-based property
     * scan). Adding a non-cockpit field via this map is forbidden.
     *
     * Default `emptyMap()` preserves all existing PilotInput
     * construction sites (additive widening).
     */
    val weatherByAerodrome: Map<xyz.easiersaid.twr.protocol.AerodromeId, xyz.easiersaid.twr.protocol.WindReport> =
        emptyMap(),
    /**
     * fn-28.1 (G3a-react-density-altitude foundation A): per-aerodrome
     * typed density-altitude inputs (OAT + QNH + field elevation) as
     * projected at the firewall boundary by
     * [xyz.easiersaid.twr.sim.PilotWiring.buildPilotInput].
     *
     * The pilot's real-world DA sensing channel is the ATIS broadcast
     * (`Aerodrome.weather.oat` + `Aerodrome.weather.qnh` per ICAO Annex
     * 11 §4.3.6.h) crossed with the published field elevation
     * (`Aerodrome.elevation`, a chart datum). The projection materialises
     * the three typed values into a [DensityAltitudeInput] only when ALL
     * three preconditions hold — fail-closed when OAT or QNH is null on
     * the aerodrome's weather (no entry produced; downstream DA
     * recognition in fn-28.2 skips that aerodrome).
     *
     * Read by the pilot's density-altitude reactive-GA recognition
     * branch landing in fn-28.2 (`derivePilotEvent`'s DA branch). The
     * signature lands at fn-28.1 with a placeholder body — the branch
     * itself, and the `AircraftType.maxDensityAltitudeFt` gate, are
     * fn-28.2 work.
     *
     * **Firewall**: a real-world cockpit input (ATIS-read OAT/QNH +
     * published chart elevation). `FirewallPilotInputTest` enumerates
     * the allowlist (canonical-constructor entry + reflection-based
     * property scan). Adding a non-cockpit field via this map is
     * forbidden — [DensityAltitudeInput] is structurally constrained
     * to typed-units + scalars (no entity reference reachable).
     *
     * Default `emptyMap()` preserves all existing `PilotInput`
     * construction sites (additive widening).
     */
    val densityAltitudeInputsByAerodrome: Map<xyz.easiersaid.twr.protocol.AerodromeId, DensityAltitudeInput> =
        emptyMap(),
)

/**
 * The pilot's decision for one tick. Total — every successful decision
 * carries kinematic intent, any transmissions to issue, and the updated
 * mission state.
 *
 * Routing errors are carried in `Either<RoutingError, PilotOutput>` at
 * the call site (`pilotDecide`), not as a side field on this type. The
 * freeze-on-routing-error policy lives at the sim boundary
 * (`Step.handlePilotTick`), not inside the success type — the FP-correct
 * separation of "the pilot decided" from "the simulator handles a routing
 * defect."
 */
data class PilotOutput(
    val intent: PilotIntent,
    val transmissions: List<PilotTransmission>,
    val updatedMission: PilotMission?,
)
