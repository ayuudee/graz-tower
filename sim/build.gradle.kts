import org.gradle.api.tasks.JavaExec

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":protocol"))
                implementation(project(":core"))
                implementation(project(":controller"))
                implementation(libs.arrow.core)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmTest {
            dependencies {
                implementation(project(":migration"))
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}

val jvmMainCompilation = kotlin.targets.getByName("jvm").compilations.getByName("main")

tasks.register<JavaExec>("compileClaimProbeArtifact") {
    group = "application"
    description = "Compile a claim-probe target card and parsed model draft into a queue artifact."
    dependsOn(tasks.named("jvmJar"))

    val targetCardPath = providers.gradleProperty("claimProbeTargetCard")
    val draftPath = providers.gradleProperty("claimProbeDraft")
    val outputPath = providers.gradleProperty("claimProbeOutput")
    val issuesOutputPath = providers.gradleProperty("claimProbeIssuesOutput")

    doFirst {
        require(targetCardPath.isPresent) { "Missing -PclaimProbeTargetCard=<path>" }
        require(draftPath.isPresent) { "Missing -PclaimProbeDraft=<path>" }
        require(outputPath.isPresent) { "Missing -PclaimProbeOutput=<path>" }
    }

    classpath(
        tasks.named("jvmJar"),
        jvmMainCompilation.runtimeDependencyFiles,
    )
    mainClass.set("xyz.easiersaid.twr.sim.reviewer.ClaimProbeCompileCliKt")
    args(
        "--target-card", targetCardPath.orNull ?: "",
        "--draft", draftPath.orNull ?: "",
        "--output", outputPath.orNull ?: "",
    )
    if (issuesOutputPath.isPresent) {
        args("--issues-output", issuesOutputPath.get())
    }
}

tasks.register<JavaExec>("compileClaimProbeBatch") {
    group = "application"
    description = "Compile a batch of claim-probe target cards and parsed model drafts into queue artifacts."
    dependsOn(tasks.named("jvmJar"))

    val manifestPath = providers.gradleProperty("claimProbeBatchManifest")
    val outputDirPath = providers.gradleProperty("claimProbeBatchOutputDir")
    val queueManifestPath = providers.gradleProperty("claimProbeBatchQueueManifest")
    val issuesOutputPath = providers.gradleProperty("claimProbeBatchIssuesOutput")

    doFirst {
        require(manifestPath.isPresent) { "Missing -PclaimProbeBatchManifest=<path>" }
        require(outputDirPath.isPresent) { "Missing -PclaimProbeBatchOutputDir=<path>" }
    }

    classpath(
        tasks.named("jvmJar"),
        jvmMainCompilation.runtimeDependencyFiles,
    )
    mainClass.set("xyz.easiersaid.twr.sim.reviewer.ClaimProbeCompileCliKt")
    args(
        "--manifest", manifestPath.orNull ?: "",
        "--output-dir", outputDirPath.orNull ?: "",
    )
    if (queueManifestPath.isPresent) {
        args("--queue-manifest-output", queueManifestPath.get())
    }
    if (issuesOutputPath.isPresent) {
        args("--issues-output", issuesOutputPath.get())
    }
}

tasks.register<JavaExec>("executeClaimProbeArtifact") {
    group = "application"
    description = "Execute a compiled claim-probe artifact and write an execution report."
    dependsOn(tasks.named("jvmJar"))

    val artifactPath = providers.gradleProperty("claimProbeArtifact")
    val outputPath = providers.gradleProperty("claimProbeExecutionOutput")
    val issuesOutputPath = providers.gradleProperty("claimProbeExecutionIssuesOutput")
    val untilSeconds = providers.gradleProperty("claimProbeExecutionUntilSeconds")

    doFirst {
        require(artifactPath.isPresent) { "Missing -PclaimProbeArtifact=<path>" }
        require(outputPath.isPresent) { "Missing -PclaimProbeExecutionOutput=<path>" }
    }

    classpath(
        tasks.named("jvmJar"),
        jvmMainCompilation.runtimeDependencyFiles,
    )
    mainClass.set("xyz.easiersaid.twr.sim.reviewer.ClaimProbeCompileCliKt")
    args(
        "--artifact", artifactPath.orNull ?: "",
        "--output", outputPath.orNull ?: "",
    )
    if (issuesOutputPath.isPresent) {
        args("--issues-output", issuesOutputPath.get())
    }
    if (untilSeconds.isPresent) {
        args("--until-seconds", untilSeconds.get())
    }
}

tasks.register<JavaExec>("executeClaimProbeBatch") {
    group = "application"
    description = "Execute a compiled claim-probe queue manifest and write a batch run manifest."
    dependsOn(tasks.named("jvmJar"))

    val queueManifestPath = providers.gradleProperty("claimProbeQueueManifest")
    val outputDirPath = providers.gradleProperty("claimProbeBatchExecutionOutputDir")
    val runManifestPath = providers.gradleProperty("claimProbeBatchExecutionRunManifest")
    val issuesOutputPath = providers.gradleProperty("claimProbeBatchExecutionIssuesOutput")
    val untilSeconds = providers.gradleProperty("claimProbeBatchExecutionUntilSeconds")

    doFirst {
        require(queueManifestPath.isPresent) { "Missing -PclaimProbeQueueManifest=<path>" }
        require(outputDirPath.isPresent) { "Missing -PclaimProbeBatchExecutionOutputDir=<path>" }
    }

    classpath(
        tasks.named("jvmJar"),
        jvmMainCompilation.runtimeDependencyFiles,
    )
    mainClass.set("xyz.easiersaid.twr.sim.reviewer.ClaimProbeCompileCliKt")
    args(
        "--queue-manifest", queueManifestPath.orNull ?: "",
        "--output-dir", outputDirPath.orNull ?: "",
    )
    if (runManifestPath.isPresent) {
        args("--run-manifest-output", runManifestPath.get())
    }
    if (issuesOutputPath.isPresent) {
        args("--issues-output", issuesOutputPath.get())
    }
    if (untilSeconds.isPresent) {
        args("--until-seconds", untilSeconds.get())
    }
}

tasks.register<JavaExec>("reviewClaimProbeBatchRun") {
    group = "application"
    description = "Review a claim-probe batch run manifest and write morning-review artifacts."
    dependsOn(tasks.named("jvmJar"))

    val runManifestPath = providers.gradleProperty("claimProbeRunManifest")
    val outputDirPath = providers.gradleProperty("claimProbeMorningReviewOutputDir")
    val markdownOutputPath = providers.gradleProperty("claimProbeMorningReviewMarkdownOutput")
    val jsonOutputPath = providers.gradleProperty("claimProbeMorningReviewJsonOutput")
    val issuesOutputPath = providers.gradleProperty("claimProbeMorningReviewIssuesOutput")

    doFirst {
        require(runManifestPath.isPresent) { "Missing -PclaimProbeRunManifest=<path>" }
        require(outputDirPath.isPresent) { "Missing -PclaimProbeMorningReviewOutputDir=<path>" }
    }

    classpath(
        tasks.named("jvmJar"),
        jvmMainCompilation.runtimeDependencyFiles,
    )
    mainClass.set("xyz.easiersaid.twr.sim.reviewer.ClaimProbeCompileCliKt")
    args(
        "--review-run-manifest", runManifestPath.orNull ?: "",
        "--output-dir", outputDirPath.orNull ?: "",
    )
    if (markdownOutputPath.isPresent) {
        args("--markdown-output", markdownOutputPath.get())
    }
    if (jsonOutputPath.isPresent) {
        args("--json-output", jsonOutputPath.get())
    }
    if (issuesOutputPath.isPresent) {
        args("--issues-output", issuesOutputPath.get())
    }
}
