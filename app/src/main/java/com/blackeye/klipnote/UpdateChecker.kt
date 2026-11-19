package com.blackeye.klipnote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Checks GitHub Releases for a newer signed APK. No crash on network/API failure. */
object UpdateChecker {

    private const val LATEST_URL =
        "https://api.github.com/repos/gabrieldave/KlipNote/releases/latest"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    sealed class Result {
        data class UpdateAvailable(
            val versionName: String,
            val downloadUrl: String
        ) : Result()

        data object UpToDate : Result()
        data object Failed : Result()
    }

    suspend fun checkLatest(localVersionName: String): Result = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "KlipNote-Android")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching Result.Failed
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@runCatching Result.Failed
                parse(body, localVersionName)
            }
        }.getOrElse { Result.Failed }
    }

    internal fun parse(json: String, localVersionName: String): Result {
        val root = JSONObject(json)
        val tag = root.optString("tag_name").orEmpty().trim()
        if (tag.isBlank()) return Result.Failed
        val remoteVersion = tag.removePrefix("v").removePrefix("V").trim()
        val local = localVersionName.trim().removePrefix("v").removePrefix("V")
        if (!isNewer(remoteVersion, local)) return Result.UpToDate

        val apkUrl = findApkAssetUrl(root)
            ?: root.optString("html_url").takeIf { it.isNotBlank() }
            ?: return Result.Failed
        return Result.UpdateAvailable(versionName = remoteVersion, downloadUrl = apkUrl)
    }

    private fun findApkAssetUrl(root: JSONObject): String? {
        val assets = root.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name").orEmpty()
            if (name.endsWith(".apk", ignoreCase = true)) {
                val url = asset.optString("browser_download_url").orEmpty()
                if (url.isNotBlank()) return url
            }
        }
        return null
    }

    /** True if [remote] is strictly newer than [local] (dot-separated numeric parts). */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.', '-', '_').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val l = local.split('.', '-', '_').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val n = maxOf(r.size, l.size)
        for (i in 0 until n) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }
}
