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
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
                
                // Add timestamp to bypass cache
                val url = URL("https://raw.githubusercontent.com/$repoOwner/$repoName/$branch/version.json?t=${System.currentTimeMillis()}")
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

    actual fun downloadAndInstall(url: String, fileName: String, onProgress: ((Float) -> Unit)?) {
        // Delete existing file to prevent caching/stale updates
        try {
            val destDir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val destFile = File(destDir, fileName)
            if (destFile.exists()) {
                destFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Add timestamp to URL to bypass cache
        val downloadUrl = if (url.contains("?")) "$url&t=${System.currentTimeMillis()}" else "$url?t=${System.currentTimeMillis()}"
        
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
        request.setTitle("Downloading Update")
        request.setDescription("Downloading $fileName")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, fileName)
        request.setMimeType("application/vnd.android.package-archive")

        val manager = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        if (onProgress != null) {
            // Poll progress
            val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main)
            scope.launch {
                var downloading = true
                while (downloading) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = manager.query(query)
                    if (cursor.moveToFirst()) {
                        val bytesDownloaded = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val bytesTotal = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        
                        if (bytesTotal > 0) {
                            val progress = bytesDownloaded.toFloat() / bytesTotal.toFloat()
                            onProgress(progress)
                        }

                        val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            onProgress(1f)
                            downloading = false
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            downloading = false
                        }
                    }
                    cursor.close()
                    kotlinx.coroutines.delay(200) // Update more frequently
                }
            }
        }

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
