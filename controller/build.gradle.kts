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
                implementation(libs.arrow.core)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test"))
                // kotlin-reflect is required by EventExhaustivenessTest to walk
                // ControllerEvent.sealedSubclasses transitively. JVM-only.
                implementation(kotlin("reflect"))
            }
        }
    }
}
