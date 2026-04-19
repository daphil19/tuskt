package dev.phillipslabs.tuskt.gradle

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.jetbrains.changelog.ChangelogPluginExtension
import org.jetbrains.changelog.date

class TusktRootReleasePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.github.ben-manes.versions")
            pluginManager.apply("org.jetbrains.changelog")

            tasks.named("dependencyUpdates", DependencyUpdatesTask::class.java).configure {
                rejectVersionIf {
                    isNonStable(candidate.version) && !isNonStable(currentVersion)
                }
                gradleReleaseChannel = "current"
            }

            extensions.configure(ChangelogPluginExtension::class.java) {
                versionPrefix.set("v")
                version.set(
                    providers.gradleProperty("VERSION_NAME").map { version ->
                        version.removeSuffix("-SNAPSHOT")
                    },
                )
                path.set(layout.projectDirectory.file("CHANGELOG.md").asFile.canonicalPath)
                header.set(
                    providers.provider {
                        "[${providers.gradleProperty("VERSION_NAME").get().removeSuffix("-SNAPSHOT")}] - ${date()}"
                    },
                )
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

            registerReleaseFlow(
                name = "releasePatch",
                releaseType = ReleaseType.PATCH,
                releaseVerification = releaseVerification,
            )
            registerReleaseFlow(
                name = "releaseMinor",
                releaseType = ReleaseType.MINOR,
                releaseVerification = releaseVerification,
            )
            registerReleaseFlow(
                name = "releaseMajor",
                releaseType = ReleaseType.MAJOR,
                releaseVerification = releaseVerification,
            )
            registerReleaseFlow(
                name = "releaseExplicit",
                releaseType = ReleaseType.EXPLICIT,
                releaseVerification = releaseVerification,
            )

            tasks.register("release") {
                group = "release"
                description = "Creates the next patch release, tags it, and bumps to the next development version."
                dependsOn("releasePatch")
                notCompatibleWithConfigurationCache(
                    "Release tasks modify gradle.properties and CHANGELOG.md and perform git commit/tag operations.",
                )
            }
        }
    }

    private fun Project.registerReleaseFlow(
        name: String,
        releaseType: ReleaseType,
        releaseVerification: TaskProvider<org.gradle.api.Task>,
    ) {
        val capitalized = name.replaceFirstChar(Char::uppercaseChar)
        val currentVersion = providers.gradleProperty("VERSION_NAME")
        val explicitReleaseVersion =
            providers.gradleProperty("release.version").orElse(
                providers.provider { "" },
            )
        val explicitNextVersion =
            providers.gradleProperty("release.newVersion").orElse(
                providers.provider { "" },
            )
        val allowUntracked =
            providers.gradleProperty("release.allowUntracked").map(String::toBoolean).orElse(false)

        val verifyState =
            tasks.register("verify${capitalized}State", VerifyReleaseStateTask::class.java) {
                group = "release"
                description = "Validates git state and release inputs for $name."
                this.currentVersion.set(currentVersion)
                this.releaseType.set(releaseType.name)
                this.explicitReleaseVersion.set(explicitReleaseVersion)
                this.explicitNextVersion.set(explicitNextVersion)
                this.allowUntracked.set(allowUntracked)
                branchName.set("main")
                changelogFile.set(layout.projectDirectory.file("CHANGELOG.md"))
                versionFile.set(layout.projectDirectory.file("gradle.properties"))
            }

        val writeReleaseVersion =
            tasks.register("write${capitalized}ReleaseVersion", UpdateVersionFileTask::class.java) {
                group = "release"
                description = "Writes the release version to gradle.properties for $name."
                dependsOn(verifyState)
                this.currentVersion.set(currentVersion)
                this.releaseType.set(releaseType.name)
                this.explicitReleaseVersion.set(explicitReleaseVersion)
                this.explicitNextVersion.set(explicitNextVersion)
                versionFile.set(layout.projectDirectory.file("gradle.properties"))
                targetVersionKind.set(TargetVersionKind.RELEASE.name)
            }

        val patchChangelog =
            tasks.register("patchChangelogFor$capitalized", PatchChangelogForReleaseTask::class.java) {
                group = "release"
                description = "Moves unreleased notes into a versioned changelog entry for $name."
                dependsOn(writeReleaseVersion)
                this.currentVersion.set(currentVersion)
                this.releaseType.set(releaseType.name)
                this.explicitReleaseVersion.set(explicitReleaseVersion)
                this.explicitNextVersion.set(explicitNextVersion)
                changelogFile.set(layout.projectDirectory.file("CHANGELOG.md"))
                releaseDate.set(LocalDate.now().toString())
            }

        val verifyReleaseBuild =
            tasks.register("verify${capitalized}Build") {
                group = "release"
                description = "Runs changelog and verification steps for $name."
                dependsOn(patchChangelog)
                dependsOn(releaseVerification)
            }
        releaseVerification.configure {
            mustRunAfter(patchChangelog)
        }

        val commitRelease =
            tasks.register("commit${capitalized}", GitCommitTask::class.java) {
                group = "release"
                description = "Creates the release commit for $name."
                dependsOn(verifyReleaseBuild)
                filesToAdd.set(listOf("gradle.properties", "CHANGELOG.md"))
                this.currentVersion.set(currentVersion)
                this.releaseType.set(releaseType.name)
                this.explicitReleaseVersion.set(explicitReleaseVersion)
                this.explicitNextVersion.set(explicitNextVersion)
                commitMessageTemplate.set("[Release] %s")
                targetVersionKind.set(TargetVersionKind.RELEASE.name)
            }

        val tagRelease =
            tasks.register("tag${capitalized}", GitTagTask::class.java) {
                group = "release"
                description = "Creates the git tag for $name."
                dependsOn(commitRelease)
                this.currentVersion.set(currentVersion)
                this.releaseType.set(releaseType.name)
                this.explicitReleaseVersion.set(explicitReleaseVersion)
                this.explicitNextVersion.set(explicitNextVersion)
            }

        val writeNextVersion =
            tasks.register("write${capitalized}NextVersion", UpdateVersionFileTask::class.java) {
                group = "release"
                description = "Writes the next development version for $name."
                dependsOn(tagRelease)
                this.currentVersion.set(currentVersion)
                this.releaseType.set(releaseType.name)
                this.explicitReleaseVersion.set(explicitReleaseVersion)
                this.explicitNextVersion.set(explicitNextVersion)
                versionFile.set(layout.projectDirectory.file("gradle.properties"))
                targetVersionKind.set(TargetVersionKind.NEXT.name)
            }

        val commitNextVersion =
            tasks.register("commit${capitalized}NextVersion", GitCommitTask::class.java) {
                group = "release"
                description = "Creates the next development version commit for $name."
                dependsOn(writeNextVersion)
                filesToAdd.set(listOf("gradle.properties"))
                this.currentVersion.set(currentVersion)
                this.releaseType.set(releaseType.name)
                this.explicitReleaseVersion.set(explicitReleaseVersion)
                this.explicitNextVersion.set(explicitNextVersion)
                commitMessageTemplate.set("[Release] Next development version %s")
                targetVersionKind.set(TargetVersionKind.NEXT.name)
            }

        tasks.register(name) {
            group = "release"
            description =
                when (releaseType) {
                    ReleaseType.PATCH -> "Creates the next patch release, tags it, and bumps to the next patch snapshot."
                    ReleaseType.MINOR -> "Creates the next minor release, tags it, and bumps to the next patch snapshot."
                    ReleaseType.MAJOR -> "Creates the next major release, tags it, and bumps to the next patch snapshot."
                    ReleaseType.EXPLICIT -> "Creates the requested explicit release version, tags it, and bumps to the next development version."
                }
            dependsOn(commitNextVersion)
            notCompatibleWithConfigurationCache(
                "Release tasks modify gradle.properties and CHANGELOG.md and perform git commit/tag operations.",
            )
        }
    }

    private fun isNonStable(version: String): Boolean {
        val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase(Locale.getDefault()).contains(it) }
        val regex = "^[0-9,.v-]+(-r)?$".toRegex()
        val isStable = stableKeyword || regex.matches(version)
        return isStable.not()
    }
}

