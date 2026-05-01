package xyz.easiersaid.twr.sim

/**
 * Seed-splittable deterministic PRNG based on SplitMix64.
 *
 * Every sampling site returns `(value, newRandom)` — the caller threads the
 * new generator forward through [SimState.rng]. The same seed + event sequence
 * always produces byte-identical traces, so tests and replays are reliable.
 *
 * Per-agent streams are obtained via [split]: hash a stable tag (an agent id,
 * a phase name) into the state to get a child generator whose output is
 * independent of how many other agents are active. That keeps "this aircraft
 * rolled a 2.3 s readback delay" stable even if another aircraft spawns first.
 *
 * SplitMix64 is the same construction behind Java's `SplittableRandom` mixer —
 * simple, fast, statistically respectable, and bit-reproducible across JVMs.
 */
@JvmInline
value class SimRandom(val state: Long) {

    /** Advance the state and return a uniformly-distributed Long. */
    fun nextLong(): Pair<Long, SimRandom> {
        val next = state + GOLDEN_GAMMA
        return mix(next) to SimRandom(next)
    }

    /** Uniform double in [0.0, 1.0). */
    fun nextDouble(): Pair<Double, SimRandom> {
        val (l, r) = nextLong()
        val bits = l ushr 11
        return bits.toDouble() / DOUBLE_DIVISOR to r
    }

    /**
     * Derive a deterministic child generator keyed by [tag]. The child's output
     * is independent of the parent's future draws (the parent's stream is not
     * advanced), so splitting `alpha` and `bravo` from the same parent at the
     * same moment gives two independent streams.
     */
    fun split(tag: String): SimRandom {
        // Mix the tag's hash into a fresh state via the SplitMix64 mixer.
        val seed = state xor (tag.hashCode().toLong() * GOLDEN_GAMMA)
        return SimRandom(mix(seed))
    }

    companion object {
        private val GOLDEN_GAMMA: Long = 0x9E3779B97F4A7C15UL.toLong()
        private val MIX_C1: Long = 0xBF58476D1CE4E5B5UL.toLong()
        private val MIX_C2: Long = 0x94D049BB133111EBUL.toLong()
        private val DOUBLE_DIVISOR: Double = (1L shl 53).toDouble()

        fun ofSeed(seed: Long): SimRandom = SimRandom(seed)

        private fun mix(zIn: Long): Long {
            var z = zIn
            z = (z xor (z ushr 30)) * MIX_C1
            z = (z xor (z ushr 27)) * MIX_C2
            return z xor (z ushr 31)
        }
    }
}
