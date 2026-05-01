package xyz.easiersaid.twr.migration.ofmx

import arrow.core.Either
import arrow.core.NonEmptyList
import xyz.easiersaid.twr.migration.common.ParseError
import xyz.easiersaid.twr.migration.common.ParseResult

/**
 * Parse OFMX XML content into a snapshot.
 * Platform-specific implementations handle the actual XML processing.
 */
expect fun parseOfmx(xml: String): Either<NonEmptyList<ParseError>, ParseResult<OfmxSnapshot>>
