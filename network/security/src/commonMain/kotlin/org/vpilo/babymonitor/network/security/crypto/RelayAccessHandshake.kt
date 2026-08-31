package org.vpilo.babymonitor.network.security.crypto

import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.network.security.crypto.internal.hmacSha256
import org.vpilo.babymonitor.network.security.crypto.internal.pbkdf2Sha256
import org.vpilo.babymonitor.network.security.crypto.internal.verifyHmacSha256
import java.security.SecureRandom

private val secureRandom = SecureRandom()

private val CONNECTOR_PROOF_LABEL = "rc".encodeToByteArray()
private val RELAY_PROOF_LABEL = "rr".encodeToByteArray()

/**
 * Fixed because the passphrase is shared by devices that never talk to each other before the handshake, so
 * there is nowhere to put a per-installation salt. Rainbow tables are still impractical against a
 * relay-specific label at this iteration count.
 */
private val RELAY_KEY_SALT = "org.vpilo.babymonitor.relay.access.v1".encodeToByteArray()
private const val RELAY_KEY_ITERATIONS = 210_000
private const val RELAY_KEY_SIZE_BYTES = 32
private const val RELAY_NONCE_SIZE_BYTES = 32

/** Turns the relay passphrase into the access key. Slow by design - derive once and cache. */
suspend fun deriveRelayAccessKey(passphrase: String): ByteArray =
    pbkdf2Sha256(passphrase.encodeToByteArray(), RELAY_KEY_SALT, RELAY_KEY_ITERATIONS, RELAY_KEY_SIZE_BYTES)

fun generateRelayNonce(): ByteArray = ByteArray(RELAY_NONCE_SIZE_BYTES).also(secureRandom::nextBytes)

suspend fun computeConnectorAccessProof(
    accessKey: ByteArray,
    role: AppRole,
    endpoint: String,
    relayCertFingerprint: String,
    connectorNonce: ByteArray,
    relayNonce: ByteArray,
): ByteArray =
    hmacSha256(
        accessKey,
        accessTranscript(CONNECTOR_PROOF_LABEL, role, endpoint, relayCertFingerprint, connectorNonce, relayNonce),
    )

suspend fun verifyConnectorAccessProof(
    accessKey: ByteArray,
    role: AppRole,
    endpoint: String,
    relayCertFingerprint: String,
    connectorNonce: ByteArray,
    relayNonce: ByteArray,
    proof: ByteArray,
): Boolean =
    verifyHmacSha256(
        accessKey,
        accessTranscript(CONNECTOR_PROOF_LABEL, role, endpoint, relayCertFingerprint, connectorNonce, relayNonce),
        proof,
    )

suspend fun computeRelayAccessProof(
    accessKey: ByteArray,
    role: AppRole,
    endpoint: String,
    relayCertFingerprint: String,
    connectorNonce: ByteArray,
    relayNonce: ByteArray,
): ByteArray =
    hmacSha256(
        accessKey,
        accessTranscript(RELAY_PROOF_LABEL, role, endpoint, relayCertFingerprint, connectorNonce, relayNonce),
    )

suspend fun verifyRelayAccessProof(
    accessKey: ByteArray,
    role: AppRole,
    endpoint: String,
    relayCertFingerprint: String,
    connectorNonce: ByteArray,
    relayNonce: ByteArray,
    proof: ByteArray,
): Boolean =
    verifyHmacSha256(
        accessKey,
        accessTranscript(RELAY_PROOF_LABEL, role, endpoint, relayCertFingerprint, connectorNonce, relayNonce),
        proof,
    )

/**
 * [relayCertFingerprint] is what makes the proofs channel-bound: an interceptor terminating TLS presents its own
 * certificate, so the fingerprint it hashes differs from the one the relay hashes and both proofs fail.
 *
 * The variable-length text fields are delimited; the two nonces are fixed size, so appending them raw stays
 * unambiguous.
 */
private fun accessTranscript(
    label: ByteArray,
    role: AppRole,
    endpoint: String,
    relayCertFingerprint: String,
    connectorNonce: ByteArray,
    relayNonce: ByteArray,
): ByteArray =
    label +
        listOf(role.name, endpoint, relayCertFingerprint).joinToString("|").encodeToByteArray() +
        connectorNonce +
        relayNonce
