@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlin.multiplatform)
//    alias(libs.plugins.ktor)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

group = "dev.phillipslabs"
// TODO centralize version (to work with server)?
version = "0.1.0"

kotlin {
    explicitApi()

    abiValidation {
        enabled = true
    }

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
                // TODO does this need to be api for any reason?
                implementation(projects.shared)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

ktlint {
    version = libs.versions.ktlint.get()
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(group.toString(), "tuskt-client", version.toString())

    pom {
        name = "Tuskt"
        description = "ULID implementation for Kotlin Multiplatform"
        inceptionYear = "2025"
        url = "https://github.com/daphil19/tuskt"
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id = "daphil19"
                name = "David Phillips"
                url = "https://github.com/daphil19"
            }
        }
        scm {
            url = "https://github.com/daphil19/tuskt"
            connection = "scm:git:git://github.com/daphil19/tuskt.git"
            developerConnection = "scm:git:ssh://git@github.com/daphil19/tuskt.git"
        }
    }
}
