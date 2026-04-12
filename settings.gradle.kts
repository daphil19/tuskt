pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }

    versionCatalogs {
        create("ktorLibs") {
            from("io.ktor:ktor-version-catalog:3.4.2")
        }
    }
}

rootProject.name = "tuskt"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
include(":tuskt-client")
include(":tuskt-server")
include(":tuskt-server-standalone")
include(":shared")
include(":integration-tests")
