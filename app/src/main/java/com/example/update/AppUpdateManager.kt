package com.example.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object AppUpdateManager {

    const val DEFAULT_UPDATE_CONFIG_URL = "https://raw.githubusercontent.com/nhut07c1/lunar_f/main/app_version.json"

    fun getCurrentVersionCode(context: Context): Int {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }
    }

    fun getCurrentVersionName(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.1"
        } catch (e: Exception) {
            "1.1"
        }
    }

    fun resolveUpdateEndpoint(inputUrl: String?): String {
        val trimmed = inputUrl?.trim()
        if (trimmed.isNullOrEmpty()) {
            return DEFAULT_UPDATE_CONFIG_URL
        }
        // If user provided a github repo URL e.g. https://github.com/nhut07c1/lunar_f or /releases
        if (trimmed.contains("github.com") && !trimmed.contains("raw.githubusercontent.com") && !trimmed.contains("api.github.com")) {
            val clean = trimmed.removeSuffix("/").removeSuffix(".git")
            val parts = clean.split("github.com/")
            if (parts.size == 2) {
                val repoPath = parts[1].split("/")
                if (repoPath.size >= 2) {
                    val owner = repoPath[0]
                    val repo = repoPath[1]
                    // If points to releases directly, use GitHub API
                    if (clean.contains("/releases")) {
                        return "https://api.github.com/repos/$owner/$repo/releases/latest"
                    }
                    // Default to app_version.json in main branch
                    return "https://raw.githubusercontent.com/$owner/$repo/main/app_version.json"
                }
            }
        }
        return trimmed
    }

    suspend fun checkForUpdate(context: Context, customUrl: String? = null): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        val targetUrl = resolveUpdateEndpoint(customUrl)
        val currentCode = getCurrentVersionCode(context)

        try {
            val url = URL(targetUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "LichAmNoi-Android/${getCurrentVersionName(context)}")
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception("Máy chủ cập nhật phản hồi mã lỗi: $responseCode"))
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(responseBody)
            
            // Support both standard custom JSON and GitHub Releases JSON format
            val latestCode = if (json.has("versionCode")) {
                json.getInt("versionCode")
            } else if (json.has("latestVersionCode")) {
                json.getInt("latestVersionCode")
            } else {
                // Fallback: extract version number from tag or name
                val tag = json.optString("tag_name", "").replace("v", "").replace(".", "")
                tag.toIntOrNull() ?: (currentCode + 1)
            }

            val latestName = json.optString("versionName", 
                json.optString("latestVersionName", json.optString("tag_name", "1.1.0"))
            )

            val changelog = json.optString("changelog", 
                json.optString("description", json.optString("body", "• Tối ưu khởi động siêu tốc khi mở máy\n• Cải thiện hiển thị đa nhiệm 2-PIP"))
            )

            var downloadUrl = json.optString("downloadUrl", json.optString("apkUrl", ""))
            
            // If GitHub release, look into assets for .apk file
            if (downloadUrl.isEmpty() && json.has("assets")) {
                val assets = json.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }

            val releaseDate = json.optString("releaseDate", json.optString("published_at", ""))
            val isForced = json.optBoolean("isForced", false)
            val fileSizeFormatted = json.optString("fileSize", "15 MB")

            if (latestCode > currentCode) {
                val info = UpdateInfo(
                    latestVersionCode = latestCode,
                    latestVersionName = latestName,
                    changelog = changelog,
                    downloadUrl = downloadUrl,
                    releaseDate = releaseDate,
                    isForced = isForced,
                    fileSizeFormatted = fileSizeFormatted
                )
                Result.success(info)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            var currentUrl = downloadUrl
            var connection: HttpURLConnection
            var redirects = 0

            // Handle HTTP 301/302/307 redirects (common on GitHub Releases)
            while (true) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "LichAmNoi-Android/${getCurrentVersionName(context)}")
                }

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || 
                    status == HttpURLConnection.HTTP_MOVED_PERM || 
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307) {
                    val newUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (newUrl != null && redirects < 5) {
                        currentUrl = newUrl
                        redirects++
                        continue
                    }
                }
                break
            }

            val totalBytes = connection.contentLength.toLong()
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "LichAmNoi_update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalDownloaded = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead

                        val progress = if (totalBytes > 0) {
                            (totalDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0.5f
                        }
                        onProgress(progress, totalDownloaded, totalBytes)
                    }
                    output.flush()
                }
            }
            connection.disconnect()

            // Check if downloaded file is a ZIP (such as GitHub Artifacts)
            val finalApkFile = if (isZipFile(apkFile)) {
                extractApkFromZip(apkFile, updatesDir) ?: apkFile
            } else {
                apkFile
            }

            Result.success(finalApkFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isZipFile(file: File): Boolean {
        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(4)
                if (input.read(header) == 4) {
                    // ZIP magic number: 0x50 0x4B 0x03 0x04 or 0x50 0x4B 0x05 0x06
                    header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
                } else false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun extractApkFromZip(zipFile: File, outputDir: File): File? {
        return try {
            var extractedApk: File? = null
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                        val outFile = File(outputDir, "extracted_app.apk")
                        if (outFile.exists()) outFile.delete()
                        FileOutputStream(outFile).use { fos ->
                            val buffer = ByteArray(8 * 1024)
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                            fos.flush()
                        }
                        extractedApk = outFile
                        break
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            extractedApk
        } catch (e: Exception) {
            null
        }
    }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun installApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || apkFile.length() <= 0) {
            Toast.makeText(context, "Tệp cài đặt APK không tồn tại hoặc bị lỗi!", Toast.LENGTH_SHORT).show()
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(context, "Vui lòng cho phép cài đặt ứng dụng từ nguồn này để tiếp tục", Toast.LENGTH_LONG).show()
            openInstallPermissionSettings(context)
            return false
        }

        return try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Không thể mở trình cài đặt APK: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    fun getSimulatedUpdateInfo(currentVersionName: String, currentVersionCode: Int): UpdateInfo {
        return UpdateInfo(
            latestVersionCode = currentVersionCode + 1,
            latestVersionName = "1.${currentVersionCode + 1}.0",
            changelog = "• Tối ưu khởi động siêu tốc khi thiết bị vừa mở nguồn\n• Tự động kiểm tra và tải cập nhật APK 1-chạm\n• Tương thích mượt mà với mọi màn hình ô tô Android (FYT, TS10, TS18, Teyes, Bravigo, Zestech)\n• Hỗ trợ chế độ 2-PIP không giật nhấp nháy",
            downloadUrl = "https://github.com/nhut07c1/lich-am-noi/releases/download/v1.${currentVersionCode + 1}.0/app-release.apk",
            releaseDate = "2026-09-20",
            isForced = false,
            fileSizeFormatted = "14.2 MB"
        )
    }
}
