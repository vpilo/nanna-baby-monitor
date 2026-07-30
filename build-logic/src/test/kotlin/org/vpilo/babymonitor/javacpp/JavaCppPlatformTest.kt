package org.vpilo.babymonitor.javacpp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JavaCppPlatformTest {
    @Test
    fun `resolves the JVM's own arch names`() {
        assertEquals(JavaCppPlatform.LinuxX8664, JavaCppPlatform.fromHost("Linux", "amd64"))
        assertEquals(JavaCppPlatform.LinuxArm64, JavaCppPlatform.fromHost("Linux", "aarch64"))
        assertEquals(JavaCppPlatform.MacosArm64, JavaCppPlatform.fromHost("Mac OS X", "aarch64"))
        assertEquals(JavaCppPlatform.MacosX8664, JavaCppPlatform.fromHost("Mac OS X", "x86_64"))
        assertEquals(JavaCppPlatform.WindowsX8664, JavaCppPlatform.fromHost("Windows 11", "amd64"))
    }

    @Test
    fun `host detection is case insensitive`() {
        assertEquals(JavaCppPlatform.LinuxX8664, JavaCppPlatform.fromHost("LINUX", "X86_64"))
    }

    @Test
    fun `unsupported host reports the override property`() {
        val error = assertFailsWith<IllegalStateException> { JavaCppPlatform.fromHost("Linux", "ppc64le") }
        assertEquals(true, error.message?.contains(JavaCppPlatform.GRADLE_PLATFORM_PROPERTY))
    }

    @Test
    fun `windows on arm has no ffmpeg build`() {
        assertFailsWith<IllegalStateException> { JavaCppPlatform.fromHost("Windows 11", "aarch64") }
    }

    @Test
    fun `classifiers round trip`() {
        JavaCppPlatform.entries.forEach {
            assertEquals(it, JavaCppPlatform.fromClassifier(it.classifier))
        }
    }

    @Test
    fun `unknown classifier lists the supported ones`() {
        val error = assertFailsWith<IllegalStateException> { JavaCppPlatform.fromClassifier("linux-x64") }
        assertEquals(true, error.message?.contains("linux-x86_64"))
    }
}
