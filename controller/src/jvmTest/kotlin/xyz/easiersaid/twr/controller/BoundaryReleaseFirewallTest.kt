package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.bdi.HandoffAction
import xyz.easiersaid.twr.controller.bdi.ProcedureSpec
import xyz.easiersaid.twr.controller.bdi.TerminateRadarServiceAction
import xyz.easiersaid.twr.controller.procedure.approachArrivalProcedure
import xyz.easiersaid.twr.controller.procedure.groundTaxiProcedure
import xyz.easiersaid.twr.controller.procedure.towerArrivalProcedure
import xyz.easiersaid.twr.controller.procedure.towerDepartureProcedure
import xyz.easiersaid.twr.protocol.RoleName
import kotlin.test.Test

/**
 * Architectural enforcement test (E17) — every `HandoffAction(role)` rule
 * has a matching `TerminateRadarServiceAction(forRole=role)` sibling at
 * the same procedure stage.
 *
 * Pass 7 (D-PF.7 closure): when the next role is unstaffed, the
 * `IsTransferTargetStaffed(role)` guard on the handoff rule fails. Without
 * a sibling boundary-release rule with the inverse staffing guard, the
 * commitment wedges. This test catches a future regression where a new
 * procedure adds a `HandoffAction` without the sibling.
 *
 * **Reflective**, not source-text scan (per Pass 6 E14 precedent +
 * pre-impl Test-M.2 fold-in): walks the procedure specs' rule lists and
 * partitions by action type. Asserts that the set of roles with
 * `HandoffAction` equals the set of roles with `TerminateRadarServiceAction`
 * per stage. Survives formatting changes; cannot miss a rule defined
 * via a non-standard layout.
 *
 * **No-suppression rule** — never resolved by `@Disabled` / `@Suppress` /
 * test removal. Resolve by adding the missing sibling rule.
 */
class BoundaryReleaseFirewallTest {

    @Test
    fun `every HandoffAction rule has a matching TerminateRadarServiceAction sibling`() {
        val procedures = listOf(
            "TowerDeparture" to towerDepartureProcedure(),
            "TowerArrival" to towerArrivalProcedure(),
            "ApproachArrival" to approachArrivalProcedure(),
            "GroundTaxi" to groundTaxiProcedure(),
        )
        for ((name, procedure) in procedures) {
            checkSiblings(name, procedure)
        }
    }

    private fun checkSiblings(name: String, procedure: ProcedureSpec) {
        for ((stage, rules) in procedure.stageRules) {
            val handoffRoles = rules
                .mapNotNull { (it.action as? HandoffAction)?.toRole }
                .toSet()
            val releaseRoles = rules
                .mapNotNull { (it.action as? TerminateRadarServiceAction)?.forRole }
                .toSet()
            check(handoffRoles == releaseRoles) {
                "FIREWALL VIOLATION: $name stage ${stage.name} mismatch.\n" +
                    "  HandoffAction roles:                   $handoffRoles\n" +
                    "  TerminateRadarServiceAction forRoles: $releaseRoles\n" +
                    "Every HandoffAction(role) rule must have a sibling " +
                    "TerminateRadarServiceAction(forRole=role) rule at the " +
                    "same stage. Pass 7 (D-PF.7) registers D-PF.7 against this " +
                    "invariant; reintroducing a handoff without the boundary-release " +
                    "sibling wedges the commitment when the target is unstaffed."
            }
        }
    }
}
