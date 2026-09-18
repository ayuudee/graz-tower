package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedForTakeoffReadback
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint

class EvidenceDslTest {
    @Test
    fun `protocolEvidence expresses structural readback without phraseology overclaim`() {
        val aircraft = AircraftId("OE-ABC")
        val runway = RunwayId("16C")

        val report = protocolEvidence("protocol-readback") {
            structuralReadback("takeoff clearance structural readback", ClearedForTakeoff(aircraft, runway)) {
                cites(ICAO9432.Readback.RequiredItems)
                requires(ClearedForTakeoffReadback(runway))
            }
        }

        report.assertNoFailures()
        assertEquals(EvidenceClaimKind.StructuralProtocolRequirement, report.results.single().claimKind)
        assertTrue(report.format().contains("structural"))
        assertTrue(!report.format().contains("phraseology compliance"))
        assertEquals(ICAO9432.Readback.RequiredItems, report.results.single().sources)
    }

    @Test
    fun `simEvidence expresses typed-source LOWG ordering without monitor vocabulary`() {
        val aircraft = AircraftId("OE-ABC")
        val runway = RunwayId("16C")

        val report = simEvidence("lowg-ordering") {
            observe {
                EvidenceFactAdapters.lowgCircuitTraining(
                    scenarioId = "lowg-ordering",
                    outcomes = listOf(CircuitOutcome.TouchAndGo, CircuitOutcome.FullStop),
                    untilMinutes = 45,
                )
            }

            source("taxi clearance before runway use") {
                cites(ICAO9432.Taxi.HoldingPointLimit)
                sample("runway", runway)
                expect {
                    (instructions<TaxiToHoldingPoint>(aircraft).first() before
                        reports<ReportEvent.Ready>(aircraft).first() before
                        instructions<LineUpAndWait>(aircraft).first()).toOutcome()
                }
            }
        }

        report.assertNoFailures()
        assertEquals(EvidenceClaimKind.SimObservedSourceBehaviour, report.results.single().claimKind)
        assertEquals(ICAO9432.Taxi.HoldingPointLimit, report.results.single().sources)
        assertTrue(report.results.single().activationFactIds.isNotEmpty())
        assertTrue(!report.format().contains("monitor", ignoreCase = true))
    }

    @Test
    fun `source case without activation fails loudly unless typed gap or vacuous`() {
        val report = simEvidence("activation-required") {
            observe { EvidenceFactSet(scenarioId = "activation-required", facts = emptyList(), diagnostic = "empty") }

            source("bad source case") {
                cites(ICAO9432.Taxi.HoldingPointLimit)
                expect { pass("not enough") }
            }
        }

        val outcome = report.results.single().outcome
        assertTrue(outcome is EvidenceAuditOutcome.Fail)
        assertTrue((outcome as EvidenceAuditOutcome.Fail).reason.contains("activate"))
    }

    @Test
    fun `simEvidence without observe fails loudly`() {
        val failure = assertFailsWith<IllegalStateException> {
            simEvidence("missing-observe") {
                invariant("placeholder") {
                    expect { pass("not reached") }
                }
            }
        }

        assertTrue(failure.message.orEmpty().contains("observe"))
    }

    @Test
    fun `renderedPhraseology selector passes only matching takeoff runway tokens`() {
        val aircraft = AircraftId("OE-ABC")
        val facts = EvidenceFactAdapters.fromProjectedPayloads(
            scenarioId = "rendered-selector-takeoff",
            payloads = listOf(
                renderedPhraseologyPayload(
                    aircraft = aircraft,
                    template = RenderedPhraseologyTemplate.TakeoffClearance,
                    tokens = listOf(
                        PhraseologyToken.AircraftCallsign(aircraft),
                        PhraseologyToken.Runway,
                        PhraseologyToken.RunwayDesignator(RunwayId("16C")),
                        PhraseologyToken.Cleared,
                        PhraseologyToken.For,
                        PhraseologyToken.TakeOff,
                    ),
                    text = "OE-ABC RUNWAY 16C CLEARED FOR TAKE-OFF",
                ),
            ),
        )

        val report = simEvidence("rendered-selector-takeoff") {
            observe { facts }
            invariant("matching runway") {
                expect { renderedPhraseology(aircraft).takeoffClearance(RunwayId("16C")) }
            }
            invariant("wrong runway") {
                expect { renderedPhraseology(aircraft).takeoffClearance(RunwayId("34C")) }
            }
        }

        assertTrue(report.results[0].outcome is EvidenceAuditOutcome.Pass)
        assertTrue(report.results[0].activationFactIds.isNotEmpty())
        assertTrue(report.results[1].outcome is EvidenceAuditOutcome.Fail)
        assertTrue(report.results[1].activationFactIds.isNotEmpty())
    }

