package org.vpilo.babymonitor.network.server.websockets

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.onCompletion
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.model.repository.StreamingVideoRepository

internal suspend fun DefaultWebSocketServerSession.videoStreamingServerWebSocket() {
    val repository = KoinPlatform.getKoin().get<StreamingVideoRepository>()

    repository.chunks
        // FIXME SharedFlows don't complete, need to close manually instead of this. maybe make a flow of repo states instead,
        //  could be useful later, e.g. to show if there's no activity to send.
        .onCompletion {
            close(CloseReason(CloseReason.Codes.GOING_AWAY, "Camera feed ended."))
        }
        .dropWhile { !it.isCodecConfig }
        .collect {
            send(it.data)
        }
}
