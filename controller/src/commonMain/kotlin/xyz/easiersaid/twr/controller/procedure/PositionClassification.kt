package xyz.easiersaid.twr.controller.procedure

import xyz.easiersaid.twr.controller.AircraftObservation
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.Knots
import xyz.easiersaid.twr.protocol.RunwayId

/**
 * Position classification for departure procedures.
 *
 * What the controller observes about a departing aircraft's physical situation,
 * independent of what was expected. Derived from entity refs, circuit leg
 * metadata, ground/airborne status, and ground speed.
 */
sealed interface DeparturePosition {
    /** On the ground, not on the runway. At or near holding point, or taxiing toward it. */
    data object AtHolding : DeparturePosition
    /** On the ground, on the runway surface, not visibly rolling (stationary or low speed). */
    data object OnRunway : DeparturePosition
    /** On the ground, on the runway, rolling at significant ground speed (takeoff roll). */
    data object OnRunwayRolling : DeparturePosition
    /** Airborne but still over the runway strip (just rotated). */
    data object AirborneOverRunway : DeparturePosition
    /** Airborne on the upwind or crosswind leg (climbout). */
    data object OnClimbout : DeparturePosition
    /** Somewhere else entirely — not at any expected departure position. */
    data object Elsewhere : DeparturePosition
}

/**
 * Position classification for arrival procedures.
 *
 * What the controller observes about an arriving aircraft's physical situation.
 */
sealed interface ArrivalPosition {
    /** Airborne on the downwind leg. */
    data object OnDownwind : ArrivalPosition
    /** Airborne on the base leg. */
    data object OnBase : ArrivalPosition
    /** Airborne on the final approach (circuit final or straight-in). */
    data object OnFinal : ArrivalPosition
    /** Airborne on approach (non-circuit, e.g. ILS established). */
    data object OnApproach : ArrivalPosition
    /** Airborne but not on a recognisable circuit leg or approach. */
    data object AirborneElsewhere : ArrivalPosition
    /** On the ground, on the runway surface. */
    data object OnRunway : ArrivalPosition
    /** On the ground, clear of the runway. */
    data object ClearOfRunway : ArrivalPosition
    /** Somewhere else not covered above. */
    data object Elsewhere : ArrivalPosition
}

/**
 * Position classification for ground taxi procedures.
 *
 * What the controller observes about a ground-movement aircraft's situation.
 */
sealed interface GroundPosition {
    /** At a stand (parking position). */
    data object AtStand : GroundPosition
    /** On the ground, not at a stand, not at a holding point, not on a runway. */
    data object Taxiing : GroundPosition
    /** At a runway holding point. */
    data object AtHoldingPoint : GroundPosition
    /** On the runway surface (unexpected for ground — possible incursion). */
    data object OnRunway : GroundPosition
    /** Somewhere the ground controller doesn't expect. */
    data object Elsewhere : GroundPosition
}

// ── Classification functions ────────────────────────────────────────

/** Ground speed above which an aircraft on the runway is considered "rolling." */
private val ROLLING_THRESHOLD_KT = Knots.unsafe(30)

/** Classify a departing aircraft's observed position. */
fun classifyDeparturePosition(
    ac: AircraftObservation,
    worldIndex: WorldIndex,
): DeparturePosition {
    val onRunway = ac.entities.any { it is EntityRef.RunwayRef }
    val circuitLegs = worldIndex.circuitLegsByPoint[ac.position].orEmpty()
    val onClimboutLeg = LegName.UPWIND in circuitLegs || LegName.CROSSWIND in circuitLegs

    return when {
        // Airborne cases
        !ac.onGround && onClimboutLeg -> DeparturePosition.OnClimbout
        !ac.onGround && onRunway -> DeparturePosition.AirborneOverRunway
        !ac.onGround -> DeparturePosition.OnClimbout // airborne, off runway strip → treat as climbout
        // Ground, on runway
        onRunway && isRolling(ac) -> DeparturePosition.OnRunwayRolling
        onRunway -> DeparturePosition.OnRunway
        // Ground, not on runway
        else -> DeparturePosition.AtHolding
    }
}

/** Classify an arriving aircraft's observed position. */
fun classifyArrivalPosition(
    ac: AircraftObservation,
    worldIndex: WorldIndex,
): ArrivalPosition {
    val onRunway = ac.entities.any { it is EntityRef.RunwayRef }
    val circuitLegs = worldIndex.circuitLegsByPoint[ac.position].orEmpty()
    val onApproach = ac.entities.any { it is EntityRef.ApproachRef }

    return when {
        // Ground cases
        ac.onGround && onRunway -> ArrivalPosition.OnRunway
        ac.onGround -> ArrivalPosition.ClearOfRunway
        // Airborne circuit legs
        LegName.FINAL in circuitLegs -> ArrivalPosition.OnFinal
        LegName.BASE in circuitLegs -> ArrivalPosition.OnBase
        LegName.DOWNWIND in circuitLegs -> ArrivalPosition.OnDownwind
        // Approach procedure (ILS, etc.)
        onApproach -> ArrivalPosition.OnApproach
        // Airborne, over runway (short final, just past threshold)
        onRunway -> ArrivalPosition.OnFinal
        // Airborne elsewhere
        !ac.onGround -> ArrivalPosition.AirborneElsewhere
        else -> ArrivalPosition.Elsewhere
    }
}

/** Classify a ground-movement aircraft's observed position. */
fun classifyGroundPosition(
    ac: AircraftObservation,
    activeRunway: RunwayId?,
    worldIndex: WorldIndex,
): GroundPosition {
    val onRunway = ac.entities.any { it is EntityRef.RunwayRef }
    val atStand = ac.entities.any { it is EntityRef.StandRef }
    val atHolding = activeRunway != null &&
        worldIndex.holdingPointsByRunway[activeRunway]?.contains(ac.position) == true

    return when {
        !ac.onGround -> GroundPosition.Elsewhere // airborne is unexpected for ground
        onRunway -> GroundPosition.OnRunway
        atHolding -> GroundPosition.AtHoldingPoint
        atStand -> GroundPosition.AtStand
        else -> GroundPosition.Taxiing
    }
}

private fun isRolling(ac: AircraftObservation): Boolean {
    val gs = ac.groundSpeed ?: return false
    return gs.value >= ROLLING_THRESHOLD_KT.value
}
