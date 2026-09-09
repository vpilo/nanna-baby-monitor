package org.vpilo.babymonitor.versioning

import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Reads git at configuration time. Implemented as a [ValueSource] so it re-evaluates each build
 * (reflecting the current git state) while remaining compatible with the configuration cache.
 */
abstract class GitVersionValueSource : ValueSource<VersionInfo, GitVersionValueSource.Parameters> {
    @get:Inject
    abstract val exec: ExecOperations

    override fun obtain(): VersionInfo {
        val commitCount = git("rev-list", "--count", "HEAD")?.toIntOrNull()
        if (commitCount == null) {
            logger.warn(
                "Unable to read git history (not a git repository or shallow clone " +
                    "with no commits); falling back to version 0.0.0.",
            )
            return VersionInfo("0.0.0", "0.0.0", 0)
        }

        val describe = git("describe", "--tags", "--long")
        val branch = git("rev-parse", "--abbrev-ref", "HEAD") ?: "HEAD"
        val shortSha = git("rev-parse", "--short", "HEAD") ?: "unknown"
        val isDirty = !git("status", "--porcelain").isNullOrEmpty()

        // F-Droid's reproducible build system requires stable tags: when using the release Gradle property on CI, only use the
        //  git tag.
        val isReleaseBuild = parameters.releaseBuild.getOrElse(false)

        return GitVersion.compute(
            describe = describe,
            commitCount = commitCount,
            branch = branch,
            shortSha = shortSha,
            isDirty = isDirty,
            isReleaseBuild = isReleaseBuild,
        )
    }

    private fun git(vararg args: String): String? {
        val stdout = ByteArrayOutputStream()
        val result =
            exec.exec {
                commandLine(listOf("git") + args)
                standardOutput = stdout
                errorOutput = ByteArrayOutputStream()
                isIgnoreExitValue = true
            }
        return if (result.exitValue == 0) stdout.toString().trim() else null
    }

    interface Parameters : ValueSourceParameters {
        /** See [GitVersionExtension.isReleaseBuild]. */
        val releaseBuild: Property<Boolean>
    }

    private companion object {
        private val logger = Logging.getLogger(GitVersionValueSource::class.java)
    }
}
