package xyz.easiersaid.twr.core.clearance

import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.FixtureIds
import xyz.easiersaid.twr.core.world.sampleWorld
import xyz.easiersaid.twr.protocol.PointId

internal fun sampleWorldWithAirspacePoints(points: Set<PointId>): AviationWorld {
    val world = sampleWorld()
    return world.copy(
        airspace = world.airspace.mapValues { (id, volume) ->
            if (id == FixtureIds.airspace) {
                volume.copy(memberPoints = points)
            } else {
                volume
            }
        }
    )
}
