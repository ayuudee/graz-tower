package xyz.easiersaid.twr.core.world

import xyz.easiersaid.twr.protocol.AerodromeId

/**
 * Single-aerodrome lens helper. Replaces `world.aerodromes[id]` with
 * `transform(world.aerodromes[id])`, returning a new [AviationWorld]
 * with the entry updated. No-op (returns the input unchanged) when the
 * id is absent or when the transform produces an identity-equal value.
 *
 * Counterpart of the all-aerodromes walk at
 * `sim/.../RunwayObstructionWiring.kt:27-44` (fn-12's
 * `expireRunwayObstructions`). The single-id form is the right shape
 * for fn-16's weather mutations — a test fixture authors a wind shift
 * at one aerodrome at a time, not at every aerodrome at once.
 *
 * **Validation-boundary caveat**: the no-op-on-absent-id semantics is
 * appropriate for a generic mid-run lens (idempotent / safe) but NOT
 * for validating constructors. Smart constructors that fold via this
 * lens must validate the id set BEFORE the fold — see
 * `SimState.initial`'s `WeatherForUnknownAerodrome` pre-fold check.
 *
 * Inline to avoid lambda allocation per call.
 */
inline fun AviationWorld.updateAerodrome(
    id: AerodromeId,
    transform: (Aerodrome) -> Aerodrome,
): AviationWorld {
    val current = aerodromes[id] ?: return this
    val updated = transform(current)
    return if (updated === current) this
    else copy(aerodromes = aerodromes + (id to updated))
}
