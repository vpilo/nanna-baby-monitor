package org.vpilo.babymonitor.relay

import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.network.model.RelayConfiguration
import org.vpilo.babymonitor.settings.model.getSettingsDir
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

/**
 * Reads the relay's hostname and access passphrase from `relay.conf` in the settings directory.
 */
fun loadRelayConfiguration(): RelayConfigurationOutcome {
    val file = File(getSettingsDir(), CONFIGURATION_FILE_NAME)
    if (!file.exists()) {
        writeTemplate(file)
        Logger.e(TAG) { "No relay configuration file ${file.path}" }
        return RelayConfigurationOutcome.TemplateCreated(file)
    }

    val entries =
        file
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith(COMMENT_MARKER) }
            .mapNotNull { line -> line.split("=", limit = 2).takeIf { it.size == 2 } }
            .associate { (key, value) -> key.trim() to value.trim() }

    val host = entries[HOST_KEY].orEmpty()
    val passphrase = entries[PASSPHRASE_KEY].orEmpty()
    val blankKeys = listOf(HOST_KEY to host, PASSPHRASE_KEY to passphrase).filter { it.second.isBlank() }.map { it.first }

    if (blankKeys.isNotEmpty()) {
        Logger.e(TAG) { "Incomplete relay configuration file ${file.path}: $blankKeys" }
        return RelayConfigurationOutcome.Incomplete(file, blankKeys)
    }

    Logger.i(TAG) { "Loaded relay configuration from ${file.path}" }
    return RelayConfigurationOutcome.Loaded(RelayConfiguration(host = host, passphrase = passphrase))
}

private fun writeTemplate(file: File) {
    val directory = file.parentFile
    check(directory.exists() || directory.mkdirs()) { "Failed to create ${directory.path}" }
    file.writeText(
        """
        # Baby Monitor relay configuration.

        # Public hostname or IP clients reach this relay on.
        $HOST_KEY=

        # Shared secret. Choose a long, hard-to-guess value and enter it in every Baby Monitor app.
        $PASSPHRASE_KEY=
        """.trimIndent() + "\n",
    )

    // Not really safe but eh
    runCatching {
        Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"))
    }
}

private const val CONFIGURATION_FILE_NAME = "relay.conf"
private const val COMMENT_MARKER = '#'
private const val HOST_KEY = "host"
private const val PASSPHRASE_KEY = "passphrase"

private const val TAG = "RelayConfiguration"
