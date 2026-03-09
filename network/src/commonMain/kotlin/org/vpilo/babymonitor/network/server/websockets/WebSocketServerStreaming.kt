package org.vpilo.babymonitor.network.server.websockets

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.flow.onCompletion
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.model.VideoFeedRepository
import org.vpilo.babymonitor.network.client.VideoFeedReceiverRepository

internal suspend fun DefaultWebSocketServerSession.webSocketServerStreaming() {
    val repository = checkNotNull(KoinPlatform.getKoin().getOrNull<VideoFeedRepository>()) {
        "Dependency injection error - VideoFeedRepository not found"
    }
    check(repository !is VideoFeedReceiverRepository) { "Dependency injection error - wrong repository" }


    repository.chunks
        // FIXME SharedFlows don't complete, need to close manually instead of this. maybe make a flow of repo states instead,
        //  could be useful later, e.g. to show if there's no activity to send.
        .onCompletion {
            close(CloseReason(CloseReason.Codes.GOING_AWAY, "Camera feed ended."))
        }
        .collect {
            TODO()
        }
}
