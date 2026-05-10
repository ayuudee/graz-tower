package xyz.easiersaid.twr.core.world

/**
 * Regulatory doctrine constants — the single source of truth for ICAO /
 * regional floors, ceilings, and other "the rule says exactly N" values
 * the runtime depends on.
 *
 * Each nested object cites the exact regulatory document, section, and
 * edition. KDoc paraphrases (in implementer's own words) — verbatim
 * quotation is intentionally avoided because we do not redistribute the
 * source documents and the existing in-repo Annex 11 references at
 * `controller/.../ControllerTypes.kt` use the same short-cite-no-verbatim
 * convention.
 */
object Doctrine {

    /**
     * ICAO Annex 11 — Air Traffic Services, 15th edition, July 2018.
     *
     * Used at:
     *  - the [Aerodrome.ctrApproximationRadius] primary-constructor default
     *    (= [CTR_FLOOR_5NM]).
     *  - [xyz.easiersaid.twr.migration.world.WorldCandidateLoader]'s
     *    `?: CTR_FLOOR_5NM` fallback when no per-aerodrome value is
     *    authored, and the `require(n >= CTR_FLOOR_NAUTICAL_MILES)`
     *    sub-floor rejection on authored JSON values.
     *
     * The numeric `5` lives here in exactly one place
     * ([CTR_FLOOR_NAUTICAL_MILES]); [CTR_FLOOR_5NM] is derived from it
     * via [Meters.fromNauticalMiles]. The two-constant shape eliminates
     * numeric drift between the metres value at the runtime read site
     * and the schema-unit (nautical-mile) value at the loader's
     * authored-bounds-check site.
     */
    object IcaoAnnex11 {

        /**
         * Source numeric — the regulatory floor value in the unit it is
         * stated in (nautical miles).
         *
         * Per ICAO Annex 11, 15th ed., July 2018, §2.11 (control zone
         * lateral limits): the lateral limits of a control zone shall
         * extend at least 5 NM (9.3 km) from the aerodrome reference
         * point in the directions from which approaches may be made.
         *
         * **Directional minimum, not a polygon shape.** This is a
         * regulatory floor along every approach axis — a 5 NM circle
         * meets the minimum, but real CTR polygons authored from AIP
         * AD 2.17 routinely extend beyond 5 NM on the approach axis.
         * A 5 NM circular approximation is therefore too small relative
         * to the actual published polygon at most controlled aerodromes.
         * Polygon containment (when modelled) supersedes the circular
         * approximation; see `D-AUDIT-polygon-ctr` for the polygon-
         * containment migration scope. The 5 NM circular value here
         * is a regulatory floor, not a polygon approximation.
         */
        const val CTR_FLOOR_NAUTICAL_MILES: Int = 5

        /**
         * Derived metres value — what the runtime guard reads and what
         * the loader uses as its `?: ...` fallback when no per-aerodrome
         * value is authored in `world-candidate.json`.
         *
         * Computed from [CTR_FLOOR_NAUTICAL_MILES] via
         * [Meters.fromNauticalMiles] so the numeric `5` is not
         * duplicated at any read site.
         */
        val CTR_FLOOR_5NM: Meters = Meters.fromNauticalMiles(CTR_FLOOR_NAUTICAL_MILES)
    }
}
