package com.a103.collect

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import org.json.JSONObject
import java.io.File

/**
 * 기록/검토 화면. 카드 탭=선택, 우측 토글=상세. 다중/전체 선택 + 하단 액션바(일괄 전송·삭제).
 * 전송 시 학습(스킬) 지정. 20편 페이지. 품질·학습은 manifest.json 에서.
 */
class HistoryActivity : Activity() {

    private val selected = HashSet<String>()
    private var page = 0
    private var filterKey: String? = null   // null=전체, ""=미분류, else skillKey

    private lateinit var root: File
    private lateinit var titleText: TextView
    private lateinit var selectAllBtn: TextView
    private lateinit var listView: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var scroll: ScrollView
    private lateinit var pagerRow: LinearLayout
    private lateinit var filterRow: LinearLayout
    private lateinit var actionBar: LinearLayout
    private lateinit var sendBtn: TextView
    private lateinit var deleteSelBtn: TextView

    private val uploadWork by lazy { WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData(UploadQueue.WORK_NAME) }
    private var workObserved = false
    private val uploadObserver = Observer<List<WorkInfo>> {
        if (!workObserved) { workObserved = true; return@Observer }
        populate()
    }

    companion object { private const val PAGE_SIZE = 20 }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = File(getExternalFilesDir(null), "sessions").apply { mkdirs() }
        DeviceSkills.load(this)

        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFF3F4F7.toInt()) }

        // 상단바: ‹ + 제목 + 전체선택
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(14), dp(16), dp(12))
        }
        bar.addView(TextView(this).apply {
            text = "‹"; textSize = 30f; setTextColor(0xFF1B2233.toInt()); setPadding(dp(8), 0, dp(12), dp(4))
            setOnClickListener { finish() }
        })
        titleText = TextView(this).apply {
            text = "기록"; textSize = 22f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(0xFF1B2233.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        bar.addView(titleText)
        selectAllBtn = TextView(this).apply {
            text = "전체 선택"; textSize = 13f; setTextColor(0xFF4F9CF9.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(dp(14), dp(7), dp(14), dp(7))
            background = GradientDrawable().apply { cornerRadius = dp(999).toFloat(); setColor(0x1A4F9CF9); setStroke(dp(1), 0x554F9CF9) }
            setOnClickListener { toggleSelectAll() }
        }
        bar.addView(selectAllBtn)
        outer.addView(bar)

        // 학습 필터 칩 (가로 스크롤, 상단 고정)
        filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        outer.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; setPadding(dp(14), dp(2), dp(14), dp(8))
            addView(filterRow)
        })

        emptyText = TextView(this).apply {
            text = "촬영 기록이 없습니다"; setTextColor(0xFF8A909E.toInt()); textSize = 15f
            setPadding(dp(20), dp(24), dp(20), 0); visibility = View.GONE
        }
        outer.addView(emptyText)

        listView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(14), dp(10)) }
        pagerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(16)); visibility = View.GONE
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(listView); addView(pagerRow) }
        scroll = ScrollView(this).apply {
            addView(col)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        outer.addView(scroll)

        // 하단 액션바(선택 시만)
        actionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt()); setPadding(dp(16), dp(12), dp(16), dp(12)); visibility = View.GONE
        }
        deleteSelBtn = TextView(this).apply {
            text = "선택 삭제"; setTextColor(0xFFE0685E.toInt()); textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(16), dp(11), dp(16), dp(11))
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(0x1AE0685E) }
            setOnClickListener { confirmDeleteSelected() }
        }
        sendBtn = TextView(this).apply {
            text = "전송"; setTextColor(0xFFFFFFFF.toInt()); textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(28), dp(11), dp(28), dp(11)); setBackgroundResource(R.drawable.bg_btn_azure)
            setOnClickListener { sendSelected() }
        }
        actionBar.addView(deleteSelBtn)
        actionBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        actionBar.addView(sendBtn)
        outer.addView(actionBar)

        setContentView(outer)
        populate()
        uploadWork.observeForever(uploadObserver)
        // 대기 중 항목 있으면 재개(제약 변경 등으로 멈춘 큐 되살리기).
        if (UploadQueue.pending(this).isNotEmpty()) UploadQueue.enqueue(this)
    }

    override fun onDestroy() { super.onDestroy(); uploadWork.removeObserver(uploadObserver) }

    private fun allRecNames(): List<String> =
        root.listFiles { f -> f.isDirectory && f.name.startsWith("rec_") }?.map { it.name } ?: emptyList()

    /** 현재 필터에 맞는 편만 (전체=필터 없음, ""=미분류). 최신순. */
    private fun visibleRecNames(): List<String> {
        val fk = filterKey
        return allRecNames()
            .filter { fk == null || stampedSkillKey(File(root, it)) == fk }
            .sortedByDescending { it }
    }

    private fun toggleSelectAll() {
        val all = visibleRecNames(); if (all.isEmpty()) return
        if (selected.containsAll(all)) selected.clear() else { selected.clear(); selected.addAll(all) }
        populate()
    }

    private fun manifestOf(dir: File): JSONObject? =
        runCatching { JSONObject(File(dir, "manifest.json").readText()) }.getOrNull()

    /** 전송 상태. */
    private fun uploadStatus(dir: File): Pair<String, Int> = when {
        File(dir, ".uploaded").exists() -> "전송됨" to 0xFF3FBF87.toInt()
        File(dir, ".queued").exists() -> "전송 대기" to 0xFFE0A44E.toInt()
        else -> "미전송" to 0xFF8A909E.toInt()
    }

    /** 학습 라벨. .upload_skill 우선, 없으면 manifest.skill_key. 빈값=미분류. 카탈로그로 표시명 매핑. */
    private fun learnLabel(dir: File, m: JSONObject?): String {
        val picked = File(dir, ".upload_skill").let { if (it.exists()) it.readText().trim() else "" }
        val key = picked.ifBlank { m?.optString("skill_key") ?: "" }
        if (key.isBlank()) return "미분류"
        return DeviceSkills.cached.firstOrNull { it.skillKey == key }?.displayName ?: key
    }

    private fun stampedSkillKey(dir: File): String {
        File(dir, ".upload_skill").let { if (it.exists()) it.readText().trim().let { s -> if (s.isNotEmpty()) return s } }
        return manifestOf(dir)?.optString("skill_key") ?: ""
    }

    // ---- 전송(학습 지정) ----
    private fun sendSelected() {
        if (selected.isEmpty()) { Toast.makeText(this, "전송할 촬영을 선택하세요", Toast.LENGTH_SHORT).show(); return }
        val catalog = DeviceSkills.cached
        if (catalog.isEmpty()) {
            Toast.makeText(this, "학습 목록이 없어 전송할 수 없습니다. 설정에서 스킬 동기화 후 다시 시도하세요.", Toast.LENGTH_LONG).show()
            return
        }
        val stamped = selected.map { stampedSkillKey(File(root, it)) }.distinct()
        val default = if (stamped.size == 1 && stamped[0].isNotEmpty()) catalog.firstOrNull { it.skillKey == stamped[0] } else null

        val dialog = Dialog(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(TextView(this).apply {
            text = "${selected.size}편 전송"; setTextColor(0xFF1A1F2E.toInt()); textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(dp(2), 0, dp(2), dp(8))
        })
        fun row(label: String, sub: String, primary: Boolean, onTap: () -> Unit) {
            box.addView(TextView(this).apply {
                text = if (sub.isBlank()) label else "$label   ·   $sub"
                setTextColor(if (primary) 0xFFFFFFFF.toInt() else 0xFF1A1F2E.toInt()); textSize = 16f
                setBackgroundResource(if (primary) R.drawable.bg_btn_azure else R.drawable.bg_pick_row_light)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(8) }
                setOnClickListener { onTap() }
            })
        }
        if (default != null) {
            row("${default.displayName} 으로 전송", "촬영 때 지정", true) { queue(default.skillKey, default.displayName, dialog) }
            box.addView(TextView(this).apply {
                text = "다른 학습으로 변경"; setTextColor(0xFF8A909E.toInt()); textSize = 13f; setPadding(dp(2), dp(12), dp(2), dp(2))
            })
        } else box.addView(TextView(this).apply {
            text = "학습을 지정하세요 (미분류 편 포함)"; setTextColor(0xFF8A909E.toInt()); textSize = 13f; setPadding(dp(2), 0, dp(2), dp(6))
        })
        for (s in catalog) row(s.displayName, "", false) { queue(s.skillKey, s.displayName, dialog) }
        box.addView(TextView(this).apply {
            text = "취소"; setTextColor(0xFF6B7280.toInt()); textSize = 15f; gravity = Gravity.CENTER
            setPadding(dp(10), dp(14), dp(10), dp(4)); setOnClickListener { dialog.dismiss() }
        })
        val sv = ScrollView(this).apply { setBackgroundResource(R.drawable.bg_dialog_light); setPadding(dp(20), dp(20), dp(20), dp(16)); addView(box) }
        dialog.setContentView(sv)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val maxH = (resources.displayMetrics.heightPixels * 0.9f).toInt()
        dialog.window?.setLayout(dp(360), minOf(dp(80 + (catalog.size + 1) * 64 + 60), maxH))
        dialog.show()
    }

    private fun queue(skillKey: String, label: String, dialog: Dialog) {
        selected.forEach { name ->
            val d = File(root, name)
            if (d.isDirectory) { File(d, ".upload_skill").writeText(skillKey); File(d, ".queued").writeText("1") }
        }
        UploadQueue.enqueue(this)
        Toast.makeText(this, "${selected.size}편 전송 대기 → $label", Toast.LENGTH_SHORT).show()
        selected.clear(); dialog.dismiss(); populate()
    }

    // ---- 삭제 ----
    private fun deleteDialog(message: String, onYes: () -> Unit) {
        val dialog = Dialog(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bg_dialog_light); setPadding(dp(22), dp(22), dp(22), dp(16))
        }
        box.addView(TextView(this).apply { text = message; setTextColor(0xFF1A1F2E.toInt()); textSize = 16f })
        val btns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(0, dp(18), 0, 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        btns.addView(TextView(this).apply {
            text = "취소"; setTextColor(0xFF6B7280.toInt()); textSize = 15f; setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener { dialog.dismiss() }
        })
        btns.addView(TextView(this).apply {
            text = "삭제"; setTextColor(0xFFE0685E.toInt()); textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(14), dp(8), dp(6), dp(8)); setOnClickListener { onYes(); dialog.dismiss(); populate() }
        })
        box.addView(btns)
        dialog.setContentView(box)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(dp(320), LinearLayout.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    private fun confirmDelete(dir: File, name: String) =
        deleteDialog("${prettyName(name)} 삭제할까요?") { dir.deleteRecursively(); selected.remove(name) }

    private fun confirmDeleteSelected() {
        if (selected.isEmpty()) return
        deleteDialog("선택한 ${selected.size}편을 삭제할까요?") {
            selected.forEach { nm -> File(root, nm).deleteRecursively() }; selected.clear()
        }
    }

    private fun updateActionBar() {
        val has = selected.isNotEmpty()
        actionBar.visibility = if (has) View.VISIBLE else View.GONE
        sendBtn.text = if (has) "전송 (${selected.size})" else "전송"
        deleteSelBtn.text = "선택 삭제 (${selected.size})"
        titleText.text = if (has) "${selected.size}개 선택" else "기록"
        val all = visibleRecNames()
        selectAllBtn.visibility = if (all.isEmpty()) View.GONE else View.VISIBLE
        selectAllBtn.text = if (all.isNotEmpty() && selected.containsAll(all)) "전체 해제" else "전체 선택"
    }

    /** 필터 칩: 전체 · 학습들 · 미분류. 선택된 칩은 꽉 찬 파랑+흰 글씨. */
    private fun buildFilter() {
        filterRow.removeAllViews()
        fun chip(label: String, key: String?) {
            val on = filterKey == key
            filterRow.addView(TextView(this).apply {
                text = label; textSize = 13f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(if (on) 0xFFFFFFFF.toInt() else 0xFF4A5169.toInt())
                setPadding(dp(16), dp(8), dp(16), dp(8))
                background = GradientDrawable().apply {
                    cornerRadius = dp(999).toFloat()
                    if (on) setColor(0xFF4F9CF9.toInt()) else { setColor(0xFFFFFFFF.toInt()); setStroke(dp(1), 0xFFDDE1E8.toInt()) }
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { marginEnd = dp(8) }
                setOnClickListener { if (filterKey != key) { filterKey = key; selected.clear(); page = 0; populate() } }
            })
        }
        chip("전체", null)
        DeviceSkills.cached.forEach { chip(it.displayName, it.skillKey) }
        chip("미분류", "")
    }

    private fun populate() {
        listView.removeAllViews()
        buildFilter()
        if (allRecNames().isEmpty()) {
            emptyText.text = "촬영 기록이 없습니다"; emptyText.visibility = View.VISIBLE; pagerRow.visibility = View.GONE; updateActionBar(); return
        }
        val recs = visibleRecNames()
        if (recs.isEmpty()) {
            emptyText.text = "이 학습의 기록이 없습니다"; emptyText.visibility = View.VISIBLE; pagerRow.visibility = View.GONE; updateActionBar(); return
        }
        emptyText.visibility = View.GONE
        val totalPages = (recs.size + PAGE_SIZE - 1) / PAGE_SIZE
        page = page.coerceIn(0, totalPages - 1)
        val from = page * PAGE_SIZE
        recs.subList(from, minOf(from + PAGE_SIZE, recs.size)).forEach { listView.addView(cardFor(File(root, it))) }
        buildPager(totalPages)
        updateActionBar()
    }

    private fun buildPager(totalPages: Int) {
        pagerRow.removeAllViews()
        if (totalPages <= 1) { pagerRow.visibility = View.GONE; return }
        pagerRow.visibility = View.VISIBLE
        fun arrow(txt: String, enabled: Boolean, onClick: () -> Unit) = TextView(this).apply {
            text = txt; textSize = 20f; setTextColor(if (enabled) 0xFF1A1F2E.toInt() else 0xFFCBD0D9.toInt())
            setPadding(dp(12), dp(6), dp(12), dp(6)); if (enabled) setOnClickListener { onClick() }
        }
        pagerRow.addView(arrow("‹", page > 0) { page--; populate(); scroll.post { scroll.scrollTo(0, 0) } })
        val start = (page / 5) * 5
        for (p in start until minOf(start + 5, totalPages)) {
            pagerRow.addView(TextView(this).apply {
                text = "${p + 1}"; textSize = 16f; gravity = Gravity.CENTER; minWidth = dp(38)
                val cur = p == page
                setTextColor(if (cur) 0xFFFFFFFF.toInt() else 0xFF1A1F2E.toInt())
                setPadding(dp(10), dp(6), dp(10), dp(6))
                if (cur) background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(0xFF4F9CF9.toInt()) }
                setOnClickListener { if (p != page) { page = p; populate(); scroll.post { scroll.scrollTo(0, 0) } } }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { marginStart = dp(2); marginEnd = dp(2) }
            })
        }
        pagerRow.addView(arrow("›", page < totalPages - 1) { page++; populate(); scroll.post { scroll.scrollTo(0, 0) } })
    }

    private fun dotText(label: String, color: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(View(this@HistoryActivity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(7), dp(7)).apply { marginEnd = dp(5) }
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        })
        addView(TextView(this@HistoryActivity).apply {
            text = label; setTextColor(color); textSize = 13f; setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
    }

    private fun cardFor(dir: File): View {
        val name = dir.name
        val m = manifestOf(dir)
        val (stText, stColor) = uploadStatus(dir)
        val sel = selected.contains(name)
        val s = m?.optLong("start_elapsed_ns", 0L) ?: 0L
        val e = m?.optLong("stop_elapsed_ns", 0L) ?: 0L
        val secs = if (e > s && s > 0) ((e - s) / 1_000_000_000.0).toInt() else 0
        val len = "%d:%02d".format(secs / 60, secs % 60)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(if (sel) R.drawable.bg_card_selected else R.drawable.bg_card_light)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(8) }
        }

        // 헤더: 시간 + 학습 pill + 선택 원
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = prettyName(name); setTextColor(0xFF1A1F2E.toInt()); textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val lbl = learnLabel(dir, m)
        val amber = lbl == "미분류"
        header.addView(TextView(this).apply {
            text = lbl; setTextColor(if (amber) 0xFFE0A44E.toInt() else 0xFF4F9CF9.toInt()); textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(dp(10), dp(5), dp(10), dp(5))
            background = GradientDrawable().apply { cornerRadius = dp(999).toFloat(); setColor(if (amber) 0x1AE0A44E else 0x1A4F9CF9) }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) }
        })
        header.addView(TextView(this).apply {
            text = if (sel) "✓" else ""; setTextColor(0xFFFFFFFF.toInt()); textSize = 15f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (sel) setColor(0xFF4F9CF9.toInt()) else { setColor(0x00000000); setStroke(dp(2), 0xFF9AA2B4.toInt()) }
            }
        })
        card.addView(header)

        // 상태줄: ● 전송상태 · 길이 ···· 삭제 (온디바이스에서 판정 못하는 품질/결과는 표시 안 함)
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, 0)
        }
        statusRow.addView(dotText(stText, stColor))
        statusRow.addView(TextView(this).apply {
            text = " · $len"; setTextColor(0xFF8A909E.toInt()); textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) }
        })
        statusRow.addView(TextView(this).apply {
            text = "삭제"; setTextColor(0xFFE0685E.toInt()); textSize = 13f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(12), dp(6), dp(4), dp(6)); setOnClickListener { confirmDelete(dir, name) }
        })
        card.addView(statusRow)

        card.setOnClickListener {
            if (selected.contains(name)) selected.remove(name) else selected.add(name); populate()
        }
        return card
    }

    private fun prettyName(name: String): String {
        val ms = name.removePrefix("rec_").substringBefore("_").toLongOrNull() ?: return name
        return DateFormat.format("MM/dd HH:mm", ms).toString()
    }
}
