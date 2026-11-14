package com.russoturissto.towerradar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** User observations are local-only and never overwritten by network updates or map-cache clears. */
data class UserTowerDraft(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val operator: String?,
    val technology: String?,
    val heightMeters: Double?,
    val equipment: String?,
    val notes: String?
)

class UserTowerStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("tower_local_observations", Context.MODE_PRIVATE)

    companion object {
        fun validate(draft: UserTowerDraft): String? = when {
            !draft.latitude.isFinite() || draft.latitude !in -90.0..90.0 ->
                "Широта должна быть от −90 до 90"
            !draft.longitude.isFinite() || draft.longitude !in -180.0..180.0 ->
                "Долгота должна быть от −180 до 180"
            draft.name.isBlank() || draft.name.length > 90 -> "Укажите название до 90 символов"
            draft.heightMeters != null &&
                (!draft.heightMeters.isFinite() || draft.heightMeters !in 1.0..500.0) ->
                "Высота конструкции должна быть от 1 до 500 м"
            (draft.equipment?.length ?: 0) > 600 -> "Описание оборудования слишком длинное"
            (draft.notes?.length ?: 0) > 1000 -> "Примечание слишком длинное"
            else -> null
        }
    }

    @Synchronized fun all(): List<TowerSite> {
        val arr = try { JSONArray(prefs.getString("items", "[]") ?: "[]") }
            catch (_: Exception) { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            try { TowerDatabase.decode(arr.getJSONObject(i).getString("body")) }
            catch (_: Exception) { null }
        }
    }

    @Synchronized fun photoFor(id: String): File? {
        val arr = try { JSONArray(prefs.getString("items", "[]") ?: "[]") }
            catch (_: Exception) { return null }
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            if (row.optString("id") != id) continue
            val name = row.optString("photo")
            if (!name.matches(Regex("local_tower_[a-f0-9-]+\\.jpg"))) return null
            return File(context.filesDir, name).takeIf { it.isFile }
        }
        return null
    }

    @Synchronized fun add(draft: UserTowerDraft, image: Uri? = null): TowerSite {
        validate(draft)?.let { throw IllegalArgumentException(it) }
        val id = "user/" + UUID.randomUUID().toString()
        val info = listOfNotNull(
            draft.equipment?.trim()?.takeIf { it.isNotEmpty() }?.let { "Оборудование: " + it },
            draft.notes?.trim()?.takeIf { it.isNotEmpty() }?.let { "Примечания: " + it }
        ).joinToString(" · ").takeIf { it.isNotEmpty() }
        val site = TowerSite(
            id, draft.latitude, draft.longitude, draft.name.trim(),
            draft.operator?.trim()?.takeIf { it.isNotEmpty() }?.let(TowerData::operator),
            true, draft.technology?.trim()?.takeIf { it.isNotEmpty() },
            draft.heightMeters?.let { it.toString() }, "",
            source = "Пользовательская запись · только на устройстве",
            extraInfo = info
        )
        val photoName = image?.let(::copyPhoto)
        try {
            val rows = JSONArray(prefs.getString("items", "[]") ?: "[]")
            rows.put(JSONObject().put("id", id)
                .put("body", TowerDatabase.encode(site)).put("photo", photoName))
            prefs.edit().putString("items", rows.toString()).commit().also {
                if (!it) error("Не удалось сохранить запись")
            }
        } catch (e: Exception) {
            if (photoName != null) File(context.filesDir, photoName).delete()
            throw e
        }
        return site
    }

    @Synchronized fun delete(id: String): Boolean {
        val old = JSONArray(prefs.getString("items", "[]") ?: "[]")
        val updated = JSONArray()
        var removed: String? = null
        for (i in 0 until old.length()) {
            val row = old.getJSONObject(i)
            if (row.optString("id") == id) removed = row.optString("photo")
            else updated.put(row)
        }
        if (removed == null) return false
        if (!prefs.edit().putString("items", updated.toString()).commit()) return false
        removed.takeIf { it.matches(Regex("local_tower_[a-f0-9-]+\\.jpg")) }
            ?.let { File(context.filesDir, it).delete() }
        return true
    }

    private fun copyPhoto(uri: Uri): String {
        if (context.contentResolver.getType(uri)?.startsWith("image/") != true)
            throw IllegalArgumentException("Выберите фотографию")
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IllegalStateException("Не удалось прочитать фотографию")
        if (options.outWidth <= 0 || options.outHeight <= 0 ||
            options.outWidth > 30000 || options.outHeight > 30000)
            throw IllegalArgumentException("Неподдерживаемый формат изображения")
        var scale = 1
        while (options.outWidth / scale > 1400 || options.outHeight / scale > 1400) scale *= 2
        val decode = BitmapFactory.Options().apply { inSampleSize = scale }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decode)
        } ?: throw IllegalStateException("Не удалось декодировать фотографию")
        val name = "local_tower_" + UUID.randomUUID().toString() + ".jpg"
        val file = File(context.filesDir, name)
        try {
            file.outputStream().use {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it))
                    error("Сжатие фотографии не удалось")
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally { bitmap.recycle() }
        return name
    }
}
