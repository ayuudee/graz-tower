package xyz.easiersaid.twr.migration.ofmx

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import xyz.easiersaid.twr.migration.common.GeoCoordinate
import xyz.easiersaid.twr.migration.common.ParseError
import xyz.easiersaid.twr.migration.common.ParseResult
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

actual fun parseOfmx(xml: String): Either<NonEmptyList<ParseError>, ParseResult<OfmxSnapshot>> =
    try {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(org.xml.sax.InputSource(StringReader(xml)))
        parseDocument(doc)
    } catch (e: org.xml.sax.SAXException) {
        nonEmptyListOf(ParseError(0, "XML parse error: ${e.message}")).left()
    } catch (e: java.io.IOException) {
        nonEmptyListOf(ParseError(0, "XML read error: ${e.message}")).left()
    } catch (e: javax.xml.parsers.ParserConfigurationException) {
        nonEmptyListOf(ParseError(0, "XML parser config error: ${e.message}")).left()
    }

private fun parseDocument(
    doc: Document,
): Either<NonEmptyList<ParseError>, ParseResult<OfmxSnapshot>> {
    val root = doc.documentElement
    val warnings = mutableListOf<ParseError>()

    val airports = mutableListOf<OfmxAirport>()
    val runways = mutableListOf<OfmxRunway>()
    val runwayDirections = mutableListOf<OfmxRunwayDirection>()
    val airspaces = mutableListOf<OfmxAirspace>()
    val boundaries = mutableListOf<OfmxAirspaceBoundary>()
    val designatedPoints = mutableListOf<OfmxDesignatedPoint>()
    val units = mutableListOf<OfmxUnit>()
    val services = mutableListOf<OfmxService>()
    val frequencies = mutableListOf<OfmxFrequency>()
    val associations = mutableListOf<OfmxServiceAirspaceAssociation>()

    root.childElements().forEach { elem ->
        when (elem.tagName) {
            "Ahp" -> parseAhp(elem).fold({ warnings.add(it) }, { airports.add(it) })
            "Rwy" -> parseRwy(elem).fold({ warnings.add(it) }, { runways.add(it) })
            "Rdn" -> parseRdn(elem).fold({ warnings.add(it) }, { runwayDirections.add(it) })
            "Ase" -> parseAse(elem).fold({ warnings.add(it) }, { airspaces.add(it) })
            "Abd" -> parseAbd(elem).fold({ warnings.add(it) }, { boundaries.add(it) })
            "Dpn" -> parseDpn(elem).fold({ warnings.add(it) }, { designatedPoints.add(it) })
            "Uni" -> parseUni(elem).fold({ warnings.add(it) }, { units.add(it) })
            "Ser" -> parseSer(elem).fold({ warnings.add(it) }, { services.add(it) })
            "Fqy" -> parseFqy(elem).fold({ warnings.add(it) }, { frequencies.add(it) })
            "Sae" -> parseSae(elem).fold({ warnings.add(it) }, { associations.add(it) })
        }
    }

    val snapshot = OfmxSnapshot(
        airports = airports.toList(),
        runways = runways.toList(),
        runwayDirections = runwayDirections.toList(),
        airspaces = airspaces.toList(),
        airspaceBoundaries = boundaries.toList(),
        designatedPoints = designatedPoints.toList(),
        units = units.toList(),
        services = services.toList(),
        frequencies = frequencies.toList(),
        serviceAirspaceAssociations = associations.toList(),
    )
    return ParseResult(snapshot, warnings.toList()).right()
}

// -- Element parsers --

private fun parseAhp(elem: Element): Either<ParseError, OfmxAirport> {
    val uid = elem.firstChild("AhpUid")
        ?: return ParseError(0, "Ahp missing AhpUid").left()
    val mid = uid.getAttribute("mid")
    val region = uid.getAttribute("region")
    val codeId = uid.textChild("codeId") ?: return ParseError(0, "Ahp missing codeId").left()

    val latText = elem.textChild("geoLat")
    val lonText = elem.textChild("geoLong")
    val position = if (latText != null && lonText != null) {
        parseOfmxCoordinate(latText, lonText).fold(
            { return ParseError(0, "Ahp $codeId bad coords: $it").left() },
            { it },
        )
    } else {
        return ParseError(0, "Ahp $codeId missing coordinates").left()
    }

    return OfmxAirport(
        mid = mid,
        region = region,
        codeId = codeId,
        name = elem.textChild("txtName") ?: "",
        icao = elem.textChild("codeIcao"),
        iata = elem.textChild("codeIata"),
        codeType = elem.textChild("codeType") ?: "",
        position = position,
        elevationFeet = elem.textChild("valElev")?.trim()?.toIntOrNull(),
        elevationUnit = elem.textChild("uomDistVer"),
        magneticVariation = elem.textChild("valMagVar")?.trim()?.toIntOrNull(),
        transitionAltitude = elem.textChild("valTransitionAlt")?.trim()?.toIntOrNull(),
        transitionAltitudeUnit = elem.textChild("uomTransitionAlt"),
        city = elem.textChild("txtNameCitySer"),
        remarks = elem.textChild("txtRmk"),
    ).right()
}

