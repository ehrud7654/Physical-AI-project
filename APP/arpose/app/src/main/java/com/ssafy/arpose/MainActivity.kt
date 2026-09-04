package com.ssafy.arpose

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.ar.core.ArCoreApk
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.CameraIntrinsics
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.UnavailableException
import java.util.EnumSet
import java.util.Locale
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/**
 * ARCore 카메라 프리뷰 + 6DOF pose 오버레이 + 녹화 + 추적 점프 감지.
 *
 * REC  : frames 폴더의 JPEG 과 poses.csv 를 폰에 저장
 * AF   : 포커스 모드 전환 (FIXED 권장 - ARCore 기본값이자 추적에 유리)
 */
class MainActivity : Activity(), GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "POSE"
        private const val TAG_CSV = "POSE_CSV"
        private const val CAMERA_REQ = 1001
        private const val TARGET_CPU_WIDTH = 640

        // 추적 점프 판정 기준 (사람 손으로는 불가능한 값)
        private const val JUMP_SPEED_MPS = 2.0      // m/s
        private const val JUMP_MIN_MM = 40.0        // 한 프레임 최소 이동량
        private const val JUMP_ROT_DEG = 12.0       // 한 프레임 회전량

        // 위 기준만으로는 빠른 손동작도 점프로 잡힌다. 진짜 재로컬라이제이션은
        // 앞뒤 프레임과 단절되지만(직전 대비 68~204배) 빠른 손동작은 연속적이므로
        // (1.2배 수준), 직전 N프레임 대비 배율을 함께 요구해 갈라낸다.
        private const val JUMP_CONT_K = 5.0         // 직전 최대값 대비 배율
        private const val JUMP_CONT_N = 3           // 비교할 직전 프레임 수
    }

    private lateinit var glView: GLSurfaceView
    private lateinit var poseText: TextView
    private lateinit var recBtn: Button
    private lateinit var focusBtn: Button

    private val bg = BackgroundRenderer()
    private var session: Session? = null
    private var recorder: Recorder? = null
    private lateinit var imu: ImuLogger
    private var installRequested = false

    private var lastUiNs = 0L
    private var frameCount = 0L
    private var fpsWindowNs = 0L
    private var fps = 0.0

    // 같은 카메라 프레임을 두 번 처리하지 않기 위한 가드
    private var lastFrameTs = 0L
    private var lastPos: FloatArray? = null
    private var lastQuat: FloatArray? = null
    private var lastJumpMm = 0.0
    private var lastJumpDeg = 0.0

    /** 공간 부족으로 자동 정지된 적이 있으면 화면에 계속 띄운다. 토스트는 놓치기 쉽다. */
    @Volatile private var lowSpaceNotice = false

    /** 전송 대기 중인 녹화 수. 화면에 띄워 사용자가 남은 작업을 알 수 있게 한다. */
    @Volatile private var pendingUploads = 0

    // 직전 N프레임의 이동량/회전량 (점프 연속성 판정용 링버퍼)
    private val recentMm = DoubleArray(JUMP_CONT_N)
    private val recentDeg = DoubleArray(JUMP_CONT_N)
    private var recentIdx = 0
    private var recentCount = 0

    @Volatile private var intrinsicsLine: String = "unknown"
    @Volatile private var cameraConfigLine: String = "unknown"
    @Volatile private var pendingToggle = false
    @Volatile private var pendingFocusToggle = false
    @Volatile private var autoFocus = false   // 기본 FIXED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        poseText = findViewById(R.id.poseText)
        recBtn = findViewById(R.id.recBtn)
        focusBtn = findViewById(R.id.focusBtn)
        glView = findViewById(R.id.glView)

        recorder = Recorder(getExternalFilesDir(null)!!)
        imu = ImuLogger(this)
        pendingUploads = UploadQueue.pendingCount(this)
        UploadQueue.enqueue(this)   // 지난번에 못 보낸 것이 있으면 이어서 보낸다

        recBtn.setOnClickListener { pendingToggle = true }
        focusBtn.setOnClickListener {
            if (recorder?.isRecording == true) {
                Toast.makeText(this, "녹화 중에는 바꿀 수 없음", Toast.LENGTH_SHORT).show()
            } else {
                pendingFocusToggle = true
            }
        }
        updateFocusBtn()

        glView.preserveEGLContextOnPause = true
        glView.setEGLContextClientVersion(2)
        glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        glView.setRenderer(this)
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    private fun focusName() = if (autoFocus) "AUTO" else "FIXED"

    private fun updateFocusBtn() = runOnUiThread {
        focusBtn.text = "AF: ${focusName()}"
        focusBtn.setBackgroundColor(if (autoFocus) 0xFF885500.toInt() else 0xFF225533.toInt())
    }

    private fun applyConfig(s: Session) {
        s.configure(Config(s).apply {
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            focusMode = if (autoFocus) Config.FocusMode.AUTO else Config.FocusMode.FIXED
            planeFindingMode = Config.PlaneFindingMode.DISABLED
            lightEstimationMode = Config.LightEstimationMode.DISABLED
            depthMode = Config.DepthMode.DISABLED
        })
        Log.i(TAG, "focusMode=${focusName()}")
    }

    override fun onResume() {
        super.onResume()

        if (!hasCameraPermission()) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQ)
            return
        }

        if (session == null) {
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true; return
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> {}
                }
                val s = Session(this)
                applyConfig(s)
                selectCameraConfig(s)
                session = s
            } catch (e: UnavailableException) {
                showError("ARCore 사용 불가: ${e.javaClass.simpleName} - ${e.message}")
                Log.e(TAG, "unavailable", e); return
            } catch (e: Exception) {
                showError("세션 생성 실패: ${e.message}")
                Log.e(TAG, "create failed", e); return
            }
        }

        try {
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            showError("카메라를 열 수 없음 (다른 앱이 점유 중?)")
            session = null; return
        }
        glView.onResume()
        imu.register()
    }

    override fun onPause() {
        super.onPause()
        if (recorder?.isRecording == true) stopRecording()
        imu.unregister()
        if (session != null) {
            glView.onPause()
            session?.pause()
        }
    }

    /** CPU 이미지 640x480 에 가장 가깝고 30fps 인 카메라 설정을 고른다. */
    private fun selectCameraConfig(s: Session) {
        try {
            val filter = CameraConfigFilter(s)
                .setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30))
            val configs = s.getSupportedCameraConfigs(filter)
            if (configs.isNullOrEmpty()) return
            val best = configs.minByOrNull { abs(it.imageSize.width - TARGET_CPU_WIDTH) } ?: return
            s.cameraConfig = best
            cameraConfigLine =
                "cpu_image=${best.imageSize.width}x${best.imageSize.height} " +
                    "gpu_texture=${best.textureSize.width}x${best.textureSize.height} " +
                    "fps=${best.fpsRange}"
            Log.i(TAG, "CameraConfig: $cameraConfigLine")
            configs.forEach { Log.i(TAG, "  option: cpu=${it.imageSize} tex=${it.textureSize} fps=${it.fpsRange}") }
        } catch (e: Exception) {
            Log.e(TAG, "CameraConfig 선택 실패 (기본값 사용)", e)
        }
    }

    private fun hasCameraPermission() =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(rc, p, g)
        if (!hasCameraPermission()) showError("카메라 권한 필요. 설정에서 허용 후 재실행.")
    }

    private fun showError(msg: String) = runOnUiThread { poseText.text = msg }

    // ---------------- 녹화 ----------------

    private fun startRecording() {
        val rec = recorder
        if (rec != null && !rec.hasStartSpace()) {
            val msg = "저장 공간 부족 - 녹화를 시작할 수 없음\n" +
                "남은 공간 ${rec.freeMb()}MB (최소 ${Recorder.START_MIN_FREE_MB}MB 필요)\n" +
                "이전 녹화를 PC 로 옮기고 지울 것"
            lowSpaceNotice = true
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
            Log.w(TAG, "녹화 시작 거부: 여유 ${rec.freeMb()}MB")
            return
        }

        val meta = buildString {
            append("device=${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("android=${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
            append("focus_mode=${focusName()}\n")
            append("camera_config=$cameraConfigLine\n")
            append("intrinsics=$intrinsicsLine\n")
            append("start_wallclock_ms=${System.currentTimeMillis()}\n")
            append("start_elapsed_realtime_ns=${android.os.SystemClock.elapsedRealtimeNanos()}\n")
            append("note=timestamp_ns is ARCore frame.timestamp; base not guaranteed by ARCore but measured == elapsedRealtimeNanos on tested devices (compare first pose ts to start_elapsed_realtime_ns)\n")
            append(imu.describe())
        }
        lastJumpMm = 0.0; lastJumpDeg = 0.0
        recorder?.start(meta)
        recorder?.currentDir?.let { imu.start(it) }
        runOnUiThread {
            recBtn.text = "■ STOP"
            recBtn.setBackgroundColor(0xFFCC2222.toInt())
        }
    }

    private fun stopRecording() {
        val imuText = imu.stop()
        val r = recorder
        val jumps = r?.jumpCount ?: 0
        val maxJump = r?.maxJumpMm ?: 0.0
        val dir = r?.stop(imuText)
        val saved = r?.savedFrames ?: 0
        val dropped = r?.droppedFrames ?: 0
        val verdict = when {
            jumps == 0 -> "OK  점프 없음 — 그대로 사용 가능"
            else -> "점프 ${jumps}회 (최대 ${"%.0f".format(maxJump)}mm) — 분할 필요"
        }
        runOnUiThread {
            recBtn.text = "● REC"
            recBtn.setBackgroundColor(0xFF333333.toInt())
            Toast.makeText(
                this,
                "[AF ${focusName()}] $verdict\n프레임 $saved / 드롭 $dropped\n${dir?.name}",
                Toast.LENGTH_LONG
            ).show()
        }
        Log.i(TAG, "저장 완료: ${dir?.absolutePath} 프레임=$saved 드롭=$dropped 점프=$jumps AF=${focusName()}")

        // 업로드 예약. 실패하든 말든 촬영 흐름은 여기서 끝난다.
        UploadQueue.enqueue(this)
        pendingUploads = UploadQueue.pendingCount(this)
    }

    // ---------------- GL ----------------

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        bg.createOnGlThread()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        @Suppress("DEPRECATION")
        val rot = windowManager.defaultDisplay.rotation
        session?.setDisplayGeometry(rot, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = session ?: return
        if (bg.textureId == -1) return

        try {
            s.setCameraTextureName(bg.textureId)
            val frame = s.update()
            bg.draw(frame)

            // 버튼 처리 (중복 프레임 여부와 무관하게 즉시 반응)
            if (pendingToggle) {
                pendingToggle = false
                if (recorder?.isRecording == true) stopRecording() else startRecording()
            }
            if (pendingFocusToggle) {
                pendingFocusToggle = false
                autoFocus = !autoFocus
                try {
                    applyConfig(s)
                    intrinsicsLine = "unknown"   // 포커스 바뀌면 내부 파라미터도 다시 읽는다
                    lastPos = null; lastQuat = null
                    recentCount = 0; recentIdx = 0
                    updateFocusBtn()
                } catch (e: Exception) {
                    Log.e(TAG, "focus 변경 실패", e)
                }
            }

            val ts = frame.timestamp

            // 화면은 60Hz, 카메라는 30Hz -> 같은 프레임이 두 번 온다. 중복은 버린다.
            if (ts == lastFrameTs) return
            val dtSec = if (lastFrameTs == 0L) 0.0 else (ts - lastFrameTs) / 1e9
            lastFrameTs = ts

            val camera = frame.camera
            val state = camera.trackingState
            val pose = camera.pose
            val t = FloatArray(3); pose.getTranslation(t, 0)
            val q = FloatArray(4); pose.getRotationQuaternion(q, 0)

            // ---- 추적 점프 감지 (위치 + 회전) ----
            var jumpMm = 0.0
            var jumpDeg = 0.0
            if (state == TrackingState.TRACKING) {
                val pPrev = lastPos
                val qPrev = lastQuat
                if (pPrev != null && qPrev != null && dtSec > 0.0) {
                    val dx = (t[0] - pPrev[0]).toDouble()
                    val dy = (t[1] - pPrev[1]).toDouble()
                    val dz = (t[2] - pPrev[2]).toDouble()
                    val distMm = sqrt(dx * dx + dy * dy + dz * dz) * 1000.0
                    val degs = quatAngleDeg(qPrev, q)

                    // 직전 N프레임 최대값. 버퍼가 덜 찼으면 연속성 조건은 적용하지 않는다.
                    var baseMm = 0.0
                    var baseDeg = 0.0
                    for (k in 0 until recentCount) {
                        if (recentMm[k] > baseMm) baseMm = recentMm[k]
                        if (recentDeg[k] > baseDeg) baseDeg = recentDeg[k]
                    }
                    val hasBase = recentCount >= JUMP_CONT_N

                    val posJump = distMm > JUMP_MIN_MM &&
                        (distMm / 1000.0) / dtSec > JUMP_SPEED_MPS &&
                        (!hasBase || distMm > JUMP_CONT_K * baseMm)
                    val rotJump = degs > JUMP_ROT_DEG &&
                        (!hasBase || degs > JUMP_CONT_K * baseDeg)
                    if (posJump || rotJump) {
                        jumpMm = distMm
                        jumpDeg = degs
                        lastJumpMm = distMm; lastJumpDeg = degs
                        recorder?.let { r ->
                            if (r.isRecording) r.addJump(distMm, degs)
                        }
                        Log.w(
                            TAG,
                            "추적 점프! ${"%.1f".format(distMm)}mm / ${"%.1f".format(degs)}° " +
                                "in ${"%.1f".format(dtSec * 1000)}ms"
                        )
                    }

                    recentMm[recentIdx] = distMm
                    recentDeg[recentIdx] = degs
                    recentIdx = (recentIdx + 1) % JUMP_CONT_N
                    if (recentCount < JUMP_CONT_N) recentCount++
                }
                lastPos = t.copyOf()
                lastQuat = q.copyOf()
            } else {
                lastPos = null; lastQuat = null
                recentCount = 0; recentIdx = 0
            }

            if (intrinsicsLine == "unknown") {
                val ci: CameraIntrinsics = camera.imageIntrinsics
                val f = ci.focalLength; val pp = ci.principalPoint; val dim = ci.imageDimensions
                intrinsicsLine = String.format(
                    Locale.US, "w=%d h=%d fx=%.3f fy=%.3f cx=%.3f cy=%.3f",
                    dim[0], dim[1], f[0], f[1], pp[0], pp[1]
                )
                Log.i(TAG, "intrinsics(${focusName()}): $intrinsicsLine")
            }

            val rec = recorder
            if (rec != null && rec.isRecording && rec.lowSpace) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "저장 공간 부족으로 녹화를 정지함\n여기까지의 데이터는 정상 저장됨",
                        Toast.LENGTH_LONG
                    ).show()
                }
                lowSpaceNotice = true
                stopRecording()
            }
            if (rec != null && rec.isRecording) {
                try {
                    frame.acquireCameraImage().use { img ->
                        rec.submit(img, ts, state.name, t, q, jumpMm, jumpDeg)
                    }
                } catch (e: NotYetAvailableException) {
                    // 이번 프레임은 CPU 이미지가 아직 없음
                }
            }

            Log.d(
                TAG_CSV,
                "$ts,$state,${f5(t[0])},${f5(t[1])},${f5(t[2])}," +
                    "${f5(q[0])},${f5(q[1])},${f5(q[2])},${f5(q[3])}"
            )

            frameCount++
            if (fpsWindowNs == 0L) fpsWindowNs = System.nanoTime()
            val el = System.nanoTime() - fpsWindowNs
            if (el > 1_000_000_000L) {
                fps = frameCount * 1e9 / el; frameCount = 0; fpsWindowNs = System.nanoTime()
            }

            val now = System.nanoTime()
            if (now - lastUiNs > 100_000_000L) {
                lastUiNs = now
                val (roll, pitch, yaw) = quatToEulerDeg(q)
                val reason = if (state == TrackingState.PAUSED)
                    "  (${camera.trackingFailureReason})" else ""
                val recLine = if (rec != null && rec.isRecording)
                    "\n● REC ${rec.savedFrames}프레임  드롭${rec.droppedFrames}  점프${rec.jumpCount}" +
                        (if (rec.jumpCount > 0) " (최대 ${"%.0f".format(rec.maxJumpMm)}mm)" else "")
                else if (lastJumpMm > 0 || lastJumpDeg > 0)
                    "\n최근 점프 ${"%.0f".format(lastJumpMm)}mm / ${"%.1f".format(lastJumpDeg)}°"
                else ""
                val text = buildString {
                    if (lowSpaceNotice) {
                        append("!! 저장 공간 부족 - 녹화 불가 (남은 ${recorder?.freeMb()}MB)\n")
                        append("!! 이전 녹화를 PC 로 옮기고 지울 것\n\n")
                    }
                    append("$state$reason   ${"%.1f".format(fps)} fps   AF:${focusName()}$recLine\n")
                    if (pendingUploads > 0) append("전송 대기 ${pendingUploads}건\n")
                    append("\n")
                    append("x ${"%+.4f".format(t[0])}   y ${"%+.4f".format(t[1])}   z ${"%+.4f".format(t[2])}  (m)\n")
                    append("r ${"%+.1f".format(roll)}°  p ${"%+.1f".format(pitch)}°  y ${"%+.1f".format(yaw)}°\n")
                }
                runOnUiThread { poseText.text = text }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "onDrawFrame 예외", e)
        }
    }

    private fun f5(v: Float) = String.format(Locale.US, "%.5f", v)

    /** 두 쿼터니언 사이의 각도(도). q 와 -q 는 같은 회전이므로 절댓값을 쓴다. */
    private fun quatAngleDeg(a: FloatArray, b: FloatArray): Double {
        var d = 0.0
        for (i in 0..3) d += a[i].toDouble() * b[i].toDouble()
        d = min(1.0, abs(d))
        return Math.toDegrees(2.0 * acos(d))
    }

    private fun quatToEulerDeg(q: FloatArray): Triple<Double, Double, Double> {
        val x = q[0].toDouble(); val y = q[1].toDouble()
        val z = q[2].toDouble(); val w = q[3].toDouble()
        val roll = atan2(2.0 * (w * x + y * z), 1.0 - 2.0 * (x * x + y * y))
        val pitch = asin((2.0 * (w * y - z * x)).coerceIn(-1.0, 1.0))
        val yaw = atan2(2.0 * (w * z + x * y), 1.0 - 2.0 * (y * y + z * z))
        val d = 180.0 / PI
        return Triple(roll * d, pitch * d, yaw * d)
    }
}
