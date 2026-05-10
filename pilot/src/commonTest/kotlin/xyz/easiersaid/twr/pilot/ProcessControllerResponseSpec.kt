package xyz.easiersaid.twr.pilot

import arrow.core.None
import arrow.core.Some
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtomDefect
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedToLandReadback
import xyz.easiersaid.twr.protocol.ReadBackCorrect
import xyz.easiersaid.twr.protocol.ReadbackCorrection
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import arrow.core.NonEmptyList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Pass 3, Item 4 — pilot-side reaction contract for [processControllerResponse].
 *
 * Per the no-corners testing principle (real-job tests only), this spec covers
 * only the **non-trivial** reactions. The other 10 [ControllerResponse] leaves
 * (`Standby`, `Identified`, `NotIdentified`, etc.) are covered structurally by
 * `ExhaustivenessTest`'s fourth row — that's the architectural anti-regression
 * contract. Tautological per-leaf rows asserting `mission == mission` are
 * scaffold and dropped.
 *
 * If a future leaf gains non-trivial behaviour (e.g. Pass 11 introduces a
 * `lastTrafficAdvisory` belief slice consumed by [TrafficInformation]), the
 * **new** behaviour earns its own spec row at that point.
 */
class ProcessControllerResponseSpec {

    private val aircraftId = AircraftId("OE-TST")
    private val rwy = RunwayId("16")

    private val freshMission: PilotMission = createMission(
        goal = HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop)),
        startPhase = PilotPhase.AtStand,
        time = SimTime.ZERO,
    )

    @Test
    fun `ReadBackCorrect leaves mission unchanged and emits no transmission`() {
        val response = ReadBackCorrect(target = aircraftId)
        val reaction = processControllerResponse(response, freshMission)
        assertEquals(freshMission, reaction.mission, "Mission must be unchanged for ReadBackCorrect")
        assertEquals(None, reaction.transmission, "Pilot is silent on ReadBackCorrect — controller already heard the readback")
    }

    @Test
    fun `ReadbackCorrection emits a Readback for the corrected instruction`() {
        // The controller said "NEGATIVE, I SAY AGAIN, …" with the corrected
        // ClearedToLand. Pilot retransmits a readback whose atoms match
        // requiredReadbackAtoms(corrected). The mission is unchanged — the
        // original instruction was already processed; the correction is a
        // verification round-trip, not a re-execution.
        val correctedInstruction = ClearedToLand(target = aircraftId, runway = rwy)
        val response = ReadbackCorrection(
            target = aircraftId,
            correct = correctedInstruction,
            defects = NonEmptyList(AtomDefect.MissingAtom(ClearedToLandReadback(rwy)), emptyList()),
        )
        val reaction = processControllerResponse(response, freshMission)
        assertEquals(freshMission, reaction.mission, "Mission must be unchanged for ReadbackCorrection")
        when (val tx = reaction.transmission) {
            is Some -> {
                val readback = tx.value as? xyz.easiersaid.twr.protocol.Readback
                    ?: fail("Expected Readback transmission, got ${tx.value::class.simpleName}")
                // The readback must carry the safety-critical atom for ClearedToLand
                // (the runway identifier).
                val carriesRunwayAtom = readback.elements.any {
                    it is xyz.easiersaid.twr.protocol.SimpleElement && it.value is ClearedToLandReadback
                }
                assertEquals(true, carriesRunwayAtom,
                    "Corrected readback must carry the ClearedToLandReadback atom; got ${readback.elements}")
            }
            is None -> fail("Expected Some(Readback) transmission for ReadbackCorrection, got None")
        }
    }
}
