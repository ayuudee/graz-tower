package xyz.easiersaid.twr.migration.aptdat

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AptDatParserTest {

    private fun loadResource(name: String): String =
        this::class.java.classLoader.getResource(name)?.readText()
            ?: error("Resource not found: $name")

    private fun parseLowg(): AptDatAirport {
        val content = loadResource("airports/LOWG.dat")
        val result = parseAptDat(content).getOrElse { errors ->
            error("Parse failed: ${errors.toList().joinToString("\n") { it.message }}")
        }
        return result.value.first()
    }

    private fun parseLjmb(): AptDatAirport {
        val content = loadResource("airports/LJMB.dat")
        val result = parseAptDat(content).getOrElse { errors ->
            error("Parse failed: ${errors.toList().joinToString("\n") { it.message }}")
        }
        return result.value.first()
    }

    @Test
    fun parsesAirportHeader() {
        val airport = parseLowg()
        assertEquals("LOWG", airport.icao)
        assertEquals("Graz", airport.name)
        assertEquals(1120, airport.elevationFeet)
    }

    @Test
    fun parsesThreeLandRunways() {
        val airport = parseLowg()
        assertEquals(3, airport.landRunways.size)
        val designators = airport.landRunways.flatMap { listOf(it.end1.designator, it.end2.designator) }.toSet()
        assertTrue(designators.contains("16C"))
        assertTrue(designators.contains("34C"))
        assertTrue(designators.contains("16R"))
        assertTrue(designators.contains("34L"))
        assertTrue(designators.contains("16L"))
        assertTrue(designators.contains("34R"))
    }

    @Test
    fun parsesRunwayEndDetails() {
        val airport = parseLowg()
        val mainRunway = airport.landRunways.first { it.end1.designator == "16C" }
        assertEquals(45.0, mainRunway.width)
        assertEquals(SurfaceType.ASPHALT, mainRunway.surface)
        assertEquals(260.0, mainRunway.end1.displacedThresholdMeters)
        assertEquals(0.0, mainRunway.end2.displacedThresholdMeters)
    }

    @Test
    fun parsesSixHelipads() {
        val airport = parseLowg()
        assertEquals(6, airport.helipads.size)
        val names = airport.helipads.map { it.designator }.toSet()
        assertTrue(names.contains("H1"))
        assertTrue(names.contains("H5"))
        assertTrue(names.contains("H6"))
    }

    @Test
    fun parsesAllTaxiNodes() {
        val airport = parseLowg()
        assertEquals(96, airport.taxiNetwork.nodes.size)
    }

    @Test
    fun parsesAllTaxiEdges() {
        val airport = parseLowg()
        assertTrue(airport.taxiNetwork.edges.isNotEmpty())
        // edges count: 75 base edges in LOWG
        assertEquals(75, airport.taxiNetwork.edges.size)
    }

    @Test
    fun parsesActiveZonesAttachedToEdges() {
        val airport = parseLowg()
        val totalActiveZones = airport.taxiNetwork.edges.sumOf { it.activeZones.size }
        assertEquals(86, totalActiveZones)
        // Verify zones are attached to edges, not floating
        val edgesWithZones = airport.taxiNetwork.edges.filter { it.activeZones.isNotEmpty() }
        assertTrue(edgesWithZones.isNotEmpty())
    }

    @Test
    fun parsesActiveZoneTypes() {
        val airport = parseLowg()
        val allZones = airport.taxiNetwork.edges.flatMap { it.activeZones }
        assertTrue(allZones.any { it.type == ActiveZoneType.DEPARTURE })
        assertTrue(allZones.any { it.type == ActiveZoneType.ARRIVAL })
        assertTrue(allZones.any { it.type == ActiveZoneType.ILS })
    }

    @Test
    fun parsesStands() {
        val airport = parseLowg()
        assertTrue(airport.stands.isNotEmpty())
        val standNames = airport.stands.map { it.name }.toSet()
        assertTrue(standNames.contains("G13"))
        assertTrue(standNames.contains("G16"))
    }

    @Test
    fun parsesMetadata() {
        val airport = parseLowg()
        assertEquals("46.993055556", airport.metadata["datum_lat"])
        assertEquals("15.439166667", airport.metadata["datum_lon"])
        assertEquals("GRZ", airport.metadata["iata_code"])
        assertEquals("10000", airport.metadata["transition_alt"])
        assertEquals("AUT Austria", airport.metadata["country"])
    }

    @Test
    fun parsesPavementPolygons() {
        val airport = parseLowg()
        assertTrue(airport.pavements.isNotEmpty())
        // Each pavement should have nodes
        airport.pavements.forEach { pavement ->
            assertTrue(pavement.nodes.isNotEmpty(), "Pavement '${pavement.name}' has no nodes")
        }
    }

    @Test
    fun parsesLinearFeatures() {
        val airport = parseLowg()
        assertTrue(airport.linearFeatures.isNotEmpty())
    }

    @Test
    fun parsesTaxiSigns() {
        val airport = parseLowg()
        assertEquals(26, airport.taxiSigns.size)
        // Signs contain encoded text
        assertTrue(airport.taxiSigns.any { it.text.contains("RWY") })
    }

    @Test
    fun parsesLightingObjects() {
        val airport = parseLowg()
        assertEquals(3, airport.lightingObjects.size)
        assertTrue(airport.lightingObjects.any { it.description.contains("PAPI") })
    }

    @Test
    fun parsesTowerViewpoint() {
        val airport = parseLowg()
        val tower = assertNotNull(airport.towerViewpoint)
        assertEquals("Graz", tower.name)
        assertTrue(tower.heightFeetAgl > 0)
    }

    @Test
    fun parsesFrequencies() {
        val airport = parseLowg()
        assertEquals(4, airport.frequencies.size)
        val atis = airport.frequencies.first { it.type == AtcFrequencyType.ATIS }
        assertEquals(126130, atis.frequencyKhz)
        val tower = airport.frequencies.first { it.type == AtcFrequencyType.TOWER }
        assertEquals(118200, tower.frequencyKhz)
        assertEquals("Graz Tower", tower.name)
    }

    @Test
    fun parsesAtcFlows() {
        val airport = parseLowg()
        assertEquals(2, airport.atcFlows.size)
        val northernFlow = airport.atcFlows.first { it.name == "Northern Flow" }
        assertNotNull(northernFlow.windRule)
        assertEquals("LOWG", northernFlow.windRule?.icao)
        assertEquals(80, northernFlow.windRule?.minHeading)
        assertEquals(260, northernFlow.windRule?.maxHeading)
        assertTrue(northernFlow.runwayAssignments.isNotEmpty())
        val assignments = northernFlow.runwayAssignments.map { it.runwayDesignator }.toSet()
        assertTrue(assignments.contains("16C"))
    }

    @Test
    fun parsesServiceVehicles() {
        val airport = parseLowg()
        assertTrue(airport.serviceVehicleLocations.isNotEmpty())
        assertTrue(airport.serviceVehicleLocations.any { it.vehicleType == "pushback" })
        assertTrue(airport.serviceVehicleLocations.any { it.vehicleType == "gpu" })
    }

    @Test
    fun parsesVehicleEdges() {
        val airport = parseLowg()
        assertTrue(airport.taxiNetwork.vehicleEdges.isNotEmpty())
    }

    // -- LJMB cross-check --

    @Test
    fun ljmbParsesBasicStructure() {
        val airport = parseLjmb()
        assertEquals("LJMB", airport.icao)
        assertEquals(2, airport.landRunways.size)
        assertTrue(airport.taxiNetwork.nodes.isNotEmpty())
        assertTrue(airport.taxiNetwork.edges.isNotEmpty())
        assertTrue(airport.stands.isNotEmpty())
    }

    @Test
    fun ljmbParsesTowerViewpoint() {
        val airport = parseLjmb()
        assertNotNull(airport.towerViewpoint)
        assertEquals("Tower Viewpoint", airport.towerViewpoint?.name)
    }

    @Test
    fun ljmbParsesFrequencies() {
        val airport = parseLjmb()
        assertTrue(airport.frequencies.isNotEmpty())
        assertTrue(airport.frequencies.any { it.type == AtcFrequencyType.TOWER })
    }

    @Test
    fun ljmbParsesServiceVehicles() {
        val airport = parseLjmb()
        assertTrue(airport.serviceVehicleLocations.isNotEmpty())
        assertTrue(airport.serviceVehicleDestinations.isNotEmpty())
    }

    // -- Error handling --

    @Test
    fun emptyContentProducesEmptyList() {
        val result = parseAptDat("").getOrElse { error("Should not fail") }
        assertTrue(result.value.isEmpty())
    }

    @Test
    fun unknownRecordCodesProduceWarnings() {
        val content = """
            1   100 0 0 TEST Test Airport
            9999 unknown record
        """.trimIndent()
        val result = parseAptDat(content).getOrElse { error("Should not fail") }
        assertTrue(result.warnings.any { it.recordCode == "9999" })
    }
}
