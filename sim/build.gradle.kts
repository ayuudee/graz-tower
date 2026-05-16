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
                implementation(project(":pilot"))
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
                // Pass 9 (D-AUDIT.2): SimEventExhaustivenessTest walks
                // SimEvent.sealedSubclasses transitively. JVM-only.
                implementation(kotlin("reflect"))
                // fn-26 (R8): Kotest framework + property + assertions
                // for engine `step()` property tests
                // (`StepPropertyTest.kt` + `EngineGenerators.kt`).
                // Root build.gradle.kts already wires
                // `tasks.withType<Test> { useJUnitPlatform() }` across
                // all subprojects, so kotest-runner-junit5 auto-
                // discovers without additional configuration here.
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.property)
                implementation(libs.kotest.runner.junit5)
            }
        }
    }
}
