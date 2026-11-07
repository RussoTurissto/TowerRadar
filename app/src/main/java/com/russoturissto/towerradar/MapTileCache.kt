package com.russoturissto.towerradar

import android.app.Activity
import android.content.Context
import org.maplibre.android.offline.OfflineManager

/**
 * Ambient caching of tiles that were actually displayed, not bulk downloads.
 *
 * MapLibre uses a shared on-device SQLite cache. Sources whose terms don't
 * authorize persistent tile caching are marked volatile in MapSources.
 * Yandex permits temporary caching for no longer than 30 days, so we clear
 * shared ambient cache at most 29 days after the previous complete purge.
 */
class MapTileCache(private val activity: Activity) {
    private val prefs = activity.getSharedPreferences("tower_map_cache", Context.MODE_PRIVATE)
    private val offline: OfflineManager get() = OfflineManager.getInstance(activity)

    companion object {
        private const val FIRST_COMPLIANT_CACHE_SCHEMA = 1
        private const val MAX_AGE_MS = 29L * 24L * 60L * 60L * 1000L
        val limitsMb = intArrayOf(100, 250, 500)

        fun diskCacheAllowed(provider: MapProvider): Boolean =
            provider.keyPref == null || provider.id == "yandex"
    }

    fun limitMb(): Int = prefs.getInt("cache_limit_mb", 250)
        .takeIf { it in limitsMb } ?: 250

    /** Call immediately after MapLibre.getInstance, before creating MapView. */
    fun initialize() {
        applyLimit()
        purgeOldTilesIfNeeded()
    }

    fun changeLimit(mb: Int, done: (Boolean, String) -> Unit) {
        if (mb !in limitsMb) {
            done(false, "Недопустимый лимит кеша")
            return
        }
        prefs.edit().putInt("cache_limit_mb", mb).apply()
        applyLimit(done)
    }

    private fun applyLimit(done: ((Boolean, String) -> Unit)? = null) {
        val bytes = limitMb().toLong() * 1024L * 1024L
        offline.setMaximumAmbientCacheSize(bytes, object : OfflineManager.FileSourceCallback {
            override fun onSuccess() {
                done?.let {
                    activity.runOnUiThread {
                        it(true, "Лимит кеша: " + limitMb() + " МБ")
                    }
                }
            }

            override fun onError(message: String) {
                done?.let {
                    activity.runOnUiThread {
                        it(false, "Не удалось изменить лимит кеша")
                    }
                }
            }
        })
    }

    private fun purgeOldTilesIfNeeded() {
        val now = System.currentTimeMillis()
        val updated = prefs.getLong("last_cache_purge", 0L)
        val schema = prefs.getInt("cache_schema", 0)
        if (schema >= FIRST_COMPLIANT_CACHE_SCHEMA &&
            now >= updated && now - updated < MAX_AGE_MS) return

        // Migration from v0.7 clears incorrectly scaled old Yandex imagery
        // and any previously cached third-party content before applying
        // the new source-specific volatile flags.
        clear { _, _ -> Unit }
    }

    fun clear(done: (Boolean, String) -> Unit) {
        offline.clearAmbientCache(object : OfflineManager.FileSourceCallback {
            override fun onSuccess() {
                prefs.edit()
                    .putLong("last_cache_purge", System.currentTimeMillis())
                    .putInt("cache_schema", FIRST_COMPLIANT_CACHE_SCHEMA)
                    .apply()
                activity.runOnUiThread {
                    done(true, "Кеш просмотренных карт очищен")
                }
            }

            override fun onError(message: String) {
                activity.runOnUiThread {
                    done(false, "Не удалось очистить кеш карт")
                }
            }
        })
    }
}
