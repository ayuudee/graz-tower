package xyz.easiersaid.twr.controller

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.reflect.KClass
import kotlin.test.Test
import xyz.easiersaid.twr.controller.observe.CoordinationState

/**
 * Pass 9 (D-AUDIT.2) — leaf-level exhaustiveness over [CoordinationState]
 * at the controller-side functions that destructure it.
 *
 * Sealed `CoordinationState` consumers must list every leaf as an
 * explicit `is CoordinationState.<Leaf>` arm:
 *  - `advanceState` (private to `Readback.kt`'s `escalateOverdueCoordinations`)
 *  - `coordinationEscalationOutputs` in `CoordinationEscalation.kt`
 *  - `markCoordinationEscalationsEmitted` in `CoordinationEscalation.kt`
 *
 * Adding a new leaf must extend every consumer. This test pins the
 * contract: any new leaf without a corresponding `is` arm fails the
 * build with a named diagnostic.
 *
 * **No-suppression rule:** never resolved by `@Disabled` / `@Suppress` /
 * test removal. Resolve by adding the missing arm or by formal plan
 * revision.
 */
class CoordinationStateExhaustivenessTest {

    @Test
    fun `escalateOverdueCoordinations covers every CoordinationState leaf`() {
        verifyCallSite(
            file = "controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/Readback.kt",
            functionName = "advanceState",
        )
    }

    @Test
    fun `coordinationEscalationOutputs covers every CoordinationState leaf`() {
        verifyCallSite(
            file = "controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/CoordinationEscalation.kt",
            functionName = "coordinationEscalationOutputs",
        )
    }

    @Test
    fun `markCoordinationEscalationsEmitted covers every CoordinationState leaf`() {
        verifyCallSite(
            file = "controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/CoordinationEscalation.kt",
            functionName = "markCoordinationEscalationsEmitted",
        )
    }

    private fun verifyCallSite(file: String, functionName: String, minLeafCount: Int = 4) {
        val leaves = sealedLeaves(CoordinationState::class)
        check(leaves.size >= minLeafCount) {
            "Sanity check: CoordinationState sealed traversal found only ${leaves.size} leaves; " +
                "expected at least $minLeafCount. Reflection regressed."
        }
        val source = projectRoot().resolve(file).toFile().readText()
        val body = extractFunctionBody(source, functionName)
            ?: throw AssertionError(
                "Could not locate function `$functionName` in $file. " +
                    "If it moved or was renamed, update CoordinationStateExhaustivenessTest.",
            )
        val missing = leaves.filterNot { leaf ->
            Regex("""\bis\s+CoordinationState\.${leaf.simpleName}\b""").containsMatchIn(body)
        }
        check(missing.isEmpty()) {
            "EXHAUSTIVENESS VIOLATION: $functionName is missing arms for CoordinationState leaves: " +
                "${missing.map { it.simpleName }}. Each leaf must have an explicit " +
                "`is CoordinationState.<Leaf> -> ...` arm."
        }
    }

    private fun sealedLeaves(root: KClass<out Any>): Set<KClass<*>> {
        val seen = mutableSetOf<KClass<*>>()
        val leaves = mutableSetOf<KClass<*>>()
        fun walk(k: KClass<*>) {
            if (!seen.add(k)) return
            val subs = k.sealedSubclasses
            if (subs.isEmpty()) {
                if (!k.isAbstract && !k.isSealed) leaves.add(k)
            } else {
                subs.forEach { walk(it) }
            }
        }
        walk(root)
        return leaves
    }

    private fun extractFunctionBody(source: String, functionName: String): String? {
        val pattern = Regex("""(?m)^\s*(@\w[\w."(),\s]*\s+)?(public\s+|private\s+|internal\s+)?(suspend\s+)?fun\s+(\w+\.)?$functionName\s*\(""")
        val match = pattern.find(source) ?: return null
        var i = match.range.last
        var parenDepth = 0
        while (i < source.length) {
            when (source[i]) {
                '(' -> parenDepth++
                ')' -> parenDepth--
                '{' -> if (parenDepth == 0) {
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
                    val start = i + 1
                    var j = start
                    var braceDepth = 0
                    while (j < source.length) {
                        when (source[j]) {
                            '{' -> braceDepth++
                            '}' -> braceDepth--
                            '\n' -> if (braceDepth == 0 && j + 1 < source.length) {
                                val rest = source.substring(j + 1).trimStart()
                                if (rest.startsWith("fun ") || rest.startsWith("private fun ") ||
                                    rest.startsWith("internal fun ") || rest.startsWith("@")) {
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
}
