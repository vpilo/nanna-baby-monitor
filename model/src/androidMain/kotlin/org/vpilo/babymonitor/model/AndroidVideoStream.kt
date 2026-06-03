package org.vpilo.babymonitor.model

import android.view.Surface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

sealed class AndroidVideoStream : VideoStream<Surface>() {
    private val mutableSurface = MutableStateFlow<Surface?>(null)
    override val surface: Flow<Surface?> = mutableSurface.asStateFlow()
    override val isActive: Flow<Boolean> = mutableSurface.map { it != null }

    fun postSurface(surface: Surface?) {
        if (surface != null) {
            check(mutableSurface.value == null) { "postSurface() called with a surface already attached" }
            mutableSurface.value = surface
            return
        }
        mutableSurface.value = null
    }
}
