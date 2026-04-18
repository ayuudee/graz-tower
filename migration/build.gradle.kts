plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core"))
                implementation(libs.arrow.core)
                implementation(libs.xmlutil.core)
                implementation(libs.xmlutil.serialization)
                implementation(libs.kotlinx.serialization.core)
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
                implementation(project(":protocol"))
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}
