import java.time.Duration

plugins {
    id("tuskt.jvm-module")
    alias(libs.plugins.kotlin.serialization)
}

tasks.withType<Test>().configureEach {
    timeout.set(Duration.ofMinutes(2))
}

dependencies {
    testImplementation(projects.tusktClient)
    testImplementation(projects.tusktServer)
    testImplementation(projects.shared)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.jimfs)
    testImplementation(ktorLibs.client.cio)
    testImplementation(ktorLibs.server.netty)
    testImplementation(libs.kotlinx.serialization.json)
}