    @Test
    fun `renderedPhraseology selector does not satisfy touch-and-go from takeoff phraseology`() {
        val aircraft = AircraftId("OE-ABC")
        val facts = EvidenceFactAdapters.fromProjectedPayloads(
            scenarioId = "rendered-selector-template-mismatch",
            payloads = listOf(
                renderedPhraseologyPayload(
                    aircraft = aircraft,
                    template = RenderedPhraseologyTemplate.TakeoffClearance,
                    tokens = listOf(
                        PhraseologyToken.AircraftCallsign(aircraft),
                        PhraseologyToken.Runway,
                        PhraseologyToken.RunwayDesignator(RunwayId("16C")),
                        PhraseologyToken.Cleared,
                        PhraseologyToken.For,
                        PhraseologyToken.TakeOff,
                    ),
                    text = "OE-ABC RUNWAY 16C CLEARED FOR TAKE-OFF",
                ),
            ),
        )

        val report = simEvidence("rendered-selector-template-mismatch") {
            observe { facts }
            source("touch and go missing") {
                cites(ICAO9432.FinalApproachLanding.ClearedTouchAndGoPhrase)
                expect { renderedPhraseology(aircraft).touchAndGoClearance() }
            }
        }

        assertTrue(report.results.single().outcome is EvidenceAuditOutcome.Fail)
    }

    @Test
    fun `operationalPolicy selector requires explicit configured branch and scope`() {
        val scope = OperationalPolicyScope.AerodromeRunway(
            aerodrome = AerodromeId("LOWG"),
            runway = RunwayId("16C"),
        )
        val facts = EvidenceFactAdapters.fromProjectedPayloads(
            scenarioId = "policy-selector",
            payloads = listOf(
                EvidenceFactPayload.ConfiguredPolicy(
                    ConfiguredOperationalPolicy(
                        scope = scope,
                        branch = TaxiClearanceLimitPolicy.DeparturesNormallyToRunwayHoldingPoint,
                    ),
                ),
            ),
        )

        val report = simEvidence("policy-selector") {
            observe { facts }
            source("configured taxi branch") {
                cites(ICAO9432.Taxi.HoldingPointLimit)
                expect {
                    operationalPolicy().configured(
                        branch = TaxiClearanceLimitPolicy.DeparturesNormallyToRunwayHoldingPoint,
                        scope = scope,
                    )
                }
            }
            source("wrong taxi branch") {
                cites(ICAO9432.Taxi.HoldingPointLimit)
                expect {
                    operationalPolicy().configured(
                        branch = TaxiClearanceLimitPolicy.AlternateAerodromePositionAllowed,
                        scope = scope,
                    )
                }
            }
        }

        assertTrue(report.results[0].outcome is EvidenceAuditOutcome.Pass)
        assertTrue(report.results[0].activationFactIds.isNotEmpty())
        assertTrue(report.results[1].outcome is EvidenceAuditOutcome.Fail)
        assertTrue(report.results[1].activationFactIds.isNotEmpty())
    }

    @Test
    fun `operationalPolicy selector fails when only an enum exists and no fact is observed`() {
        val scope = OperationalPolicyScope.AerodromeServiceShape(
            aerodrome = AerodromeId("LOWG"),
            roles = setOf(RoleName.GROUND, RoleName.TOWER),
        )
        val report = simEvidence("policy-selector-missing") {
            observe {
                EvidenceFactSet(
                    scenarioId = "policy-selector-missing",
                    facts = emptyList(),
                    diagnostic = "no configured policies",
                )
            }
            source("missing configured policy") {
                cites(ICAO9432.TakeoffProcedures.TowerTransferAtHoldingPosition)
                expect {
                    operationalPolicy().configured(
                        branch = TowerTransferPolicy.SeparateGroundTowerTransferAtHoldingPoint,
                        scope = scope,
                    )
                }
            }
        }

        assertTrue(report.results.single().outcome is EvidenceAuditOutcome.Fail)
    }

    private fun renderedPhraseologyPayload(
        aircraft: AircraftId,
        template: RenderedPhraseologyTemplate,
        tokens: List<PhraseologyToken>,
        text: String,
    ): EvidenceFactPayload.RenderedPhraseology =
        EvidenceFactPayload.RenderedPhraseology(
            controllerId = ControllerId("LOWG_TWR"),
            aircraftId = aircraft,
            transmissionRef = TransmissionId(1),
            template = template,
            obligationKinds = setOf(
                PhraseologyObligationKind.OrderedPhrase,
                PhraseologyObligationKind.SemanticSlot,
                PhraseologyObligationKind.ForbiddenMeaning,
            ),
            tokens = tokens,
            text = RenderedPhraseText(text),
        )
}
