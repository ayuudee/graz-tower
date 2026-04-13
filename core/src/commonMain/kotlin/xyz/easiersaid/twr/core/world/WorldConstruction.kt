package xyz.easiersaid.twr.core.world

class InvalidWorldException(
    val report: WorldValidationReport
) : IllegalArgumentException(
    buildString {
        append("Invalid aviation world with ")
        append(report.issues.size)
        append(" issue(s)")
        if (report.issues.isNotEmpty()) {
            append(": ")
            append(
                report.issues.joinToString("; ") { issue ->
                    "${issue.code}: ${issue.message}"
                }
            )
        }
    }
)

fun buildValidatedWorld(world: AviationWorld): AviationWorld {
    val report = world.validate()
    if (!report.isValid) {
        throw InvalidWorldException(report)
    }
    return world
}

fun AviationWorld.requireValid(): AviationWorld =
    buildValidatedWorld(this)
