package com.a103.collect

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * 설정. 장치 등록 상태, 스킬(학습) 카탈로그 동기화·추가, (디버그) 장치 자격 입력.
 * 스킬 카탈로그가 있어야 기록에서 학습 지정·전송이 된다.
 */
class SettingsActivity : Activity() {

    private lateinit var skillCountView: TextView
    private lateinit var deviceView: TextView
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceSkills.load(this)
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFF3F4F7.toInt()) }

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(14), dp(16), dp(12)) }
        bar.addView(TextView(this).apply { text = "‹"; textSize = 30f; setTextColor(0xFF1B2233.toInt()); setPadding(dp(8), 0, dp(12), dp(4)); setOnClickListener { finish() } })
        bar.addView(TextView(this).apply { text = "설정"; textSize = 22f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(0xFF1B2233.toInt()) })
        outer.addView(bar)

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(20)) }

        // 장치
        body.addView(sectionTitle("장치"))
        deviceView = valueText(deviceStatus())
        body.addView(card(deviceView))

        // 학습 카탈로그
        body.addView(sectionTitle("학습(스킬) 카탈로그"))
        skillCountView = valueText(skillStatus())
        val skillCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bg_card_light); setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
            addView(skillCountView)
            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0)
                addView(actionChip("서버 동기화") { syncSkills() })
                addView(actionChip("학습 추가") { addSkillDialog() }.apply { (layoutParams as LinearLayout.LayoutParams).marginStart = dp(8) })
            })
        }
        body.addView(skillCard)

        // 디버그
        if (BuildConfig.DEBUG) {
            body.addView(sectionTitle("디버그"))
            body.addView(card(valueText("서버: ${BuildConfig.UPLOAD_BASE_URL}")))
            val idIn = EditText(this).apply { hint = "deviceId"; setText(Prefs.deviceId(this@SettingsActivity)); textSize = 14f }
            val secIn = EditText(this).apply { hint = "deviceSecret"; setText(Prefs.deviceSecret(this@SettingsActivity)); textSize = 14f }
            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bg_card_light); setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
                addView(TextView(this@SettingsActivity).apply { text = "장치 자격(테스트 enroll 대체)"; setTextColor(0xFF6B7280.toInt()); textSize = 13f; setPadding(0, 0, 0, dp(6)) })
                addView(idIn); addView(secIn)
                addView(actionChip("자격 저장") {
                    Prefs.setDeviceCreds(this@SettingsActivity, idIn.text.toString().trim(), secIn.text.toString().trim())
                    deviceView.text = deviceStatus(); Toast.makeText(this@SettingsActivity, "저장됨", Toast.LENGTH_SHORT).show()
                }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(10) })
            })
        }

        outer.addView(ScrollView(this).apply { addView(body) })
        setContentView(outer)
    }

    private fun deviceStatus(): String {
        val id = Prefs.deviceId(this)
        return if (id.isBlank()) "미등록 — QR 등록 또는 디버그 자격 입력" else "등록됨 · ${id.take(12)}…"
    }

    private fun skillStatus(): String {
        val n = DeviceSkills.cached.size
        return if (n == 0) "카탈로그 없음 — 동기화하거나 추가하세요" else "$n 개: " + DeviceSkills.cached.joinToString(", ") { it.displayName }
    }

    private fun syncSkills() {
        Toast.makeText(this, "동기화 중…", Toast.LENGTH_SHORT).show()
        Thread {
            DeviceSkills.refresh(this)
            runOnUiThread { skillCountView.text = skillStatus() }
        }.start()
    }

    private fun addSkillDialog() {
        val dialog = Dialog(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bg_dialog_light); setPadding(dp(22), dp(20), dp(22), dp(16))
        }
        box.addView(TextView(this).apply { text = "학습 추가 (테스트)"; setTextColor(0xFF1A1F2E.toInt()); textSize = 18f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
        val keyIn = EditText(this).apply { hint = "skillKey (예: pick_cup)"; textSize = 14f }
        val nameIn = EditText(this).apply { hint = "표시명 (예: 컵 집기)"; textSize = 14f }
        box.addView(keyIn); box.addView(nameIn)
        val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(0, dp(14), 0, 0) }
        btns.addView(TextView(this).apply { text = "취소"; setTextColor(0xFF6B7280.toInt()); textSize = 15f; setPadding(dp(14), dp(8), dp(14), dp(8)); setOnClickListener { dialog.dismiss() } })
        btns.addView(TextView(this).apply {
            text = "추가"; setTextColor(0xFFFFFFFF.toInt()); textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_btn_azure); setPadding(dp(20), dp(9), dp(20), dp(9))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(10) }
            setOnClickListener {
                val k = keyIn.text.toString().trim(); val n = nameIn.text.toString().trim().ifBlank { k }
                if (k.isNotBlank()) { DeviceSkills.addLocal(this@SettingsActivity, k, n); skillCountView.text = skillStatus() }
                dialog.dismiss()
            }
        })
        box.addView(btns)
        dialog.setContentView(box)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(dp(340), LinearLayout.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    private fun sectionTitle(t: String) = TextView(this).apply {
        text = t; setTextColor(0xFF8A909E.toInt()); textSize = 13f; setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(dp(4), dp(16), dp(4), dp(8))
    }
    private fun valueText(t: String) = TextView(this).apply { text = t; setTextColor(0xFF1B2233.toInt()); textSize = 15f }
    private fun card(v: android.view.View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bg_card_light); setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        addView(v)
    }
    private fun actionChip(t: String, onClick: () -> Unit) = TextView(this).apply {
        text = t; setTextColor(0xFF4F9CF9.toInt()); textSize = 14f; setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(dp(16), dp(9), dp(16), dp(9))
        background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(0x1A4F9CF9) }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        setOnClickListener { onClick() }
    }
}
