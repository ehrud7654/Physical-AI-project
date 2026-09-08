package com.ssafy.arpose

import android.content.Context

/** 카메라 설정 저장소. 촬영 화면(적용)과 설정 화면(선택)이 공유한다. */
object Prefs {
    private const val NAME = "arpose"
    private const val KEY_AF_AUTO = "af_auto"   // true=AUTO, false=FIXED(기본)
    private const val KEY_AE_LOCK = "ae_lock"   // true=LOCK, false=AUTO(기본)

    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun afAuto(ctx: Context) = sp(ctx).getBoolean(KEY_AF_AUTO, false)
    fun aeLock(ctx: Context) = sp(ctx).getBoolean(KEY_AE_LOCK, false)

    fun setAfAuto(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(KEY_AF_AUTO, v).apply()
    fun setAeLock(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(KEY_AE_LOCK, v).apply()
}
