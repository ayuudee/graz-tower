package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId

sealed interface OperationalPolicyBranch<out S : OperationalPolicyScope> {
    val id: OperationalPolicyBranchId
}

@JvmInline
value class OperationalPolicyBranchId(val value: String) {
    init {
        require(value.isNotBlank()) { "operational policy branch id must not be blank" }
    }
}

sealed interface OperationalPolicyScope {
    data class AerodromeRunway(
        val aerodrome: AerodromeId,
        val runway: RunwayId,
    ) : OperationalPolicyScope

    data class AerodromeServiceShape(
        val aerodrome: AerodromeId,
        val roles: Set<RoleName>,
    ) : OperationalPolicyScope {
        init {
            require(roles.isNotEmpty()) { "operational policy service shape must name at least one role" }
        }
    }
}

interface ConfiguredPolicyBinding {
    val scope: OperationalPolicyScope
    val branch: OperationalPolicyBranch<*>
}

data class ConfiguredOperationalPolicy<S : OperationalPolicyScope, B : OperationalPolicyBranch<S>>(
    override val scope: S,
    override val branch: B,
) : ConfiguredPolicyBinding

enum class TaxiClearanceLimitPolicy(
    override val id: OperationalPolicyBranchId,
) : OperationalPolicyBranch<OperationalPolicyScope.AerodromeRunway> {
    DeparturesNormallyToRunwayHoldingPoint(
        OperationalPolicyBranchId("icao9432-4.4.departures-normally-to-runway-holding-point"),
    ),
    AlternateAerodromePositionAllowed(
        OperationalPolicyBranchId("icao9432-4.4.alternate-aerodrome-position-allowed"),
    ),
}

enum class TowerTransferPolicy(
    override val id: OperationalPolicyBranchId,
) : OperationalPolicyBranch<OperationalPolicyScope.AerodromeServiceShape> {
    SeparateGroundTowerTransferAtHoldingPoint(
        OperationalPolicyBranchId("icao9432-4.5.1.separate-ground-tower-transfer-at-holding-point"),
    ),
    OtherConfiguredTransferPoint(
        OperationalPolicyBranchId("icao9432-4.5.1.other-configured-transfer-point"),
    ),
}
