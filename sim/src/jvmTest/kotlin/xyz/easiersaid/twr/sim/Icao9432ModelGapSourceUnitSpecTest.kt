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
}
