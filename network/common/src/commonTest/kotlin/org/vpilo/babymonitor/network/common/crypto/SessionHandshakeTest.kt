package org.vpilo.babymonitor.network.common.crypto

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionHandshakeTest {
    @Test
    fun bothSidesDeriveMatchingSessionKeysAndValidProofs() =
        runTest {
            val sharedSecret = Random.nextBytes(32)
            val clientSalt = generateSessionSalt()
            val serverSalt = generateSessionSalt()

            val clientProof = computeClientHandshakeProof(sharedSecret, clientSalt)
            assertTrue(verifyClientHandshakeProof(sharedSecret, clientSalt, clientProof))

            val serverProof = computeServerHandshakeProof(sharedSecret, clientSalt, serverSalt)
            assertTrue(verifyServerHandshakeProof(sharedSecret, clientSalt, serverSalt, serverProof))

            val clientKeys = deriveSessionKeys(sharedSecret, clientSalt, serverSalt)
            val serverKeys = deriveSessionKeys(sharedSecret, clientSalt, serverSalt)
            assertContentEquals(clientKeys.clientToServer, serverKeys.clientToServer)
            assertContentEquals(clientKeys.serverToClient, serverKeys.serverToClient)
            assertFalse(clientKeys.clientToServer.contentEquals(clientKeys.serverToClient))
        }

    @Test
    fun proofsFailUnderAWrongSharedSecret() =
        runTest {
            val realSecret = Random.nextBytes(32)
            val wrongSecret = Random.nextBytes(32)
            val clientSalt = generateSessionSalt()
            val serverSalt = generateSessionSalt()

            val clientProof = computeClientHandshakeProof(realSecret, clientSalt)
            assertFalse(verifyClientHandshakeProof(wrongSecret, clientSalt, clientProof))

            val serverProof = computeServerHandshakeProof(realSecret, clientSalt, serverSalt)
            assertFalse(verifyServerHandshakeProof(wrongSecret, clientSalt, serverSalt, serverProof))
        }

    @Test
    fun serverProofDoesNotVerifyAgainstADifferentClientSalt() =
        runTest {
            val sharedSecret = Random.nextBytes(32)
            val serverSalt = generateSessionSalt()
            val serverProof = computeServerHandshakeProof(sharedSecret, generateSessionSalt(), serverSalt)

            assertFalse(verifyServerHandshakeProof(sharedSecret, generateSessionSalt(), serverSalt, serverProof))
        }

    @Test
    fun differentSaltsProduceDifferentSessionKeys() =
        runTest {
            val sharedSecret = Random.nextBytes(32)
            val keysA = deriveSessionKeys(sharedSecret, generateSessionSalt(), generateSessionSalt())
            val keysB = deriveSessionKeys(sharedSecret, generateSessionSalt(), generateSessionSalt())

            assertFalse(keysA.clientToServer.contentEquals(keysB.clientToServer))
        }

    @Test
    fun frameCiphersSealAndOpenAcrossSides() =
        runTest {
            val sharedSecret = Random.nextBytes(32)
            val clientSalt = generateSessionSalt()
            val serverSalt = generateSessionSalt()
            val aad = "device-42|video".encodeToByteArray()

            val serverCipher = deriveServerSessionCipher(sharedSecret, clientSalt, serverSalt, aad)
            val clientCipher = deriveClientSessionCipher(sharedSecret, clientSalt, serverSalt, aad)

            val wireFrame = serverCipher.seal("frame-1".encodeToByteArray())
            assertContentEquals("frame-1".encodeToByteArray(), clientCipher.open(wireFrame))

            val wireFrame2 = serverCipher.seal("frame-2".encodeToByteArray())
            assertContentEquals("frame-2".encodeToByteArray(), clientCipher.open(wireFrame2))
        }

    @Test
    fun frameCipherRejectsTamperedWireFrames() =
        runTest {
            val sharedSecret = Random.nextBytes(32)
            val clientSalt = generateSessionSalt()
            val serverSalt = generateSessionSalt()
            val aad = "device-42|video".encodeToByteArray()

            val wireFrame = deriveServerSessionCipher(sharedSecret, clientSalt, serverSalt, aad).seal("frame".encodeToByteArray())
            wireFrame[wireFrame.size - 1] = wireFrame[wireFrame.size - 1].inc()

            kotlin.test.assertFailsWith<Throwable> {
                deriveClientSessionCipher(sharedSecret, clientSalt, serverSalt, aad).open(wireFrame)
            }
        }
}
