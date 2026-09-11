package com.ssafy.arpose

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

/**
 * 기록/검토 화면. 지난 촬영을 목록으로 보여주고 summary.txt 를 사람 말로 번역한다.
 * (녹화 판정을 Toast 로 놓치던 문제 해결 — S15P21A103-146)
 */
class HistoryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        findViewById<Button>(R.id.backBtn).setOnClickListener { finish() }
        populate()
    }

    private fun populate() {
        val list = findViewById<LinearLayout>(R.id.historyList)
        val empty = findViewById<TextView>(R.id.emptyText)
        list.removeAllViews()

        val root = getExternalFilesDir(null)
        val recs = root?.listFiles { f -> f.isDirectory && f.name.startsWith("rec_") }
            ?.sortedByDescending { it.name } ?: emptyList()

        if (recs.isEmpty()) {
            empty.visibility = View.VISIBLE
            return
        }
        empty.visibility = View.GONE
        for (dir in recs) list.addView(rowFor(dir, list))
    }

    /** rec 폴더 하나를 카드 한 장으로. */
    private fun rowFor(dir: File, parent: LinearLayout): View {
        val m = parseSummary(File(dir, "summary.txt"))
        val mkRate = markerRate(dir)
        // 마커가 거의 안 잡히면(<50%) 최우선 '못 씀'. 아니면 추적/점프 기준 판정.
        val (icon, color, label) = if (mkRate != null && mkRate < 50)
            Triple("✗", 0xFFFF4444.toInt(), "마커 거의 안 잡힘 ($mkRate%) — 재촬영")
        else verdictOf(m)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(0xFF1C1C1C.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
        }

        val info = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            val title = "$icon  ${prettyName(dir.name)}   $label"
            val mkLine = mkRate?.let { " · 마커 $it%" } ?: ""
            val stat = if (m.isEmpty()) "요약 없음 (녹화 미완료 가능)" else
                "${m["duration_sec"] ?: "?"}s · ${m["frames_saved"] ?: "?"}프레임 · " +
                    "${m["rate_hz"] ?: "?"}Hz · 점프 ${m["tracking_jumps"] ?: "?"} · 드롭 ${m["frames_dropped"] ?: "?"}$mkLine"
            val seg = m["usable_segments"]?.let { "\n사용 가능: $it" } ?: ""
            text = "$title\n$stat$seg"
            setTextColor(0xFFDDDDDD.toInt())
            textSize = 14f
        }

        val statusDot = TextView(this).apply {
            text = icon
            setTextColor(color)
            textSize = 22f
            setPadding(0, 0, 20, 0)
        }

        val del = Button(this).apply {
            text = "삭제"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF662222.toInt())
            setOnClickListener {
                AlertDialog.Builder(this@HistoryActivity)
                    .setMessage("${prettyName(dir.name)} 삭제할까요?")
                    .setPositiveButton("삭제") { _, _ ->
                        dir.deleteRecursively()
                        populate()
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
        }

        card.addView(statusDot)
        card.addView(info)
        card.addView(del)
        return card
    }

    /** marker_qc.txt(detected/total) → 검출률(%). 없으면 null(옛 촬영·계산 전). */
    private fun markerRate(dir: File): Int? {
        val f = File(dir, "marker_qc.txt")
        if (!f.exists()) return null
        var d = 0; var t = 0
        f.readLines().forEach { line ->
            val i = line.indexOf('=')
            if (i > 0) {
                val v = line.substring(i + 1).trim().toIntOrNull() ?: return@forEach
                when (line.substring(0, i).trim()) { "detected" -> d = v; "total" -> t = v }
            }
        }
        return if (t > 0) d * 100 / t else null
    }

    private fun prettyName(name: String): String {
        // rec_20260907_172150 -> 09/07 17:21
        val m = Regex("rec_\\d{4}(\\d{2})(\\d{2})_(\\d{2})(\\d{2})").find(name) ?: return name
        val (mo, d, h, mi) = m.destructured
        return "$mo/$d $h:$mi"
    }

    private fun parseSummary(f: File): Map<String, String> {
        if (!f.exists()) return emptyMap()
        val map = HashMap<String, String>()
        var seg: String? = null
        var inSeg = false
        f.readLines().forEach { line ->
            when {
                line.startsWith("usable_segments") -> {
                    val i = line.indexOf('=')
                    if (i >= 0) seg = line.substring(i + 1).trim()   // 단일 줄: usable_segments=0.00~10.80
                    else inSeg = true                                 // 다음 줄부터 목록
                }
                inSeg && line.trim().matches(Regex("[0-9.]+~[0-9.]+")) ->
                    seg = (seg?.plus(", ") ?: "") + line.trim()
                line.contains("=") -> {
                    inSeg = false
                    val i = line.indexOf('=')
                    map[line.substring(0, i).trim()] = line.substring(i + 1).trim()
                }
            }
        }
        // usable_segments 가 한 줄(=0.00~10.80)로 온 경우도 처리
        seg?.let { map["usable_segments"] = it }
        map["usable_segments"]?.let { if (it.isBlank()) map.remove("usable_segments") }
        return map
    }

    /** 친화 판정: ✓ 좋음 / ⚠ 점프·끊김 / ✗ 못 씀 */
    private fun verdictOf(m: Map<String, String>): Triple<String, Int, String> {
        if (m.isEmpty()) return Triple("?", 0xFF888888.toInt(), "미완료")
        val holes = (m["frames_write_failed"]?.toIntOrNull() ?: 0) > 0 ||
            (m["pose_rows_failed"]?.toIntOrNull() ?: 0) > 0 ||
            m.keys.any { it.startsWith("WARNING") } || m.containsKey("WARNING")
        val jumps = m["tracking_jumps"]?.toIntOrNull() ?: 0
        val paused = m["paused_frames"]?.toIntOrNull() ?: 0
        return when {
            holes -> Triple("✗", 0xFFFF4444.toInt(), "못 씀 (데이터 구멍)")
            jumps > 0 || paused > 0 -> Triple("⚠", 0xFFFFCC44.toInt(), "점프/끊김 — 구간 분할 사용")
            else -> Triple("✓", 0xFF33DD55.toInt(), "좋음 — 전체 사용 가능")
        }
    }
}
