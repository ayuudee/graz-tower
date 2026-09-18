package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertEquals

class Icao9432ModelGapSourceUnitSpecTest {
    private val chunk08DistressUrgencyClassificationRefs: List<SourceUnitRef> = chunk08Refs(
        "icao9432-extracted::distress_urgency_intro_9_1_en::13d1c2accd0f7a73",
        "icao9432-extracted::distress_urgency_intro_9_1_en::c30159856a1a5e7a",
    )

    private val chunk08EmergencyMessagePhraseologyRefs: List<SourceUnitRef> = chunk08Refs(
        "icao9432-extracted::distress_urgency_intro_9_1_en::9907744b4723d14c",
        "icao9432-extracted::distress_urgency_intro_9_1_en::bf04647e26f9c018",
        "icao9432-extracted::distress_messages_9_2_en::f0e99a4c08ea0cb3",
    )

    private val chunk08AssistanceRelayTerminationRefs: List<SourceUnitRef> = chunk08Refs(
        "icao9432-extracted::distress_messages_9_2_en::4b37e039e7eb8afa",
    )

    private val chunk08EmergencyDescentRefs: List<SourceUnitRef> = emptyList()

    private val chunk08EmergencyDescentPolicyRefs: List<SourceUnitRef> = chunk08Refs(
        "icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::c71568b00fb1535e",
    )

    private val chunk08CommsFailureRoutingRefs: List<SourceUnitRef> = emptyList()

    private val chunk08BlindTransmissionPhraseologyRefs: List<SourceUnitRef> = chunk08Refs(
        "icao9432-extracted::communications_failure_9_5_en::bc9bb12804033b07",
        "icao9432-extracted::communications_failure_9_5_en::abbc376a430003b0",
        "icao9432-extracted::communications_failure_9_5_en::78c73a75fab644f4",
    )

    private val chunk08SsrAndBlindClearanceRefs: List<SourceUnitRef> = chunk08Refs(
        "icao9432-extracted::communications_failure_9_5_en::c1c14fab53a608c6",
    )

    private val chunk08CoveredOrSplitRefs: List<SourceUnitRef> = chunk08Refs(
        "icao9432-extracted::distress_urgency_intro_9_1_en::8b3b3b4117c04807",
        "icao9432-extracted::distress_urgency_intro_9_1_en::c959f0325390e7fe",
        "icao9432-extracted::distress_urgency_intro_9_1_en::d742970b22d8de26",
        "icao9432-extracted::distress_messages_9_2_en::23c9f447cd6c7814",
        "icao9432-extracted::distress_urgency_intro_9_1_en::3b1079aa56df2ce6",
        "icao9432-extracted::distress_urgency_intro_9_1_en::1a20cd48e58a5693",
        "icao9432-extracted::distress_urgency_intro_9_1_en::7d35c042421b5b03",
        "icao9432-extracted::distress_messages_9_2_en::e2902de496f43a95",
        "icao9432-extracted::distress_messages_9_2_en::ed898005cd1a4da5",
        "icao9432-extracted::distress_messages_9_2_en::c20024dad1b7144e",
        "icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::082f9668292ed82c",
        "icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::ca0c243491ff5d13",
        "icao9432-extracted::distress_urgency_intro_9_1_en::9c34a1b8d6d623fa",
        "icao9432-extracted::distress_urgency_intro_9_1_en::24f94381ed9e8ce1",
        "icao9432-extracted::distress_urgency_intro_9_1_en::2fee92c222323e6a",
        "icao9432-extracted::distress_urgency_intro_9_1_en::82ee7048517d8478",
        "icao9432-extracted::distress_urgency_intro_9_1_en::8e9f7818b91d08c3",
        "icao9432-extracted::distress_urgency_intro_9_1_en::cb12c2f9b97c64b7",
        "icao9432-extracted::distress_urgency_intro_9_1_en::06f7a72397c325ac",
        "icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::5df94af7a64c3f5d",
        "icao9432-extracted::distress_messages_9_2_en::27a450fa3bfcbc0a",
        "icao9432-extracted::distress_messages_9_2_en::94e94be0c800c982",
        "icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::1de475a788206cc8",
        "icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::b95d7bb1cb409bca",
        "icao9432-extracted::communications_failure_9_5_en::f05016444e2b8808",
        "icao9432-extracted::communications_failure_9_5_en::fcb3a49672165b4f",
        "icao9432-extracted::communications_failure_9_5_en::975a63151706f68f",
        "icao9432-extracted::communications_failure_9_5_en::045c2e33f59f5ede",
        "icao9432-extracted::communications_failure_9_5_en::7900c606e05e509b",
        "icao9432-extracted::communications_failure_9_5_en::91e7d233bf3b64ff",
        "icao9432-extracted::communications_failure_9_5_en::b73dda299970c2f3",
        "icao9432-extracted::communications_failure_9_5_en::bb66a050093251c2",
        "icao9432-extracted::communications_failure_9_5_en::24c806b040f4ef5e",
        "icao9432-extracted::communications_failure_9_5_en::75055714e70d4560",
        "icao9432-extracted::distress_urgency_intro_9_1_en::87b67820c6092a98",
    )

