package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertTrue
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.ExpediteTaxi
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.protocol.TaxiViaRunway

/**
 * ICAO 9432 chunk-03 taxi source-mapped evidence for §4.4.
 *
 * The universal clearance-limit source unit deliberately lands covered-red:
 * ICAO Doc 9432 §4.4.1 says a controller-issued taxi instruction always
 * contains a clearance limit, but the current typed protocol still contains
 * taxi-like instruction leaves without a clearance-limit field. This test
 * makes that mismatch visible without greening the source from a single LOWG
 * trace.
 */
class Icao9432Chunk03GroundMovementEvidenceTest {
    private val aircraft = AircraftId("OE-ABC")
    private val controller = ControllerId("LOWG_GND")

    @Test
    fun `taxi instruction clearance-limit source unit lands covered red against typed protocol surface`() {
        val report = simEvidence("icao9432-chunk03-taxi-clearance-limit-structural") {
            observe {
                EvidenceFactAdapters.fromProjectedPayloads(
                    scenarioId = "icao9432-chunk03-taxi-clearance-limit-structural",
                    payloads = listOf(
                        instructionPayload(
                            TaxiToHoldingPoint(
                                target = aircraft,
                                destination = PointId("LOWG-HP-C"),
                                runway = RunwayId("16C"),
                            ),
                        ),
                        instructionPayload(
                            TaxiViaRunway(
                                target = aircraft,
                                runway = RunwayId("16C"),
                                destination = null,
                            ),
                        ),
                        instructionPayload(ExpediteTaxi(target = aircraft)),
                    ),
                )
            }

            source("taxi-instruction-clearance-limit") {
                cites(ICAO9432.Taxi.TaxiInstructionClearanceLimitMandatory)
                expect { taxiInstructions().allHaveClearanceLimit() }
            }
        }

        val result = report.results.single()
        assertTrue(
            result.outcome is EvidenceAuditOutcome.Fail,
            "expected covered-red taxi clearance-limit audit; got ${result.outcome}",
        )
        assertTrue(result.activationFactIds.isNotEmpty())
    }

    private fun instructionPayload(instruction: AtcInstruction): EvidenceFactPayload.Instruction =
        EvidenceFactPayload.Instruction(
            controllerId = controller,
            aircraftId = aircraft,
            instruction = instruction,
        )
}
