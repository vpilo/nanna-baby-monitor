package org.vpilo.babymonitor.camera.data

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.vpilo.babymonitor.common.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * GL fan-out from a single camera-fed SurfaceTexture (the "bridge") to
 * two consumer Surfaces (viewfinder, encoder). All EGL/GL state lives on
 * a dedicated handler thread; the public API marshals onto it.
 *
 * Lifecycle:
 *  - Constructor blocks briefly while EGL initializes on the GL thread so
 *    that [cameraInputSurface] is ready immediately upon return.
 *  - The instance lives for the data source's service lifetime.
 *  - [release] tears down EGL and the GL thread; suspends until done.
 */
internal class CameraGlRenderer(
    private val bridgeSize: Size,
    private val onFrameRendered: () -> Unit,
) {
    private val handlerThread = HandlerThread(GL_THREAD_NAME).apply { start() }
    private val glHandler: Handler = Handler(handlerThread.looper)
    private val glDispatcher = glHandler.asCoroutineDispatcher(GL_THREAD_NAME)

    @Volatile
    private var isReleased = false

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var pbufferSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var textureId: Int = 0
    private lateinit var surfaceTexture: SurfaceTexture

    lateinit var cameraInputSurface: Surface
        private set

    private var viewfinder: ConsumerSurface? = null
    private var encoder: ConsumerSurface? = null

    private var program: Int = 0
    private var aPositionLoc: Int = 0
    private var aTexCoordLoc: Int = 0
    private var uSTMatrixLoc: Int = 0
    private var uTextureLoc: Int = 0

    private lateinit var vertexBuffer: FloatBuffer
    private val stMatrix = FloatArray(16)

    init {
        Logger.d(TAG) { "Initializing with size=$bridgeSize" }
        runBlocking(glDispatcher) {
            initializeEgl()
            createProgram()
            createBridgeTexture(bridgeSize)
        }
    }

    suspend fun attachViewfinder(surface: Surface) =
        withContext(glDispatcher) {
            Logger.d(TAG) { "Attaching Viewfinder" }
            viewfinder?.let { EGL14.eglDestroySurface(eglDisplay, it.eglSurface) }
            viewfinder = createWindowSurface("viewfinder", surface)
        }

    suspend fun detachViewfinder() =
        withContext(glDispatcher) {
            Logger.d(TAG) { "Detaching Viewfinder" }
            viewfinder?.let { EGL14.eglDestroySurface(eglDisplay, it.eglSurface) }
            viewfinder = null
            checkFallbackContext()
        }

    suspend fun attachEncoder(surface: Surface) =
        withContext(glDispatcher) {
            Logger.d(TAG) { "Attaching encoder" }
            encoder?.let { EGL14.eglDestroySurface(eglDisplay, it.eglSurface) }
            encoder = createWindowSurface("encoder", surface)
        }

    suspend fun detachEncoder() =
        withContext(glDispatcher) {
            Logger.d(TAG) { "Detaching encoder" }
            encoder?.let { EGL14.eglDestroySurface(eglDisplay, it.eglSurface) }
            encoder = null
            checkFallbackContext()
        }

    // Fall back to the pbuffer context if both consumer surfaces are detached, to continue consuming frames without stalling.
    private fun checkFallbackContext() {
        if (viewfinder == null && encoder == null) {
            EGL14.eglMakeCurrent(eglDisplay, pbufferSurface, pbufferSurface, eglContext)
        }
    }

    suspend fun release() =
        withContext(glDispatcher) {
            isReleased = true
            Logger.d(TAG) { "Releasing" }
            viewfinder?.let { EGL14.eglDestroySurface(eglDisplay, it.eglSurface) }
            encoder?.let { EGL14.eglDestroySurface(eglDisplay, it.eglSurface) }
            viewfinder = null
            encoder = null
            cameraInputSurface.release()
            surfaceTexture.release()
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (pbufferSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, pbufferSurface)
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                }
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(eglDisplay)
            }
            handlerThread.quitSafely()
        }

    fun isReleased(): Boolean = isReleased

    private fun initializeEgl() {
        val version = IntArray(2)
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "eglInitialize failed" }

        val configAttribs =
            intArrayOf(
                EGL14.EGL_RED_SIZE,
                8,
                EGL14.EGL_GREEN_SIZE,
                8,
                EGL14.EGL_BLUE_SIZE,
                8,
                EGL14.EGL_ALPHA_SIZE,
                8,
                EGL14.EGL_DEPTH_SIZE,
                0,
                EGL14.EGL_RENDERABLE_TYPE,
                EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE,
                EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
                EGL_RECORDABLE_ANDROID,
                1,
                EGL14.EGL_NONE,
            )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(
            EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0) &&
                numConfigs[0] > 0,
        ) { "eglChooseConfig failed" }
        eglConfig = configs[0]

        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }

        val pbufferAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        pbufferSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, pbufferAttribs, 0)
        check(pbufferSurface != EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface failed" }

        check(EGL14.eglMakeCurrent(eglDisplay, pbufferSurface, pbufferSurface, eglContext)) {
            "eglMakeCurrent on pbuffer failed"
        }
    }

    private fun createBridgeTexture(size: Size) {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture.setDefaultBufferSize(size.width, size.height)
        surfaceTexture.setOnFrameAvailableListener({ drawFrame() }, glHandler)
        cameraInputSurface = Surface(surfaceTexture)
    }

    private fun drawFrame() {
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(stMatrix)

        viewfinder?.let { drawTo(it) }
        encoder?.let { drawTo(it) }

        onFrameRendered()
    }

    private fun drawTo(surface: ConsumerSurface) {
        if (!EGL14.eglMakeCurrent(eglDisplay, surface.eglSurface, surface.eglSurface, eglContext)) {
            Logger.w(TAG) { "eglMakeCurrent failed for ${surface.name}: ${EGL14.eglGetError()}" }
            return
        }

        GLES20.glViewport(0, 0, surface.width, surface.height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(uTextureLoc, 0)

        GLES20.glUniformMatrix4fv(uSTMatrixLoc, 1, false, stMatrix, 0)

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 4 * 4, vertexBuffer)

        vertexBuffer.position(2)
        GLES20.glEnableVertexAttribArray(aTexCoordLoc)
        GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 4 * 4, vertexBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTexCoordLoc)

        // Help the encoder sync frames to prevent stutter/jitter.
        if (surface == encoder) {
            EGLExt.eglPresentationTimeANDROID(eglDisplay, surface.eglSurface, surfaceTexture.timestamp)
        }

        if (!EGL14.eglSwapBuffers(eglDisplay, surface.eglSurface)) {
            Logger.w(TAG) { "eglSwapBuffers failed: ${EGL14.eglGetError()}" }
        }
    }

    private fun createProgram() {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_SRC)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_SRC)
        program = GLES20.glCreateProgram()
        check(program != 0) { "glCreateProgram failed" }
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        check(linkStatus[0] == GLES20.GL_TRUE) {
            "Program link failed: ${GLES20.glGetProgramInfoLog(program)}"
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoordLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        uSTMatrixLoc = GLES20.glGetUniformLocation(program, "uSTMatrix")
        uTextureLoc = GLES20.glGetUniformLocation(program, "uTexture")

        // Triangle strip covering the screen in NDC, with matching tex coords.
        // Four vertices, each has a x, y, u, v value.
        val verts =
            floatArrayOf(
                -1f,
                -1f,
                0f,
                0f,
                1f,
                -1f,
                1f,
                0f,
                -1f,
                1f,
                0f,
                1f,
                1f,
                1f,
                1f,
                1f,
            )
        vertexBuffer =
            ByteBuffer
                .allocateDirect(VERTEX_BUFFER_SIZE)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(verts)
                    position(0)
                }
    }

    private fun compileShader(
        type: Int,
        src: String,
    ): Int {
        val shader = GLES20.glCreateShader(type)
        check(shader != 0) { "glCreateShader($type) failed" }
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) {
            "Shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}"
        }
        return shader
    }

    private fun createWindowSurface(
        name: String,
        surface: Surface,
    ): ConsumerSurface? {
        val attrs = intArrayOf(EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, attrs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            Logger.e(TAG) { "eglCreateWindowSurface for $name failed: ${EGL14.eglGetError()}" }
            return null
        }
        val size = IntArray(2)
        EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_WIDTH, size, 0)
        EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_HEIGHT, size, 1)
        Logger.d(TAG) { "Created surface for $name: ${size[0]}x${size[1]}" }

        return ConsumerSurface(name, eglSurface, size[0], size[1])
    }

    private class ConsumerSurface(
        val name: String,
        val eglSurface: EGLSurface,
        val width: Int,
        val height: Int,
    )

    private companion object {
        private val TAG = CameraGlRenderer::class

        private const val GL_THREAD_NAME = "camera-gl"

        private const val VERTEX_BUFFER_SIZE = 4 * 4 * 4 // 4 [float-sized] * 4 [vertices] * 4 [with x,y,u,v properties]

        // EGL_ANDROID_recordable extension.
        // Required for MediaCodec to accept our EGLSurface as a draw target.
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        private const val VERTEX_SHADER_SRC = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uSTMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uSTMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
            }
        """

        private const val FRAGMENT_SHADER_SRC = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """
    }
}
