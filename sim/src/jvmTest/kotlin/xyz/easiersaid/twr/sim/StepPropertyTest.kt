package xyz.easiersaid.twr.sim

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.ControllerView
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.controller.certify.CertificationContext
import xyz.easiersaid.twr.controller.certify.KotlinRunwayKernel
import xyz.easiersaid.twr.controller.certify.RunwayKernelDecision
import xyz.easiersaid.twr.controller.certify.RunwayKernelInput
import xyz.easiersaid.twr.controller.certify.RunwayKernelOperation
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.LineUpAndWait

/**
 * fn-26 Engine pass A — Kotest property tests for `step()`. Four
 * engine-level invariants over LOWG-seeded generators ([EngineGenerators]):
 *
 *  - **R3 totality** — `step()` never throws for any valid generated
 *    `(state, event)`. Generator guarantees `event.time >= state.now`
 *    (engine boundary).
 *  - **R4 monotonicity** — post-step `state.now` and `state.seq` do not
 *    retreat.
 *  - **R5 determinism** — `step(s, e) == step(s, e)`.
 *  - **R6 conditional runway-kernel preservation** — when `step()` emits
 *    a controller `TransmissionStart` carrying a runway instruction
 *    (`LineUpAndWait` / `ClearedForTakeoff` / `ClearedToLand`),
 *    re-evaluate via `KotlinRunwayKernel.evaluate(...)` and assert
 *    `Accepted`. `Rejected` here means controller and kernel disagree —
 *    a real defect. Includes an explicit non-vacuity gate (≥5% of
 *    generated cases reach the kernel-call branch) per epic R6.
 *
 * **R7**: 500 iterations default; override via system property
 * `twr.stepPropertyIterations` (project-namespaced to avoid Kotest's
 * own `kotest.framework.*` keys).
 */
@OptIn(ExperimentalKotest::class)
class StepPropertyTest : FunSpec({

    val iterations = System.getProperty("twr.stepPropertyIterations")?.toIntOrNull() ?: DEFAULT_ITERATIONS
    // `PropTestConfig(iterations = ...)` is ExperimentalKotest in 5.9.x.
    val propConfig = PropTestConfig(iterations = iterations)

    test("R3 totality: step() never throws for any valid (state, event)") {
        checkAll(propConfig, EngineGenerators.arbInitialStatePlusEvent()) { (state, event) ->
            shouldNotThrowAny { step(state, event) }
        }
    }

    test("R4 monotonicity: post-step now and seq do not retreat") {
        checkAll(propConfig, EngineGenerators.arbInitialStatePlusEvent()) { (state, event) ->
            val (newState, _) = step(state, event)
            newState.now shouldBeGreaterThanOrEqualTo state.now
            newState.seq shouldBeGreaterThanOrEqualTo state.seq
        }
    }

    test("R5 determinism: step(s, e) == step(s, e)") {
        checkAll(propConfig, EngineGenerators.arbInitialStatePlusEvent()) { (state, event) ->
            val first = step(state, event)
            val second = step(state, event)
            first shouldBe second
        }
    }

    test("R6 conditional runway-kernel preservation (non-vacuous)") {
        // Per-case (not per-instruction) counter for non-vacuity, so a
        // few iterations emitting several instructions can't mask sparse
        // coverage. Threshold 5%; below that, R6 is vacuous and the
        // post-filing seed isn't reaching the kernel-call branch.
        var totalCases = 0
        var kernelCaseHits = 0
        checkAll(propConfig, EngineGenerators.arbPostFilingStatePlusEvent()) { (state, event) ->
            totalCases++
            val (newState, emitted) = step(state, event)
            val runwayInstructions = extractRunwayInstructions(emitted)
            if (runwayInstructions.isNotEmpty()) {
                kernelCaseHits++
            }
            for ((controllerId, instruction) in runwayInstructions) {
                val input = buildRunwayKernelInput(newState, controllerId, instruction)
                val decision = KotlinRunwayKernel.evaluate(input)
                decision.shouldBeInstanceOf<RunwayKernelDecision.Accepted>()
            }
        }
        val threshold = totalCases / 20  // 5%
        check(kernelCaseHits >= threshold) {
            "R6 vacuous: only $kernelCaseHits of $totalCases generated cases " +
                "(<5%) reached the runway-kernel-call branch. Surface as a " +
                "planning defect; do not relax the threshold. (threshold=$threshold)"
        }
    }
}) {
    companion object {
        /** R7: 500-iteration default cap (≤60s budget with fixture-backed generators). */
        internal const val DEFAULT_ITERATIONS = 500
    }
}

