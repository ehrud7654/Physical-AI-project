package com.ssafy.arpose

import android.app.Activity
import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * 기록/검토 화면. 지난 촬영을 라이트 카드로 보여주고 summary.txt 를 사람 말로 번역한다.
 * 카드 본문 탭 = 상세 펼치기, 오른쪽 체크 = 선택. 20편 단위 페이지.
 */
class HistoryActivity : Activity() {

    private val selected = HashSet<String>()   // 선택된 rec 폴더명
    private val expanded = HashSet<String>()   // 펼쳐진 rec 폴더명
    private var page = 0

    companion object { private const val PAGE_SIZE = 20 }

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
        findViewById<ImageButton>(R.id.backBtn).setOnClickListener { finish() }
        findViewById<Button>(R.id.sendBtn).setOnClickListener { sendSelected() }
        findViewById<TextView>(R.id.pagePrev).setOnClickListener { if (page > 0) { page--; populate(); scrollTop() } }
        findViewById<TextView>(R.id.pageNext).setOnClickListener { page++; populate(); scrollTop() }
        populate()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** 페이지 넘기면 항상 최상단부터 보이게. */
    private fun scrollTop() {
        val sv = findViewById<ScrollView>(R.id.historyScroll)
        sv.post { sv.scrollTo(0, 0) }
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
        else -> Triple("미전송", 0xFF8A909E.toInt(), true)
    }

    private fun updateSendBtn() {
        val btn = findViewById<Button>(R.id.sendBtn)
        if (selected.isEmpty()) {
            btn.text = "전송"
            btn.setBackgroundResource(R.drawable.bg_btn_azure_disabled)
            btn.isEnabled = false
        } else {
            btn.text = "${selected.size}편 전송"
            btn.setBackgroundResource(R.drawable.bg_btn_azure)
            btn.isEnabled = true
        }
    }

    private fun populate() {
        val list = findViewById<LinearLayout>(R.id.historyList)
        val empty = findViewById<TextView>(R.id.emptyText)
        val pager = findViewById<LinearLayout>(R.id.pagerRow)
        list.removeAllViews()

        val root = getExternalFilesDir(null)
        val recs = root?.listFiles { f -> f.isDirectory && f.name.startsWith("rec_") }
            ?.sortedByDescending { it.name } ?: emptyList()

        if (recs.isEmpty()) {
            empty.visibility = View.VISIBLE
            pager.visibility = View.GONE
            updateSendBtn()
            return
        }
        empty.visibility = View.GONE

        val totalPages = (recs.size + PAGE_SIZE - 1) / PAGE_SIZE
        if (page >= totalPages) page = totalPages - 1
        if (page < 0) page = 0
        val from = page * PAGE_SIZE
        val slice = recs.subList(from, minOf(from + PAGE_SIZE, recs.size))
        for (dir in slice) list.addView(cardFor(dir, list))

        // 페이저
        if (totalPages <= 1) {
            pager.visibility = View.GONE
        } else {
            pager.visibility = View.VISIBLE
            findViewById<TextView>(R.id.pageLabel).text = "${page + 1} / $totalPages"
            findViewById<TextView>(R.id.pagePrev).apply { isEnabled = page > 0; alpha = if (page > 0) 1f else 0.3f }
            findViewById<TextView>(R.id.pageNext).apply { isEnabled = page < totalPages - 1; alpha = if (page < totalPages - 1) 1f else 0.3f }
        }
        updateSendBtn()
    }

    /** 사용가능 판정 → 짧은 라벨 + 색. mkRate<50 은 최우선 재촬영. */
    private fun usableOf(icon: String): Pair<String, Int> = when (icon) {
        "✓" -> "사용 가능" to 0xFF3FBF87.toInt()
        "⚠" -> "확인 필요" to 0xFFE0A44E.toInt()
        "✗" -> "재촬영" to 0xFFE0685E.toInt()
        else -> "미완료" to 0xFF8A909E.toInt()
    }

