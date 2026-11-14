package com.russoturissto.towerradar

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * In-app OTA: downloads to app space, validates GitHub digest/package/version/
 * installed signing certificate, then delegates user-approved install to Android.
 */
object TowerApkInstaller {
    data class Progress(val state: Int, val downloaded: Long, val total: Long, val reason: Int)
    private const val PREFS = "towerradar_apk_update"
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun manager(c: Context) = c.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private fun file(c: Context, release: TowerRelease): File =
        File(c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "TowerRadar-v${release.version}.apk")

    fun status(c: Context, release: TowerRelease): Progress? {
        if (prefs(c).getString("version", "") != release.version) return null
        val id = prefs(c).getLong("id", -1L)
        if (id < 0) return null
        val cursor = manager(c).query(DownloadManager.Query().setFilterById(id)) ?: return null
        return cursor.use {
            if (!it.moveToFirst()) return null
            fun column(key: String) = it.getColumnIndexOrThrow(key)
            Progress(it.getInt(column(DownloadManager.COLUMN_STATUS)),
                it.getLong(column(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                it.getLong(column(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                it.getInt(column(DownloadManager.COLUMN_REASON)))
        }
    }

    fun download(c: Context, release: TowerRelease): Long {
        require(release.digest?.matches(Regex("sha256:[0-9a-fA-F]{64}")) == true) {
            "У релиза отсутствует проверенная SHA-256"
        }
        val existing = status(c, release)
        if (existing?.state == DownloadManager.STATUS_RUNNING ||
            existing?.state == DownloadManager.STATUS_PENDING ||
            existing?.state == DownloadManager.STATUS_SUCCESSFUL) return prefs(c).getLong("id", -1L)
        val output = file(c, release)
        output.delete()
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("TowerRadar " + release.version)
            .setDescription("Проверяемое обновление APK")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setDestinationInExternalFilesDir(c, Environment.DIRECTORY_DOWNLOADS, output.name)
        val id = manager(c).enqueue(request)
        prefs(c).edit().putLong("id", id).putString("version", release.version).apply()
        return id
    }

    private fun versionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28)
        info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signerHashes(info: PackageInfo): Set<String> {
        val signers = if (Build.VERSION.SDK_INT >= 28)
            info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        else info.signatures?.toList().orEmpty()
        return signers.map {
            val bytes = MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
            bytes.joinToString("") { b -> "%02x".format(b.toInt() and 0xff) }
        }.toSet()
    }

    fun verifiedFile(c: Context, release: TowerRelease): File {
        val apk = file(c, release)
        if (!apk.isFile || apk.length() != release.apkBytes ||
            apk.length() !in 1_048_576L..100_000_000L)
            throw SecurityException("Размер APK не совпадает с релизом")
        val sha = MessageDigest.getInstance("SHA-256")
        FileInputStream(apk).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                sha.update(buffer, 0, n)
            }
        }
        val actual = sha.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        if (release.digest?.lowercase() != "sha256:$actual") {
            apk.delete()
            throw SecurityException("SHA-256 загруженного APK не совпадает")
        }
        @Suppress("DEPRECATION")
        val flag = if (Build.VERSION.SDK_INT >= 28)
            PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val archive = c.packageManager.getPackageArchiveInfo(apk.absolutePath, flag)
            ?: throw SecurityException("Android не распознал APK")
        @Suppress("DEPRECATION")
        val installed = c.packageManager.getPackageInfo(c.packageName, flag)
        if (archive.packageName != c.packageName ||
            archive.versionName != release.version ||
            versionCode(archive) <= versionCode(installed))
            throw SecurityException("Неверный пакет или номер версии")
        if (signerHashes(archive).isEmpty() || signerHashes(archive) != signerHashes(installed))
            throw SecurityException("Сертификат подписи не совпадает")
        return apk
    }

    fun canInstall(c: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || c.packageManager.canRequestPackageInstalls()

    fun permissionIntent(c: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + c.packageName))

    fun installationIntent(c: Context, apk: File): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(FileProvider.getUriForFile(c,
                c.packageName + ".updates", apk),
                "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
}
