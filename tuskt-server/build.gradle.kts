plugins {
    alias(libs.plugins.kotlin.jvm)
//    alias(libs.plugins.ktor)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    // TODO library and publish plugins!
}

kotlin {
    jvmToolchain(
        libs.versions.jdk
            .get()
            .toInt(),
    )
}

// application {
//    mainClass.set("dev.phillipslabs.tuskt.ApplicationKt")
//
//    val isDevelopment: Boolean = project.ext.has("development")
//    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
// }

dependencies {
    // TODO does this need to be api for any reason?
    implementation(projects.shared)
    implementation(libs.ktor.server.core)
//    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.method.override)
}

ktlint {
    version = libs.versions.ktlint.get()
}

detekt {
    buildUponDefaultConfig = true
}
