package com.ssafy.arpose

import android.app.Activity
import android.os.Bundle
import android.widget.Button

/** 기록/검토 화면. 지금은 껍데기 — 지난 촬영 목록·판정은 S15P21A103-146 에서. */
class HistoryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        findViewById<Button>(R.id.backBtn).setOnClickListener { finish() }
    }
}
