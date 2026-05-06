package xyz.easiersaid.twr.protocol

/**
 * Active runway configuration at an aerodrome — supervisor-set rather
 * than heading-derived. Pass 15 (D-AUDIT.7 closure): replaces the
 * single-runway model where `selectRunwayIntoWind` returned one
 * runway. Real towers run parallel-runway configurations with
 * arrivals and departures assigned independently
 * (ICAO Doc 4444 §7.2 — runway-in-use selection).
 *
 * **OR-invariant** (rather than AND): real ops include arrivals-only
 * and departures-only configurations (LVP, contamination, displaced
 * threshold for departures only). The stricter "both lists must be
 * non-empty" would falsely reject realistic shapes.
 *
 * **Determinism**: [arrivals] and [departures] are `List<RunwayId>`
 * (not `Set`) so iteration order is preserved. The [primary]
 * projection reads from list head — callers pin order at construction.
 *
 * Out of scope (filed deferments):
 *  - Mixed-mode operations (independent / dependent / simultaneous
 *    parallel approach) — D-AUDIT.7.II-FOLLOWUP.
 *  - World-candidate JSON `runwayConfiguration` field — D-WORLD.1
 *    (CAD-authoring pass).
 */
data class RunwayConfiguration(
    val arrivals: List<RunwayId>,
    val departures: List<RunwayId>,
) {
    init {
        require(arrivals.isNotEmpty() || departures.isNotEmpty()) {
            "RunwayConfiguration must have at least one active runway " +
                "(arrivals=$arrivals, departures=$departures)"
        }
        require(arrivals.toSet().size == arrivals.size) {
            "RunwayConfiguration.arrivals must not contain duplicates: $arrivals"
        }
        require(departures.toSet().size == departures.size) {
            "RunwayConfiguration.departures must not contain duplicates: $departures"
        }
    }

    /** Distinct runways across both roles, preserving arrivals-first order. */
    val active: List<RunwayId> get() = (arrivals + departures).distinct()

    /**
     * Primary single-runway projection — the most operationally
     * significant runway. Total: [init] enforces OR-non-empty union,
     * so at least one of [arrivals] or [departures] is non-empty;
     * `arrivals.firstOrNull() ?: departures.first()` then resolves.
     */
    val primary: RunwayId get() = arrivals.firstOrNull() ?: departures.first()
}
