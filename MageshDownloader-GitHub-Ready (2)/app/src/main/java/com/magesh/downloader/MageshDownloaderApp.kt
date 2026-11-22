package com.magesh.downloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface EngineInitState {
    data object Initializing : EngineInitState
    data object Ready : EngineInitState
    data class Error(val message: String) : EngineInitState
}

class MageshDownloaderApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        initializeEngines()
    }

    private fun initializeEngines() {
        appScope.launch {
            try {
                YoutubeDL.getInstance().init(this@MageshDownloaderApp)
                FFmpeg.getInstance().init(this@MageshDownloaderApp)
                _initState.value = EngineInitState.Ready
            } catch (e: Exception) {
                _initState.value = EngineInitState.Error(
                    e.localizedMessage ?: "Failed to initialize downloader engine."
                )
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val progressChannel = NotificationChannel(
                CHANNEL_DOWNLOAD_PROGRESS,
                "Active Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows download progress and cancel controls"
                setShowBadge(false)
            }
            val completeChannel = NotificationChannel(
                CHANNEL_DOWNLOAD_COMPLETE,
                "Completed Downloads",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a download finishes or fails"
            }
            manager.createNotificationChannel(progressChannel)
            manager.createNotificationChannel(completeChannel)
        }
    }

    companion object {
        const val CHANNEL_DOWNLOAD_PROGRESS = "magesh_download_progress"
        const val CHANNEL_DOWNLOAD_COMPLETE = "magesh_download_complete"

        private val _initState = MutableStateFlow<EngineInitState>(EngineInitState.Initializing)
        val initState: StateFlow<EngineInitState> = _initState.asStateFlow()
    }
}