private enum class ReleaseType {
    PATCH,
    MINOR,
    MAJOR,
    EXPLICIT,
}

private enum class TargetVersionKind {
    RELEASE,
    NEXT,
}

private data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)

    fun nextPatch(): SemanticVersion = copy(patch = patch + 1)

    fun nextMinor(): SemanticVersion = SemanticVersion(major = major, minor = minor + 1, patch = 0)

    fun nextMajor(): SemanticVersion = SemanticVersion(major = major + 1, minor = 0, patch = 0)

    fun toSnapshot(): String = "$this-SNAPSHOT"

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val versionRegex = Regex("""^(\d+)\.(\d+)\.(\d+)$""")

        fun parse(value: String): SemanticVersion {
            val match = versionRegex.matchEntire(value)
                ?: throw GradleException("Version must match X.Y.Z: $value")
            return SemanticVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt(),
            )
        }
    }
}

private data class ReleasePlan(
    val currentSnapshotVersion: String,
    val releaseVersion: SemanticVersion,
    val nextVersion: String,
) {
    companion object {
        fun from(
            currentVersion: String,
            releaseType: String,
            explicitReleaseVersion: String?,
            explicitNextVersion: String?,
        ): ReleasePlan {
            if (!currentVersion.endsWith("-SNAPSHOT")) {
                throw GradleException("VERSION_NAME must end with -SNAPSHOT before releasing. Found: $currentVersion")
            }

            val currentReleased = SemanticVersion.parse(currentVersion.removeSuffix("-SNAPSHOT"))
            val resolvedReleaseVersion =
                when (ReleaseType.valueOf(releaseType)) {
                    ReleaseType.PATCH -> currentReleased
                    ReleaseType.MINOR -> currentReleased.nextMinor()
                    ReleaseType.MAJOR -> currentReleased.nextMajor()
                    ReleaseType.EXPLICIT -> {
                        val explicit = explicitReleaseVersion?.takeIf(String::isNotBlank)
                            ?: throw GradleException("releaseExplicit requires -Prelease.version=X.Y.Z")
                        SemanticVersion.parse(explicit)
                    }
                }

            if (resolvedReleaseVersion <= currentReleased && ReleaseType.valueOf(releaseType) == ReleaseType.EXPLICIT) {
                throw GradleException(
                    "Explicit release version $resolvedReleaseVersion must be greater than current snapshot base version $currentReleased.",
                )
            }

            val resolvedNextVersion =
                explicitNextVersion
                    ?.takeIf(String::isNotBlank)
                    ?.also { nextVersion ->
                        if (!nextVersion.endsWith("-SNAPSHOT")) {
                            throw GradleException("release.newVersion must end with -SNAPSHOT. Found: $nextVersion")
                        }
                        SemanticVersion.parse(nextVersion.removeSuffix("-SNAPSHOT"))
                    }
                    ?.let { "$it-SNAPSHOT" }
                    ?: resolvedReleaseVersion.nextPatch().toSnapshot()

            return ReleasePlan(
                currentSnapshotVersion = currentVersion,
                releaseVersion = resolvedReleaseVersion,
                nextVersion = resolvedNextVersion,
            )
        }
    }
}

