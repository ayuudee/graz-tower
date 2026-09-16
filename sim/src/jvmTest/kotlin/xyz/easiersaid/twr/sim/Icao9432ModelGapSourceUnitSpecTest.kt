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
}
