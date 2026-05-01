package xyz.easiersaid.twr.migration.world

import xyz.easiersaid.twr.core.world.AuthorityGrant
import xyz.easiersaid.twr.protocol.AuthorityEntityType
import xyz.easiersaid.twr.protocol.AuthorityOperation

/**
 * Single source of truth for loader defaults that exist only because a richer
 * model isn't yet wired.
 *
 * Pass 6 (D-AUDIT.12 closure) lifts [placeholderAuthorities] out of test
 * fixtures and into one named place. The bridge from
 * [LoaderAuthority.Placeholder] to the existing `Set<AuthorityGrant>` lives
 * here. When D-AUDIT.11 adds real authority leaves, this object extends with
 * per-leaf bridge functions and the sealed-when at every consumer of
 * `AerodromeRole.authorities` forces the migration.
 */
object LoaderDefaults {
    /**
     * The single-CONTACT authority grant the test fixtures previously injected.
     * Sufficient for `HandoffAction.resolve` to succeed; not a real authority
     * model. D-AUDIT.11 owns the replacement.
     */
    val placeholderAuthorities: Set<AuthorityGrant> = setOf(
        AuthorityGrant(
            entityType = AuthorityEntityType.RADIO_ROLE,
            operations = setOf(AuthorityOperation.CONTACT),
        ),
    )

    /** Bridge `LoaderAuthority` → the loader's `Set<AuthorityGrant>` shape. */
    fun toAuthorityGrants(authorities: List<LoaderAuthority>): Set<AuthorityGrant> {
        if (authorities.isEmpty()) return emptySet()
        val grants = mutableSetOf<AuthorityGrant>()
        for (a in authorities) {
            when (a) {
                is LoaderAuthority.Placeholder -> grants.addAll(placeholderAuthorities)
            }
        }
        return grants
    }
}
