@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    id("tuskt.kmp-library-base")
    id("tuskt.published-library")
}

kotlin {
    jvm()

    js {
        nodejs()
        browser()
    }

    wasmJs {
        browser()
        nodejs()
    }

    android {
        namespace = "dev.phillipslabs.tuskt"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }

    // "tiers" here are taken from https://kotlinlang.org/docs/native-target-support.html
    // and filtered by https://ktor.io/docs/client-supported-platforms.html
    // tier 1
    macosX64()
    macosArm64()
    iosSimulatorArm64()
    iosX64()
    iosArm64()

    // tier 2
    linuxX64()
    linuxArm64()
    watchosSimulatorArm64()
    watchosX64()
    watchosArm32()
    watchosArm64()
    tvosSimulatorArm64()
    tvosX64()
    tvosArm64()

    // tier 3
    mingwX64()

    sourceSets {
        commonMain {
            dependencies {
                implementation(ktorLibs.client.core)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

mavenPublishing {
    pom {
        name = "Tuskt Shared"
        description = "Shared Tus protocol primitives and constants used by Tuskt libraries"
    }
}
