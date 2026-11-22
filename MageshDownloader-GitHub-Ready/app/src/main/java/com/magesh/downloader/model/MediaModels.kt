package com.magesh.downloader.model

import java.util.Locale

data class FormatOption(
    val id: String,
    val label: String,
    val resolutionTag: String,
    val container: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val isAudioOnly: Boolean = false,
    val isImage: Boolean = false,
    val audioFormat: String? = null,
    val directImageUrl: String? = null,
    val formatSelector: String = "best"
)

data class FetchedMedia(
    val sourceUrl: String,
    val platform: String,
    val title: String,
    val uploader: String?,
    val durationSeconds: Long,
    val thumbnailUrl: String?,
    val previewStreamUrl: String?,
    val formats: List<FormatOption>
)

data class DownloadProgressState(
    val isDownloading: Boolean = false,
    val processId: String? = null,
    val title: String = "",
    val formatLabel: String = "",
    val progress: Float = 0f,
    val etaSeconds: Long = 0L,
    val statusText: String = "",
    val completedFilePath: String? = null,
    val errorMessage: String? = null
)

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "Size varies"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
        else -> "$bytes B"
    }
}

fun formatDuration(seconds: Long): String {
    if (seconds <= 0L) return ""
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}
