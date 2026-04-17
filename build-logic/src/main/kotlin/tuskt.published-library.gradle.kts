import dev.phillipslabs.tuskt.gradle.gradlePropertyOrEnvVar

plugins {
    id("com.vanniktech.maven.publish")
}

val libs = the<VersionCatalogsExtension>().named("libs")

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

val artifactoryUrl = providers.gradlePropertyOrEnvVar("ARTIFACTORY_MAVEN_URL")
val artifactoryUsername = providers.gradlePropertyOrEnvVar("ARTIFACTORY_USERNAME")
val artifactoryPassword = providers.gradlePropertyOrEnvVar("ARTIFACTORY_PASSWORD")

mavenPublishing {
    signAllPublications()

    if (project.name == "shared") {
        coordinates(artifactId = "tuskt-shared")
    }

    pom {
        inceptionYear = "2025"
        url = providers.gradleProperty("POM_URL").get()
        licenses {
            license {
                name.set(providers.gradleProperty("POM_LICENSE_NAME").get())
                url.set(providers.gradleProperty("POM_LICENSE_URL").get())
                distribution.set(providers.gradleProperty("POM_LICENSE_DIST").get())
            }
        }
        developers {
            developer {
                id = providers.gradleProperty("POM_DEVELOPER_ID").get()
                name = providers.gradleProperty("POM_DEVELOPER_NAME").get()
                url = providers.gradleProperty("POM_DEVELOPER_URL").get()
            }
        }
        scm {
            url = providers.gradleProperty("POM_SCM_URL").get()
            connection = providers.gradleProperty("POM_SCM_CONNECTION").get()
            developerConnection = providers.gradleProperty("POM_SCM_DEV_CONNECTION").get()
        }
    }
}

extensions.configure<PublishingExtension>("publishing") {
    repositories {
        if (artifactoryUrl.isPresent) {
            maven {
                name = "artifactory"
                url = uri(artifactoryUrl.get())
                credentials {
                    username = artifactoryUsername.orNull
                    password = artifactoryPassword.orNull
                }
            }
        }
    }
}
