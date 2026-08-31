package org.vpilo.babymonitor.network.security.relay

import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.test.runTest
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.network.model.Constants
import org.vpilo.babymonitor.network.model.RelayConfiguration
import org.vpilo.babymonitor.network.security.crypto.deriveRelayAccessKey
import org.vpilo.babymonitor.network.security.crypto.sha256Fingerprint
import java.net.ServerSocket
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val ENDPOINT = "/relay/discovery"
private const val PASSPHRASE = "a shared relay passphrase"
private const val KEY_ALIAS = "test-relay"
private const val KEY_PASSWORD = "test-password"
private const val GREETING = "you are in"

/**
 * Drives [relayWss] against a real TLS Ktor server running [verifyRelayAccess], so the wire format, the frame
 * ordering, and - most importantly - the two sides independently arriving at the same certificate fingerprint
 * are all exercised on the stack that ships.
 */
class RelayAccessRoundTripTest {
    private var server: EmbeddedServer<*, *>? = null

    @AfterTest
    fun tearDown() {
        server?.stop(0, 0, TimeUnit.MILLISECONDS)
        server = null
    }

    @Test
    fun aConnectorHoldingThePassphraseIsLetThrough() =
        runTest {
            val port = startRelay(PASSPHRASE)
            var greeting: String? = null

            relayWss(
                configuration = RelayConfiguration(host = "127.0.0.1", passphrase = PASSPHRASE),
                role = AppRole.CLIENT,
                endpoint = ENDPOINT,
                port = port,
            ) {
                greeting = (incoming.receive() as Frame.Text).readText()
            }

            assertEquals(GREETING, greeting)
        }

    @Test
    fun aConnectorWithTheWrongPassphraseNeverRunsItsSessionBlock() =
        runTest {
            val port = startRelay(PASSPHRASE)
            var sessionRan = false

            relayWss(
                configuration = RelayConfiguration(host = "127.0.0.1", passphrase = "not the passphrase"),
                role = AppRole.CLIENT,
                endpoint = ENDPOINT,
                port = port,
            ) {
                sessionRan = true
            }

            assertFalse(sessionRan, "The session block must not run when the relay rejected us")
        }

    @Test
    fun theRelayRejectsAConnectorClaimingADifferentRole() =
        runTest {
            // The relay verifies against the role its route serves; a connector announcing another one fails.
            val port = startRelay(PASSPHRASE, expectedRole = AppRole.SERVER)
            var sessionRan = false

            relayWss(
                configuration = RelayConfiguration(host = "127.0.0.1", passphrase = PASSPHRASE),
                role = AppRole.CLIENT,
                endpoint = ENDPOINT,
                port = port,
            ) {
                sessionRan = true
            }

            assertFalse(sessionRan)
        }

    /**
     * Stands in for an interceptor: the connector hashes the certificate it was actually served, so a relay
     * verifying against any other certificate - as would happen if the TLS session terminated somewhere else -
     * cannot agree on a transcript.
     */
    @Test
    fun aRelayVerifyingAgainstAnotherCertificateIsNotTrusted() =
        runTest {
            var sessionRan = false
            val port = startRelay(PASSPHRASE, fingerprintOverride = "00".repeat(32))

            relayWss(
                configuration = RelayConfiguration(host = "127.0.0.1", passphrase = PASSPHRASE),
                role = AppRole.CLIENT,
                endpoint = ENDPOINT,
                port = port,
            ) {
                sessionRan = true
            }

            assertFalse(sessionRan)
        }

    private suspend fun startRelay(
        passphrase: String,
        expectedRole: AppRole = AppRole.CLIENT,
        fingerprintOverride: String? = null,
    ): Int {
        val port = ServerSocket(0).use { it.localPort }
        val keyStore =
            buildKeyStore {
                certificate(KEY_ALIAS) {
                    password = KEY_PASSWORD
                    domains = listOf(Constants.TLS_SERVER_NAME, "127.0.0.1")
                    keySizeInBits = 2048
                    daysValid = 1
                }
            }
        val fingerprint = fingerprintOverride ?: (keyStore.getCertificate(KEY_ALIAS) as X509Certificate).sha256Fingerprint()
        val accessKey = deriveRelayAccessKey(passphrase)

        server =
            embeddedServer(
                factory = Netty,
                configure = {
                    sslConnector(
                        keyStore = keyStore,
                        keyAlias = KEY_ALIAS,
                        keyStorePassword = { KEY_PASSWORD.toCharArray() },
                        privateKeyPassword = { KEY_PASSWORD.toCharArray() },
                    ) {
                        host = "127.0.0.1"
                        this.port = port
                    }
                },
                module = {
                    install(WebSockets)
                    routing {
                        webSocket(ENDPOINT) {
                            if (!verifyRelayAccess(accessKey, expectedRole, ENDPOINT, fingerprint)) return@webSocket
                            send(Frame.Text(GREETING))
                        }
                    }
                },
            ).start(wait = false)
        return port
    }
}
