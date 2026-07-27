package org.vpilo.babymonitor.versioning

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

/** Registered on the project by the `babymonitor.git-version` plugin. */
abstract class GitVersionExtension @Inject constructor(providers: ProviderFactory) {
    /** The git-derived [VersionInfo] for this build. */
    val info: Provider<VersionInfo> = providers.of(GitVersionValueSource::class.java) {}
}
