package com.riyadm.apkrepacker.update

import android.content.Context
import com.riyadm.apkrepacker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Looks for a newer release of the app on GitHub and downloads its APK. Every push to main
 * publishes a release named v<major>.<minor>.<patch> with one signed APK and notes that carry the
 * APK's SHA-256 and the list of changes (see .github/workflows/release.yml).
 */
object UpdateChecker {

    private const val LATEST = "https://api.github.com/repos/riyadmondol2006/ApkRepacker-Revived/releases/latest"
    private const val TIMEOUT_MS = 15_000

    /** A published release: [notes] is the list of changes, ready to show. */
    data class Release(
        val version: String,
        val versionCode: Long,
        val notes: String,
        val apkUrl: String?,
        val apkSize: Long,
        val sha256: String?,
        val pageUrl: String,
    )

    /** The newest release when it is newer than this app, else null. */
    @Throws(IOException::class)
    suspend fun newerRelease(): Release? = withContext(Dispatchers.IO) {
        val release = parse(JSONObject(get(LATEST)))
        release.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    }

    /**
     * Downloads [release]'s APK into the cache and checks it against the SHA-256 in the release
     * notes. [onProgress] gets the bytes read and the total (0 when unknown).
     */
    @Throws(IOException::class)
    suspend fun download(context: Context, release: Release, onProgress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        val url = release.apkUrl ?: throw IOException("The release has no APK")
        val dir = File(context.externalCacheDir ?: context.cacheDir, "updates").apply { mkdirs() }
        // Only the update being installed is kept.
        dir.listFiles()?.forEach { it.delete() }
        val apk = File(dir, "ApkRepackerRevived-v${release.version}.apk")
        val part = File(dir, apk.name + ".part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val connection = open(url, api = false)
            try {
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            done += n
                            onProgress(done, total)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (release.sha256 != null && !release.sha256.equals(actual, ignoreCase = true)) {
                throw IOException("The downloaded APK is damaged (SHA-256 does not match)")
            }
            if (!part.renameTo(apk)) throw IOException("Can't save ${apk.name}")
            apk
        } finally {
            part.delete()
        }
    }

    /** versionCode of a version name, as the release workflow and app/build.gradle compute it. */
    fun versionCodeOf(version: String): Long {
        val parts = Regex("\\d+").findAll(version).map { it.value.toLong() }.toList() + listOf(0L, 0L, 0L)
        return parts[0] * 1_000_000 + parts[1] * 1_000 + parts[2]
    }

    private fun parse(json: JSONObject): Release {
        val version = json.getString("tag_name").removePrefix("v")
        val body = json.optString("body")
        val apk = json.optJSONArray("assets")?.let { assets ->
            (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") }
        }
        return Release(
            version = version,
            versionCode = versionCodeOf(version),
            notes = changesOf(body),
            apkUrl = apk?.optString("browser_download_url")?.takeIf { it.isNotEmpty() },
            apkSize = apk?.optLong("size") ?: 0L,
            sha256 = Regex("SHA-256\\*\\*\\s*`([0-9a-fA-F]{64})`").find(body)?.groupValues?.get(1),
            pageUrl = json.optString("html_url"),
        )
    }

    /** The "### Changes" list of the notes, without markdown and commit hashes. */
    private fun changesOf(body: String): String {
        val lines = body.lines()
        val start = lines.indexOfFirst { it.trim().startsWith("### Changes") }
        val changes = if (start >= 0) lines.drop(start + 1) else lines
        return changes
            .map { it.trim() }
            .filter { it.startsWith("- ") }
            .map { "• " + it.removePrefix("- ").replace(Regex("\\s*\\([0-9a-f]{7,40}\\)$"), "").replace("`", "") }
            .take(12)
            .joinToString("\n")
    }

    private fun get(url: String): String {
        val connection = open(url)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("GitHub answered ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, api: Boolean = true): HttpURLConnection {
        // GitHub sends release downloads through a redirect to its file host (https to https, which
        // HttpURLConnection follows by itself).
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true
        if (api) connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "ApkRepackerRevived/${BuildConfig.VERSION_NAME}")
        return connection
    }
}
