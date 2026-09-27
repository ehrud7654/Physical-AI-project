package com.ssafy.arpose

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Device Secret 같은 민감값을 AndroidKeyStore 의 AES-GCM 키로 암호화해 저장 (S15P21A103-168 §12).
 * 키는 하드웨어 보안영역(지원 시)에 상주하고 앱은 암호문만 SharedPreferences 에 둔다.
 * 저장 포맷: "v1:" + base64(iv(12) + ciphertext+tag). 마커 없는 값은 레거시 평문으로 간주해 그대로 읽어
 * 다음 쓰기에서 자동 재암호화(무중단 마이그레이션).
 */
object SecureStore {
    private const val KS = "AndroidKeyStore"
    private const val ALIAS = "arpose_device_secret"
    private const val PREFIX = "v1:"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KS).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KS)
        kg.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return kg.generateKey()
    }

    /** 평문 → "v1:<base64(iv+ct)>". 빈 값은 그대로. */
    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = c.iv + c.doFinal(plain.toByteArray())
        return PREFIX + Base64.encodeToString(out, Base64.NO_WRAP)
    }

    /** "v1:..." → 평문. 마커 없으면 레거시 평문으로 보고 그대로. 복호 실패 시 "". */
    fun decrypt(stored: String): String {
        if (stored.isEmpty()) return ""
        if (!stored.startsWith(PREFIX)) return stored
        return try {
            val raw = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, IV_LEN)
            val ct = raw.copyOfRange(IV_LEN, raw.size)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
                .apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv)) }
            String(c.doFinal(ct))
        } catch (e: Exception) { "" }
    }
}
