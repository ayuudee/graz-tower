package xyz.easiersaid.twr.pilot

import xyz.easiersaid.twr.core.world.AviationWorld
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * G2 Phase C (D-G2.5 enforcement) — `AviationWorld` field-shape allowlist.
 *
 * The pilot reads `world.aerodromes[…]` (and other AviationWorld members) as
 * chart-database-equivalent reference data. The firewall principle is that
 * everything the pilot reads must be chart-equivalent (geometry, fixes,
 * aerodromes with published procedures/runways/frequencies, airways, VFR
 * routes, airspace volumes, FIRs) — not live state (real-time traffic,
 * controller-state mirrors).
 *
 * This test is a reflection allowlist: pinning both the SET of property
 * names AND each property's return-type classifier. A future contributor who:
 *  (a) adds a new field to AviationWorld — fails with name-set mismatch.
 *  (b) widens an existing field's type (e.g. `aerodromes: Map<…>` →
 *      `aerodromes: ObservableMap<…>`) — fails with type-set mismatch.
 * Either way the failure forces a deliberate decision: extend the allowlist
 * (and document why the new shape is firewall-compliant), or route the data
 * through a NavComputer façade (D-G2.5 real-fix contract).
 *
 * JVM-only because `kotlin.reflect.full.memberProperties` is JVM. The test
 * lives in `:pilot/jvmTest` because the firewall claim is about the pilot's
 * read surface — `:pilot` already depends on `:core` for [AviationWorld].
 *
 * **No-suppression rule:** an architectural test failure is never resolved
 * by `@Disabled`, `@Suppress`, or test removal. Fix the violation or amend
 * the firewall via plan revision.
 *
 * Reflection runs over public properties only. A `private val` backing field
 * added via secondary constructor or custom `init`-block does not appear
 * here — that's intentional, the firewall is about what callers can read.
 */
class FirewallAviationWorldFieldsTest {

    /**
     * Firewall allowlist: each entry maps a property name to a fully-walked
     * type signature including type-argument classifiers. This catches both:
     *  - name-additions (new field on AviationWorld);
     *  - same-name type-widenings (e.g. `aerodromes: Map<AerodromeId, Aerodrome>`
     *    → `aerodromes: Map<AerodromeId, LiveAerodrome>` where `LiveAerodrome`
     *    mirrors controller-state — would silently leak live state past the
     *    head-classifier check).
     */
    private val ALLOWED_FIELDS: Map<String, String> = mapOf(
        // PhysicalGeometry: cartesian frame + named points.
        "geometry" to "PhysicalGeometry",
        // Map<FixId, Fix>: named navaids, fixes, intersections.
        "fixes" to "Map<FixId, Fix>",
        // Map<AerodromeId, Aerodrome>: per-airport authoring (runways,
        // circuits, AIP, roles, plates, published VFR procedures).
        "aerodromes" to "Map<AerodromeId, Aerodrome>",
        // Map<AirwayId, Airway>: en-route airways.
        "airways" to "Map<AirwayId, Airway>",
        // Map<VfrRouteId, VfrRoute>: cross-country VFR corridors.
        "vfrRoutes" to "Map<VfrRouteId, VfrRoute>",
        // Map<AirspaceVolumeId, AirspaceVolume>: CTR/TMA polygons + classes.
        // The pilot reads these for AirspaceClass discovery (chart data),
        // not for per-tick polygon-containment tests.
        "airspace" to "Map<AirspaceVolumeId, AirspaceVolume>",
        // Map<FirId, FlightInformationRegion>: FIR boundaries + FIS frequencies.
        "firs" to "Map<FirId, FlightInformationRegion>",
    )

    @Test
    fun `AviationWorld carries only chart-database-equivalent fields with expected types`() {
        val actual: Map<String, String> = AviationWorld::class.memberProperties.associate { prop ->
            prop.name to typeSignature(prop.returnType)
        }
        assertEquals(ALLOWED_FIELDS, actual,
            "AviationWorld's field shape is the firewall allowlist for what the pilot " +
                "reads as chart data. Adding a 'live' field, OR widening an existing field's " +
                "type to carry live state (e.g. `aerodromes: Map<AerodromeId, Aerodrome>` → " +
                "`aerodromes: Map<AerodromeId, LiveAerodrome>`), silently widens the pilot's " +
                "read surface beyond the firewall (D-G2.5). Either:\n" +
                "  (a) The new shape is genuinely chart-equivalent — update ALLOWED_FIELDS in " +
                "      this test, AND add a one-line comment on the entry justifying the shape, " +
                "      AND if the field has any live-state semantics, file a deferment for the " +
                "      NavComputer façade migration (D-G2.5 real-fix contract).\n" +
                "  (b) The new field/shape carries live state — route it through a NavComputer " +
                "      façade and keep AviationWorld chart-only.\n" +
                "Note: this test runs reflection over public properties only. A `private val` " +
                "backing field added via secondary constructor or custom `init`-block does not " +
                "appear here — that's intentional, the firewall is about what callers can read.\n" +
                "Expected: $ALLOWED_FIELDS\n" +
                "Actual:   $actual",
        )
    }

    /**
     * Walk a [KType] producing a `Head<Arg1, Arg2, …>` string of classifier
     * simpleNames. Catches type-argument widenings that a head-only check
     * (`returnType.classifier`) would silently allow.
     */
    private fun typeSignature(type: KType): String {
        val head = (type.classifier as? KClass<*>)?.simpleName ?: "?"
        if (type.arguments.isEmpty()) return head
        val args = type.arguments.joinToString(", ") { proj ->
            proj.type?.let { typeSignature(it) } ?: "*"
        }
        return "$head<$args>"
    }
}
