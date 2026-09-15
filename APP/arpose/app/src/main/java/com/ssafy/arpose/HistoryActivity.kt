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
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
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

    // 업로드 워커(arpose-upload) 상태를 관찰 → 전송 완료 시 목록을 바로 새로고침(대기→완료).
    // plain Activity 라 LifecycleOwner 가 없어 observeForever + onDestroy 해제.
    private val uploadWork by lazy {
        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData(UploadQueue.WORK_NAME)
    }
    private val uploadObserver = Observer<List<WorkInfo>> { populate() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        findViewById<ImageButton>(R.id.backBtn).setOnClickListener { finish() }
        findViewById<Button>(R.id.sendBtn).setOnClickListener { sendSelected() }
        findViewById<TextView>(R.id.selectAllBtn).setOnClickListener { toggleSelectAll() }
        findViewById<TextView>(R.id.deleteSelectedBtn).apply {
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(0xFFE0685E.toInt()) }
            setOnClickListener { confirmDeleteSelected() }
        }
        populate()
        uploadWork.observeForever(uploadObserver)   // 전송 완료되면 목록 자동 갱신
    }

    /** 모든 rec 폴더명. 전체 선택/해제용. */
    private fun allRecNames(): List<String> =
        getExternalFilesDir(null)?.listFiles { f -> f.isDirectory && f.name.startsWith("rec_") }
            ?.map { it.name } ?: emptyList()

    private fun toggleSelectAll() {
        val all = allRecNames()
        if (all.isEmpty()) return
        if (selected.containsAll(all)) selected.clear() else { selected.clear(); selected.addAll(all) }
        populate()
    }

    override fun onDestroy() {
        super.onDestroy()
        uploadWork.removeObserver(uploadObserver)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** 페이지 넘기면 항상 최상단부터 보이게. */
    private fun scrollTop() {
        val sv = findViewById<ScrollView>(R.id.historyScroll)
        sv.post { sv.scrollTo(0, 0) }
    }

    /** ‹ 1 2 3 4 5 › — 5페이지 블록 단위. 번호 클릭=이동, 화살표=앞뒤 페이지, 현재=azure 강조. */
    private fun buildPager(pager: LinearLayout, totalPages: Int) {
        pager.removeAllViews()
        if (totalPages <= 1) { pager.visibility = View.GONE; return }
        pager.visibility = View.VISIBLE
        fun arrow(txt: String, enabled: Boolean, onClick: () -> Unit) = TextView(this).apply {
            text = txt; textSize = 20f
            setTextColor(if (enabled) 0xFF1A1F2E.toInt() else 0xFFCBD0D9.toInt())
            setPadding(dp(12), dp(6), dp(12), dp(6))
            if (enabled) setOnClickListener { onClick() }
        }
        pager.addView(arrow("‹", page > 0) { page--; populate(); scrollTop() })
        val start = (page / 5) * 5
        for (p in start until minOf(start + 5, totalPages)) {
            pager.addView(TextView(this).apply {
                text = "${p + 1}"; textSize = 16f; gravity = Gravity.CENTER; minWidth = dp(38)
                val cur = p == page
                setTextColor(if (cur) 0xFFFFFFFF.toInt() else 0xFF1A1F2E.toInt())
                setTypeface(typeface, if (cur) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                if (cur) background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(0xFF4F9CF9.toInt()) }
                setOnClickListener { if (p != page) { page = p; populate(); scrollTop() } }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(2); marginEnd = dp(2) }
            })
        }
        pager.addView(arrow("›", page < totalPages - 1) { page++; populate(); scrollTop() })
    }

    /** 촬영 때 stamp된 skillKey(.upload_skill 우선, 없으면 meta active_skill_key). 미분류/없으면 "". */
    private fun stampedSkillKey(dir: File): String {
        File(dir, ".upload_skill").let { if (it.exists()) it.readText().trim().let { s -> if (s.isNotEmpty()) return s } }
        val meta = File(dir, "meta.txt")
        if (!meta.exists()) return ""
        return meta.readLines().firstOrNull { it.startsWith("active_skill_key=") }
            ?.substringAfter("=")?.trim() ?: ""
    }

    /** 선택분에 학습을 지정하고 큐에 넣는다. */
    private fun queueSelected(jobId: String, skillKey: String, label: String, dialog: android.app.Dialog) {
        val rootDir = getExternalFilesDir(null)
        selected.forEach { name ->
            val d = File(rootDir, name)
            if (d.isDirectory) {
                File(d, ".upload_job").writeText(jobId)
                File(d, ".upload_skill").writeText(skillKey)
                File(d, ".queued").writeText("1")
            }
        }
        UploadQueue.enqueue(this)
        Toast.makeText(this, "${selected.size}편 전송 대기 → $label", Toast.LENGTH_SHORT).show()
        selected.clear(); dialog.dismiss(); populate()
    }

    /**
     * 선택 전송. 기본은 촬영 때 stamp된 학습으로 원탭 전송(#2), 바꿀 때만 목록에서 변경.
     * 학습 목록이 없으면(오프라인·미동기화) 실제 학습을 지정할 수 없어 전송 차단(#f, 미배정 방지).
     */
    private fun sendSelected() {
        if (selected.isEmpty()) { Toast.makeText(this, "전송할 촬영을 선택하세요", Toast.LENGTH_SHORT).show(); return }
        val catalog = DeviceSkills.cached
        if (catalog.isEmpty()) {
            Toast.makeText(this, "학습 목록이 없어 전송할 수 없습니다. 온라인 연결 후 다시 시도하세요.", Toast.LENGTH_LONG).show()
            return
        }
        val rootDir = getExternalFilesDir(null)
        val stamped = selected.map { stampedSkillKey(File(rootDir, it)) }.distinct()
        val default = if (stamped.size == 1 && stamped[0].isNotEmpty())
            catalog.firstOrNull { it.skillKey == stamped[0] } else null

        val dialog = android.app.Dialog(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(TextView(this).apply {
            text = "${selected.size}편 전송"; setTextColor(0xFF1A1F2E.toInt()); textSize = 18f
            setPadding(dp(2), 0, dp(2), dp(8))
        })

        fun row(label: String, sub: String, primary: Boolean, onTap: () -> Unit) {
            root.addView(TextView(this).apply {
                text = if (sub.isBlank()) label else "$label   ·   $sub"
                setTextColor(if (primary) 0xFFFFFFFF.toInt() else 0xFF1A1F2E.toInt()); textSize = 16f
                setBackgroundResource(if (primary) R.drawable.bg_btn_azure else R.drawable.bg_pick_row_light)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
                setOnClickListener { onTap() }
            })
        }

        if (default != null) {
            row("${default.displayName} 으로 전송", "촬영 때 지정", true) {
                queueSelected(default.id, default.skillKey, default.displayName, dialog)
            }
            root.addView(TextView(this).apply {
                text = "다른 학습으로 변경"; setTextColor(0xFF8A909E.toInt()); textSize = 13f
                setPadding(dp(2), dp(12), dp(2), dp(2))
            })
        } else {
            root.addView(TextView(this).apply {
                text = "학습을 지정하세요 (미분류 편 포함)"; setTextColor(0xFF8A909E.toInt()); textSize = 13f
                setPadding(dp(2), 0, dp(2), dp(6))
            })
        }
        for (s in catalog) row(s.displayName, "", false) {
            queueSelected(s.id, s.skillKey, s.displayName, dialog)
        }
        root.addView(TextView(this).apply {
            text = "취소"; setTextColor(0xFF6B7280.toInt()); textSize = 15f; gravity = Gravity.CENTER
            setPadding(dp(10), dp(14), dp(10), dp(4)); setOnClickListener { dialog.dismiss() }
        })
        val scroll = ScrollView(this).apply {
            setBackgroundResource(R.drawable.bg_dialog_light)
            setPadding(dp(20), dp(20), dp(20), dp(16)); addView(root)
        }
        dialog.setContentView(scroll)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
        val maxH = (resources.displayMetrics.heightPixels * 0.9f).toInt()
        val rows = catalog.size + (if (default != null) 1 else 0)
        dialog.window?.setLayout(dp(360), minOf(dp(80 + rows * 64 + 60), maxH))
    }

    /** 삭제 확인 — 라이트 커스텀 다이얼로그(단건·다건 공용). onYes 후 목록 새로고침. */
    private fun deleteDialog(message: String, onYes: () -> Unit) {
        val dialog = android.app.Dialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_dialog_light)
            setPadding(dp(22), dp(22), dp(22), dp(16))
        }
        root.addView(TextView(this).apply {
            text = message; setTextColor(0xFF1A1F2E.toInt()); textSize = 16f
        })
        val btns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END
            setPadding(0, dp(18), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        btns.addView(TextView(this).apply {
            text = "취소"; setTextColor(0xFF6B7280.toInt()); textSize = 15f
            setPadding(dp(14), dp(8), dp(14), dp(8)); setOnClickListener { dialog.dismiss() }
        })
        btns.addView(TextView(this).apply {
            text = "삭제"; setTextColor(0xFFE0685E.toInt()); textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(14), dp(8), dp(6), dp(8))
            setOnClickListener { onYes(); dialog.dismiss(); populate() }
        })
        root.addView(btns)
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout(dp(320), android.view.WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun confirmDelete(dir: File, name: String) =
        deleteDialog("${prettyName(name)} 삭제할까요?") {
            dir.deleteRecursively(); selected.remove(name); expanded.remove(name)
        }

    private fun confirmDeleteSelected() {
        if (selected.isEmpty()) return
        deleteDialog("선택한 ${selected.size}편을 삭제할까요?") {
            val rootDir = getExternalFilesDir(null)
            selected.forEach { nm -> File(rootDir, nm).deleteRecursively(); expanded.remove(nm) }
            selected.clear()
        }
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
        // 선택 액션 바: 선택 삭제 버튼(있을 때만) + 전체 선택/해제 토글 라벨.
        findViewById<TextView>(R.id.deleteSelectedBtn).apply {
            text = "선택 삭제 (${selected.size})"
            visibility = if (selected.isEmpty()) View.GONE else View.VISIBLE
        }
        val all = allRecNames()
        findViewById<TextView>(R.id.selectAllBtn).text =
            if (all.isNotEmpty() && selected.containsAll(all)) "전체 해제" else "전체 선택"
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

        buildPager(pager, totalPages)
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
        header.addView(left)
        learnLabel(dir)?.let { lbl ->
            // 미분류 = 앰버(지정 필요 신호), 지정되면 azure 실제 스킬명. 지정 후 자동으로 바뀜.
            val amber = lbl == "미분류"
            val fg = if (amber) 0xFFE0A44E.toInt() else 0xFF4F9CF9.toInt()
            val bg = if (amber) 0x1AE0A44E else 0x1A4F9CF9
            header.addView(TextView(this).apply {
                text = lbl; setTextColor(fg); textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(10), dp(5), dp(10), dp(5))
                background = GradientDrawable().apply { cornerRadius = dp(999).toFloat(); setColor(bg) }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
            })
        }
        header.addView(check)
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
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setPadding(dp(20), dp(9), dp(20), dp(9))
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(0xFFE0685E.toInt()) }
            setOnClickListener { confirmDelete(dir, name) }
        }
        detail.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END
            setPadding(0, dp(12), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(del)
        })
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

    /** 촬영 때 stamp 된 학습명. meta.txt 의 active_skill_key/training_job_id → 스텁 라벨. 없으면 null. */
    private fun learnLabel(dir: File): String? {
        val meta = File(dir, "meta.txt")
        if (!meta.exists()) return null
        var key = ""; var job = ""
        meta.readLines().forEach {
            when {
                it.startsWith("active_skill_key=") -> key = it.substringAfter("=").trim()
                it.startsWith("training_job_id=") -> job = it.substringAfter("=").trim()
            }
        }
        // 전송 때 재선택한 skillKey(.upload_skill)가 있으면 그게 우선.
        val picked = File(dir, ".upload_skill").let { if (it.exists()) it.readText().trim() else "" }
        val useKey = picked.ifBlank { key }
        if (useKey.isBlank() && (job == Prefs.UNASSIGNED_JOB || job.isBlank())) return "미분류"
        val raw = useKey.ifBlank { job }
        if (raw.isBlank()) return null
        // 서버 카탈로그로 displayName 매핑(청기잡기 등). 없으면 raw key.
        return DeviceSkills.cached.firstOrNull { it.skillKey == useKey }?.displayName ?: raw
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
