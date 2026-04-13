plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()

    sourceSets {
        commonMain {}
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
