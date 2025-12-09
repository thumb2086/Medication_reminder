package com.example.medicationreminderapp.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import android.util.Log
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

class UpdateManager(private val context: Context) {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val repoOwner = "CPXru"
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

                // Fixed warning: Unnecessary safe call on a non-null receiver
                val jsonStr = response.body.string()
                val json = gson.fromJson(jsonStr, JsonObject::class.java)

                val tagName = json.get("tag_name").asString
                val releaseNotes = json.get("body").asString
                
                val currentVersion = BuildConfig.VERSION_NAME

                // Logic for determining if an update is available:
                val isUpdateAvailable = when (channel) {
                    "stable" -> {
                        // For stable, tag usually starts with 'v' (e.g., v1.1.8)
                        // Simple check: if cleaned tag != current version (assumed stable build has simple X.Y.Z)
                        val cleanTag = tagName.removePrefix("v")
                        // Split current version to handle potential extra info if running a non-stable build
                        // But if user is on stable channel, they should be compared against the stable tag.
                        // Ideally we use SemVer, but for now simple string inequality is used as a trigger.
                        // Also, we should probably only update if tag is *different* from current.
                        // If current is "1.1.8" and tag is "v1.1.8", cleanTag is "1.1.8", so equal -> no update.
                        
                        // However, if user is on "1.1.8 nightly 5" and switches to stable "1.1.8", 
                        // they might want to "downgrade" or switch. 
                        // This simple logic just checks if the version string matches.
                        cleanTag != currentVersion
                    }
                    "dev", "nightly" -> {
                         // For dev/nightly, the tag is static ("latest-dev" or "nightly").
                         // We rely on the release body or name to contain the version info.
                         // But commonly, users on dev/nightly just want the *absolute latest*.
                         // Since we can't easily parse "nightly 5" vs "nightly 6" without standardizing the body,
                         // we will check if the release notes contain the exact current version string.
                         // If the body DOES NOT contain the current version string, we assume it's a new build.
                         // (This relies on the CI putting the version name in the release body)
                         !releaseNotes.contains(currentVersion)
                    }
                    else -> false
                }
                
                if (!isUpdateAvailable) return@withContext null

                val assets = json.getAsJsonArray("assets")
                if (assets.size() == 0) return@withContext null
                
                // Find .apk asset
                var apkUrl = ""
                for (asset in assets) {
                    val assetObj = asset.asJsonObject
                    val name = assetObj.get("name").asString
                    if (name.endsWith(".apk")) {
                        apkUrl = assetObj.get("browser_download_url").asString
                        break
                    }
                }
                
                if (apkUrl.isEmpty()) return@withContext null

                UpdateInfo(tagName, apkUrl, releaseNotes, channel != "stable")

            } catch (e: Exception) {
                Log.e("UpdateManager", "Error checking for updates", e)
                null
            }
        }
    }

    fun downloadAndInstall(url: String, fileName: String) {
        val request = DownloadManager.Request(url.toUri())
            .setTitle(context.getString(R.string.downloading_update))
            .setDescription(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        // Register receiver for download complete
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(fileName)
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
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (!file.exists()) {
            Log.e("UpdateManager", "APK file not found")
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
    }
}