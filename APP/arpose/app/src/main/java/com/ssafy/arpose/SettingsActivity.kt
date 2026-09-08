package com.ssafy.arpose

import android.app.Activity
import android.os.Bundle
import android.widget.Button

/** 설정 화면. 지금은 껍데기 — AE/AF/해상도 이동은 S15P21A103-145 에서. */
class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<Button>(R.id.backBtn).setOnClickListener { finish() }
    }
}
