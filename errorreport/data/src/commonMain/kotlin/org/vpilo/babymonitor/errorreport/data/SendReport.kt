package org.vpilo.babymonitor.errorreport.data

import org.vpilo.babymonitor.errorreport.model.ReportHandoff
import java.io.File

/**
 * Hand the [report] over to the user's mail client, in an email addressed to the developer.
 */
internal expect fun sendErrorReport(
    report: File,
    subject: String,
    body: String,
): ReportHandoff
