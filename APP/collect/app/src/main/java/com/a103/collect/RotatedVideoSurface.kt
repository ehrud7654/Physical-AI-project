package com.a103.collect

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
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 카메라 출력(SurfaceTexture, 외부 OES)을 받아 upright_v1(180° 회전)로 그려 인코더 입력 Surface 에 낸다.
 * 회전을 픽셀에 baked → mp4 에 회전 메타데이터가 없다.
 * 각 프레임 제출 시 presentation time 을 카메라 프레임의 SurfaceTexture.timestamp(센서 시각, ns)로 박는다
 * → 인코더 PTS 가 센서 타임스탬프가 되어 frames.csv/IMU 와 정렬된다(파이프라인 계약).
 */
class RotatedVideoSurface(encoderInput: Surface, private val recording: Recording) {

    val surface: Surface

    private val thread = HandlerThread("collect-video-rotation").apply { start() }
    private val handler = Handler(thread.looper)
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var output: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var textureId = 0
    private lateinit var texture: SurfaceTexture
    private var failure: Exception? = null

    // 풀스크린 쿼드(triangle strip). 정점당 pos.xy + uv.xy = 4 float(16 byte).
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(4 * 4 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(
                -1f, -1f, 0f, 0f,
                 1f, -1f, 1f, 0f,
                -1f,  1f, 0f, 1f,
                 1f,  1f, 1f, 1f,
            ))
            position(0)
        }

    init {
        val latch = CountDownLatch(1)
        handler.post {
            try { setup(encoderInput) } catch (e: Exception) { failure = e } finally { latch.countDown() }
        }
        if (!latch.await(5, TimeUnit.SECONDS)) throw Exception("영상 회전 설정 타임아웃")
        failure?.let { thread.quitSafely(); throw it }
        surface = Surface(texture)
    }

    private fun setup(encoderInput: Surface) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ver = IntArray(2)
        if (!EGL14.eglInitialize(display, ver, 0, ver, 1)) throw Exception("EGL initialize 실패")

        val cfgs = arrayOfNulls<EGLConfig>(1)
        val n = IntArray(1)
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        if (!EGL14.eglChooseConfig(display, attribs, 0, cfgs, 0, 1, n, 0) || n[0] == 0)
            throw Exception("인코더용 EGL config 없음")

        context = EGL14.eglCreateContext(display, cfgs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        output = EGL14.eglCreateWindowSurface(display, cfgs[0], encoderInput, intArrayOf(EGL14.EGL_NONE), 0)
        if (!EGL14.eglMakeCurrent(display, output, output, context)) throw Exception("EGL makeCurrent 실패")

        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        textureId = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val vs = shader(GLES20.GL_VERTEX_SHADER,
            "attribute vec2 pos; attribute vec2 uv; uniform mat4 transform; varying vec2 tex;" +
                "void main(){gl_Position=vec4(pos,0.,1.);tex=(transform*vec4(vec2(1.-uv.y,uv.x),0.,1.)).xy;}")
        val fs = shader(GLES20.GL_FRAGMENT_SHADER,
            "#extension GL_OES_EGL_image_external : require\nprecision mediump float;" +
                "uniform samplerExternalOES frame; varying vec2 tex; void main(){gl_FragColor=texture2D(frame,tex);}")
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0)
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
        if (ok[0] == 0) throw Exception(GLES20.glGetProgramInfoLog(program))

        texture = SurfaceTexture(textureId).apply {
            setDefaultBufferSize(1920, 1080)
            setOnFrameAvailableListener({ onFrame() }, handler)
        }
    }

    private val xform = FloatArray(16)

    private fun onFrame() {
        try {
            texture.updateTexImage()
            texture.getTransformMatrix(xform)
            GLES20.glViewport(0, 0, 1920, 1080)
            GLES20.glUseProgram(program)
            val pos = GLES20.glGetAttribLocation(program, "pos")
            val uv = GLES20.glGetAttribLocation(program, "uv")
            quad.position(0)
            GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glEnableVertexAttribArray(pos)
            quad.position(2)
            GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glEnableVertexAttribArray(uv)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "transform"), 1, false, xform, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "frame"), 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            // presentation time = 센서 프레임 시각 → 인코더 PTS 가 센서 타임스탬프가 된다.
            EGLExt.eglPresentationTimeANDROID(display, output, texture.timestamp)
            if (!EGL14.eglSwapBuffers(display, output)) throw Exception("회전 프레임 제출 실패")
        } catch (e: Exception) {
            recording.error = e.toString()
        }
    }

    private fun shader(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) throw Exception(GLES20.glGetShaderInfoLog(s))
        return s
    }

    fun stop() {
        val latch = CountDownLatch(1)
        handler.post {
            try {
                texture.setOnFrameAvailableListener(null)
                surface.release()
                texture.release()
                GLES20.glDeleteProgram(program)
                GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(display, output)
                EGL14.eglDestroyContext(display, context)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(display)
            } finally { latch.countDown() }
        }
        if (!latch.await(5, TimeUnit.SECONDS)) throw Exception("회전 종료 타임아웃")
        thread.quitSafely()
        thread.join(1000)
    }

    companion object {
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}
