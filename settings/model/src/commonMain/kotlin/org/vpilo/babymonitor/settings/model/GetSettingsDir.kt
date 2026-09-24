package org.vpilo.babymonitor.settings.model

import java.io.File

/**
 * App directory for persistent settings files.
 */
expect fun getSettingsDir(): File
