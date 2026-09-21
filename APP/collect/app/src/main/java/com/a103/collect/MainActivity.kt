package com.a103.collect

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * 수집 화면. SurfaceView 프리뷰 + 시작/종료/기록. 초광각 카메라 + IMU + 영상(회전 baked)을
 * 동시에 기록해 세션 폴더 6파일을 만든다. 종료 시 성공/실패/미판정 결과를 manifest 에 남긴다.
 */
class MainActivity : Activity() {

    private lateinit var root: File
    private lateinit var preview: SurfaceView
    private lateinit var status: TextView
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var learnBtn: Button

    private val worker = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    private var camera: CameraCapture? = null
    private var video: VideoCapture? = null
    private var imu: ImuCapture? = null
    private var current: Recording? = null

    @Volatile private var busy = false
    @Volatile private var recording = false
    @Volatile private var stopping = false
    private var started = 0L

    private val ticker = object : Runnable {
        override fun run() {
            if (!recording) return
            val cur = current
            when {
                cur?.error != null -> finishRecording(cur.error)
                root.usableSpace < 100L * 1024 * 1024 -> finishRecording("저장 공간 부족")
                else -> {
                    val sec = (SystemClock.elapsedRealtime() - started) / 1000
                    status.text = "녹화 중 · 초광각 고정초점 · ${sec}초\n영상 + 가속도 + 자이로 저장 중"
                    ui.postDelayed(this, 1000)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = File(getExternalFilesDir(null), "sessions").apply { mkdirs() }

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 8, 16, 8) }
        status = TextView(this).apply {
            textSize = 18f
            text = "UMI 수집 · 1080p / 30fps · IMU 200Hz 요청\n녹화 시작 후 접근 → 잡기 → 들기 → 3초 유지"
        }
        col.addView(status)

        val bar = LinearLayout(this)
        startBtn = Button(this).apply { text = "녹화 시작"; setOnClickListener { begin() } }
        stopBtn = Button(this).apply { text = "녹화 종료"; isEnabled = false; setOnClickListener { finishRecording(null) } }
        learnBtn = Button(this).apply { setOnClickListener { pickLearning() } }
        val listBtn = Button(this).apply {
            text = "기록"
            setOnClickListener { if (!recording && !busy) startActivity(android.content.Intent(this@MainActivity, HistoryActivity::class.java)) }
        }
        bar.addView(startBtn); bar.addView(stopBtn); bar.addView(learnBtn); bar.addView(listBtn)
        col.addView(bar)
        updateLearnLabel()

        preview = SurfaceView(this).apply { holder.setFixedSize(1920, 1080) }
        col.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(col)
    }

    private fun updateLearnLabel() {
        learnBtn.text = "학습: ${Prefs.activeSkillName(this)}"
    }

    /** 학습 선택. 미분류 + 서버 카탈로그 캐시(없으면 미분류만). */
    private fun pickLearning() {
        if (recording) return
        val keys = arrayListOf("")               // "" = 미분류
        val names = arrayListOf("미분류 (나중에 지정)")
        runCatching {
            val arr = JSONArray(Prefs.skillCatalog(this).ifBlank { "[]" })
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                keys.add(o.optString("key")); names.add(o.optString("name"))
            }
        }
        AlertDialog.Builder(this).setTitle("학습 선택")
            .setItems(names.toTypedArray()) { _, i ->
                Prefs.setActiveSkill(this, keys[i], names[i].substringBefore(" ("))
                updateLearnLabel()
            }.show()
    }

    private fun begin() {
        if (busy || recording) return
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 1); return
        }
        if (!preview.holder.surface.isValid) { status.text = "화면 준비 후 다시 시작하세요."; return }
        busy = true
        startBtn.isEnabled = false
        status.text = "초광각과 IMU 준비 중…"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        worker.execute {
            try {
                if (root.usableSpace < 300L * 1024 * 1024) throw IOException("저장 공간 300MB 이상 필요")
                val cam = CameraCapture(this)
                val rec = Recording(root, cam.metadata, Prefs.activeSkillKey(this))
                val im = ImuCapture(this).also { it.start(rec) }
                val vid = VideoCapture(rec)
                camera = cam; current = rec; imu = im; video = vid
                cam.open(preview.holder.surface, vid.surface, rec, object : CameraCapture.Listener {
                    override fun ready() { ui.post {
                        if (!busy || stopping) return@post
                        busy = false; recording = true
                        stopBtn.isEnabled = true
                        started = SystemClock.elapsedRealtime()
                        ticker.run()
                    } }
                    override fun failed(reason: String) { ui.post { finishRecording(reason) } }
                })
            } catch (e: Exception) {
                ui.post { finishRecording(e.toString()) }
            }
        }
    }

    private fun finishRecording(reason: String?) {
        if (stopping || (!busy && !recording)) return
        stopping = true; recording = false; busy = true
        stopBtn.isEnabled = false
        ui.removeCallbacks(ticker)
        status.text = "파일 마무리 중…"
        worker.execute {
            var err = reason
            try { camera?.stop() } catch (e: Exception) { err = e.toString() }; camera = null
            try { video?.stop() } catch (e: Exception) { err = e.toString() }; video = null
            try { imu?.stop() } catch (e: Exception) { err = e.toString() }; imu = null
            val rec = current; current = null
            if (rec != null) try { rec.finish(err) } catch (e: Exception) { err = e.toString() }
            ui.post { afterStop(err, rec) }
        }
    }

    private fun afterStop(err: String?, rec: Recording?) {
        busy = false; stopping = false
        startBtn.isEnabled = true
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        status.text = if (err == null) "저장 완료 · ${rec?.dir?.name}" else "불완전 녹화 / 오류: $err"
        if (err == null && rec != null && !isFinishing && !isDestroyed) {
            AlertDialog.Builder(this).setTitle("시연 결과")
                .setItems(arrayOf("성공", "실패", "장비 시험 / 미판정")) { _, i ->
                    val outcome = arrayOf("success", "failure", "unrated")[i]
                    worker.execute {
                        try { rec.outcome(outcome) }
                        catch (e: Exception) { ui.post { status.text = "결과 저장 실패: $e" } }
                    }
                }.show()
        }
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<String>, g: IntArray) {
        super.onRequestPermissionsResult(rc, p, g)
    }

    override fun onPause() {
        if (recording || busy) finishRecording("앱이 백그라운드로 전환됨")
        super.onPause()
    }
}
