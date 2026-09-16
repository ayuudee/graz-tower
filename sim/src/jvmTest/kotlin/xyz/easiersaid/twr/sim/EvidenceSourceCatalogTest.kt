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
                "icao9432-extracted::taxi_4_4_en::1367907005a34ad1",
                "icao9432-extracted::taxi_4_4_en::53f33b6da4f2be58",
                "icao9432-extracted::taxi_4_4_en::eadf2541fcd51825",
                "icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::19cfd36a9fce4587",
                "icao9432-extracted::final_approach_landing_4_7_en::0ece166e11d7728e",
                "icao9432-extracted::final_approach_landing_4_7_en::a4c8fffd8a61adb4",
                "icao9432-extracted::go_around_4_8_en::43c33a8e74b02873",
                "icao9432-extracted::go_around_4_8_en::6c8993a0519d5d64",
                "icao9432-extracted::go_around_4_8_en::c3581d40a48406bb",
                "icao9432-extracted::after_landing_4_9_en::4a512226eec962cb",
                "icao9432-extracted::after_landing_4_9_en::5d742dc66caa1790",
                "icao9432-extracted::transfer_communications_2_8_2_en::40382df156ad071e",
                "icao9432-extracted::transfer_communications_2_8_2_en::b49ae03cbbb2d538",
                "icao9432-extracted::essential_aerodrome_information_4_10_en::01c0a4bc62b1e926",
                "icao9432-extracted::essential_aerodrome_information_4_10_en::1306eb5cc586df34",
                "icao9432-extracted::essential_aerodrome_information_4_10_en::18288908932d5ee8",
                "icao9432-extracted::essential_aerodrome_information_4_10_en::1aa5cb7e758055bc",
                "icao9432-extracted::essential_aerodrome_information_4_10_en::502221a46fcc2879",
                "icao9432-extracted::essential_aerodrome_information_4_10_en::66196c8442372a96",
                "icao9432-extracted::essential_aerodrome_information_4_10_en::736cc42a00337fee",
                "icao9432-extracted::essential_aerodrome_information_4_10_en::9b6c9dbc2af2b5b0",
                "icao9432-extracted::essential_aerodrome_information_4_10_en::a531dea421075380",
                "icao9432-extracted::essential_aerodrome_information_4_10_en::c874e24413f4cdee",
                "icao9432-extracted::essential_aerodrome_information_4_10_en::c92651071a9c009e",
                "icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::122426242abf3225",
                "icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::6061311019d039c7",
                "icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::606d053954eff037",
                "icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::7759017903acf140",
                "icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::84c61b2ef1f12ad5",
                "icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::ed59ee805ff7fe77",
                "icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::0b45b4a4dc2acc0c",
                "icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::24f7b6a86407ef6f",
                "icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::29be4b26bb851605",
                "icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::331c1cfc98ead868",
                "icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::4b103081585bfb71",
                "icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::735b3e9ada06105b",
                "icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4",
                "icao9432-extracted::test_procedures_2_8_4_en::d67d1f63cbbecd7d",
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

        assertEquals(3, metadata.size)
        assertTrue(metadata.all { gap -> gap.affectedSources.isNotEmpty() })
        assertTrue(metadata.all { gap -> gap.missingConcept.isNotBlank() })
        assertTrue(metadata.all { gap -> gap.closureTrigger.isNotBlank() })
        assertEquals(
            setOf("FN43-GAP-1", "FN43-GAP-2", "FN44-GAP-1"),
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
