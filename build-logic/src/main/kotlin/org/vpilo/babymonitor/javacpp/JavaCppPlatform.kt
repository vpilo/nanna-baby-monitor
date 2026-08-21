package org.vpilo.babymonitor.javacpp

/**
 * A platform for which JavaCPP publishes native artifacts for.
 *
 * Only the desktop platforms the app targets are listed: Android does not use JavaCPP.
 *
 * The `-platform` Bytedeco artifacts pull in every platform classifier at once, wasting a lot of space. So this is needed to make the
 * built app only depend on the single right artifact instead.
 */
enum class JavaCppPlatform(
    val classifier: String,
) {
    LinuxX8664("linux-x86_64"),
    LinuxArm64("linux-arm64"),
    MacosX8664("macosx-x86_64"),
    MacosArm64("macosx-arm64"),
    WindowsX8664("windows-x86_64"),
    ;

    companion object {
        fun fromClassifier(classifier: String): JavaCppPlatform =
            entries.firstOrNull { it.classifier == classifier }
                ?: error("Unknown JavaCPP platform '$classifier'. Supported: ${supportedClassifiers()}")

        /** Resolves the platform classifier of the given host machine. */
        fun fromHost(
            osName: String,
            osArch: String,
        ): JavaCppPlatform {
            val os = osName.lowercase()
            val arch = osArch.lowercase()
            return when {
                os.startsWith("linux") -> {
                    when {
                        arch.isArm64() -> LinuxArm64
                        arch.isX8664() -> LinuxX8664
                        else -> null
                    }
                }

                os.startsWith("mac") || os.startsWith("darwin") -> {
                    when {
                        arch.isArm64() -> MacosArm64
                        arch.isX8664() -> MacosX8664
                        else -> null
                    }
                }

                os.startsWith("windows") -> {
                    when {
                        arch.isX8664() -> WindowsX8664
                        else -> null
                    }
                }

                else -> {
                    null
                }
            }
                ?: error(
                    "No JavaCPP native artifacts for host '$osName' '$osArch'." +
                        " Rebuild with -P$GRADLE_PLATFORM_PROPERTY=<classifier> to choose a supported classifier:" +
                        " ${supportedClassifiers()}",
                )
        }

        private fun supportedClassifiers() = entries.joinToString { it.classifier }

        private fun String.isX8664() = this in listOf("x86_64", "amd64")

        private fun String.isArm64() = this in listOf("aarch64", "arm64")

        /**
         * Gradle property to override the host platform, so a build can target a different host platform.
         * Only some combinations are unsupported.
         */
        const val GRADLE_PLATFORM_PROPERTY = "javacpp.platform"
    }
}
