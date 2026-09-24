package org.vpilo.babymonitor.errorreport.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.errorreport.model.DEVELOPER_EMAIL
import org.vpilo.babymonitor.errorreport.model.ReportHandoff
import java.io.File

// Must match the authority of the app's FileProvider.
private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".errorreport.fileprovider"

internal actual fun sendErrorReport(
    report: File,
    subject: String,
    body: String,
): ReportHandoff {
    val context = KoinPlatform.getKoin().get<Context>()
    // A file:// Uri would throw FileUriExposedException.
    val uri = FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX, report)
    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_EMAIL, arrayOf(DEVELOPER_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(
        Intent.createChooser(sendIntent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    return ReportHandoff(displayPath = null, isMailClientOpened = true)
}
