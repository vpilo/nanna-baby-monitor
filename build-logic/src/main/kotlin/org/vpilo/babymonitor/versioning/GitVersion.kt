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
        val commitsSinceTag = described?.groupValues?.get(2)?.toInt()
        val core = parseCore(described?.groupValues?.get(1), commitsSinceTag, commitCount)

        val isTaggedRelease = isReleaseBuild && commitsSinceTag == 0
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

    /** Returns MAJOR.MINOR.PATCH. Patch = the tag's patch plus commits since the tag, or total commits when untagged. */
    private fun parseCore(
        tag: String?,
        commitsSinceTag: Int?,
        commitCount: Int,
    ): String {
        if (tag == null || commitsSinceTag == null) return "0.0.$commitCount"
        val parts = tag.removePrefix("v").split('.')
        val (major, minor, tagPatch) = (0..2).map { parts.getOrNull(it)?.filter(Char::isDigit)?.ifEmpty { "0" } ?: "0" }
        return "$major.$minor.${tagPatch.toInt() + commitsSinceTag}"
    }
}
