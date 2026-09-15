package org.vpilo.babymonitor.versioning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GitVersionTest {
    private fun describe(
        baseTag: String,
        commits: Int,
    ) = "$baseTag-$commits-gabc1234"

    private fun compute(
        describe: String?,
        headTags: List<String> = emptyList(),
        branch: String = GitVersion.MAIN_BRANCH,
        isDirty: Boolean = false,
        isReleaseBuild: Boolean = false,
    ) = GitVersion.compute(describe, headTags, branch, "abc1234", isDirty, isReleaseBuild)

    @Test
    fun `main clean off-tag uses commits since the base tag as patch, no suffix`() {
        val v = compute(describe("5.1.0", 13))
        assertEquals("5.1.13", v.versionName)
        assertEquals("5.1.13", v.versionCore)
        assertEquals(5_001_013, v.versionCode)
    }

    @Test
    fun `main dirty appends SNAPSHOT`() {
        val v = compute(describe("2.0.0", 6), isDirty = true)
        assertEquals("2.0.6-SNAPSHOT", v.versionName)
        assertEquals("2.0.6", v.versionCore)
    }

    @Test
    fun `exactly on a base tag yields patch zero`() {
        val v = compute(describe("2.0.0", 0))
        assertEquals("2.0.0", v.versionName)
        assertEquals(2_000_000, v.versionCode)
    }

    @Test
    fun `highest version fits the Play version code cap`() {
        assertEquals(2_099_999_999, compute(describe("2099.999.0", 999)).versionCode)
    }

    @Test
    fun `too many commits since the base tag fail`() {
        assertFailsWith<IllegalStateException> { compute(describe("5.1.0", 1000)) }
    }

    @Test
    fun `too high a major or minor fails`() {
        assertFailsWith<IllegalStateException> { compute(describe("2100.0.0", 0)) }
        assertFailsWith<IllegalStateException> { compute(describe("1.1000.0", 0)) }
    }

    @Test
    fun `a base tag not shaped MAJOR_MINOR_0 fails`() {
        assertFailsWith<IllegalStateException> { compute(describe("1.2.3.0", 5)) }
    }

    @Test
    fun `release build on a matching patch tag is named after the tag alone`() {
        val v = compute(describe("5.1.0", 6), listOf("5.1.6"), branch = "HEAD", isDirty = true, isReleaseBuild = true)
        assertEquals("5.1.6", v.versionName)
        assertEquals("5.1.6", v.versionCore)
        assertEquals(5_001_006, v.versionCode)
    }

    @Test
    fun `release build on a tag not matching the computed version fails`() {
        assertFailsWith<IllegalStateException> {
            compute(describe("5.1.0", 13), listOf("5.1.7"), isReleaseBuild = true)
        }
    }

    @Test
    fun `release build accepts a matching tag among others`() {
        val v = compute(describe("5.1.0", 13), listOf("5.1.7", "5.1.13"), isReleaseBuild = true)
        assertEquals("5.1.13", v.versionName)
    }

    @Test
    fun `without the release flag a mismatching tag is ignored`() {
        val v = compute(describe("5.1.0", 13), listOf("5.1.7"), branch = "HEAD")
        assertEquals("5.1.13-abc1234", v.versionName)
    }

    @Test
    fun `release build ignores non-version tags`() {
        val v = compute(describe("5.1.0", 13), listOf("fdroid-test"), branch = "HEAD", isReleaseBuild = true)
        assertEquals("5.1.13-abc1234", v.versionName)
    }

    @Test
    fun `release build on a base tag ignores the branch name`() {
        val v = compute(describe("2.0.0", 0), listOf("2.0.0"), branch = "feature/ABC-123", isReleaseBuild = true)
        assertEquals("2.0.0", v.versionName)
    }

    @Test
    fun `without the release flag a tagged build still shows branch and dirt`() {
        val v = compute(describe("2.0.0", 0), listOf("2.0.0"), branch = "HEAD", isDirty = true)
        assertEquals("2.0.0-abc1234-SNAPSHOT", v.versionName)
    }

    @Test
    fun `a release build off-tag keeps branch and dirt`() {
        val v = compute(describe("2.0.0", 6), branch = "HEAD", isDirty = true, isReleaseBuild = true)
        assertEquals("2.0.6-abc1234-SNAPSHOT", v.versionName)
    }

    @Test
    fun `a release build without a base tag fails`() {
        assertFailsWith<IllegalStateException> { compute(null, isReleaseBuild = true) }
    }

    @Test
    fun `no base tag falls back to 0_0_0`() {
        val v = compute(null)
        assertEquals("0.0.0", v.versionName)
        assertEquals(0, v.versionCode)
    }

    @Test
    fun `branch clean appends sanitized branch`() {
        val v = compute(describe("2.0.0", 6), branch = "worktree-base")
        assertEquals("2.0.6-worktree-base", v.versionName)
    }

    @Test
    fun `branch dirty appends branch then SNAPSHOT`() {
        val v = compute(describe("2.0.0", 6), branch = "feature/ABC-123", isDirty = true)
        assertEquals("2.0.6-feature-abc-123-SNAPSHOT", v.versionName)
    }

    @Test
    fun `detached head uses short sha as branch token`() {
        val v = compute(describe("2.0.0", 6), branch = "HEAD")
        assertEquals("2.0.6-abc1234", v.versionName)
    }

    @Test
    fun `sanitize lowercases collapses and trims`() {
        assertEquals("feature-abc-123", GitVersion.sanitizeBranch("feature/ABC--123"))
        assertEquals("foo-bar", GitVersion.sanitizeBranch("__foo  bar__"))
    }
}
