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
    macosArm64()
    iosSimulatorArm64()
    iosX64()
    iosArm64()

    // tier 2
    linuxX64()
    linuxArm64()
    watchosSimulatorArm64()
    watchosArm32()
    watchosArm64()
    tvosSimulatorArm64()
    tvosArm64()

    // tier 3
    mingwX64()

    sourceSets {
        commonMain {
            dependencies {
                // TODO does this need to be api for any reason?
                implementation(projects.shared)
                implementation(ktorLibs.client.core)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(ktorLibs.client.mock)
            }
        }
    }
}

mavenPublishing {
    pom {
        name = "Tuskt Client"
        description = "Tus client implementation for Kotlin Multiplatform"
    }
}
