plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

kotlin {
    jvmToolchain(17)
}

ktlint {
    version = providers.gradleProperty("KTLINT_CLI_VERSION").get()
}

detekt {
    buildUponDefaultConfig = true
}