/**
 * Extract `(controllerId, runway-instruction)` pairs from a step's
 * emissions. Order-preserving so the R6 assertion loop iterates
 * deterministically. Pairs carry the emitter so the belief projection
 * downstream knows which controller's beliefs to read.
 */
private fun extractRunwayInstructions(
    emitted: List<SimEvent>,
): List<Pair<ControllerId, AtcInstruction>> = emitted.mapNotNull { ev ->
    val tx = (ev as? SimEvent.TransmissionStart)?.transmission ?: return@mapNotNull null
    val speaker = tx.speaker as? SpeakerRef.Controller ?: return@mapNotNull null
    val utterance = tx.utterance as? Utterance.FromController ?: return@mapNotNull null
    val output = utterance.output as? ControllerOutput.Instruct ?: return@mapNotNull null
    val instruction = when (val dispatch = output.dispatch) {
        is Dispatch.Direct -> dispatch.instruction
        is Dispatch.Conditional -> dispatch.instruction
    }
    when (instruction) {
        is LineUpAndWait, is ClearedForTakeoff, is ClearedToLand -> speaker.id to instruction
        else -> null
    }
}

/**
 * Build a `RunwayKernelInput` from the post-step state, sourced from
 * the emitting controller's `BeliefState` (`activeRunway` / `runwayDuty`
 * / `runwayBeliefs` / `trackedAircraft`). The controller's emission
 * gates on these same primitives → kernel re-evaluation should `Accept`.
 *
 * The `view` is a `CertificationContext` shape requirement;
 * `KotlinRunwayKernel.evaluate(...)` reads only `context.beliefs`.
 */
private fun buildRunwayKernelInput(
    state: SimState,
    controllerId: ControllerId,
    instruction: AtcInstruction,
): RunwayKernelInput {
    val operation = runwayKernelOperationFor(instruction)
        ?: error("buildRunwayKernelInput: $instruction is not a runway-kernel operation")
    val beliefs = state.beliefs[controllerId]
        ?: error("buildRunwayKernelInput: controller $controllerId has no beliefs in post-state")
    val controllerSpec = state.controllers[controllerId]
        ?: error("buildRunwayKernelInput: controller $controllerId not in state.controllers")
    val view = ControllerView(
        time = state.now,
        controllerId = controllerId,
        role = controllerSpec.role,
        aerodromeId = controllerSpec.aerodromeId,
        responsibilities = controllerSpec.responsibilities.keys,
        aircraft = beliefs.trackedAircraft,
        runways = beliefs.runwayBeliefs,
        activeClearances = beliefs.issuedClearances,
        receivedMessages = emptyList(),
        weather = state.world.aerodromes[controllerSpec.aerodromeId]?.weather,
        worldIndex = state.worldIndex,
    )
    return RunwayKernelInput(
        operation = operation,
        context = CertificationContext(
            view = view,
            beliefs = beliefs,
            world = state.world,
            decisionTime = state.now,
        ),
    )
}

/**
 * Mirror of the internal `controller.certify.runwayKernelOperationFor`
 * — reproduced rather than re-exported so this test doesn't widen the
 * certify package's public surface for a single consumer.
 */
private fun runwayKernelOperationFor(instruction: AtcInstruction): RunwayKernelOperation? =
    when (instruction) {
        is LineUpAndWait -> RunwayKernelOperation.LineUpAndWait(instruction.target, instruction.runway)
        is ClearedForTakeoff -> RunwayKernelOperation.ClearedForTakeoff(instruction.target, instruction.runway)
        is ClearedToLand -> RunwayKernelOperation.ClearedToLand(instruction.target, instruction.runway)
        else -> null
    }
