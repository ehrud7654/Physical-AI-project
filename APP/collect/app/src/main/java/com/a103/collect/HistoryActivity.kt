package com.a103.collect

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.File

/** 저장된 세션 목록. 학습·결과·상태·길이 표시 + 개별 삭제. 최신순. */
class HistoryActivity : Activity() {

    private lateinit var root: File
    private lateinit var list: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = File(getExternalFilesDir(null), "sessions").apply { mkdirs() }

        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFF3F4F7.toInt()) }
        outer.addView(TextView(this).apply {
            text = "기록"; textSize = 22f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(0xFF1B2233.toInt()); setPadding(dp(20), dp(18), dp(20), dp(12))
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(14), dp(20)) }
        outer.addView(ScrollView(this).apply { addView(list) })
        setContentView(outer)
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        list.removeAllViews()
        val dirs = root.listFiles { f -> f.isDirectory && f.name.startsWith("rec_") }
            ?.sortedByDescending { it.name } ?: emptyList()
        if (dirs.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "촬영 기록이 없습니다"; setTextColor(0xFF8A909E.toInt()); textSize = 15f; setPadding(dp(8), dp(24), dp(8), 0)
            })
            return
        }
        dirs.forEach { list.addView(card(it)) }
    }

    private fun card(dir: File): View {
        val m = runCatching { JSONObject(File(dir, "manifest.json").readText()) }.getOrNull()
        val skill = m?.optString("skill_key").orEmpty().ifBlank { "미분류" }
        val status = m?.optString("status") ?: "?"
        val (outLabel, outColor) = when (m?.optString("outcome")) {
            "success" -> "성공" to 0xFF3FBF87.toInt()
            "failure" -> "실패" to 0xFFE0685E.toInt()
            else -> "미판정" to 0xFF8A909E.toInt()
        }
        val dur = run {
            val s = m?.optLong("start_elapsed_ns", 0L) ?: 0L
            val e = m?.optLong("stop_elapsed_ns", 0L) ?: 0L
            if (e > s && s > 0) (e - s) / 1_000_000_000.0 else 0.0
        }
        val timeMs = dir.name.removePrefix("rec_").substringBefore("_").toLongOrNull() ?: 0L

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) }

            val top = LinearLayout(this@HistoryActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(TextView(this@HistoryActivity).apply {
                text = if (timeMs > 0) DateFormat.format("MM/dd HH:mm", timeMs) else dir.name
                setTextColor(0xFF1B2233.toInt()); textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            top.addView(TextView(this@HistoryActivity).apply {
                text = outLabel; setTextColor(outColor); textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(top)
            addView(TextView(this@HistoryActivity).apply {
                val len = "%d:%02d".format((dur.toInt()) / 60, dur.toInt() % 60)
                text = "학습 $skill · $status · $len"
                setTextColor(0xFF6B7280.toInt()); textSize = 13f; setPadding(0, dp(6), 0, 0)
            })
            addView(TextView(this@HistoryActivity).apply {
                text = "삭제"; setTextColor(0xFFE0685E.toInt()); textSize = 13f
                setPadding(0, dp(8), 0, 0); gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener { confirmDelete(dir) }
            })
        }
    }

    private fun confirmDelete(dir: File) {
        AlertDialog.Builder(this).setTitle("삭제")
            .setMessage("이 세션을 삭제할까요?\n${dir.name}")
            .setPositiveButton("삭제") { _, _ -> dir.deleteRecursively(); render() }
            .setNegativeButton("취소", null).show()
    }
}
