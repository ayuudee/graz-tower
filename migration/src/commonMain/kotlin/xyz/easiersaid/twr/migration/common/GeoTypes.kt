package xyz.easiersaid.twr.migration.common

import arrow.core.Either
import arrow.core.left
import arrow.core.right

@ConsistentCopyVisibility
data class Latitude private constructor(val value: Double) {
    companion object {
        operator fun invoke(value: Double): Either<String, Latitude> =
            if (value in -90.0..90.0) Latitude(value).right()
            else "Latitude must be in [-90, 90]: $value".left()

        fun unsafe(value: Double): Latitude =
            invoke(value).fold({ error(it) }, { it })
    }

    override fun toString(): String = value.toString()
}

@ConsistentCopyVisibility
data class Longitude private constructor(val value: Double) {
    companion object {
        operator fun invoke(value: Double): Either<String, Longitude> =
            if (value in -180.0..180.0) Longitude(value).right()
            else "Longitude must be in [-180, 180]: $value".left()

        fun unsafe(value: Double): Longitude =
            invoke(value).fold({ error(it) }, { it })
    }

    override fun toString(): String = value.toString()
}

data class GeoCoordinate(val lat: Latitude, val lon: Longitude)
