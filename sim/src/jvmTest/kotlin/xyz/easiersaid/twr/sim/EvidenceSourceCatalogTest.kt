package xyz.easiersaid.twr.sim

import java.nio.file.Files
import java.nio.file.Paths
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
                "icao9432-extracted::readback_2_8_3_en::f06dfa1cefd2d649",
                "icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::4c808d67d281ff71",
                "icao9432-extracted::taxi_4_4_en::417f64324f7495bf",
                "icao9432-extracted::taxi_4_4_en::b9e7fc3605fe616e",
                "icao9432-extracted::taxi_4_4_en::1367907005a34ad1",
                "icao9432-extracted::taxi_4_4_en::53f33b6da4f2be58",
                "icao9432-extracted::taxi_4_4_en::eadf2541fcd51825",
                "icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::19cfd36a9fce4587",
                "icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::13264a6ac6d529c3",
                "icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::a93888a25f0bad03",
                "icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::6b5a0d8b27525cbd",
                "icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::8af22eb8d9795cef",
                "icao9432-extracted::final_approach_landing_4_7_en::0ece166e11d7728e",
                "icao9432-extracted::final_approach_landing_4_7_en::00baaf3c55155044",
                "icao9432-extracted::final_approach_landing_4_7_en::4c698a5ad52a30e4",
                "icao9432-extracted::final_approach_landing_4_7_en::70e781a65920c075",
                "icao9432-extracted::final_approach_landing_4_7_en::a4c8fffd8a61adb4",
                "icao9432-extracted::go_around_4_8_en::43c33a8e74b02873",
                "icao9432-extracted::go_around_4_8_en::6c8993a0519d5d64",
                "icao9432-extracted::go_around_4_8_en::c3581d40a48406bb",
                "icao9432-extracted::after_landing_4_9_en::4a512226eec962cb",
                "icao9432-extracted::after_landing_4_9_en::5d742dc66caa1790",
                "icao9432-extracted::after_landing_4_9_en::df25159c1e7b94a3",
                "icao9432-extracted::after_landing_4_9_en::e30350fdecad45a1",
                "icao9432-extracted::transfer_communications_2_8_2_en::40382df156ad071e",
                "icao9432-extracted::transfer_communications_2_8_2_en::96720e821bf926cc",
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
                "icao9432-extracted::distress_urgency_intro_9_1_en::06f7a72397c325ac",
                "icao9432-extracted::distress_urgency_intro_9_1_en::13d1c2accd0f7a73",
                "icao9432-extracted::distress_urgency_intro_9_1_en::1a20cd48e58a5693",
                "icao9432-extracted::distress_urgency_intro_9_1_en::24f94381ed9e8ce1",
                "icao9432-extracted::distress_urgency_intro_9_1_en::2fee92c222323e6a",
                "icao9432-extracted::distress_urgency_intro_9_1_en::3b1079aa56df2ce6",
                "icao9432-extracted::distress_urgency_intro_9_1_en::7d35c042421b5b03",
                "icao9432-extracted::distress_urgency_intro_9_1_en::82ee7048517d8478",
                "icao9432-extracted::distress_urgency_intro_9_1_en::87b67820c6092a98",
                "icao9432-extracted::distress_urgency_intro_9_1_en::8b3b3b4117c04807",
                "icao9432-extracted::distress_urgency_intro_9_1_en::8e9f7818b91d08c3",
                "icao9432-extracted::distress_urgency_intro_9_1_en::9907744b4723d14c",
                "icao9432-extracted::distress_urgency_intro_9_1_en::9c34a1b8d6d623fa",
                "icao9432-extracted::distress_urgency_intro_9_1_en::bf04647e26f9c018",
                "icao9432-extracted::distress_urgency_intro_9_1_en::c30159856a1a5e7a",
                "icao9432-extracted::distress_urgency_intro_9_1_en::c959f0325390e7fe",
                "icao9432-extracted::distress_urgency_intro_9_1_en::cb12c2f9b97c64b7",
                "icao9432-extracted::distress_urgency_intro_9_1_en::d742970b22d8de26",
                "icao9432-extracted::distress_messages_9_2_en::23c9f447cd6c7814",
                "icao9432-extracted::distress_messages_9_2_en::27a450fa3bfcbc0a",
                "icao9432-extracted::distress_messages_9_2_en::4b37e039e7eb8afa",
                "icao9432-extracted::distress_messages_9_2_en::94e94be0c800c982",
                "icao9432-extracted::distress_messages_9_2_en::c20024dad1b7144e",
                "icao9432-extracted::distress_messages_9_2_en::e2902de496f43a95",
                "icao9432-extracted::distress_messages_9_2_en::ed898005cd1a4da5",
                "icao9432-extracted::distress_messages_9_2_en::f0e99a4c08ea0cb3",
                "icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::082f9668292ed82c",
                "icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::1de475a788206cc8",
                "icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::5df94af7a64c3f5d",
                "icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::b95d7bb1cb409bca",
                "icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::c71568b00fb1535e",
                "icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::ca0c243491ff5d13",
                "icao9432-extracted::communications_failure_9_5_en::045c2e33f59f5ede",
                "icao9432-extracted::communications_failure_9_5_en::24c806b040f4ef5e",
                "icao9432-extracted::communications_failure_9_5_en::75055714e70d4560",
                "icao9432-extracted::communications_failure_9_5_en::78c73a75fab644f4",
                "icao9432-extracted::communications_failure_9_5_en::7900c606e05e509b",
                "icao9432-extracted::communications_failure_9_5_en::91e7d233bf3b64ff",
                "icao9432-extracted::communications_failure_9_5_en::975a63151706f68f",
                "icao9432-extracted::communications_failure_9_5_en::abbc376a430003b0",
                "icao9432-extracted::communications_failure_9_5_en::b73dda299970c2f3",
                "icao9432-extracted::communications_failure_9_5_en::bb66a050093251c2",
                "icao9432-extracted::communications_failure_9_5_en::bc9bb12804033b07",
                "icao9432-extracted::communications_failure_9_5_en::c1c14fab53a608c6",
                "icao9432-extracted::communications_failure_9_5_en::f05016444e2b8808",
                "icao9432-extracted::communications_failure_9_5_en::fcb3a49672165b4f",
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
    fun `rendered phraseology claim scope is limited to reviewed PHRASE-1 proof units`() {
        assertEquals(
            setOf(
                "icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::13264a6ac6d529c3",
                "icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::a93888a25f0bad03",
                "icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::6b5a0d8b27525cbd",
                "icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::8af22eb8d9795cef",
                "icao9432-extracted::transfer_communications_2_8_2_en::96720e821bf926cc",
                "icao9432-extracted::final_approach_landing_4_7_en::a4c8fffd8a61adb4",
                "icao9432-extracted::final_approach_landing_4_7_en::00baaf3c55155044",
                "icao9432-extracted::final_approach_landing_4_7_en::4c698a5ad52a30e4",
                "icao9432-extracted::final_approach_landing_4_7_en::70e781a65920c075",
                "icao9432-extracted::after_landing_4_9_en::df25159c1e7b94a3",
                "icao9432-extracted::after_landing_4_9_en::e30350fdecad45a1",
                "icao9432-extracted::readback_2_8_3_en::f06dfa1cefd2d649",
                "icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::4c808d67d281ff71",
            ),
            EvidenceSourceCatalog.All
                .filter { source -> source.record.claimScope == EvidenceSourceClaimScope.RenderedPhraseologyTrace }
                .map { source -> source.canonicalId }
                .toSet(),
        )
    }

    @Test
    fun `final report rendered phraseology refs are explicitly wording only`() {
        val refs = ICAO9432.FinalApproachLanding.ReportWording

        assertEquals(
            setOf(
                "icao9432-extracted::final_approach_landing_4_7_en::00baaf3c55155044",
                "icao9432-extracted::final_approach_landing_4_7_en::4c698a5ad52a30e4",
                "icao9432-extracted::final_approach_landing_4_7_en::70e781a65920c075",
            ),
            refs.map { ref -> ref.canonicalId }.toSet(),
        )
        assertTrue(refs.all { ref -> ref.record.title.contains("Wording-only") })
        assertTrue(refs.all { ref -> ref.record.title.contains("blocked") })
        assertTrue(refs.all { ref -> ref.record.claimScope == EvidenceSourceClaimScope.RenderedPhraseologyTrace })
    }

    @Test
    fun `final report residual blockers remain represented in central manifest`() {
        val manifest = Files.readString(
            manifestPath(),
        )

        listOf(
            "icao9432-extracted::final_approach_landing_4_7_en::00baaf3c55155044" to
                "split: FINAL wording covered; distance/timing remains blocked",
            "icao9432-extracted::final_approach_landing_4_7_en::4c698a5ad52a30e4" to
                "split: LONG FINAL wording covered; final-turn distance remains blocked",
            "icao9432-extracted::final_approach_landing_4_7_en::70e781a65920c075" to
                "split: straight-in LONG FINAL wording covered; straight-in timing/policy remains blocked",
        ).forEach { (sourceId, state) ->
            assertTrue(manifest.contains(sourceId), "manifest missing $sourceId")
            assertTrue(manifest.contains(state), "manifest missing state $state")
        }
    }

    @Test
    fun `after-landing contact-ground residual blocker remains represented in central manifest`() {
        val manifest = Files.readString(
            manifestPath(),
        )

        val sourceId = "icao9432-extracted::after_landing_4_9_en::df25159c1e7b94a3"
        val state = "split: CONTACT GROUND wording covered; TAKE FIRST RIGHT WHEN VACATED remains blocked"
        val residual = "first-right/vacating wording remains PHRASE-1"
        assertTrue(manifest.contains(sourceId), "manifest missing $sourceId")
        assertTrue(manifest.contains(state), "manifest missing state $state")
        assertTrue(manifest.contains(residual), "manifest missing residual $residual")
    }

    private fun manifestPath() =
        generateSequence(Paths.get("").toAbsolutePath()) { path -> path.parent }
            .map { path ->
                path.resolve(
                    "research/tools/requirements-spike/quality/icao9432_programme/implementation_blocker_manifest.csv",
                )
            }
            .first { path -> Files.exists(path) }
}
