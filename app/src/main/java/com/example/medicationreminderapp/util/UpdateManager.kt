package com.example.medicationreminderapp.util

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Build
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
        val isNightly: Boolean,
        val isNewer: Boolean,
        val isDifferentAppId: Boolean = false
    )

    /**
     * Checks for updates.
     * @param isManualCheck If true, checks the user's selected channel and allows reinstalling.
     *                      If false (automatic check), it only checks the app's built-in channel.
     */
    suspend fun checkForUpdates(isManualCheck: Boolean = false): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val buildChannel = if (BuildConfig.UPDATE_CHANNEL.isEmpty()) "main" else BuildConfig.UPDATE_CHANNEL

                val channelToCheck: String
                val force: Boolean

                if (isManualCheck) {
                    // Manual Check: Respect user's selection, and always allow download.
                    channelToCheck = prefs.getString("update_channel", buildChannel) ?: buildChannel
                    force = true
                    Log.d("UpdateManager", "Starting MANUAL check for channel: '$channelToCheck'")
                } else {
                    // Automatic Check: Only check the app's own channel, no forcing.
                    channelToCheck = buildChannel
                    force = false
                    Log.d("UpdateManager", "Starting AUTOMATIC check for channel: '$channelToCheck'")
                }

                val isStable = channelToCheck == "main" || channelToCheck == "master" || channelToCheck == "stable"

                if (isStable) {
                    checkStableUpdates(force)
                } else {
                    checkDynamicChannelUpdates(channelToCheck, force)
                }
            } catch (e: Exception) {
                Log.e("UpdateManager", "Error checking for updates", e)
                null
            }
        }
    }

    private fun checkDynamicChannelUpdates(channel: String, force: Boolean): UpdateInfo? {
        val jsonUrl = if (channel == BuildConfig.UPDATE_CHANNEL) {
             try {
                 BuildConfig.UPDATE_JSON_URL
             } catch (_: NoSuchFieldError) {
                 "https://$repoOwner.github.io/$repoName/update_$channel.json"
             }
        } else {
             "https://$repoOwner.github.io/$repoName/update_$channel.json"
        }
        
        Log.d("UpdateManager", "Fetching update config from: $jsonUrl")
        
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
        val jsonStr = responseBody.string()
        val json = gson.fromJson(jsonStr, JsonObject::class.java)

        // Parse JSON
        val latestVersionName = json.get("latestVersion").asString
        val downloadUrl = json.get("url").asString
        val releaseNotes = json.get("releaseNotes").asString

        // Use the proper SemVer comparison
        val isStrictlyNewer = isNewerVersion(BuildConfig.VERSION_NAME, latestVersionName)
        
        // Determine App ID difference
        val currentSuffix = getAppIdSuffix()
        val targetSuffix = when {
            channel == "dev" -> ".dev"
            channel.isNotEmpty() && channel != "main" -> ".nightly" // Assume non-main/dev is nightly
            else -> ""
        }
        
        val isDifferentId = currentSuffix != targetSuffix

        // Return info if it's newer OR forced (for manual checks)
        if (isStrictlyNewer || force) {
            return UpdateInfo(latestVersionName, downloadUrl, releaseNotes, true, isStrictlyNewer, isDifferentId)
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
        val currentVersionNormalized = BuildConfig.VERSION_NAME

        val isStrictlyNewer = isNewerVersion(currentVersionNormalized, remoteVersion)

        // Stable channel has no suffix
        val currentSuffix = getAppIdSuffix()
        val targetSuffix = ""
        val isDifferentId = currentSuffix != targetSuffix

        if (isStrictlyNewer || force) {
             return UpdateInfo(remoteVersion, apkUrl, releaseNotes, false, isStrictlyNewer, isDifferentId)
        }
        return null
    }

    private fun getAppIdSuffix(): String {
        val applicationId = BuildConfig.APPLICATION_ID
        return when {
            applicationId.endsWith(".dev") -> ".dev"
            applicationId.endsWith(".nightly") -> ".nightly"
            else -> ""
        }
    }
    
    @Suppress("SameParameterValue")
    private fun isNewerVersion(localVersion: String, remoteVersion: String): Boolean {
        // Normalize both strings to handle "1.2.0 dev 254" vs "1.2.0-dev-254"
        val normalizedLocal = localVersion.replace(" ", "-")
        val normalizedRemote = remoteVersion.replace(" ", "-")

        if (normalizedLocal == normalizedRemote) return false

        // Extract base version (1.2.0)
        val localBase = normalizedLocal.substringBefore('-')
        val remoteBase = normalizedRemote.substringBefore('-')

        // Compare base versions (e.g., 1.2.1 vs 1.2.0)
        if (localBase != remoteBase) {
            val localParts = localBase.split('.').map { it.toIntOrNull() ?: 0 }
            val remoteParts = remoteBase.split('.').map { it.toIntOrNull() ?: 0 }
            val length = max(localParts.size, remoteParts.size)

            for (i in 0 until length) {
                val l = localParts.getOrElse(i) { 0 }
                val r = remoteParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (l > r) return false
            }
        }

        // --- Logic for when base versions are the same (e.g., 1.2.1-dev vs 1.2.1) ---
        val localSuffix = normalizedLocal.substringAfter('-', "")
        val remoteSuffix = normalizedRemote.substringAfter('-', "")
        val localHasSuffix = localSuffix.isNotEmpty()

        // If local is stable (no suffix), remote must have a suffix to be different.
        // A remote pre-release is not newer than a local stable version.
        if (!localHasSuffix) {
            return false // e.g. local '1.2.1', remote '1.2.1-dev'. Remote is not newer.
        }

        // If local has a suffix, but remote does not, remote is newer (stable release).
        if (remoteSuffix.isEmpty()) {
            return true // e.g. local '1.2.1-dev', remote '1.2.1'. Remote is newer.
        }

        // If we reach here, both have suffixes. Compare them.
        // First, try to compare by commit count (numeric suffix)
        val localCount = localSuffix.substringAfterLast('-').toIntOrNull()
        val remoteCount = remoteSuffix.substringAfterLast('-').toIntOrNull()

        if (localCount != null && remoteCount != null) {
            return remoteCount > localCount
        }

        // If not numeric, compare alphabetically (e.g., 'beta' vs 'rc1').
        return remoteSuffix > localSuffix
    }

    fun downloadAndInstall(context: Context, url: String, fileName: String) {
        if (context is Activity && (context.isFinishing || context.isDestroyed)) {
            return // Activity is not running, do not show dialog
        }

        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            Toast.makeText(context, R.string.debug_build_warning, Toast.LENGTH_LONG).show()
        }

        if (!context.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(context)
                .setTitle(R.string.install_permission_title)
                .setMessage(R.string.install_permission_message)
                .setPositiveButton(R.string.go_to_settings) { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = "package:${context.packageName}".toUri()
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                .setNegativeButton(R.string.cancel, null)
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
                                    Toast.makeText(context, R.string.update_file_not_found, Toast.LENGTH_SHORT).show()
                                }

                            } else {
                                Log.e("UpdateManager", "Download failed with status: $status")
                                Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_SHORT).show()
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
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun installApk(file: File) {
        try {
            if (!file.exists()) {
                Toast.makeText(context, R.string.install_file_not_found, Toast.LENGTH_SHORT).show()
                return
            }
            
            if (file.length() < 1024) {
                 Toast.makeText(context, R.string.install_file_corrupted, Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "${context.getString(R.string.install_failed)}: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
