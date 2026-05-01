package xyz.easiersaid.twr.pilot

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.reflect.KClass
import kotlin.test.Test
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.ControllerResponse

/**
 * Architectural enforcement test (Pass 1, Item 4) — leaf-level exhaustiveness
 * over [AtcInstruction] at the three call sites that handle instructions.
 *
 * The Kotlin compiler enforces leaf coverage on a sealed `when (instruction)`
 * — but only if no arm absorbs leaves via a sealed sub-interface
 * (`is GroundInstruction -> ...`). Many [AtcInstruction] leaves implement
 * multiple sub-interfaces (the diamond hierarchy), so a category arm silently
 * absorbs unrelated leaves. A new instruction subtype added to one of those
 * categories gets the absorbed behaviour without compiler warning.
 *
 * **This test's job is anti-regression**: it forbids re-introduction of
 * category-arm absorption at the three call sites. Adding a category-only arm
 * here is detected and named.
 *
 * Three call sites:
 *  - [xyz.easiersaid.twr.pilot.processInstruction] (mission-tree dispatch)
 *  - [xyz.easiersaid.twr.pilot.updateActiveRunwayFromInstruction] (runway extraction)
 *  - `xyz.easiersaid.twr.sim.applyPilotHeardInstruction` (Step.kt — sim-side effect)
 *
 * The leaf set comes from JVM reflection (`AtcInstruction::class.sealedSubclasses`
 * traversed transitively). Source-text scan determines which `is X` arms each
 * function declares.
 *
 * **No-suppression rule** (per Pass 1 plan, A5): an architectural test failure
 * is never resolved by `@Disabled`, `@Suppress`, or test removal. Resolve by
 * adding the missing leaf arm, or by formally amending the plan if the
 * exhaustiveness contract is being relaxed.
 */
class ExhaustivenessTest {

    /**
     * The set of category interfaces between [AtcInstruction] and concrete leaves.
     * If any of these names appears as `is <CategoryName>` in a call-site function,
     * we flag it as category absorption (forbidden).
     */
    private val atcInstructionCategoryInterfaces = setOf(
        "Clearance", "GroundInstruction", "RunwayInstruction", "RouteInstruction",
        "VectorInstruction", "LevelInstruction", "SpeedInstruction", "ApproachInstruction",
        "ReportInstruction", "FrequencyInstruction", "SurveillanceInstruction",
        "SequencingInstruction", "AerodromeInstruction", "EmergencyInstruction",
        // Pass 6 (D-PF.6): the new TaxiClearance sealed parent of
        // TaxiToHoldingPoint and TaxiToStand. Pilot-side per-leaf coverage
        // is preserved; an `is TaxiClearance` arm would absorb both leaves
        // and silently regress the runway-vs-stand distinction.
        "TaxiClearance",
    )

    @Test
    fun `processInstruction matches every AtcInstruction leaf, no category absorption`() {
        verifyCallSite(
            file = "pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotCognitive.kt",
            functionName = "processInstruction",
            sealedRoot = AtcInstruction::class,
            categoryInterfaces = atcInstructionCategoryInterfaces,
            expectedLeafCount = ATC_INSTRUCTION_LEAF_COUNT,
        )
    }

    @Test
    fun `runwayFromInstruction matches every AtcInstruction leaf, no category absorption`() {
        // Pass 2 (D-PF.4 closure) extracted the per-leaf runway dispatch from
        // updateActiveRunwayFromInstruction (now a one-liner) into runwayFromInstruction
        // returning Option<RunwayId>. The leaf-coverage contract is unchanged; the
        // function name moved.
        verifyCallSite(
            file = "pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotCognitive.kt",
            functionName = "runwayFromInstruction",
            sealedRoot = AtcInstruction::class,
            categoryInterfaces = atcInstructionCategoryInterfaces,
            expectedLeafCount = ATC_INSTRUCTION_LEAF_COUNT,
        )
    }

    @Test
    fun `applyPilotHeardInstruction matches every AtcInstruction leaf, no category absorption`() {
        verifyCallSite(
            file = "sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/Step.kt",
            functionName = "applyPilotHeardInstruction",
            sealedRoot = AtcInstruction::class,
            categoryInterfaces = atcInstructionCategoryInterfaces,
            expectedLeafCount = ATC_INSTRUCTION_LEAF_COUNT,
        )
    }

    @Test
    fun `processControllerResponse matches every ControllerResponse leaf, no category absorption`() {
        // Pass 3 (Item 5): pilot-side handler for ControllerOutput.Respond. The
        // ControllerResponse hierarchy is flat — no intermediate sealed sub-interfaces —
        // so categoryInterfaces is empty (the category-arm check is structurally a no-op).
        verifyCallSite(
            file = "pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotCognitive.kt",
            functionName = "processControllerResponse",
            sealedRoot = ControllerResponse::class,
            categoryInterfaces = emptySet(),
            expectedLeafCount = CONTROLLER_RESPONSE_LEAF_COUNT,
        )
    }

