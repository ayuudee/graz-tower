package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AftnAddress
import xyz.easiersaid.twr.protocol.AircraftIntent
import xyz.easiersaid.twr.protocol.FiledPlan
import xyz.easiersaid.twr.protocol.RoleName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Pass 14 (D-AUDIT.6.A-FOLLOWUP / .6.B-FOLLOWUP / .13) — AFTN routing
 * topology. `routeFiledPlan` is a pure function from a [FiledPlan] +
 * a published-roles projection to the set of [AftnAddress]es that
 * receive the strip.
 *
 * Doctrine: ICAO Doc 4444 §11 (flight plan filing); Annex 10 Vol II
 * (AFTN addressing); Doc 4444 §10.1 (strip distribution to handoff-
 * receiving controllers).
 *
 * Each row exercises a genuinely distinct branch of the routing
 * function. Rows that collapsed during plan-review fold-in are NOT
 * here (single-aerodrome and circuit-training-self-routed are the
 * same predicate; combined into row 2).
 */
class AftnRoutingSpec {

    private val LOWG = AerodromeId("LOWG")
    private val LJMB = AerodromeId("LJMB")
    private val UNKNOWN = AerodromeId("ZZZZ")

    @Test
    fun `cross-aerodrome plan fans out to two recipients (departure GROUND + destination TOWER) per Doc 4444 sec11`() {
        val plan = FiledPlan.Vfr(
            departureAerodrome = LOWG,
            destinationAerodrome = LJMB,
            intent = AircraftIntent.Departing,
        )
        val rolesAt: (AerodromeId) -> Set<RoleName> = { aerodrome ->
            when (aerodrome) {
                LOWG -> setOf(RoleName.GROUND, RoleName.TOWER)
                LJMB -> setOf(RoleName.TOWER)
                else -> emptySet()
            }
        }
        val recipients = AftnRouting.routeFiledPlan(plan, rolesAt)
            .fold({ fail("expected Right, got Left($it)") }, { it.toList() })
        assertEquals(
            listOf(
                AftnAddress(LOWG, RoleName.GROUND),
                AftnAddress(LJMB, RoleName.TOWER),
            ),
            recipients,
            "ICAO Doc 4444 §11: cross-aerodrome plan distributes to departure (GROUND) " +
                "and destination (TOWER) controller bays",
        )
    }

    @Test
    fun `single-aerodrome plan routes only to departure side (covers null-destination AND circuit-training)`() {
        // Both null-destination (transit unfiled) and same-aerodrome
        // destination (circuit training) hit the SAME branch:
        // `destinationAerodrome != departureAerodrome` is false so no
        // destination-side recipient is added.
        val rolesAt: (AerodromeId) -> Set<RoleName> = {
            if (it == LOWG) setOf(RoleName.GROUND, RoleName.TOWER) else emptySet()
        }
        val nullDestinationPlan = FiledPlan.Vfr(
            departureAerodrome = LOWG,
            destinationAerodrome = null,
            intent = AircraftIntent.Departing,
        )
        val sameDestinationPlan = FiledPlan.Vfr(
            departureAerodrome = LOWG,
            destinationAerodrome = LOWG,
            intent = AircraftIntent.Departing,
        )

        val nullRecipients = AftnRouting.routeFiledPlan(nullDestinationPlan, rolesAt)
            .fold({ fail("expected Right for null destination, got Left($it)") }, { it.toList() })
        val sameRecipients = AftnRouting.routeFiledPlan(sameDestinationPlan, rolesAt)
            .fold({ fail("expected Right for same destination, got Left($it)") }, { it.toList() })

        val expected = listOf(AftnAddress(LOWG, RoleName.GROUND))
        assertEquals(expected, nullRecipients, "null-destination plan → 1 recipient (departure GROUND)")
        assertEquals(expected, sameRecipients, "circuit-training (departure==destination) → 1 recipient (departure GROUND)")
    }

    @Test
    fun `departure-side falls back to TOWER when GROUND not published per Doc 4444 sec11`() {
        // Small-field with only TOWER staffed (e.g. AFIS-only aerodrome
        // pre-D-PF.1). Routing must still produce a recipient.
        val plan = FiledPlan.Vfr(
            departureAerodrome = LJMB,
            destinationAerodrome = null,
            intent = AircraftIntent.Departing,
        )
        val rolesAt: (AerodromeId) -> Set<RoleName> = {
            if (it == LJMB) setOf(RoleName.TOWER) else emptySet()
        }
        val recipients = AftnRouting.routeFiledPlan(plan, rolesAt)
            .fold({ fail("expected Right, got Left($it)") }, { it.toList() })
        assertEquals(
            listOf(AftnAddress(LJMB, RoleName.TOWER)),
            recipients,
            "GROUND-not-published → TOWER fallback (Doc 4444 §11 strip dispatch)",
        )
    }

