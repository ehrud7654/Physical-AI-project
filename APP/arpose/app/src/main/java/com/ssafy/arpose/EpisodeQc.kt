package com.ssafy.arpose

import java.io.File

/**
 * 에피소드 "사용 가능" 판정 — 배지 편수(N/목표)와 기록 판정이 같은 기준을 쓰게 공용화.
 * ✓(좋음)만 usable. 요약 없음/데이터 구멍/점프·끊김/마커<50% 는 재촬영이라 제외.
 */
object EpisodeQc {

    /** marker_qc.txt(detected/total) → 검출률(%). 없으면 null(옛 촬영·계산 전). */
    fun markerRate(dir: File): Int? {
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

    /** ✓(좋음)만 true. (기록 화면 verdictOf 의 ✓ 조건과 동일 기준) */
    fun isUsable(dir: File): Boolean {
        val s = File(dir, "summary.txt")
        if (!s.exists()) return false
        val m = HashMap<String, String>()
        var any = false
        s.readLines().forEach { line ->
            if (line.trim().startsWith("WARNING")) m["__warn"] = "1"
            val i = line.indexOf('=')
            if (i > 0) { m[line.substring(0, i).trim()] = line.substring(i + 1).trim(); any = true }
        }
        if (!any) return false                                          // 요약 없음(미완료)
        if (m.containsKey("__warn")) return false                       // 경고
        if ((m["frames_write_failed"]?.toIntOrNull() ?: 0) > 0) return false
        if ((m["pose_rows_failed"]?.toIntOrNull() ?: 0) > 0) return false  // 데이터 구멍
        if ((m["tracking_jumps"]?.toIntOrNull() ?: 0) > 0) return false
        if ((m["paused_frames"]?.toIntOrNull() ?: 0) > 0) return false     // 점프/끊김
        val mk = markerRate(dir)
        if (mk != null && mk < 50) return false                         // 마커 거의 안 잡힘
        return true
    }
}
