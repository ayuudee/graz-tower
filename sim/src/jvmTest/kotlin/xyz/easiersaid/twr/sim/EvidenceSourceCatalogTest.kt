package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EvidenceSourceCatalogTest {
    @Test
    fun `catalog covers FN41 source refs with exact registry records`() {
        EvidenceSourceCatalog.validateAgainstRegistry().requireValid()

        assertEquals(
            setOf(
                "icao9432-extracted::communications_2_8_1_en::0a964f42b6100596",
                "icao9432-extracted::readback_2_8_3_en::15940532b37f8528",
                "icao9432-extracted::readback_2_8_3_en::25c245bc4728ed60",
                "icao9432-extracted::readback_2_8_3_en::58594a8ee6243296",
                "icao9432-extracted::readback_2_8_3_en::4b6ece953649da07",
                "icao9432-extracted::readback_2_8_3_en::ac9111d240cfd2c2",
                "icao9432-extracted::taxi_4_4_en::417f64324f7495bf",
                "icao9432-extracted::taxi_4_4_en::b9e7fc3605fe616e",
                "icao9432-extracted::final_approach_landing_4_7_en::0ece166e11d7728e",
                "icao9432-extracted::final_approach_landing_4_7_en::a4c8fffd8a61adb4",
                "icao9432-extracted::transfer_communications_2_8_2_en::40382df156ad071e",
                "icao9432-extracted::transfer_communications_2_8_2_en::b49ae03cbbb2d538",
                "icao9432-extracted::essential_aerodrome_information_4_10_en::1aa5cb7e758055bc",
                "icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4",
            ),
            EvidenceSourceCatalog.All.map { source -> source.canonicalId }.toSet(),
        )
    }

    @Test
    fun `validation rejects malformed and invented plausible ids`() {
        val malformed = EvidenceSourceRecord(
            canonicalId = "icao9432-extracted::readback_2_8_3_en",
            title = "Malformed",
            claimScope = EvidenceSourceClaimScope.StructuralProtocol,
        )
        val invented = EvidenceSourceRecord(
            canonicalId = "icao9432-extracted::readback_2_8_3_en::ffffffffffffffff",
            title = "Invented",
            claimScope = EvidenceSourceClaimScope.StructuralProtocol,
        )

        assertIs<EvidenceSourceValidation.Invalid>(
            EvidenceSourceCatalog.validateRecordAgainstRegistry(malformed),
        )
        assertIs<EvidenceSourceValidation.Invalid>(
            EvidenceSourceCatalog.validateRecordAgainstRegistry(invented),
        )
    }

    @Test
    fun `gap ids carry affected sources missing concept closure trigger and backlog link`() {
        val metadata = EvidenceGaps.All.map { gap -> gap.metadata }

        assertEquals(4, metadata.size)
        assertTrue(metadata.all { gap -> gap.affectedSources.isNotEmpty() })
        assertTrue(metadata.all { gap -> gap.missingConcept.isNotBlank() })
        assertTrue(metadata.all { gap -> gap.closureTrigger.isNotBlank() })
        assertEquals(
            setOf("FN43-GAP-1", "FN43-GAP-2", "FN44-GAP-1", "FN44-GAP-2"),
            metadata.map { gap -> gap.tracking.id }.toSet(),
        )
    }

    @Test
    fun `ordinary citation scope has no raw string cites overload`() {
        val citeMethods = EvidenceSourceCitationScope::class.java.methods
            .filter { method -> method.name == "cites" }

        assertTrue(citeMethods.isNotEmpty())
        assertTrue(
            citeMethods.none { method ->
                method.parameterTypes.any { parameterType -> parameterType == String::class.java }
            },
            "ordinary evidence citation API must not accept raw source-unit strings",
        )
    }

    @Test
    fun `catalog records do not claim phraseology compliance`() {
        assertTrue(
            EvidenceSourceCatalog.All.none { source ->
                source.record.claimScope.name.contains("PhraseologyCompliance")
            },
            "catalog entries may cite phraseology source units but must not claim phraseology compliance",
        )
    }
}
