package xyz.easiersaid.twr.migration.ofmx

import xyz.easiersaid.twr.migration.common.GeoCoordinate

/**
 * Domain model faithful to the OFMX/AIXM 4.5 XML structure.
 * Models what is in the file, not our ATC domain.
 */
data class OfmxSnapshot(
    val airports: List<OfmxAirport>,
    val runways: List<OfmxRunway>,
    val runwayDirections: List<OfmxRunwayDirection>,
    val airspaces: List<OfmxAirspace>,
    val airspaceBoundaries: List<OfmxAirspaceBoundary>,
    val designatedPoints: List<OfmxDesignatedPoint>,
    val units: List<OfmxUnit>,
    val services: List<OfmxService>,
    val frequencies: List<OfmxFrequency>,
    val serviceAirspaceAssociations: List<OfmxServiceAirspaceAssociation>,
)

// -- <Ahp> Airport/Heliport --

data class OfmxAirport(
    val mid: String,
    val region: String,
    val codeId: String,
    val name: String,
    val icao: String?,
    val iata: String?,
    val codeType: String,
    val position: GeoCoordinate,
    val elevationFeet: Int?,
    val elevationUnit: String?,
    val magneticVariation: Int?,
    val transitionAltitude: Int?,
    val transitionAltitudeUnit: String?,
    val city: String?,
    val remarks: String?,
)

// -- <Rwy> Runway --

data class OfmxRunway(
    val mid: String,
    val airportMid: String,
    val airportCodeId: String,
    val designator: String,
    val lengthMeters: Int?,
    val widthMeters: Int?,
    val lengthWidthUnit: String?,
    val composition: String?,
    val preparation: String?,
    val pcnClass: Int?,
    val pcnPavementType: String?,
    val pcnPavementSubgrade: String?,
)

// -- <Rdn> Runway Direction --

data class OfmxRunwayDirection(
    val mid: String,
    val runwayMid: String,
    val designator: String,
    val position: GeoCoordinate?,
    val trueBearing: Int?,
    val magneticBearing: Int?,
)

// -- <Ase> Airspace --

data class OfmxAirspace(
    val mid: String,
    val region: String,
    val codeType: String,
    val codeId: String,
    val name: String?,
    val nameAlt: String?,
    val upperLimitValue: Int?,
    val upperLimitUnit: String?,
    val upperLimitReference: String?,
    val lowerLimitValue: Int?,
    val lowerLimitUnit: String?,
    val lowerLimitReference: String?,
)

// -- <Abd> Airspace Boundary --

data class OfmxAirspaceBoundary(
    val mid: String,
    val airspaceMid: String,
    val vertices: List<BoundaryVertex>,
)

data class BoundaryVertex(
    val type: String,
    val position: GeoCoordinate,
    val borderName: String?,
)

// -- <Dpn> Designated Point --

data class OfmxDesignatedPoint(
    val mid: String,
    val region: String,
    val codeId: String,
    val position: GeoCoordinate,
    val codeType: String?,
    val name: String?,
    val associatedAirportCodeId: String?,
)

// -- <Uni> ATC Unit --

data class OfmxUnit(
    val mid: String,
    val region: String,
    val name: String,
    val codeType: String,
    val airportCodeId: String?,
    val codeClass: String?,
)

// -- <Ser> Service --

data class OfmxService(
    val mid: String,
    val unitMid: String,
    val codeType: String,
    val sequenceNumber: Int,
)

// -- <Fqy> Frequency --

data class OfmxFrequency(
    val mid: String,
    val serviceMid: String,
    val frequencyMhz: String,
    val frequencyUnit: String?,
    val codeType: String?,
    val callSign: String?,
    val language: String?,
)

// -- <Sae> Service-Airspace Association --

data class OfmxServiceAirspaceAssociation(
    val mid: String,
    val serviceMid: String,
    val airspaceMid: String,
)
