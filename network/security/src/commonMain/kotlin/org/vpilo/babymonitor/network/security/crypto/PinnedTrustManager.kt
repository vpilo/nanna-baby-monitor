package org.vpilo.babymonitor.network.security.crypto

import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Client-side TLS trust for a single Nanna Baby Monitor connection.
 *
 * With [expectedFingerprint] `null`: trust-on-first-use - accepts any presented certificate but records it in
 * [capturedCertificate] so the caller can bind the actual negotiated certificate into a transcript. Used for the
 * `/pair` connection, where the server isn't trusted yet, and for relay connections, where the relay is
 * authenticated by the channel-bound access handshake rather than by PKI.
 *
 * With [expectedFingerprint] set: strict pinning - rejects any certificate whose SHA-256 fingerprint doesn't
 * match. Used for every LAN connection after pairing.
 *
 * One instance serves exactly one connection: [capturedCertificate] would otherwise race between them.
 */
@Suppress("CustomX509TrustManager")
class PinnedTrustManager(
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
