package org.vpilo.babymonitor.network.client.pairing

import kotlinx.coroutines.test.runTest
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.model.pairing.ClientPairingFailureCause
import org.vpilo.babymonitor.network.model.pairing.ClientPairingState
import org.vpilo.babymonitor.network.model.pairing.Pin
import java.io.IOException
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class DefaultClientPairingRepositoryTest {
    @Test
    fun anUnloadableIdentityFailsPairingInsteadOfThrowing() =
        runTest {
            val repository = DefaultClientPairingRepository(loadIdentity = { throw IOException("Keystore was tampered with") })
            val server = Device.LocalServer(Uuid.random(), "Nursery", setOf(InetAddress.getLoopbackAddress()))

            val outcome = repository.pairWith(server, Device.Client(Uuid.random(), "Phone"), Pin.generate())

            assertEquals(ClientPairingState.Failure(ClientPairingFailureCause.CONNECTION_FAILED), outcome)
        }
}
