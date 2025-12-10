package com.example.medicationreminderapp.util

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.example.medicationreminderapp.BuildConfig
import com.example.medicationreminderapp.R
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.math.max

class UpdateManager(private val context: Context) {

    private val client = OkHttpClient()
    private val gson = Gson()
    // Updated repo owner to match the active fork
    private val repoOwner = "thumb2086"
    private val repoName = "Medication_reminder"

    data class UpdateInfo(
        val version: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val isNightly: Boolean
    )

    suspend fun checkForUpdates(channel: String): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                // Official (Stable) -> Fetch latest release (tags starting with v)
                // Dev -> Fetch release with tag 'latest-dev'
                // Nightly -> Fetch release with tag 'nightly'

                val url = when (channel) {
                    "stable" -> "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
                    "dev" -> "https://api.github.com/repos/$repoOwner/$repoName/releases/tags/latest-dev"
                    "nightly" -> "https://api.github.com/repos/$repoOwner/$repoName/releases/tags/nightly"
                    else -> "https://api.github.com/repos/$repoOwner/$repoName/releases/latest" // Default to stable
                }

                val request = Request.Builder()
                    .url(url)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e("UpdateManager", "Failed to fetch updates: ${response.code}")
                    return@withContext null
                }

                val jsonStr = response.body.string()
                val json = gson.fromJson(jsonStr, JsonObject::class.java)

                val tagName = json.get("tag_name").asString
                val releaseNotes = json.get("body").asString
                
                val assets = json.getAsJsonArray("assets")
                if (assets.size() == 0) return@withContext null
                
                // Find .apk asset and extract version from filename
                var apkUrl = ""
                var remoteFilename = ""
                
                for (asset in assets) {
                    val assetObj = asset.asJsonObject
                    val name = assetObj.get("name").asString
                    if (name.endsWith(".apk")) {
                        apkUrl = assetObj.get("browser_download_url").asString
                        remoteFilename = name
                        break
                    }
                }
                
                if (apkUrl.isEmpty()) return@withContext null

                // Determine Remote Version
                val remoteVersion = if (channel == "stable") {
                    // For Stable, rely on the Tag Name as the source of truth
                    tagName.removePrefix("v")
                } else {
                    // For Dev/Nightly, extract from filename or fallback
                    val prefix = "MedicationReminder-"
                    val suffix = ".apk"
                    if (remoteFilename.startsWith(prefix) && remoteFilename.endsWith(suffix)) {
                        remoteFilename.removePrefix(prefix).removeSuffix(suffix)
                    } else {
                        tagName.removePrefix("v")
                    }
                }

                val currentVersion = BuildConfig.VERSION_NAME
                val currentVersionNormalized = currentVersion.replace(" ", "-")

                Log.d("UpdateManager", "Channel: $channel, Remote: $remoteVersion, Local: $currentVersionNormalized")

                // Check for update using semantic versioning logic
                val isUpdateAvailable = isNewerVersion(currentVersionNormalized, remoteVersion)

                if (!isUpdateAvailable) {
                    return@withContext null
                }

                UpdateInfo(remoteVersion, apkUrl, releaseNotes, channel != "stable")

            } catch (e: Exception) {
                Log.e("UpdateManager", "Error checking for updates", e)
                null
            }
        }
    }
    
    private fun isNewerVersion(local: String, remote: String): Boolean {
        if (local == remote) return false

        val localBase = local.substringBefore("-")
        val remoteBase = remote.substringBefore("-")
        
        if (localBase != remoteBase) {
            val localParts = localBase.split(".").map { it.toIntOrNull() ?: 0 }
            val remoteParts = remoteBase.split(".").map { it.toIntOrNull() ?: 0 }
            val length = max(localParts.size, remoteParts.size)

            for (i in 0 until length) {
                val l = localParts.getOrElse(i) { 0 }
                val r = remoteParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
        }
        
        val localCount = getCommitCount(local)
        val remoteCount = getCommitCount(remote)
        
        if (localCount != null && remoteCount != null) {
            return remoteCount > localCount
        }
        
        if (localCount == null && remoteCount != null) {
            return false
        }
        
        if (localCount != null) {
            return true
        }
        
        return false
    }

    private fun getCommitCount(version: String): Int? {
        val parts = version.split("-")
        return parts.lastOrNull()?.toIntOrNull()
    }

    fun downloadAndInstall(url: String, fileName: String) {
        // Warning if updating from Debug build (signature mismatch risk)
        if (BuildConfig.DEBUG) {
            Toast.makeText(context, "警告: 正在使用除錯版本，更新可能會因簽名不符而失敗。", Toast.LENGTH_LONG).show()
        }

        // Check for INSTALL_PACKAGES permission (Android 8+)
        if (!context.packageManager.canRequestPackageInstalls()) {
             AlertDialog.Builder(context)
                .setTitle("需要安裝權限")
                .setMessage("為了自動安裝更新，請允許應用程式安裝未知來源的應用程式。")
                .setPositiveButton("前往設定") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    intent.data = "package:${context.packageName}".toUri()
                    context.startActivity(intent)
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }

        // Delete any existing file with the same name to avoid DownloadManager renaming it
        val existingFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (existingFile.exists()) {
            existingFile.delete()
        }

        val request = DownloadManager.Request(url.toUri())
            .setTitle(context.getString(R.string.downloading_update))
            .setDescription(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setMimeType("application/vnd.android.package-archive") // Explicitly set MIME type
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        Toast.makeText(context, R.string.downloading_update, Toast.LENGTH_SHORT).show()

        // Register receiver for download complete
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    if (cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        
                        if (statusIndex != -1) {
                            val status = cursor.getInt(statusIndex)
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                // Retrieve the actual file location from DownloadManager
                                if (uriIndex != -1) {
                                    val uriString = cursor.getString(uriIndex)
                                    val fileUri = uriString.toUri()
                                    // Normally fileUri is file:///...
                                    val path = fileUri.path
                                    if (path != null) {
                                        val downloadedFile = File(path)
                                        installApk(downloadedFile)
                                    } else {
                                        // Fallback to manual path if URI path is null (unlikely for file://)
                                        installApk(File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName))
                                    }
                                } else {
                                     // Fallback
                                     installApk(File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName))
                                }
                            } else {
                                Log.e("UpdateManager", "Download failed with status: $status")
                                Toast.makeText(context, "更新下載失敗", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    cursor.close()

                    try {
                        context.unregisterReceiver(this)
                    } catch (_: IllegalArgumentException) {
                        // Ignore if already unregistered
                    }
                }
            }
        }
        ContextCompat.registerReceiver(context, onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun installApk(file: File) {
        try {
            if (!file.exists()) {
                Log.e("UpdateManager", "APK file not found at ${file.absolutePath}")
                Toast.makeText(context, "安裝檔案未找到", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Check file size (e.g., < 1KB likely implies a corrupt file or an HTML error page)
            if (file.length() < 1024) {
                 Log.e("UpdateManager", "APK file is too small (${file.length()} bytes). Possible download error.")
                 Toast.makeText(context, "安裝檔案損毀", Toast.LENGTH_SHORT).show()
                 return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            Log.d("UpdateManager", "Installing APK from $uri")

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to install APK", e)
            Toast.makeText(context, "安裝失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}