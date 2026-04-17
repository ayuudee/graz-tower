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
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
