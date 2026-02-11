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
    implementation(ktorLibs.server.core)
//    implementation(libs.ktor.server.netty)
    implementation(ktorLibs.server.defaultHeaders)
    implementation(ktorLibs.server.methodOverride)

    testImplementation(ktorLibs.server.testHost)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.jimfs)
}

ktlint {
    version = libs.versions.ktlint.get()
}

detekt {
    buildUponDefaultConfig = true
}
