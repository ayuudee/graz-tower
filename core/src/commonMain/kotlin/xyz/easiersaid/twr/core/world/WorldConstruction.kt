package xyz.easiersaid.twr.core.world

import arrow.core.Either
import arrow.core.left
import arrow.core.right

fun buildValidatedWorld(world: AviationWorld): Either<WorldValidationReport, AviationWorld> {
    val report = world.validate()
    return if (report.isValid) world.right() else report.left()
}
