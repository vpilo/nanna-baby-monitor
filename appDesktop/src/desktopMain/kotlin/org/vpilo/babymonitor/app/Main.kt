package org.vpilo.babymonitor.app

import org.vpilo.babymonitor.settings.model.getSettingsDir
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE

private const val LOCK_FILE_NAME = "instance.lock"

private fun babyMonitorMain() {
    val settingsDir = Files.createDirectories(getSettingsDir().toPath())
    FileChannel.open(settingsDir.resolve(LOCK_FILE_NAME), CREATE, WRITE).use { channel ->
        val lock =
            channel.tryLock()
                ?: run {
                    launchAlreadyRunningDialog()
                    return
                }

        lock.use {
            val pid =
                ByteBuffer.wrap(
                    ProcessHandle
                        .current()
                        .pid()
                        .toString()
                        .toByteArray(),
                )
            channel.truncate(0)
            while (pid.hasRemaining()) {
                channel.write(pid)
            }
            launchApp()
        }
    }
}

@Suppress("unused")
fun main(args: Array<String>) {
    babyMonitorMain()
}

@Suppress("MemberNameEqualsClassName")
class Main private constructor() {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) = babyMonitorMain()
    }
}
