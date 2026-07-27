package org.vpilo.babymonitor.network.security.crypto

import io.ktor.network.tls.certificates.buildKeyStore
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CertificateFingerprintTest {
    @Test
    fun sameCertificateProducesTheSameFingerprint() {
        val cert = selfSignedTestCertificate()

        assertEquals(cert.sha256Fingerprint(), cert.sha256Fingerprint())
        assertEquals(64, cert.sha256Fingerprint().length)
        assertEquals(cert.sha256Fingerprint(), cert.sha256Fingerprint().lowercase())
    }

    @Test
    fun differentCertificatesProduceDifferentFingerprints() {
        val certA = selfSignedTestCertificate()
        val certB = selfSignedTestCertificate()

        assertNotEquals(certA.sha256Fingerprint(), certB.sha256Fingerprint())
    }

    private fun selfSignedTestCertificate(): X509Certificate {
        // Built with plain JDK sun.security.x509 test scaffolding is avoided on purpose (see Task 7's
        // discussion of JDK internals); instead this reuses the same buildKeyStore helper Task 7 wires
        // into production code, keeping the test aligned with what actually ships.
        val keyStore =
            buildKeyStore {
                certificate("test") {
                    password = "test-password"
                    domains = listOf("localhost")
                    keySizeInBits = 2048
                    daysValid = 1
                }
            }
        return keyStore.getCertificate("test") as X509Certificate
    }
}
