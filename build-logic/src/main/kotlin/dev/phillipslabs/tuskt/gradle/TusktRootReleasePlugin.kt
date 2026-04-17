package dev.phillipslabs.tuskt.gradle

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import java.util.Locale
import net.researchgate.release.ReleaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.changelog.ChangelogPluginExtension
import org.jetbrains.changelog.date

class TusktRootReleasePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.github.ben-manes.versions")
            pluginManager.apply("org.jetbrains.changelog")
            pluginManager.apply("net.researchgate.release")

            tasks.named("dependencyUpdates", DependencyUpdatesTask::class.java).configure {
                rejectVersionIf {
                    isNonStable(candidate.version) && !isNonStable(currentVersion)
                }
                gradleReleaseChannel = "current"
            }

            extensions.configure(ChangelogPluginExtension::class.java) {
                versionPrefix.set("v")
                version.set(providers.provider { project.version.toString().removeSuffix("-SNAPSHOT") })
                path.set(layout.projectDirectory.file("CHANGELOG.md").asFile.canonicalPath)
                header.set(providers.provider { "[${project.version.toString().removeSuffix("-SNAPSHOT")}] - ${date()}" })
                unreleasedTerm.set("[Unreleased]")
                keepUnreleasedSection.set(true)
                itemPrefix.set("-")
                groups.set(listOf("Added", "Changed", "Deprecated", "Removed", "Fixed", "Security"))
            }

            val releaseVerification =
                tasks.register("releaseVerification") {
                    group = "release"
                    description = "Runs the verification tasks required before a release tag is created."
                    dependsOn(
                        "ktlintCheck",
                        "detekt",
                        "test",
                        ":shared:allTests",
                        ":tuskt-client:allTests",
                        ":tuskt-server-standalone:shadowJar",
                    )
                }

            extensions.configure(ReleaseExtension::class.java) {
                tagTemplate.set("v\$version")
                versionPropertyFile.set("gradle.properties")
                versionProperties.set(listOf("VERSION_NAME"))
                buildTasks.set(listOf(releaseVerification.name))
                preTagCommitMessage.set("[Gradle Release Plugin] - release ")
                tagCommitMessage.set("[Gradle Release Plugin] - tag ")
                newVersionCommitMessage.set("[Gradle Release Plugin] - next version ")

                git {
                    requireBranch.set("main")
                    pushToRemote.set("origin")
                    commitVersionFileOnly.set(false)
                    signTag.set(false)
                }
            }

            tasks.named("beforeReleaseBuild") {
                dependsOn("patchChangelog")
            }

            tasks.named("release") {
                notCompatibleWithConfigurationCache(
                    "net.researchgate.release uses build listeners that are not configuration-cache compatible",
                )
            }
        }
    }

    private fun isNonStable(version: String): Boolean {
        val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase(Locale.getDefault()).contains(it) }
        val regex = "^[0-9,.v-]+(-r)?$".toRegex()
        val isStable = stableKeyword || regex.matches(version)
        return isStable.not()
    }
}
