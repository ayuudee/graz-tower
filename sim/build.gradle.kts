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
            }
        }
    }
}