    private val chunk08GapSpecRefGroups: List<List<SourceUnitRef>> =
        listOf(
            chunk08DistressUrgencyClassificationRefs,
            chunk08EmergencyMessagePhraseologyRefs,
            chunk08AssistanceRelayTerminationRefs,
            chunk08EmergencyDescentRefs,
            chunk08EmergencyDescentPolicyRefs,
            chunk08CommsFailureRoutingRefs,
            chunk08BlindTransmissionPhraseologyRefs,
            chunk08SsrAndBlindClearanceRefs,
            chunk08CoveredOrSplitRefs,
        )

    @Test
    fun `essential aerodrome information timing reports missing trace vocabulary`() {
        sourceUnitSpec("icao9432-essential-aerodrome-information-timing") {
            title("Essential aerodrome information is passed before taxi or final approach when not already known")
            sourceUnit(
                SourceUnitRef("icao9432-extracted::essential_aerodrome_information_4_10_en::1aa5cb7e758055bc"),
            )
            domain("information-state", setOf("already-known", "not-known"))
            domain("timing-window", setOf("before-taxi", "before-final-approach"))
            domain("hazard-kind", setOf("runway-condition", "lighting-failure", "temporary-hazard"))

            partition(
                name = "not-known runway condition before taxi",
                parameters = mapOf(
                    "information-state" to "not-known",
                    "timing-window" to "before-taxi",
                    "hazard-kind" to "runway-condition",
                ),
            ) {
                hit("hazard-authored")
                modelGap(
                    "ScenarioTrace does not yet expose an aircraft-facing essential-aerodrome-information " +
                        "message or an aircraft-known-information state.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `critical phase radio silence reports missing safety-necessity classification`() {
        sourceUnitSpec("icao9432-critical-phase-radio-silence") {
            title("Non-safety transmissions are absent during critical flight phases")
            sourceUnit(
                SourceUnitRef("icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4"),
            )
            domain("phase-window", setOf("takeoff-roll", "initial-climb", "late-final", "landing-roll"))
            domain("transmission-class", setOf("safety-necessary", "routine"))
            domain("altitude-ft", setOf("500..1500"))

            fuzz(
                name = "critical phase communication window",
                samples = 8,
                seed = 412L,
                parameters = { random, _ ->
                    val phases = listOf("takeoff-roll", "initial-climb", "late-final", "landing-roll")
                    val transmissionClasses = listOf("safety-necessary", "routine")
                    mapOf(
                        "phase-window" to phases[random.nextInt(phases.size)],
                        "transmission-class" to transmissionClasses[random.nextInt(transmissionClasses.size)],
                        "altitude-ft" to random.nextInt(500, 1501).toString(),
                    )
                },
            ) {
                hit("critical-window-sampled")
                modelGap(
                    "ScenarioTrace does not classify transmissions by safety necessity, so the spec cannot " +
                        "distinguish allowed safety calls from prohibited routine calls in critical phases.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `startup approval then engine start reports missing lifecycle evidence`() {
        sourceUnitSpec("icao9432-startup-approval-engine-start") {
            title("After ATC start-up approval, the pilot starts engines")
            sourceUnit(
                SourceUnitRef("icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::95034efc191fa9cd"),
            )
            domain("approval", setOf("StartupApproved"))
            domain("engine-start-observation", setOf("explicit-engine-start-event"))
            domain("ordering", setOf("approval-before-start"))

            partition(
                name = "approval before explicit engine start",
                parameters = mapOf(
                    "approval" to "StartupApproved",
                    "engine-start-observation" to "explicit-engine-start-event",
                    "ordering" to "approval-before-start",
                ),
            ) {
                hit("startup-workflow-required")
                modelGap(
                    "The live departure tree omits REQUEST_STARTUP/AWAIT_STARTUP_APPROVAL under D-PF.1, " +
                        "and AircraftState.engineRunning defaults true for failure/abort physics rather than " +
                        "recording an orderable engine-start lifecycle.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `powerback source unit reports missing reverse-engine manoeuvre model`() {
        sourceUnitSpec("icao9432-pushback-powerback-model-gap") {
            title("Powerback requires aircraft reverse movement using engine power")
            sourceUnit(
                SourceUnitRef("icao9432-extracted::pushback_powerback_4_3_en::1aae1f61b91984e8"),
            )
            domain("manoeuvre", setOf("powerback"))
            domain("reverse-source", setOf("engine-power"))
            domain("aircraft-type", setOf("powerback-capable", "not-powerback-capable"))

            partition(
                name = "powerback reverse movement",
                parameters = mapOf(
                    "manoeuvre" to "powerback",
                    "reverse-source" to "engine-power",
                    "aircraft-type" to "powerback-capable",
                ),
            ) {
                hit("powerback-model-required")
                modelGap(
                    "The sim has a tug-style pushback approval and typed ground-crew completion signal, " +
                        "but no aircraft reverse movement using engine power and no powerback-capable type model.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `taxi beyond runway limit reports missing compound taxi clearance semantics`() {
        sourceUnitSpec("icao9432-taxi-limit-beyond-runway-compound-clearance") {
            title("Taxi limit beyond a runway includes explicit crossing clearance or hold-short instruction")
            sourceUnit(
                SourceUnitRef("icao9432-extracted::taxi_4_4_en::1367907005a34ad1"),
            )
            domain("taxi-limit", setOf("beyond-runway"))
            domain("runway-protection", setOf("cross-clearance", "hold-short"))

            partition(
                name = "taxi limit beyond runway",
                parameters = mapOf(
                    "taxi-limit" to "beyond-runway",
                    "runway-protection" to "cross-clearance-or-hold-short",
                ),
            ) {
                hit("compound-taxi-clearance-required")
                modelGap(
                    "The current protocol has standalone CrossRunway/HoldShortOf/TaxiViaRunway leaves, " +
                        "but no typed compound taxi clearance that proves a taxi limit beyond a runway " +
                        "contains the required crossing clearance or hold-short instruction.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `atis acknowledged taxi information reports missing departure-information content model`() {
        sourceUnitSpec("icao9432-atis-acknowledged-taxi-departure-information") {
            title("ATIS acknowledgement removes need to pass departure information with taxi instructions")
            sourceUnit(
                SourceUnitRef("icao9432-extracted::taxi_4_4_en::53f33b6da4f2be58"),
            )
            domain("atis-state", setOf("acknowledged"))
            domain("departure-information", setOf("not-repeated-with-taxi"))

            partition(
                name = "atis acknowledged before taxi",
                parameters = mapOf(
                    "atis-state" to "acknowledged",
                    "departure-information" to "not-repeated-with-taxi",
                ),
            ) {
                hit("departure-information-content-required")
                modelGap(
                    "The evidence surface can observe ATIS acknowledgement, but it does not expose rendered " +
                        "or typed departure-information content on taxi instructions strongly enough to prove " +
                        "that the information was omitted because ATIS was acknowledged.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `runway vacated definition reports missing whole-aircraft holding-position geometry evidence`() {
        sourceUnitSpec("icao9432-runway-vacated-holding-position-geometry") {
            title("Runway is vacated when the entire aircraft is beyond the relevant runway-holding position")
            sourceUnit(
                SourceUnitRef("icao9432-extracted::taxi_4_4_en::eadf2541fcd51825"),
            )
            domain("aircraft-extent", setOf("entire-aircraft"))
            domain("holding-position", setOf("relevant-runway-holding-position"))

            partition(
                name = "entire aircraft beyond holding position",
                parameters = mapOf(
                    "aircraft-extent" to "entire-aircraft",
                    "holding-position" to "relevant-runway-holding-position",
                ),
            ) {
                hit("whole-aircraft-geometry-required")
                modelGap(
                    "Current traces observe RunwayVacated reports and aircraft point positions, but do not " +
                        "prove that the entire aircraft extent is beyond the relevant runway-holding position.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `conditional runway clearance reports missing dual-sighting evidence`() {
        sourceUnitSpec("icao9432-conditional-runway-clearance-dual-sighting") {
            title("Conditional runway clearances require controller and pilot sighting of concerned traffic")
            sourceUnit(
                SourceUnitRef("icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::2e598ad0323e9e2a"),
            )
            domain("movement", setOf("active-runway"))
            domain("sighting", setOf("controller-and-pilot"))
            domain("conditioned-object", setOf("aircraft", "vehicle"))

            partition(
                name = "active runway conditional clearance",
                parameters = mapOf(
                    "movement" to "active-runway",
                    "sighting" to "controller-and-pilot",
                    "conditioned-object" to "aircraft",
                ),
            ) {
                hit("dual-sighting-required")
                modelGap(
                    "Current traces can observe ConditionalClearance instructions, but do not expose typed " +
                        "evidence that both controller and pilot see the conditioned aircraft or vehicle.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `runway departure policy source units report missing operational policy concepts`() {
        sourceUnitSpec("icao9432-runway-departure-policy-gaps") {
            title("Runway departure should and may clauses require explicit operational policy concepts")
            sourceUnits(
                listOf(
                    SourceUnitRef("icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::4e0bacdd1c2c06e0"),
                    SourceUnitRef("icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::2660849403bff7de"),
                    SourceUnitRef("icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::c386a5865bdd7876"),
                    SourceUnitRef("icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::dd301daf2b69fe83"),
                    SourceUnitRef("icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::0afe0064c4c933af"),
                    SourceUnitRef("icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::2b7c45264775e3e2"),
                    SourceUnitRef("icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::2cc8caf62c15688b"),
                    SourceUnitRef("icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::6bee6c63069d8250"),
                    SourceUnitRef("icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::81490161201eb712"),
                ),
            )
            domain("policy", setOf("operational-guidance", "controller-intervention"))
            domain("trigger", setOf("poor-visibility", "traffic-development", "abandoned-takeoff", "departure-instruction"))

            partition(
                name = "policy-sensitive runway departure decision",
                parameters = mapOf(
                    "policy" to "operational-guidance",
                    "trigger" to "traffic-development",
                ),
            ) {
                hit("policy-required")
                modelGap(
                    "The source units use should/may/usually or traffic-contingency language. Current tests " +
                        "lack typed policy concepts for poor visibility, conditional-clearance identification, " +
                        "departure-instruction co-issuance, abandoned-takeoff timing, and runway-freeing " +
                        "for landing traffic.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `runway departure phraseology source units remain blocked by rendered phraseology`() {
        sourceUnitSpec("icao9432-runway-departure-phraseology-gap") {
            title("Runway departure source units that require rendered phraseology remain blocked by PHRASE-1")
            sourceUnits(
                listOf(
                    SourceUnitRef("icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::13264a6ac6d529c3"),
                    SourceUnitRef("icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::152f0ffb84869af5"),
                    SourceUnitRef("icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::42b0460ed4f07751"),
                    SourceUnitRef("icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::a93888a25f0bad03"),
                    SourceUnitRef("icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::db8a2c3dcd586b0e"),
                    SourceUnitRef("icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::9b30810984e06a35"),
                    SourceUnitRef("icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::6b5a0d8b27525cbd"),
                    SourceUnitRef("icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::8af22eb8d9795cef"),
                ),
            )
            domain("phraseology-surface", setOf("rendered-controller-utterance", "rendered-readback"))

            partition(
                name = "rendered runway departure phraseology",
                parameters = mapOf(
                    "phraseology-surface" to "rendered-controller-utterance",
                ),
            ) {
                hit("phraseology-required")
                modelGap(
                    "Typed protocol instructions exist for some runway departure actions, but this source " +
                        "set requires rendered phraseology and readback wording. That remains blocked by PHRASE-1.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `circuit arrival local procedure source units report missing policy concepts`() {
        sourceUnitSpec("icao9432-circuit-arrival-local-procedure-policy-gaps") {
            title("Circuit arrival local-procedure and traffic-dependent claims require explicit policy concepts")
            sourceUnits(
                listOf(
                    SourceUnitRef("icao9432-extracted::aerodrome_traffic_circuit_4_6_part1_en::667985b4a6159d18"),
                    SourceUnitRef("icao9432-extracted::aerodrome_traffic_circuit_4_6_part1_en::d65650486b4d1b8f"),
                    SourceUnitRef("icao9432-extracted::aerodrome_traffic_circuit_4_6_part2_en::28bea79b8da559cd"),
                    SourceUnitRef("icao9432-extracted::aerodrome_traffic_circuit_4_6_part2_en::34445db09fdd6e0a"),
                    SourceUnitRef("icao9432-extracted::aerodrome_traffic_circuit_4_6_part2_en::b64030acf6ef4bbd"),
                    SourceUnitRef("icao9432-extracted::aerodrome_traffic_circuit_4_6_part2_en::dcf776a1b9c8a303"),
                ),
            )
            domain("policy", setOf("local-procedure", "traffic-dependent-controller-intervention"))
            domain("trigger", setOf("planned-entry", "straight-in", "delay-accelerate", "routine-position-reports"))

            partition(
                name = "local procedure circuit arrival behaviour",
                parameters = mapOf(
                    "policy" to "local-procedure",
                    "trigger" to "routine-position-reports",
                ),
            ) {
                hit("local-procedure-policy-required")
                modelGap(
                    "The source units depend on local procedures, traffic situation, arrival direction, " +
                        "or controller intervention policy. Current traces can observe some position " +
                        "reports, but cannot prove which reports or circuit-entry timing local procedures require.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `final approach low pass source units report missing low approach workflow`() {
        sourceUnitSpec("icao9432-final-approach-low-pass-workflow-gap") {
            title("Low pass and training low approach claims require pilot request and controller workflow evidence")
            sourceUnits(
                listOf(
                    SourceUnitRef("icao9432-extracted::final_approach_landing_4_7_en::63836b7aef62a6f6"),
                    SourceUnitRef("icao9432-extracted::final_approach_landing_4_7_en::e17d8b9b99c43496"),
                ),
            )
            domain("workflow", setOf("low-pass-visual-inspection", "training-low-approach"))
            domain("evidence", setOf("pilot-request", "controller-clearance", "no-landing-flight-path"))

            partition(
                name = "training low approach request",
                parameters = mapOf(
                    "workflow" to "training-low-approach",
                    "evidence" to "pilot-request",
                ),
            ) {
                hit("low-approach-workflow-required")
                modelGap(
                    "Protocol has a typed ClearedLowApproach instruction, but the sim has no source-mapped " +
                        "pilot low-pass / low-approach request workflow and no scenario evidence that the " +
                        "aircraft flies along or parallel to the runway without landing.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `final and long final report source units retain residual distance timing and policy blockers`() {
        sourceUnitSpec("icao9432-final-long-final-distance-phraseology-gap") {
            title("FINAL and LONG FINAL split rows still require distance, timing, and straight-in policy evidence")
            sourceUnits(
                listOf(
                    SourceUnitRef("icao9432-extracted::final_approach_landing_4_7_en::00baaf3c55155044"),
                    SourceUnitRef("icao9432-extracted::final_approach_landing_4_7_en::4c698a5ad52a30e4"),
                    SourceUnitRef("icao9432-extracted::final_approach_landing_4_7_en::70e781a65920c075"),
                ),
            )
            domain("report", setOf("final", "long-final"))
            domain("distance-threshold", setOf("7km-4nm", "15km-8nm"))

            partition(
                name = "long final distance threshold",
                parameters = mapOf(
                    "report" to "long-final",
                    "distance-threshold" to "7km-4nm",
                ),
            ) {
                hit("distance-at-report-required")
                modelGap(
                    "fn-83 covers rendered FINAL / LONG FINAL wording only. Current traces still do not " +
                        "prove the aircraft distance from touchdown, the 7 km / 4 NM threshold, the " +
                        "about-15 km / 8 NM straight-in threshold, or straight-in procedure policy.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `circuit arrival landing phraseology source units remain blocked by rendered phraseology`() {
        sourceUnitSpec("icao9432-circuit-arrival-landing-phraseology-gap") {
            title("Circuit arrival and landing source units that require rendered phraseology remain blocked by PHRASE-1")
            sourceUnits(
                listOf(
                    SourceUnitRef("icao9432-extracted::aerodrome_traffic_circuit_4_6_part1_en::58ae778732ec6347"),
                    SourceUnitRef("icao9432-extracted::aerodrome_traffic_circuit_4_6_part1_en::a4fcedaac8a838e1"),
                    SourceUnitRef("icao9432-extracted::aerodrome_traffic_circuit_4_6_part2_en::7e3aec5e5fd60c41"),
                    SourceUnitRef("icao9432-extracted::final_approach_landing_4_7_en::1327871f46c1d348"),
                    SourceUnitRef("icao9432-extracted::final_approach_landing_4_7_en::1960f59d8b9efecb"),
                    SourceUnitRef("icao9432-extracted::final_approach_landing_4_7_en::1db805d02051bf47"),
                    SourceUnitRef("icao9432-extracted::final_approach_landing_4_7_en::7bbc96aa5ee36893"),
                    SourceUnitRef("icao9432-extracted::final_approach_landing_4_7_en::aaf5262d8e7750b2"),
                    SourceUnitRef("icao9432-extracted::final_approach_landing_4_7_en::b1c21e2f70bbf36f"),
                    SourceUnitRef("icao9432-extracted::final_approach_landing_4_7_en::fbae3a11e1d068a3"),
                ),
            )
            domain("phraseology-surface", setOf("rendered-controller-utterance", "rendered-pilot-report"))

            partition(
                name = "rendered circuit arrival landing phraseology",
                parameters = mapOf(
                    "phraseology-surface" to "rendered-controller-utterance",
                ),
            ) {
                hit("phraseology-required")
                modelGap(
                    "Typed protocol reports and instructions exist for some circuit and landing actions, " +
                        "but this source set requires rendered phraseology, example dialogue, or ATIS / " +
                        "pattern wording. That remains blocked by PHRASE-1.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `go-around non-vfr source units report missing procedure and policy evidence`() {
        sourceUnitSpec("icao9432-go-around-procedure-policy-gaps") {
            title("IFR missed approach and go-around radio brevity need explicit procedure and policy evidence")
            sourceUnits(
                listOf(
                    ICAO9432.GoAroundProcedures.InstrumentMissedApproachDefault.toSourceUnitRef(),
                    ICAO9432.GoAroundProcedures.GoAroundTransmissionBrevity.toSourceUnitRef(),
                ),
            )
            domain("operation", setOf("instrument-approach", "vfr-circuit"))
            domain("evidence", setOf("published-missed-approach", "radio-brevity-policy"))

            partition(
                name = "instrument missed approach default",
                parameters = mapOf(
                    "operation" to "instrument-approach",
                    "evidence" to "published-missed-approach",
                ),
            ) {
                hit("missed-approach-procedure-required")
                modelGap(
                    "The sim has pilot-side missed-approach task vocabulary, but no source-mapped " +
                        "end-to-end instrument approach scenario proving that the aircraft follows a " +
                        "published missed approach procedure unless ATC instructs otherwise.",
                )
            }

            partition(
                name = "go-around radio brevity",
                parameters = mapOf(
                    "operation" to "vfr-circuit",
                    "evidence" to "radio-brevity-policy",
                ),
            ) {
                hit("radio-brevity-policy-required")
                modelGap(
                    "Current traces can count transmissions in a scenario, but they do not expose a typed " +
                        "radio-load or brevity policy that makes 'brief and kept to a minimum' an auditable " +
                        "universal source claim.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `after landing frequency and taxi timing source units report missing policy evidence`() {
        sourceUnitSpec("icao9432-after-landing-policy-gaps") {
            title("After-landing frequency retention and taxi timing require explicit policy concepts")
            sourceUnits(
                listOf(
                    ICAO9432.AfterLanding.RemainTowerFrequencyUntilRunwayVacated.toSourceUnitRef(),
                    ICAO9432.AfterLanding.TaxiInstructionsAfterLandingRoll.toSourceUnitRef(),
                ),
            )
            domain("policy", setOf("frequency-retention", "taxi-instruction-timing"))
            domain("exception", setOf("otherwise-advised", "absolutely-necessary"))

            partition(
                name = "tower frequency retained until vacated",
                parameters = mapOf(
                    "policy" to "frequency-retention",
                    "exception" to "otherwise-advised",
                ),
            ) {
                hit("frequency-retention-policy-required")
                modelGap(
                    "Current traces can observe some frequency changes, but do not prove the absence of " +
                        "contrary advice or expose a policy concept for retaining tower frequency until the " +
                        "runway is vacated.",
                )
            }

            partition(
                name = "taxi instruction after landing roll",
                parameters = mapOf(
                    "policy" to "taxi-instruction-timing",
                    "exception" to "absolutely-necessary",
                ),
            ) {
                hit("taxi-timing-policy-required")
                modelGap(
                    "Current traces do not classify taxi instructions by absolute necessity, so they cannot " +
                        "turn the after-landing 'unless absolutely necessary' guidance into a universal " +
                        "covered-green assertion.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `essential aerodrome information policy rows remain blocked`() {
        sourceUnitSpec("icao9432-essential-aerodrome-information-policy-gaps") {
            title("Essential aerodrome information timing omission and open pertinence require policy evidence")
            sourceUnits(
                listOf(
                    ICAO9432.AerodromeInformation.OmitWhenKnownFromOtherSources.toSourceUnitRef(),
                    ICAO9432.AerodromeInformation.EssentialAerodromeInformationTiming.toSourceUnitRef(),
                    ICAO9432.AerodromeInformation.OtherPertinentInformation.toSourceUnitRef(),
                ),
            )
            domain("information-kind", setOf("already-known", "timing", "other-pertinent"))
            domain("aircraft-knowledge", setOf("known-from-other-source", "not-known"))

            partition(
                name = "policy-sensitive essential information decision",
                parameters = mapOf(
                    "information-kind" to "timing",
                    "aircraft-knowledge" to "not-known",
                ),
            ) {
                hit("essential-information-policy-required")
                modelGap(
                    "The typed category evidence does not prove aircraft-known-information omission policy, " +
                        "the 'whenever possible' timing policy, or open-category pertinence decisions.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `vehicle movement policy source units report missing vigilance and apron traffic models`() {
        sourceUnitSpec("icao9432-vehicle-movement-policy-model-gaps") {
            title("Vehicle vigilance and apron-traffic claims require policy and traffic-interaction models")
            sourceUnits(
                listOf(
                    ICAO9432.VehiclesAndTowing.DriverVigilanceAndCompliance.toSourceUnitRef(),
                    ICAO9432.VehiclesAndTowing.ApronProceedMayIncludeTrafficInstructions.toSourceUnitRef(),
                ),
            )
            domain("policy-surface", setOf("local-procedure-compliance", "apron-traffic-instruction"))
            domain("traffic-relation", setOf("near-aircraft", "give-way-to-apron-traffic"))

            partition(
                name = "vehicle vigilance and apron traffic policy",
                parameters = mapOf(
                    "policy-surface" to "local-procedure-compliance",
                    "traffic-relation" to "near-aircraft",
                ),
            ) {
                hit("vehicle-policy-model-required")
                modelGap(
                    "The sim has a minimal vehicle permission lifecycle, but does not model driver vigilance, " +
                        "proximity-to-aircraft compliance, local-procedure policy, or apron traffic-interaction " +
                        "instructions. Aircraft taxi state cannot prove vehicle-driver obligations.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `vehicle runway conflict source units report missing expected operation and hazard policy models`() {
        sourceUnitSpec("icao9432-vehicle-runway-conflict-model-gaps") {
            title("Vehicle runway conflict claims require hazard policy or expected aircraft operation facts")
            sourceUnits(
                listOf(
                    ICAO9432.VehiclesAndTowing.DangerousSituationStopInstruction.toSourceUnitRef(),
                    ICAO9432.VehiclesAndTowing.RunwayVehicleVacatesForAircraftOperation.toSourceUnitRef(),
                ),
            )
            domain("runway-state", setOf("vehicle-moving-on-movement-area", "vehicle-on-runway"))
            domain("aircraft-operation", setOf("landing-expected", "takeoff-expected", "none"))
            domain("intervention", setOf("dangerous-situation-stop", "vacate-for-aircraft-operation"))

            partition(
                name = "vehicle on runway requires expected aircraft operation trigger",
                parameters = mapOf(
                    "runway-state" to "vehicle-on-runway",
                    "aircraft-operation" to "landing-expected",
                    "intervention" to "vacate-for-aircraft-operation",
                ),
            ) {
                hit("vehicle-runway-conflict-policy-required")
                modelGap(
                    "The sim has vehicle runway-crossing permission and vehicle-only clear-beyond-holding-point " +
                        "evidence, but no dangerous-situation relation, intervention policy, or explicit expected " +
                        "landing/takeoff trigger for instructing a runway vehicle to vacate.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `vehicle towing source units report missing rendered vehicle phraseology`() {
        sourceUnitSpec("icao9432-vehicle-towing-phraseology-model-gaps") {
            title("Vehicle towing rendered wording remains outside the structured metadata branch")
            sourceUnit(
                ICAO9432.VehiclesAndTowing.TowRequestStatesAircraftTypeAndOperator.toSourceUnitRef(),
            )
            domain("transmission", setOf("tow-request"))
            domain("structured-metadata", setOf("aircraft-type-operator-present"))
            domain("phraseology-surface", setOf("rendered-vehicle-utterance", "not-rendered"))

            partition(
                name = "tow request rendered wording",
                parameters = mapOf(
                    "transmission" to "tow-request",
                    "structured-metadata" to "aircraft-type-operator-present",
                    "phraseology-surface" to "rendered-vehicle-utterance",
                ),
            ) {
                hit("rendered-vehicle-tow-phraseology-required")
                modelGap(
                    "The sim has structured vehicle/tow metadata and receiving-station addressing, but no " +
                        "rendered vehicle/tow phraseology surface for asserting the spoken wording.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `chunk 08 emergency source specs cite every accepted source unit exactly once`() {
        val citedRefs = chunk08GapSpecRefGroups.flatten()
        val distinctCitedRefs = citedRefs.toSet()

        assertEquals(46, citedRefs.size)
        assertEquals(
            citedRefs.size,
            distinctCitedRefs.size,
            "chunk 08 source refs must appear in exactly one grouped source spec",
        )
        assertEquals(
            ICAO9432.DistressUrgencyCommsFailure.Chunk08Items.map { source -> source.toSourceUnitRef() }.toSet(),
            distinctCitedRefs,
        )
    }

    @Test
    fun `distress and urgency classification source units report missing emergency condition model`() {
        sourceUnitSpec("icao9432-emergency-classification-model-gaps") {
            title("Distress and urgency classification claims require typed emergency condition state")
            sourceUnits(chunk08DistressUrgencyClassificationRefs)
            domain("condition-kind", setOf("annex-10-conformance", "speech-quality"))
            domain("assistance-need", setOf("immediate", "not-immediate"))
            domain("speech-quality", setOf("slow-distinct", "not-rendered"))

            partition(
                name = "distress and urgency are distinct emergency classes",
                parameters = mapOf(
                    "condition-kind" to "distress",
                    "assistance-need" to "immediate",
                    "speech-quality" to "not-rendered",
                ),
            ) {
                hit("typed-emergency-condition-required")
                modelGap(
                    "The sim has no Annex 10 emergency-procedure conformance model, and no rendered speech-quality " +
                        "evidence for slow and distinct emergency calls.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `emergency message phraseology source units report missing rendered emergency message structure`() {
        sourceUnitSpec("icao9432-emergency-message-payload-model-gaps") {
            title("Emergency message phraseology claims require rendered emergency wording and order")
            sourceUnits(chunk08EmergencyMessagePhraseologyRefs)
            domain("message-kind", setOf("distress", "urgency", "relay"))
            domain("rendered-prefix", setOf("mayday-panpan-repeated", "not-rendered"))
            domain("rendered-order", setOf("ordered-elements", "not-rendered"))

            partition(
                name = "emergency message wording and element order are rendered",
                parameters = mapOf(
                    "message-kind" to "distress",
                    "rendered-prefix" to "mayday-panpan-repeated",
                    "rendered-order" to "ordered-elements",
                ),
            ) {
                hit("emergency-message-phraseology-rendering-required")
                modelGap(
                    "The sim has structured emergency-message payload and policy evidence, but no rendered " +
                        "MAYDAY/PAN PAN repetition, emergency context/time-pressure phraseology adaptation, " +
                        "or ordered emergency-message wording.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `emergency assistance and relay source units report missing emergency traffic control`() {
        sourceUnitSpec("icao9432-emergency-assistance-relay-termination-model-gaps") {
            title("Distress any-means assistance claims require broader emergency traffic control")
            sourceUnits(chunk08AssistanceRelayTerminationRefs)
            domain("actor", setOf("station", "aircraft"))
            domain("communication-means", setOf("radio", "any-means"))
            domain("traffic-state", setOf("active-distress"))

            partition(
                name = "distress aircraft uses any means and stations assist",
                parameters = mapOf(
                    "actor" to "station",
                    "communication-means" to "any-means",
                    "traffic-state" to "active-distress",
                ),
            ) {
                hit("distress-any-means-assistance-required")
                modelGap(
                    "The sim has structured emergency assistance and SSR 7700 evidence, but no general " +
                        "any-means communication model or station assistance workflow for aircraft in distress.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `emergency descent specific instruction source unit reports missing necessity policy`() {
        sourceUnitSpec("icao9432-emergency-descent-specific-instruction-policy-gap") {
            title("Emergency descent specific-instruction claims require necessity policy")
            sourceUnits(chunk08EmergencyDescentPolicyRefs)
            domain("controller-action", setOf("general-warning", "specific-instruction"))
            domain("necessity", setOf("necessary", "not-necessary"))
            domain("policy", setOf("operational-guidance-policy"))

            partition(
                name = "specific instructions are necessary after emergency descent warning",
                parameters = mapOf(
                    "controller-action" to "specific-instruction",
                    "necessity" to "necessary",
                    "policy" to "operational-guidance-policy",
                ),
            ) {
                hit("emergency-descent-specific-instruction-necessity-policy-required")
                modelGap(
                    "The sim has structured emergency-descent safeguarding and general-warning evidence, " +
                        "but no policy evidence deciding when follow-up specific instructions are necessary.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `blind transmission phraseology source units report missing rendered wording`() {
        sourceUnitSpec("icao9432-blind-transmission-phraseology-model-gaps") {
            title("Blind transmission split rows require rendered blind phraseology")
            sourceUnits(chunk08BlindTransmissionPhraseologyRefs)
            domain("failure-mode", setOf("failed-contact", "receiver-failure"))
            domain("rendering", setOf("transmitting-blind-prefix", "receiver-failure-prefix", "addressee-wording"))
            domain("structured-branch", setOf("covered", "not-rendered"))

            partition(
                name = "blind transmission prefixes and addressees are rendered",
                parameters = mapOf(
                    "failure-mode" to "receiver-failure",
                    "rendering" to "receiver-failure-prefix",
                    "structured-branch" to "not-rendered",
                ),
            ) {
                hit("blind-transmission-rendered-phraseology-required")
                modelGap(
                    "The sim has structured blind-transmission mode, repetition, addressee, schedule, and " +
                        "continuation-intention evidence, but no rendered TRANSMITTING BLIND, addressee, " +
                        "or receiver-failure phraseology evidence.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `annex ten communications failure source unit reports missing conformance model`() {
        sourceUnitSpec("icao9432-ssr-blind-clearance-model-gaps") {
            title("Annex 10 communications-failure claims require a conformance model")
            sourceUnits(chunk08SsrAndBlindClearanceRefs)
            domain("external-standard", setOf("annex-10-volume-ii"))
            domain("communications-failure-procedure", setOf("general-rules"))
            domain("coverage-surface", setOf("local-projection", "annex-conformance"))

            partition(
                name = "local communications-failure projection does not prove Annex 10 conformance",
                parameters = mapOf(
                    "external-standard" to "annex-10-volume-ii",
                    "communications-failure-procedure" to "general-rules",
                    "coverage-surface" to "annex-conformance",
                ),
            ) {
                hit("annex-ten-communications-failure-conformance-required")
                modelGap(
                    "The local communications-failure projection covers selected ICAO 9432 structured " +
                        "branches but does not model or assert Annex 10 Volume II conformance.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    private fun chunk08Refs(vararg canonicalIds: String): List<SourceUnitRef> =
        canonicalIds.map(::chunk08Ref)

    private fun chunk08Ref(canonicalId: String): SourceUnitRef =
        ICAO9432.DistressUrgencyCommsFailure.Chunk08Items
            .single { source -> source.canonicalId == canonicalId }
            .toSourceUnitRef()
}
