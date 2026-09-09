package org.vpilo.babymonitor.versioning

/** Pure composition of [VersionInfo] from raw git output. No process execution here, so it is unit-testable. */
object GitVersion {
    const val MAIN_BRANCH = "main"
    private const val DETACHED = "HEAD"

    private val DESCRIBE = Regex("""^(.*)-(\d+)-g[0-9a-f]+$""")
    private val NON_SLUG = Regex("[^a-z0-9]+")

    /**
     * Computes version name and code from git output.
     *
     * @param describe output of `git describe --tags --long`, or null when there is no reachable tag.
     * @param commitCount total commits on HEAD (`git rev-list --count HEAD`); also the Android versionCode.
     * @param branch current branch, or "HEAD" when detached.
     * @param shortSha short commit hash, used as the branch token when detached.
     * @param isDirty whether the working copy has uncommitted changes.
     * @param isReleaseBuild whether this version is meant explicitly for a release build.
     */
    fun compute(
        describe: String?,
        commitCount: Int,
        branch: String,
        shortSha: String,
        isDirty: Boolean,
        isReleaseBuild: Boolean,
    ): VersionInfo {
        val described = describe?.let { DESCRIBE.find(it) }
        val (majorMinor, patch) = parseCore(described, commitCount)
        val core = "$majorMinor.$patch"

        val isTaggedRelease = isReleaseBuild && described != null && patch == 0
        if (isTaggedRelease) return VersionInfo(versionName = core, versionCore = core, versionCode = commitCount)

        val branchToken =
            when (branch) {
                MAIN_BRANCH -> null
                DETACHED -> shortSha
                else -> sanitizeBranch(branch)
            }

        val name =
            buildString {
                append(core)
                if (!branchToken.isNullOrEmpty()) append('-').append(branchToken)
                if (isDirty) append("-SNAPSHOT")
            }

        return VersionInfo(versionName = name, versionCore = core, versionCode = commitCount)
    }

    fun sanitizeBranch(branch: String): String = branch.lowercase().replace(NON_SLUG, "-").trim('-')

    /** Returns MAJOR.MINOR and the patch number. Patch = commits since tag, or total commits when untagged. */
    private fun parseCore(
        described: MatchResult?,
        commitCount: Int,
    ): Pair<String, Int> {
        val match = described ?: return "0.0" to commitCount
        val tag = match.groupValues[1].removePrefix("v")
        val commitsSinceTag = match.groupValues[2].toInt()
        val parts = tag.split('.')
        val major = parts.getOrNull(0)?.filter(Char::isDigit)?.ifEmpty { "0" } ?: "0"
        val minor = parts.getOrNull(1)?.filter(Char::isDigit)?.ifEmpty { "0" } ?: "0"
        return "$major.$minor" to commitsSinceTag
    }
}
