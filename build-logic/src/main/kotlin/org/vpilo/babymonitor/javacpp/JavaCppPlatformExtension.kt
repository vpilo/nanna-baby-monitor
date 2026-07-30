package org.vpilo.babymonitor.javacpp

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

/** Registered on the project by the `babymonitor.javacpp-platform` plugin. */
abstract class JavaCppPlatformExtension @Inject constructor(
    providers: ProviderFactory,
) {
    /** The single platform this build will bundle native libraries for. */
    val target: Provider<JavaCppPlatform> =
        with(providers) {
            gradleProperty(JavaCppPlatform.GRADLE_PLATFORM_PROPERTY)
                .map { JavaCppPlatform.fromClassifier(it.trim()) }
                .orElse(
                    systemProperty("os.name").zip(systemProperty("os.arch")) { name, arch ->
                        JavaCppPlatform.fromHost(name, arch)
                    },
                )
        }

    /** Maven classifier of [target], to select the native artifact of a Bytedeco module. */
    val classifier: Provider<String> = target.map { it.classifier }
}
