package com.chris.financeapp.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class AppUpdater(
    private val context: Context,
    private val client: OkHttpClient,
    private val repoOwner: String,
    private val repoName: String
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val parser = Json { ignoreUnknownKeys = true }

    interface UpdateCheckCallback {
        fun onUpdateAvailable(newVersion: String, downloadUrl: String, assetName: String, sizeBytes: Long)
        fun onNoUpdateAvailable()
        fun onError(error: String)
    }

    interface DownloadCallback {
        fun onProgress(percentage: Int)
        fun onCompleted(apkFile: File)
        fun onError(error: String)
    }

    // Queries the latest GitHub release anonymously
    fun checkForUpdates(currentVersion: String, callback: UpdateCheckCallback) {
        if (repoOwner.isEmpty() || repoName.isEmpty()) {
            callback.onError("GitHub repository owner or name is not configured.")
            return
        }

        val url = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "AndroidFinanceApp-Updater")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback.onError("Network error: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        mainHandler.post { callback.onError("Update check failed: Code ${response.code}") }
                        return
                    }
                    val bodyStr = response.body?.string() ?: ""
                    try {
                        val jsonElement = parser.parseToJsonElement(bodyStr)
                        val tagName = jsonElement.jsonObject["tag_name"]?.jsonPrimitive?.contentOrNull ?: ""
                        
                        val latestVer = tagName.removePrefix("v").trim()
                        val currentVer = currentVersion.removePrefix("v").trim()

                        if (latestVer.isNotEmpty() && latestVer != currentVer) {
                            val assets = jsonElement.jsonObject["assets"]?.jsonArray ?: return
                            var foundApk = false
                            for (asset in assets) {
                                val assetObj = asset.jsonObject
                                val name = assetObj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                                if (name.endsWith(".apk")) {
                                    val downloadUrl = assetObj["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: ""
                                    val size = assetObj["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                                    mainHandler.post {
                                        callback.onUpdateAvailable(latestVer, downloadUrl, name, size)
                                    }
                                    foundApk = true
                                    break
                                }
                            }
                            if (!foundApk) {
                                mainHandler.post { callback.onError("No APK file found in latest release assets.") }
                            }
                        } else {
                            mainHandler.post { callback.onNoUpdateAvailable() }
                        }
                    } catch (e: Exception) {
                        mainHandler.post { callback.onError("Error parsing response: ${e.message}") }
                    }
                }
            }
        })
    }

    // Downloads the public release asset anonymously
    fun downloadUpdateApk(downloadUrl: String, assetName: String, callback: DownloadCallback) {
        val request = Request.Builder()
            .url(downloadUrl)
            .addHeader("User-Agent", "AndroidFinanceApp-Updater")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback.onError("Download failed: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    mainHandler.post { callback.onError("Download error: Code ${response.code}") }
                    return
                }

                val body = response.body
                if (body == null) {
                    mainHandler.post { callback.onError("Empty response body.") }
                    return
                }

                val targetFile = File(context.externalCacheDir, assetName)
                if (targetFile.exists()) targetFile.delete()

                try {
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    val contentLength = body.contentLength()

                    body.byteStream().use { inputStream ->
                        FileOutputStream(targetFile).use { outputStream ->
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                if (contentLength > 0) {
                                    val progress = ((totalBytesRead * 100) / contentLength).toInt()
                                    mainHandler.post { callback.onProgress(progress) }
                                }
                            }
                        }
                    }
                    mainHandler.post { callback.onCompleted(targetFile) }
                } catch (e: Exception) {
                    mainHandler.post { callback.onError("Write error: ${e.message}") }
                }
            }
        })
    }

    // Launches the Android OS Package Installer
    fun installApk(apkFile: File) {
        val authority = "com.chris.financeapp.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, apkFile)
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
