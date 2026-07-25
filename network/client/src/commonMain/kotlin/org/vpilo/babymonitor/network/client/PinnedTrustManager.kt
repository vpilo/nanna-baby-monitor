package org.vpilo.babymonitor.network.client

import org.vpilo.babymonitor.network.security.crypto.sha256Fingerprint
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Client-side TLS trust for a single LAN connection to a Baby Monitor server.
 *
 * With [expectedFingerprint] `null`: trust-on-first-use — accepts any presented certificate (used only for
 * the `/pair` connection, where the server isn't trusted yet) but records it in [capturedCertificate] so the
 * caller can bind the actual negotiated certificate into the pairing transcript.
 *
 * With [expectedFingerprint] set: strict pinning — rejects any certificate whose SHA-256 fingerprint doesn't
 * match (used for every connection after pairing; see Task 11).
 */
internal class PinnedTrustManager(
    private val expectedFingerprint: String?,
) : X509TrustManager {
    var capturedCertificate: X509Certificate? = null
        private set

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) = Unit

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) {
        val presented = chain?.firstOrNull() ?: throw CertificateException("No certificate presented")
        capturedCertificate = presented
        val expected = expectedFingerprint ?: return
        val actual = presented.sha256Fingerprint()
        if (actual != expected) {
            throw CertificateException("Certificate fingerprint mismatch: expected $expected, got $actual")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