    @Test
    fun `departure aerodrome with neither GROUND nor TOWER returns Left NoDepartureRoleStaffed`() {
        // Per "no corners" (FP M1): empty list is a partial-function shape;
        // the typed Either-Left surfaces the failure mode at the call site.
        val plan = FiledPlan.Vfr(
            departureAerodrome = UNKNOWN,
            destinationAerodrome = null,
            intent = AircraftIntent.Departing,
        )
        val rolesAt: (AerodromeId) -> Set<RoleName> = { emptySet() }
        val result = AftnRouting.routeFiledPlan(plan, rolesAt)
        assertTrue(result.isLeft(), "empty published-roles → Left, never empty list")
        val failure = result.swap().getOrNull()
        assertEquals(
            RoutingFailure.NoDepartureRoleStaffed(UNKNOWN),
            failure,
            "Left carries the failing aerodrome for diagnostic surface",
        )
    }

    @Test
    fun `destination aerodrome with neither TOWER nor APPROACH returns Left NoDestinationRoleStaffed`() {
        // Pass 14 fail-closed (FP M1): the destination side has its own
        // typed Left. A regression that silently dropped the destination
        // recipient (degrading to a single-recipient list) would ship
        // green without this row.
        val plan = FiledPlan.Vfr(
            departureAerodrome = LOWG,
            destinationAerodrome = UNKNOWN,
            intent = AircraftIntent.Departing,
        )
        val rolesAt: (AerodromeId) -> Set<RoleName> = { aerodrome ->
            if (aerodrome == LOWG) setOf(RoleName.GROUND) else emptySet()
        }
        val result = AftnRouting.routeFiledPlan(plan, rolesAt)
        assertTrue(result.isLeft(), "destination publishes neither TOWER nor APPROACH → Left")
        assertEquals(
            RoutingFailure.NoDestinationRoleStaffed(UNKNOWN),
            result.swap().getOrNull(),
            "Left carries the failing destination aerodrome (Annex 10 Vol II address resolution)",
        )
    }

    @Test
    fun `destination side falls back to APPROACH when TOWER not published`() {
        // Pass 14: large fields with separate APPROACH staffing. Today
        // no TWR2 world has APPROACH-without-TOWER, so this row is the
        // only assertion that the fallback ordering is correct. A swap
        // (APPROACH-first preference) would ship green without this.
        val plan = FiledPlan.Vfr(
            departureAerodrome = LOWG,
            destinationAerodrome = LJMB,
            intent = AircraftIntent.Departing,
        )
        val rolesAt: (AerodromeId) -> Set<RoleName> = { aerodrome ->
            when (aerodrome) {
                LOWG -> setOf(RoleName.GROUND)
                LJMB -> setOf(RoleName.APPROACH) // TOWER not published
                else -> emptySet()
            }
        }
        val recipients = AftnRouting.routeFiledPlan(plan, rolesAt)
            .fold({ fail("expected Right, got Left($it)") }, { it.toList() })
        assertEquals(
            listOf(
                AftnAddress(LOWG, RoleName.GROUND),
                AftnAddress(LJMB, RoleName.APPROACH),
            ),
            recipients,
            "TOWER-not-published → APPROACH fallback (Doc 4444 §11 destination-side dispatch)",
        )
    }

    @Test
    fun `IFR plan routing reads destination from FlightPlan-arrivalAerodrome`() {
        // Pass 14: `FiledPlan` sealed dispatch over `Vfr | Ifr`. The
        // hoisted `destinationAerodrome` property delegates to
        // `flightPlan.arrivalAerodrome` for the IFR leaf. A regression
        // where the IFR branch returned null (e.g. someone copied the
        // VFR-only `destinationAerodrome` field shape) would ship green
        // without this row — IFR plans would always single-recipient.
        val ifrPlan = FiledPlan.Ifr(
            flightPlan = xyz.easiersaid.twr.protocol.FlightPlan(
                departureAerodrome = LOWG,
                arrivalAerodrome = LJMB,
                requestedLevel = xyz.easiersaid.twr.protocol.Level.AltitudeFeet.unsafe(8000),
                enRouteWaypoints = emptyList(),
            ),
        )
        val rolesAt: (AerodromeId) -> Set<RoleName> = { aerodrome ->
            when (aerodrome) {
                LOWG -> setOf(RoleName.GROUND)
                LJMB -> setOf(RoleName.TOWER)
                else -> emptySet()
            }
        }
        val recipients = AftnRouting.routeFiledPlan(ifrPlan, rolesAt)
            .fold({ fail("expected Right for IFR plan, got Left($it)") }, { it.toList() })
        assertEquals(
            listOf(
                AftnAddress(LOWG, RoleName.GROUND),
                AftnAddress(LJMB, RoleName.TOWER),
            ),
            recipients,
            "IFR plan dispatch must reach FlightPlan.arrivalAerodrome (Doc 4444 §11 IFR FPL)",
        )
    }
}
