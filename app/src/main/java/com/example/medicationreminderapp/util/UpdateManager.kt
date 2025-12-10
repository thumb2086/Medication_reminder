package com.example.medicationreminderapp.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
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
                    // This handles cases where the attached artifact might have an old filename
                    // but the release itself is tagged as new (e.g. v1.2.0).
                    tagName.removePrefix("v")
                } else {
                    // For Dev/Nightly, the tag is static (latest-dev / nightly),
                    // so we MUST extract the version from the filename.
                    val prefix = "MedicationReminder-"
                    val suffix = ".apk"
                    if (remoteFilename.startsWith(prefix) && remoteFilename.endsWith(suffix)) {
                        // Extract version from filename (e.g. "1.2.0-nightly-161")
                        remoteFilename.removePrefix(prefix).removeSuffix(suffix)
                    } else {
                        // Fallback to tag name (likely "nightly" or "latest-dev")
                        // This will likely fail the version comparison, which is safer than a false positive.
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
    
    /**
     * Compares two version strings to determine if the remote version is newer.
     * Handles standard SemVer (1.2.0) and nightly builds (1.2.0-nightly-161).
     * Returns true if remote is newer than local.
     */
    private fun isNewerVersion(local: String, remote: String): Boolean {
        // If exact match, obviously not newer
        if (local == remote) return false

        // 1. Try to compare base versions (X.Y.Z)
        // Extract "1.2.0" from "1.2.0-nightly-161" or "1.2.0"
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
            // Base versions are numerically equal (e.g. 1.2 vs 1.2.0), treat as equal for now
        }
        
        // 2. If base versions are equal, check for build metadata (commit counts)
        // Scenario: Local 1.2.0-nightly-160 vs Remote 1.2.0-nightly-161
        val localCount = getCommitCount(local)
        val remoteCount = getCommitCount(remote)
        
        if (localCount != null && remoteCount != null) {
            return remoteCount > localCount
        }
        
        // Scenario: Local 1.2.0 (Stable) vs Remote 1.2.0-nightly-161
        // Stable (no suffix) is usually considered finalized compared to nightly (with suffix).
        // Return false to avoid downgrading to a nightly of the same base version.
        if (localCount == null && remoteCount != null) {
            return false
        }
        
        // Scenario: Local 1.2.0-nightly-161 vs Remote 1.2.0 (Stable)
        // Remote is Stable (no suffix). Local is Nightly.
        // If base versions match, Stable is preferred.
        if (localCount != null) {
            // Here remoteCount implies null because of previous checks
            return true
        }
        
        // Fallback
        return false
    }

    private fun getCommitCount(version: String): Int? {
        // Expected format: ...-nightly-<digits> or ...-<digits>
        // We try to find the last numeric component
        val parts = version.split("-")
        return parts.lastOrNull()?.toIntOrNull()
    }

    fun downloadAndInstall(url: String, fileName: String) {
        // Delete any existing file with the same name to avoid DownloadManager renaming it (e.g. file-1.apk)
        val existingFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (existingFile.exists()) {
            existingFile.delete()
        }

        val request = DownloadManager.Request(url.toUri())
            .setTitle(context.getString(R.string.downloading_update))
            .setDescription(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
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
                        if (statusIndex != -1) {
                            val status = cursor.getInt(statusIndex)
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                installApk(fileName)
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

    private fun installApk(fileName: String) {
        try {
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (!file.exists()) {
                Log.e("UpdateManager", "APK file not found at ${file.absolutePath}")
                Toast.makeText(context, "安裝檔案未找到", Toast.LENGTH_SHORT).show()
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