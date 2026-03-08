package com.openclaw.android.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0,
    @SerialName("content_type") val contentType: String? = null
)

data class UpdateInfo(
    val versionName: String,
    val releaseNotes: String?,
    val downloadUrl: String,
    val htmlUrl: String
)

class AppUpdater(private val context: Context) {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val RELEASES_URL =
            "https://api.github.com/repos/shashanksaxena-tz/openClaw-android/releases/latest"
    }

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val release = json.decodeFromString<GitHubRelease>(body)

            val remoteVersion = parseVersion(release.tagName) ?: return@withContext null
            val currentVersion = getCurrentVersion() ?: return@withContext null

            if (compareVersions(remoteVersion, currentVersion) <= 0) {
                return@withContext null
            }

            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                ?: return@withContext null

            UpdateInfo(
                versionName = release.tagName,
                releaseNotes = release.body,
                downloadUrl = apkAsset.browserDownloadUrl,
                htmlUrl = release.htmlUrl
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun downloadAndInstall(url: String, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IllegalStateException("Download failed with code ${response.code}")
            }

            val responseBody = response.body
                ?: throw IllegalStateException("Empty response body")

            val totalBytes = responseBody.contentLength()
            val updateDir = File(context.cacheDir, "apk_updates")
            updateDir.mkdirs()
            val apkFile = File(updateDir, "update.apk")

            responseBody.byteStream().use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Long = 0
                    var read: Int

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (totalBytes > 0) {
                            onProgress(bytesRead.toFloat() / totalBytes.toFloat())
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                installApk(apkFile)
            }
        }

    private fun installApk(apkFile: File) {
        val authority = "${context.packageName}.fileprovider"
        val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun getCurrentVersion(): Triple<Int, Int, Int>? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            parseVersion(packageInfo.versionName ?: return null)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseVersion(tag: String): Triple<Int, Int, Int>? {
        val cleaned = tag.removePrefix("v")
        val parts = cleaned.split(".")
        if (parts.size < 3) return null
        return try {
            Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun compareVersions(
        a: Triple<Int, Int, Int>,
        b: Triple<Int, Int, Int>
    ): Int {
        if (a.first != b.first) return a.first.compareTo(b.first)
        if (a.second != b.second) return a.second.compareTo(b.second)
        return a.third.compareTo(b.third)
    }
}
