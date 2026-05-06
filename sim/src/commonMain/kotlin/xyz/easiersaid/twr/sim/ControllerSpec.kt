package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.FiledPlan
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.ResponsibilityState
import xyz.easiersaid.twr.protocol.RoleName

/**
 * Registered controller in the simulation.
 *
 * A [ControllerSpec] is the ground-truth side of the boundary the controller
 * module observes through a [xyz.easiersaid.twr.controller.ControllerView]:
 * who the controller is ([id]), what they staff ([role]) at which aerodrome
 * ([aerodromeId]), and which aircraft they are currently responsible for
 * ([responsibilities]).
 *
 * Pass 7 (D-AUDIT.5): [responsibilities] is `Map<AircraftId, ResponsibilityState>`
 * — a typed transfer state machine. Pre-Pass-7 it was `Set<AircraftId>`
 * (boolean membership), which couldn't represent the *transferring* /
 * *watching* states real ATC distinguishes per ICAO Doc 4444 §10.1.
 *
 * Pass 14 (D-AUDIT.6.A-FOLLOWUP / .6.B-FOLLOWUP / .13): [knownStrips]
 * carries strips received via AFTN where the controller does NOT hold
 * responsibility — typically a destination tower informed of an inbound
 * flight before the aircraft physically arrives. **Strip ≠ responsibility**;
 * the structural disjointness is enforced by an [init] invariant. The rule
 * layer (`:controller/commonMain`) must NOT read this field directly;
 * `FirewallKnownStripsReadTest` enforces. The only legitimate consumer
 * is the projection layer in [ControllerWiring].
 *
 * Three derived accessors ([ownedAircraft], [watchingAircraft],
 * [handingOffAircraft]) extract the single-state slices most call sites
 * need — replaces the noisy `responsibilities.entries.filter { ... }`
 * pattern at every call site.
 */
data class ControllerSpec(
    val id: ControllerId,
    val role: RoleName,
    val aerodromeId: AerodromeId,
    val frequency: Frequency,
    val responsibilities: Map<AircraftId, ResponsibilityState>,
    /**
     * Strips received via AFTN where the controller has prior knowledge
     * but does NOT hold responsibility. Pass 14 (D-AUDIT.6.A-FOLLOWUP /
     * .6.B-FOLLOWUP / .13). Default empty — non-cross-aerodrome scenarios
     * (single-aerodrome circuit-training, the G0 LOWG fixture) carry no
     * known-strips entries.
     */
    val knownStrips: Map<AircraftId, FiledPlan> = emptyMap(),
) {
    init {
        // Pass 14 disjointness invariant: an aircraft is either a strip-
        // only known to the controller (knownStrips) OR an active
        // responsibility (responsibilities) — never both. The handler in
        // `Step.applyContactFrequency` removes the entry from knownStrips
        // when the responsibility state machine first reaches Watching;
        // a structural require makes the rule un-violatable.
        val overlap = knownStrips.keys intersect responsibilities.keys
        require(overlap.isEmpty()) {
            "ControllerSpec($id): aircraft IDs in both knownStrips and responsibilities " +
                "violates the strip≠responsibility disjointness invariant: $overlap"
        }
    }

    /** Aircraft this controller currently OWNS (talks to directly). */
    val ownedAircraft: Set<AircraftId> get() =
        responsibilities.entries.filter { it.value is ResponsibilityState.Owned }.map { it.key }.toSet()

    /** Aircraft this controller is WATCHING (incoming handoff, expects InitialContact). */
    val watchingAircraft: Set<AircraftId> get() =
        responsibilities.entries.filter { it.value is ResponsibilityState.Watching }.map { it.key }.toSet()

    /** Aircraft this controller has issued a handoff for, awaiting pilot's InitialContact (or readback for boundary release). */
    val handingOffAircraft: Set<AircraftId> get() =
        responsibilities.entries.filter { it.value is ResponsibilityState.HandingOff }.map { it.key }.toSet()

    companion object {
        /**
         * Construct a [ControllerSpec] with all responsibilities in [ResponsibilityState.Owned]
         * since [now]. Convenience for tests and fixtures that pre-populate responsibilities
         * (the typical case before D-AUDIT.6's filing event lands and replaces fixture-pre-pop).
         */
        fun withOwned(
            id: ControllerId,
            role: RoleName,
            aerodromeId: AerodromeId,
            frequency: Frequency,
            ownedAircraft: Set<AircraftId> = emptySet(),
            now: xyz.easiersaid.twr.protocol.SimTime = xyz.easiersaid.twr.protocol.SimTime.ZERO,
        ): ControllerSpec = ControllerSpec(
            id = id,
            role = role,
            aerodromeId = aerodromeId,
            frequency = frequency,
            responsibilities = ownedAircraft.associateWith { ResponsibilityState.Owned(now) },
        )
    }
}
