package org.vpilo.babymonitor.app.navigation

import kotlinx.serialization.Serializable
import org.vpilo.babymonitor.model.repository.DeviceId

sealed interface Route {
    @Serializable
    data object RootNavGraph : Route

    @Serializable
    data object Onboarding : Route

    @Serializable
    data object AppRoleChooser : Route

    @Serializable
    data object Menu : Route

    @Serializable
    data object Quit : Route

    @Serializable
    data object AppPermissionCheck : Route

    @Serializable
    data object CameraPermissionCheck : Route

    @Serializable
    data class CameraSelection(
        // When given, this device will be connected to.
        val deviceId: String? = null,
    ) : Route

    @Serializable
    data object ClientHome : Route

    @Serializable
    data object ServerHome : Route

    @Serializable
    data object ServerPairing : Route

    @Serializable
    data object PairedDevices : Route

    @Serializable
    data class ClientPairing(
        val deviceId: String,
    ) : Route
}
