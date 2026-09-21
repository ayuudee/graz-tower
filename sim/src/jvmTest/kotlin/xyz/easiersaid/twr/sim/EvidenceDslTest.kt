package xyz.easiersaid.twr.sim

import arrow.core.NonEmptyList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.DecisionTrace
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.controller.certify.CertificationEvidence
import xyz.easiersaid.twr.controller.observe.OutstandingCoordination
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.AirTaxiTo
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedForTakeoffReadback
import xyz.easiersaid.twr.protocol.ConditionalElement
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.FrequencyReadback
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.LineUpReadback
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.Readback
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.SimpleElement
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.protocol.TaxiRouteReadback
import xyz.easiersaid.twr.protocol.Urgency
import xyz.easiersaid.twr.protocol.VacateReadback
import xyz.easiersaid.twr.protocol.WhenAbleCondition
import xyz.easiersaid.twr.sim.testing.TransmissionRecord

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
    fun `after-landing rendered phraseology selectors require matching ordered taxi route`() {
        val aircraft = AircraftId("OE-ABC")
        val stand = PointId("STAND-27")
        val via = listOf(PointId("ALPHA"))
        val facts = EvidenceFactAdapters.fromProjectedPayloads(
            scenarioId = "after-landing-rendered-selectors",
            payloads = listOf(
                renderedPilotReportPhraseologyPayload(
                    aircraft = aircraft,
                    template = RenderedPhraseologyTemplate.RunwayVacatedReport,
                    tokens = listOf(PhraseologyToken.Runway, PhraseologyToken.Vacated),
                    text = "RUNWAY VACATED",
                ),
                renderedPhraseologyPayload(
                    aircraft = aircraft,
                    template = RenderedPhraseologyTemplate.TaxiToStandInstruction,
                    obligationKinds = setOf(
                        PhraseologyObligationKind.OrderedPhrase,
                        PhraseologyObligationKind.SemanticSlot,
                        PhraseologyObligationKind.Readback,
                    ),
                    tokens = listOf(
                        PhraseologyToken.AircraftCallsign(aircraft),
                        PhraseologyToken.Taxi,
                        PhraseologyToken.To,
                        PhraseologyToken.PointName(stand),
                        PhraseologyToken.Via,
                        PhraseologyToken.PointName(via.single()),
                    ),
                    text = "OE-ABC TAXI TO STAND-27 VIA ALPHA",
                ),
                renderedPilotReadbackPhraseologyPayload(
                    aircraft = aircraft,
                    template = RenderedPhraseologyTemplate.TaxiRouteReadback,
                    tokens = listOf(
                        PhraseologyToken.PointName(stand),
                        PhraseologyToken.Via,
                        PhraseologyToken.PointName(via.single()),
                        PhraseologyToken.AircraftCallsign(aircraft),
                    ),
                    text = "STAND-27 VIA ALPHA OE-ABC",
                ),
            ),
        )

        val report = simEvidence("after-landing-rendered-selectors") {
            observe { facts }
            invariant("runway vacated") {
                expect { renderedPilotReportPhraseology(aircraft).runwayVacatedReport() }
            }
            invariant("taxi to stand") {
                expect { renderedPhraseology(aircraft).taxiToStand(stand, via) }
            }
            invariant("taxi route readback") {
                expect { renderedPilotReadbackPhraseology(aircraft).taxiRouteReadback(stand, via) }
            }
            invariant("correlated exchange") {
                expect { afterLandingPhraseology(aircraft).runwayVacatedTaxiToStandExchange() }
            }
        }

        report.assertNoFailures()

        val wrongRouteFacts = EvidenceFactAdapters.fromProjectedPayloads(
            scenarioId = "after-landing-rendered-wrong-route",
            payloads = listOf(
                renderedPilotReportPhraseologyPayload(
                    aircraft = aircraft,
                    template = RenderedPhraseologyTemplate.RunwayVacatedReport,
                    tokens = listOf(PhraseologyToken.Runway, PhraseologyToken.Vacated),
                    text = "RUNWAY VACATED",
                ),
                renderedPhraseologyPayload(
                    aircraft = aircraft,
                    template = RenderedPhraseologyTemplate.TaxiToStandInstruction,
                    obligationKinds = setOf(
                        PhraseologyObligationKind.OrderedPhrase,
                        PhraseologyObligationKind.SemanticSlot,
                        PhraseologyObligationKind.Readback,
                    ),
                    tokens = listOf(
                        PhraseologyToken.AircraftCallsign(aircraft),
                        PhraseologyToken.Taxi,
                        PhraseologyToken.To,
                        PhraseologyToken.PointName(stand),
                        PhraseologyToken.Via,
                        PhraseologyToken.PointName(via.single()),
                    ),
                    text = "OE-ABC TAXI TO STAND-27 VIA ALPHA",
                ),
                renderedPilotReadbackPhraseologyPayload(
                    aircraft = aircraft,
                    template = RenderedPhraseologyTemplate.TaxiRouteReadback,
                    tokens = listOf(
                        PhraseologyToken.PointName(stand),
                        PhraseologyToken.Via,
                        PhraseologyToken.PointName(PointId("BRAVO")),
                        PhraseologyToken.AircraftCallsign(aircraft),
                    ),
                    text = "STAND-27 VIA BRAVO OE-ABC",
                ),
            ),
        )
        val wrongRouteReport = simEvidence("after-landing-rendered-wrong-route") {
            observe { wrongRouteFacts }
            invariant("wrong route does not correlate") {
                expect { afterLandingPhraseology(aircraft).runwayVacatedTaxiToStandExchange() }
            }
        }

        assertTrue(wrongRouteReport.results.single().outcome is EvidenceAuditOutcome.Fail)

        val malformedRouteReport = simEvidence("after-landing-rendered-malformed-route") {
            observe {
                EvidenceFactAdapters.fromProjectedPayloads(
                    scenarioId = "after-landing-rendered-malformed-route",
                    payloads = listOf(
                        renderedPilotReportPhraseologyPayload(
                            aircraft = aircraft,
                            template = RenderedPhraseologyTemplate.RunwayVacatedReport,
                            tokens = listOf(PhraseologyToken.Runway, PhraseologyToken.Vacated),
                            text = "RUNWAY VACATED",
                        ),
                        renderedPhraseologyPayload(
                            aircraft = aircraft,
                            template = RenderedPhraseologyTemplate.TaxiToStandInstruction,
                            obligationKinds = setOf(
                                PhraseologyObligationKind.OrderedPhrase,
                                PhraseologyObligationKind.SemanticSlot,
                                PhraseologyObligationKind.Readback,
                            ),
                            tokens = listOf(
                                PhraseologyToken.AircraftCallsign(aircraft),
                                PhraseologyToken.Taxi,
                                PhraseologyToken.To,
                                PhraseologyToken.Via,
                                PhraseologyToken.PointName(via.single()),
                            ),
                            text = "OE-ABC TAXI TO VIA ALPHA",
                        ),
                        renderedPilotReadbackPhraseologyPayload(
                            aircraft = aircraft,
                            template = RenderedPhraseologyTemplate.TaxiRouteReadback,
                            tokens = listOf(
                                PhraseologyToken.Via,
                                PhraseologyToken.PointName(via.single()),
                                PhraseologyToken.AircraftCallsign(aircraft),
                            ),
                            text = "VIA ALPHA OE-ABC",
                        ),
                    ),
                )
            }
            invariant("malformed route without destination does not correlate") {
                expect { afterLandingPhraseology(aircraft).runwayVacatedTaxiToStandExchange() }
            }
        }

        assertTrue(malformedRouteReport.results.single().outcome is EvidenceAuditOutcome.Fail)
        assertTrue(malformedRouteReport.results.single().activationFactIds.isNotEmpty())

        val readbackBeforeTaxiReport = simEvidence("after-landing-rendered-readback-before-taxi") {
            observe {
                EvidenceFactAdapters.fromProjectedPayloads(
                    scenarioId = "after-landing-rendered-readback-before-taxi",
                    payloads = listOf(
                        renderedPilotReportPhraseologyPayload(
                            aircraft = aircraft,
                            template = RenderedPhraseologyTemplate.RunwayVacatedReport,
                            tokens = listOf(PhraseologyToken.Runway, PhraseologyToken.Vacated),
                            text = "RUNWAY VACATED",
                        ),
                        renderedPilotReadbackPhraseologyPayload(
                            aircraft = aircraft,
                            template = RenderedPhraseologyTemplate.TaxiRouteReadback,
                            tokens = listOf(
                                PhraseologyToken.PointName(stand),
                                PhraseologyToken.Via,
                                PhraseologyToken.PointName(via.single()),
                                PhraseologyToken.AircraftCallsign(aircraft),
                            ),
                            text = "STAND-27 VIA ALPHA OE-ABC",
                        ),
                        renderedPhraseologyPayload(
                            aircraft = aircraft,
                            template = RenderedPhraseologyTemplate.TaxiToStandInstruction,
                            obligationKinds = setOf(
                                PhraseologyObligationKind.OrderedPhrase,
                                PhraseologyObligationKind.SemanticSlot,
                                PhraseologyObligationKind.Readback,
                            ),
                            tokens = listOf(
                                PhraseologyToken.AircraftCallsign(aircraft),
                                PhraseologyToken.Taxi,
                                PhraseologyToken.To,
                                PhraseologyToken.PointName(stand),
                                PhraseologyToken.Via,
                                PhraseologyToken.PointName(via.single()),
                            ),
                            text = "OE-ABC TAXI TO STAND-27 VIA ALPHA",
                        ),
                    ),
                )
            }
            invariant("readback before taxi does not correlate") {
                expect { afterLandingPhraseology(aircraft).runwayVacatedTaxiToStandExchange() }
            }
        }

        assertTrue(readbackBeforeTaxiReport.results.single().outcome is EvidenceAuditOutcome.Fail)

        val taxiBeforeVacatedReport = simEvidence("after-landing-rendered-taxi-before-vacated") {
            observe {
                EvidenceFactAdapters.fromProjectedPayloads(
                    scenarioId = "after-landing-rendered-taxi-before-vacated",
                    payloads = listOf(
                        renderedPhraseologyPayload(
                            aircraft = aircraft,
                            template = RenderedPhraseologyTemplate.TaxiToStandInstruction,
                            obligationKinds = setOf(
                                PhraseologyObligationKind.OrderedPhrase,
                                PhraseologyObligationKind.SemanticSlot,
                                PhraseologyObligationKind.Readback,
                            ),
                            tokens = listOf(
                                PhraseologyToken.AircraftCallsign(aircraft),
                                PhraseologyToken.Taxi,
                                PhraseologyToken.To,
                                PhraseologyToken.PointName(stand),
                                PhraseologyToken.Via,
                                PhraseologyToken.PointName(via.single()),
                            ),
                            text = "OE-ABC TAXI TO STAND-27 VIA ALPHA",
                        ),
                        renderedPilotReportPhraseologyPayload(
                            aircraft = aircraft,
                            template = RenderedPhraseologyTemplate.RunwayVacatedReport,
                            tokens = listOf(PhraseologyToken.Runway, PhraseologyToken.Vacated),
                            text = "RUNWAY VACATED",
                        ),
                        renderedPilotReadbackPhraseologyPayload(
                            aircraft = aircraft,
                            template = RenderedPhraseologyTemplate.TaxiRouteReadback,
                            tokens = listOf(
                                PhraseologyToken.PointName(stand),
                                PhraseologyToken.Via,
                                PhraseologyToken.PointName(via.single()),
                                PhraseologyToken.AircraftCallsign(aircraft),
                            ),
                            text = "STAND-27 VIA ALPHA OE-ABC",
                        ),
                    ),
                )
            }
            invariant("taxi before runway-vacated does not correlate") {
                expect { afterLandingPhraseology(aircraft).runwayVacatedTaxiToStandExchange() }
            }
        }

        assertTrue(taxiBeforeVacatedReport.results.single().outcome is EvidenceAuditOutcome.Fail)
    }

    @Test
    fun `after-landing first-right selector requires adjacent ordered rendered exchange`() {
        val aircraft = AircraftId("FASTAIR 345")
        val frequency = Frequency.unsafe("118.350")

        val passReport = firstRightExchangeReport(
            scenarioId = "first-right-exchange-pass",
            aircraft = aircraft,
            frequency = frequency,
            payloads = firstRightExchangePayloads(aircraft, frequency),
        )
        assertTrue(passReport.results.single().outcome is EvidenceAuditOutcome.Pass)
        assertTrue(passReport.results.single().activationFactIds.isNotEmpty())

        val wrongOrderReport = firstRightExchangeReport(
            scenarioId = "first-right-exchange-wrong-order",
            aircraft = aircraft,
            frequency = frequency,
            payloads = listOf(
                contactGroundPayload(aircraft, frequency),
                firstRightInstructionPayload(aircraft),
                firstRightReadbackPayload(aircraft, frequency),
            ),
        )
        assertTrue(wrongOrderReport.results.single().outcome is EvidenceAuditOutcome.Fail)

        val interveningReadbackReport = firstRightExchangeReport(
            scenarioId = "first-right-exchange-intervening-readback",
            aircraft = aircraft,
            frequency = frequency,
            payloads = listOf(
                firstRightInstructionPayload(aircraft),
                contactGroundPayload(aircraft, frequency),
                renderedPilotReadbackPhraseologyPayload(
                    aircraft = aircraft,
                    template = RenderedPhraseologyTemplate.FrequencyReadback,
                    tokens = listOf(
                        PhraseologyToken.FrequencyValue(frequency),
                        PhraseologyToken.AircraftCallsign(aircraft),
                    ),
                    text = "118.350 FASTAIR 345",
                ),
                firstRightReadbackPayload(aircraft, frequency),
            ),
        )
        assertTrue(interveningReadbackReport.results.single().outcome is EvidenceAuditOutcome.Fail)

        val malformedReport = firstRightExchangeReport(
            scenarioId = "first-right-exchange-malformed",
            aircraft = aircraft,
            frequency = frequency,
            payloads = listOf(
                renderedPhraseologyPayload(
                    aircraft = aircraft,
                    template = RenderedPhraseologyTemplate.AfterLandingVacateViaInstruction,
                    obligationKinds = setOf(PhraseologyObligationKind.OrderedPhrase),
                    tokens = listOf(
                        PhraseologyToken.AircraftCallsign(aircraft),
                        PhraseologyToken.Take,
                        PhraseologyToken.Right,
                        PhraseologyToken.When,
                        PhraseologyToken.Vacated,
                    ),
                    text = "FASTAIR 345 TAKE RIGHT WHEN VACATED",
                ),
                contactGroundPayload(aircraft, frequency),
                firstRightReadbackPayload(aircraft, frequency),
            ),
        )
        val malformedOutcome = malformedReport.results.single().outcome
        assertTrue(malformedOutcome is EvidenceAuditOutcome.Fail)
        assertTrue(malformedOutcome.reason.contains("Malformed"))

        val wrongTextReport = firstRightExchangeReport(
            scenarioId = "first-right-exchange-wrong-text",
            aircraft = aircraft,
            frequency = frequency,
            payloads = listOf(
                firstRightInstructionPayload(aircraft).copy(
                    text = RenderedPhraseText("FASTAIR 345 TAKE SECOND RIGHT WHEN VACATED"),
                ),
                contactGroundPayload(aircraft, frequency),
                firstRightReadbackPayload(aircraft, frequency),
            ),
        )
        val wrongTextOutcome = wrongTextReport.results.single().outcome
        assertTrue(wrongTextOutcome is EvidenceAuditOutcome.Fail)
        assertTrue(wrongTextOutcome.reason.contains("Malformed"))

        val missingObligationReport = firstRightExchangeReport(
            scenarioId = "first-right-exchange-missing-obligation",
            aircraft = aircraft,
            frequency = frequency,
            payloads = listOf(
                firstRightInstructionPayload(aircraft).copy(
                    obligationKinds = setOf(
                        PhraseologyObligationKind.OrderedPhrase,
                        PhraseologyObligationKind.SemanticSlot,
                    ),
                ),
                contactGroundPayload(aircraft, frequency),
                firstRightReadbackPayload(aircraft, frequency),
            ),
        )
        val missingObligationOutcome = missingObligationReport.results.single().outcome
        assertTrue(missingObligationOutcome is EvidenceAuditOutcome.Fail)
        assertTrue(missingObligationOutcome.reason.contains("Malformed"))

        val wrongTemplateReport = firstRightExchangeReport(
            scenarioId = "first-right-exchange-wrong-template",
            aircraft = aircraft,
            frequency = frequency,
            payloads = listOf(
                firstRightInstructionPayload(aircraft).copy(
                    template = RenderedPhraseologyTemplate.TaxiToStandInstruction,
                ),
                contactGroundPayload(aircraft, frequency),
                firstRightReadbackPayload(aircraft, frequency),
            ),
        )
        assertTrue(wrongTemplateReport.results.single().outcome is EvidenceAuditOutcome.Fail)
        assertTrue(wrongTemplateReport.results.single().activationFactIds.isNotEmpty())

        val emptyReport = firstRightExchangeReport(
            scenarioId = "first-right-exchange-empty",
            aircraft = aircraft,
            frequency = frequency,
            payloads = emptyList(),
        )
        assertTrue(emptyReport.results.single().outcome is EvidenceAuditOutcome.Fail)
        assertTrue(emptyReport.results.single().activationFactIds.isEmpty())

        val wrongFrequencyReport = firstRightExchangeReport(
            scenarioId = "first-right-exchange-wrong-frequency",
            aircraft = aircraft,
            frequency = frequency,
            payloads = firstRightExchangePayloads(aircraft, Frequency.unsafe("118.400")),
        )
        assertTrue(wrongFrequencyReport.results.single().outcome is EvidenceAuditOutcome.Fail)

        val otherAircraftReport = firstRightExchangeReport(
            scenarioId = "first-right-exchange-other-aircraft",
            aircraft = aircraft,
            frequency = frequency,
            payloads = firstRightExchangePayloads(AircraftId("OTHER 123"), frequency),
        )
        assertTrue(otherAircraftReport.results.single().outcome is EvidenceAuditOutcome.Fail)
        assertTrue(otherAircraftReport.results.single().activationFactIds.isEmpty())
    }

    @Test
    fun `first-right readback renderer only supports exact vacate plus frequency shape`() {
        val aircraft = AircraftId("FASTAIR 345")
        val frequency = Frequency.unsafe("118.350")
        val firstRightVacate = SimpleElement(VacateReadback(via = PointId("FIRST_RIGHT")))
        val frequencyReadback = SimpleElement(FrequencyReadback(frequency))
        val unsupportedReadbacks = listOf(
            Readback(listOf(firstRightVacate)),
            Readback(listOf(firstRightVacate, frequencyReadback, SimpleElement(FrequencyReadback(frequency)))),
            Readback(listOf(SimpleElement(VacateReadback(via = PointId("BRAVO"))), frequencyReadback)),
            Readback(listOf(SimpleElement(LineUpReadback(RunwayId("16C"))), frequencyReadback)),
            Readback(
                listOf(
                    ConditionalElement(
                        condition = WhenAbleCondition,
                        action = VacateReadback(via = PointId("FIRST_RIGHT")),
                    ),
                    frequencyReadback,
                ),
            ),
        )

        val supported = renderPilotReadbackPhraseology(
            aircraftId = aircraft,
            readback = Readback(listOf(firstRightVacate, frequencyReadback)),
        )
        assertTrue(supported is PilotReadbackPhraseologyRenderResult.Rendered)

        unsupportedReadbacks.forEach { readback ->
            val result = renderPilotReadbackPhraseology(aircraftId = aircraft, readback = readback)
            assertTrue(result is PilotReadbackPhraseologyRenderResult.UnsupportedReadback)
        }
    }

    @Test
    fun `air-taxi renderer supports only helicopter stand branch and preserves ordinary route readbacks`() {
        val aircraft = AircraftId("G-HELI")

        val supportedController = renderControllerPhraseology(
            airTaxiControllerOutput(AirTaxiTo(aircraft, HelicopterStandPoint)),
        )
        assertTrue(supportedController is ControllerPhraseologyRenderResult.Rendered)
        assertEquals(RenderedPhraseologyTemplate.AirTaxiToInstruction, supportedController.phraseology.template)
        assertEquals("G-HELI AIR-TAXI TO HELICOPTER STAND", supportedController.phraseology.text.value)

        val supportedReadback = renderPilotReadbackPhraseology(
            aircraftId = aircraft,
            readback = Readback(listOf(SimpleElement(TaxiRouteReadback(HelicopterStandPoint)))),
        )
        assertTrue(supportedReadback is PilotReadbackPhraseologyRenderResult.Rendered)
        assertEquals(RenderedPhraseologyTemplate.AirTaxiRouteReadback, supportedReadback.phraseology.template)
        assertEquals("AIR-TAXI TO HELICOPTER STAND G-HELI", supportedReadback.phraseology.text.value)

        val ordinaryReadback = renderPilotReadbackPhraseology(
            aircraftId = aircraft,
            readback = Readback(listOf(SimpleElement(TaxiRouteReadback(PointId("STAND-27"))))),
        )
        assertTrue(ordinaryReadback is PilotReadbackPhraseologyRenderResult.Rendered)
        assertEquals(RenderedPhraseologyTemplate.TaxiRouteReadback, ordinaryReadback.phraseology.template)

        val unsupportedControllerVariants = listOf(
            AirTaxiTo(aircraft, PointId("STAND-27")),
            AirTaxiTo(aircraft, HelicopterStandPoint, via = listOf(PointId("ALPHA"))),
        )
        unsupportedControllerVariants.forEach { instruction ->
            val result = renderControllerPhraseology(
                airTaxiControllerOutput(instruction),
            )
            assertTrue(result is ControllerPhraseologyRenderResult.UnsupportedInstruction)
        }
    }

    @Test
    fun `air-taxi adapter keeps unsupported controller variants explicit`() {
        val aircraft = AircraftId("G-HELI")
        val unsupportedDestination = AirTaxiTo(aircraft, PointId("STAND-27"))
        val unsupportedVia = AirTaxiTo(aircraft, HelicopterStandPoint, via = listOf(PointId("ALPHA")))

        val facts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "air-taxi-unsupported-adapter",
            records = listOf(
                airTaxiControllerRecord(TransmissionId(400), unsupportedDestination),
                airTaxiControllerRecord(TransmissionId(401), unsupportedVia),
            ),
        )
        val unsupported = facts.orderedFacts().mapNotNull { fact ->
            fact.payload as? EvidenceFactPayload.UnsupportedRenderedPhraseology
        }

        assertEquals(2, unsupported.size)
        assertEquals(setOf(unsupportedDestination, unsupportedVia), unsupported.map { payload -> payload.instruction }.toSet())
        assertTrue(
            facts.orderedFacts().none { fact ->
                val payload = fact.payload as? EvidenceFactPayload.RenderedPhraseology ?: return@none false
                payload.template == RenderedPhraseologyTemplate.AirTaxiToInstruction
            },
        )
    }

    @Test
    fun `helicopter air-taxi selector requires exact adjacent instruction and readback`() {
        val aircraft = AircraftId("G-HELI")

        val passReport = airTaxiExchangeReport(
            scenarioId = "air-taxi-exchange-pass",
            aircraft = aircraft,
            payloads = airTaxiExchangePayloads(aircraft),
        )
        assertTrue(passReport.results.single().outcome is EvidenceAuditOutcome.Pass)
        assertTrue(passReport.results.single().activationFactIds.isNotEmpty())

        val failingCases = listOf(
            "wrong-order" to listOf(airTaxiReadbackPayload(aircraft), airTaxiInstructionPayload(aircraft)),
            "wrong-aircraft" to airTaxiExchangePayloads(AircraftId("OTHER")),
            "ordinary-taxi" to listOf(
                renderedPhraseologyPayload(
                    aircraft = aircraft,
                    template = RenderedPhraseologyTemplate.TaxiToStandInstruction,
                    obligationKinds = readbackInstructionObligationKinds,
                    tokens = listOf(
                        PhraseologyToken.AircraftCallsign(aircraft),
                        PhraseologyToken.Taxi,
                        PhraseologyToken.To,
                        PhraseologyToken.PointName(HelicopterStandPoint),
                    ),
                    text = "G-HELI TAXI TO HELICOPTER STAND",
                ),
                renderedPilotReadbackPhraseologyPayload(
                    aircraft = aircraft,
                    template = RenderedPhraseologyTemplate.TaxiRouteReadback,
                    tokens = listOf(
                        PhraseologyToken.PointName(HelicopterStandPoint),
                        PhraseologyToken.AircraftCallsign(aircraft),
                    ),
                    text = "HELICOPTER STAND G-HELI",
                ),
            ),
            "wrong-controller-text" to listOf(
                airTaxiInstructionPayload(aircraft).copy(text = RenderedPhraseText("G-HELI AIR TAXI TO HELICOPTER STAND")),
                airTaxiReadbackPayload(aircraft),
            ),
            "wrong-readback-text" to listOf(
                airTaxiInstructionPayload(aircraft),
                airTaxiReadbackPayload(aircraft).copy(text = RenderedPhraseText("HELICOPTER STAND G-HELI")),
            ),
            "missing-obligation" to listOf(
                airTaxiInstructionPayload(aircraft).copy(
                    obligationKinds = setOf(PhraseologyObligationKind.OrderedPhrase),
                ),
                airTaxiReadbackPayload(aircraft),
            ),
            "wrong-template" to listOf(
                airTaxiInstructionPayload(aircraft).copy(template = RenderedPhraseologyTemplate.TaxiToStandInstruction),
                airTaxiReadbackPayload(aircraft),
            ),
            "malformed-controller-tokens" to listOf(
                airTaxiInstructionPayload(aircraft).copy(
                    tokens = listOf(
                        PhraseologyToken.AircraftCallsign(aircraft),
                        PhraseologyToken.Taxi,
                        PhraseologyToken.To,
                        PhraseologyToken.PointName(HelicopterStandPoint),
                    ),
                ),
                airTaxiReadbackPayload(aircraft),
            ),
            "malformed-readback-tokens" to listOf(
                airTaxiInstructionPayload(aircraft),
                airTaxiReadbackPayload(aircraft).copy(
                    tokens = listOf(
                        PhraseologyToken.PointName(HelicopterStandPoint),
                        PhraseologyToken.AircraftCallsign(aircraft),
                    ),
                ),
            ),
            "mismatched-readback-destination" to listOf(
                airTaxiInstructionPayload(aircraft),
                renderedPilotReadbackPhraseologyPayload(
                    aircraft = aircraft,
                    template = RenderedPhraseologyTemplate.TaxiRouteReadback,
                    tokens = listOf(
                        PhraseologyToken.PointName(PointId("STAND-27")),
                        PhraseologyToken.AircraftCallsign(aircraft),
                    ),
                    text = "STAND-27 G-HELI",
                ),
            ),
            "intervening-phraseology" to listOf(
                airTaxiInstructionPayload(aircraft),
                renderedPilotReadbackPhraseologyPayload(
                    aircraft = aircraft,
                    template = RenderedPhraseologyTemplate.FrequencyReadback,
                    tokens = listOf(
                        PhraseologyToken.FrequencyValue(Frequency.unsafe("118.350")),
                        PhraseologyToken.AircraftCallsign(aircraft),
                    ),
                    text = "118.350 G-HELI",
                ),
                airTaxiReadbackPayload(aircraft),
            ),
            "standalone-readback" to listOf(airTaxiReadbackPayload(aircraft)),
        )

        failingCases.forEach { (scenario, payloads) ->
            val report = airTaxiExchangeReport(
                scenarioId = "air-taxi-exchange-$scenario",
                aircraft = aircraft,
                payloads = payloads,
            )
            assertTrue(report.results.single().outcome is EvidenceAuditOutcome.Fail, scenario)
        }
    }

    @Test
    fun `renderedPilotReportPhraseology selectors require exact final and long-final tokens`() {
        val aircraft = AircraftId("OE-ABC")
        val facts = EvidenceFactAdapters.fromProjectedPayloads(
            scenarioId = "rendered-pilot-report-selector",
            payloads = listOf(
                renderedPilotReportPhraseologyPayload(
                    aircraft = aircraft,
                    template = RenderedPhraseologyTemplate.FinalReport,
                    tokens = listOf(PhraseologyToken.Final),
                    text = "FINAL",
                ),
                renderedPilotReportPhraseologyPayload(
                    aircraft = aircraft,
                    template = RenderedPhraseologyTemplate.LongFinalReport,
                    tokens = listOf(PhraseologyToken.Long, PhraseologyToken.Final),
                    text = "LONG FINAL",
                ),
            ),
        )
        val wrongTokenFacts = EvidenceFactAdapters.fromProjectedPayloads(
            scenarioId = "rendered-pilot-report-wrong-token",
            payloads = listOf(
                renderedPilotReportPhraseologyPayload(
                    aircraft = aircraft,
                    template = RenderedPhraseologyTemplate.LongFinalReport,
                    tokens = listOf(PhraseologyToken.Final),
                    text = "FINAL",
                ),
            ),
        )

        val report = simEvidence("rendered-pilot-report-selector") {
            observe { facts }
            invariant("final report") {
                expect { renderedPilotReportPhraseology(aircraft).finalReport() }
            }
            invariant("long-final report") {
                expect { renderedPilotReportPhraseology(aircraft).longFinalReport() }
            }
        }
        val wrongTokenReport = simEvidence("rendered-pilot-report-wrong-token") {
            observe { wrongTokenFacts }
            invariant("wrong long-final token") {
                expect { renderedPilotReportPhraseology(aircraft).longFinalReport() }
            }
        }
        val missingReport = simEvidence("rendered-pilot-report-missing") {
            observe {
                EvidenceFactSet(scenarioId = "rendered-pilot-report-missing", facts = emptyList(), diagnostic = "empty")
            }
            invariant("missing final") {
                expect { renderedPilotReportPhraseology(aircraft).finalReport() }
            }
        }

        assertTrue(report.results.all { result -> result.outcome is EvidenceAuditOutcome.Pass })
        assertTrue(report.results.all { result -> result.activationFactIds.isNotEmpty() })
        assertTrue(wrongTokenReport.results.single().outcome is EvidenceAuditOutcome.Fail)
        assertTrue(wrongTokenReport.results.single().activationFactIds.isNotEmpty())
        assertTrue(missingReport.results.single().outcome is EvidenceAuditOutcome.Fail)
    }

    @Test
    fun `rendered vehicle initial-call selector matches exact route and rejects malformed cases`() {
        val vehicle = VehicleId("WORKER-21")
        val callsign = Callsign("WORKER 21")
        val position = PointId("GATE-27")
        val destination = PointId("HOTEL")
        val route = listOf(PointId("KILO"), PointId("ALPHA"))

        val passReport = renderedVehicleInitialCallReport(
            scenarioId = "vehicle-initial-call-pass",
            vehicle = vehicle,
            payload = renderedVehiclePhraseologyPayload(
                vehicle = vehicle,
                template = RenderedPhraseologyTemplate.VehicleInitialCall,
                tokens = vehicleInitialCallTokens(callsign, position, destination, route),
                text = "WORKER 21 GATE-27 TO HOTEL VIA KILO ALPHA",
            ),
            callsign = callsign,
            position = position,
            destination = destination,
            route = route,
        )
        assertTrue(passReport.results.single().outcome is EvidenceAuditOutcome.Pass)
        assertTrue(passReport.results.single().activationFactIds.isNotEmpty())

        val failingPayloads = listOf(
            Triple("missing-callsign", vehicleInitialCallTokens(callsign, position, destination, route).drop(1), "x"),
            Triple(
                "missing-route",
                vehicleInitialCallTokens(callsign, position, destination, route).take(5),
                "WORKER 21 GATE-27 TO HOTEL VIA",
            ),
            Triple(
                "wrong-destination",
                vehicleInitialCallTokens(callsign, position, PointId("WRONG"), route),
                "WORKER 21 GATE-27 TO WRONG VIA KILO ALPHA",
            ),
            Triple(
                "reordered-route",
                vehicleInitialCallTokens(callsign, position, destination, route.reversed()),
                "WORKER 21 GATE-27 TO HOTEL VIA ALPHA KILO",
            ),
            Triple(
                "shorter-route",
                vehicleInitialCallTokens(callsign, position, destination, listOf(PointId("KILO"))),
                "WORKER 21 GATE-27 TO HOTEL VIA KILO",
            ),
            Triple(
                "wrong-text",
                vehicleInitialCallTokens(callsign, position, destination, route),
                "WORKER 21 GATE-27 TO HOTEL VIA WRONG",
            ),
        )
        failingPayloads.forEach { (scenario, tokens, text) ->
            val report = renderedVehicleInitialCallReport(
                scenarioId = "vehicle-initial-call-$scenario",
                vehicle = vehicle,
                payload = renderedVehiclePhraseologyPayload(
                    vehicle = vehicle,
                    template = RenderedPhraseologyTemplate.VehicleInitialCall,
                    tokens = tokens,
                    text = text,
                ),
                callsign = callsign,
                position = position,
                destination = destination,
                route = route,
            )
            val outcome = report.results.single().outcome
            assertTrue(outcome is EvidenceAuditOutcome.Fail)
            val expectedReason = if (scenario.startsWith("missing-")) "Malformed" else "Mismatched"
            assertTrue(outcome.reason.contains(expectedReason))
            assertTrue(report.results.single().activationFactIds.isNotEmpty())
        }

        val aircraftPhraseologyReport = simEvidence("vehicle-initial-call-rejects-aircraft-phraseology") {
            observe {
                EvidenceFactAdapters.fromProjectedPayloads(
                    scenarioId = "vehicle-initial-call-rejects-aircraft-phraseology",
                    payloads = listOf(
                        renderedPhraseologyPayload(
                            aircraft = AircraftId("OE-ABC"),
                            template = RenderedPhraseologyTemplate.TaxiToStandInstruction,
                            tokens = listOf(
                                PhraseologyToken.AircraftCallsign(AircraftId("OE-ABC")),
                                PhraseologyToken.Taxi,
                                PhraseologyToken.To,
                                PhraseologyToken.PointName(destination),
                            ),
                            text = "OE-ABC TAXI TO HOTEL",
                        ),
                        renderedPhraseologyPayload(
                            aircraft = AircraftId("OE-DEF"),
                            template = RenderedPhraseologyTemplate.TakeoffClearance,
                            tokens = listOf(
                                PhraseologyToken.AircraftCallsign(AircraftId("OE-DEF")),
                                PhraseologyToken.Runway,
                                PhraseologyToken.RunwayDesignator(RunwayId("16C")),
                                PhraseologyToken.Cleared,
                                PhraseologyToken.For,
                                PhraseologyToken.TakeOff,
                            ),
                            text = "OE-DEF RUNWAY 16C CLEARED FOR TAKE-OFF",
                        ),
                    ),
                )
            }
            invariant("vehicle selector ignores aircraft phraseology") {
                expect { renderedVehicleDriverPhraseology(vehicle).initialCall(callsign, position, destination, route) }
            }
        }
        assertTrue(aircraftPhraseologyReport.results.single().outcome is EvidenceAuditOutcome.Fail)
        assertTrue(aircraftPhraseologyReport.results.single().activationFactIds.isEmpty())

        val absentVehicleEvidenceReport = simEvidence("vehicle-initial-call-absent-rendered-evidence") {
            observe {
                EvidenceFactAdapters.fromProjectedPayloads(
                    scenarioId = "vehicle-initial-call-absent-rendered-evidence",
                    payloads = emptyList(),
                )
            }
            invariant("vehicle selector reports absent rendered vehicle evidence") {
                expect { renderedVehicleDriverPhraseology(vehicle).initialCall(callsign, position, destination, route) }
            }
        }
        assertTrue(absentVehicleEvidenceReport.results.single().outcome is EvidenceAuditOutcome.Fail)
        assertTrue(absentVehicleEvidenceReport.results.single().activationFactIds.isEmpty())
    }

    @Test
    fun `rendered vehicle tow selector matches metadata and rejects missing fields`() {
        val vehicle = VehicleId("TUG-5")
        val callsign = Callsign("TUG-5")
        val station = ControllerId("LOWG_TOWER")
        val aircraft = AircraftId("OE-TOW")
        val operator = AircraftOperator("Austrian")

        val passReport = renderedVehicleTowReport(
            scenarioId = "vehicle-tow-pass",
            vehicle = vehicle,
            payload = renderedVehiclePhraseologyPayload(
                vehicle = vehicle,
                template = RenderedPhraseologyTemplate.VehicleTowRequest,
                tokens = vehicleTowTokens(callsign, station, aircraft, AircraftType.B738, operator),
                text = "LOWG_TOWER TUG-5 REQUEST TOW OE-TOW B738 Austrian",
            ),
            callsign = callsign,
            station = station,
            aircraft = aircraft,
            operator = operator,
        )
        assertTrue(passReport.results.single().outcome is EvidenceAuditOutcome.Pass)

        val failingPayloads = listOf(
            Triple(
                "missing-addressee",
                vehicleTowTokens(callsign, station, aircraft, AircraftType.B738, operator).drop(1),
                "TUG-5 REQUEST TOW OE-TOW B738 Austrian",
            ),
            Triple(
                "wrong-aircraft",
                vehicleTowTokens(callsign, station, AircraftId("OE-WRONG"), AircraftType.B738, operator),
                "LOWG_TOWER TUG-5 REQUEST TOW OE-WRONG B738 Austrian",
            ),
            Triple(
                "missing-type-operator",
                listOf(
                    PhraseologyToken.StationName(station),
                    PhraseologyToken.VehicleCallsign(callsign),
                    PhraseologyToken.Request,
                    PhraseologyToken.Tow,
                    PhraseologyToken.AircraftCallsign(aircraft),
                ),
                "LOWG_TOWER TUG-5 REQUEST TOW OE-TOW",
            ),
            Triple(
                "wrong-text",
                vehicleTowTokens(callsign, station, aircraft, AircraftType.B738, operator),
                "LOWG_TOWER TUG-5 REQUEST TOW OE-TOW WRONG Austrian",
            ),
        )
        failingPayloads.forEach { (scenario, tokens, text) ->
            val report = renderedVehicleTowReport(
                scenarioId = "vehicle-tow-$scenario",
                vehicle = vehicle,
                payload = renderedVehiclePhraseologyPayload(
                    vehicle = vehicle,
                    template = RenderedPhraseologyTemplate.VehicleTowRequest,
                    tokens = tokens,
                    text = text,
                ),
                callsign = callsign,
                station = station,
                aircraft = aircraft,
                operator = operator,
            )
            val outcome = report.results.single().outcome
            assertTrue(outcome is EvidenceAuditOutcome.Fail)
            val expectedReason = if (scenario == "wrong-aircraft" || scenario == "wrong-text") {
                "Mismatched"
            } else {
                "Malformed"
            }
            assertTrue(outcome.reason.contains(expectedReason))
            assertTrue(report.results.single().activationFactIds.isNotEmpty())
        }
    }

    @Test
    fun `rendered vehicle unsupported payload selector stays explicit`() {
        val vehicle = VehicleId("WORKER-21")
        val payload = EvidenceFactPayload.UnsupportedRenderedVehicleDriverPhraseology(
            vehicleId = vehicle,
            transmissionRef = TransmissionId(1),
            transmission = VehicleDriverTransmission.RequestFurtherPermission(
                vehicle = vehicle,
                from = PointId("HOLD-1"),
                destination = PointId("HOTEL"),
            ),
        )

        val report = simEvidence("vehicle-unsupported-rendered-phraseology") {
            observe {
                EvidenceFactAdapters.fromProjectedPayloads(
                    scenarioId = "vehicle-unsupported-rendered-phraseology",
                    payloads = listOf(payload),
                )
            }
            invariant("unsupported request further permission") {
                expect { renderedVehicleDriverPhraseology(vehicle).unsupportedRequestFurtherPermission() }
            }
        }

        assertTrue(report.results.single().outcome is EvidenceAuditOutcome.Pass)
        assertTrue(report.results.single().activationFactIds.isNotEmpty())
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

    @Test
    fun `essentialAerodromeInformationPhraseology selector matches exact synthetic example block`() {
        val expected = listOf(
            essentialCautionConstructionWorkExample(),
            essentialCentreLineLightingUnserviceableExample(),
            essentialRunwayConditionExample(),
        )
        val unrelated = essentialInformationPayload(
            category = EssentialAerodromeInformationCategory.WaterOnMovementArea,
            domain = EssentialAerodromeInformationDomain.MovementArea,
            facets = setOf(EssentialAerodromeInformationFacet.Runway),
            detail = "standing water on RWY 16C",
        )

        val passReport = essentialPhraseologyReport(
            scenarioId = "essential-phraseology-pass",
            payloads = listOf(unrelated) + expected,
        )
        assertTrue(passReport.results.single().outcome is EvidenceAuditOutcome.Pass)
        assertEquals(3, passReport.results.single().activationFactIds.size)

        val failingCases = listOf(
            "absent" to emptyList(),
            "missing-example" to expected.take(2),
            "wrong-order" to listOf(expected[1], expected[0], expected[2]),
            "wrong-template" to listOf(
                expected[0].copy(template = RenderedEssentialAerodromeInformationPhraseologyTemplate.RunwayConditionReport),
                expected[1],
                expected[2],
            ),
            "wrong-text" to listOf(
                expected[0].copy(text = RenderedPhraseText("FASTAIR 345 CAUTION WORK ADJACENT TO GATE 37")),
                expected[1],
                expected[2],
            ),
            "malformed-tokens" to listOf(
                expected[0].copy(tokens = expected[0].tokens.dropLast(1)),
                expected[1],
                expected[2],
            ),
            "extra-duplicate" to (expected + expected[0]),
        )

        failingCases.forEach { (scenario, payloads) ->
            val report = essentialPhraseologyReport(
                scenarioId = "essential-phraseology-$scenario",
                payloads = payloads,
            )
            assertTrue(report.results.single().outcome is EvidenceAuditOutcome.Fail, scenario)
        }
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

    private fun essentialPhraseologyReport(
        scenarioId: String,
        payloads: List<EvidenceFactPayload>,
    ): EvidenceAuditReport =
        simEvidence(scenarioId) {
            observe {
                EvidenceFactAdapters.fromProjectedPayloads(
                    scenarioId = scenarioId,
                    payloads = payloads,
                )
            }
            invariant("essential aerodrome information phraseology") {
                expect { essentialAerodromeInformationPhraseology().exampleBlock() }
            }
        }

    private fun renderedPhraseologyPayload(
        aircraft: AircraftId,
        template: RenderedPhraseologyTemplate,
        obligationKinds: Set<PhraseologyObligationKind> = setOf(
            PhraseologyObligationKind.OrderedPhrase,
            PhraseologyObligationKind.SemanticSlot,
            PhraseologyObligationKind.ForbiddenMeaning,
        ),
        tokens: List<PhraseologyToken>,
        text: String,
    ): EvidenceFactPayload.RenderedPhraseology =
        EvidenceFactPayload.RenderedPhraseology(
            controllerId = ControllerId("LOWG_TWR"),
            aircraftId = aircraft,
            transmissionRef = TransmissionId(1),
            template = template,
            obligationKinds = obligationKinds,
            tokens = tokens,
            text = RenderedPhraseText(text),
        )

    private fun airTaxiControllerOutput(
        instruction: AirTaxiTo,
    ): ControllerOutput.Instruct =
        ControllerOutput.Instruct.fromCoordinationReissue(
            coordination = OutstandingCoordination(
                aircraft = instruction.target,
                dispatch = Dispatch.Direct(instruction),
                certificationEvidence = NonEmptyList(
                    CertificationEvidence.RuntimeChecked(
                        checkId = "synthetic-icao9432-air-taxi-phraseology",
                        summary = "Synthetic ICAO 9432 §4.9 air-taxi phraseology branch",
                    ),
                    emptyList(),
                ),
                expectedReadback = setOf(TaxiRouteReadback(instruction.destination, instruction.via)),
                issuedAt = SimTime.ZERO,
            ),
            urgency = Urgency.PROGRESSION,
            trace = DecisionTrace("TEST-AIR-TAXI", "test air-taxi phraseology", emptyList()),
        )

    private fun airTaxiControllerRecord(
        transmissionId: TransmissionId,
        instruction: AirTaxiTo,
    ): TransmissionRecord =
        TransmissionRecord(
            transmissionId = transmissionId,
            time = SimTime.ZERO,
            endedAt = SimTime.ZERO + SimDuration.ofSeconds(2),
            speaker = SpeakerRef.Controller(ControllerId("GEORGETOWN_TWR")),
            receiver = ReceiverRef.Pilot(instruction.target),
            utterance = Utterance.FromController(airTaxiControllerOutput(instruction)),
        )

    private fun renderedPilotReportPhraseologyPayload(
        aircraft: AircraftId,
        template: RenderedPhraseologyTemplate,
        tokens: List<PhraseologyToken>,
        text: String,
    ): EvidenceFactPayload.RenderedPilotReportPhraseology =
        EvidenceFactPayload.RenderedPilotReportPhraseology(
            aircraftId = aircraft,
            transmissionRef = TransmissionId(1),
            template = template,
            obligationKinds = setOf(
                PhraseologyObligationKind.OrderedPhrase,
                PhraseologyObligationKind.SemanticSlot,
            ),
            tokens = tokens,
            text = RenderedPhraseText(text),
        )

    private fun renderedPilotReadbackPhraseologyPayload(
        aircraft: AircraftId,
        template: RenderedPhraseologyTemplate,
        tokens: List<PhraseologyToken>,
        text: String,
    ): EvidenceFactPayload.RenderedPilotReadbackPhraseology =
        EvidenceFactPayload.RenderedPilotReadbackPhraseology(
            aircraftId = aircraft,
            transmissionRef = TransmissionId(1),
            template = template,
            obligationKinds = setOf(
                PhraseologyObligationKind.OrderedPhrase,
                PhraseologyObligationKind.Readback,
                PhraseologyObligationKind.SemanticSlot,
            ),
            tokens = tokens,
            text = RenderedPhraseText(text),
        )

    private fun renderedVehiclePhraseologyPayload(
        vehicle: VehicleId,
        template: RenderedPhraseologyTemplate,
        tokens: List<PhraseologyToken>,
        text: String,
    ): EvidenceFactPayload.RenderedVehicleDriverPhraseology =
        EvidenceFactPayload.RenderedVehicleDriverPhraseology(
            vehicleId = vehicle,
            transmissionRef = TransmissionId(1),
            template = template,
            obligationKinds = setOf(
                PhraseologyObligationKind.OrderedPhrase,
                PhraseologyObligationKind.SemanticSlot,
            ),
            tokens = tokens,
            text = RenderedPhraseText(text),
        )

    private fun vehicleInitialCallTokens(
        callsign: Callsign,
        position: PointId,
        destination: PointId,
        route: List<PointId>,
    ): List<PhraseologyToken> =
        listOf(
            PhraseologyToken.VehicleCallsign(callsign),
            PhraseologyToken.PointName(position),
            PhraseologyToken.To,
            PhraseologyToken.PointName(destination),
            PhraseologyToken.Via,
        ) + route.map(PhraseologyToken::PointName)

    private fun vehicleTowTokens(
        callsign: Callsign,
        station: ControllerId,
        aircraft: AircraftId,
        aircraftType: AircraftType,
        operator: AircraftOperator,
    ): List<PhraseologyToken> =
        listOf(
            PhraseologyToken.StationName(station),
            PhraseologyToken.VehicleCallsign(callsign),
            PhraseologyToken.Request,
            PhraseologyToken.Tow,
            PhraseologyToken.AircraftCallsign(aircraft),
            PhraseologyToken.AircraftTypeName(aircraftType),
            PhraseologyToken.OperatorName(operator),
        )

    private fun renderedVehicleInitialCallReport(
        scenarioId: String,
        vehicle: VehicleId,
        payload: EvidenceFactPayload.RenderedVehicleDriverPhraseology,
        callsign: Callsign,
        position: PointId,
        destination: PointId,
        route: List<PointId>,
    ): EvidenceAuditReport =
        simEvidence(scenarioId) {
            observe {
                EvidenceFactAdapters.fromProjectedPayloads(
                    scenarioId = scenarioId,
                    payloads = listOf(payload),
                )
            }
            invariant("vehicle initial call") {
                expect { renderedVehicleDriverPhraseology(vehicle).initialCall(callsign, position, destination, route) }
            }
        }

    private fun renderedVehicleTowReport(
        scenarioId: String,
        vehicle: VehicleId,
        payload: EvidenceFactPayload.RenderedVehicleDriverPhraseology,
        callsign: Callsign,
        station: ControllerId,
        aircraft: AircraftId,
        operator: AircraftOperator,
    ): EvidenceAuditReport =
        simEvidence(scenarioId) {
            observe {
                EvidenceFactAdapters.fromProjectedPayloads(
                    scenarioId = scenarioId,
                    payloads = listOf(payload),
                )
            }
            invariant("vehicle tow request") {
                expect {
                    renderedVehicleDriverPhraseology(vehicle).towRequest(
                        callsign = callsign,
                        receivingStation = station,
                        aircraft = aircraft,
                        aircraftType = AircraftType.B738,
                        operator = operator,
                    )
                }
            }
        }

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
                template = RenderedPhraseologyTemplate.AfterLandingVacateViaInstruction,
                obligationKinds = setOf(
                    PhraseologyObligationKind.OrderedPhrase,
                    PhraseologyObligationKind.SemanticSlot,
                    PhraseologyObligationKind.Readback,
                ),
                tokens = listOf(
                    PhraseologyToken.AircraftCallsign(aircraft),
                    PhraseologyToken.Take,
                    PhraseologyToken.First,
                    PhraseologyToken.Right,
                    PhraseologyToken.When,
                    PhraseologyToken.Vacated,
                ),
                text = "OE-ABC TAKE FIRST RIGHT WHEN VACATED",
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
            renderedPhraseologyPayload(
                aircraft = aircraft,
                template = RenderedPhraseologyTemplate.AirTaxiToInstruction,
                obligationKinds = readbackInstructionObligationKinds,
                tokens = airTaxiInstructionTokens(aircraft),
                text = "${aircraft.value} AIR-TAXI TO HELICOPTER STAND",
            ),
            renderedPhraseologyPayload(
                aircraft = aircraft,
                template = RenderedPhraseologyTemplate.TaxiToStandInstruction,
                obligationKinds = setOf(
                    PhraseologyObligationKind.OrderedPhrase,
                    PhraseologyObligationKind.SemanticSlot,
                    PhraseologyObligationKind.Readback,
                ),
                tokens = listOf(
                    PhraseologyToken.AircraftCallsign(aircraft),
                    PhraseologyToken.Taxi,
                    PhraseologyToken.To,
                    PhraseologyToken.PointName(PointId("STAND-1")),
                ),
                text = "OE-ABC TAXI TO STAND-1",
            ),
        )

    private fun airTaxiExchangeReport(
        scenarioId: String,
        aircraft: AircraftId,
        payloads: List<EvidenceFactPayload>,
    ): EvidenceAuditReport =
        simEvidence(scenarioId) {
            observe {
                EvidenceFactAdapters.fromProjectedPayloads(
                    scenarioId = scenarioId,
                    payloads = payloads,
                )
            }
            invariant("helicopter air-taxi exchange") {
                expect { afterLandingPhraseology(aircraft).helicopterAirTaxiToStandExchange() }
            }
        }

    private fun airTaxiExchangePayloads(
        aircraft: AircraftId,
    ): List<EvidenceFactPayload> =
        listOf(airTaxiInstructionPayload(aircraft), airTaxiReadbackPayload(aircraft))

    private fun airTaxiInstructionPayload(
        aircraft: AircraftId,
    ): EvidenceFactPayload.RenderedPhraseology =
        renderedPhraseologyPayload(
            aircraft = aircraft,
            template = RenderedPhraseologyTemplate.AirTaxiToInstruction,
            obligationKinds = readbackInstructionObligationKinds,
            tokens = airTaxiInstructionTokens(aircraft),
            text = "${aircraft.value} AIR-TAXI TO HELICOPTER STAND",
        )

    private fun airTaxiReadbackPayload(
        aircraft: AircraftId,
    ): EvidenceFactPayload.RenderedPilotReadbackPhraseology =
        renderedPilotReadbackPhraseologyPayload(
            aircraft = aircraft,
            template = RenderedPhraseologyTemplate.AirTaxiRouteReadback,
            tokens = airTaxiRouteReadbackTokens(aircraft),
            text = "AIR-TAXI TO HELICOPTER STAND ${aircraft.value}",
        )

    private fun firstRightExchangeReport(
        scenarioId: String,
        aircraft: AircraftId,
        frequency: Frequency,
        payloads: List<EvidenceFactPayload>,
    ): EvidenceAuditReport =
        simEvidence(scenarioId) {
            observe {
                EvidenceFactAdapters.fromProjectedPayloads(
                    scenarioId = scenarioId,
                    payloads = payloads,
                )
            }
            invariant("first-right contact-ground exchange") {
                expect { afterLandingPhraseology(aircraft).firstRightWhenVacatedContactGroundExchange(frequency) }
            }
        }

    private fun firstRightExchangePayloads(
        aircraft: AircraftId,
        frequency: Frequency,
    ): List<EvidenceFactPayload> =
        listOf(
            firstRightInstructionPayload(aircraft),
            contactGroundPayload(aircraft, frequency),
            firstRightReadbackPayload(aircraft, frequency),
        )

    private fun firstRightInstructionPayload(
        aircraft: AircraftId,
    ): EvidenceFactPayload.RenderedPhraseology =
        renderedPhraseologyPayload(
            aircraft = aircraft,
            template = RenderedPhraseologyTemplate.AfterLandingVacateViaInstruction,
            obligationKinds = setOf(
                PhraseologyObligationKind.OrderedPhrase,
                PhraseologyObligationKind.SemanticSlot,
                PhraseologyObligationKind.Readback,
            ),
            tokens = listOf(
                PhraseologyToken.AircraftCallsign(aircraft),
                PhraseologyToken.Take,
                PhraseologyToken.First,
                PhraseologyToken.Right,
                PhraseologyToken.When,
                PhraseologyToken.Vacated,
            ),
            text = "${aircraft.value} TAKE FIRST RIGHT WHEN VACATED",
        )

    private fun contactGroundPayload(
        aircraft: AircraftId,
        frequency: Frequency,
    ): EvidenceFactPayload.RenderedPhraseology =
        renderedPhraseologyPayload(
            aircraft = aircraft,
            template = RenderedPhraseologyTemplate.ContactFrequencyInstruction,
            obligationKinds = setOf(
                PhraseologyObligationKind.OrderedPhrase,
                PhraseologyObligationKind.SemanticSlot,
                PhraseologyObligationKind.Readback,
            ),
            tokens = listOf(
                PhraseologyToken.AircraftCallsign(aircraft),
                PhraseologyToken.Contact,
                PhraseologyToken.UnitName("GROUND"),
                PhraseologyToken.FrequencyValue(frequency),
            ),
            text = "${aircraft.value} CONTACT GROUND ${frequency.mhz}",
        )

    private fun firstRightReadbackPayload(
        aircraft: AircraftId,
        frequency: Frequency,
    ): EvidenceFactPayload.RenderedPilotReadbackPhraseology =
        renderedPilotReadbackPhraseologyPayload(
            aircraft = aircraft,
            template = RenderedPhraseologyTemplate.FirstRightFrequencyReadback,
            tokens = listOf(
                PhraseologyToken.First,
                PhraseologyToken.Right,
                PhraseologyToken.FrequencyValue(frequency),
                PhraseologyToken.AircraftCallsign(aircraft),
            ),
            text = "FIRST RIGHT ${frequency.mhz} ${aircraft.value}",
        )

    private val readbackInstructionObligationKinds: Set<PhraseologyObligationKind> =
        setOf(
            PhraseologyObligationKind.OrderedPhrase,
            PhraseologyObligationKind.SemanticSlot,
            PhraseologyObligationKind.Readback,
        )
}
