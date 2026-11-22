package com.magesh.downloader.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.magesh.downloader.EngineInitState
import com.magesh.downloader.MageshDownloaderApp
import com.magesh.downloader.download.DownloadService
import com.magesh.downloader.model.DownloadProgressState
import com.magesh.downloader.model.FetchedMedia
import com.magesh.downloader.model.FormatOption
import com.magesh.downloader.model.formatFileSize
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val urlInput: String = "",
    val isFetching: Boolean = false,
    val errorMessage: String? = null,
    val fetchedMedia: FetchedMedia? = null
)

class DownloaderViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val downloadState: StateFlow<DownloadProgressState> = DownloadService.state
    val engineInitState: StateFlow<EngineInitState> = MageshDownloaderApp.initState

    fun onUrlChange(newUrl: String) {
        _uiState.value = _uiState.value.copy(
            urlInput = newUrl,
            errorMessage = null
        )
    }

    fun clearUrl() {
        _uiState.value = _uiState.value.copy(
            urlInput = "",
            errorMessage = null,
            fetchedMedia = null
        )
    }

    fun handleSharedText(sharedText: String?) {
        if (sharedText.isNullOrBlank()) return
        val extracted = extractFirstUrl(sharedText) ?: sharedText.trim()
        _uiState.value = _uiState.value.copy(
            urlInput = extracted,
            errorMessage = null
        )
        if (isValidPublicUrl(extracted)) {
            fetchFormats()
        } else {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Shared text does not contain a valid video link."
            )
        }
    }

    fun fetchFormats() {
        val raw = _uiState.value.urlInput.trim()
        val cleanUrl = extractFirstUrl(raw) ?: raw

        if (cleanUrl.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Please paste a YouTube, Instagram, or Facebook link."
            )
            return
        }

        if (!isValidPublicUrl(cleanUrl)) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Invalid URL. Please enter a valid https:// link."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                urlInput = cleanUrl,
                isFetching = true,
                errorMessage = null,
                fetchedMedia = null
            )

            // Wait for YoutubeDL + FFmpeg initialization if still in progress
            val initResult = MageshDownloaderApp.initState.first {
                it !is EngineInitState.Initializing
            }
            if (initResult is EngineInitState.Error) {
                _uiState.value = _uiState.value.copy(
                    isFetching = false,
                    errorMessage = initResult.message
                )
                return@launch
            }

            try {
                val media = withContext(Dispatchers.IO) {
                    val request = YoutubeDLRequest(cleanUrl).apply {
                        addOption("--no-playlist")
                    }
                    val info = YoutubeDL.getInstance().getInfo(request)
                    val formatsList = info.formats ?: emptyList()

                    val previewUrl = pickPreviewStreamUrl(formatsList, info.url)
                    val options = buildFormatOptions(formatsList)

                    FetchedMedia(
                        sourceUrl = cleanUrl,
                        platform = detectPlatform(cleanUrl),
                        title = info.title?.takeIf { it.isNotBlank() } ?: "Untitled Video",
                        uploader = info.uploader?.takeIf { it.isNotBlank() },
                        durationSeconds = info.duration.toLong(),
                        thumbnailUrl = info.thumbnail,
                        previewStreamUrl = previewUrl,
                        formats = options
                    )
                }

                _uiState.value = _uiState.value.copy(
                    isFetching = false,
                    fetchedMedia = media,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isFetching = false,
                    fetchedMedia = null,
                    errorMessage = mapFetchError(e.localizedMessage ?: e.message ?: "")
                )
            }
        }
    }

    fun startDownload(option: FormatOption) {
        val media = _uiState.value.fetchedMedia ?: return
        DownloadService.startDownload(
            context = getApplication(),
            url = media.sourceUrl,
            title = media.title,
            formatLabel = option.label,
            resolutionTag = option.resolutionTag,
            formatSelector = option.formatSelector,
            isAudioOnly = option.isAudioOnly,
            audioFormat = option.audioFormat
        )
    }

    fun cancelDownload() {
        DownloadService.cancelActiveDownload(
            context = getApplication(),
            processId = downloadState.value.processId
        )
    }

    private fun pickPreviewStreamUrl(
        formats: List<VideoFormat>,
        fallbackUrl: String?
    ): String? {
        val progressive = formats
            .filter { fmt ->
                !fmt.url.isNullOrBlank() &&
                    fmt.vcodec != null && fmt.vcodec != "none" &&
                    fmt.acodec != null && fmt.acodec != "none"
            }
            .sortedByDescending { fmt ->
                val isMp4 = if (fmt.ext == "mp4") 1 else 0
                val idealHeight = if (fmt.height in 360..720) 1 else 0
                (isMp4 * 10000) + (idealHeight * 5000) + fmt.height
            }
        return progressive.firstOrNull()?.url ?: fallbackUrl
    }

    private fun buildFormatOptions(formats: List<VideoFormat>): List<FormatOption> {
        val bestAudio = formats
            .filter {
                (it.vcodec == null || it.vcodec == "none") &&
                    it.acodec != null && it.acodec != "none"
            }
            .maxByOrNull { maxOf(it.fileSize, it.fileSizeApproximate) }

        val bestAudioSize = bestAudio?.let { maxOf(it.fileSize, it.fileSizeApproximate) } ?: 0L

        val videoFormats = formats.filter {
            it.height > 0 && it.vcodec != null && it.vcodec != "none"
        }

        val options = mutableListOf<FormatOption>()

        val distinctHeights = videoFormats
            .map { it.height }
            .distinct()
            .sorted()

        for (height in distinctHeights) {
            val candidates = videoFormats.filter { it.height == height }
            val bestAtHeight = candidates.maxByOrNull { fmt ->
                val mp4Bonus = if (fmt.ext == "mp4") 1_000_000_000L else 0L
                val progressiveBonus = if (fmt.acodec != null && fmt.acodec != "none") 500_000_000L else 0L
                mp4Bonus + progressiveBonus + maxOf(fmt.fileSize, fmt.fileSizeApproximate)
            } ?: continue

            val rawSize = maxOf(bestAtHeight.fileSize, bestAtHeight.fileSizeApproximate)
            val hasAudio = bestAtHeight.acodec != null && bestAtHeight.acodec != "none"
            val estimatedBytes = when {
                rawSize <= 0L -> 0L
                hasAudio -> rawSize
                else -> rawSize + bestAudioSize
            }

            val tag = when {
                height >= 2160 -> "4K"
                height >= 1440 -> "2K"
                else -> "${height}p"
            }
            val label = when {
                height >= 2160 -> "2160p (4K) • MP4"
                height >= 1440 -> "1440p (2K) • MP4"
                height == 1080 -> "1080p Full HD • MP4"
                height == 720 -> "720p HD • MP4"
                else -> "${height}p • MP4"
            }

            options.add(
                FormatOption(
                    id = "video_$height",
                    label = label,
                    resolutionTag = tag,
                    container = "MP4",
                    sizeBytes = estimatedBytes,
                    formattedSize = formatFileSize(estimatedBytes),
                    isAudioOnly = false,
                    formatSelector = "bestvideo[height<=${height}][ext=mp4]+bestaudio[ext=m4a]/" +
                        "bestvideo[height<=${height}]+bestaudio/" +
                        "best[height<=${height}][ext=mp4]/best[height<=${height}]"
                )
            )
        }

        if (options.isEmpty()) {
            val fallbackSize = formats
                .map { maxOf(it.fileSize, it.fileSizeApproximate) }
                .maxOrNull() ?: 0L
            options.add(
                FormatOption(
                    id = "video_best",
                    label = "Best Available Quality • MP4",
                    resolutionTag = "Best",
                    container = "MP4",
                    sizeBytes = fallbackSize,
                    formattedSize = formatFileSize(fallbackSize),
                    isAudioOnly = false,
                    formatSelector = "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
                )
            )
        }

        // Audio-only options (MP3 & M4A)
        options.add(
            FormatOption(
                id = "audio_mp3",
                label = "Audio Only • MP3",
                resolutionTag = "MP3",
                container = "MP3",
                sizeBytes = bestAudioSize,
                formattedSize = formatFileSize(bestAudioSize),
                isAudioOnly = true,
                audioFormat = "mp3",
                formatSelector = "bestaudio/best"
            )
        )
        options.add(
            FormatOption(
                id = "audio_m4a",
                label = "Audio Only • M4A",
                resolutionTag = "M4A",
                container = "M4A",
                sizeBytes = bestAudioSize,
                formattedSize = formatFileSize(bestAudioSize),
                isAudioOnly = true,
                audioFormat = "m4a",
                formatSelector = "bestaudio/best"
            )
        )

        return options
    }

    private fun extractFirstUrl(text: String): String? {
        val regex = Regex("""https?://[^\s<>"{}|\\^`\[\]]+""", RegexOption.IGNORE_CASE)
        return regex.find(text)?.value
    }

    private fun isValidPublicUrl(url: String): Boolean {
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            return false
        }
        val host = Uri.parse(url).host?.lowercase() ?: return false
        return host.contains(".") && Patterns.WEB_URL.matcher(url).matches()
    }

    private fun detectPlatform(url: String): String {
        val host = Uri.parse(url).host?.lowercase().orEmpty()
        return when {
            host.contains("youtube.com") || host.contains("youtu.be") -> "YouTube"
            host.contains("instagram.com") || host.contains("instagr.am") -> "Instagram"
            host.contains("facebook.com") || host.contains("fb.watch") || host.contains("fb.com") -> "Facebook"
            else -> "Web"
        }
    }

    private fun mapFetchError(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("private") || lower.contains("login") ||
                lower.contains("sign in") || lower.contains("cookies") ||
                lower.contains("authentication") ->
                "This link appears to be private or requires login. Only public YouTube, Instagram, and Facebook links are supported."
            lower.contains("unsupported url") || lower.contains("no suitable") ->
                "Unsupported or invalid link. Please check the URL and try again."
            lower.contains("unavailable") || lower.contains("404") ||
                lower.contains("removed") || lower.contains("not found") ->
                "Video is unavailable or has been removed."
            lower.contains("unable to resolve host") || lower.contains("network") ||
                lower.contains("timed out") ->
                "Network error. Please check your internet connection and try again."
            else -> "Could not fetch formats: ${raw.take(160).ifBlank { "Unknown error" }}"
        }
    }
}
