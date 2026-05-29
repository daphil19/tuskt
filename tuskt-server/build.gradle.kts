plugins {
    id("tuskt.published-jvm-library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.shared)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.defaultHeaders)
    implementation(ktorLibs.server.methodOverride)
//    implementation(ktorLibs.server.statusPages)
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
