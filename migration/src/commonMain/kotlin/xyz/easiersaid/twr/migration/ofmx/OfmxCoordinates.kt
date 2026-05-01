package xyz.easiersaid.twr.migration.ofmx

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import xyz.easiersaid.twr.migration.common.GeoCoordinate
import xyz.easiersaid.twr.migration.common.Latitude
import xyz.easiersaid.twr.migration.common.Longitude

/**
 * Parse OFMX latitude format: "47.65381389N" or "47.65381389S"
 */
fun parseOfmxLatitude(text: String): Either<String, Latitude> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return "Empty latitude string".left()
    val lastChar = trimmed.last()
    val sign = when (lastChar) {
        'N' -> 1.0
        'S' -> -1.0
        else -> return "Latitude must end with N or S: $trimmed".left()
    }
    val numStr = trimmed.dropLast(1)
    val value = numStr.toDoubleOrNull()
        ?: return "Invalid latitude number: $numStr".left()
    return Latitude(value * sign)
        .mapLeft { "Invalid latitude value: $it" }
}

/**
 * Parse OFMX longitude format: "015.43916667E" or "015.43916667W"
 */
fun parseOfmxLongitude(text: String): Either<String, Longitude> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return "Empty longitude string".left()
    val lastChar = trimmed.last()
    val sign = when (lastChar) {
        'E' -> 1.0
        'W' -> -1.0
        else -> return "Longitude must end with E or W: $trimmed".left()
    }
    val numStr = trimmed.dropLast(1)
    val value = numStr.toDoubleOrNull()
        ?: return "Invalid longitude number: $numStr".left()
    return Longitude(value * sign)
        .mapLeft { "Invalid longitude value: $it" }
}

/**
 * Parse an OFMX coordinate pair.
 */
fun parseOfmxCoordinate(latText: String, lonText: String): Either<String, GeoCoordinate> {
    val lat = parseOfmxLatitude(latText).fold({ return it.left() }, { it })
    val lon = parseOfmxLongitude(lonText).fold({ return it.left() }, { it })
    return GeoCoordinate(lat, lon).right()
}
