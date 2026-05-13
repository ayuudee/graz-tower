package xyz.easiersaid.twr.protocol

import kotlin.test.Ignore
import kotlin.test.Test

/**
 * `@Ignore`d placeholder tests pinning eventual contracts for deferred work
 * in `:protocol`. Mirrors the `:pilot` and `:controller` patterns; see
 * `docs/deferments.md` for the canonical register and
 * `docs/deferments-CONVENTION.md` for the four-bucket model.
 *
 * When a deferment is picked up, the implementer flips `@Ignore` off,
 * uncomments / extends the body, and the test becomes a real verification
 * of the contract.
 */
class DeferredContractsSpec {

    /**
     * **D-AUDIT.8.III-FOLLOWUP** — voice-style ATIS rendering
     * (`Atis.toMessage()`).
     *
     * When implemented:
     *  - `Atis.toMessage(): AtisMessage` renders the structured ATIS
     *    record as a voice-shaped string (or `AtisMessage` value class)
     *    suitable for radio broadcast.
     *  - Phrasing follows CAP 413 / ICAO Annex 11 §4.3.6 broadcast
     *    conventions.
     *
     * Bucket 2 — `Atis.toMessage()` does not exist; today the structured
     * record is consumed directly by the rule layer (no voice render).
     */
    @Ignore
    @Test
    fun `D-AUDIT8-III Atis carries a toMessage rendering for voice broadcast`() {
        // Bucket 2: no `toMessage()` on `Atis` today.
        // TODO when D-AUDIT.8.III-FOLLOWUP lands — requires `Atis.toMessage()`
        // (or `AtisMessage` value class) on the protocol surface.
        //   val atis = Atis(
        //       letter = 'A',
        //       aerodrome = AerodromeId("LOWG"),
        //       configuration = RunwayConfiguration(arrivals = listOf(RunwayId("16C")), departures = listOf(RunwayId("16C"))),
        //       wind = Wind(direction = 160, speedKts = 8),
        //       qnh = PressureSetting.hpa(1013),
        //       visibility = 10000,
        //       generatedAt = SimTime.zero,
        //   )
        //   val message: AtisMessage = atis.toMessage()
        //   assertTrue("LOWG INFORMATION ALPHA" in message.text)
        //   assertTrue("RUNWAY 16C IN USE" in message.text)
    }

    /**
     * **D-WORLD.1** — `Aerodrome.runwayConfiguration` field in
     * world-candidate JSON (CAD-authoring pass).
     *
     * When implemented:
     *  - `CandidateAerodrome` (in `:migration/world/WorldCandidateSchema.kt`)
     *    gains a `runwayConfiguration: CandidateRunwayConfiguration?` field
     *    naming the published arrivals / departures bucket per aerodrome.
     *  - `WorldCandidateLoader.toWorld` populates a corresponding
     *    `Aerodrome.runwayConfiguration: RunwayConfiguration?` field.
     *  - ATIS publishing reads this published configuration rather than
     *    deriving from wind alone.
     *
     * Bucket 2 — neither schema field nor protocol field exists today;
     * `RunwayConfiguration` lives as a controller-side selection output
     * only.
     */
    @Ignore
    @Test
    fun `D-WORLD1 Aerodrome carries a published runwayConfiguration field`() {
        // Bucket 2: no `Aerodrome.runwayConfiguration` field today.
        // TODO when D-WORLD.1 lands — requires schema field on
        // `CandidateAerodrome` + loader population to `Aerodrome.runwayConfiguration`.
        //   val aerodrome = Aerodrome(
        //       id = AerodromeId("LOWG"),
        //       // ... existing fields ...
        //       runwayConfiguration = RunwayConfiguration(
        //           arrivals = listOf(RunwayId("16C")),
        //           departures = listOf(RunwayId("16C")),
        //       ),
        //   )
        //   assertEquals(listOf(RunwayId("16C")), aerodrome.runwayConfiguration?.arrivals)
    }
}
