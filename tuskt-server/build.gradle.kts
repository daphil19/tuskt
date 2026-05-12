plugins {
    id("tuskt.published-jvm-library")
    alias(libs.plugins.kotlin.serialization)
}

// application {
//    mainClass.set("dev.phillipslabs.tuskt.ApplicationKt")
//
//    val isDevelopment: Boolean = project.ext.has("development")
//    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
// }

dependencies {
    implementation(projects.shared)
    implementation(ktorLibs.server.core)
//    implementation(libs.ktor.server.netty)
    implementation(ktorLibs.server.defaultHeaders)
    implementation(ktorLibs.server.methodOverride)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(ktorLibs.server.testHost)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.jimfs)
}

mavenPublishing {
    pom {
        name = "Tuskt Server"
        description = "Tus server implementation for Ktor"
    }
}
