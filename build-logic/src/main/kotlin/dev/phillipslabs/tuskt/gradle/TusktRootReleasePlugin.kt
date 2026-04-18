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

            tasks.named("dependencyUpdates", DependencyUpdatesTask::class.java).configure {
                rejectVersionIf {
                    isNonStable(candidate.version) && !isNonStable(currentVersion)
                }
                gradleReleaseChannel = "current"
            }

            if (requiresReleaseTooling()) {
                pluginManager.apply("org.jetbrains.changelog")
                pluginManager.apply("net.researchgate.release")

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
                            ":integration-tests:test",
                            ":shared:check",
                            ":shared:allTests",
                            ":tuskt-client:check",
                            ":tuskt-client:allTests",
                            ":tuskt-server:check",
                            ":tuskt-server-standalone:check",
                            ":tuskt-server-standalone:shadowJar",
                        )
                    }

                extensions.configure(ReleaseExtension::class.java) {
                    tagTemplate.set("v\$version")
                    versionPropertyFile.set("gradle.properties")
                    versionProperties.set(listOf("VERSION_NAME"))
                    buildTasks.set(emptyList())
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
                    dependsOn(releaseVerification)
                }

                tasks.named("release") {
                    notCompatibleWithConfigurationCache(
                        "net.researchgate.release uses build listeners that are not configuration-cache compatible",
                    )
                }
            }
        }
    }

    private fun Project.requiresReleaseTooling(): Boolean {
        val releaseTaskNames =
            setOf(
                "release",
                "beforeReleaseBuild",
                "patchChangelog",
                "getChangelog",
            )

        return gradle.startParameter.taskNames.any { requested ->
            releaseTaskNames.any { taskName ->
                requested == taskName ||
                    requested.endsWith(":$taskName") ||
                    requested.contains(taskName, ignoreCase = true)
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
