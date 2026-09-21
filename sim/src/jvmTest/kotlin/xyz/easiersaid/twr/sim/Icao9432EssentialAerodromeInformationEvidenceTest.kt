package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.RunwayId

class Icao9432EssentialAerodromeInformationEvidenceTest {
    @Test
    fun `typed essential aerodrome information vocabulary covers ICAO 9432 section 4_10 category rows`() {
        val report = simEvidence("icao9432-essential-aerodrome-information-categories") {
            observe {
                EvidenceFactAdapters.fromProjectedPayloads(
                    scenarioId = "icao9432-essential-aerodrome-information-categories",
                    payloads = listOf(
                        essentialInformation(
                            domain = EssentialAerodromeInformationDomain.MovementArea,
                            category = EssentialAerodromeInformationCategory.WaterOnMovementArea,
                            facets = movementSurfaceFacets,
                            detail = "standing water on RWY 16C",
                        ),
                        essentialInformation(
                            domain = EssentialAerodromeInformationDomain.MovementArea,
                            category = EssentialAerodromeInformationCategory.RoughOrBrokenSurface,
                            facets = movementSurfaceFacets,
                            detail = "rough apron surface",
                        ),
                        essentialInformation(
                            domain = EssentialAerodromeInformationDomain.MovementArea,
                            category = EssentialAerodromeInformationCategory.ConstructionOrMaintenance,
                            facets = setOf(
                                EssentialAerodromeInformationFacet.OnMovementArea,
                                EssentialAerodromeInformationFacet.AdjacentToMovementArea,
                            ),
                            detail = "maintenance work adjacent to TWY C",
                        ),
                        essentialInformation(
                            domain = EssentialAerodromeInformationDomain.MovementArea,
                            category = EssentialAerodromeInformationCategory.SnowBankOrDrift,
                            facets = movementSurfaceFacets + EssentialAerodromeInformationFacet.AdjacentToMovementArea,
                            detail = "snow bank adjacent to RWY 16C",
                        ),
                        essentialInformation(
                            domain = EssentialAerodromeInformationDomain.MovementArea,
                            category = EssentialAerodromeInformationCategory.TemporaryHazard,
                            facets = setOf(
                                EssentialAerodromeInformationFacet.ParkedAircraft,
                                EssentialAerodromeInformationFacet.BirdsOnGroundOrInAir,
                            ),
                            detail = "birds on the movement area",
                        ),
                        essentialInformation(
                            domain = EssentialAerodromeInformationDomain.AssociatedFacility,
                            category = EssentialAerodromeInformationCategory.LightingSystemFailureOrIrregularOperation,
                            facets = setOf(EssentialAerodromeInformationFacet.LightingSystem),
                            detail = "approach lighting irregular operation",
                        ),
                        essentialInformation(
                            domain = EssentialAerodromeInformationDomain.MovementArea,
                            category = EssentialAerodromeInformationCategory.WinterContamination,
                            facets = movementSurfaceFacets,
                            detail = "slush on taxiway",
                        ),
                    ),
                    origin = EvidenceFactOrigin.SyntheticProjection,
                    diagnostic = "Synthetic structural vocabulary facts; not live sim-observed aerodrome information",
                )
            }

            sourceVocabulary("essential-information-definition-domain") {
                cites(ICAO9432.AerodromeInformation.Definition)
                expect {
                    essentialAerodromeInformation().domainsInclude(
                        setOf(
                            EssentialAerodromeInformationDomain.MovementArea,
                            EssentialAerodromeInformationDomain.AssociatedFacility,
                        ),
                    )
                }
            }

            sourceVocabulary("water-on-movement-area") {
                cites(ICAO9432.AerodromeInformation.WaterOnMovementArea)
                expect {
                    includes(
                        category = EssentialAerodromeInformationCategory.WaterOnMovementArea,
                        facets = movementSurfaceFacets,
                    )
                }
            }

            sourceVocabulary("rough-or-broken-surface") {
                cites(ICAO9432.AerodromeInformation.RoughOrBrokenSurfaces)
                expect {
                    includes(
                        category = EssentialAerodromeInformationCategory.RoughOrBrokenSurface,
                        facets = movementSurfaceFacets,
                    )
                }
            }

            sourceVocabulary("construction-or-maintenance") {
                cites(ICAO9432.AerodromeInformation.ConstructionOrMaintenance)
                expect {
                    includes(
                        category = EssentialAerodromeInformationCategory.ConstructionOrMaintenance,
                        facets = setOf(
                            EssentialAerodromeInformationFacet.OnMovementArea,
                            EssentialAerodromeInformationFacet.AdjacentToMovementArea,
                        ),
                    )
                }
            }

            sourceVocabulary("snow-bank-or-drift") {
                cites(ICAO9432.AerodromeInformation.SnowBanksOrDrifts)
                expect {
                    includes(
                        category = EssentialAerodromeInformationCategory.SnowBankOrDrift,
                        facets = movementSurfaceFacets + EssentialAerodromeInformationFacet.AdjacentToMovementArea,
                    )
                }
            }

            sourceVocabulary("temporary-hazard") {
                cites(ICAO9432.AerodromeInformation.OtherTemporaryHazards)
                expect {
                    includes(
                        category = EssentialAerodromeInformationCategory.TemporaryHazard,
                        facets = setOf(
                            EssentialAerodromeInformationFacet.ParkedAircraft,
                            EssentialAerodromeInformationFacet.BirdsOnGroundOrInAir,
                        ),
                    )
                }
            }

            sourceVocabulary("lighting-system-failure") {
                cites(ICAO9432.AerodromeInformation.LightingSystemFailure)
                expect {
                    includes(
                        category = EssentialAerodromeInformationCategory.LightingSystemFailureOrIrregularOperation,
                        facets = setOf(EssentialAerodromeInformationFacet.LightingSystem),
                    )
                }
            }

            sourceVocabulary("winter-contamination") {
                cites(ICAO9432.AerodromeInformation.SnowSlushOrIce)
                expect {
                    includes(
                        category = EssentialAerodromeInformationCategory.WinterContamination,
                        facets = movementSurfaceFacets,
                    )
                }
            }
        }

        report.assertNoFailures()
        assertEquals(
            setOf(EvidenceClaimKind.StructuralEvidenceVocabulary),
            report.results.map { result -> result.claimKind }.toSet(),
        )
    }