    /** rec 폴더 하나를 라이트 카드 한 장으로. */
    private fun cardFor(dir: File, parent: LinearLayout): View {
        val name = dir.name
        val m = parseSummary(File(dir, "summary.txt"))
        val mkRate = markerRate(dir)
        val (icon, _, vLabel) = if (mkRate != null && mkRate < 50)
            Triple("✗", 0xFFE0685E.toInt(), "마커 거의 안 잡힘 ($mkRate%) — 재촬영")
        else verdictOf(m)
        val (usableLabel, usableColor) = usableOf(icon)
        val (stText, stColor, _) = uploadStatus(dir)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(if (selected.contains(name)) R.drawable.bg_card_selected else R.drawable.bg_card_light)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        // ---- 헤더 (탭 = 펼치기) ----
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val time = TextView(this).apply {
            text = prettyName(name)
            setTextColor(0xFF1A1F2E.toInt())
            textSize = 16f
        }
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        val usable = TextView(this).apply {
            text = usableLabel
            setTextColor(usableColor)
            textSize = 13f
            setPadding(0, 0, dp(10), 0)
        }
        val pill = TextView(this).apply {
            text = stText
            setTextColor(stColor)
            textSize = 12f
            setPadding(dp(10), dp(3), dp(10), dp(3))
            background = GradientDrawable().apply {
                cornerRadius = dp(999).toFloat()
                setColor((stColor and 0x00FFFFFF) or 0x1A000000)   // 옅은 배경
            }
        }
        statusRow.addView(usable); statusRow.addView(pill)
        left.addView(time); left.addView(statusRow)

        // 선택 체크 어포던스 (오른쪽) — 탭 = 선택 토글
        val check = TextView(this).apply {
            text = if (selected.contains(name)) "☑" else "☐"
            setTextColor(if (selected.contains(name)) 0xFF4F9CF9.toInt() else 0xFF8A909E.toInt())
            textSize = 24f
            setPadding(dp(10), dp(4), dp(4), dp(4))
            setOnClickListener {
                if (selected.contains(name)) selected.remove(name) else selected.add(name)
                populate()
            }
        }
        header.addView(left); header.addView(check)
        card.addView(header)

        // ---- 상세 (펼쳐졌을 때만) ----
        val detail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
            visibility = if (expanded.contains(name)) View.VISIBLE else View.GONE
        }
        val dur = if (m.isEmpty()) "요약 없음 (녹화 미완료 가능)"
            else "${m["duration_sec"] ?: "?"}s · ${m["frames_saved"] ?: "?"}프레임"
        detail.addView(detailRow("길이", dur))
        detail.addView(detailRow("마커 검출", mkRate?.let { "$it%" } ?: "—"))
        detail.addView(detailRow("추적", vLabel))
        detail.addView(detailRow("전송", stText))

        val del = TextView(this).apply {
            text = "삭제"
            setTextColor(0xFFE0685E.toInt())
            textSize = 14f
            setPadding(0, dp(10), 0, dp(2))
            setOnClickListener {
                AlertDialog.Builder(this@HistoryActivity)
                    .setMessage("${prettyName(name)} 삭제할까요?")
                    .setPositiveButton("삭제") { _, _ ->
                        dir.deleteRecursively()
                        selected.remove(name); expanded.remove(name)
                        populate()
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
        }
        detail.addView(del)
        card.addView(detail)

        header.setOnClickListener {
            if (expanded.contains(name)) expanded.remove(name) else expanded.add(name)
            detail.visibility = if (expanded.contains(name)) View.VISIBLE else View.GONE
        }
        return card
    }

    private fun detailRow(label: String, value: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(3), 0, dp(3))
        }
        row.addView(TextView(this).apply {
            text = label
            setTextColor(0xFF8A909E.toInt())
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(dp(88), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        row.addView(TextView(this).apply {
            text = value
            setTextColor(0xFF6B7280.toInt())
            textSize = 14f
        })
        return row
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
        if (m.isEmpty()) return Triple("?", 0xFF8A909E.toInt(), "미완료")
        val holes = (m["frames_write_failed"]?.toIntOrNull() ?: 0) > 0 ||
            (m["pose_rows_failed"]?.toIntOrNull() ?: 0) > 0 ||
            m.keys.any { it.startsWith("WARNING") } || m.containsKey("WARNING")
        val jumps = m["tracking_jumps"]?.toIntOrNull() ?: 0
        val paused = m["paused_frames"]?.toIntOrNull() ?: 0
        return when {
            holes -> Triple("✗", 0xFFE0685E.toInt(), "못 씀 (데이터 구멍)")
            jumps > 0 || paused > 0 -> Triple("⚠", 0xFFE0A44E.toInt(), "점프/끊김 — 구간 분할 사용")
            else -> Triple("✓", 0xFF3FBF87.toInt(), "좋음 — 전체 사용 가능")
        }
    }
}
