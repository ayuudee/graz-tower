package xyz.easiersaid.twr.sim

import kotlin.test.Test

class Icao9432ModelGapSourceUnitSpecTest {
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
    fun `pushback and powerback source units report missing actor and manoeuvre model`() {
        sourceUnitSpec("icao9432-pushback-powerback-model-gap") {
            title("Pushback and powerback require aircraft manoeuvre, ground-crew, and local-procedure actors")
            sourceUnits(
                listOf(
                    SourceUnitRef("icao9432-extracted::pushback_powerback_4_3_en::1aae1f61b91984e8"),
                    SourceUnitRef("icao9432-extracted::pushback_powerback_4_3_en::5980a8f786170b01"),
                    SourceUnitRef("icao9432-extracted::pushback_powerback_4_3_en::b3652213a568f55f"),
                ),
            )
            domain("manoeuvre", setOf("pushback", "powerback"))
            domain("responsible-party", setOf("atc", "apron-management", "ground-crew", "local-procedure-dependent"))
            domain("completion-signal", setOf("visual-ground-crew-signal"))

            partition(
                name = "pushback completion before taxi",
                parameters = mapOf(
                    "manoeuvre" to "pushback",
                    "responsible-party" to "local-procedure-dependent",
                    "completion-signal" to "visual-ground-crew-signal",
                ),
            ) {
                hit("pushback-actor-model-required")
                modelGap(
                    "The sim has no pushback/powerback manoeuvre lifecycle, no apron-management actor, " +
                        "and no ground-crew visual completion signal before taxi.",
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
    fun `final and long final report source units report missing distance and rendered phraseology evidence`() {
        sourceUnitSpec("icao9432-final-long-final-distance-phraseology-gap") {
            title("FINAL and LONG FINAL source units require rendered reports and distance-at-report evidence")
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
                    "Current traces expose typed Final / LongFinal reports, but do not prove the rendered " +
                        "report wording or the aircraft distance from touchdown at the report threshold.",
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
                    SourceUnitRef("icao9432-extracted::final_approach_landing_4_7_en::a4c8fffd8a61adb4"),
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
    fun `essential aerodrome information categories report missing typed information model`() {
        sourceUnitSpec("icao9432-essential-aerodrome-information-category-gaps") {
            title("Essential aerodrome information category source units require typed hazard and facility models")
            sourceUnits(
                listOf(
                    ICAO9432.AerodromeInformation.WaterOnMovementArea.toSourceUnitRef(),
                    ICAO9432.AerodromeInformation.OmitWhenKnownFromOtherSources.toSourceUnitRef(),
                    ICAO9432.AerodromeInformation.Definition.toSourceUnitRef(),
                    ICAO9432.AerodromeInformation.EssentialAerodromeInformationTiming.toSourceUnitRef(),
                    ICAO9432.AerodromeInformation.RoughOrBrokenSurfaces.toSourceUnitRef(),
                    ICAO9432.AerodromeInformation.ConstructionOrMaintenance.toSourceUnitRef(),
                    ICAO9432.AerodromeInformation.SnowBanksOrDrifts.toSourceUnitRef(),
                    ICAO9432.AerodromeInformation.OtherTemporaryHazards.toSourceUnitRef(),
                    ICAO9432.AerodromeInformation.LightingSystemFailure.toSourceUnitRef(),
                    ICAO9432.AerodromeInformation.SnowSlushOrIce.toSourceUnitRef(),
                    ICAO9432.AerodromeInformation.OtherPertinentInformation.toSourceUnitRef(),
                ),
            )
            domain("information-kind", setOf("surface-condition", "temporary-hazard", "facility-serviceability"))
            domain("aircraft-knowledge", setOf("known-from-other-source", "not-known"))

            partition(
                name = "movement area condition information",
                parameters = mapOf(
                    "information-kind" to "surface-condition",
                    "aircraft-knowledge" to "not-known",
                ),
            ) {
                hit("typed-essential-information-required")
                modelGap(
                    "The sim does not yet expose typed movement-area condition, temporary-hazard, " +
                        "facility-serviceability, aircraft-known-information, or pertinence policy evidence " +
                        "for essential aerodrome information.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `vehicle movement permission source units report missing vehicle actor lifecycle`() {
        sourceUnitSpec("icao9432-vehicle-movement-permission-model-gaps") {
            title("Vehicle movement permission claims require vehicle actors, positions, and proceed lifecycle")
            sourceUnits(
                listOf(
                    ICAO9432.VehiclesAndTowing.DriverVigilanceAndCompliance.toSourceUnitRef(),
                    ICAO9432.VehiclesAndTowing.HoldPositionRequiresCallbackPermission.toSourceUnitRef(),
                    ICAO9432.VehiclesAndTowing.ApronProceedMayIncludeTrafficInstructions.toSourceUnitRef(),
                    ICAO9432.VehiclesAndTowing.StopAtLimitThenRequestFurtherPermission.toSourceUnitRef(),
                    ICAO9432.VehiclesAndTowing.StandbyRequiresPermissionBeforeProceeding.toSourceUnitRef(),
                ),
            )
            domain("vehicle-state", setOf("standby", "hold-position", "proceeding", "stopped-at-limit"))
            domain("permission", setOf("not-yet-given", "callback-given", "traffic-conditioned"))
            domain("local-procedure", setOf("required", "not-modelled"))

            partition(
                name = "hold position requires callback permission",
                parameters = mapOf(
                    "vehicle-state" to "hold-position",
                    "permission" to "not-yet-given",
                    "local-procedure" to "not-modelled",
                ),
            ) {
                hit("vehicle-actor-lifecycle-required")
                modelGap(
                    "The sim has no vehicle actor, vehicle position/destination state, vehicle proceed/hold " +
                        "lifecycle, driver acknowledgement, or local-procedure compliance model. Aircraft taxi " +
                        "state cannot prove vehicle-driver obligations.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `vehicle runway crossing and vacating source units report missing vehicle runway model`() {
        sourceUnitSpec("icao9432-vehicle-runway-crossing-vacating-model-gaps") {
            title("Vehicle runway crossing and vacating claims require vehicle runway occupancy and geometry")
            sourceUnits(
                listOf(
                    ICAO9432.VehiclesAndTowing.DangerousSituationStopInstruction.toSourceUnitRef(),
                    ICAO9432.VehiclesAndTowing.RunwayCrossingRequiresPermissionAndAcknowledgement.toSourceUnitRef(),
                    ICAO9432.VehiclesAndTowing.RunwayVehicleVacatesForAircraftOperation.toSourceUnitRef(),
                    ICAO9432.VehiclesAndTowing.RunwayVacatedReportAfterVehicleTowClear.toSourceUnitRef(),
                ),
            )
            domain("runway-state", setOf("vehicle-holding-short", "vehicle-crossing", "vehicle-on-runway"))
            domain("aircraft-operation", setOf("landing-expected", "takeoff-expected", "none"))
            domain("clearance-evidence", setOf("permission-and-acknowledgement", "vacated-beyond-holding-point"))

            partition(
                name = "vehicle runway crossing requires permission and acknowledgement",
                parameters = mapOf(
                    "runway-state" to "vehicle-crossing",
                    "aircraft-operation" to "none",
                    "clearance-evidence" to "permission-and-acknowledgement",
                ),
            ) {
                hit("vehicle-runway-permission-required")
                modelGap(
                    "The sim has no vehicle runway-crossing permission, driver acknowledgement, vehicle " +
                        "runway occupancy, aircraft-operation conflict rule, or vehicle/tow extent geometry " +
                        "for proving clearance beyond a holding point.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }

    @Test
    fun `vehicle towing and first-call source units report missing tow metadata and rendered vehicle phraseology`() {
        sourceUnitSpec("icao9432-vehicle-towing-phraseology-model-gaps") {
            title("Vehicle first-call and towing claims require vehicle transmissions, tow metadata, and rendered wording")
            sourceUnits(
                listOf(
                    ICAO9432.VehiclesAndTowing.FirstCallIdentifiesVehicleRoute.toSourceUnitRef(),
                    ICAO9432.VehiclesAndTowing.TowDriverMustNotAssumeStationAware.toSourceUnitRef(),
                    ICAO9432.VehiclesAndTowing.TowRequestStatesAircraftTypeAndOperator.toSourceUnitRef(),
                ),
            )
            domain("transmission", setOf("vehicle-first-call", "tow-request"))
            domain("metadata", setOf("call-sign-position-destination-route", "aircraft-type-operator"))
            domain("phraseology-surface", setOf("rendered-vehicle-utterance", "not-rendered"))

            partition(
                name = "tow request states aircraft type and operator",
                parameters = mapOf(
                    "transmission" to "tow-request",
                    "metadata" to "aircraft-type-operator",
                    "phraseology-surface" to "rendered-vehicle-utterance",
                ),
            ) {
                hit("vehicle-transmission-and-tow-metadata-required")
                modelGap(
                    "The sim has no vehicle transmission actor, vehicle call sign, vehicle position/destination/" +
                        "route fields, aircraft-under-tow metadata, receiving-station tow-awareness state, or " +
                        "rendered vehicle/tow phraseology.",
                )
            }
        }.assertSatisfied().assertHasModelGap()
    }
}
