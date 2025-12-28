package com.example.messenger.shared.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import org.json.JSONObject

actual class AppUpdater actual constructor(context: Any?) {
    private val ctx = context as Context

    actual suspend fun checkForUpdate(currentVersion: String): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                // Check version.json from the release repository
                val repoOwner = "Attam2213"
                val repoName = "messenger-release"
                val branch = "main"
                
                val url = URL("https://raw.githubusercontent.com/$repoOwner/$repoName/$branch/version.json")
                val json = url.readText()
                val obj = JSONObject(json)
                
                val latestVersion = obj.getString("version")
                val downloadUrl = obj.getString("downloadUrl")
                val changelog = obj.optString("changelog", "No changelog available")
                
                if (latestVersion != currentVersion) {
                    return@withContext UpdateInfo(
                        version = latestVersion,
                        downloadUrl = downloadUrl,
                        changelog = changelog
                    )
                }
                null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    actual fun downloadAndInstall(url: String, fileName: String) {
        val request = DownloadManager.Request(Uri.parse(url))
        request.setTitle("Downloading Update")
        request.setDescription("Downloading $fileName")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, fileName)
        request.setMimeType("application/vnd.android.package-archive")

        val manager = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        // Register receiver to listen for completion
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (downloadId == id) {
                    installApk(id)
                    try {
                        ctx.unregisterReceiver(this)
                    } catch (e: Exception) {
                        // Receiver might not be registered or already unregistered
                    }
                }
            }
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
             ctx.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
             ctx.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    actual fun getCurrentVersion(): String {
        return try {
            val pInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    private fun installApk(downloadId: Long) {
        val manager = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = manager.getUriForDownloadedFile(downloadId)
        
        if (uri != null) {
            val install = Intent(Intent.ACTION_VIEW)
            install.setDataAndType(uri, "application/vnd.android.package-archive")
            install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(install)
        }
    }
}
