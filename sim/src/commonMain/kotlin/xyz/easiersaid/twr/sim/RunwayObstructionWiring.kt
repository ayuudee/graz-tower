package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.controller.observe.ControllerEvent
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.RunwayObstruction
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime

/**
 * fn-12 (R3a): per-cycle world expiry pass.
 *
 * Walks `state.world.aerodromes[*].runways[*].obstruction`; for every
 * runway whose obstruction has `clearsAt <= now`, produces a new world
 * with `runway.obstruction = null`. Pure — no PRNG, no side effects, no
 * randomness.
 *
 * Runs **before** [runwayObstructionEvents] in the sim cycle so the
 * `Cleared` event fires on the same cycle the obstruction expires
 * (otherwise the controller would see `Some` in the diff one tick longer
 * than reality).
 *
 * Returns the input state unchanged when no expiry happened — the world
 * (deeply nested data structure) is rebuilt only when at least one
 * runway's obstruction was nulled.
 */
fun expireRunwayObstructions(state: SimState, now: SimTime): SimState {
    var anyExpired = false
    val updatedAerodromes = state.world.aerodromes.mapValues { (_, aerodrome) ->
        val updatedRunways = aerodrome.runways.mapValues { (_, runway) ->
            val obs = runway.obstruction
            if (obs != null && obs.clearsAt <= now) {
                anyExpired = true
                runway.copy(obstruction = null)
            } else {
                runway
            }
        }
        if (updatedRunways === aerodrome.runways) aerodrome
        else aerodrome.copy(runways = updatedRunways)
    }
    return if (!anyExpired) state
    else state.copy(world = state.world.copy(aerodromes = updatedAerodromes))
}

/**
 * fn-12 (R3b): per-controller world-diff producer.
 *
 * Computes the obstruction-event delta for one controller's runway set.
 * Iterates `current.aerodromes[aerodromeId].runways` and compares against
 * `priorObstructions` (the prior-cycle snapshot for this controller).
 * Emits `RunwayObstructionDetected` on `None → Some(new)` and
 * `RunwayObstructionCleared` on `Some → None`.
 *
 * **Edge-only emission** — persistence (`None → None` or `Some → Some`
 * with same value) emits no event. Per fn-12 Decision #4 the inner
 * `clearsAt` is immutable for an obstruction lifetime, so the
 * `Some(old) → Some(new)` case should never appear; defensive `check(...)`
 * (state-invariant violation) catches a test fixture that violates the
 * invariant (must null first before re-setting).
 *
 * **Per-controller scoping invariant**: events reference only `RunwayId`s
 * within `aerodromeId`'s runway set. Cross-aerodrome routing is filed as
 * `D-PASS-g3a-obstruction-aerodrome-payload`.
 *
 * **Static-runway-set assumption** (fn-12 epic Decision #4): runway
 * membership in `aerodrome.runways` is static across a sim run. The
 * iteration walks `current` keys only; if a future scenario allows
 * runways to be added/removed at runtime, the key set must change to
 * `prior.keys + current.keys` and the diff must handle removal +
 * addition explicitly.
 */
fun runwayObstructionEvents(
    aerodromeId: AerodromeId,
    priorObstructions: Map<RunwayId, RunwayObstruction>,
    current: AviationWorld,
): List<ControllerEvent> {
    val currentRunways = current.aerodromes[aerodromeId]?.runways ?: return emptyList()
    val out = mutableListOf<ControllerEvent>()
    for ((id, runway) in currentRunways) {
        val priorObs: RunwayObstruction? = priorObstructions[id]
        val currentObs: RunwayObstruction? = runway.obstruction
        when {
            priorObs == null && currentObs != null ->
                out.add(ControllerEvent.RunwayObstructionDetected(id, currentObs))
            priorObs != null && currentObs == null ->
                out.add(ControllerEvent.RunwayObstructionCleared(id))
            priorObs != null && currentObs != null -> {
                // Per Decision #4 invariant: clearsAt is immutable for an
                // obstruction lifetime. We should never see Some(old) →
                // Some(new). `check` (IllegalStateException) — state-invariant
                // violation, not arg validation.
                check(priorObs == currentObs) {
                    "Invariant violation: RunwayObstruction.clearsAt mutated mid-lifetime on runway $id " +
                        "(prior=$priorObs, current=$currentObs). Test fixtures must use one-shot " +
                        "authorship; world-state mutations must null first before re-setting."
                }
            }
            else -> Unit // None → None: no event.
        }
    }
    return out
}

/**
 * Snapshot the current world's obstructions for one aerodrome — used to
 * persist the prior-cycle snapshot per controller. Reads
 * `world.aerodromes[aerodromeId].runways[*].obstruction` and filters out
 * the null entries (only present obstructions matter for the next-cycle
 * diff).
 */
fun obstructionsSnapshot(
    aerodromeId: AerodromeId,
    world: AviationWorld,
): Map<RunwayId, RunwayObstruction> {
    val runways = world.aerodromes[aerodromeId]?.runways ?: return emptyMap()
    return runways.mapNotNull { (id, runway) ->
        runway.obstruction?.let { id to it }
    }.toMap()
}
