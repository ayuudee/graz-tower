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
}
