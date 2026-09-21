package com.a103.collect

import org.json.JSONObject
import java.io.File

/** 학습별 "사용 가능(recorded·success)" 편수 집계. 목표 GOAL 편. */
object Episodes {
    const val GOAL = 50

    /** 세션의 실제 학습키: 전송 때 지정한 .upload_skill 우선, 없으면 manifest.skill_key. "" = 미분류. */
    fun effectiveSkill(dir: File): String {
        File(dir, ".upload_skill").let { if (it.exists()) it.readText().trim().let { s -> if (s.isNotEmpty()) return s } }
        return runCatching { JSONObject(File(dir, "manifest.json").readText()).optString("skill_key") }.getOrDefault("")
    }

    private fun usable(dir: File): Boolean {
        val m = runCatching { JSONObject(File(dir, "manifest.json").readText()) }.getOrNull() ?: return false
        return m.optString("status") == "recorded" && m.optString("outcome") == "success"
    }

    /** 해당 학습키의 사용 가능 편수. */
    fun count(root: File, skillKey: String): Int =
        root.listFiles { f -> f.isDirectory && f.name.startsWith("rec_") }
            ?.count { effectiveSkill(it) == skillKey && usable(it) } ?: 0
}
