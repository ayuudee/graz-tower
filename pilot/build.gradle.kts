plugins {
    kotlin("multiplatform")
}

// :pilot — the pilot's decision module.
//
// FIREWALL: this module must NOT declare a dependency on :controller or :sim.
// The pilot decides only on values that originated from own kinematic state
// (cockpit instruments), own filed plan (pre-briefing), own visual observation
// (world geometry at current position), and radio reception (received
// instructions, folded into the pilot's mission state by processInstruction
// outside the decision tick).
//
// The architectural firewall is enforced four ways: (1) build graph (this
// file's dependency block), (2) typed boundary (PilotInput), (3) compile-time
// allowlist test (FirewallPilotInputTest, FirewallAircraftStateTest), (4)
// source-text tripwires (FirewallSameTreatmentTest, FirewallSimPilotTickIsolationTest
// in :sim).
//
// See /home/andrew/.claude/plans/pilot-firewall.md for the full architectural
// principle and the deferments register.

kotlin {
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":protocol"))
                implementation(project(":core"))
                implementation(libs.arrow.core)
                // NO :controller, NO :sim. Firewall.
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
                // kotlin-reflect is required by ExhaustivenessTest to walk
                // AtcInstruction.sealedSubclasses transitively. JVM-only;
                // the architectural test does not run on other targets.
                implementation(kotlin("reflect"))
            }
        }
    }
}
