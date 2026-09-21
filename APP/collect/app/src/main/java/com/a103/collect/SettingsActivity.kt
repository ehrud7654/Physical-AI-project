package com.a103.collect

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/**
 * 설정 — 장치 등록 상태, 수집 현황(학습별 N/GOAL편·총 세션·저장공간), 앱 정보.
 * 스킬 카탈로그는 진입 시 자동 동기화(버튼 없음).
 */
class SettingsActivity : Activity() {

    private lateinit var root: File
    private lateinit var body: LinearLayout
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = File(getExternalFilesDir(null), "sessions").apply { mkdirs() }
        DeviceSkills.load(this)

        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFF3F4F7.toInt()) }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(14), dp(16), dp(12)) }
        bar.addView(TextView(this).apply { text = "‹"; textSize = 30f; setTextColor(0xFF1B2233.toInt()); setPadding(dp(8), 0, dp(12), dp(4)); setOnClickListener { finish() } })
        bar.addView(TextView(this).apply { text = "설정"; textSize = 22f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(0xFF1B2233.toInt()) })
        outer.addView(bar)

        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(20)) }
        outer.addView(ScrollView(this).apply { addView(body) })
        setContentView(outer)

        render()
        // 진입 시 카탈로그 자동 동기화 후 현황 갱신.
        Thread { DeviceSkills.refresh(this); runOnUiThread { render() } }.start()
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        body.removeAllViews()

        body.addView(sectionTitle("장치"))
        val id = Prefs.deviceId(this)
        body.addView(card(valueText(if (id.isBlank()) "미등록 — QR 등록 필요" else "등록됨 · ${id.take(12)}…")))

        body.addView(sectionTitle("수집 현황"))
        val progress = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bg_card_light); setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        }
        val skills = DeviceSkills.cached
        if (skills.isEmpty()) progress.addView(valueText("학습 목록 없음 — 온라인 연결 시 자동 동기화"))
        else skills.forEach { s -> progress.addView(progressRow(s.displayName, Episodes.count(root, s.skillKey))) }
        progress.addView(progressRow("미분류", Episodes.count(root, "")))
        body.addView(progress)

        val total = root.listFiles { f -> f.isDirectory && f.name.startsWith("rec_") }?.size ?: 0
        val freeGb = root.usableSpace / (1024.0 * 1024 * 1024)
        body.addView(card(valueText("총 세션 %d편 · 남은 저장 %.1f GB".format(total, freeGb))))

        body.addView(sectionTitle("정보"))
        val host = BuildConfig.UPLOAD_BASE_URL.substringAfter("://")
        body.addView(card(valueText("앱 v${BuildConfig_versionName()} · 서버 $host")))
    }

    private fun progressRow(name: String, n: Int): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
        row.addView(TextView(this).apply {
            text = name; setTextColor(0xFF1B2233.toInt()); textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val done = n >= Episodes.GOAL
        row.addView(TextView(this).apply {
            text = "$n/${Episodes.GOAL}편"; textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(if (done) 0xFF3FBF87.toInt() else 0xFF4F9CF9.toInt())
        })
        return row
    }

    private fun BuildConfig_versionName() = "0.1"

    private fun sectionTitle(t: String) = TextView(this).apply {
        text = t; setTextColor(0xFF8A909E.toInt()); textSize = 13f; setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(dp(4), dp(16), dp(4), dp(8))
    }
    private fun valueText(t: String) = TextView(this).apply { text = t; setTextColor(0xFF1B2233.toInt()); textSize = 15f }
    private fun card(v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bg_card_light); setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        addView(v)
    }
}