    @Test
    fun `synthetic rendered essential aerodrome information examples cover ICAO 9432 section 4_10 phraseology`() {
        val report = simEvidence("icao9432-essential-aerodrome-information-example-phraseology") {
            observe {
                EvidenceFactAdapters.fromProjectedPayloads(
                    scenarioId = "icao9432-essential-aerodrome-information-example-phraseology",
                    payloads = listOf(
                        EvidenceFactPayload.RenderedEssentialAerodromeInformationPhraseology(
                            template = RenderedEssentialAerodromeInformationPhraseologyTemplate
                                .CautionConstructionWorkAdjacentToGate,
                            tokens = listOf(
                                EssentialAerodromeInformationPhraseologyToken
                                    .AircraftCallsign(AircraftId("FASTAIR 345")),
                                EssentialAerodromeInformationPhraseologyToken.Caution,
                                EssentialAerodromeInformationPhraseologyToken.ConstructionWork,
                                EssentialAerodromeInformationPhraseologyToken.AdjacentTo,
                                EssentialAerodromeInformationPhraseologyToken.Gate("37"),
                            ),
                            text = RenderedPhraseText(
                                "FASTAIR 345 CAUTION CONSTRUCTION WORK ADJACENT TO GATE 37",
                            ),
                        ),
                        EvidenceFactPayload.RenderedEssentialAerodromeInformationPhraseology(
                            template = RenderedEssentialAerodromeInformationPhraseologyTemplate
                                .CentreLineTaxiwayLightingUnserviceable,
                            tokens = listOf(
                                EssentialAerodromeInformationPhraseologyToken.CentreLine,
                                EssentialAerodromeInformationPhraseologyToken.TaxiwayLighting,
                                EssentialAerodromeInformationPhraseologyToken.Unserviceable,
                            ),
                            text = RenderedPhraseText("CENTRE LINE TAXIWAY LIGHTING UNSERVICEABLE"),
                        ),
                        EvidenceFactPayload.RenderedEssentialAerodromeInformationPhraseology(
                            template = RenderedEssentialAerodromeInformationPhraseologyTemplate.RunwayConditionReport,
                            tokens = listOf(
                                EssentialAerodromeInformationPhraseologyToken.RunwayConditions,
                                EssentialAerodromeInformationPhraseologyToken.RunwayDesignator(RunwayId("09")),
                                EssentialAerodromeInformationPhraseologyToken.AvailableWidth,
                                EssentialAerodromeInformationPhraseologyToken.WidthMetres(32),
                                EssentialAerodromeInformationPhraseologyToken.CoveredWithThinPatchesOfIce,
                                EssentialAerodromeInformationPhraseologyToken.BrakingActionPoor,
                            ),
                            text = RenderedPhraseText(
                                "RUNWAY CONDITIONS 09: AVAILABLE WIDTH 32 METRES, " +
                                    "COVERED WITH THIN PATCHES OF ICE, BRAKING ACTION POOR",
                            ),
                        ),
                    ),
                    origin = EvidenceFactOrigin.SyntheticProjection,
                    diagnostic = "Synthetic rendered example phraseology facts; not live controller projection",
                )
            }

            sourceRenderedExample("essential-information-example-phraseology") {
                cites(ICAO9432.AerodromeInformation.ExamplePhraseology)
                sample("source", "ICAO Doc 9432, Manual of Radiotelephony, Fourth Edition, 2007, §4.10")
                sample("claim-scope", "synthetic rendered example phraseology")
                expect {
                    essentialAerodromeInformationPhraseology().exampleBlock()
                }
            }
        }

        report.assertNoFailures()
        assertEquals(
            setOf(EvidenceClaimKind.SyntheticRenderedPhraseologyExample),
            report.results.map { result -> result.claimKind }.toSet(),
        )
    }

    private fun EvidenceExpectContext.includes(
        category: EssentialAerodromeInformationCategory,
        facets: Set<EssentialAerodromeInformationFacet>,
    ): EvidenceAuditOutcome =
        essentialAerodromeInformation().includes(category = category, facets = facets)

    private fun essentialInformation(
        domain: EssentialAerodromeInformationDomain,
        category: EssentialAerodromeInformationCategory,
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

    private val movementSurfaceFacets: Set<EssentialAerodromeInformationFacet> =
        setOf(
            EssentialAerodromeInformationFacet.Runway,
            EssentialAerodromeInformationFacet.Taxiway,
            EssentialAerodromeInformationFacet.Apron,
        )
}
