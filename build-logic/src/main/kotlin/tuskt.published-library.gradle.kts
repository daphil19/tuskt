import org.jetbrains.kotlin.util.prefixIfNot

plugins {
    id("com.vanniktech.maven.publish")
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    if (project.name == "shared") {
        coordinates(artifactId = "tuskt-shared")
    }

    pom {
        // published artifacts should be prefixed with tuskt-
        name = project.name.prefixIfNot("tuskt-")
        inceptionYear = "2025"
        url = "https://github.com/daphil19/tuskt"
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id = "daphil19"
                name = "David Phillips"
                url = "https://github.com/daphil19"
            }
        }
        scm {
            url = "https://github.com/daphil19/tuskt"
            connection = "scm:git:git://github.com/daphil19/tuskt.git"
            developerConnection = "scm:git:ssh://git@github.com/daphil19/tuskt.git"
        }
    }
}
