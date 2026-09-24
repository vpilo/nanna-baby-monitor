package org.vpilo.babymonitor.errorreport.data

import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.errorreport.model.DEVELOPER_EMAIL
import org.vpilo.babymonitor.errorreport.model.ReportHandoff
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.net.URLEncoder

private const val TAG = "ErrorReportSender"

/**
 * No mail client honors the `attachment` parameter of `mailto`, so the user attaches the report by hand, from the folder revealed here.
 */
internal actual fun sendErrorReport(
    report: File,
    subject: String,
    body: String,
): ReportHandoff {
    // Without a desktop environment or a xdg handler, these actions are unsupported or throw.
    val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null

    val isMailClientOpened =
        desktop?.isSupported(Desktop.Action.MAIL) == true &&
            runCatching { desktop.mail(makeMailtoUri(subject, body)) }
                .onFailure { Logger.w(TAG, it) { "Unable to open the mail client" } }
                .isSuccess

    if (desktop?.isSupported(Desktop.Action.OPEN) == true) {
        runCatching { desktop.open(report.parentFile) }
            .onFailure { Logger.w(TAG, it) { "Unable to reveal the report" } }
    }

    if (!isMailClientOpened) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(DEVELOPER_EMAIL), null) }
            .onFailure { Logger.w(TAG, it) { "Unable to copy the developer address" } }
    }

    return ReportHandoff(displayPath = report.absolutePath, isSucceeded = isMailClientOpened)
}

private fun makeMailtoUri(
    subject: String,
    body: String,
): URI {
    // URLEncoder encodes for forms, where spaces become '+'.
    fun String.encode(): String = URLEncoder.encode(this, Charsets.UTF_8).replace("+", "%20")
    return URI("mailto:$DEVELOPER_EMAIL?subject=${subject.encode()}&body=${body.encode()}")
}
