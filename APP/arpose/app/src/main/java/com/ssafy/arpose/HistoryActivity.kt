package com.ssafy.arpose

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * 기록/검토 화면. 지난 촬영을 목록으로 보여주고 summary.txt 를 사람 말로 번역한다.
 * (녹화 판정을 Toast 로 놓치던 문제 해결 — S15P21A103-146)
 */
class HistoryActivity : Activity() {

    private val selected = HashSet<String>()   // 선택된 rec 폴더명

    // BE(SKILL-01/TRAIN-02) 연동 전 스텁 학습 목록 (촬영 화면과 동일).
    private val stubCatalog = listOf(
        Triple("job_pick_place", "pick_place", "집기·놓기"),
        Triple("job_sort_two", "sort_two", "둘 분류"),
        Triple("job_align_fixture", "align_fixture", "지그 정렬"),
        Triple("job_present_inspect", "present_inspect", "검사 제시"),
        Triple("job_line_up", "line_up", "줄 세우기")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        findViewById<Button>(R.id.backBtn).setOnClickListener { finish() }
        findViewById<Button>(R.id.sendBtn).setOnClickListener { sendSelected() }
        populate()
    }

    /** 선택 → 목적지(학습) 확인 → .queued 마킹 후 업로드 큐. 실제 전송은 UploadWorker(3-step). */
    private fun sendSelected() {
        if (selected.isEmpty()) { Toast.makeText(this, "전송할 촬영을 선택하세요", Toast.LENGTH_SHORT).show(); return }
        val names = stubCatalog.map { it.third }.toTypedArray()
        val activeName = Prefs.activeSkillName(this)
        AlertDialog.Builder(this)
            .setTitle("${selected.size}편 전송 → 학습 선택" + if (activeName.isNotBlank()) " (현재: $activeName)" else "")
            .setItems(names) { _, i ->
                val (jobId, _, _) = stubCatalog[i]
                val root = getExternalFilesDir(null)
                selected.forEach { name ->
                    val dir = File(root, name)
                    if (dir.isDirectory) {
                        File(dir, ".upload_job").writeText(jobId)   // 목적지 job (meta training_job_id 덮어씀)
                        File(dir, ".queued").writeText("1")          // 전송 대기 표시
                    }
                }
                UploadQueue.enqueue(this)
                Toast.makeText(this, "${selected.size}편 전송 대기 → ${stubCatalog[i].third}", Toast.LENGTH_SHORT).show()
                selected.clear()
                populate()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /** 전송 상태: 완료(.uploaded) / 대기(.queued) / 미전송. */
    private fun uploadStatus(dir: File): Triple<String, Int, Boolean> = when {
        File(dir, ".uploaded").exists() -> Triple("완료", 0xFF3FBF87.toInt(), false)
        File(dir, ".queued").exists() -> Triple("대기", 0xFFE0A44E.toInt(), false)
        else -> Triple("미전송", 0xFF9AA2B8.toInt(), true)
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

        val check = CheckBox(this).apply {
            isChecked = selected.contains(dir.name)
            setPadding(0, 0, 12, 0)
            setOnCheckedChangeListener { _, c -> if (c) selected.add(dir.name) else selected.remove(dir.name) }
        }

        val statusDot = TextView(this).apply {
            text = icon
            setTextColor(color)
            textSize = 22f
            setPadding(0, 0, 20, 0)
        }

        val (stText, stColor, _) = uploadStatus(dir)
        val sendBadge = TextView(this).apply {
            text = stText
            setTextColor(stColor)
            textSize = 13f
            setPadding(0, 0, 16, 0)
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

        card.addView(check)
        card.addView(statusDot)
        card.addView(info)
        card.addView(sendBadge)
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