private abstract class ReleaseAwareTask : DefaultTask() {
    @get:Input
    abstract val currentVersion: Property<String>

    @get:Input
    abstract val releaseType: Property<String>

    @get:Optional
    @get:Input
    abstract val explicitReleaseVersion: Property<String>

    @get:Optional
    @get:Input
    abstract val explicitNextVersion: Property<String>

    protected fun releasePlan(): ReleasePlan =
        ReleasePlan.from(
            currentVersion = currentVersion.get(),
            releaseType = releaseType.get(),
            explicitReleaseVersion = explicitReleaseVersion.orNull,
            explicitNextVersion = explicitNextVersion.orNull,
        )
}

private abstract class VerifyReleaseStateTask : ReleaseAwareTask() {
    @get:Input
    abstract val branchName: Property<String>

    @get:Input
    abstract val allowUntracked: Property<Boolean>

    @get:InputFile
    abstract val changelogFile: RegularFileProperty

    @get:InputFile
    abstract val versionFile: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun verify() {
        val plan = releasePlan()
        val currentBranch = gitOutput("git", "branch", "--show-current")
        if (currentBranch != branchName.get()) {
            throw GradleException("Releases must run from branch ${branchName.get()}. Current branch: $currentBranch")
        }

        val statusOutput =
            if (allowUntracked.get()) {
                gitOutput("git", "status", "--porcelain", "--untracked-files=no")
            } else {
                gitOutput("git", "status", "--porcelain")
            }
        if (statusOutput.isNotBlank()) {
            val qualifier = if (allowUntracked.get()) "tracked files" else "working tree"
            throw GradleException("$qualifier must be clean before releasing.\n$statusOutput")
        }

        val existingTag = gitOutput("git", "tag", "--list", "v${plan.releaseVersion}")
        if (existingTag == "v${plan.releaseVersion}") {
            throw GradleException("Tag v${plan.releaseVersion} already exists.")
        }

        val changelogText = changelogFile.get().asFile.readText()
        val unreleasedHeader = "## [Unreleased]"
        val unreleasedStart = changelogText.indexOf(unreleasedHeader)
        if (unreleasedStart < 0) {
            throw GradleException("CHANGELOG.md must contain a ## [Unreleased] section.")
        }

        val unreleasedBodyStart = unreleasedStart + unreleasedHeader.length
        val nextHeading = Regex("^## \\[", RegexOption.MULTILINE).find(changelogText, unreleasedBodyStart)
        val unreleasedBody = changelogText.substring(unreleasedBodyStart, nextHeading?.range?.first ?: changelogText.length).trim()
        if (unreleasedBody.isBlank()) {
            throw GradleException("CHANGELOG.md must contain unreleased content before creating a release.")
        }

        val versionFileText = versionFile.get().asFile.readText()
        if (!versionFileText.contains("VERSION_NAME=${plan.currentSnapshotVersion}")) {
            throw GradleException("gradle.properties does not contain VERSION_NAME=${plan.currentSnapshotVersion}")
        }
    }

