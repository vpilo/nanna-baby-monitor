package org.vpilo.babymonitor.network.common

object Endpoints {
    const val CONTROL = "/control"
    const val STREAM_AUDIO = "/audio"
    const val STREAM_VIDEO = "/video"

    object Relay {
        const val CLIENT_DISCOVERY = "/relay/discovery"
        const val CLIENT_CONTROL = "/relay/client/control"
        const val CLIENT_AUDIO = "/relay/client/audio"
        const val CLIENT_VIDEO = "/relay/client/video"

        const val SERVER_REGISTRATION = "/relay/server"
        const val SERVER_CONTROL = "/relay/server/control"
        const val SERVER_AUDIO = "/relay/server/audio"
        const val SERVER_VIDEO = "/relay/server/video"
    }
}
