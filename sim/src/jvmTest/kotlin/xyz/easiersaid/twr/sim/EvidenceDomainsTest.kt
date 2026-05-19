package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.FlyHeading
import xyz.easiersaid.twr.protocol.HeadingReadback

class EvidenceDomainsTest {
    @Test
    fun `heading domain is deterministic and partitioned`() {
        val first = EvidenceDomains.headings(seed = 9432L, count = 12)
        val second = EvidenceDomains.headings(seed = 9432L, count = 12)

        assertEquals(
            first.samples.map { sample -> sample.metadata.displayValue },
            second.samples.map { sample -> sample.metadata.displayValue },
        )
        assertTrue(first.samples.map { sample -> sample.metadata.partition }.toSet().size >= 2)
        assertTrue(first.samples.all { sample -> sample.metadata.seed == 9432L && sample.metadata.count == 12 })
    }

    @Test
    fun `generated heading protocol readbacks carry reproduction metadata`() {
        val aircraft = AircraftId("OE-ABC")
        val domain = EvidenceDomains.headings(seed = 9432L, count = 12)

        val report = protocolEvidence("generated-heading-readbacks") {
            generatedProtocol("heading readbacks", domain) { sample ->
                structuralReadback(
                    id = "heading readback ${sample.metadata.sampleIndex}",
                    instruction = FlyHeading(aircraft, sample.value),
                ) {
                    cites(ICAO9432.Readback.OperationalParametersRequiredReadback)
                    sample(sample)
                    requires(HeadingReadback(sample.value))
                }
            }
        }

        report.assertNoFailures()
        assertEquals(12, report.results.size)
        assertTrue(report.results.all { result -> result.samples.single().generated != null })
        assertEquals(
            domain.samples.map { sample -> sample.metadata.sampleIndex },
            report.results.map { result -> result.samples.single().generated?.sampleIndex },
        )
    }

    @Test
    fun `omitted-field generated readback case is reported as a negative structural failure`() {
        val aircraft = AircraftId("OE-ABC")
        val sample = EvidenceDomains.headings(seed = 9432L, count = 1).samples.single()

        val report = protocolEvidence("generated-heading-negative") {
            structuralReadback(
                id = "heading omitted readback atom ${sample.metadata.sampleIndex}",
                instruction = FlyHeading(aircraft, sample.value),
            ) {
                cites(ICAO9432.Readback.OperationalParametersRequiredReadback)
                sample(sample)
                requiresNoAtoms()
            }
        }

        val result = report.results.single()
        assertTrue(result.outcome is EvidenceAuditOutcome.Fail)
        assertEquals(sample.metadata, result.samples.single().generated)
        assertEquals("heading omitted readback atom 0", result.id)
        assertEquals(ICAO9432.Readback.OperationalParametersRequiredReadback, result.sources.single())
    }
}