    private fun gitOutput(vararg command: String): String {
        val output = java.io.ByteArrayOutputStream()
        execOperations.exec {
            commandLine(*command)
            standardOutput = output
        }
        return output.toString().trim()
    }
}

private abstract class UpdateVersionFileTask : ReleaseAwareTask() {
    @get:InputFile
    abstract val versionFile: RegularFileProperty

    @get:Input
    abstract val targetVersionKind: Property<String>

    @TaskAction
    fun updateVersion() {
        val plan = releasePlan()
        val targetVersion =
            when (TargetVersionKind.valueOf(targetVersionKind.get())) {
                TargetVersionKind.RELEASE -> plan.releaseVersion.toString()
                TargetVersionKind.NEXT -> plan.nextVersion
            }

        val file = versionFile.get().asFile
        val original = file.readText()
        val updated =
            original.replace(
                Regex("""^VERSION_NAME=.*$""", RegexOption.MULTILINE),
                "VERSION_NAME=$targetVersion",
            )

        if (updated == original) {
            throw GradleException("Unable to update VERSION_NAME in ${file.name}")
        }

        file.writeText(updated)
    }
}

private abstract class PatchChangelogForReleaseTask : ReleaseAwareTask() {
    @get:InputFile
    abstract val changelogFile: RegularFileProperty

    @get:Input
    abstract val releaseDate: Property<String>

    @TaskAction
    fun patch() {
        val plan = releasePlan()
        val file = changelogFile.get().asFile
        val original = file.readText()
        val unreleasedHeader = "## [Unreleased]"
        val unreleasedStart = original.indexOf(unreleasedHeader)
        if (unreleasedStart < 0) {
            throw GradleException("CHANGELOG.md must contain a ## [Unreleased] section.")
        }

        val prefix = original.substring(0, unreleasedStart)
        val bodyStart = unreleasedStart + unreleasedHeader.length
        val nextHeading = Regex("^## \\[", RegexOption.MULTILINE).find(original, bodyStart)
        val suffix = original.substring(nextHeading?.range?.first ?: original.length).trimStart('\n')
        val unreleasedBody = original.substring(bodyStart, nextHeading?.range?.first ?: original.length).trim()
        if (unreleasedBody.isBlank()) {
            throw GradleException("CHANGELOG.md must contain unreleased content before patching a release.")
        }

        val updated =
            buildString {
                append(prefix)
                append(unreleasedHeader)
                append("\n\n")
                append("## [${plan.releaseVersion}] - ${releaseDate.get()}")
                append("\n\n")
                append(unreleasedBody)
                append('\n')
                if (suffix.isNotBlank()) {
                    append('\n')
                    append(suffix)
                    append('\n')
                }
            }

        file.writeText(updated)
    }
}

private abstract class GitCommitTask : ReleaseAwareTask() {
    @get:Input
    abstract val filesToAdd: ListProperty<String>

    @get:Input
    abstract val commitMessageTemplate: Property<String>

    @get:Input
    abstract val targetVersionKind: Property<String>

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun commit() {
        val plan = releasePlan()
        val targetVersion =
            when (TargetVersionKind.valueOf(targetVersionKind.get())) {
                TargetVersionKind.RELEASE -> plan.releaseVersion.toString()
                TargetVersionKind.NEXT -> plan.nextVersion
            }

        execOperations.exec {
            commandLine(listOf("git", "add") + filesToAdd.get())
        }
        execOperations.exec {
            commandLine("git", "commit", "-m", commitMessageTemplate.get().format(targetVersion))
        }
    }
}

private abstract class GitTagTask : ReleaseAwareTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun tag() {
        val plan = releasePlan()
        execOperations.exec {
            commandLine("git", "tag", "v${plan.releaseVersion}")
        }
    }
}
