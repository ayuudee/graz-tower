package xyz.easiersaid.twr.sim

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.FlyHeading
import xyz.easiersaid.twr.protocol.HeadingReadback
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint

class EvidenceReportWriterTest {
    @Test
    fun `writes deterministic markdown and json report with source activation and generated metadata`() {
        val outputDir = Files.createTempDirectory("evidence-report")
        val aircraft = AircraftId("OE-ABC")
        val runway = RunwayId("16C")
        val domain = EvidenceDomains.headings(seed = 9432L, count = 3)
        val generatedSample = domain.samples.first()
        val simReport = simEvidence("report-lowg") {
            observe {
                EvidenceFactAdapters.lowgCircuitTraining(
                    scenarioId = "report-lowg",
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
        val protocolReport = protocolEvidence("report-generated") {
            structuralReadback("generated heading", FlyHeading(aircraft, generatedSample.value)) {
                cites(ICAO9432.Readback.OperationalParametersRequiredReadback)
                sample(generatedSample)
                requires(HeadingReadback(generatedSample.value))
            }
        }

        val simFiles = EvidenceReportWriter.write(simReport, outputDir)
        val protocolFiles = EvidenceReportWriter.write(protocolReport, outputDir)

        assertTrue(Files.exists(simFiles.markdown))
        assertTrue(Files.exists(simFiles.json))
        assertEquals(Files.readString(simFiles.markdown), EvidenceReportWriter.markdown(simReport))
        assertEquals(Files.readString(protocolFiles.json), EvidenceReportWriter.json(protocolReport))

        val simCase = Json.parseToJsonElement(Files.readString(simFiles.json))
            .jsonObject.getValue("cases").jsonArray.single().jsonObject
        assertEquals("SimObservedSourceBehaviour", simCase.getValue("claimKind").jsonPrimitive.content)
        assertEquals("activated", simCase.getValue("activation").jsonPrimitive.content)
        assertEquals("activated-facts-present", simCase.getValue("adequacy").jsonPrimitive.content)
        assertTrue(simCase.getValue("sourceRefs").jsonArray.isNotEmpty())
        assertTrue(simCase.getValue("activationFactIds").jsonArray.isNotEmpty())

        val protocolCase = Json.parseToJsonElement(Files.readString(protocolFiles.json))
            .jsonObject.getValue("cases").jsonArray.single().jsonObject
        val generated = protocolCase.getValue("samples").jsonArray.single().jsonObject.getValue("generated").jsonObject
        assertEquals("heading", generated.getValue("domainName").jsonPrimitive.content)
        assertEquals("9432", generated.getValue("seed").jsonPrimitive.content)
        assertNotNull(generated["sampleIndex"])
        assertNotNull(generated["partition"])
        assertEquals("not-required", protocolCase.getValue("activation").jsonPrimitive.content)
    }

    @Test
    fun `report includes typed expected gap metadata and missing activation failure`() {
        val outputDir = Files.createTempDirectory("evidence-gap-report")
        val gapReport = simEvidence("gap-report") {
            observe { EvidenceFactSet(scenarioId = "gap-report", facts = emptyList(), diagnostic = "empty") }

            source("expected aerodrome information gap") {
                cites(ICAO9432.GapSources.EssentialAerodromeInformationTiming)
                expect {
                    expectedGap(
                        EvidenceGaps.EssentialAerodromeInformationReceiptProjection,
                        "aerodrome-information receipt facts are not projected yet",
                    )
                }
            }

            source("missing activation") {
                cites(ICAO9432.Taxi.HoldingPointLimit)
                expect { pass("bad source case") }
            }

            sourceRenderedExample("missing synthetic activation") {
                cites(ICAO9432.AerodromeInformation.ExamplePhraseology)
                expect { pass("bad synthetic source case") }
            }
        }

        val files = EvidenceReportWriter.write(gapReport, outputDir)
        val cases = Json.parseToJsonElement(Files.readString(files.json))
            .jsonObject.getValue("cases").jsonArray.map { element -> element.jsonObject }
        val gapCase = cases.first { case -> case.getValue("caseId").jsonPrimitive.content == "expected aerodrome information gap" }
        val missingActivation = cases.first { case -> case.getValue("caseId").jsonPrimitive.content == "missing activation" }
        val missingSyntheticActivation =
            cases.first { case -> case.getValue("caseId").jsonPrimitive.content == "missing synthetic activation" }

        assertEquals("expected_gap", gapCase.getValue("outcome").jsonPrimitive.content)
        assertEquals("typed-gap", gapCase.getValue("activation").jsonPrimitive.content)
        assertEquals(
            "evidence-gap-essential-aerodrome-information-receipt",
            gapCase.getValue("typedGap").jsonObject.getValue("id").jsonPrimitive.content,
        )
        assertEquals("fail", missingActivation.getValue("outcome").jsonPrimitive.content)
        assertEquals("missing", missingActivation.getValue("activation").jsonPrimitive.content)
        assertEquals("fail", missingSyntheticActivation.getValue("outcome").jsonPrimitive.content)
        assertEquals("missing", missingSyntheticActivation.getValue("activation").jsonPrimitive.content)
    }
}
