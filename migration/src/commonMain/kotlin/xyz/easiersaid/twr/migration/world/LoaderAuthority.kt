package xyz.easiersaid.twr.migration.world

/**
 * Authority grants the loader is willing to materialise from a hand-authored
 * `world-candidate.json`'s `authorities` list.
 *
 * Pass 6 (D-AUDIT.12) introduces [Placeholder] as the sole leaf — the existing
 * `placeholderAuthorities` set lifted from the test fixture into a single
 * named place. **D-AUDIT.11** will add real-authority leaves (e.g.
 * `RunwayAuthority(runway: RunwayId)`, `SeparationAuthority(scope: ...)`) and
 * the JSON↔leaf dispatch at [LoaderAuthoritySerializer] will extend per-leaf.
 *
 * Adding a new leaf forces every consumer of `AerodromeRole.authorities` to
 * extend its sealed-when — structural pressure that makes D-AUDIT.11's
 * closure visible at compile time, not a grep target.
 */
sealed interface LoaderAuthority {
    data object Placeholder : LoaderAuthority

    companion object {
        /** Canonical JSON token for [Placeholder]. The serializer is the dispatcher. */
        const val PLACEHOLDER_TOKEN: String = "PLACEHOLDER"
    }
}
