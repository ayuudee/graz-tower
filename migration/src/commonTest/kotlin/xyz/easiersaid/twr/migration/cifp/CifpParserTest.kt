package xyz.easiersaid.twr.migration.cifp

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CifpParserTest {

    private fun loadResource(name: String): String =
        this::class.java.classLoader.getResource(name)?.readText()
            ?: error("Resource not found: $name")

    private fun parseLowg(): CifpAirport {
        val content = loadResource("cifp/LOWG.dat")
        val result = parseCifp(content).getOrElse { errors ->
            error("Parse failed: ${errors.toList().joinToString("\n") { it.message }}")
        }
        return result.value
    }

    // -- SIDs --

    @Test
    fun parsesAllSidProcedures() {
        val airport = parseLowg()
        assertTrue(airport.sids.isNotEmpty())
        val sidNames = airport.sids.map { it.name }.toSet()
        assertTrue(sidNames.contains("ABIR3V"))
        assertTrue(sidNames.contains("GRZ4X"))
        assertTrue(sidNames.contains("MURE5G"))
        assertEquals(21, sidNames.size)
    }

    @Test
    fun sidLegsHaveCorrectPathTerminators() {
        val airport = parseLowg()
        val allLegs = airport.sids.flatMap { it.legs }
        assertTrue(allLegs.any { it.pathTerminator == PathTerminator.CA })
        assertTrue(allLegs.any { it.pathTerminator == PathTerminator.DF })
        assertTrue(allLegs.any { it.pathTerminator == PathTerminator.TF })
    }

    @Test
    fun sidHasRouteTypes() {
        val airport = parseLowg()
        val routeTypes = airport.sids.map { it.routeType }.toSet()
        assertTrue(routeTypes.contains("5")) // RNAV SID
        assertTrue(routeTypes.contains("2")) // conventional SID
    }

    @Test
    fun sidLegsHaveAltitudeConstraints() {
        val airport = parseLowg()
        val abir3v = airport.sids.first { it.name == "ABIR3V" }
        val firstLeg = abir3v.legs.first()
        assertNotNull(firstLeg.altitudeConstraint)
        assertEquals(AltitudeConstraintType.AT_OR_ABOVE, firstLeg.altitudeConstraint!!.type)
        assertEquals(3500, firstLeg.altitudeConstraint!!.altitude1)
    }

    @Test
    fun sidLegsHaveFixReferences() {
        val airport = parseLowg()
        val abir3v = airport.sids.filter { it.name == "ABIR3V" }
        val legs = abir3v.flatMap { it.legs }
        val fixIds = legs.mapNotNull { it.fix?.id }
        assertTrue(fixIds.contains("ABIRI"))
        assertTrue(fixIds.contains("WG608"))
    }

    @Test
    fun sidFirstLegHasCenterFix() {
        val airport = parseLowg()
        val abir3v = airport.sids.first { it.name == "ABIR3V" }
        val firstLeg = abir3v.legs.first()
        // CA leg has no recommended navaid but has a center fix (GRZ VOR)
        assertNotNull(firstLeg.centerFix)
        assertEquals("GRZ", firstLeg.centerFix!!.id)
        assertEquals("D", firstLeg.centerFix!!.section)
    }

    // -- STARs --

    @Test
    fun parsesAllStarProcedures() {
        val airport = parseLowg()
        assertTrue(airport.stars.isNotEmpty())
        val starNames = airport.stars.map { it.name }.toSet()
        assertTrue(starNames.contains("ABIR1M"))
        assertTrue(starNames.contains("GOTA2M"))
        assertEquals(8, starNames.size)
    }

    @Test
    fun starsHaveCorrectLegs() {
        val airport = parseLowg()
        val abir1m = airport.stars.first { it.name == "ABIR1M" }
        assertEquals(3, abir1m.legs.size)
        assertEquals(PathTerminator.IF, abir1m.legs[0].pathTerminator)
        assertEquals(PathTerminator.TF, abir1m.legs[1].pathTerminator)
        assertEquals(PathTerminator.TF, abir1m.legs[2].pathTerminator)
    }

    @Test
    fun starLegsHaveFixes() {
        val airport = parseLowg()
        val abir1m = airport.stars.first { it.name == "ABIR1M" }
        assertEquals("ABIRI", abir1m.legs[0].fix?.id)
        assertEquals("WG508", abir1m.legs[1].fix?.id)
        assertEquals("XIBAR", abir1m.legs[2].fix?.id)
    }

    @Test
    fun starHasCenterFixReference() {
        val airport = parseLowg()
        val abir1m = airport.stars.first { it.name == "ABIR1M" }
        val firstLeg = abir1m.legs.first()
        assertNotNull(firstLeg.centerFix)
        assertEquals("XIBAR", firstLeg.centerFix!!.id)
    }

    // -- Approaches --

    @Test
    fun parsesAllApproachProcedures() {
        val airport = parseLowg()
        assertTrue(airport.approaches.isNotEmpty())
        val approachNames = airport.approaches.map { it.name }.toSet()
        assertTrue(approachNames.contains("D16C"))
        assertTrue(approachNames.contains("D34C"))
        assertTrue(approachNames.contains("I34C"))
        assertTrue(approachNames.contains("R16C"))
        assertTrue(approachNames.contains("R34C"))
    }

    @Test
    fun approachesHaveTransitions() {
        val airport = parseLowg()
        val d16cApproaches = airport.approaches.filter { it.name == "D16C" }
        val routeTypes = d16cApproaches.map { it.routeType }.toSet()
        assertTrue(routeTypes.contains("A")) // approach transition
        assertTrue(routeTypes.contains("D")) // final approach
    }

    @Test
    fun ilsApproachHasCorrectStructure() {
        val airport = parseLowg()
        val ilsFinal = airport.approaches.first { it.name == "I34C" && it.routeType == "I" }
        assertTrue(ilsFinal.legs.isNotEmpty())
        val firstLeg = ilsFinal.legs.first()
        assertEquals("CI34C", firstLeg.fix?.id)
        assertEquals(PathTerminator.IF, firstLeg.pathTerminator)
        assertNotNull(firstLeg.recommendedNavaid)
        assertEquals("OEG", firstLeg.recommendedNavaid!!.id)
    }

    @Test
    fun approachHasMissedApproachLegs() {
        val airport = parseLowg()
        val ilsFinal = airport.approaches.first { it.name == "I34C" && it.routeType == "I" }
        // After the runway fix, there should be missed approach legs (DF, HM)
        assertTrue(ilsFinal.legs.any { it.pathTerminator == PathTerminator.HM })
    }

    @Test
    fun approachRouteTypesDecoded() {
        val airport = parseLowg()
        val routeTypes = airport.approaches.map { it.routeType }.toSet()
        assertTrue(routeTypes.contains("A")) // approach transitions
        assertTrue(routeTypes.contains("D")) // VOR final
        assertTrue(routeTypes.contains("I")) // ILS final
        assertTrue(routeTypes.contains("R")) // RNAV final
    }

    // -- Runways --

    @Test
    fun parsesSixRunways() {
        val airport = parseLowg()
        assertEquals(6, airport.runways.size)
        val designators = airport.runways.map { it.designator }.toSet()
        assertTrue(designators.contains("RW16C"))
        assertTrue(designators.contains("RW34C"))
        assertTrue(designators.contains("RW16L"))
        assertTrue(designators.contains("RW34R"))
    }

    @Test
    fun runwayHasThresholdCoordinates() {
        val airport = parseLowg()
        val rw16c = airport.runways.first { it.designator == "RW16C" }
        assertNotNull(rw16c.thresholdLatitude)
        assertNotNull(rw16c.thresholdLongitude)
        assertEquals("N47000722", rw16c.thresholdLatitude)
        assertEquals("E015261181", rw16c.thresholdLongitude)
    }

    @Test
    fun runwayHasElevation() {
        val airport = parseLowg()
        val rw16c = airport.runways.first { it.designator == "RW16C" }
        assertEquals(1117, rw16c.elevation)
    }

    @Test
    fun runwayWithIlsHasIdentifier() {
        val airport = parseLowg()
        val rw34c = airport.runways.first { it.designator == "RW34C" }
        assertEquals("OEG", rw34c.ilsIdentifier)
        assertEquals(3, rw34c.ilsCategory)
    }

    @Test
    fun runwayWithoutIlsHasNullIdentifier() {
        val airport = parseLowg()
        val rw16l = airport.runways.first { it.designator == "RW16L" }
        assertTrue(rw16l.ilsIdentifier == null)
    }

    // -- Precision Data --

    @Test
    fun parsesPrecisionApproachData() {
        val airport = parseLowg()
        assertEquals(2, airport.precisionApproachData.size)
        val first = airport.precisionApproachData.first()
        assertTrue(first.minimums.any { it.label.contains("LPV") })
        assertTrue(first.minimums.any { it.label.contains("LNAV") })
    }

    // -- Leg counts --

    @Test
    fun totalLegCountMatchesFile() {
        val airport = parseLowg()
        val totalSidLegs = airport.sids.sumOf { it.legs.size }
        val totalStarLegs = airport.stars.sumOf { it.legs.size }
        val totalApproachLegs = airport.approaches.sumOf { it.legs.size }
        assertEquals(56, totalSidLegs)
        assertEquals(24, totalStarLegs)
        assertEquals(93, totalApproachLegs)
    }

    // -- Error handling --

    @Test
    fun emptyContentProducesEmptyAirport() {
        val result = parseCifp("").getOrElse { error("Should not fail") }
        assertTrue(result.value.sids.isEmpty())
        assertTrue(result.value.stars.isEmpty())
        assertTrue(result.value.approaches.isEmpty())
    }

    @Test
    fun pathTerminatorCoverage() {
        val airport = parseLowg()
        val allLegs = airport.sids.flatMap { it.legs } +
            airport.stars.flatMap { it.legs } +
            airport.approaches.flatMap { it.legs }
        val terminators = allLegs.map { it.pathTerminator }.toSet()
        assertTrue(terminators.contains(PathTerminator.IF))
        assertTrue(terminators.contains(PathTerminator.TF))
        assertTrue(terminators.contains(PathTerminator.CF))
        assertTrue(terminators.contains(PathTerminator.DF))
        assertTrue(terminators.contains(PathTerminator.CA))
        assertTrue(terminators.contains(PathTerminator.HM))
    }
}
