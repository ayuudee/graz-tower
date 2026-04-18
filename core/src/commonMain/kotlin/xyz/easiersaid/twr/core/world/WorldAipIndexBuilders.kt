package xyz.easiersaid.twr.core.world

import xyz.easiersaid.twr.protocol.PointId

internal fun collectAerodromeAipEntries(aerodrome: Aerodrome): List<Pair<PointId, EntityRef>> =
    collectOperationalSectorEntries(aerodrome) +
        collectPublishedVfrProcedureEntries(aerodrome)

private fun collectOperationalSectorEntries(aerodrome: Aerodrome): List<Pair<PointId, EntityRef>> =
    aerodrome.aip.operationalSectors.values.flatMap { sector ->
        val ref = EntityRef.OperationalSectorRef(sector.id)
        sector.boundary.rings.flatMap { ring -> ring.points.map { point -> point to ref } } +
            listOfNotNull(sector.anchor.pointOrNull()?.let { point -> point to ref }) +
            sector.entryExitPoints.map { point -> point to ref }
    }

private fun collectPublishedVfrProcedureEntries(
    aerodrome: Aerodrome
): List<Pair<PointId, EntityRef>> =
    aerodrome.aip.publishedVfrProcedures.values.flatMap { procedure ->
        val ref = EntityRef.PublishedVfrProcedureRef(procedure.id)
        val sequenceEntries = procedure.publishedSequence.mapNotNull { point ->
            point.pointOrNull()?.let { resolved -> resolved to ref }
        }
        val labelEntries = procedure.mapLabels.mapNotNull { label ->
            label.location.pointOrNull()?.let { resolved -> resolved to ref }
        }
        val commFailureEntries = procedure.communicationFailure
            ?.afterContactEstablishedExitSequence
            .orEmpty()
            .mapNotNull { point ->
                point.pointOrNull()?.let { resolved -> resolved to ref }
            }
        val contactTimingEntries = listOfNotNull(
            procedure.contactRequirement?.timing?.pointOrNull()?.let { point -> point to ref },
        )
        val terminationEntries = listOfNotNull(
            procedure.terminatesAt?.pointOrNull()?.let { point -> point to ref },
            procedure.holdAt?.pointOrNull()?.let { point -> point to ref },
        )
        sequenceEntries + labelEntries + commFailureEntries + contactTimingEntries + terminationEntries
    }
