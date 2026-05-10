package xyz.easiersaid.twr.controller

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.test.Test

class FirewallCertifiedInstructionEmissionTest {
    private val root: Path = repoRoot()

    @Test
    fun `production code constructs Instruct only through approved factories`() {
        val allowed = setOf(
            "controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/ControllerTypes.kt",
        )
        val violations = productionKotlinFiles()
            .filterNot { path -> root.relativize(path).toString() in allowed }
            .flatMap { path ->
                val text = path.readText()
                Regex("""ControllerOutput\.Instruct\s*\(""")
                    .findAll(text)
                    .map { match -> "${root.relativize(path)}:${lineNumber(text, match.range.first)}" }
                    .toList()
            }
        check(violations.isEmpty()) {
            """
            FIREWALL VIOLATION: raw ControllerOutput.Instruct construction is forbidden.
            Use the narrow ControllerOutput.Instruct factory that matches the emission source.
            Violations:
            ${violations.joinToString(separator = "\n")}
            """.trimIndent()
        }
    }

    @Test
    fun `CertifiedInstruction is constructed only inside certification boundary`() {
        val allowed = setOf(
            "controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/certify/Certification.kt",
        )
        val violations = productionKotlinFiles()
            .filterNot { path -> root.relativize(path).toString() in allowed }
            .flatMap { path ->
                val text = path.readText()
                Regex("""CertifiedInstruction\s*\(""")
                    .findAll(text)
                    .map { match -> "${root.relativize(path)}:${lineNumber(text, match.range.first)}" }
                    .toList()
            }
        check(violations.isEmpty()) {
            """
            FIREWALL VIOLATION: CertifiedInstruction construction must stay inside
            the certification boundary. Violations:
            ${violations.joinToString(separator = "\n")}
            """.trimIndent()
        }
    }

    @Test
    fun `CertifiedInstruction create factory is not called outside certification boundary`() {
        val allowed = setOf(
            "controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/certify/Certification.kt",
        )
        val violations = productionKotlinFiles()
            .filterNot { path -> root.relativize(path).toString() in allowed }
            .flatMap { path ->
                val text = path.readText()
                Regex("""CertifiedInstruction(?:\.Companion)?\.create\s*\(""")
                    .findAll(text)
                    .map { match -> "${root.relativize(path)}:${lineNumber(text, match.range.first)}" }
                    .toList()
            }
        check(violations.isEmpty()) {
            """
            FIREWALL VIOLATION: CertifiedInstruction.create must stay inside the
            certification boundary. Violations:
            ${violations.joinToString(separator = "\n")}
            """.trimIndent()
        }
    }

    @Test
    fun `CertifiedInstruction token implementations stay inside certification boundary`() {
        val allowed = setOf(
            "controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/certify/Certification.kt",
        )
        val violations = productionKotlinFiles()
            .filterNot { path -> root.relativize(path).toString() in allowed }
            .flatMap { path ->
                val text = path.readText()
                Regex(""":\s*CertifiedInstructionToken\b""")
                    .findAll(text)
                    .map { match -> "${root.relativize(path)}:${lineNumber(text, match.range.first)}" }
                    .toList()
            }
        check(violations.isEmpty()) {
            """
            FIREWALL VIOLATION: CertifiedInstructionToken implementations must
            stay inside the certification boundary. Violations:
            ${violations.joinToString(separator = "\n")}
            """.trimIndent()
        }
    }

    private fun productionKotlinFiles(): List<Path> {
        val base = root.resolve("controller/src/commonMain/kotlin")
        return Files.walk(base).use { stream ->
            stream.filter { it.isRegularFile() && it.toString().endsWith(".kt") }
                .toList()
        }
    }

    private fun lineNumber(text: String, offset: Int): Int =
        text.substring(0, offset).count { it == '\n' } + 1

    private fun repoRoot(): Path {
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("settings.gradle.kts")) }
            ?.let { return it }
        error("Could not locate repo root from ${Path.of("").toAbsolutePath()}")
    }
}
