package org.vpilo.babymonitor.relay

import org.vpilo.babymonitor.network.model.RelayConfiguration
import java.io.File

sealed interface RelayConfigurationOutcome {
    data class Loaded(
        val configuration: RelayConfiguration,
    ) : RelayConfigurationOutcome

    data class TemplateCreated(
        val path: File,
    ) : RelayConfigurationOutcome

    data class Incomplete(
        val path: File,
        val blankKeys: List<String>,
    ) : RelayConfigurationOutcome
}
