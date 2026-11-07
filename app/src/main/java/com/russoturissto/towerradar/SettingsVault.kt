package com.russoturissto.towerradar

import android.content.SharedPreferences
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * User-controlled AES-256-GCM backup. The file contains no plaintext API keys,
 * signing key, or developer secrets. No background export or network transfer.
 */
object SettingsVault {
    private val header = byteArrayOf(0x54, 0x52, 0x42, 0x4B, 0x31) // TRBK1
    private const val iterations = 210000
    private const val saltLength = 16
    private const val ivLength = 12
    private val keys = setOf(
        "map_key_yandex", "map_key_2gis", "map_key_google", "opencellid_key",
        "manual_lat", "manual_lon", "map_provider", "app_theme",
        "sim_id", "radius", "show_cone", "beam_brightness", "sector_brightness",
        "sector_intensity"
    )

    fun capture(prefs: SharedPreferences): String {
        val body = JSONObject()
        for ((name, raw) in prefs.all) {
            if (name !in keys) continue
            if (raw is String || raw is Int || raw is Boolean || raw is Long) {
                body.put(name, raw)
            }
        }
        return JSONObject().put("schema", 1).put("settings", body).toString()
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        require(password.size >= 12) { "Для резервной копии нужен пароль минимум 12 символов" }
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            val data = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            try { SecretKeySpec(data, "AES") }
            finally { data.fill(0) }
        } finally { spec.clearPassword() }
    }

    fun encrypt(json: String, password: CharArray): ByteArray {
        require(json.length <= 100000) { "Слишком большой файл настроек" }
        val random = SecureRandom()
        val salt = ByteArray(saltLength).also(random::nextBytes)
        val iv = ByteArray(ivLength).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        val payload = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        return header + salt + iv + payload
    }

    fun decrypt(bytes: ByteArray, password: CharArray): String {
        require(bytes.size in (header.size + saltLength + ivLength + 16)..500000) {
            "Неверный размер резервной копии"
        }
        require(bytes.copyOfRange(0, header.size).contentEquals(header)) {
            "Неизвестный формат резервной копии"
        }
        val a = header.size
        val salt = bytes.copyOfRange(a, a + saltLength)
        val iv = bytes.copyOfRange(a + saltLength, a + saltLength + ivLength)
        val data = bytes.copyOfRange(a + saltLength + ivLength, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        val json = cipher.doFinal(data).toString(Charsets.UTF_8)
        require(JSONObject(json).optInt("schema") == 1) {
            "Неподдерживаемая версия резервной копии"
        }
        return json
    }

    /** Replace only known settings, leaving unrelated app metadata intact. */
    fun restore(prefs: SharedPreferences, json: String) {
        val body = JSONObject(json).getJSONObject("settings")
        val edit = prefs.edit()
        for (name in keys) {
            if (!body.has(name)) continue
            when (val raw = body.get(name)) {
                is String -> edit.putString(name, raw)
                is Boolean -> edit.putBoolean(name, raw)
                is Int -> edit.putInt(name, raw)
                is Long -> edit.putLong(name, raw)
            }
        }
        check(edit.commit()) { "Не удалось записать настройки" }
    }
}
