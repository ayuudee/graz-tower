plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                api(libs.arrow.core)
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
                // kotlin-reflect is required by TaxiToSplitFirewallTest to
                // walk GroundInstruction.sealedSubclasses transitively. JVM-only.
                implementation(kotlin("reflect"))
            }
        }
    }
}
