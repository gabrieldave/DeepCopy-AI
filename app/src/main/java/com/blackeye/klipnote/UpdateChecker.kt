package com.blackeye.klipnote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Checks GitHub Releases for a newer signed APK. No crash on network/API failure. */
object UpdateChecker {

    private const val LATEST_URL =
        "https://api.github.com/repos/gabrieldave/KlipNote/releases/latest"

    private const val OFFICIAL_DOWNLOAD_PREFIX =
        "https://github.com/gabrieldave/KlipNote/"

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

    /**
     * Parses GitHub Releases JSON without Android [org.json] stubs so JVM unit tests work.
     */
    internal fun parse(json: String, localVersionName: String): Result {
        val tag = jsonStringField(json, "tag_name").orEmpty().trim()
        if (tag.isBlank()) return Result.Failed
        val remoteVersion = tag.removePrefix("v").removePrefix("V").trim()
        val local = localVersionName.trim().removePrefix("v").removePrefix("V")
        if (!isNewer(remoteVersion, local)) return Result.UpToDate

        val apkUrl = findApkAssetUrl(json)
            ?: jsonStringField(json, "html_url")?.takeIf { it.isNotBlank() }
            ?: return Result.Failed
        if (!isOfficialDownloadUrl(apkUrl)) {
            return Result.Failed
        }
        return Result.UpdateAvailable(versionName = remoteVersion, downloadUrl = apkUrl)
    }

    internal fun isOfficialDownloadUrl(url: String): Boolean =
        url.startsWith(OFFICIAL_DOWNLOAD_PREFIX)

    private fun findApkAssetUrl(json: String): String? {
        val re = Regex(
            """"name"\s*:\s*"([^"]+\.apk)"\s*,\s*"browser_download_url"\s*:\s*"([^"]+)"""",
            RegexOption.IGNORE_CASE
        )
        // GitHub may order keys either way; also try reverse order.
        val reAlt = Regex(
            """"browser_download_url"\s*:\s*"([^"]+)"\s*,\s*"name"\s*:\s*"([^"]+\.apk)"""",
            RegexOption.IGNORE_CASE
        )
        re.find(json)?.let { m ->
            val url = m.groupValues[2]
            if (url.isNotBlank()) return url
        }
        reAlt.find(json)?.let { m ->
            val url = m.groupValues[1]
            if (url.isNotBlank()) return url
        }
        // Looser: first browser_download_url ending in .apk
        Regex(""""browser_download_url"\s*:\s*"(https://[^"]+\.apk)"""", RegexOption.IGNORE_CASE)
            .find(json)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return null
    }

    private fun jsonStringField(json: String, key: String): String? {
        val re = Regex(""""$key"\s*:\s*"((?:\\.|[^"\\])*)"""")
        return re.find(json)?.groupValues?.getOrNull(1)?.replace("\\\"", "\"")
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
