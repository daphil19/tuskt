plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

val libs = the<VersionCatalogsExtension>().named("libs")

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

kotlin {
    jvmToolchain(libs.findVersion("jdk").get().toString().toInt())
}

ktlint {
    version = libs.findVersion("ktlint-cli").get().toString()
}

detekt {
    buildUponDefaultConfig = true
}
