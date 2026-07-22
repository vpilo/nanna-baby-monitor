package org.vpilo.babymonitor.network.common.crypto

import org.vpilo.babymonitor.network.common.crypto.internal.hkdfSha256
import org.vpilo.babymonitor.network.common.crypto.internal.hmacSha256
import org.vpilo.babymonitor.network.common.crypto.internal.verifyHmacSha256
import java.security.SecureRandom

private val secureRandom = SecureRandom()

private val CLIENT_PROOF_LABEL = "c".encodeToByteArray()
private val SERVER_PROOF_LABEL = "s".encodeToByteArray()
private val CLIENT_TO_SERVER_INFO = "c2s".encodeToByteArray()
private val SERVER_TO_CLIENT_INFO = "s2c".encodeToByteArray()
private const val SESSION_KEY_SIZE_BYTES = 32
private const val SESSION_SALT_SIZE_BYTES = 32

fun generateSessionSalt(): ByteArray = ByteArray(SESSION_SALT_SIZE_BYTES).also(secureRandom::nextBytes)

suspend fun computeClientHandshakeProof(
    sharedSecret: ByteArray,
    clientSalt: ByteArray,
): ByteArray = hmacSha256(sharedSecret, CLIENT_PROOF_LABEL + clientSalt)

suspend fun verifyClientHandshakeProof(
    sharedSecret: ByteArray,
    clientSalt: ByteArray,
    proof: ByteArray,
): Boolean = verifyHmacSha256(sharedSecret, CLIENT_PROOF_LABEL + clientSalt, proof)

suspend fun computeServerHandshakeProof(
    sharedSecret: ByteArray,
    clientSalt: ByteArray,
    serverSalt: ByteArray,
): ByteArray = hmacSha256(sharedSecret, SERVER_PROOF_LABEL + clientSalt + serverSalt)

suspend fun verifyServerHandshakeProof(
    sharedSecret: ByteArray,
    clientSalt: ByteArray,
    serverSalt: ByteArray,
    proof: ByteArray,
): Boolean = verifyHmacSha256(sharedSecret, SERVER_PROOF_LABEL + clientSalt + serverSalt, proof)

suspend fun deriveSessionKeys(
    sharedSecret: ByteArray,
    clientSalt: ByteArray,
    serverSalt: ByteArray,
): SessionKeys {
    val combinedSalt = clientSalt + serverSalt
    return SessionKeys(
        clientToServer = hkdfSha256(sharedSecret, combinedSalt, CLIENT_TO_SERVER_INFO, SESSION_KEY_SIZE_BYTES),
        serverToClient = hkdfSha256(sharedSecret, combinedSalt, SERVER_TO_CLIENT_INFO, SESSION_KEY_SIZE_BYTES),
    )
}

fun SessionKeys.asServerCipher(associatedData: ByteArray): SessionFrameCipher =
    SessionFrameCipher(sendKey = serverToClient, receiveKey = clientToServer, associatedData = associatedData)

fun SessionKeys.asClientCipher(associatedData: ByteArray): SessionFrameCipher =
    SessionFrameCipher(sendKey = clientToServer, receiveKey = serverToClient, associatedData = associatedData)
