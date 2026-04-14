package xyz.easiersaid.twr.migration.ofmx

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OfmxParserTest {

    private fun loadResource(name: String): String =
        this::class.java.classLoader.getResource(name)?.readText()
            ?: error("Resource not found: $name")

    private fun parseSubset(): OfmxSnapshot {
        val xml = loadResource("ofmx/lowg_subset.ofmx")
        val result = parseOfmx(xml).getOrElse { errors ->
            error("Parse failed: ${errors.toList().joinToString("\n") { it.message }}")
        }
        return result.value
    }

    @Test
    fun parsesAirportFields() {
        val snapshot = parseSubset()
        assertEquals(1, snapshot.airports.size)
        val lowg = snapshot.airports.first()
        assertEquals("LOWG", lowg.codeId)
        assertEquals("GRAZ", lowg.name)
        assertEquals("LOWG", lowg.icao)
        assertEquals("GRZ", lowg.iata)
        assertEquals("AD", lowg.codeType)
        assertEquals(1120, lowg.elevationFeet)
        assertEquals(5, lowg.magneticVariation)
        assertEquals(10000, lowg.transitionAltitude)
        assertEquals("FT", lowg.transitionAltitudeUnit)
        assertEquals("GRAZ", lowg.city)
    }

    @Test
    fun parsesAirportCoordinates() {
        val lowg = parseSubset().airports.first()
        assertEquals(46.99305556, lowg.position.lat.value, 0.0001)
        assertEquals(15.43916667, lowg.position.lon.value, 0.0001)
    }

    @Test
    fun parsesRunwayDimensions() {
        val snapshot = parseSubset()
        assertEquals(1, snapshot.runways.size)
        val rwy = snapshot.runways.first()
        assertEquals("16C/34C", rwy.designator)
        assertEquals("LOWG", rwy.airportCodeId)
        assertEquals(3000, rwy.lengthMeters)
        assertEquals(45, rwy.widthMeters)
        assertEquals("ASPH", rwy.composition)
        assertEquals(61, rwy.pcnClass)
    }

    @Test
    fun parsesRunwayDirection() {
        val snapshot = parseSubset()
        assertEquals(1, snapshot.runwayDirections.size)
        val rdn = snapshot.runwayDirections.first()
        assertEquals("16C", rdn.designator)
        assertEquals(169, rdn.trueBearing)
        assertEquals(164, rdn.magneticBearing)
        assertNotNull(rdn.position)
        assertEquals(47.00429124, rdn.position!!.lat.value, 0.0001)
    }

    @Test
    fun parsesAirspace() {
        val snapshot = parseSubset()
        assertEquals(1, snapshot.airspaces.size)
        val ase = snapshot.airspaces.first()
        assertEquals("CTR", ase.codeType)
        assertEquals("LOWG", ase.codeId)
        assertEquals("GRAZ CTR", ase.name)
        assertEquals(75, ase.upperLimitValue)
        assertEquals("FL", ase.upperLimitUnit)
        assertEquals("STD", ase.upperLimitReference)
        assertEquals(0, ase.lowerLimitValue)
        assertEquals("FT", ase.lowerLimitUnit)
    }

    @Test
    fun parsesAirspaceBoundary() {
        val snapshot = parseSubset()
        assertEquals(1, snapshot.airspaceBoundaries.size)
        val abd = snapshot.airspaceBoundaries.first()
        assertEquals("ase-lowg-ctr", abd.airspaceMid)
        assertEquals(4, abd.vertices.size)
        assertTrue(abd.vertices.all { it.type == "GRC" })
    }

    @Test
    fun parsesDesignatedPoint() {
        val snapshot = parseSubset()
        assertEquals(1, snapshot.designatedPoints.size)
        val dpn = snapshot.designatedPoints.first()
        assertEquals("GRAZ-NORD", dpn.codeId)
        assertEquals("GRAZ NORD", dpn.name)
        assertEquals("VFR-MRP", dpn.codeType)
        assertEquals("LOWG", dpn.associatedAirportCodeId)
    }

    @Test
    fun parsesUnit() {
        val snapshot = parseSubset()
        assertEquals(1, snapshot.units.size)
        val uni = snapshot.units.first()
        assertEquals("GRAZ", uni.name)
        assertEquals("TWR", uni.codeType)
        assertEquals("LOWG", uni.airportCodeId)
        assertEquals("ICAO", uni.codeClass)
    }

    @Test
    fun parsesService() {
        val snapshot = parseSubset()
        assertEquals(1, snapshot.services.size)
        val ser = snapshot.services.first()
        assertEquals("uni-graz-twr", ser.unitMid)
        assertEquals("TWR", ser.codeType)
        assertEquals(0, ser.sequenceNumber)
    }

    @Test
    fun parsesFrequencyAndCallSign() {
        val snapshot = parseSubset()
        assertEquals(1, snapshot.frequencies.size)
        val fqy = snapshot.frequencies.first()
        assertEquals("118.200", fqy.frequencyMhz)
        assertEquals("MHZ", fqy.frequencyUnit)
        assertEquals("STD", fqy.codeType)
        assertEquals("GRAZ TOWER", fqy.callSign)
        assertEquals("EN", fqy.language)
    }

    @Test
    fun parsesServiceAirspaceAssociation() {
        val snapshot = parseSubset()
        assertEquals(1, snapshot.serviceAirspaceAssociations.size)
        val sae = snapshot.serviceAirspaceAssociations.first()
        assertEquals("ser-graz-twr", sae.serviceMid)
        assertEquals("ase-lowg-ctr", sae.airspaceMid)
    }

    @Test
    fun parsesOfmxCoordinateFormat() {
        val lat = parseOfmxLatitude("47.65381389N")
        assertTrue(lat.isRight())
        assertEquals(47.65381389, lat.getOrNull()!!.value, 0.0001)

        val latS = parseOfmxLatitude("33.94611111S")
        assertTrue(latS.isRight())
        assertEquals(-33.94611111, latS.getOrNull()!!.value, 0.0001)

        val lon = parseOfmxLongitude("015.43916667E")
        assertTrue(lon.isRight())
        assertEquals(15.43916667, lon.getOrNull()!!.value, 0.0001)

        val lonW = parseOfmxLongitude("073.78166667W")
        assertTrue(lonW.isRight())
        assertEquals(-73.78166667, lonW.getOrNull()!!.value, 0.0001)
    }
}
