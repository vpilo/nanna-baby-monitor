package org.vpilo.babymonitor.settings.model

import java.io.File

/**
 * App directory for files which may be lost at any time.
 */
expect fun getCacheDir(): File
