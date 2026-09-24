package org.vpilo.babymonitor.settings.model

import java.io.File

actual fun getSettingsDir(): File = File(homeDir, ".config/$APP_DIR_NAME")
