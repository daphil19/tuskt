plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("tusktRootRelease") {
            id = "tuskt.root-release"
            implementationClass = "dev.phillipslabs.tuskt.gradle.TusktRootReleasePlugin"
        }
    }
}

repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.android.gradlePlugin)
    implementation(libs.ktlint.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.benManes.gradlePlugin)
    implementation(libs.vanniktech.mavenPublish)
    implementation(libs.jetbrains.changelogPlugin)
}
