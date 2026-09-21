package com.a103.collect

import android.content.Context

/**
 * 로컬 설정. 촬영 전 고른 학습(스킬)과 서버 스킬 카탈로그 캐시를 보관한다.
 * 학습 미선택(미분류) = skillKey 빈 문자열.
 */
object Prefs {
    private const val NAME = "collect"
    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** 현재 촬영에 붙일 학습 키. "" = 미분류. */
    fun activeSkillKey(ctx: Context): String = sp(ctx).getString("active_skill_key", "") ?: ""
    fun activeSkillName(ctx: Context): String = sp(ctx).getString("active_skill_name", "미분류") ?: "미분류"
    fun setActiveSkill(ctx: Context, key: String, name: String) =
        sp(ctx).edit().putString("active_skill_key", key).putString("active_skill_name", name).apply()

    /** 서버 스킬 카탈로그 JSON 배열 문자열 [{"key","name"}]. 온라인 연동(후속) 전엔 빈 값. */
    fun skillCatalog(ctx: Context): String = sp(ctx).getString("skill_catalog", "") ?: ""
    fun setSkillCatalog(ctx: Context, json: String) =
        sp(ctx).edit().putString("skill_catalog", json).apply()

    /** 튜토리얼 최초 1회 표시. */
    fun tutorialSeen(ctx: Context): Boolean = sp(ctx).getBoolean("tutorial_seen", false)
    fun setTutorialSeen(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("tutorial_seen", v).apply()

    /** 장치 자격 — enroll 로 1회 수령. secret 은 Keystore 암호화 저장. */
    fun deviceId(ctx: Context): String = sp(ctx).getString("device_id", "") ?: ""
    fun deviceSecret(ctx: Context): String = SecureStore.decrypt(sp(ctx).getString("device_secret", "") ?: "")
    fun setDeviceCreds(ctx: Context, id: String, secret: String) =
        sp(ctx).edit().putString("device_id", id).putString("device_secret", SecureStore.encrypt(secret)).apply()
}
