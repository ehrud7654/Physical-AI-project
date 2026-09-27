package com.ssafy.arpose

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** 설정 화면. 토글로 촬영 옵션을 Prefs 에 저장한다. 촬영 화면이 복귀 시 읽어 적용. */
class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener { finish() }

        // 초점: 체크 = 고정(FIXED). Prefs.afAuto 는 AUTO 여부이므로 반전.
        val afSwitch = findViewById<Switch>(R.id.afSwitch)
        afSwitch.isChecked = !Prefs.afAuto(this)
        afSwitch.setOnCheckedChangeListener { _, checked -> Prefs.setAfAuto(this, !checked) }

        val aeSwitch = findViewById<Switch>(R.id.aeSwitch)
        aeSwitch.isChecked = Prefs.aeLock(this)
        aeSwitch.setOnCheckedChangeListener { _, checked -> Prefs.setAeLock(this, checked) }

        // 디버그 섹션: 디버그 빌드에서만. QR 연동 전 jobId·자격 직접 입력해 실서버 테스트.
        if (BuildConfig.DEBUG) {
            findViewById<LinearLayout>(R.id.debugSection).visibility = View.VISIBLE
            val dbgSwitch = findViewById<Switch>(R.id.debugSwitch)
            val jobIn = findViewById<EditText>(R.id.jobIdInput)
            val devIn = findViewById<EditText>(R.id.deviceIdInput)
            val secIn = findViewById<EditText>(R.id.secretInput)
            dbgSwitch.isChecked = Prefs.debugEnabled(this)
            jobIn.setText(Prefs.debugJobId(this))
            devIn.setText(Prefs.deviceId(this))
            secIn.setText(Prefs.deviceSecret(this))
            findViewById<Button>(R.id.saveDebugBtn).setOnClickListener {
                Prefs.setDebug(this, dbgSwitch.isChecked, jobIn.text.toString(),
                    devIn.text.toString(), secIn.text.toString())
                Toast.makeText(this, "디버그 설정 저장됨", Toast.LENGTH_SHORT).show()
            }

            // 초점 거리 슬라이더 (FIXED 일 때 적용). 촬영화면 복귀 시 반영.
            val focusSeek = findViewById<SeekBar>(R.id.focusSeek)
            val focusLabel = findViewById<TextView>(R.id.focusLabel)
            fun fmtFocus(d: Float) = if (d < 0.2f) "초점 거리: 무한대"
                else "초점 거리: ${(100f / d).toInt()}cm (${"%.1f".format(d)} 디옵터)"
            focusSeek.progress = (Prefs.focusDiopters(this) * 10).toInt()
            focusLabel.text = fmtFocus(Prefs.focusDiopters(this))
            focusSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    val d = p / 10f
                    focusLabel.text = fmtFocus(d)
                    Prefs.setFocusDiopters(this@SettingsActivity, d)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
    }
}
