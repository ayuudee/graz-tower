package xyz.easiersaid.twr.sim

/**
 * What the aircraft is doing right now, per the pilot agent.
 *
 * Explicit and sealed — TWR1 inferred phase from the kind of segment an aircraft
 * had just crossed, which breaks the moment multiple circuit directions or
 * parallel taxiways are in play. Here, the phase is set by the pilot and is
 * the source of truth.
 *
 * Ground vs airborne is derived in the wiring layer via [isGroundPhase] so the
 * controller's `onGround` observation is a pure function of phase.
 *
 * 4e-A added the departure airborne phases ([TakeoffRoll], [Climbing],
 * [Crosswind]). 4e-B adds the arrival phases ([Downwind], [Base], [Final],
 * [LandingRoll], [Vacating], [ClearOfRunway]) so a full departure → arrival
 * → park vertical can run end-to-end.
 */
sealed interface PilotPhase {
    // Ground phases.
    data object AtStand : PilotPhase
    data object Taxiing : PilotPhase
    data object HoldingShort : PilotPhase
    data object LinedUp : PilotPhase
    data object Parked : PilotPhase

    // Departure airborne phases.
    data object TakeoffRoll : PilotPhase
    data object Climbing : PilotPhase
    data object Crosswind : PilotPhase

    // Arrival airborne phases.
    data object Downwind : PilotPhase
    data object Base : PilotPhase
    data object Final : PilotPhase

    // Arrival ground phases — between touchdown and the ground controller taking over.
    /**
     * On the runway, decelerating after touchdown. The pilot holds position on
     * the runway until the tower issues an [xyz.easiersaid.twr.protocol.AfterLandingVacateVia]
     * (or equivalent) that writes a ground route off the runway.
     */
    data object LandingRoll : PilotPhase

    /**
     * Taxiing off the runway via the vacate point assigned by tower. The pilot
     * is following a [PilotRoute.Ground]; on reaching its final waypoint (the
     * vacate point itself) the phase becomes [ClearOfRunway] and the aircraft
     * waits for ground's taxi-to-stand instruction.
     */
    data object Vacating : PilotPhase

    /**
     * Clear of the runway, past the vacate point, waiting for a ground taxi
     * instruction. No active route.
     */
    data object ClearOfRunway : PilotPhase
}
