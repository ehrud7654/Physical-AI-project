package com.a103.collect

import android.app.Activity
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.YuvImage
import android.graphics.drawable.GradientDrawable
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * 스트리밍 모드 — 폰 카메라를 PC(ROS 런타임)에 실시간 공급한다(UMI USB Camera2 호환, [FrameServer]).
 * 화면 진입 시 카메라와 서버를 열어 젯슨 접속을 기다리고, 셔터로 영상 송출 시작/중지 — 촬영 화면 셔터와 같은 조작.
 * 카메라 설정은 수집 모드와 동일([CameraCapture] 재사용: 초광각 고정초점·30fps·AF/OIS/EIS/왜곡보정 OFF) — 학습·추론 조건 일치.
 * JPEG 는 회전 없이 센서 원본 방향. PC(policy_runtime)가 180° 회전하므로 녹화 경로(180° baked)를 타면 안 된다.
 */
class StreamActivity : Activity() {

    private lateinit var preview: SurfaceView
    private lateinit var dot: TextView
    private lateinit var status: TextView
    private lateinit var shutter: View

    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var camera: CameraCapture? = null
    private var reader: ImageReader? = null
    private var jpegThread: HandlerThread? = null
    /** 화면이 열려 있는 동안 젯슨 접속을 받는 서버. worker 에서 생성·종료(재진입 시 포트 재바인드 순서 보장). */
    @Volatile private var server: FrameServer? = null
    private var started = false
    /** 셔터로 켜는 송출 여부. jpeg 스레드가 읽음. */
    @Volatile private var streaming = false
    @Volatile private var cameraReady = false
    @Volatile private var connected = false
    private var error: String? = null
    private var retry = 0
    private var lastSent = 0L
    private var fps = 0L

