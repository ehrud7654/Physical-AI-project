package com.a103.collect

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * 촬영 화면(가로 오버레이). 카메라 프리뷰 위에 상단바(신호등·학습칩·메뉴)와 좌측 원형 셔터.
 * 셔터로 녹화 토글, 종료 시 성공/실패/미판정. 초광각+IMU+영상 6파일을 세션 폴더에 남긴다.
 */
class MainActivity : Activity() {

    private lateinit var root: File
    private lateinit var preview: SurfaceView
    private lateinit var sigDot: TextView
    private lateinit var sigText: TextView
    private lateinit var recTimer: TextView
    private lateinit var learnBadge: TextView
    private lateinit var menuBtn: TextView
    private lateinit var shutter: View

    private val worker = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    private var camera: CameraCapture? = null
    private var previewCam: CameraCapture? = null
    private var video: VideoCapture? = null
    private var imu: ImuCapture? = null
    private var current: Recording? = null

    @Volatile private var busy = false
    @Volatile private var recording = false
    @Volatile private var stopping = false
    private var started = 0L

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val ticker = object : Runnable {
        override fun run() {
            if (!recording) return
            val cur = current
            when {
                cur?.error != null -> finishRecording(cur.error)
                root.usableSpace < 100L * 1024 * 1024 -> finishRecording("저장 공간 부족")
                else -> {
                    val sec = (SystemClock.elapsedRealtime() - started) / 1000
                    recTimer.text = "● %02d:%02d".format(sec / 60, sec % 60)
                    ui.postDelayed(this, 1000)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = File(getExternalFilesDir(null), "sessions").apply { mkdirs() }

        val outer = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        preview = SurfaceView(this).apply {
            holder.setFixedSize(1920, 1080)
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(h: SurfaceHolder) { maybeStartPreview() }
                override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {}
                override fun surfaceDestroyed(h: SurfaceHolder) { stopPreview() }
            })
        }
        outer.addView(preview, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // 상단바
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xCC0E1524.toInt()); setPadding(dp(16), dp(11), dp(16), dp(11))
        }
        sigDot = TextView(this).apply { text = "●"; textSize = 18f; setTextColor(0xFF8A93AD.toInt()) }
        sigText = TextView(this).apply {
            textSize = 16f; setTextColor(0xFFDCE3F2.toInt()); setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(8), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        recTimer = TextView(this).apply {
            text = "● 00:00"; textSize = 16f; setTextColor(0xFFE0685E.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD); visibility = View.GONE
            setPadding(0, 0, dp(14), 0)
        }
        learnBadge = TextView(this).apply {
            background = GradientDrawable().apply { cornerRadius = dp(999).toFloat(); setColor(0xFF2E4A6B.toInt()) }
            setPadding(dp(14), dp(7), dp(14), dp(7)); textSize = 14f; setTextColor(0xFFDCE3F2.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setOnClickListener { pickLearning() }
        }
        menuBtn = TextView(this).apply {
            text = "☰"; textSize = 22f; setTextColor(0xFFDCE3F2.toInt()); setPadding(dp(12), dp(2), dp(4), dp(2))
            setOnClickListener { showMenu() }
        }
        bar.addView(sigDot); bar.addView(sigText); bar.addView(recTimer); bar.addView(learnBadge); bar.addView(menuBtn)
        outer.addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        // 좌측 원형 셔터
        shutter = View(this).apply {
            setBackgroundResource(R.drawable.bg_shutter)
            setOnClickListener { if (recording) finishRecording(null) else begin() }
        }
        outer.addView(shutter, FrameLayout.LayoutParams(dp(74), dp(74), Gravity.START or Gravity.CENTER_VERTICAL)
            .apply { marginStart = dp(28) })

        setContentView(outer)
        updateLearnBadge()
        setSignal(SIG_IDLE)
    }

    private var previewRetry = 0

    /** 녹화 대기 중 초광각 라이브 프리뷰. 이미 켜졌거나 녹화/준비 중이면 무시. */
    private fun maybeStartPreview() {
        if (recording || busy || previewCam != null) return
        if (!preview.holder.surface.isValid) return
        val cam = try { CameraCapture(this) } catch (e: Exception) { sigText.text = "카메라 사용 불가: ${e.message}"; return }
        previewCam = cam
        cam.openPreview(preview.holder.surface, object : CameraCapture.Listener {
            override fun ready() { ui.post { previewRetry = 0; if (!recording && !busy) setSignal(SIG_IDLE) } }
            override fun failed(reason: String) {
                ui.post {
                    val pc = previewCam; previewCam = null
                    worker.execute { try { pc?.stop() } catch (_: Exception) {} }
                    // 직전 화면(온보딩 QR)의 카메라가 아직 해제 중이면 잠시 후 재시도.
                    if (!recording && !busy && previewRetry < 5) {
                        previewRetry++; ui.postDelayed({ maybeStartPreview() }, 700)
                    } else sigText.text = "프리뷰 실패: $reason"
                }
            }
        })
    }

    private fun stopPreview() {
        val pc = previewCam ?: return
        previewCam = null
        worker.execute { try { pc.stop() } catch (_: Exception) {} }
    }

    private fun setSignal(state: Int) {
        val (color, text) = when (state) {
            SIG_REC -> 0xFF3FBF87.toInt() to "녹화 중"
            SIG_BUSY -> 0xFFE0A44E.toInt() to "카메라 준비 중…"
            else -> 0xFF8A93AD.toInt() to "대기 — 셔터를 누르세요"
        }
        sigDot.setTextColor(color); sigText.text = text
    }

    private fun updateLearnBadge() { learnBadge.text = "학습: ${Prefs.activeSkillName(this)} ▾" }

    /** 우상단 메뉴 — 다크 커스텀 팝업(기본 PopupMenu 대신). */
    private fun showMenu() {
        if (recording) return
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_menu_dark); setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        val pop = PopupWindow(container, dp(180), LinearLayout.LayoutParams.WRAP_CONTENT, true)
            .apply { isOutsideTouchable = true; elevation = dp(8).toFloat() }
        val row = TextView(this).apply {
            text = "기록"; setTextColor(0xFFFFFFFF.toInt()); textSize = 17f
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_history, 0, 0, 0)
            compoundDrawablePadding = dp(14); setPadding(dp(16), dp(15), dp(16), dp(15))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                if (!busy && !recording) startActivity(Intent(this@MainActivity, HistoryActivity::class.java))
                pop.dismiss()
            }
        }
        container.addView(row)
        pop.showAsDropDown(menuBtn)
    }

    private data class CardRow(val label: String, val active: Boolean, val onClick: () -> Unit)

    /** 다크 카드 시트 — 학습 선택·시연 결과 등에 공용(기본 AlertDialog 대신). */
    private fun cardSheet(title: String, rows: List<CardRow>) {
        val dialog = Dialog(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(TextView(this).apply {
            text = title; setTextColor(0xFFFFFFFF.toInt()); textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(dp(4), 0, dp(4), dp(12))
        })
        rows.forEach { r ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(if (r.active) R.drawable.bg_pick_card_active else R.drawable.bg_pick_card)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = dp(8) }
                setOnClickListener { r.onClick(); dialog.dismiss() }
            }
            row.addView(TextView(this).apply {
                text = r.label; setTextColor(0xFFFFFFFF.toInt()); textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply {
                text = if (r.active) "●" else ""; setTextColor(0xFF4F9CF9.toInt()); textSize = 16f
            })
            root.addView(row)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundResource(R.drawable.bg_menu_dark); setPadding(dp(16), dp(16), dp(16), dp(16)); addView(root)
        }
        dialog.setContentView(scroll)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(dp(340), LinearLayout.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    private fun pickLearning() {
        if (recording) return
        val activeKey = Prefs.activeSkillKey(this)
        val rows = ArrayList<CardRow>()
        rows.add(CardRow("미분류 (나중에 지정)", activeKey.isBlank()) {
            Prefs.setActiveSkill(this, "", "미분류"); updateLearnBadge()
        })
        runCatching {
            val arr = JSONArray(Prefs.skillCatalog(this).ifBlank { "[]" })
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i); val key = o.optString("key"); val name = o.optString("name")
                rows.add(CardRow(name, key == activeKey && key.isNotBlank()) {
                    Prefs.setActiveSkill(this, key, name); updateLearnBadge()
                })
            }
        }
        cardSheet("학습 선택", rows)
    }

    private fun begin() {
        if (busy || recording) return
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 1); return
        }
        if (!preview.holder.surface.isValid) { sigText.text = "화면 준비 후 다시"; return }
        busy = true
        shutter.isEnabled = false
        setSignal(SIG_BUSY)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val pc = previewCam; previewCam = null   // 같은 카메라 → 녹화 전에 프리뷰 세션 해제
        worker.execute {
            try {
                try { pc?.stop() } catch (_: Exception) {}
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
                        shutter.isEnabled = true
                        shutter.setBackgroundResource(R.drawable.bg_shutter_stop)
                        recTimer.visibility = View.VISIBLE
                        setSignal(SIG_REC)
                        started = SystemClock.elapsedRealtime()
                        ticker.run()
                    } }
                    override fun failed(reason: String) { ui.post { finishRecording(reason) } }
                })
            } catch (e: Exception) { ui.post { finishRecording(e.toString()) } }
        }
    }

    private fun finishRecording(reason: String?) {
        if (stopping || (!busy && !recording)) return
        stopping = true; recording = false; busy = true
        shutter.isEnabled = false
        ui.removeCallbacks(ticker)
        setSignal(SIG_BUSY); sigText.text = "파일 마무리 중…"
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
        shutter.isEnabled = true
        shutter.setBackgroundResource(R.drawable.bg_shutter)
        recTimer.visibility = View.GONE
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setSignal(SIG_IDLE)
        maybeStartPreview()   // 녹화 세션 해제됨 → 대기 프리뷰 재개
        if (err != null) { sigText.text = "오류: $err"; return }
        sigText.text = "저장 완료"
        if (rec != null && !isFinishing && !isDestroyed) {
            val opts = listOf("성공" to "success", "실패" to "failure", "장비 시험 / 미판정" to "unrated")
            cardSheet("시연 결과", opts.map { (label, outcome) ->
                CardRow(label, false) {
                    worker.execute {
                        try { rec.outcome(outcome) } catch (e: Exception) { ui.post { sigText.text = "결과 저장 실패: $e" } }
                    }
                }
            })
        }
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<String>, g: IntArray) {
        super.onRequestPermissionsResult(rc, p, g)
    }

    override fun onResume() { super.onResume(); maybeStartPreview() }

    override fun onPause() {
        if (recording || busy) finishRecording("앱이 백그라운드로 전환됨") else stopPreview()
        super.onPause()
    }

    companion object { private const val SIG_IDLE = 0; private const val SIG_BUSY = 1; private const val SIG_REC = 2 }
}
