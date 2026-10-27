package org.vpilo.babymonitor.model

import android.graphics.SurfaceTexture
import android.view.Surface
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidVideoStreamTest {
    @Test
    fun cameraStaysActiveUntilEncoderDetaches() =
        runTest {
            val stream = AndroidServerVideoStream()
            val finishDetach = CompletableDeferred<Unit>()
            backgroundScope.launch {
                stream.consumeEncoderSurface(attach = {}, detach = { finishDetach.await() })
            }
            val surface = Surface(SurfaceTexture(0))
            stream.attachEncoderSurface(surface)
            runCurrent()

            val unregister = backgroundScope.launch { stream.detachEncoderSurface(surface) }
            runCurrent()
            try {
                assertFalse(unregister.isCompleted)
                assertTrue(stream.isActive.first())
            } finally {
                finishDetach.complete(Unit)
            }
            runCurrent()
            assertTrue(unregister.isCompleted)
            assertFalse(stream.isActive.first())
        }

    @Test
    fun cancellingConsumerWaitsForGlDetachBeforeReleasingSurface() =
        runTest {
            val stream = AndroidServerVideoStream()
            val finishDetach = CompletableDeferred<Unit>()
            var detached = false
            val consumer =
                backgroundScope.launch {
                    stream.consumeEncoderSurface(
                        attach = {},
                        detach = {
                            finishDetach.await()
                            detached = true
                        },
                    )
                }
            val surface = Surface(SurfaceTexture(0))
            stream.attachEncoderSurface(surface)
            runCurrent()
            consumer.cancel()
            runCurrent()
            val unregister = backgroundScope.launch { stream.detachEncoderSurface(surface) }
            runCurrent()
            try {
                assertFalse(unregister.isCompleted)
            } finally {
                finishDetach.complete(Unit)
            }
            runCurrent()
            assertTrue(detached)
            assertTrue(consumer.isCompleted)
            assertTrue(unregister.isCompleted)
        }

    @Test
    fun replacementCannotReleasePreviousSurfaceBeforeGlDetachesIt() =
        runTest {
            val stream = AndroidServerVideoStream()
            val firstSurface = Surface(SurfaceTexture(0))
            val secondSurface = Surface(SurfaceTexture(0))
            val finishFirstAttach = CompletableDeferred<Unit>()
            val finishDetach = CompletableDeferred<Unit>()
            var secondAttached = false
            backgroundScope.launch {
                stream.consumeEncoderSurface(
                    attach = { surface ->
                        if (surface === firstSurface) {
                            // Model an EGL call already executing on the GL thread.
                            withContext(NonCancellable) { finishFirstAttach.await() }
                        } else {
                            secondAttached = true
                        }
                    },
                    detach = { finishDetach.await() },
                )
            }
            stream.attachEncoderSurface(firstSurface)
            runCurrent()
            val unregister = backgroundScope.launch { stream.detachEncoderSurface(firstSurface) }
            runCurrent()
            stream.attachEncoderSurface(secondSurface)
            finishFirstAttach.complete(Unit)
            runCurrent()
            try {
                assertFalse(unregister.isCompleted)
                assertFalse(secondAttached)
            } finally {
                finishDetach.complete(Unit)
            }
            runCurrent()
            assertTrue(unregister.isCompleted)
            assertTrue(secondAttached)
            assertTrue(stream.isActive.first())
        }

    @Test
    fun surfaceWithdrawnBeforeConsumptionIsNeverAttached() =
        runTest {
            val stream = AndroidServerVideoStream()
            var attached = false
            backgroundScope.launch {
                stream.consumeEncoderSurface(attach = { attached = true }, detach = {})
            }
            val surface = Surface(SurfaceTexture(0))
            stream.attachEncoderSurface(surface)
            stream.detachEncoderSurface(surface)
            runCurrent()
            assertFalse(attached)
            assertFalse(stream.isActive.first())
        }

    @Test
    fun viewfinderStaysActiveUntilConsumerDetaches() =
        runTest {
            val stream = AndroidServerVideoStream()
            val finishDetach = CompletableDeferred<Unit>()
            backgroundScope.launch {
                stream.consumeSurface(attach = {}, detach = { finishDetach.await() })
            }
            val surface = Surface(SurfaceTexture(0))
            stream.attachSurface(surface)
            runCurrent()

            stream.detachSurface(surface)
            runCurrent()
            try {
                assertTrue(stream.isActive.first())
            } finally {
                finishDetach.complete(Unit)
            }
            runCurrent()
            assertFalse(stream.isActive.first())
        }

    @Test
    fun staleViewfinderDetachKeepsReplacement() =
        runTest {
            val stream = AndroidClientVideoStream()
            val firstSurface = Surface(SurfaceTexture(0))
            val secondSurface = Surface(SurfaceTexture(0))
            val attached = mutableListOf<Surface>()
            backgroundScope.launch {
                stream.consumeSurface(attach = { attached += it }, detach = {})
            }
            stream.attachSurface(firstSurface)
            runCurrent()
            stream.attachSurface(secondSurface)
            stream.detachSurface(firstSurface)
            runCurrent()
            assertEquals(listOf(firstSurface, secondSurface), attached)
            assertTrue(stream.isActive.first())
        }
}
