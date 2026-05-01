package xyz.easiersaid.twr.migration.common

data class ParseError(
    val line: Int,
    val message: String,
    val recordCode: String? = null,
)

data class ParseResult<out T>(
    val value: T,
    val warnings: List<ParseError> = emptyList(),
)
