package org.vpilo.babymonitor.network.common

import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class RelayTrustManager : X509TrustManager {
    private val pinnedCert: X509Certificate = loadPinnedCert()

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) = Unit

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) {
        if (chain.isNullOrEmpty()) throw CertificateException("No certificate chain")
        if (!chain[0].encoded.contentEquals(pinnedCert.encoded)) {
            throw CertificateException("Relay certificate does not match pinned certificate")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    private companion object {
        fun loadPinnedCert(): X509Certificate {
            val stream =
                checkNotNull(
                    RelayTrustManager::class.java.classLoader.getResourceAsStream("relay.crt"),
                ) { "relay.crt not found in resources" }
            return CertificateFactory.getInstance("X.509").generateCertificate(stream) as X509Certificate
        }
    }
}
