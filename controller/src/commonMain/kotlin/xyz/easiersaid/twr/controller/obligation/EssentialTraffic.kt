package xyz.easiersaid.twr.controller.obligation

import xyz.easiersaid.twr.controller.AircraftObservation
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.SimTime

/**
 * Essential traffic information obligation (Doc 4444 §6.5.3, §11.4.2.1).
 *
 * Mandatory when prescribed separation is not applied — classically VFR vs IFR
 * in Class D, or reduced-separation authorisations. Modelled as its own obligation
 * with trigger, timing, and geometry-based expiry. NOT a companion payload.
 *
 * Symmetric: essential traffic to departures about arriving traffic (the inverse
 * flow most plans forget — Doc 4444 §6.5.3 doesn't distinguish direction).
 *
 * See design doc §3.5 (2026-04-19-approach-sequencing.md).
 */
data class EssentialTrafficObligation(
    /** Aircraft that must receive the traffic information. */
    val target: AircraftId,
    /** The conflicting traffic to report. */
    val conflict: AircraftId,
    /** When this obligation was created. */
    val createdAt: SimTime,
    /** When information was last transmitted (null = not yet issued). */
    val lastIssuedAt: SimTime? = null,
)

/**
 * Derive essential traffic obligations from the current state.
 *
 * Trigger: two aircraft in proximity where prescribed separation is not applied.
 * For Phase 5: any departure at the holding point or lined up when an arrival is
 * on base/final, and vice versa.
 *
 * Expiry: conflict geometry no longer applicable (not a boolean flip — re-evaluated
 * per cycle). The same pair can generate new obligations as geometry evolves.
 */
fun deriveEssentialTrafficObligations(
    beliefs: BeliefState,
    worldIndex: WorldIndex?,
): List<EssentialTrafficObligation> {
    val obligations = mutableListOf<EssentialTrafficObligation>()
    val aircraft = beliefs.trackedAircraft

    // Find arrivals on base/final and departures at holding/runway.
    val arrivalsOnApproach = aircraft.filter { (_, ac) ->
        !ac.onGround && isOnBaseOrFinal(ac, worldIndex)
    }
    val departuresAtRunway = aircraft.filter { (_, ac) ->
        ac.onGround && isAtHoldingOrRunway(ac, worldIndex)
    }

    // Symmetric: arrival needs to know about departure, departure needs to know about arrival.
    for ((arrId, _) in arrivalsOnApproach) {
        for ((depId, _) in departuresAtRunway) {
            obligations.add(EssentialTrafficObligation(target = arrId, conflict = depId, createdAt = SimTime.ofSeconds(0)))
            obligations.add(EssentialTrafficObligation(target = depId, conflict = arrId, createdAt = SimTime.ofSeconds(0)))
        }
    }

    return obligations
}

private fun isOnBaseOrFinal(ac: AircraftObservation, worldIndex: WorldIndex?): Boolean {
    val legs = worldIndex?.circuitLegsByPoint?.get(ac.position) ?: emptySet()
    val onApproach = ac.entities.any { it is EntityRef.ApproachRef }
    return LegName.BASE in legs || LegName.FINAL in legs || onApproach
}

private fun isAtHoldingOrRunway(ac: AircraftObservation, worldIndex: WorldIndex?): Boolean {
    val onRunway = ac.entities.any { it is EntityRef.RunwayRef }
    val atHolding = worldIndex?.holdingPointsByRunway?.values?.any { points ->
        ac.position in points
    } ?: false
    return onRunway || atHolding
}
