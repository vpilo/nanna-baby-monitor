package org.vpilo.babymonitor.network.common.crypto

import java.security.MessageDigest
import java.security.cert.X509Certificate

/**
 * SHA-256 fingerprint of this certificate's DER encoding, as lowercase hex with no separators.
 * This is the pinning anchor clients store after pairing (see [PairingQrPayload] and the pairing
 * transcript in the pairing protocol task).
 */
fun X509Certificate.sha256Fingerprint(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(encoded)
    return digest.joinToString("") { "%02x".format(it) }
}
