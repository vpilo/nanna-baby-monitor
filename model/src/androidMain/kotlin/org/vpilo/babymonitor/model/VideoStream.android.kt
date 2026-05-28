package org.vpilo.babymonitor.model

import android.view.Surface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class AndroidClientVideoStream : AndroidVideoStream()

class AndroidServerVideoStream : AndroidVideoStream() {
    private val mutableEncoderSurface = MutableStateFlow<Surface?>(null)
    val encoderSurface: Flow<Surface?> = mutableEncoderSurface.asStateFlow()

    override val isActive: Flow<Boolean> =
        combine(surface, mutableEncoderSurface) { v, e -> v != null || e != null }
            .distinctUntilChanged()

    /**
     * Registers or unregisters the encoder Surface.
     *
     * When registering, it does not suspend.
     * When unregistering, suspends until all references to the surface have been released.
     */
    fun postEncoderSurface(surface: Surface?) {
        if (surface != null) {
            check(mutableEncoderSurface.value == null) { "postEncoderSurface(null) called with a surface already attached" }
            mutableEncoderSurface.value = surface
            return
        }
        mutableEncoderSurface.value = null
    }
}
