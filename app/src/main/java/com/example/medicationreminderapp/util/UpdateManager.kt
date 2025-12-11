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
                // Official -> Fetch latest release (tags)
                // Nightly -> Fetch latest release from "nightly" tag
                
                val url = if (channel == "official") {
                    "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
                } else {
                     // For nightly, we target the 'nightly' tag specifically
                    "https://api.github.com/repos/$repoOwner/$repoName/releases/tags/nightly"
                }

                val request = Request.Builder()
                    .url(url)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e("UpdateManager", "Failed to fetch updates: ${response.code}")
                    return@withContext null
                }

                // response.body is nullable, so we use safe call or check
                val responseBody = response.body
                if (responseBody == null) {
                    Log.e("UpdateManager", "Response body is null")
                    return@withContext null
                }
                val jsonStr = responseBody.string()
                val json = gson.fromJson(jsonStr, JsonObject::class.java)

                val tagName = json.get("tag_name").asString
                val releaseNotes = json.get("body").asString
                
                val currentVersion = BuildConfig.VERSION_NAME
                
                val isUpdateAvailable = if (channel == "official") {
                     val cleanTag = tagName.removePrefix("v")
                     cleanTag != currentVersion.split(" ").first() 
                } else {
                    !releaseNotes.contains(currentVersion) 
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

                UpdateInfo(tagName, apkUrl, releaseNotes, channel == "nightly")

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
                    context.unregisterReceiver(this)
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