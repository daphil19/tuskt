plugins {
    id("tuskt.jvm-module")
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
}
