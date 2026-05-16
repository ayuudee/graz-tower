package xyz.easiersaid.twr.pilot

import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.Runway
import xyz.easiersaid.twr.pilot.world.PilotAerodrome
import xyz.easiersaid.twr.pilot.world.PilotAviationWorld
import xyz.easiersaid.twr.pilot.world.PilotRunway
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * fn-24 (R8) — structural firewall on the pilot-side world projection.
 *
 * The named-arg constructor wiring in `PilotAviationWorld.kt` catches
 * sloppy projection construction (every projection call site must
 * enumerate every projection field). But it does NOT catch future
 * additions to the core [Aerodrome] / [Runway] types that the projection
 * silently drops. This test is the complementary future-field gate —
 * a reflection-driven property-set assertion that fails fast when:
 *
 *   (a) A new field is added to [Aerodrome] or [Runway] in `:core` and
 *       not added to [PilotAerodrome] / [PilotRunway].
 *   (b) A projection type accidentally gains a field that doesn't
 *       exist on the corresponding core entity (a backwards drift —
 *       projection-only state is a pilot-firewall anti-pattern).
 *   (c) The value-type substitution at the projection boundary
 *       (`aerodromes: Map<_, PilotAerodrome>`,
 *       `runways: Map<_, PilotRunway>`) is silently undone — e.g. a
 *       future refactor that types it as `Map<_, Aerodrome>`, defeating
 *       the structural enforcement.
 *   (d) A new top-level field is added to [AviationWorld] without
 *       being added to [PilotAviationWorld] (the parity-with-only-
 *       aerodromes-substitution contract from epic R1).
 *
 * JVM-only because `kotlin.reflect.full.memberProperties` is JVM. The
 * test lives in `:pilot/jvmTest` (peer to [FirewallAviationWorldFieldsTest]
 * which pins the same parity discipline for the controller-facing
 * [AviationWorld] read surface).
 *
 * **No-suppression rule:** an architectural test failure is never
 * resolved by `@Disabled`, `@Suppress`, or test removal. Fix the
 * violation or amend the firewall via plan revision.
 */
class FirewallPilotAviationWorldTest {

    @Test
    fun `PilotAerodrome has no weather property — fn-24 structural omission`() {
        val pilotProps = PilotAerodrome::class.memberProperties.map { it.name }.toSet()
        assertFalse(
            "weather" in pilotProps,
            "FIREWALL VIOLATION: PilotAerodrome must NOT carry `weather`. The fn-24 " +
                "projection omits entity-level dynamic state so pilot code cannot reach it " +
                "by chart-read. Wind reaches the pilot via `PilotInput.weatherByAerodrome` " +
                "(WindReport projection — windsock + ASI + instrument scan). " +
                "Resolution: drop the `weather` property from PilotAerodrome; if a new " +
                "dynamic field truly belongs to the pilot's sensing surface, model it as a " +
                "typed projection on PilotInput, not as a back-door field on the chart type. " +
                "Got pilot props: $pilotProps",
        )
    }

    @Test
    fun `PilotRunway has no obstruction property — fn-12-precedent structural omission`() {
        val pilotProps = PilotRunway::class.memberProperties.map { it.name }.toSet()
        assertFalse(
            "obstruction" in pilotProps,
            "FIREWALL VIOLATION: PilotRunway must NOT carry `obstruction`. The fn-24 " +
                "projection omits entity-level dynamic state so pilot code cannot reach it " +
                "by chart-read. Obstruction reaches the pilot via radio (controller " +
                "transmission), never via chart-read. " +
                "Resolution: drop the `obstruction` property from PilotRunway. " +
                "Got pilot props: $pilotProps",
        )
    }

    @Test
    fun `PilotAerodrome property set equals Aerodrome minus weather`() {
        val pilotProps = PilotAerodrome::class.memberProperties.map { it.name }.toSet()
        val coreProps = Aerodrome::class.memberProperties.map { it.name }.toSet()
        val expected = coreProps - setOf("weather")
        assertEquals(
            expected,
            pilotProps,
            """
            FIREWALL VIOLATION: PilotAerodrome property set must equal Aerodrome minus {weather}.

            missing-from-pilot: ${expected - pilotProps}
            extra-in-pilot:     ${pilotProps - expected}

            Resolution paths:
              - missing-from-pilot non-empty: a field was added to `:core/Aerodrome` but not
                to `PilotAerodrome`. If the field is chart-equivalent / static reference
                data, add it to PilotAerodrome AND to the `Aerodrome.toPilotView()` named-arg
                wiring in `PilotAviationWorld.kt`. If the field is dynamic state, model it as
                a typed projection field on `PilotInput` (the way `weatherByAerodrome` does
                for `Aerodrome.weather.wind`) and leave it off PilotAerodrome.
              - extra-in-pilot non-empty: PilotAerodrome has a field that doesn't exist on
                core Aerodrome. Either the field was removed from core but not from pilot
                (drop it from PilotAerodrome) or pilot-only state was introduced (a pilot-
                firewall anti-pattern — projection types must mirror the core entity, not
                accumulate pilot-only fields).

            No `@Suppress`, no `@Disabled`, no test removal.
            """.trimIndent(),
        )
    }

