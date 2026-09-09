package org.vpilo.babymonitor.versioning

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

/** Registered on the project by the `babymonitor.git-version` plugin. */
abstract class GitVersionExtension
    @Inject
    constructor(
        providers: ProviderFactory,
    ) {
        /**
         * Whether this build is meant to produce a release, via `-Pbabymonitor.release=true`.
         */
        val isReleaseBuild: Provider<Boolean> =
            providers.gradleProperty(RELEASE_PROPERTY).map(String::toBoolean).orElse(false)

        /** The git-derived [VersionInfo] for this build. */
        val info: Provider<VersionInfo> =
            providers.of(GitVersionValueSource::class.java) {
                parameters.releaseBuild.set(isReleaseBuild)
            }

        private companion object {
            private const val RELEASE_PROPERTY = "babymonitor.release"
        }
    }