    // jpeg 스레드 전용 버퍼(프레임마다 재할당 방지).
    private val encodeLock = Any()
    private var nv21: ByteArray? = null
    private var vRow = ByteArray(0)
    private var uRow = ByteArray(0)
    private val jpegOut = ByteArrayOutputStream(1 shl 19)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)   // 로봇 장착 중 화면 꺼짐 방지

        val outer = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        preview = SurfaceView(this).apply {
            holder.setFixedSize(FrameServer.WIDTH, FrameServer.HEIGHT)
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(h: SurfaceHolder) { start() }
                override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {}
                override fun surfaceDestroyed(h: SurfaceHolder) { stop() }
            })
        }
        outer.addView(preview, FrameLayout.LayoutParams(MATCH, MATCH))

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xCC0E1524.toInt()); setPadding(dp(16), dp(11), dp(16), dp(11))
        }
        dot = TextView(this).apply { text = "●"; textSize = 18f }
        status = TextView(this).apply {
            textSize = 16f; setTextColor(0xFFDCE3F2.toInt()); setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(8), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val exit = TextView(this).apply {
            text = "종료"; textSize = 15f; setTextColor(0xFFFFFFFF.toInt()); setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(16), dp(7), dp(16), dp(7))
            background = GradientDrawable().apply { cornerRadius = dp(999).toFloat(); setColor(0xCC1B2440.toInt()) }
            setOnClickListener { finish() }
        }
        bar.addView(dot); bar.addView(status); bar.addView(exit)
        outer.addView(bar, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))

        // 좌측 원형 셔터 — 촬영 화면과 같은 위치·모양(대기: 빨간 원 / 진행 중: 정지 사각형).
        shutter = View(this).apply {
            setBackgroundResource(R.drawable.bg_shutter)
            setOnClickListener { toggleStreaming() }
        }
        outer.addView(shutter, FrameLayout.LayoutParams(dp(74), dp(74), Gravity.START or Gravity.CENTER_VERTICAL)
            .apply { marginStart = dp(28) })

        setContentView(outer)
        render()
    }

    override fun onResume() { super.onResume(); start() }

    override fun onPause() {
        ui.removeCallbacks(retryStart)
        stop()
        super.onPause()
    }

    override fun onDestroy() { super.onDestroy(); worker.shutdown() }

    private val retryStart = Runnable { start() }

    /** 카메라 세션(프리뷰 + YUV 리더) 시작. 촬영 화면 카메라가 아직 해제 중이면 재시도. */
    private fun start() {
        if (started || !preview.holder.surface.isValid) return
        started = true; cameraReady = false; connected = false; error = null
        val surface = preview.holder.surface
        val jt = HandlerThread("umi-jpeg").apply { start() }
        val rd = ImageReader.newInstance(FrameServer.WIDTH, FrameServer.HEIGHT, ImageFormat.YUV_420_888, 3)
        rd.setOnImageAvailableListener({ encode(it) }, Handler(jt.looper))
        jpegThread = jt; reader = rd
        worker.execute {
            if (server == null) server = FrameServer(
                onClient = { c -> connected = c; ui.post { render() } },
                onError = { m -> ui.post { error = m; render() } },
            )
            val cam = try { CameraCapture(this) }
            catch (e: Exception) { ui.post { error = "카메라 사용 불가: ${e.message}"; render() }; return@execute }
            // 기기 문제라 재시도해도 소용없으므로 바로 안내.
            if (!cam.supportsYuv1080()) {
                try { cam.close() } catch (_: Exception) {}
                ui.post { error = "이 렌즈는 1920×1080 스트리밍을 지원하지 않아요"; render() }
                return@execute
            }
            camera = cam
            cam.open(surface, rd.surface, object : CameraCapture.Listener {
                override fun ready() { cameraReady = true; ui.post { retry = 0; render() } }
                override fun failed(reason: String) {
                    ui.post {
                        stop()
                        if (retry < 5 && !isFinishing) { retry++; ui.postDelayed(retryStart, 700) }
                        else { error = "카메라 실패: $reason"; render() }
                    }
                }
            })
        }
        render()
    }

    private fun stop() {
        if (!started) return
        stopStreaming()
        started = false; cameraReady = false; connected = false
        val rd = reader; val jt = jpegThread
        reader = null; jpegThread = null
        // camera·server 는 worker 에서 대입되므로 같은 worker 에서 읽어야 순서가 보장된다.
        worker.execute {
            val cam = camera; camera = null
            try { cam?.close() } catch (_: Exception) {}
            try { rd?.close() } catch (_: Exception) {}
            jt?.quitSafely()
            val s = server; server = null
            try { s?.close() } catch (_: Exception) {}
        }
        render()
    }

    private fun toggleStreaming() {
        if (streaming) stopStreaming()
        else if (cameraReady && error == null) startStreaming()
    }

    private fun startStreaming() {
        streaming = true; lastSent = server?.sent ?: 0L; fps = 0L
        shutter.setBackgroundResource(R.drawable.bg_shutter_stop)
        ui.removeCallbacks(tick); ui.postDelayed(tick, 1000)
        render()
    }

    private fun stopStreaming() {
        if (!streaming) return
        streaming = false
        shutter.setBackgroundResource(R.drawable.bg_shutter)
        ui.removeCallbacks(tick)
        render()
    }

    /** YUV_420_888 → NV21 → JPEG(품질 90, 원본 방향) → 서버 게시. 송출 중이 아니거나 로봇이 안 붙었으면 버리기만(발열↓). */
    private fun encode(r: ImageReader) = synchronized(encodeLock) {
        val img = try { r.acquireLatestImage() } catch (_: Exception) { null } ?: return@synchronized
        val srv = server
        if (!streaming || !connected || srv == null) { img.close(); return@synchronized }
        val ts: Long
        val buf: ByteArray
        try {
            ts = img.timestamp   // 센서 시각(ns) — 학습 frames.csv 와 같은 시간축
            buf = nv21 ?: ByteArray(img.width * img.height * 3 / 2).also { nv21 = it }
            toNv21(img, buf)
        } finally { img.close() }
        jpegOut.reset()
        val ok = YuvImage(buf, ImageFormat.NV21, FrameServer.WIDTH, FrameServer.HEIGHT, null)
            .compressToJpeg(Rect(0, 0, FrameServer.WIDTH, FrameServer.HEIGHT), JPEG_QUALITY, jpegOut)
        if (ok) srv.publish(jpegOut.toByteArray(), ts)
    }

    /** 행/픽셀 stride 를 고려해 NV21(Y 평면 + VU 인터리브)로 복사. U/V 는 stride 가 같음이 보장된다. */
    private fun toNv21(img: Image, out: ByteArray) {
        val w = img.width; val h = img.height
        val yP = img.planes[0]; val uP = img.planes[1]; val vP = img.planes[2]
        val yBuf = yP.buffer
        var pos = 0
        if (yP.rowStride == w) { yBuf.get(out, 0, w * h); pos = w * h }
        else for (row in 0 until h) { yBuf.position(row * yP.rowStride); yBuf.get(out, pos, w); pos += w }

        val rowStride = vP.rowStride; val pixStride = vP.pixelStride
        val uBuf = uP.buffer; val vBuf = vP.buffer
        if (vRow.size < rowStride) { vRow = ByteArray(rowStride); uRow = ByteArray(rowStride) }
        for (row in 0 until h / 2) {
            vBuf.position(row * rowStride); vBuf.get(vRow, 0, minOf(rowStride, vBuf.remaining()))
            uBuf.position(row * rowStride); uBuf.get(uRow, 0, minOf(rowStride, uBuf.remaining()))
            for (col in 0 until w / 2) {
                val i = col * pixStride
                out[pos++] = vRow[i]; out[pos++] = uRow[i]
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val s = server?.sent ?: 0L
            fps = s - lastSent; lastSent = s
            render()
            ui.postDelayed(this, 1000)
        }
    }

    private fun render() {
        val (color, text) = when {
            error != null -> RED to error!!
            !cameraReady -> AMBER to "카메라 준비 중…"
            !streaming && !connected -> AMBER to "로봇 연결 대기"
            // 로봇(젯슨) 수신측은 접속 후 5초 안에 프레임이 없으면 끊으므로 바로 누르게 유도.
            !streaming -> GREEN to "로봇 연결됨 — 셔터를 눌러 시작"
            connected -> RED to "스트리밍 중 · 로봇 연결됨 · $fps fps"
            else -> RED to "스트리밍 중 · 로봇 연결 대기"
        }
        dot.setTextColor(color); status.text = text
    }

    companion object {
        private const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = FrameLayout.LayoutParams.WRAP_CONTENT
        private const val JPEG_QUALITY = 90   // 원본 org.umi.stream 과 동일
        // 촬영 화면 신호와 같은 색: 초록=가능, 빨강=진행 중, 주황=준비 중
        private const val GREEN = 0xFF3FBF87.toInt()
        private const val RED = 0xFFE0685E.toInt()
        private const val AMBER = 0xFFE0A44E.toInt()
    }
}
