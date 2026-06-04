package org.vpilo.babymonitor.build

import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Reads git at configuration time. Implemented as a [ValueSource] so it re-evaluates each build
 * (reflecting the current git state) while remaining compatible with the configuration cache.
 */
abstract class GitVersionValueSource : ValueSource<VersionInfo, ValueSourceParameters.None> {
    @get:Inject
    abstract val exec: ExecOperations

    override fun obtain(): VersionInfo {
        val commitCount = git("rev-list", "--count", "HEAD")?.toIntOrNull()
        if (commitCount == null) {
            logger.warn(
                "Baby Monitor: unable to read git history (not a git repository or shallow clone " +
                    "with no commits); falling back to version 0.0.0.",
            )
            return VersionInfo("0.0.0", "0.0.0", 0)
        }

        val describe = git("describe", "--tags", "--long")
        val branch = git("rev-parse", "--abbrev-ref", "HEAD") ?: "HEAD"
        val shortSha = git("rev-parse", "--short", "HEAD") ?: "unknown"
        val isDirty = !git("status", "--porcelain").isNullOrEmpty()

        return GitVersion.compute(describe, commitCount, branch, shortSha, isDirty)
    }

    private fun git(vararg args: String): String? {
        val stdout = ByteArrayOutputStream()
        val result = exec.exec {
            commandLine(listOf("git") + args)
            standardOutput = stdout
            errorOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }
        return if (result.exitValue == 0) stdout.toString().trim() else null
    }

    private companion object {
        private val logger = Logging.getLogger(GitVersionValueSource::class.java)
    }
}

/** Provider yielding the git-derived [VersionInfo] for this build. */
@Suppress("unused")
fun Project.gitVersionProvider(): Provider<VersionInfo> =
    providers.of(GitVersionValueSource::class.java) {}
