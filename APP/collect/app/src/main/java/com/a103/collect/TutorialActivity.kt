package com.a103.collect

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * 온보딩 튜토리얼. 가로: 스냅(좌·밝은 카드) + 설명(우). 4장.
 * 최초 등록 후 자동 1회(끝나면 촬영화면), 메뉴 "도움말"에서 재열람.
 * 하단 우측 고정 이전/다음 + 좌우 스와이프(슬라이드 전환). 그림은 drawable `tut_1`..`tut_4`.
 */
class TutorialActivity : Activity() {

    private data class Page(val title: String, val desc: String)

    private val pages = listOf(
        Page("학습을 먼저 선택하세요",
            "촬영 전에 상단 '학습' 탭에서 작업을 고르세요. 못 정했으면 '미분류'로 찍고 기록에서 나중에 지정할 수 있어요."),
        Page("초록불이면 셔터로 촬영",
            "'촬영 가능'(초록불)일 때 셔터를 눌러 시작하고, 다시 누르면 끝나요. 다시 찍고 싶으면 ↻ 재촬영을 누르세요."),
        Page("마커가 계속 보이게",
            "집게의 마커가 화면에 계속 보이게 하고 천천히 움직이세요. 마커가 없거나 너무 빠르면 사용할 수 없어요."),
        Page("기록에서 전송",
            "촬영이 끝나면 자동 저장돼요. '기록'에서 촬영을 선택해 지정하고 전송하면 돼요."),
    )

    private var idx = 0
    private var animating = false

    private lateinit var content: LinearLayout
    private lateinit var imgWrap: FrameLayout
    private lateinit var textBlock: LinearLayout
    private lateinit var image: ImageView
    private lateinit var placeholder: TextView
    private lateinit var stepView: TextView
    private lateinit var titleView: TextView
    private lateinit var descView: TextView
    private lateinit var dots: LinearLayout
    private lateinit var prevBtn: TextView
    private lateinit var nextBtn: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val outer = FrameLayout(this).apply { setBackgroundColor(0xFF0E1524.toInt()) }

        content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(28), dp(20), dp(28), dp(24))
        }
        imgWrap = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat(); setColor(0xFFE9ECF1.toInt()); setStroke(dp(1), 0xFFC3CAD6.toInt())
            }
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.15f)
                .apply { marginEnd = dp(24); topMargin = dp(4); bottomMargin = dp(4) }
        }
        image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        placeholder = TextView(this).apply {
            text = "그림 준비 중"; setTextColor(0xFF5F6B8C.toInt()); textSize = 14f; gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        imgWrap.addView(image); imgWrap.addView(placeholder)
        content.addView(imgWrap)

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }
        textBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        stepView = TextView(this).apply { setTextColor(0xFF5F6B8C.toInt()); textSize = 12f; setPadding(0, 0, 0, dp(8)) }
        titleView = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt()); textSize = 22f; setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        descView = TextView(this).apply {
            setTextColor(0xFFAEB8D4.toInt()); textSize = 15f; setLineSpacing(dp(5).toFloat(), 1f); setPadding(0, dp(10), 0, 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(100))
        }
        textBlock.addView(stepView); textBlock.addView(titleView); textBlock.addView(descView)

        dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        prevBtn = TextView(this).apply {
            text = "이전"; setTextColor(0xFF8A93AD.toInt()); textSize = 16f
            setPadding(dp(14), dp(10), dp(18), dp(10)); setOnClickListener { go(-1) }
        }
        nextBtn = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt()); textSize = 16f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(30), dp(12), dp(30), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(0xFF4F9CF9.toInt()) }
            setOnClickListener { if (idx == pages.size - 1) finishTutorial() else go(1) }
        }
        val navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(22) }
            addView(dots)
            addView(View(this@TutorialActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(prevBtn); addView(nextBtn)
        }

        textCol.addView(textBlock); textCol.addView(navRow)
        content.addView(textCol)
        outer.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        outer.addView(TextView(this).apply {
            text = "건너뛰기"; setTextColor(0xFFDCE3F2.toInt()); textSize = 14f
            setPadding(dp(16), dp(9), dp(16), dp(9))
            background = GradientDrawable().apply { cornerRadius = dp(999).toFloat(); setColor(0x26FFFFFF); setStroke(dp(1), 0x40FFFFFF) }
            setOnClickListener { finishTutorial() }
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
            .apply { topMargin = dp(10); rightMargin = dp(10) })

        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dx = e2.x - (e1?.x ?: e2.x)
                if (abs(dx) > dp(70) && abs(dx) > abs(e2.y - (e1?.y ?: e2.y))) {
                    if (dx < 0) go(1) else go(-1); return true
                }
                return false
            }
        })
        outer.setOnTouchListener { _, ev -> gd.onTouchEvent(ev); true }

        setContentView(outer)
        render()
    }

    /** delta=+1 다음 / -1 이전. 이미지·텍스트만 슬라이드(버튼행은 고정). */
    private fun go(delta: Int) {
        val t = idx + delta
        if (animating || t < 0 || t >= pages.size) return
        animating = true
        val w = content.width.toFloat().let { if (it <= 0f) dp(400).toFloat() else it }
        val out = -delta * w * 0.12f
        val back = delta * w * 0.12f
        textBlock.animate().translationX(out).alpha(0f).setDuration(110).start()
        imgWrap.animate().translationX(out).alpha(0f).setDuration(110).withEndAction {
            idx = t; render()
            imgWrap.translationX = back; textBlock.translationX = back
            textBlock.animate().translationX(0f).alpha(1f).setDuration(180).start()
            imgWrap.animate().translationX(0f).alpha(1f).setDuration(180)
                .withEndAction { animating = false }.start()
        }.start()
    }

    private fun render() {
        val p = pages[idx]
        stepView.text = "${idx + 1} / ${pages.size}"
        titleView.text = p.title
        descView.text = p.desc

        val resId = resources.getIdentifier("tut_${idx + 1}", "drawable", packageName)
        if (resId != 0) {
            image.setImageResource(resId); image.visibility = View.VISIBLE; placeholder.visibility = View.GONE
        } else { image.visibility = View.GONE; placeholder.visibility = View.VISIBLE }

        dots.removeAllViews()
        for (i in pages.indices) {
            dots.addView(View(this).apply {
                val on = i == idx
                layoutParams = LinearLayout.LayoutParams(if (on) dp(18) else dp(7), dp(7)).apply { marginEnd = dp(6) }
                background = GradientDrawable().apply {
                    cornerRadius = dp(99).toFloat(); setColor(if (on) 0xFF4F9CF9.toInt() else 0xFF39456A.toInt())
                }
            })
        }
        prevBtn.visibility = if (idx == 0) View.GONE else View.VISIBLE
        nextBtn.text = if (idx == pages.size - 1) "시작하기" else "다음"
    }

    private fun finishTutorial() {
        Prefs.setTutorialSeen(this, true)
        if (intent.getBooleanExtra("go_main", false)) startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
