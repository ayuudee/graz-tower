plugins {
    kotlin("multiplatform") version "2.1.0" apply false
    kotlin("jvm") version "2.1.0" apply false
    kotlin("plugin.serialization") version "2.1.0" apply false
    alias(libs.plugins.detekt)
}

allprojects {
    group = "xyz.easiersaid.twr"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/detekt.yml"))
    baseline = file("$rootDir/detekt-baseline.xml")
    parallel = true
    source.setFrom(
        subprojects.flatMap { project ->
            listOf(
                "${project.projectDir}/src/commonMain/kotlin",
                "${project.projectDir}/src/jvmMain/kotlin"
            )
        }
    )
}

// Gate `./gradlew check` on detekt so lint failures break the build, not just
// a standalone `./gradlew detekt` invocation. Subprojects don't each run the
// root `detekt` task, so we attach a dependency from each subproject's `check`
// to the root `detekt` task.
subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(rootProject.tasks.named("detekt"))
    }
}
