@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlin.multiplatform)
//    alias(libs.plugins.ktor)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
//    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
//    explicitApi()
//
//    abiValidation {
//        enabled = true
//    }

    jvm()
    jvmToolchain(
        libs.versions.jdk
            .get()
            .toInt(),
    )

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
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
