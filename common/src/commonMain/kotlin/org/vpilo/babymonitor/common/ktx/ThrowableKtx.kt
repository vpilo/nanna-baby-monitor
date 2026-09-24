package org.vpilo.babymonitor.common.ktx

import org.vpilo.babymonitor.common.BuildInfo
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

private const val REDACTED_MESSAGE = "<redacted>"
private const val NO_MESSAGE = "No message"

/**
 * Hide messages for exceptions showing IPs or host names.
 */
private val Throwable.isSensitive: Boolean
    get() =
        when (this) {
            is UnknownHostException,
            is SocketException,
            is SocketTimeoutException,
            is SSLException,
            is CertificateException,
                -> true

            else -> false
        }

fun Throwable.prettify(): String {
    val exceptionMessage =
        when {
            BuildInfo.IS_DEBUG -> message
            isSensitive -> REDACTED_MESSAGE
            else -> message
        }
    return "${this::class.simpleName}: ${exceptionMessage ?: NO_MESSAGE}"
}
