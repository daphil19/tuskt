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
