package xyz.easiersaid.twr.migration.common

import arrow.core.Either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GeoTypesTest {

    @Test
    fun validLatitudeReturnsRight() {
        val result = Latitude(46.993)
        assertIs<Either.Right<Latitude>>(result)
        assertEquals(46.993, result.value.value)
    }

    @Test
    fun latitudeAtBoundariesReturnsRight() {
        assertIs<Either.Right<Latitude>>(Latitude(90.0))
        assertIs<Either.Right<Latitude>>(Latitude(-90.0))
        assertIs<Either.Right<Latitude>>(Latitude(0.0))
    }

    @Test
    fun latitudeOutOfRangeReturnsLeft() {
        assertIs<Either.Left<String>>(Latitude(90.1))
        assertIs<Either.Left<String>>(Latitude(-90.1))
        assertIs<Either.Left<String>>(Latitude(180.0))
    }

    @Test
    fun validLongitudeReturnsRight() {
        val result = Longitude(15.439)
        assertIs<Either.Right<Longitude>>(result)
        assertEquals(15.439, result.value.value)
    }

    @Test
    fun longitudeAtBoundariesReturnsRight() {
        assertIs<Either.Right<Longitude>>(Longitude(180.0))
        assertIs<Either.Right<Longitude>>(Longitude(-180.0))
        assertIs<Either.Right<Longitude>>(Longitude(0.0))
    }

    @Test
    fun longitudeOutOfRangeReturnsLeft() {
        assertIs<Either.Left<String>>(Longitude(180.1))
        assertIs<Either.Left<String>>(Longitude(-180.1))
    }

    @Test
    fun unsafeFactoryReturnsValueForValidInput() {
        val lat = Latitude.unsafe(46.993)
        assertEquals(46.993, lat.value)
        val lon = Longitude.unsafe(15.439)
        assertEquals(15.439, lon.value)
    }

    @Test
    fun geoCoordinateHoldsLatAndLon() {
        val coord = GeoCoordinate(Latitude.unsafe(46.993), Longitude.unsafe(15.439))
        assertEquals(46.993, coord.lat.value)
        assertEquals(15.439, coord.lon.value)
    }
}