    @Test
    fun `PilotRunway property set equals Runway minus obstruction`() {
        val pilotProps = PilotRunway::class.memberProperties.map { it.name }.toSet()
        val coreProps = Runway::class.memberProperties.map { it.name }.toSet()
        val expected = coreProps - setOf("obstruction")
        assertEquals(
            expected,
            pilotProps,
            """
            FIREWALL VIOLATION: PilotRunway property set must equal Runway minus {obstruction}.

            missing-from-pilot: ${expected - pilotProps}
            extra-in-pilot:     ${pilotProps - expected}

            Resolution paths:
              - missing-from-pilot non-empty: a field was added to `:core/Runway` but not to
                `PilotRunway`. If chart-equivalent / static, add it to PilotRunway AND to the
                `Runway.toPilotView()` named-arg wiring. If dynamic state (like obstruction),
                model it as a typed projection on `PilotInput`.
              - extra-in-pilot non-empty: PilotRunway has a field that doesn't exist on core
                Runway. Drop it from PilotRunway (pilot-only state on chart types violates
                the firewall).

            No `@Suppress`, no `@Disabled`, no test removal.
            """.trimIndent(),
        )
    }

    @Test
    fun `PilotAviationWorld aerodromes value type is PilotAerodrome`() {
        val aerodromesProp = PilotAviationWorld::class.memberProperties.find { it.name == "aerodromes" }
        assertNotNull(aerodromesProp, "PilotAviationWorld must have an `aerodromes` property")
        // Map<AerodromeId, PilotAerodrome>: arg[0] is the key, arg[1] is the value.
        val valueClassifier = aerodromesProp.returnType.arguments[1].type?.classifier as? KClass<*>
        assertEquals(
            PilotAerodrome::class,
            valueClassifier,
            "FIREWALL VIOLATION: PilotAviationWorld.aerodromes must have value type " +
                "`PilotAerodrome`, not `Aerodrome` (or any other type). The pilot-firewall " +
                "projection's structural enforcement depends on the value-type substitution: " +
                "if aerodromes still mapped to `Aerodrome`, pilot code could still reach " +
                "`world.aerodromes[id].weather` by chart-read. " +
                "Got value classifier: $valueClassifier",
        )
    }

    @Test
    fun `PilotAerodrome runways value type is PilotRunway`() {
        val runwaysProp = PilotAerodrome::class.memberProperties.find { it.name == "runways" }
        assertNotNull(runwaysProp, "PilotAerodrome must have a `runways` property")
        val valueClassifier = runwaysProp.returnType.arguments[1].type?.classifier as? KClass<*>
        assertEquals(
            PilotRunway::class,
            valueClassifier,
            "FIREWALL VIOLATION: PilotAerodrome.runways must have value type `PilotRunway`, " +
                "not `Runway` (or any other type). Without this substitution, pilot code " +
                "could still reach `aerodrome.runways[id].obstruction` by chart-read, " +
                "defeating the fn-24 structural enforcement. " +
                "Got value classifier: $valueClassifier",
        )
    }

    @Test
    fun `PilotAviationWorld property set equals AviationWorld property set`() {
        val pilotProps = PilotAviationWorld::class.memberProperties.map { it.name }.toSet()
        val coreProps = AviationWorld::class.memberProperties.map { it.name }.toSet()
        assertEquals(
            coreProps,
            pilotProps,
            """
            FIREWALL VIOLATION: PilotAviationWorld property set must equal AviationWorld
            property set (the top-level mirrors `AviationWorld` exactly; only the
            `aerodromes` value-type substitutes to `PilotAerodrome` per R1).

            missing-from-pilot: ${coreProps - pilotProps}
            extra-in-pilot:     ${pilotProps - coreProps}

            Resolution paths:
              - missing-from-pilot non-empty: a top-level field was added to AviationWorld
                but not to PilotAviationWorld. Add the same field (same name, value-type
                projected if it contains nested entity types) AND extend
                `AviationWorld.toPilotView()` named-arg wiring.
              - extra-in-pilot non-empty: a top-level field exists on PilotAviationWorld
                that doesn't exist on AviationWorld (pilot-only top-level chart state —
                pilot-firewall anti-pattern). Drop it.

            No `@Suppress`, no `@Disabled`, no test removal.
            """.trimIndent(),
        )
    }
}
