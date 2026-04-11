plugins {
    alias(libs.plugins.kotlin.jvm)
//    alias(libs.plugins.ktor)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "dev.phillipslabs"
version = "0.1.0"

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

// mavenPublishing {
//    publishToMavenCentral()
//
//    signAllPublications()
//
//    coordinates(group.toString(), "tuskt-server", version.toString())
//
//    pom {
//        name = "Tuskt Server"
//        description = "Tus server implementation for Ktor"
//        inceptionYear = "2025"
//        url = "https://github.com/daphil19/tuskt"
//        licenses {
//            license {
//                name.set("The Apache License, Version 2.0")
//                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
//                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
//            }
//        }
//        developers {
//            developer {
//                id = "daphil19"
//                name = "David Phillips"
//                url = "https://github.com/daphil19"
//            }
//        }
//        scm {
//            url = "https://github.com/daphil19/tuskt"
//            connection = "scm:git:git://github.com/daphil19/tuskt.git"
//            developerConnection = "scm:git:ssh://git@github.com/daphil19/tuskt.git"
//        }
//    }
// }
