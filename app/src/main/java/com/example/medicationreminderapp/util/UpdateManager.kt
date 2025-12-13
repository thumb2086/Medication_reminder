package com.example.medicationreminderapp.util

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.example.medicationreminderapp.BuildConfig
import com.example.medicationreminderapp.R
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.math.max

class UpdateManager(private val context: Context) {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val repoOwner = "thumb2086"
    private val repoName = "Medication_reminder"

    data class UpdateInfo(
        val version: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val isNightly: Boolean
    )

    suspend fun checkForUpdates(): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                
                // Determine default channel based on BuildConfig
                val defaultChannel = if (BuildConfig.UPDATE_CHANNEL.isNullOrEmpty()) "main" else BuildConfig.UPDATE_CHANNEL
                
                // Get user selected channel, fallback to the build's channel
                val selectedChannel = prefs.getString("update_channel", defaultChannel) ?: defaultChannel
                val currentChannel = BuildConfig.UPDATE_CHANNEL
                
                val isChannelSwitch = selectedChannel != currentChannel
                Log.d("UpdateManager", "Checking for updates on channel: $selectedChannel (Switch: $isChannelSwitch)")

                val isStable = selectedChannel == "main" || selectedChannel == "master" || selectedChannel == "stable"

                if (isStable) {
                    checkStableUpdates(isChannelSwitch)
                } else {
                    // Check both Dynamic (Dev/Nightly) and Stable channels
                    val devUpdate = checkDynamicChannelUpdates(selectedChannel, isChannelSwitch)
                    val stableUpdate = checkStableUpdates(false) // Don't force, just check for newer stable

                    // Logic to pick the best update:
                    // 1. If both exist, pick the newer one.
                    // 2. Note: isNewerVersion(A, B) returns true if B > A.
                    if (devUpdate != null && stableUpdate != null) {
                        if (isNewerVersion(devUpdate.version, stableUpdate.version)) {
                            Log.d("UpdateManager", "Stable update (${stableUpdate.version}) is newer than Dev update (${devUpdate.version})")
                            stableUpdate
                        } else {
                            Log.d("UpdateManager", "Dev update (${devUpdate.version}) is newer or equal to Stable update (${stableUpdate.version})")
                            devUpdate
                        }
                    } else {
                        // devUpdate could be null, so check if stableUpdate is not null
                        devUpdate ?: stableUpdate
                    }
                }
            } catch (e: Exception) {
                Log.e("UpdateManager", "Error checking for updates", e)
                null
            }
        }
    }

    private fun checkDynamicChannelUpdates(channel: String, force: Boolean): UpdateInfo? {
        // Fetch JSON from GitHub Pages
        val jsonUrl = "https://$repoOwner.github.io/$repoName/update_$channel.json"
        
        val request = Request.Builder()
            .url(jsonUrl)
            .cacheControl(CacheControl.FORCE_NETWORK) // Always fetch latest
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.e("UpdateManager", "Failed to fetch channel config: ${response.code}")
            return null
        }

        val responseBody = response.body
        val jsonStr = responseBody.string() // response.body is not null if isSuccessful
        val json = gson.fromJson(jsonStr, JsonObject::class.java)

        // Parse JSON
        val remoteVersionCode = json.get("versionCode").asInt
        val latestVersionName = json.get("latestVersion").asString
        val downloadUrl = json.get("url").asString
        val releaseNotes = json.get("releaseNotes").asString

        // Compare logic:
        // 1. If remote VersionCode > local VersionCode, update is available.
        // 2. If it's a forced channel switch, allow update.
        if (remoteVersionCode > BuildConfig.VERSION_CODE || force) {
            return UpdateInfo(latestVersionName, downloadUrl, releaseNotes, true)
        } 
        
        return null
    }

    private fun checkStableUpdates(force: Boolean): UpdateInfo? {
        val url = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
        val request = Request.Builder().url(url).build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null

        val responseBody = response.body
        val jsonStr = responseBody.string()
        val json = gson.fromJson(jsonStr, JsonObject::class.java)

        val tagName = json.get("tag_name").asString
        val releaseNotes = json.get("body").asString
        
        // Check for assets
        val assets = json.getAsJsonArray("assets")
        if (assets.size() == 0) return null
        
        var apkUrl = ""
        
        for (asset in assets) {
            val assetObj = asset.asJsonObject
            val name = assetObj.get("name").asString
            if (name.endsWith(".apk")) {
                apkUrl = assetObj.get("browser_download_url").asString
                break
            }
        }
        
        if (apkUrl.isEmpty()) return null

        val remoteVersion = tagName.removePrefix("v")
        val currentVersionNormalized = BuildConfig.VERSION_NAME.replace(" ", "-")

        if (isNewerVersion(currentVersionNormalized, remoteVersion) || force) {
             return UpdateInfo(remoteVersion, apkUrl, releaseNotes, false)
        }
        return null
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
        
        // Fallback or secondary check using commit count if available in version string
        val localCount = getCommitCount(local)
        val remoteCount = getCommitCount(remote)
        
        if (localCount != null && remoteCount != null) {
            return remoteCount > localCount
        }
        
        return false
    }

    private fun getCommitCount(version: String): Int? {
        val parts = version.split("-", " ")
        val lastPart = parts.lastOrNull()
        return lastPart?.toIntOrNull()
    }

    fun downloadAndInstall(url: String, fileName: String) {
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            Toast.makeText(context, "警告: 正在使用除錯版本，更新可能會因簽名不符而失敗。", Toast.LENGTH_LONG).show()
        }

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

        val existingFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (existingFile.exists()) {
            existingFile.delete()
        }

        val request = DownloadManager.Request(url.toUri())
            .setTitle(context.getString(R.string.downloading_update))
            .setDescription(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        Toast.makeText(context, R.string.downloading_update, Toast.LENGTH_SHORT).show()

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
                                var downloadedFile: File? = null

                                if (uriIndex != -1) {
                                    val uriString = cursor.getString(uriIndex)
                                    if (uriString != null) {
                                        val fileUri = uriString.toUri()
                                        if (fileUri.scheme == "file") {
                                            val path = fileUri.path
                                            if (path != null) {
                                                downloadedFile = File(path)
                                            }
                                        }
                                    }
                                }

                                if (downloadedFile == null || !downloadedFile.exists()) {
                                    Log.w("UpdateManager", "Could not resolve file via URI. Falling back to hardcoded path.")
                                    downloadedFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                                }

                                if (downloadedFile.exists()) {
                                    Log.d("UpdateManager", "Install target found: ${downloadedFile.absolutePath}")
                                    installApk(downloadedFile)
                                } else {
                                    Log.e("UpdateManager", "APK file not found after download success reported.")
                                    Toast.makeText(context, "更新檔案未找到", Toast.LENGTH_SHORT).show()
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
                        // Ignore
                    }
                }
            }
        }
        
        ContextCompat.registerReceiver(
            context,
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun installApk(file: File) {
        try {
            if (!file.exists()) {
                Toast.makeText(context, "安裝檔案未找到", Toast.LENGTH_SHORT).show()
                return
            }
            
            if (file.length() < 1024) {
                 Toast.makeText(context, "安裝檔案損毀", Toast.LENGTH_SHORT).show()
                 return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

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
