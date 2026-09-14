package com.ssafy.arpose

import android.app.Activity
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Switch

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

        val gridSwitch = findViewById<Switch>(R.id.gridSwitch)
        gridSwitch.isChecked = Prefs.gridGuide(this)
        gridSwitch.setOnCheckedChangeListener { _, checked -> Prefs.setGridGuide(this, checked) }

        val detailSwitch = findViewById<Switch>(R.id.detailSwitch)
        detailSwitch.isChecked = Prefs.showDetail(this)
        detailSwitch.setOnCheckedChangeListener { _, checked -> Prefs.setShowDetail(this, checked) }
    }
}
