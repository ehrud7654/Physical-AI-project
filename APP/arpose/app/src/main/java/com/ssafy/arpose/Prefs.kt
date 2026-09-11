package com.ssafy.arpose

import android.content.Context

/** 카메라 설정 저장소. 촬영 화면(적용)과 설정 화면(선택)이 공유한다. */
object Prefs {
    private const val NAME = "arpose"
    private const val KEY_AF_AUTO = "af_auto"   // true=AUTO, false=FIXED(기본)
    private const val KEY_AE_LOCK = "ae_lock"   // true=LOCK, false=AUTO(기본)

    // QR 로 채울 세션 메타. 지금은 빈 값, QR 스캔 붙으면 setSession 으로 채운다.
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_SESSION_ID = "session_id"
    private const val KEY_SKILL_ID = "skill_id"   // 값 목록은 이슈 109 확정 대기

    // 활성 학습(TrainingJob). 촬영 시 stamp → 전송 라우팅·오배정 예방 (S15P21A103-164).
    private const val KEY_ACTIVE_JOB = "active_job_id"
    private const val KEY_ACTIVE_SKILL_KEY = "active_skill_key"
    private const val KEY_ACTIVE_SKILL_NAME = "active_skill_name"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun afAuto(ctx: Context) = sp(ctx).getBoolean(KEY_AF_AUTO, false)
    fun aeLock(ctx: Context) = sp(ctx).getBoolean(KEY_AE_LOCK, false)

    fun setAfAuto(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(KEY_AF_AUTO, v).apply()
    fun setAeLock(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(KEY_AE_LOCK, v).apply()

    fun serverUrl(ctx: Context) = sp(ctx).getString(KEY_SERVER_URL, "") ?: ""
    fun userId(ctx: Context) = sp(ctx).getString(KEY_USER_ID, "") ?: ""
    fun sessionId(ctx: Context) = sp(ctx).getString(KEY_SESSION_ID, "") ?: ""
    fun skillId(ctx: Context) = sp(ctx).getString(KEY_SKILL_ID, "") ?: ""

    fun activeJobId(ctx: Context) = sp(ctx).getString(KEY_ACTIVE_JOB, "") ?: ""
    fun activeSkillKey(ctx: Context) = sp(ctx).getString(KEY_ACTIVE_SKILL_KEY, "") ?: ""
    fun activeSkillName(ctx: Context) = sp(ctx).getString(KEY_ACTIVE_SKILL_NAME, "") ?: ""

    /** 활성 학습 선택(배지). 목록은 BE(SKILL-01/TRAIN-02) 연동 전까지 스텁. */
    fun setActiveLearning(ctx: Context, jobId: String, skillKey: String, skillName: String) =
        sp(ctx).edit()
            .putString(KEY_ACTIVE_JOB, jobId)
            .putString(KEY_ACTIVE_SKILL_KEY, skillKey)
            .putString(KEY_ACTIVE_SKILL_NAME, skillName)
            .apply()

    /** QR 스캔 결과로 세션 메타를 한 번에 채운다. */
    fun setSession(ctx: Context, serverUrl: String, userId: String, sessionId: String, skillId: String) =
        sp(ctx).edit()
            .putString(KEY_SERVER_URL, serverUrl)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_SESSION_ID, sessionId)
            .putString(KEY_SKILL_ID, skillId)
            .apply()
}
