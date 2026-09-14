package com.ssafy.arpose

import android.content.Context

/** 카메라 설정 저장소. 촬영 화면(적용)과 설정 화면(선택)이 공유한다. */
object Prefs {
    private const val NAME = "arpose"
    private const val KEY_AF_AUTO = "af_auto"   // true=AUTO, false=FIXED(기본)
    private const val KEY_AE_LOCK = "ae_lock"   // true=LOCK(기본), false=AUTO
    private const val KEY_GRID_GUIDE = "grid_guide"   // 구도 그리드 (기본 off)
    private const val KEY_SHOW_DETAIL = "show_detail"  // 상세값 표시 (기본 off)

    // QR 로 채울 세션 메타. 지금은 빈 값, QR 스캔 붙으면 setSession 으로 채운다.
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_SESSION_ID = "session_id"
    private const val KEY_SKILL_ID = "skill_id"   // 값 목록은 이슈 109 확정 대기

    // 장치 자격(QR 로 받음). durable — 토큰은 이걸로 DEVICE-05 에서 발급/갱신 (S15P21A103-163).
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_DEVICE_SECRET = "device_secret"
    private const val KEY_DEVICE_IDENT = "device_identifier"

    // 디버그 업로드(디버그 빌드 전용). QR 연동 전 실서버 테스트: jobId·자격 직접 입력.
    private const val KEY_DEBUG_ENABLED = "debug_upload"
    private const val KEY_DEBUG_JOB = "debug_job_id"

    // FIXED 초점 거리(디옵터=1/m). 기본 6.25 ≈ 16cm (파지 10~16cm + ~40cm 를 224 기준으로 커버).
    private const val KEY_FOCUS_DIOPTERS = "focus_diopters"

    // 활성 학습(TrainingJob). 촬영 시 stamp → 전송 라우팅·오배정 예방 (S15P21A103-164).
    private const val KEY_ACTIVE_JOB = "active_job_id"
    private const val KEY_ACTIVE_SKILL_KEY = "active_skill_key"
    private const val KEY_ACTIVE_SKILL_NAME = "active_skill_name"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun afAuto(ctx: Context) = sp(ctx).getBoolean(KEY_AF_AUTO, false)
    fun aeLock(ctx: Context) = sp(ctx).getBoolean(KEY_AE_LOCK, true)
    fun gridGuide(ctx: Context) = sp(ctx).getBoolean(KEY_GRID_GUIDE, false)
    fun showDetail(ctx: Context) = sp(ctx).getBoolean(KEY_SHOW_DETAIL, false)

    fun setAfAuto(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(KEY_AF_AUTO, v).apply()
    fun setAeLock(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(KEY_AE_LOCK, v).apply()
    fun setGridGuide(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(KEY_GRID_GUIDE, v).apply()
    fun setShowDetail(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(KEY_SHOW_DETAIL, v).apply()

    fun serverUrl(ctx: Context) = sp(ctx).getString(KEY_SERVER_URL, "") ?: ""
    fun userId(ctx: Context) = sp(ctx).getString(KEY_USER_ID, "") ?: ""
    fun sessionId(ctx: Context) = sp(ctx).getString(KEY_SESSION_ID, "") ?: ""
    fun skillId(ctx: Context) = sp(ctx).getString(KEY_SKILL_ID, "") ?: ""

    fun deviceId(ctx: Context) = sp(ctx).getString(KEY_DEVICE_ID, "") ?: ""
    /** Device Secret 은 Keystore 암호문으로 저장 — 읽을 때 복호 (S15P21A103-168). */
    fun deviceSecret(ctx: Context) = SecureStore.decrypt(sp(ctx).getString(KEY_DEVICE_SECRET, "") ?: "")
    /** 등록 시 서버에 저장된 하드웨어 핸들(예: UMI-001). 업로드 metadata.device.deviceIdentifier 로 나감. */
    fun deviceIdentifier(ctx: Context) = sp(ctx).getString(KEY_DEVICE_IDENT, "") ?: ""
    fun setDeviceCreds(ctx: Context, id: String, secret: String) =
        sp(ctx).edit().putString(KEY_DEVICE_ID, id)
            .putString(KEY_DEVICE_SECRET, SecureStore.encrypt(secret)).apply()

    fun debugEnabled(ctx: Context) = sp(ctx).getBoolean(KEY_DEBUG_ENABLED, false)
    fun debugJobId(ctx: Context) = sp(ctx).getString(KEY_DEBUG_JOB, "") ?: ""
    fun focusDiopters(ctx: Context) = sp(ctx).getFloat(KEY_FOCUS_DIOPTERS, 6.25f)
    fun setFocusDiopters(ctx: Context, v: Float) = sp(ctx).edit().putFloat(KEY_FOCUS_DIOPTERS, v).apply()

    /** 디버그 업로드 설정 저장(토글·jobId·자격). 디버그 빌드 설정 화면에서만 호출. */
    fun setDebug(ctx: Context, enabled: Boolean, jobId: String, deviceId: String, deviceSecret: String) =
        sp(ctx).edit()
            .putBoolean(KEY_DEBUG_ENABLED, enabled)
            .putString(KEY_DEBUG_JOB, jobId.trim())
            .putString(KEY_DEVICE_ID, deviceId.trim())
            .putString(KEY_DEVICE_SECRET, SecureStore.encrypt(deviceSecret.trim()))
            .apply()

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
