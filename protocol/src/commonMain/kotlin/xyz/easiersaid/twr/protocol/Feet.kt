package xyz.easiersaid.twr.protocol

/**
 * Typed altitude / elevation in feet.
 *
 * fn-28.1 (R24 / G3a-react-density-altitude): residency confirmed/lifted to
 * `:protocol` (alongside [Knots] / [PressureSetting] / [HeightFeet]) so the
 * typed datum is reachable by both `:protocol`-resident consumers (e.g.
 * `AircraftType.maxDensityAltitudeFt` — added in fn-28.2) and `:pilot`-
 * resident consumers (e.g. `DensityAltitudeInput.fieldElevation` — added
 * in fn-28.1). `:protocol` does NOT depend on `:core`, so the prior
 * `:core/world/WorldModel.kt` residency would have made the protocol-side
 * consumer infeasible without a cyclic dep.
 *
 * Backward-compatible: `:core/world` re-exports via `typealias Feet =
 * xyz.easiersaid.twr.protocol.Feet`, so existing
 * `import xyz.easiersaid.twr.core.world.Feet` references resolve
 * transparently. New code SHOULD import directly from `:protocol`.
 *
 * Smart-constructor invariant matches the pre-fn-28.1 shape: `value >= 0`.
 * Reused by [xyz.easiersaid.twr.core.world.Aerodrome.elevation],
 * [xyz.easiersaid.twr.core.world.HoldingPattern.stackSeparation], and
 * (fn-28.2) `AircraftType.maxDensityAltitudeFt`.
 */
data class Feet(val value: Int) {
    init {
        require(value >= 0) { "Feet must be >= 0" }
    }

    companion object {
        /**
         * Trusted-call-site variant — for test fixtures and compile-time-
         * literal call sites (e.g. fn-28.2's `AircraftType.C172
         * .maxDensityAltitudeFt = Feet.unsafe(5000)` per FAA AC 61-107B §3-1).
         * Throws on the same invariant the public constructor enforces.
         * Pattern mirrors sibling typed units [Knots] / [Temperature] /
         * [PressureSetting.QnhHpa].
         */
        fun unsafe(value: Int): Feet = Feet(value)
    }
}