private fun parseRwy(elem: Element): Either<ParseError, OfmxRunway> {
    val uid = elem.firstChild("RwyUid")
        ?: return ParseError(0, "Rwy missing RwyUid").left()
    val mid = uid.getAttribute("mid")
    val ahpUid = uid.firstChild("AhpUid")
    val airportMid = ahpUid?.getAttribute("mid") ?: ""
    val airportCodeId = ahpUid?.textChild("codeId") ?: ""
    val designator = uid.textChild("txtDesig") ?: ""

    return OfmxRunway(
        mid = mid,
        airportMid = airportMid,
        airportCodeId = airportCodeId,
        designator = designator,
        lengthMeters = elem.textChild("valLen")?.trim()?.toIntOrNull(),
        widthMeters = elem.textChild("valWid")?.trim()?.toIntOrNull(),
        lengthWidthUnit = elem.textChild("uomDimRwy"),
        composition = elem.textChild("codeComposition"),
        preparation = elem.textChild("codePreparation"),
        pcnClass = elem.textChild("valPcnClass")?.trim()?.toIntOrNull(),
        pcnPavementType = elem.textChild("codePcnPavementType"),
        pcnPavementSubgrade = elem.textChild("codePcnPavementSubgrade"),
    ).right()
}

private fun parseRdn(elem: Element): Either<ParseError, OfmxRunwayDirection> {
    val uid = elem.firstChild("RdnUid")
        ?: return ParseError(0, "Rdn missing RdnUid").left()
    val mid = uid.getAttribute("mid")
    val rwyUid = uid.firstChild("RwyUid")
    val runwayMid = rwyUid?.getAttribute("mid") ?: ""
    val designator = uid.textChild("txtDesig") ?: ""

    val latText = elem.textChild("geoLat")
    val lonText = elem.textChild("geoLong")
    val position = if (latText != null && lonText != null) {
        parseOfmxCoordinate(latText, lonText).getOrNull()
    } else {
        null
    }

    return OfmxRunwayDirection(
        mid = mid,
        runwayMid = runwayMid,
        designator = designator,
        position = position,
        trueBearing = elem.textChild("valTrueBrg")?.trim()?.toIntOrNull(),
        magneticBearing = elem.textChild("valMagBrg")?.trim()?.toIntOrNull(),
    ).right()
}

private fun parseAse(elem: Element): Either<ParseError, OfmxAirspace> {
    val uid = elem.firstChild("AseUid")
        ?: return ParseError(0, "Ase missing AseUid").left()
    val mid = uid.getAttribute("mid")
    val region = uid.getAttribute("region")
    val codeType = uid.textChild("codeType") ?: ""
    val codeId = uid.textChild("codeId") ?: ""

    return OfmxAirspace(
        mid = mid,
        region = region,
        codeType = codeType,
        codeId = codeId,
        name = elem.textChild("txtName"),
        nameAlt = elem.textChild("txtNameAlt"),
        upperLimitValue = elem.textChild("valDistVerUpper")?.trim()?.toIntOrNull(),
        upperLimitUnit = elem.textChild("uomDistVerUpper"),
        upperLimitReference = elem.textChild("codeDistVerUpper"),
        lowerLimitValue = elem.textChild("valDistVerLower")?.trim()?.toIntOrNull(),
        lowerLimitUnit = elem.textChild("uomDistVerLower"),
        lowerLimitReference = elem.textChild("codeDistVerLower"),
    ).right()
}

private fun parseAbd(elem: Element): Either<ParseError, OfmxAirspaceBoundary> {
    val uid = elem.firstChild("AbdUid")
        ?: return ParseError(0, "Abd missing AbdUid").left()
    val mid = uid.getAttribute("mid")
    val aseUid = uid.firstChild("AseUid")
    val airspaceMid = aseUid?.getAttribute("mid") ?: ""

    val vertices = elem.children("Avx").mapNotNull { avx ->
        val type = avx.textChild("codeType") ?: return@mapNotNull null
        val latText = avx.textChild("geoLat") ?: return@mapNotNull null
        val lonText = avx.textChild("geoLong") ?: return@mapNotNull null
        val position = parseOfmxCoordinate(latText, lonText).getOrNull() ?: return@mapNotNull null
        val borderName = avx.firstChild("GbrUid")?.textChild("txtName")
        BoundaryVertex(type, position, borderName)
    }

    return OfmxAirspaceBoundary(mid = mid, airspaceMid = airspaceMid, vertices = vertices).right()
}