    private fun verifyCallSite(
        file: String,
        functionName: String,
        sealedRoot: KClass<out Any>,
        categoryInterfaces: Set<String>,
        expectedLeafCount: Int,
    ) {
        val leaves = sealedLeaves(sealedRoot)
        // Pass 6 (FP review S.1): exact-equals leaf-count check (was a soft
        // floor `>= minLeafCount`). Adding a leaf forces the test update to
        // surface in PR review, rather than drifting silently under the
        // floor.
        check(leaves.size == expectedLeafCount) {
            "${sealedRoot.simpleName} sealed traversal found ${leaves.size} leaves; " +
                "expected exactly $expectedLeafCount. If a leaf was added, update the " +
                "expected count in ExhaustivenessTest's companion. If a leaf was removed, " +
                "update both the count and the call-sites."
        }

        val source = projectRoot().resolve(file).toFile().readText()
        val functionBody = extractFunctionBody(source, functionName)
            ?: throw AssertionError(
                "Could not locate function `$functionName` in $file. " +
                    "If the function moved or was renamed, update ExhaustivenessTest accordingly."
            )

        // Forbidden: `is <CategoryName>` appearing as a when-arm. The diamond
        // hierarchy means category arms silently absorb leaves.
        val categoryArms = categoryInterfaces.filter { name ->
            Regex("""\bis\s+$name\b""").containsMatchIn(functionBody)
        }
        check(categoryArms.isEmpty()) {
            "EXHAUSTIVENESS VIOLATION: $functionName contains category-arm matches " +
                "$categoryArms. Category arms absorb leaves silently due to the diamond " +
                "hierarchy (many leaves implement multiple sealed sub-interfaces). " +
                "Replace category arms with explicit per-leaf `is <LeafName> -> ...` arms."
        }

        // Required: every leaf appears as `is <LeafName>` in the function body.
        val missing = leaves.filterNot { leaf ->
            Regex("""\bis\s+${leaf.simpleName}\b""").containsMatchIn(functionBody)
        }
        check(missing.isEmpty()) {
            "EXHAUSTIVENESS VIOLATION: $functionName is missing arms for ${sealedRoot.simpleName} leaves: " +
                "${missing.map { it.simpleName }}. Each leaf must have an explicit `is <Leaf> -> ...` " +
                "arm. Adding a new ${sealedRoot.simpleName} subtype requires extending this call site."
        }
    }

    /**
     * Walk a sealed root's subclasses transitively and collect concrete leaves.
     * A "leaf" is a concrete (non-sealed, non-abstract) class — `data class`, `data object`,
     * or `object`.
     */
    private fun sealedLeaves(root: KClass<out Any>): Set<KClass<*>> {
        val seen = mutableSetOf<KClass<*>>()
        val leaves = mutableSetOf<KClass<*>>()
        fun walk(k: KClass<*>) {
            if (!seen.add(k)) return
            val subs = k.sealedSubclasses
            if (subs.isEmpty()) {
                // Concrete leaf if not abstract / not sealed itself.
                if (!k.isAbstract && !k.isSealed) leaves.add(k)
            } else {
                subs.forEach { walk(it) }
            }
        }
        walk(root)
        return leaves
    }

    /**
     * Extract the body of [functionName] from [source]. Returns the substring from the
     * function's opening line to the matching closing brace (computed by brace counting).
     * Returns null if the function isn't found.
     */
    private fun extractFunctionBody(source: String, functionName: String): String? {
        val pattern = Regex("""(?m)^\s*(@\w[\w."(),\s]*\s+)?(public\s+|private\s+|internal\s+)?(suspend\s+)?fun\s+(\w+\.)?$functionName\s*\(""")
        val match = pattern.find(source) ?: return null
        // Find the opening brace `{` after the signature, skipping the parameter list and return type.
        var i = match.range.last
        var parenDepth = 0
        while (i < source.length) {
            when (source[i]) {
                '(' -> parenDepth++
                ')' -> parenDepth--
                '{' -> if (parenDepth == 0) {
                    // Body starts here. Find matching close brace.
                    val start = i
                    var depth = 0
                    var j = i
                    while (j < source.length) {
                        when (source[j]) {
                            '{' -> depth++
                            '}' -> {
                                depth--
                                if (depth == 0) return source.substring(start, j + 1)
                            }
                        }
                        j++
                    }
                    return null
                }
                '=' -> if (parenDepth == 0 && i + 1 < source.length && source[i + 1] != '=' && source[i + 1] != '>') {
                    // Expression body `fun foo(...): T = expr` — read until end of expression
                    // (heuristic: until a blank line at column 0 or end of file).
                    val start = i + 1
                    var j = start
                    var braceDepth = 0
                    while (j < source.length) {
                        when (source[j]) {
                            '{' -> braceDepth++
                            '}' -> braceDepth--
                            '\n' -> if (braceDepth == 0 && j + 1 < source.length && (source[j + 1] == '\n' || source[j + 1] == '@' ||
                                    Regex("""^[a-zA-Z@/]""").containsMatchIn(source.substring(j + 1, minOf(j + 2, source.length))))) {
                                // Heuristic stop: blank line or new declaration at column 0.
                                if (source.substring(j + 1).trimStart().startsWith("fun ") ||
                                    source.substring(j + 1).trimStart().startsWith("private fun ") ||
                                    source.substring(j + 1).trimStart().startsWith("internal fun ") ||
                                    source.substring(j + 1).trimStart().startsWith("@")) {
                                    return source.substring(start, j)
                                }
                            }
                        }
                        j++
                    }
                    return source.substring(start)
                }
            }
            i++
        }
        return null
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }

    companion object {
        /**
         * Concrete-leaf count of [AtcInstruction]'s sealed hierarchy.
         *
         * Pass 5: 98. Pass 6 (D-PF.6): split `TaxiTo` (−1) into
         * `TaxiToHoldingPoint` (+1) and `TaxiToStand` (+1) ⇒ 99.
         *
         * The exact-equals check (FP review S.1) means any future leaf
         * addition fails this test — bump the constant in the same PR.
         */
        const val ATC_INSTRUCTION_LEAF_COUNT: Int = 99

        /** Concrete-leaf count of [ControllerResponse]'s sealed hierarchy. */
        const val CONTROLLER_RESPONSE_LEAF_COUNT: Int = 12
    }
}
