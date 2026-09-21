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
import java.io.File
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
    private lateinit var redoBtn: TextView
    private var lastDir: File? = null

    private val worker = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    // 카메라 세션·인코더는 지속형(프리뷰~녹화 내내 유지). IMU·Recording 만 녹화 단위.
    private var camera: CameraCapture? = null
    private var encoder: VideoEncoder? = null
    private var imu: ImuCapture? = null
    private var current: Recording? = null
    @Volatile private var engineReady = false

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
        DeviceSkills.load(this)

        val outer = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        preview = SurfaceView(this).apply {
            holder.setFixedSize(1920, 1080)
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(h: SurfaceHolder) { startEngine() }
                override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {}
                override fun surfaceDestroyed(h: SurfaceHolder) { stopEngine() }
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

        // 직전 촬영 재촬영(삭제 후 다시). 저장 직후에만 노출.
        redoBtn = TextView(this).apply {
            text = "↻ 재촬영"; setTextColor(0xFFFFFFFF.toInt()); textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(dp(16), dp(9), dp(16), dp(9))
            background = GradientDrawable().apply { cornerRadius = dp(999).toFloat(); setColor(0xCC1B2440.toInt()) }
            visibility = View.GONE
            setOnClickListener { redoLast() }
        }
        // 셔터(74dp, 세로중앙) 바로 아래. 세로중앙 기준 + 아래로 오프셋.
        outer.addView(redoBtn, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = dp(24); topMargin = dp(104) })

        setContentView(outer)
        updateLearnBadge()
        setSignal(SIG_IDLE)
        syncSkills()   // 앱 진입 시 카탈로그 자동 동기화
    }

    private var engineRetry = 0
    @Volatile private var engineStarting = false

    /** 카메라 세션+인코더를 열어 연속 프리뷰(녹화 내내 유지). 한 번만. 실패 시(직전 화면 카메라 해제 중) 재시도. */
    private fun startEngine() {
        if (engineReady || engineStarting) return
        if (!preview.holder.surface.isValid) return
        engineStarting = true
        setSignal(SIG_BUSY)
        val surface = preview.holder.surface
        worker.execute {
            val enc = try { VideoEncoder() }
            catch (e: Exception) { engineStarting = false; ui.post { sigText.text = "인코더 실패: ${e.message}" }; return@execute }
            val cam = try { CameraCapture(this) }
            catch (e: Exception) { enc.release(); engineStarting = false; ui.post { sigText.text = "카메라 사용 불가: ${e.message}" }; return@execute }
            camera = cam; encoder = enc
            cam.open(surface, enc.cameraSurface, object : CameraCapture.Listener {
                override fun ready() { ui.post { engineStarting = false; engineReady = true; engineRetry = 0; if (!recording && !busy) setSignal(SIG_IDLE) } }
                override fun failed(reason: String) {
                    ui.post {
                        engineStarting = false; stopEngine()
                        // 직전 화면(온보딩 QR)의 카메라가 아직 해제 중이면 잠시 후 재시도.
                        if (!recording && !busy && engineRetry < 5) { engineRetry++; ui.postDelayed({ startEngine() }, 700) }
                        else sigText.text = "카메라 실패: $reason"
                    }
                }
            })
        }
    }

    private fun stopEngine() {
        engineReady = false; engineStarting = false
        val cam = camera; val enc = encoder
        worker.execute {
            try { cam?.close() } catch (_: Exception) {}
            try { enc?.release() } catch (_: Exception) {}
            camera = null; encoder = null
        }
    }

    private fun setSignal(state: Int) {
        val (color, text) = when (state) {
            SIG_REC -> 0xFFE0685E.toInt() to "녹화 중"                 // 빨강
            SIG_BUSY -> 0xFFE0A44E.toInt() to "카메라 준비 중…"        // 주황
            else -> 0xFF3FBF87.toInt() to "촬영 가능 — 동작 준비 완료"   // 초록(대기 = 준비됨)
        }
        sigDot.setTextColor(color); sigText.text = text
    }

    private fun updateLearnBadge() {
        val name = Prefs.activeSkillName(this)
        val n = Episodes.count(root, Prefs.activeSkillKey(this))
        learnBadge.text = "학습: $name · $n/${Episodes.GOAL}편 ▾"
    }

    /** 우상단 메뉴 — 다크 커스텀 팝업(기본 PopupMenu 대신). */
    private fun showMenu() {
        if (recording) return
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_menu_dark); setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        val pop = PopupWindow(container, dp(180), LinearLayout.LayoutParams.WRAP_CONTENT, true)
            .apply { isOutsideTouchable = true; elevation = dp(8).toFloat() }
        fun row(label: String, icon: Int, target: Class<*>) = TextView(this).apply {
            text = label; setTextColor(0xFFFFFFFF.toInt()); textSize = 17f
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            compoundDrawablePadding = dp(14); setPadding(dp(16), dp(15), dp(16), dp(15))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                if (!busy && !recording) startActivity(Intent(this@MainActivity, target))
                pop.dismiss()
            }
        }
        container.addView(row("기록", R.drawable.ic_history, HistoryActivity::class.java))
        container.addView(row("설정", R.drawable.ic_tune, SettingsActivity::class.java))
        pop.showAsDropDown(menuBtn)
    }

    private data class CardRow(val label: String, val sub: String, val active: Boolean, val onClick: () -> Unit)

    /** 다크 카드 시트 — 학습 선택 등 공용. 지정 높이(화면 90%) 초과 시 내부 스크롤. */
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
            if (r.sub.isNotEmpty()) row.addView(TextView(this).apply {
                text = r.sub; setTextColor(0xFFB8C0D8.toInt()); textSize = 13f; setPadding(0, 0, dp(10), 0)
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
        // 크기 고정 + 초과 시 스크롤: 추정 높이를 화면 90%로 상한.
        val maxH = (resources.displayMetrics.heightPixels * 0.9f).toInt()
        val approx = dp(80) + rows.size * dp(74)
        dialog.window?.setLayout(dp(360), minOf(approx, maxH))
        dialog.show()
    }

    private fun pickLearning() {
        if (recording) return
        DeviceSkills.load(this)
        val activeKey = Prefs.activeSkillKey(this)
        val rows = ArrayList<CardRow>()
        // 실제 학습 먼저, 미분류는 맨 아래.
        for (s in DeviceSkills.cached) {
            rows.add(CardRow(s.displayName, "${Episodes.count(root, s.skillKey)}/${Episodes.GOAL}편",
                s.skillKey == activeKey && s.skillKey.isNotBlank()) {
                Prefs.setActiveSkill(this, s.skillKey, s.displayName); updateLearnBadge()
            })
        }
        rows.add(CardRow("미분류 (나중에 지정)", "${Episodes.count(root, "")}/${Episodes.GOAL}편", activeKey.isBlank()) {
            Prefs.setActiveSkill(this, "", "미분류"); updateLearnBadge()
        })
        cardSheet("학습 선택", rows)
        syncSkills()   // 열 때 백그라운드로 카탈로그 최신화(다음 열람 반영)
    }

    /** 스킬 카탈로그 백그라운드 동기화 후 배지 갱신. */
    private fun syncSkills() {
        worker.execute { DeviceSkills.refresh(this); ui.post { updateLearnBadge() } }
    }

    private fun begin() {
        if (busy || recording) return
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 1); return
        }
        val cam = camera; val enc = encoder
        if (!engineReady || cam == null || enc == null) { sigText.text = "카메라 준비 중 — 잠시 후 다시"; return }
        if (root.usableSpace < 300L * 1024 * 1024) { sigText.text = "저장 공간 300MB 이상 필요"; return }
        busy = true
        shutter.isEnabled = false
        lastDir = null; redoBtn.visibility = View.GONE
        setSignal(SIG_BUSY)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        worker.execute {
            try {
                // 카메라 세션은 이미 열려 있음 → 녹화는 파일만 시작(재구성 없음, 멈춤 없음).
                val rec = Recording(root, cam.metadata, Prefs.activeSkillKey(this))
                val im = ImuCapture(this).also { it.start(rec) }
                enc.startFile(rec)
                cam.rec = rec               // frames.csv 기록 시작
                current = rec; imu = im
                ui.post {
                    busy = false; recording = true
                    shutter.isEnabled = true
                    shutter.setBackgroundResource(R.drawable.bg_shutter_stop)
                    recTimer.visibility = View.VISIBLE
                    setSignal(SIG_REC)
                    started = SystemClock.elapsedRealtime()
                    ticker.run()
                }
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
            camera?.rec = null                                        // frames.csv 중단
            try { encoder?.stopFile() } catch (e: Exception) { err = e.toString() }   // muxer 마무리(세션은 유지)
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
        setSignal(SIG_IDLE)   // 카메라 세션은 계속 살아있음 → 프리뷰 그대로, 멈춤 없음
        if (err != null) { sigText.text = "오류: $err"; lastDir = null; redoBtn.visibility = View.GONE; return }
        // 기본 성공. 망했으면 ↻ 재촬영으로 삭제 후 다시.
        rec?.let { r -> worker.execute { try { r.outcome("success") } catch (_: Exception) {} } }
        lastDir = rec?.dir
        android.widget.Toast.makeText(this, "저장됨 · 성공", android.widget.Toast.LENGTH_SHORT).show()
        redoBtn.visibility = if (lastDir != null) View.VISIBLE else View.GONE
    }

    private fun redoLast() {
        val d = lastDir ?: return
        lastDir = null; redoBtn.visibility = View.GONE
        worker.execute { try { d.deleteRecursively() } catch (_: Exception) {} }
        setSignal(SIG_IDLE); sigText.text = "재촬영 — 셔터를 누르세요"
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<String>, g: IntArray) {
        super.onRequestPermissionsResult(rc, p, g)
    }

    override fun onResume() { super.onResume(); startEngine(); if (::learnBadge.isInitialized) updateLearnBadge() }

    override fun onPause() {
        if (recording || busy) finishRecording("앱이 백그라운드로 전환됨")
        stopEngine()
        super.onPause()
    }

    companion object { private const val SIG_IDLE = 0; private const val SIG_BUSY = 1; private const val SIG_REC = 2 }
}
