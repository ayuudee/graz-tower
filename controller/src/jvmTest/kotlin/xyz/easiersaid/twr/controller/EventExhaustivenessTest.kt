package xyz.easiersaid.twr.controller

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.reflect.KClass
import kotlin.test.Test
import xyz.easiersaid.twr.controller.observe.ControllerEvent

/**
 * Architectural enforcement test — leaf-level exhaustiveness over
 * [ControllerEvent] at the controller-side functions that destructure it.
 *
 * The Pass-1 [xyz.easiersaid.twr.pilot.ExhaustivenessTest] enforces this
 * contract for [xyz.easiersaid.twr.protocol.AtcInstruction] and
 * [xyz.easiersaid.twr.protocol.ControllerResponse]. Pass 5 (D-AUDIT.14
 * closure) introduces two new functions that switch on every
 * [ControllerEvent] leaf:
 *  - [xyz.easiersaid.twr.controller.observe.aircraftIdOf]
 *  - [xyz.easiersaid.twr.controller.observe.intentFromRadio]
 *
 * Adding a [ControllerEvent] subtype must extend both functions. This test
 * pins that contract: any new leaf without a corresponding `is <Leaf> -> ...`
 * arm fails the build with a named diagnostic.
 *
 * **No-suppression rule:** never resolved by `@Disabled` / `@Suppress` / test
 * removal. Resolve by adding the missing arm or by formal plan revision.
 */
class EventExhaustivenessTest {

    @Test
    fun `aircraftIdOf matches every ControllerEvent leaf`() {
        verifyCallSite(
            file = "controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/Observe.kt",
            functionName = "aircraftIdOf",
            sealedRoot = ControllerEvent::class,
            minLeafCount = 13,
        )
    }

    @Test
    fun `intentFromRadio matches every ControllerEvent leaf`() {
        verifyCallSite(
            file = "controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/Observe.kt",
            functionName = "intentFromRadio",
            sealedRoot = ControllerEvent::class,
            minLeafCount = 13,
        )
    }

    private fun verifyCallSite(
        file: String,
        functionName: String,
        sealedRoot: KClass<out Any>,
        minLeafCount: Int,
    ) {
        val leaves = sealedLeaves(sealedRoot)
        check(leaves.size >= minLeafCount) {
            "Sanity check: ${sealedRoot.simpleName} sealed traversal found only ${leaves.size} leaves; " +
                "expected at least $minLeafCount. Reflection or sealedSubclasses traversal regressed."
        }

        val source = projectRoot().resolve(file).toFile().readText()
        val functionBody = extractFunctionBody(source, functionName)
            ?: throw AssertionError(
                "Could not locate function `$functionName` in $file. " +
                    "If the function moved or was renamed, update EventExhaustivenessTest accordingly."
            )

        val missing = leaves.filterNot { leaf ->
            Regex("""\bis\s+ControllerEvent\.${leaf.simpleName}\b""").containsMatchIn(functionBody)
        }
        check(missing.isEmpty()) {
            "EXHAUSTIVENESS VIOLATION: $functionName is missing arms for ${sealedRoot.simpleName} leaves: " +
                "${missing.map { it.simpleName }}. Each leaf must have an explicit " +
                "`is ControllerEvent.<Leaf> -> ...` arm. Adding a new ${sealedRoot.simpleName} subtype " +
                "requires extending this call site."
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
