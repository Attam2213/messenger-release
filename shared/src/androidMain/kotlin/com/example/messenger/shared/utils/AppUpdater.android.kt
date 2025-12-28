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
                // TODO: Replace with your repository: owner/repo
                val repoOwner = "Attam2213"
                val repoName = "messenger-releases" // Public repository for updates
                
                val url = URL("https://api.github.com/repos/$repoOwner/$repoName/releases/latest")
                val json = url.readText()
                val obj = JSONObject(json)
                
                // GitHub tags usually start with 'v', e.g., 'v1.0'
                val latestVersionTag = obj.getString("tag_name") 
                val latestVersion = latestVersionTag.removePrefix("v")
                
                // Simple version comparison (not perfect, but works for simple cases)
                if (latestVersion != currentVersion && latestVersionTag != currentVersion) {
                    val assets = obj.getJSONArray("assets")
                    var downloadUrl = ""
                    
                    // Find the APK asset
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.getString("name")
                        if (name.endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url")
                            break
                        }
                    }
                    
                    if (downloadUrl.isNotEmpty()) {
                        return@withContext UpdateInfo(
                            version = latestVersionTag,
                            downloadUrl = downloadUrl,
                            changelog = obj.optString("body", "No changelog")
                        )
                    }
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
