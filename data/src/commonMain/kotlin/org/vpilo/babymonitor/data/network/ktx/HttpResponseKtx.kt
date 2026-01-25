package org.vpilo.babymonitor.data.network.ktx

import org.vpilo.babymonitor.model.DataSourceError
import org.vpilo.babymonitor.model.Result
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

suspend inline fun <reified T> HttpResponse.responseToResult(): Result<T, DataSourceError.Remote> =
    when (status.value) {
        in 200..299 -> try {
            Result.Success(body<T>())
        } catch (ex: NoTransformationFoundException) {
            Result.Error(DataSourceError.Remote.Serialization)
        }

        408 -> Result.Error(DataSourceError.Remote.TimeOut)
        429 -> Result.Error(DataSourceError.Remote.TooManyRequests)
        in 500..599 -> Result.Error(DataSourceError.Remote.Server)
        else -> Result.Error(DataSourceError.Remote.Unknown)
    }

suspend inline fun <reified T> safeCall(block: () -> HttpResponse): Result<T, DataSourceError.Remote> =
    try {
        block().responseToResult()
    } catch (ex: SocketTimeoutException) {
        Result.Error(DataSourceError.Remote.TimeOut)
    } catch (ex: UnresolvedAddressException) {
        Result.Error(DataSourceError.Remote.NoInternetConnection)
    } catch (ex: Exception) {
        coroutineContext.ensureActive()
        Result.Error(DataSourceError.Remote.Unknown)
    }
