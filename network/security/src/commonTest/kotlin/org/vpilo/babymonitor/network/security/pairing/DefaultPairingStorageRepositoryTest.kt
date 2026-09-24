package org.vpilo.babymonitor.network.security.pairing

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.vpilo.babymonitor.model.settings.SettingId
import org.vpilo.babymonitor.network.model.pairing.PairedDevice
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.settings.LegacyPairedClientsJson
import org.vpilo.babymonitor.settings.model.settings.LegacyPairedServersJson
import org.vpilo.babymonitor.settings.model.settings.PairedDevicesJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class DefaultPairingStorageRepositoryTest {
    @Test
    fun pairedDeviceIsFoundAndListed() =
        runTest {
            val repository = DefaultPairingStorageRepository(FakeSettingsRepository())
            val device = pairedDevice()

            repository.pair(device)

            assertEquals(device, repository.find(Uuid.parse(device.deviceId)))
            assertEquals(listOf(device), repository.pairedDevices.first())
        }

    @Test
    fun pairingTheSamePeerAgainReplacesTheRecord() =
        runTest {
            val repository = DefaultPairingStorageRepository(FakeSettingsRepository())
            val first = pairedDevice()
            val second = first.copy(name = "Renamed", certFingerprint = "b2".repeat(32), sharedSecretBase64 = "bmV3")

            repository.pair(first)
            repository.pair(second)

            assertEquals(listOf(second), repository.pairedDevices.first())
        }

    @Test
    fun unpairRemovesOnlyThatDevice() =
        runTest {
            val repository = DefaultPairingStorageRepository(FakeSettingsRepository())
            val kept = pairedDevice()
            val removed = pairedDevice()
            repository.pair(kept)
            repository.pair(removed)

            repository.unpair(Uuid.parse(removed.deviceId))

            assertNull(repository.find(Uuid.parse(removed.deviceId)))
            assertEquals(listOf(kept), repository.pairedDevices.first())
        }

    @Test
    fun updateNameRenamesTheRecord() =
        runTest {
            val repository = DefaultPairingStorageRepository(FakeSettingsRepository())
            val device = pairedDevice()
            repository.pair(device)

            repository.updateName(Uuid.parse(device.deviceId), "Kitchen")

            assertEquals("Kitchen", repository.find(Uuid.parse(device.deviceId))?.name)
        }

    @Test
    fun legacyPairingsAreClearedAndIgnored() =
        runTest {
            val legacyServers = """[{"deviceId":"${Uuid.random()}","name":"Old","certFingerprint":"x","sharedSecretBase64":"eA=="}]"""
            val legacyClients =
                """[{"deviceId":"${Uuid.random()}","name":"Old","sharedSecretBase64":"eA==","pairedAtEpochMillis":1}]"""
            val settings =
                FakeSettingsRepository(
                    mapOf(
                        Setting.LegacyPairedServersJson.id to legacyServers,
                        Setting.LegacyPairedClientsJson.id to legacyClients,
                    ),
                )
            val repository = DefaultPairingStorageRepository(settings)

            assertEquals(emptyList(), repository.pairedDevices.first())
            assertFalse(settings.values.value.containsKey(Setting.LegacyPairedServersJson.id))
            assertFalse(settings.values.value.containsKey(Setting.LegacyPairedClientsJson.id))
        }

    @Test
    fun corruptJsonReadsAsEmpty() =
        runTest {
            val settings = FakeSettingsRepository(mapOf<SettingId, Any>(Setting.PairedDevicesJson.id to "{not json"))
            val repository = DefaultPairingStorageRepository(settings)

            assertEquals(emptyList(), repository.pairedDevices.first())
            assertNull(repository.find(Uuid.random()))
        }

    private fun pairedDevice() =
        PairedDevice(
            deviceId = Uuid.random().toString(),
            name = "Nursery",
            certFingerprint = "a1".repeat(32),
            sharedSecretBase64 = "c2VjcmV0",
        )
}
