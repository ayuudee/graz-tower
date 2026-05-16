package xyz.easiersaid.twr.pilot

import xyz.easiersaid.twr.protocol.Feet
import xyz.easiersaid.twr.protocol.PressureSetting
import xyz.easiersaid.twr.protocol.Temperature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * fn-28.2 (G3a-react-density-altitude R17): `computeDensityAltitudeFeet`
 * pure-function correctness.
 *
 * **Boundary numerics** anchored at LOWG (elev ≈ 1115 ft, the fixture
 * aerodrome from .1/.3) per AGENTS.md world data, plus a sea-level
 * boundary case and the C172-threshold boundary cases.
 *
 * Formula re-derivation (FAA AC 61-107B §3-1 / pilot rule of thumb):
 *  - pressure_alt_ft = elev_ft + (1013.25 - qnh_hPa) * 30
 *  - isa_temp_c     = 15.0 - (elev_ft / 1000.0) * 1.98
 *  - da_ft          = pressure_alt + 120 * (oat_c - isa_temp_c)
 *
 * Hand checks below quote the intermediate values so a regression on one
 * coefficient is diagnosable from the assertion message.
 */
class DensityAltitudeFormulaTest {

    @Test
    fun `ISA at sea level — DA equals field elevation (zero ISA deviation)`() {
        // Sea-level field at ISA (15°C, 1013 hPa) → pressure_alt = 0,
        // isa_temp = 15, oat = 15 → da = 0.
        val input = DensityAltitudeInput(
            oat = Temperature.celsius(15.0),
            qnh = PressureSetting.QnhHpa.unsafe(1013),
            fieldElevation = Feet.unsafe(0),
        )
        // Pressure-alt contribution: (1013.25 - 1013) * 30 = 7.5 ft
        // DA contribution: 120 * (15.0 - 15.0) = 0 ft
        // Total: 7.5 → rounded to 8 ft.
        assertEquals(
            8,
            computeDensityAltitudeFeet(input).value,
            "ISA-sea-level: pressure_alt 7.5 ft + 0 ISA deviation = 8 ft (rounded)",
        )
    }

    @Test
    fun `ISA at LOWG elevation — DA approximates field elevation`() {
        // LOWG elev 1115 ft. ISA at 1115 ft = 15 - 1.115 * 1.98 ≈ 12.79°C.
        // QNH 1013 → pressure_alt = 1115 + 7.5 = 1122.5
        // OAT = ISA → da_correction = 0
        // Total ≈ 1122.5 → rounded to 1123 ft.
        val input = DensityAltitudeInput(
            oat = Temperature.celsius(12.79),
            qnh = PressureSetting.QnhHpa.unsafe(1013),
            fieldElevation = Feet.unsafe(1115),
        )
        val da = computeDensityAltitudeFeet(input).value
        // Allow ±2 ft round-off across the lapse-rate / 1013.25 boundary.
        assertTrue(
            da in 1120..1125,
            "ISA(LOWG): expected DA ≈ field elev (~1123 ft); got $da",
        )
    }

    @Test
    fun `ISA plus 35C at LOWG — high-DA scenario fires DA decline for C172`() {
        // The fn-28.3 sim golden scenario: ISA(1115 ft) ≈ 12.79°C + 35°C
        // deviation = 47.79°C OAT. QNH stays at ISA 1013.
        // pressure_alt = 1115 + 7.5 = 1122.5
        // da_correction = 120 * 35.0 = 4200 ft
        // Total ≈ 5322.5 → 5323 ft.
        val input = DensityAltitudeInput(
            oat = Temperature.celsius(47.79),
            qnh = PressureSetting.QnhHpa.unsafe(1013),
            fieldElevation = Feet.unsafe(1115),
        )
        val da = computeDensityAltitudeFeet(input).value
        assertTrue(
            da in 5320..5325,
            "ISA+35°C at LOWG: expected DA ≈ 5323 ft (exceeds C172's 5000 ft threshold); got $da",
        )
        // Cross-check: this value MUST exceed the C172 threshold
        // (xyz.easiersaid.twr.protocol.AircraftType.C172.maxDensityAltitudeFt =
        // Feet.unsafe(5000)) — sim-golden contract.
        assertTrue(
            da > 5000,
            "DA at LOWG ISA+35°C must exceed C172's 5000 ft threshold for golden scenario; got $da",
        )
    }

    @Test
    fun `low QNH amplifies DA — pressure altitude term load-bearing`() {
        // QNH below ISA increases pressure altitude. At sea-level field,
        // QNH 1000 (vs ISA 1013.25) → pressure_alt = 0 + (1013.25 - 1000) * 30
        // = 13.25 * 30 = 397.5 ft. OAT at ISA → no DA correction.
        // Total: 397.5 → rounded to 398 ft.
        val input = DensityAltitudeInput(
            oat = Temperature.celsius(15.0),
            qnh = PressureSetting.QnhHpa.unsafe(1000),
            fieldElevation = Feet.unsafe(0),
        )
        val da = computeDensityAltitudeFeet(input).value
        assertTrue(
            da in 395..400,
            "Low QNH: expected DA ≈ 398 ft (pressure_alt term, no temp deviation); got $da",
        )
    }

    @Test
    fun `cold-front high-pressure scenario clamps to zero DA — fail-closed at boundary`() {
        // Extreme cold-front: sea-level field, QNH 1050 (high pressure),
        // OAT well below ISA. pressure_alt = (1013.25 - 1050) * 30 = -1102.5 ft.
        // OAT 0°C → ISA dev = -15°C → da_correction = -1800 ft.
        // Total: -2902.5 → clamps to 0.
        val input = DensityAltitudeInput(
            oat = Temperature.celsius(0.0),
            qnh = PressureSetting.QnhHpa.unsafe(1050),
            fieldElevation = Feet.unsafe(0),
        )
        assertEquals(
            0,
            computeDensityAltitudeFeet(input).value,
            "Negative DA values clamp to 0 ft — Feet smart-constructor invariant + " +
                "operational reality (negative DA is performance margin, not decline trigger)",
        )
    }

    @Test
    fun `Standard altimeter setting collapses pressure-alt to ISA — v1 simplification`() {
        // PressureSetting.Standard means 29.92 in / 1013.25 hPa — the
        // formula collapses pressure_alt = elev (ISA pressure cancels).
        // Sea-level field + ISA-temp → DA = 0.
        val input = DensityAltitudeInput(
            oat = Temperature.celsius(15.0),
            qnh = PressureSetting.Standard,
            fieldElevation = Feet.unsafe(0),
        )
        assertEquals(
            0,
            computeDensityAltitudeFeet(input).value,
            "Standard QNH: pressure_alt collapses to elev (0); ISA temp → DA = 0",
        )
    }

    @Test
    fun `pure function — same inputs always produce same output`() {
        // Idempotence pin — recognition site calls every tick; a hidden
        // time/state dependency would surface as test flakiness.
        val input = DensityAltitudeInput(
            oat = Temperature.celsius(20.0),
            qnh = PressureSetting.QnhHpa.unsafe(1020),
            fieldElevation = Feet.unsafe(500),
        )
        val a = computeDensityAltitudeFeet(input)
        val b = computeDensityAltitudeFeet(input)
        val c = computeDensityAltitudeFeet(input)
        assertEquals(a, b, "computeDensityAltitudeFeet is pure (a==b)")
        assertEquals(a, c, "computeDensityAltitudeFeet is pure (a==c)")
    }
}
