package org.vpilo.babymonitor.versioning

import kotlin.test.Test
import kotlin.test.assertEquals

class GitVersionTest {
    private fun describe(
        tag: String,
        commits: Int,
    ) = "$tag-$commits-gabc1234"

    @Test
    fun `main clean off-tag uses commit count as patch, no suffix`() {
        val v = GitVersion.compute(describe("2.0.0", 6), commitCount = 249, branch = "main", shortSha = "abc1234", isDirty = false)
        assertEquals("2.0.6", v.versionName)
        assertEquals("2.0.6", v.versionCore)
        assertEquals(249, v.versionCode)
    }

    @Test
    fun `main dirty appends SNAPSHOT`() {
        val v = GitVersion.compute(describe("2.0.0", 6), 249, "main", "abc1234", isDirty = true)
        assertEquals("2.0.6-SNAPSHOT", v.versionName)
        assertEquals("2.0.6", v.versionCore)
    }

    @Test
    fun `exactly on tag yields patch zero`() {
        val v = GitVersion.compute(describe("2.0.0", 0), 243, "main", "abc1234", isDirty = false)
        assertEquals("2.0.0", v.versionName)
    }

    @Test
    fun `branch clean appends sanitized branch`() {
        val v = GitVersion.compute(describe("2.0.0", 6), 249, "worktree-base", "abc1234", isDirty = false)
        assertEquals("2.0.6-worktree-base", v.versionName)
    }

    @Test
    fun `branch dirty appends branch then SNAPSHOT`() {
        val v = GitVersion.compute(describe("2.0.0", 6), 249, "feature/ABC-123", "abc1234", isDirty = true)
        assertEquals("2.0.6-feature-abc-123-SNAPSHOT", v.versionName)
    }

    @Test
    fun `detached head uses short sha as branch token`() {
        val v = GitVersion.compute(describe("2.0.0", 6), 249, "HEAD", "abc1234", isDirty = false)
        assertEquals("2.0.6-abc1234", v.versionName)
    }

    @Test
    fun `no tag falls back to 0_0 with commit count patch`() {
        val v = GitVersion.compute(describe = null, commitCount = 12, branch = "main", shortSha = "abc1234", isDirty = false)
        assertEquals("0.0.12", v.versionName)
        assertEquals(12, v.versionCode)
    }

    @Test
    fun `v-prefixed tag is stripped`() {
        val v = GitVersion.compute(describe("v3.4.0", 2), 100, "main", "abc1234", isDirty = false)
        assertEquals("3.4.2", v.versionName)
    }

    @Test
    fun `sanitize lowercases collapses and trims`() {
        assertEquals("feature-abc-123", GitVersion.sanitizeBranch("feature/ABC--123"))
        assertEquals("foo-bar", GitVersion.sanitizeBranch("__foo  bar__"))
    }
}