private fun parseDpn(elem: Element): Either<ParseError, OfmxDesignatedPoint> {
    val uid = elem.firstChild("DpnUid")
        ?: return ParseError(0, "Dpn missing DpnUid").left()
    val mid = uid.getAttribute("mid")
    val region = uid.getAttribute("region")
    val codeId = uid.textChild("codeId") ?: ""

    val latText = uid.textChild("geoLat")
    val lonText = uid.textChild("geoLong")
    val position = if (latText != null && lonText != null) {
        parseOfmxCoordinate(latText, lonText).fold(
            { return ParseError(0, "Dpn $codeId bad coords: $it").left() },
            { it },
        )
    } else {
        return ParseError(0, "Dpn $codeId missing coordinates").left()
    }

    val ahpAssoc = elem.firstChild("AhpUidAssoc")
    val associatedAirportCodeId = ahpAssoc?.textChild("codeId")

    return OfmxDesignatedPoint(
        mid = mid,
        region = region,
        codeId = codeId,
        position = position,
        codeType = elem.textChild("codeType"),
        name = elem.textChild("txtName"),
        associatedAirportCodeId = associatedAirportCodeId,
    ).right()
}

private fun parseUni(elem: Element): Either<ParseError, OfmxUnit> {
    val uid = elem.firstChild("UniUid")
        ?: return ParseError(0, "Uni missing UniUid").left()
    val mid = uid.getAttribute("mid")
    val region = uid.getAttribute("region")
    val name = uid.textChild("txtName") ?: ""
    val codeType = uid.textChild("codeType") ?: ""

    val ahpUid = elem.firstChild("AhpUid")
    val airportCodeId = ahpUid?.textChild("codeId")

    return OfmxUnit(
        mid = mid,
        region = region,
        name = name,
        codeType = codeType,
        airportCodeId = airportCodeId,
        codeClass = elem.textChild("codeClass"),
    ).right()
}

private fun parseSer(elem: Element): Either<ParseError, OfmxService> {
    val uid = elem.firstChild("SerUid")
        ?: return ParseError(0, "Ser missing SerUid").left()
    val mid = uid.getAttribute("mid")
    val uniUid = uid.firstChild("UniUid")
    val unitMid = uniUid?.getAttribute("mid") ?: ""
    val codeType = uid.textChild("codeType") ?: ""
    val seq = uid.textChild("noSeq")?.trim()?.toIntOrNull() ?: 0

    return OfmxService(mid = mid, unitMid = unitMid, codeType = codeType, sequenceNumber = seq).right()
}

private fun parseFqy(elem: Element): Either<ParseError, OfmxFrequency> {
    val uid = elem.firstChild("FqyUid")
        ?: return ParseError(0, "Fqy missing FqyUid").left()
    val mid = uid.getAttribute("mid")
    val serUid = uid.firstChild("SerUid")
    val serviceMid = serUid?.getAttribute("mid") ?: ""
    val freqMhz = uid.textChild("valFreqTrans") ?: ""

    val cdl = elem.firstChild("Cdl")
    val callSign = cdl?.textChild("txtCallSign")
    val language = cdl?.textChild("codeLang")

    return OfmxFrequency(
        mid = mid,
        serviceMid = serviceMid,
        frequencyMhz = freqMhz,
        frequencyUnit = elem.textChild("uomFreq"),
        codeType = elem.textChild("codeType"),
        callSign = callSign,
        language = language,
    ).right()
}

private fun parseSae(elem: Element): Either<ParseError, OfmxServiceAirspaceAssociation> {
    val uid = elem.firstChild("SaeUid")
        ?: return ParseError(0, "Sae missing SaeUid").left()
    val mid = uid.getAttribute("mid")
    val serUid = uid.firstChild("SerUid")
    val serviceMid = serUid?.getAttribute("mid") ?: ""
    val aseUid = uid.firstChild("AseUid")
    val airspaceMid = aseUid?.getAttribute("mid") ?: ""

    return OfmxServiceAirspaceAssociation(mid = mid, serviceMid = serviceMid, airspaceMid = airspaceMid).right()
}

// -- DOM helpers --

private fun Element.childElements(): List<Element> {
    val result = mutableListOf<Element>()
    val nodes: NodeList = childNodes
    for (i in 0 until nodes.length) {
        val node = nodes.item(i)
        if (node is Element) result.add(node)
    }
    return result
}

private fun Element.firstChild(tagName: String): Element? {
    val nodes = getElementsByTagName(tagName)
    for (i in 0 until nodes.length) {
        val node = nodes.item(i)
        if (node is Element && node.parentNode == this) return node
    }
    return null
}

private fun Element.children(tagName: String): List<Element> {
    val result = mutableListOf<Element>()
    val nodes = getElementsByTagName(tagName)
    for (i in 0 until nodes.length) {
        val node = nodes.item(i)
        if (node is Element && node.parentNode == this) result.add(node)
    }
    return result
}

private fun Element.textChild(tagName: String): String? {
    val child = firstChild(tagName) ?: return null
    return child.textContent?.takeIf { it.isNotBlank() }
}
