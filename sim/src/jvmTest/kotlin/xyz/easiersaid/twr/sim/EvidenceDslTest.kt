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
import xyz.easiersaid.twr.protocol.Frequency
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
    fun `takeoff word-use selector fails on missing or violating supported rendered templates`() {
        val aircraft = AircraftId("OE-ABC")
        val validPayloads = supportedControllerPhraseologyPayloads(aircraft)

        val emptyReport = renderedTakeOffWordUseReport(
            scenarioId = "takeoff-word-empty",
            aircraft = aircraft,
            payloads = emptyList(),
        )
        val missingTemplateReport = renderedTakeOffWordUseReport(
            scenarioId = "takeoff-word-missing-template",
            aircraft = aircraft,
            payloads = validPayloads.dropLast(1),
        )
        val violatingPayload = renderedPhraseologyPayload(
            aircraft = aircraft,
            template = RenderedPhraseologyTemplate.ContactFrequencyInstruction,
            tokens = listOf(
                PhraseologyToken.AircraftCallsign(aircraft),
                PhraseologyToken.Contact,
                PhraseologyToken.TakeOff,
            ),
            text = "OE-ABC CONTACT TAKE-OFF",
        )
        val violatingReport = renderedTakeOffWordUseReport(
            scenarioId = "takeoff-word-violating-template",
            aircraft = aircraft,
            payloads = listOf(violatingPayload),
        )
        val mixedReport = renderedTakeOffWordUseReport(
            scenarioId = "takeoff-word-mixed",
            aircraft = aircraft,
            payloads = validPayloads + violatingPayload,
        )
        val validReport = renderedTakeOffWordUseReport(
            scenarioId = "takeoff-word-valid",
            aircraft = aircraft,
            payloads = validPayloads,
        )

        assertTrue(emptyReport.results.single().outcome is EvidenceAuditOutcome.Fail)
        assertTrue(missingTemplateReport.results.single().outcome is EvidenceAuditOutcome.Fail)
        assertTrue(violatingReport.results.single().outcome is EvidenceAuditOutcome.Fail)
        assertTrue(mixedReport.results.single().outcome is EvidenceAuditOutcome.Fail)
        assertTrue(validReport.results.single().outcome is EvidenceAuditOutcome.Pass)
        assertTrue(validReport.results.single().activationFactIds.isNotEmpty())
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

    @Test
    fun `essentialAerodromeInformation selector requires activated category facts`() {
        val facts = EvidenceFactAdapters.fromProjectedPayloads(
            scenarioId = "essential-category-selector",
            payloads = listOf(
                essentialInformationPayload(
                    category = EssentialAerodromeInformationCategory.WaterOnMovementArea,
                    domain = EssentialAerodromeInformationDomain.MovementArea,
                    facets = setOf(EssentialAerodromeInformationFacet.Runway),
                    detail = "water on RWY 16C",
                ),
            ),
        )

        val report = simEvidence("essential-category-selector") {
            observe { facts }
            source("water category") {
                cites(ICAO9432.AerodromeInformation.WaterOnMovementArea)
                expect {
                    essentialAerodromeInformation()
                        .includes(EssentialAerodromeInformationCategory.WaterOnMovementArea)
                }
            }
            source("lighting category missing") {
                cites(ICAO9432.AerodromeInformation.LightingSystemFailure)
                expect {
                    essentialAerodromeInformation()
                        .includes(EssentialAerodromeInformationCategory.LightingSystemFailureOrIrregularOperation)
                }
            }
        }

        assertTrue(report.results[0].outcome is EvidenceAuditOutcome.Pass)
        assertTrue(report.results[0].activationFactIds.isNotEmpty())
        assertTrue(report.results[1].outcome is EvidenceAuditOutcome.Fail)
        assertTrue(report.results[1].activationFactIds.isNotEmpty())
    }

    private fun essentialInformationPayload(
        category: EssentialAerodromeInformationCategory,
        domain: EssentialAerodromeInformationDomain,
        facets: Set<EssentialAerodromeInformationFacet>,
        detail: String,
    ): EvidenceFactPayload.EssentialAerodromeInformation =
        EvidenceFactPayload.EssentialAerodromeInformation(
            domain = domain,
            category = category,
            facets = facets,
            safetyRelevance = EssentialAerodromeInformationSafetyRelevance.NecessaryForSafeOperation,
            detail = AerodromeInformationDetail(detail),
        )

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

    private fun renderedTakeOffWordUseReport(
        scenarioId: String,
        aircraft: AircraftId,
        payloads: List<EvidenceFactPayload.RenderedPhraseology>,
    ): EvidenceAuditReport =
        simEvidence(scenarioId) {
            observe {
                EvidenceFactAdapters.fromProjectedPayloads(
                    scenarioId = scenarioId,
                    payloads = payloads,
                )
            }
            invariant("takeoff word use") {
                expect { renderedPhraseology(aircraft).takeOffWordOnlyInTakeoffClearanceAcrossSupportedTemplates() }
            }
        }

    private fun supportedControllerPhraseologyPayloads(
        aircraft: AircraftId,
    ): List<EvidenceFactPayload.RenderedPhraseology> =
        listOf(
            renderedPhraseologyPayload(
                aircraft = aircraft,
                template = RenderedPhraseologyTemplate.ContactFrequencyInstruction,
                tokens = listOf(
                    PhraseologyToken.AircraftCallsign(aircraft),
                    PhraseologyToken.Contact,
                    PhraseologyToken.UnitName("TOWER"),
                    PhraseologyToken.FrequencyValue(Frequency.unsafe("118.200")),
                ),
                text = "OE-ABC CONTACT TOWER 118.200",
            ),
            renderedPhraseologyPayload(
                aircraft = aircraft,
                template = RenderedPhraseologyTemplate.LineUpAndWaitInstruction,
                tokens = listOf(
                    PhraseologyToken.AircraftCallsign(aircraft),
                    PhraseologyToken.Runway,
                    PhraseologyToken.RunwayDesignator(RunwayId("16C")),
                    PhraseologyToken.Line,
                    PhraseologyToken.Up,
                    PhraseologyToken.And,
                    PhraseologyToken.Wait,
                ),
                text = "OE-ABC RUNWAY 16C LINE UP AND WAIT",
            ),
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
            renderedPhraseologyPayload(
                aircraft = aircraft,
                template = RenderedPhraseologyTemplate.TouchAndGoClearance,
                tokens = listOf(
                    PhraseologyToken.AircraftCallsign(aircraft),
                    PhraseologyToken.Cleared,
                    PhraseologyToken.Touch,
                    PhraseologyToken.And,
                    PhraseologyToken.Go,
                ),
                text = "OE-ABC CLEARED TOUCH AND GO",
            ),
            renderedPhraseologyPayload(
                aircraft = aircraft,
                template = RenderedPhraseologyTemplate.StopImmediatelyInstruction,
                tokens = listOf(
                    PhraseologyToken.AircraftCallsign(aircraft),
                    PhraseologyToken.Stop,
                    PhraseologyToken.Immediately,
                    PhraseologyToken.AircraftCallsign(aircraft),
                    PhraseologyToken.Stop,
                    PhraseologyToken.Immediately,
                ),
                text = "OE-ABC STOP IMMEDIATELY OE-ABC STOP IMMEDIATELY",
            ),
        )
}
