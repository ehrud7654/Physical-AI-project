package com.ssafy.arpose

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup

/** 설정 화면. AF/AE 모드를 골라 Prefs 에 저장한다. 촬영 화면이 복귀 시 읽어 적용. */
class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<Button>(R.id.backBtn).setOnClickListener { finish() }

        val afGroup = findViewById<RadioGroup>(R.id.afGroup)
        val aeGroup = findViewById<RadioGroup>(R.id.aeGroup)

        // 저장된 값으로 초기 선택
        afGroup.check(if (Prefs.afAuto(this)) R.id.afAuto else R.id.afFixed)
        aeGroup.check(if (Prefs.aeLock(this)) R.id.aeLock else R.id.aeAuto)

        afGroup.setOnCheckedChangeListener { _, id -> Prefs.setAfAuto(this, id == R.id.afAuto) }
        aeGroup.setOnCheckedChangeListener { _, id -> Prefs.setAeLock(this, id == R.id.aeLock) }
    }
}
