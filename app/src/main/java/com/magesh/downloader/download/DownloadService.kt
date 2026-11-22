package com.magesh.downloader.download

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.magesh.downloader.MageshDownloaderApp
import com.magesh.downloader.MainActivity
import com.magesh.downloader.model.DownloadProgressState
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var lastNotifyTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Video"
                val formatLabel = intent.getStringExtra(EXTRA_FORMAT_LABEL) ?: "MP4"
                val resolutionTag = intent.getStringExtra(EXTRA_RESOLUTION_TAG) ?: "Best"
                val formatSelector = intent.getStringExtra(EXTRA_FORMAT_SELECTOR) ?: "best"
                val isAudioOnly = intent.getBooleanExtra(EXTRA_IS_AUDIO_ONLY, false)
                val audioFormat = intent.getStringExtra(EXTRA_AUDIO_FORMAT)

                startDownload(
                    url = url,
                    title = title,
                    formatLabel = formatLabel,
                    resolutionTag = resolutionTag,
                    formatSelector = formatSelector,
                    isAudioOnly = isAudioOnly,
                    audioFormat = audioFormat
                )
            }
            ACTION_CANCEL -> {
                val processId = intent.getStringExtra(EXTRA_PROCESS_ID)
                    ?: _state.value.processId
                performCancel(processId)
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(
        url: String,
        title: String,
        formatLabel: String,
        resolutionTag: String,
        formatSelector: String,
        isAudioOnly: Boolean,
        audioFormat: String?
    ) {
        downloadJob?.cancel()
        val processId = "magesh_dl_${UUID.randomUUID()}"

        _state.value = DownloadProgressState(
            isDownloading = true,
            processId = processId,
            title = title,
            formatLabel = formatLabel,
            progress = 0f,
            etaSeconds = 0L,
            statusText = "Preparing download...",
            completedFilePath = null,
            errorMessage = null
        )

        val initialNotification = buildProgressNotification(
            title = title,
            subtitle = "Starting $formatLabel...",
            progress = 0,
            indeterminate = true,
            processId = processId
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID_PROGRESS,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID_PROGRESS, initialNotification)
        }

        downloadJob = serviceScope.launch {
            try {
                val downloadsRoot = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                val targetDir = File(downloadsRoot, "MAGESH DOWNLOADER")
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                val request = YoutubeDLRequest(url).apply {
                    addOption("--no-mtime")
                    addOption("--no-playlist")
                    if (isAudioOnly) {
                        val fmt = audioFormat ?: "mp3"
                        addOption("-x")
                        addOption("--audio-format", fmt)
                        addOption("--audio-quality", "0")
                        addOption(
                            "-o",
                            "${targetDir.absolutePath}/%(title).100B [Audio].%(ext)s"
                        )
                    } else {
                        addOption("-f", formatSelector)
                        addOption("--merge-output-format", "mp4")
                        addOption(
                            "-o",
                            "${targetDir.absolutePath}/%(title).100B [$resolutionTag].%(ext)s"
                        )
                    }
                }

                YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, _ ->
                    val clamped = progress.coerceIn(0f, 100f)
                    val etaText = if (etaInSeconds > 0) " • ETA ${etaInSeconds}s" else ""
                    val status = if (clamped >= 99.5f) {
                        "Finalizing with FFmpeg..."
                    } else {
                        "Downloading ${clamped.roundToInt()}%$etaText"
                    }

                    _state.value = _state.value.copy(
                        isDownloading = true,
                        progress = clamped,
                        etaSeconds = etaInSeconds,
                        statusText = status
                    )

                    val now = System.currentTimeMillis()
                    if (now - lastNotifyTime >= 450L || clamped >= 100f) {
                        lastNotifyTime = now
                        val manager = getSystemService(NotificationManager::class.java)
                        manager.notify(
                            NOTIFICATION_ID_PROGRESS,
                            buildProgressNotification(
                                title = title,
                                subtitle = status,
                                progress = clamped.roundToInt(),
                                indeterminate = clamped <= 0f,
                                processId = processId
                            )
                        )
                    }
                }

                // Scan target directory so newly downloaded media appears in Gallery / Files
                val files = targetDir.listFiles()?.map { it.absolutePath }?.toTypedArray()
                if (!files.isNullOrEmpty()) {
                    MediaScannerConnection.scanFile(this@DownloadService, files, null, null)
                }

                _state.value = DownloadProgressState(
                    isDownloading = false,
                    processId = null,
                    title = title,
                    formatLabel = formatLabel,
                    progress = 100f,
                    etaSeconds = 0L,
                    statusText = "Saved to Downloads/MAGESH DOWNLOADER",
                    completedFilePath = targetDir.absolutePath,
                    errorMessage = null
                )

                showCompletionNotification(
                    title = title,
                    message = "Saved ($formatLabel) to Downloads/MAGESH DOWNLOADER"
                )
            } catch (e: Exception) {
                val rawMsg = e.localizedMessage ?: e.message ?: "Download failed."
                val isCancelled = rawMsg.contains("Destroy", ignoreCase = true) ||
                    rawMsg.contains("interrupt", ignoreCase = true) ||
                    _state.value.processId == null

                if (isCancelled) {
                    _state.value = DownloadProgressState(
                        isDownloading = false,
                        statusText = "Download cancelled."
                    )
                } else {
                    val friendlyError = mapFriendlyError(rawMsg)
                    _state.value = DownloadProgressState(
                        isDownloading = false,
                        title = title,
                        formatLabel = formatLabel,
                        errorMessage = friendlyError
                    )
                    showCompletionNotification(
                        title = "Download failed",
                        message = friendlyError
                    )
                }
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun performCancel(processId: String?) {
        if (processId != null) {
            try {
                YoutubeDL.getInstance().destroyProcessById(processId)
            } catch (_: Exception) {
            }
        }
        downloadJob?.cancel()
        _state.value = DownloadProgressState(
            isDownloading = false,
            processId = null,
            statusText = "Download cancelled."
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildProgressNotification(
        title: String,
        subtitle: String,
        progress: Int,
        indeterminate: Boolean,
        processId: String
    ) = NotificationCompat.Builder(this, MageshDownloaderApp.CHANNEL_DOWNLOAD_PROGRESS)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(title)
        .setContentText(subtitle)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(100, progress, indeterminate)
        .setContentIntent(createOpenAppIntent())
        .addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Cancel",
            createCancelIntent(processId)
        )
        .build()

    private fun showCompletionNotification(title: String, message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(
            this,
            MageshDownloaderApp.CHANNEL_DOWNLOAD_COMPLETE
        )
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(createOpenAppIntent())
            .build()
        manager.notify(NOTIFICATION_ID_COMPLETE, notification)
    }

    private fun createOpenAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createCancelIntent(processId: String): PendingIntent {
        val intent = Intent(this, DownloadCancelReceiver::class.java).apply {
            putExtra(EXTRA_PROCESS_ID, processId)
        }
        return PendingIntent.getBroadcast(
            this,
            101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun mapFriendlyError(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("private") || lower.contains("sign in") || lower.contains("login") ->
                "This video is private or requires login. Only public links are supported."
            lower.contains("unavailable") || lower.contains("404") ->
                "Video unavailable or removed."
            lower.contains("permission") || lower.contains("eacces") ->
                "Storage permission denied. Please grant storage access and try again."
            else -> "Download error: ${raw.take(140)}"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START = "com.magesh.downloader.action.START_DOWNLOAD"
        const val ACTION_CANCEL = "com.magesh.downloader.action.CANCEL_DOWNLOAD"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_FORMAT_LABEL = "extra_format_label"
        const val EXTRA_RESOLUTION_TAG = "extra_resolution_tag"
        const val EXTRA_FORMAT_SELECTOR = "extra_format_selector"
        const val EXTRA_IS_AUDIO_ONLY = "extra_is_audio_only"
        const val EXTRA_AUDIO_FORMAT = "extra_audio_format"
        const val EXTRA_PROCESS_ID = "extra_process_id"

        private const val NOTIFICATION_ID_PROGRESS = 1001
        private const val NOTIFICATION_ID_COMPLETE = 1002

        private val _state = MutableStateFlow(DownloadProgressState())
        val state: StateFlow<DownloadProgressState> = _state.asStateFlow()

        fun startDownload(
            context: Context,
            url: String,
            title: String,
            formatLabel: String,
            resolutionTag: String,
            formatSelector: String,
            isAudioOnly: Boolean,
            audioFormat: String?
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_FORMAT_LABEL, formatLabel)
                putExtra(EXTRA_RESOLUTION_TAG, resolutionTag)
                putExtra(EXTRA_FORMAT_SELECTOR, formatSelector)
                putExtra(EXTRA_IS_AUDIO_ONLY, isAudioOnly)
                putExtra(EXTRA_AUDIO_FORMAT, audioFormat)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelActiveDownload(context: Context, processId: String?) {
            val pid = processId ?: _state.value.processId
            if (pid != null) {
                try {
                    YoutubeDL.getInstance().destroyProcessById(pid)
                } catch (_: Exception) {
                }
            }
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_PROCESS_ID, pid)
            }
            context.startService(intent)
        }
    }
}
