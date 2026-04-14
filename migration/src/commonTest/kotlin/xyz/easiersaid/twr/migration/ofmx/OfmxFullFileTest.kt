package xyz.easiersaid.twr.migration.ofmx

import arrow.core.getOrElse
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test against the full Austria OFMX dataset.
 * Requires the extracted OFMX file at data/ofm/austria/ofmx_extracted/ofmx_lo/isolated/ofmx_lo.ofmx
 * relative to the project root.
 */
class OfmxFullFileTest {

    private val ofmxPath = Paths.get(
        System.getProperty("user.dir"),
    ).resolve("../data/ofm/austria/ofmx_extracted/ofmx_lo/isolated/ofmx_lo.ofmx").let {
        // Try relative to tools/ first, then project root
        if (it.toFile().exists()) it
        else Paths.get("data/ofm/austria/ofmx_extracted/ofmx_lo/isolated/ofmx_lo.ofmx")
    }

    private fun skipIfMissing(): Boolean = !ofmxPath.toFile().exists()

    @Test
    fun parsesFullAustriaDataset() {
        if (skipIfMissing()) {
            println("Skipping full OFMX test: file not found at $ofmxPath")
            return
        }
        val content = ofmxPath.toFile().readText()
        val snapshot = parseOfmx(content).getOrElse { errors ->
            error("Parse failed: ${errors.toList().take(5).joinToString("\n") { it.message }}")
        }

        val result = snapshot.value
        assertTrue(result.airports.size > 200, "Expected >200 airports, got ${result.airports.size}")
        assertTrue(result.runways.isNotEmpty(), "Expected runways")
        assertTrue(result.runwayDirections.isNotEmpty(), "Expected runway directions")
        assertTrue(result.airspaces.isNotEmpty(), "Expected airspaces")
        assertTrue(result.airspaceBoundaries.isNotEmpty(), "Expected airspace boundaries")
        assertTrue(result.designatedPoints.size > 300, "Expected >300 designated points, got ${result.designatedPoints.size}")
        assertTrue(result.units.isNotEmpty(), "Expected units")
        assertTrue(result.services.isNotEmpty(), "Expected services")
        assertTrue(result.frequencies.isNotEmpty(), "Expected frequencies")
    }

    @Test
    fun findsLowgInFullDataset() {
        if (skipIfMissing()) return
        val content = ofmxPath.toFile().readText()
        val snapshot = parseOfmx(content).getOrElse { error("Parse failed") }

        val lowg = snapshot.value.airports.find { it.codeId == "LOWG" }
        assertNotNull(lowg, "LOWG not found in dataset")
        assertEquals("GRAZ", lowg.name)
        assertEquals("GRZ", lowg.iata)
        assertEquals(1120, lowg.elevationFeet)
        assertEquals(5, lowg.magneticVariation)
        assertEquals(10000, lowg.transitionAltitude)
    }

    @Test
    fun findsLowgRunwaysInFullDataset() {
        if (skipIfMissing()) return
        val content = ofmxPath.toFile().readText()
        val snapshot = parseOfmx(content).getOrElse { error("Parse failed") }

        val lowgMid = snapshot.value.airports.find { it.codeId == "LOWG" }?.mid
        assertNotNull(lowgMid)
        val lowgRunways = snapshot.value.runways.filter { it.airportMid == lowgMid }
        assertTrue(lowgRunways.isNotEmpty(), "Expected LOWG runways")
        val mainRunway = lowgRunways.find { it.designator == "16C/34C" }
        assertNotNull(mainRunway, "Expected 16C/34C runway")
        assertEquals(3000, mainRunway.lengthMeters)
        assertEquals(45, mainRunway.widthMeters)
    }

    @Test
    fun findsGrazFrequenciesInFullDataset() {
        if (skipIfMissing()) return
        val content = ofmxPath.toFile().readText()
        val snapshot = parseOfmx(content).getOrElse { error("Parse failed") }

        val grazFreqs = snapshot.value.frequencies.filter {
            it.callSign?.contains("GRAZ") == true
        }
        assertTrue(grazFreqs.isNotEmpty(), "Expected GRAZ frequencies")
        assertTrue(grazFreqs.any { it.callSign == "GRAZ TOWER" }, "Expected GRAZ TOWER frequency")
    }
}
