package com.ssafy.arpose

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import java.io.File
import android.util.Log
import android.util.Range
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import com.google.ar.core.ArCoreApk
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.CameraIntrinsics
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.SharedCamera
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
        // 16:9 1920x1080 (계약 meta 예시와 일치, 마젠타 마커 검출 확인됨). ARCore S22 제공.
        // 4:3 은 640x480 뿐이라 고해상은 16:9. 마운트가 16:9 크롭에서 마커 유지 확인.
        private const val TARGET_CPU_WIDTH = 1920

        // 추적 점프 판정 기준 (사람 손으로는 불가능한 값)
        private const val JUMP_SPEED_MPS = 2.0      // m/s
        private const val JUMP_MIN_MM = 40.0        // 한 프레임 최소 이동량
        private const val JUMP_ROT_DEG = 12.0       // 한 프레임 회전량

        // 위 기준만으로는 빠른 손동작도 점프로 잡힌다. 진짜 재로컬라이제이션은
        // 앞뒤 프레임과 단절되지만(직전 대비 68~204배) 빠른 손동작은 연속적이므로
        // (1.2배 수준), 직전 N프레임 대비 배율을 함께 요구해 갈라낸다.
        private const val JUMP_CONT_K = 5.0         // 직전 최대값 대비 배율
        private const val JUMP_CONT_N = 3           // 비교할 직전 프레임 수


        // shared camera 에서 ARCore 가 30fps 프레임을 ~0.1ms 간격으로 두 번씩 준다.
        // 정확히 같은 timestamp 만 버리면 중복이 새어 60Hz 로 저장되므로, 이 간격보다
        // 촘촘하면 중복으로 보고 버린다. 실프레임은 33ms 라 20ms 로 안전하게 가른다.
        private const val MIN_FRAME_GAP_NS = 20_000_000L
    }

    private lateinit var glView: GLSurfaceView
    private lateinit var poseText: TextView
    private lateinit var recBtn: Button
    private lateinit var gridOverlay: ImageView
    private lateinit var menuBtn: ImageButton

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
    @Volatile private var autoFocus = false   // 기본 FIXED (설정에서 변경)

    // SharedCamera: AE/AWB lock 을 위해 ARCore 옆에서 Camera2 캡처요청을 직접 쥔다.
    private var sharedCamera: SharedCamera? = null
    private var cameraId: String? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewBuilder: CaptureRequest.Builder? = null
    private var camThread: HandlerThread? = null
    private var camHandler: Handler? = null
    @Volatile private var arcoreActive = false          // 캡처세션 구성 후에만 session.update() 허용
    @Volatile private var aeLock = false                // 기본 AUTO, 토글로 LOCK
    @Volatile private var camModesLine = "unknown"      // CaptureResult 실측 (OIS/AE/AF...) → meta
    private var capLogCount = 0

    // 촬영 화면 지표 (친화 UI)
    private lateinit var sigDot: TextView
    private lateinit var sigText: TextView
    private lateinit var aeBadge: TextView
    private lateinit var dropWarn: TextView
    private lateinit var recTimer: TextView
    private lateinit var learnBadge: TextView
    @Volatile private var trackingOk = false   // REC 가드용: TRACKING 여부
    private var recStartMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        poseText = findViewById(R.id.poseText)
        recBtn = findViewById(R.id.recBtn)
        glView = findViewById(R.id.glView)
        gridOverlay = findViewById(R.id.gridOverlay)
        menuBtn = findViewById(R.id.menuBtn)

        recorder = Recorder(getExternalFilesDir(null)!!)
        imu = ImuLogger(this)
        pendingUploads = UploadQueue.pendingCount(this)
        UploadQueue.enqueue(this)   // 지난번에 못 보낸 것이 있으면 이어서 보낸다

        recBtn.setOnClickListener {
            when {
                recorder?.isRecording == true -> pendingToggle = true   // 정지는 항상 허용
                !trackingOk -> Toast.makeText(this, "위치 잡는 중이에요 — 잠시 후 다시 시도", Toast.LENGTH_SHORT).show()
                !aeLock -> Toast.makeText(this, "밝기를 고정해 주세요 (설정 → 밝기 고정)", Toast.LENGTH_SHORT).show()
                else -> pendingToggle = true
            }
        }

        // 설정·기록·보조 토글은 메뉴로. 녹화 중엔 화면 이동을 막는다.
        menuBtn.setOnClickListener { showMenu() }

        // 지표 뷰
        sigDot = findViewById(R.id.sigDot)
        sigText = findViewById(R.id.sigText)
        aeBadge = findViewById(R.id.aeBadge)
        dropWarn = findViewById(R.id.dropWarn)
        recTimer = findViewById(R.id.recTimer)
        learnBadge = findViewById(R.id.learnBadge)
        learnBadge.setOnClickListener { showLearningPicker() }

        glView.preserveEGLContextOnPause = true
        glView.setEGLContextClientVersion(2)
        glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        glView.setRenderer(this)
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    private fun focusName() = if (autoFocus) "AUTO" else "FIXED"

    private fun aeName() = if (aeLock) "LOCK" else "AUTO"

    // BE(SKILL-01/TRAIN-02) 연동 전 스텁 학습 목록. 라이브 붙으면 이 목록만 교체.
    private val stubCatalog = listOf(
        Triple("job_pick_place", "pick_place", "집기·놓기"),
        Triple("job_sort_two", "sort_two", "둘 분류"),
        Triple("job_align_fixture", "align_fixture", "지그 정렬"),
        Triple("job_present_inspect", "present_inspect", "검사 제시"),
        Triple("job_line_up", "line_up", "줄 세우기")
    )

    /** 배지 텍스트 = 활성 학습 + 해당 학습으로 찍은 편수(오배정 조기 인지용). */
    private fun updateLearnBadge() {
        if (!::learnBadge.isInitialized) return
        val name = Prefs.activeSkillName(this)
        learnBadge.text = if (name.isBlank()) "학습: 미선택 ▾"
            else "학습: $name · ${episodeCount(Prefs.activeJobId(this))}편 ▾"
    }

    private fun episodeCount(jobId: String): Int {
        if (jobId.isBlank()) return 0
        val base = getExternalFilesDir(null) ?: return 0
        return base.listFiles { f -> f.isDirectory && f.name.startsWith("rec_") }
            ?.count { d ->
                File(d, "meta.txt").let { it.exists() && it.readText().contains("training_job_id=$jobId") }
            } ?: 0
    }

    /** 학습 선택 — 촬영(다크) 테마의 커스텀 시트. 활성 학습은 azure 테두리 + 편수 표시. */
    private fun showLearningPicker() {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val activeJob = Prefs.activeJobId(this)
        val dialog = android.app.Dialog(this)
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        root.addView(TextView(this).apply {
            text = "학습 선택"; setTextColor(0xFFFFFFFF.toInt()); textSize = 18f
            setPadding(dp(4), 0, dp(4), dp(12))
        })

        fun addCard(name: String, sub: String, active: Boolean, onSelect: () -> Unit) {
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundResource(if (active) R.drawable.bg_pick_card_active else R.drawable.bg_pick_card)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                isClickable = true
                setOnClickListener { onSelect(); updateLearnBadge(); dialog.dismiss() }
            }
            row.addView(TextView(this).apply {
                text = name; setTextColor(0xFFFFFFFF.toInt()); textSize = 16f
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (sub.isNotEmpty()) row.addView(TextView(this).apply {
                text = sub; setTextColor(0xFFB8C0D8.toInt()); textSize = 13f
                setPadding(0, 0, dp(10), 0)
            })
            row.addView(TextView(this).apply {
                text = if (active) "●" else ""; setTextColor(0xFF4F9CF9.toInt()); textSize = 16f
            })
            root.addView(row)
        }

        for ((jobId, skillKey, label) in stubCatalog) {
            addCard(label, "${episodeCount(jobId)}편", jobId == activeJob) {
                Prefs.setActiveLearning(this, jobId, skillKey, label)
            }
        }
        addCard("학습 없이 촬영", "", activeJob.isBlank()) {
            Prefs.setActiveLearning(this, "", "", "")
        }

        val scroll = android.widget.ScrollView(this).apply {
            setBackgroundResource(R.drawable.bg_menu_dark)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(root)
        }
        dialog.setContentView(scroll)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
        // 창을 화면 안(≤90%)에 맞추고 내용이 넘치면 내부에서만 스크롤 — 창 자체는 안 움직이게 높이 고정.
        val approxPx = dp(44 + (stubCatalog.size + 1) * 66 + 32)
        val maxH = (resources.displayMetrics.heightPixels * 0.9f).toInt()
        dialog.window?.setLayout(dp(340), minOf(approxPx, maxH))
    }

    /** 우상단 메뉴: 기록·설정만(구도 그리드·상세값은 설정 화면에서). 코드로 조립(다크 팝업). */
    private fun showMenu() {
        val d = resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_menu_dark)
            setPadding(px(6), px(6), px(6), px(6))
        }
        val pop = PopupWindow(
            container, px(230), LinearLayout.LayoutParams.WRAP_CONTENT, true
        ).apply { isOutsideTouchable = true; elevation = px(8).toFloat() }

        fun guardNav(cls: Class<*>) {
            if (recorder?.isRecording == true) Toast.makeText(this, "녹화 중에는 이동할 수 없음", Toast.LENGTH_SHORT).show()
            else startActivity(android.content.Intent(this, cls))
            pop.dismiss()
        }
        fun row(label: String, icon: Int, onClick: () -> Unit) = TextView(this).apply {
            text = label
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 17f
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            compoundDrawablePadding = px(14)
            setPadding(px(16), px(15), px(16), px(15))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isClickable = true
            setOnClickListener { onClick() }
        }

        container.addView(row("기록", R.drawable.ic_history) { guardNav(HistoryActivity::class.java) })
        container.addView(row("설정", R.drawable.ic_tune) { guardNav(SettingsActivity::class.java) })
        pop.showAsDropDown(menuBtn)
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

        // 설정 화면에서 바꾼 AF/AE 모드 반영. 세션 생성(applyConfig)·카메라 오픈(applyRequestKeys)이
        // 이 값을 읽으므로 그 전에 세팅한다. AE=LOCK 이면 아래 openCamera 가 현재 장면에 잠근다.
        val afChanged = autoFocus != Prefs.afAuto(this)
        autoFocus = Prefs.afAuto(this)
        aeLock = Prefs.aeLock(this)
        if (afChanged) intrinsicsLine = "unknown"   // 초점 바뀌면 내부 파라미터 다시 읽기
        // AF 바뀌면 ARCore focusMode 도 재적용. (기존엔 최초 생성 때만 applyConfig 해서 토글이 안 먹던 버그)
        if (afChanged) session?.let { try { applyConfig(it) } catch (e: Exception) { Log.e(TAG, "AF 재적용 실패", e) } }
        updateLearnBadge()   // 학습 배지·편수 갱신
        gridOverlay.visibility = if (Prefs.gridGuide(this)) View.VISIBLE else View.GONE
        poseText.visibility = if (Prefs.showDetail(this)) View.VISIBLE else View.GONE

        if (session == null) {
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true; return
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> {}
                }
                val s = Session(this, EnumSet.of(Session.Feature.SHARED_CAMERA))
                applyConfig(s)
                selectCameraConfig(s)
                sharedCamera = s.sharedCamera
                cameraId = s.cameraConfig.cameraId
                session = s
            } catch (e: UnavailableException) {
                showError("ARCore 사용 불가: ${e.javaClass.simpleName} - ${e.message}")
                Log.e(TAG, "unavailable", e); return
            } catch (e: Exception) {
                showError("세션 생성 실패: ${e.message}")
                Log.e(TAG, "create failed", e); return
            }
        }

        // 공유 카메라: session.resume() 은 캡처세션이 구성된 뒤 resumeARCore() 에서 부른다.
        startCamThread()
        glView.onResume()
        imu.register()
        openCamera()
    }

    override fun onPause() {
        super.onPause()
        if (recorder?.isRecording == true) stopRecording()
        imu.unregister()
        if (session != null) {
            arcoreActive = false
            glView.onPause()
            session?.pause()
            closeCamera()
            // 카메라 스레드는 여기서 죽이지 않는다. cameraDevice.close() 는 비동기라
            // onClosed 콜백이 잠시 뒤 이 스레드로 오는데, 지금 종료하면 "dead thread" 예외로
            // 앱이 죽는다. 스레드는 onDestroy 에서만 정리한다.
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        closeCamera()
        stopCamThread()
        try { session?.close() } catch (_: Exception) {}
        session = null
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

    // ---------------- SharedCamera (AE/AWB lock + 모드 실측) ----------------

    private fun startCamThread() {
        if (camThread != null) return
        camThread = HandlerThread("cam").apply { start() }
        camHandler = Handler(camThread!!.looper)
    }

    private fun stopCamThread() {
        camThread?.quitSafely()
        camThread = null; camHandler = null
    }

    @SuppressLint("MissingPermission")   // onResume 에서 권한 확인 후에만 호출
    private fun openCamera() {
        val id = cameraId ?: return
        val sc = sharedCamera ?: return
        val h = camHandler ?: return
        try {
            val cm = getSystemService(CameraManager::class.java)
            cm.openCamera(id, sc.createARDeviceStateCallback(deviceCb, h), h)
        } catch (e: Exception) {
            Log.e(TAG, "openCamera 실패", e)
        }
    }

    private fun closeCamera() {
        captureSession?.close(); captureSession = null
        cameraDevice?.close(); cameraDevice = null
    }

    private val deviceCb = object : CameraDevice.StateCallback() {
        override fun onOpened(device: CameraDevice) { cameraDevice = device; createCamSession() }
        override fun onClosed(device: CameraDevice) {}
        override fun onDisconnected(device: CameraDevice) { device.close(); cameraDevice = null }
        override fun onError(device: CameraDevice, error: Int) {
            Log.e(TAG, "camera error $error"); device.close(); cameraDevice = null
        }
    }

    private fun createCamSession() {
        val device = cameraDevice ?: return
        val sc = sharedCamera ?: return
        val h = camHandler ?: return
        try {
            val b = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            sc.arCoreSurfaces.forEach { b.addTarget(it) }
            previewBuilder = b
            applyRequestKeys(b)
            @Suppress("DEPRECATION")
            device.createCaptureSession(sc.arCoreSurfaces, sc.createARSessionStateCallback(sessionCb, h), h)
        } catch (e: Exception) {
            Log.e(TAG, "createCamSession 실패", e)
        }
    }

    private val sessionCb = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(cs: CameraCaptureSession) {
            captureSession = cs
            setRepeating()
            resumeARCore()
        }
        override fun onConfigureFailed(cs: CameraCaptureSession) { Log.e(TAG, "capture session 구성 실패") }
    }

    private fun resumeARCore() {
        try {
            session?.resume(); arcoreActive = true
            Log.i(TAG, "ARCore resumed (shared camera)")
        } catch (e: Exception) {
            Log.e(TAG, "session.resume 실패", e)
        }
    }

    /** 캡처요청에 손떨림보정 off + AF(포커스 토글) + AE/AWB lock 을 실어준다. */
    private fun applyRequestKeys(b: CaptureRequest.Builder) {
        // OIS/EIS off: 안정화가 켜지면 이미지 움직임이 실제 움직임과 분리돼
        // "카메라가 그리퍼에 고정" 전제가 깨진다 (실측 OIS 기본 ON 이었음). 항상 끈다.
        b.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
            CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF)
        b.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
            CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
        // 30fps 고정. 우리가 캡처요청을 쥐면서 ARCore 의 fps 제약이 빠져 노출 짧을 때
        // 카메라가 60fps 로 free-run 했다(계약 30Hz 위반). cameraConfig 와 동일하게 잠근다.
        b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30, 30))
        if (autoFocus) {
            b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        } else {
            b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            // FIXED 초점 거리(디옵터). 디버그 슬라이더로 조절, 기본 6.25≈16cm. 물리 튜닝 노브.
            b.set(CaptureRequest.LENS_FOCUS_DISTANCE, Prefs.focusDiopters(this))
        }
        b.set(CaptureRequest.CONTROL_AE_LOCK, aeLock)
        b.set(CaptureRequest.CONTROL_AWB_LOCK, aeLock)
    }

    private fun setRepeating() {
        val cs = captureSession ?: return
        val b = previewBuilder ?: return
        try {
            cs.setRepeatingRequest(b.build(), captureCb, camHandler)
        } catch (e: Exception) {
            Log.e(TAG, "setRepeating 실패", e)
        }
    }

    private val captureCb = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            s: CameraCaptureSession, req: CaptureRequest, res: TotalCaptureResult
        ) {
            val ois = res.get(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE)
            val eis = res.get(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE)
            val aeMode = res.get(CaptureResult.CONTROL_AE_MODE)
            val aeLk = res.get(CaptureResult.CONTROL_AE_LOCK)
            val afMode = res.get(CaptureResult.CONTROL_AF_MODE)
            val awbMode = res.get(CaptureResult.CONTROL_AWB_MODE)
            val awbLk = res.get(CaptureResult.CONTROL_AWB_LOCK)
            val exp = res.get(CaptureResult.SENSOR_EXPOSURE_TIME)
            val iso = res.get(CaptureResult.SENSOR_SENSITIVITY)
            camModesLine = "ois=$ois eis=$eis ae_mode=$aeMode ae_lock=$aeLk " +
                "af_mode=$afMode awb_mode=$awbMode awb_lock=$awbLk exp_ns=$exp iso=$iso"
            if (capLogCount < 6) { Log.i(TAG, "CAM $camModesLine"); capLogCount++ }
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
            // QR 로 채울 세션 메타 (지금은 빈 값). 서버 전송·episode.json 이 집어감.
            append("server_url=${Prefs.serverUrl(this@MainActivity)}\n")
            append("user_id=${Prefs.userId(this@MainActivity)}\n")
            append("session_id=${Prefs.sessionId(this@MainActivity)}\n")
            append("skill_id=${Prefs.skillId(this@MainActivity)}\n")
            append("training_job_id=${Prefs.activeJobId(this@MainActivity)}\n")
            append("active_skill_key=${Prefs.activeSkillKey(this@MainActivity)}\n")
            append("focus_mode=${focusName()}\n")
            append("ae_lock=${aeName()}\n")
            append("camera_modes=$camModesLine\n")
            append("camera_config=$cameraConfigLine\n")
            append("intrinsics=$intrinsicsLine\n")
            append("start_wallclock_ms=${System.currentTimeMillis()}\n")
            append("start_elapsed_realtime_ns=${android.os.SystemClock.elapsedRealtimeNanos()}\n")
            append("note=timestamp_ns is ARCore frame.timestamp; base not guaranteed by ARCore but measured == elapsedRealtimeNanos on tested devices (compare first pose ts to start_elapsed_realtime_ns)\n")
            append(imu.describe())
        }
        lastJumpMm = 0.0; lastJumpDeg = 0.0
        recStartMs = System.currentTimeMillis()
        recorder?.start(meta)
        recorder?.currentDir?.let { imu.start(it) }
        runOnUiThread {
            recBtn.setBackgroundResource(R.drawable.bg_shutter_stop)
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
        recStartMs = 0L
        runOnUiThread {
            recBtn.setBackgroundResource(R.drawable.bg_shutter)
            recTimer.visibility = View.GONE
            dropWarn.visibility = View.GONE
        }
        Log.i(TAG, "저장 완료: ${dir?.absolutePath} 프레임=$saved 드롭=$dropped 점프=$jumps AF=${focusName()}")

        // 자동 전송 안 함(검토 후 전송). 사용자가 기록에서 골라 [전송]해야 큐에 들어간다.
        pendingUploads = UploadQueue.pendingCount(this)

        // 촬영 직후 마커 검출률을 백그라운드(~1초)로 계산해 폴더에 저장한다.
        // 팝업으로 흐름 끊지 않고, 기록 화면이 이 값을 읽어 사용가능 판정에 쓴다 (S15P21A103-160).
        if (dir != null) Thread {
            val qc = MarkerQc.detectRate(File(dir, "frames"))
            try {
                File(dir, "marker_qc.txt").writeText("detected=${qc.detected}\ntotal=${qc.total}\n")
            } catch (e: Exception) {
                Log.e(TAG, "marker_qc 저장 실패", e)
            }
        }.also { it.name = "marker-qc"; it.start() }
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
        if (!arcoreActive) return   // 공유 카메라 캡처세션 준비 전에는 update() 금지

        try {
            s.setCameraTextureName(bg.textureId)
            val frame = s.update()
            bg.draw(frame)

            // REC 토글 (중복 프레임 여부와 무관하게 즉시 반응). AF/AE 는 설정 화면에서 바꾼다.
            if (pendingToggle) {
                pendingToggle = false
                if (recorder?.isRecording == true) stopRecording() else startRecording()
            }

            val ts = frame.timestamp

            // 카메라 30Hz 인데 프레임이 두 번씩 온다(shared 모드는 ~0.1ms 차, 옛 모드는 동일 ts).
            // 직전 채택 프레임과 MIN_FRAME_GAP_NS 보다 촘촘하면 중복으로 보고 버린다.
            if (lastFrameTs != 0L && ts - lastFrameTs < MIN_FRAME_GAP_NS) return
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
                trackingOk = state == TrackingState.TRACKING
                val recording = rec != null && rec.isRecording
                val dropped = rec?.droppedFrames ?: 0
                val elapsed = if (recording && recStartMs > 0) (System.currentTimeMillis() - recStartMs) / 1000 else 0L
                val (roll, pitch, yaw) = quatToEulerDeg(q)
                val reason = if (state == TrackingState.PAUSED)
                    "  (${camera.trackingFailureReason})" else ""
                val recLine = if (recording)
                    "\n● REC ${rec?.savedFrames ?: 0}프레임  드롭${dropped}  점프${rec?.jumpCount ?: 0}" +
                        (if ((rec?.jumpCount ?: 0) > 0) " (최대 ${"%.0f".format(rec?.maxJumpMm ?: 0.0)}mm)" else "")
                else if (lastJumpMm > 0 || lastJumpDeg > 0)
                    "\n최근 점프 ${"%.0f".format(lastJumpMm)}mm / ${"%.1f".format(lastJumpDeg)}°"
                else ""
                val detail = buildString {
                    append("$state$reason   ${"%.1f".format(fps)} fps   AF:${focusName()}$recLine\n")
                    if (pendingUploads > 0) append("전송 대기 ${pendingUploads}건\n")
                    append("\n")
                    append("x ${"%+.4f".format(t[0])}   y ${"%+.4f".format(t[1])}   z ${"%+.4f".format(t[2])}  (m)\n")
                    append("r ${"%+.1f".format(roll)}°  p ${"%+.1f".format(pitch)}°  y ${"%+.1f".format(yaw)}°\n")
                }
                runOnUiThread {
                    // 촬영 신호등 — 개발용 용어 대신 사람 말로
                    when {
                        lowSpaceNotice -> { sigDot.setTextColor(0xFFE0685E.toInt()); sigText.text = "저장공간 부족 — 녹화 불가" }
                        trackingOk -> { sigDot.setTextColor(0xFF3FBF87.toInt()); sigText.text = if (recording) "녹화 중" else "촬영 준비 완료" }
                        state == TrackingState.PAUSED -> { sigDot.setTextColor(0xFFE0685E.toInt()); sigText.text = "위치 잡는 중 — 천천히 움직이세요" }
                        else -> { sigDot.setTextColor(0xFFE0A44E.toInt()); sigText.text = "준비 중…" }
                    }
                    aeBadge.visibility = if (aeLock) View.GONE else View.VISIBLE
                    dropWarn.text = "⚠ 누락 $dropped"
                    dropWarn.visibility = if (recording && dropped > 0) View.VISIBLE else View.GONE
                    if (recording) {
                        recTimer.visibility = View.VISIBLE
                        recTimer.text = "● %02d:%02d".format(elapsed / 60, elapsed % 60)
                    } else {
                        recTimer.visibility = View.GONE
                        // 녹화 아닐 때만 버튼 갱신 (녹화 중엔 startRecording 이 '정지'로 세팅)
                        recBtn.setBackgroundResource(R.drawable.bg_shutter)
                        recBtn.alpha = if (trackingOk) 1f else 0.5f
                    }
                    poseText.text = detail
                }
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
