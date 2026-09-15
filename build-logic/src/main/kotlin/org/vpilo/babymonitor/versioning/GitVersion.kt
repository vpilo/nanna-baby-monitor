package org.vpilo.babymonitor.versioning

/** Pure composition of [VersionInfo] from raw git output. No process execution here, so it is unit-testable. */
object GitVersion {
    const val MAIN_BRANCH = "main"
    private const val DETACHED = "HEAD"

    // `git describe --match` glob selecting the base tags.
    const val BASE_TAG_GLOB = "[0-9]*.[0-9]*.0"

    // Google Play max version code is capped to 2100000000.
    private const val MAX_MAJOR = 2099
    private const val MAX_MINOR = 999
    private const val MAX_PATCH = 999

    private val DESCRIBE = Regex("""(\d+)\.(\d+)\.0-(\d+)-g[0-9a-f]+""")
    private val VERSION_TAG = Regex("""\d+\.\d+\.\d+""")
    private val NON_SLUG = Regex("[^a-z0-9]+")

    /**
     * Computes version name and code from git output.
     *
     * The version is `MAJOR.MINOR.PATCH`: `MAJOR.MINOR` comes from the nearest `MAJOR.MINOR.0` tag, and PATCH counts the commits
     * since it. Any other version tag is only a label, which a release build requires to match the computed version.
     *
     * The version code is `MAJOR * 10^6 + MINOR * 10^3 + PATCH`.
     *
     * @param describe output of `git describe --tags --long --match` [BASE_TAG_GLOB], or null when there is no reachable base tag.
     * @param headTags tags pointing at HEAD.
     * @param branch current branch, or "HEAD" when detached.
     * @param shortSha short commit hash, used as the branch token when detached.
     * @param isDirty whether the working copy has uncommitted changes.
     * @param isReleaseBuild whether this version is meant explicitly for a release build.
     */
    fun compute(
        describe: String?,
        headTags: List<String>,
        branch: String,
        shortSha: String,
        isDirty: Boolean,
        isReleaseBuild: Boolean,
    ): VersionInfo {
        check(describe != null || !isReleaseBuild) {
            "A release build needs a reachable MAJOR.MINOR.0 tag: fetch the full history and its tags."
        }

        val (major, minor, patch) = parseCore(describe)
        val core = "$major.$minor.$patch"
        val code = versionCode(major, minor, patch)

        val versionTags = headTags.filter(VERSION_TAG::matches)
        if (isReleaseBuild && versionTags.isNotEmpty()) {
            check(core in versionTags) { "Version tags $versionTags on HEAD don't match its computed version $core." }
            return VersionInfo(versionName = core, versionCore = core, versionCode = code)
        }

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

        return VersionInfo(versionName = name, versionCore = core, versionCode = code)
    }

    fun sanitizeBranch(branch: String): String = branch.lowercase().replace(NON_SLUG, "-").trim('-')

    /** Returns MAJOR, MINOR and PATCH, or all zeros when there is no base tag. */
    private fun parseCore(describe: String?): Triple<Int, Int, Int> {
        if (describe == null) return Triple(0, 0, 0)
        val match = checkNotNull(DESCRIBE.matchEntire(describe)) { "'$describe' is not based on a MAJOR.MINOR.0 tag." }
        val (major, minor, commitsSinceTag) = match.destructured
        return Triple(major.toInt(), minor.toInt(), commitsSinceTag.toInt())
    }

    private fun versionCode(
        major: Int,
        minor: Int,
        patch: Int,
    ): Int {
        check(major <= MAX_MAJOR) { "Major version $major exceeds $MAX_MAJOR." }
        check(minor <= MAX_MINOR) { "Minor version $minor exceeds $MAX_MINOR." }
        check(patch <= MAX_PATCH) { "$patch commits since $major.$minor.0 exceed $MAX_PATCH: tag a new MAJOR.MINOR.0." }
        return (major * 1000 + minor) * 1000 + patch
    }
}
