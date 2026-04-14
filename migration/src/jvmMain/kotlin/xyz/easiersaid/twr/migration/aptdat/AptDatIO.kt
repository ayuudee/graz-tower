package xyz.easiersaid.twr.migration.aptdat

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import xyz.easiersaid.twr.migration.common.ParseError
import xyz.easiersaid.twr.migration.common.ParseResult
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.readText

suspend fun parseAptDatFile(
    path: Path,
): Either<NonEmptyList<ParseError>, ParseResult<List<AptDatAirport>>> =
    try {
        val content = path.readText()
        parseAptDat(content)
    } catch (e: IOException) {
        Either.Left(nonEmptyListOf(ParseError(0, "Failed to read file: ${e.message}")))
    }
