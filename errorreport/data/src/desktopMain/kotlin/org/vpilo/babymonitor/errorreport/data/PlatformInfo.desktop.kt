package org.vpilo.babymonitor.errorreport.data

internal actual fun retrievePlatformMetadata(): Map<String, String> =
    listOf("os.name", "os.arch", "java.version").associateWith { System.getProperty(it).orEmpty() }

internal actual fun currentProcessId(): Long = ProcessHandle.current().pid()
