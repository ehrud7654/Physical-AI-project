package com.ssafy.arpose

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button

/**
 * 초기화면. QR 인식으로 세션·스킬을 연동할 자리(예정).
 * 지금은 하단 "넘어가기"로 바로 촬영 화면(MainActivity)으로 간다.
 */
class OnboardingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        findViewById<Button>(R.id.skipBtn).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
