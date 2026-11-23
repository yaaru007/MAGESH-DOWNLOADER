package com.magesh.downloader.viewmodel

import android.app.Application
import android.net.Uri
import android.text.Html
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.magesh.downloader.EngineInitState
import com.magesh.downloader.MageshDownloaderApp
import com.magesh.downloader.cookies.CookieHelper
import com.magesh.downloader.cookies.WebViewMediaExtractor
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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UiState(
    val urlInput: String = "",
    val isFetching: Boolean = false,
    val isUpdatingEngine: Boolean = false,
    val statusBanner: String? = null,
    val hasCookies: Boolean = false,
    val errorMessage: String? = null,
    val fetchedMedia: FetchedMedia? = null
)

class DownloaderViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        UiState(hasCookies = CookieHelper.getCookiesFileIfValid(application) != null)
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val downloadState: StateFlow<DownloadProgressState> = DownloadService.state
    val engineInitState: StateFlow<EngineInitState> = MageshDownloaderApp.initState

    fun refreshCookieStatus() {
        val exists = CookieHelper.getCookiesFileIfValid(getApplication()) != null
        _uiState.value = _uiState.value.copy(hasCookies = exists)
    }

    fun onCookiesSaved(saved: Boolean) {
        refreshCookieStatus()
        _uiState.value = _uiState.value.copy(
            statusBanner = if (saved) {
                "Account cookies saved. Tap Fetch to retry."
            } else {
                "No session cookies found. Make sure you completed login."
            },
            errorMessage = null
        )
    }

    fun onCookiesCleared() {
        refreshCookieStatus()
        _uiState.value = _uiState.value.copy(
            statusBanner = "Saved cookies cleared."
        )
    }

    fun updateYtDlp() {
        if (_uiState.value.isUpdatingEngine) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isUpdatingEngine = true,
                statusBanner = "Updating yt-dlp extractors..."
            )
            try {
                val status = withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().updateYoutubeDL(
                        getApplication(),
                        YoutubeDL.UpdateChannel.STABLE
                    )
                }
                _uiState.value = _uiState.value.copy(
                    isUpdatingEngine = false,
                    statusBanner = when (status) {
                        YoutubeDL.UpdateStatus.DONE -> "yt-dlp updated to latest version!"
                        YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp is already up to date."
                        else -> "yt-dlp update check finished."
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUpdatingEngine = false,
                    statusBanner = "yt-dlp update failed: ${e.localizedMessage ?: "Network error"}"
                )
            }
        }
    }

    fun onUrlChange(newUrl: String) {
        _uiState.value = _uiState.value.copy(
            urlInput = newUrl,
            errorMessage = null,
            statusBanner = null
        )
    }

    fun clearUrl() {
        _uiState.value = _uiState.value.copy(
            urlInput = "",
            errorMessage = null,
            statusBanner = null,
            fetchedMedia = null
        )
    }

    fun handleSharedText(sharedText: String?) {
        if (sharedText.isNullOrBlank()) return
        val extracted = extractFirstUrl(sharedText) ?: sharedText.trim()
        _uiState.value = _uiState.value.copy(
            urlInput = extracted,
            errorMessage = null,
            statusBanner = null
        )
        if (isValidUrl(extracted)) {
            fetchFormats()
        } else {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Shared text does not contain a valid media link."
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

        if (!isValidUrl(cleanUrl)) {
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
                statusBanner = null,
                fetchedMedia = null
            )

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
                    val cookiesFile = CookieHelper.getCookiesFileIfValid(getApplication())
                    val request = YoutubeDLRequest(cleanUrl).apply {
                        addOption("--no-playlist")
                        addOption("--no-warnings")
                        if (cookiesFile != null) {
                            addOption("--cookies", cookiesFile.absolutePath)
                        }
                    }

                    try {
                        val info = YoutubeDL.getInstance().getInfo(request)
                        val formatsList = info.formats ?: emptyList()
                        val previewUrl = pickPreviewStreamUrl(formatsList, info.url)
                        val options = buildFormatOptions(formatsList, info.thumbnail)

                        FetchedMedia(
                            sourceUrl = cleanUrl,
                            platform = detectPlatform(cleanUrl),
                            title = info.title?.takeIf { it.isNotBlank() } ?: "Untitled Media",
                            uploader = info.uploader?.takeIf { it.isNotBlank() },
                            durationSeconds = info.duration.toLong(),
                            thumbnailUrl = info.thumbnail,
                            previewStreamUrl = previewUrl,
                            formats = options
                        )
                    } catch (ytdlError: Exception) {
                        val platform = detectPlatform(cleanUrl)
                        if (platform == "YouTube") {
                            throw ytdlError
                        }
                        val isLikelyVideoOnlyUrl = cleanUrl.contains("/reel/", ignoreCase = true) ||
                            cleanUrl.contains("/reels/", ignoreCase = true) ||
                            cleanUrl.contains("/tv/", ignoreCase = true) ||
                            cleanUrl.contains("/watch", ignoreCase = true) ||
                            cleanUrl.contains("/videos/", ignoreCase = true)

                        val fallbackMedia = fallbackExtractSocialMedia(cleanUrl)
                            ?: extractViaWebViewFallback(cleanUrl)

                        if (fallbackMedia != null &&
                            (!isLikelyVideoOnlyUrl || fallbackMedia.formats.any { it.isDirectVideo })
                        ) {
                            fallbackMedia
                        } else {
                            throw ytdlError
                        }
                    }
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
            isImage = option.isImage,
            isDirectVideo = option.isDirectVideo,
            audioFormat = option.audioFormat,
            directImageUrl = option.directImageUrl,
            directVideoUrl = option.directVideoUrl
        )
    }

    fun cancelDownload() {
        DownloadService.cancelActiveDownload(
            context = getApplication(),
            processId = downloadState.value.processId
        )
    }

    private suspend fun extractViaWebViewFallback(url: String): FetchedMedia? {
        val extracted = WebViewMediaExtractor.extract(getApplication(), url) ?: return null
        if (extracted.imageUrls.isEmpty() && extracted.videoUrls.isEmpty()) return null
        return buildDirectFetchedMedia(
            originalUrl = url,
            title = extracted.title.ifBlank {
                if (extracted.videoUrls.isNotEmpty()) "Video Post" else "Picture Post"
            },
            uploader = null,
            videoUrls = extracted.videoUrls,
            imageUrls = extracted.imageUrls
        )
    }

    private fun fallbackExtractSocialMedia(url: String): FetchedMedia? {
        val igShortcode = Regex(
            """instagram\.com/(?:reel|reels|p|tv)/([A-Za-z0-9_-]+)""",
            RegexOption.IGNORE_CASE
        ).find(url)?.groupValues?.getOrNull(1)

        if (!igShortcode.isNullOrBlank()) {
            // 1) Try Instagram JSON API (works well with session cookies for photos, carousels & private posts)
            extractInstagramJsonApi(igShortcode, originalUrl = url)?.let { return it }

            // 2) Try Instagram public embed page (works without login for many public posts & reels)
            val embedUrl = "https://www.instagram.com/p/$igShortcode/embed/captioned/"
            extractFromHtmlPage(embedUrl, originalUrl = url, sendCookies = false)?.let { return it }

            // 3) Try canonical Instagram page with cookies
            val postUrl = "https://www.instagram.com/p/$igShortcode/"
            extractFromHtmlPage(postUrl, originalUrl = url, sendCookies = true)?.let { return it }
        }

        // 4) Generic HTML meta/JSON fallback for Facebook / Instagram / Web links
        return extractFromHtmlPage(url, originalUrl = url, sendCookies = true)
    }

    private fun extractInstagramJsonApi(shortcode: String, originalUrl: String): FetchedMedia? {
        val cookieHeader = CookieHelper.getCookieHeaderForUrl("https://www.instagram.com")
        val csrfToken = cookieHeader?.split(";")
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("csrftoken=") }
            ?.substringAfter("csrftoken=")

        val endpoints = listOf(
            "https://www.instagram.com/p/$shortcode/?__a=1&__d=dis",
            "https://www.instagram.com/graphql/query/?doc_id=8845758582119845&variables=%7B%22shortcode%22%3A%22$shortcode%22%7D"
        )

        for (apiUrl in endpoints) {
            val parsed = tryFetchInstagramJson(apiUrl, originalUrl, cookieHeader, csrfToken)
            if (parsed != null) return parsed
        }
        return null
    }

    private fun tryFetchInstagramJson(
        apiUrl: String,
        originalUrl: String,
        cookieHeader: String?,
        csrfToken: String?
    ): FetchedMedia? {
        return try {
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
                setRequestProperty("X-IG-App-ID", "936619743392459")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Referer", "https://www.instagram.com/")
                if (!csrfToken.isNullOrBlank()) {
                    setRequestProperty("X-CSRFToken", csrfToken)
                }
                if (!cookieHeader.isNullOrBlank()) {
                    setRequestProperty("Cookie", cookieHeader)
                }
            }
            conn.connect()
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }.trim()
            if (!body.startsWith("{")) return null

            val root = JSONObject(body)
            val items = root.optJSONArray("items")
            val item = items?.optJSONObject(0)
                ?: root.optJSONObject("graphql")?.optJSONObject("shortcode_media")
                ?: root.optJSONObject("data")?.optJSONObject("xdt_shortcode_media")
                ?: return null

            val videoUrls = linkedSetOf<String>()
            val imageUrls = linkedSetOf<String>()

            // New items[] schema
            fun parseItemNode(node: JSONObject) {
                node.optJSONArray("video_versions")?.let { vids ->
                    if (vids.length() > 0) {
                        vids.optJSONObject(0)?.optString("url")?.takeIf { it.startsWith("http") }?.let {
                            videoUrls.add(it)
                        }
                    }
                }
                node.optJSONObject("image_versions2")?.optJSONArray("candidates")?.let { cands ->
                    if (cands.length() > 0) {
                        cands.optJSONObject(0)?.optString("url")?.takeIf { it.startsWith("http") }?.let {
                            imageUrls.add(it)
                        }
                    }
                }
                node.optString("video_url").takeIf { it.startsWith("http") }?.let { videoUrls.add(it) }
                node.optString("display_url").takeIf { it.startsWith("http") }?.let { imageUrls.add(it) }
            }

            parseItemNode(item)

            // Carousel items (items[0].carousel_media)
            item.optJSONArray("carousel_media")?.let { carousel ->
                for (i in 0 until carousel.length()) {
                    carousel.optJSONObject(i)?.let { parseItemNode(it) }
                }
            }

            // GraphQL carousel (edge_sidecar_to_children.edges)
            item.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")?.let { edges ->
                for (i in 0 until edges.length()) {
                    edges.optJSONObject(i)?.optJSONObject("node")?.let { parseItemNode(it) }
                }
            }

            if (videoUrls.isEmpty() && imageUrls.isEmpty()) return null

            val caption = item.optJSONObject("caption")?.optString("text")
                ?.takeIf { it.isNotBlank() }
                ?: "Instagram Post"
            val uploader = item.optJSONObject("user")?.optString("username")
                ?.takeIf { it.isNotBlank() }
                ?.let { "@$it" }

            buildDirectFetchedMedia(
                originalUrl = originalUrl,
                title = caption,
                uploader = uploader,
                videoUrls = videoUrls.toList(),
                imageUrls = imageUrls.toList()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractFromHtmlPage(
        fetchUrl: String,
        originalUrl: String,
        sendCookies: Boolean
    ): FetchedMedia? {
        return try {
            val conn = (URL(fetchUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
                if (sendCookies) {
                    CookieHelper.getCookieHeaderForUrl(fetchUrl)?.let {
                        setRequestProperty("Cookie", it)
                    }
                }
            }
            conn.connect()
            if (conn.responseCode !in 200..299) return null

            val html = conn.inputStream.bufferedReader().use { it.readText() }

            val videoUrls = linkedSetOf<String>()
            val imageUrls = linkedSetOf<String>()

            val videoRegexes = listOf(
                Regex(""""video_url"\s*:\s*"([^"]+)""""),
                Regex("""<meta[^>]+property=["']og:video(?::secure_url)?["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
                Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:video(?::secure_url)?["']""", RegexOption.IGNORE_CASE),
                Regex("""<video[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            )
            videoRegexes.forEach { regex ->
                regex.findAll(html).forEach { match ->
                    val decoded = decodeEscapedUrl(match.groupValues[1])
                    if (decoded.startsWith("http")) {
                        videoUrls.add(decoded)
                    }
                }
            }

            val imageRegexes = listOf(
                Regex(""""display_url"\s*:\s*"([^"]+)""""),
                Regex(""""image_versions2"\s*:\s*\{\s*"candidates"\s*:\s*\[\s*\{[^}]*"url"\s*:\s*"([^"]+)""""),
                Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
                Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']""", RegexOption.IGNORE_CASE),
                Regex("""<meta[^>]+name=["']twitter:image["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
                Regex("""<img[^>]+class=["'][^"']*EmbeddedMediaImage[^"']*["'][^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
                Regex("""<img[^>]+src=["']([^"']+)["'][^>]+class=["'][^"']*EmbeddedMediaImage[^"']*["']""", RegexOption.IGNORE_CASE),
                Regex("""<img[^>]+src=["']([^"']*(?:cdninstagram\.com|fbcdn\.net)[^"']+)["']""", RegexOption.IGNORE_CASE)
            )
            imageRegexes.forEach { regex ->
                regex.findAll(html).forEach { match ->
                    val decoded = decodeEscapedUrl(match.groupValues[1])
                    if (isValidMediaImageUrl(decoded)) {
                        imageUrls.add(decoded)
                    }
                }
            }

            if (videoUrls.isEmpty() && imageUrls.isEmpty()) return null

            val titleMatch = Regex(
                """<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)["']""",
                RegexOption.IGNORE_CASE
            ).find(html)?.groupValues?.getOrNull(1)
                ?: Regex(
                    """<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:title["']""",
                    RegexOption.IGNORE_CASE
                ).find(html)?.groupValues?.getOrNull(1)

            val title = titleMatch?.let {
                Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString().trim()
            }?.takeIf { it.isNotBlank() } ?: if (videoUrls.isNotEmpty()) "Video Post" else "Picture Post"

            buildDirectFetchedMedia(
                originalUrl = originalUrl,
                title = title,
                uploader = null,
                videoUrls = videoUrls.toList(),
                imageUrls = imageUrls.toList()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun buildDirectFetchedMedia(
        originalUrl: String,
        title: String,
        uploader: String?,
        videoUrls: List<String>,
        imageUrls: List<String>
    ): FetchedMedia {
        val options = mutableListOf<FormatOption>()

        videoUrls.forEachIndexed { index, vUrl ->
            val label = if (videoUrls.size == 1) {
                "Original Quality • MP4"
            } else {
                "Video ${index + 1} • MP4"
            }
            options.add(
                FormatOption(
                    id = "direct_video_$index",
                    label = label,
                    resolutionTag = "HD",
                    container = "MP4",
                    sizeBytes = 0L,
                    formattedSize = "Direct Stream",
                    isDirectVideo = true,
                    directVideoUrl = vUrl
                )
            )
        }

        imageUrls.forEachIndexed { index, imgUrl ->
            val label = when {
                videoUrls.isNotEmpty() && index == 0 && imageUrls.size == 1 ->
                    "Thumbnail / Cover Picture • JPG"
                imageUrls.size == 1 -> "Picture / Image • JPG"
                else -> "Picture ${index + 1} • JPG"
            }
            options.add(
                FormatOption(
                    id = "img_$index",
                    label = label,
                    resolutionTag = "JPG",
                    container = "JPG",
                    sizeBytes = 0L,
                    formattedSize = "Original Image",
                    isImage = true,
                    directImageUrl = imgUrl
                )
            )
        }

        return FetchedMedia(
            sourceUrl = originalUrl,
            platform = detectPlatform(originalUrl),
            title = title,
            uploader = uploader,
            durationSeconds = 0L,
            thumbnailUrl = imageUrls.firstOrNull(),
            previewStreamUrl = videoUrls.firstOrNull(),
            formats = options
        )
    }

    private fun isValidMediaImageUrl(url: String): Boolean {
        if (!url.startsWith("http")) return false
        val lower = url.lowercase()
        return !lower.contains("rsrc.php") &&
            !lower.contains("static.cdninstagram.com/rsrc") &&
            !lower.contains("s150x150") &&
            !lower.contains("p150x150") &&
            !lower.contains("t51.2885-19") &&
            !lower.contains("profile_pic")
    }

    private fun decodeEscapedUrl(raw: String): String {
        val unescaped = raw
            .replace("\\u0026", "&")
            .replace("\\/", "/")
            .replace("&amp;", "&")
        return Html.fromHtml(unescaped, Html.FROM_HTML_MODE_LEGACY).toString().trim()
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

    private fun buildFormatOptions(
        formats: List<VideoFormat>,
        thumbnailUrl: String?
    ): List<FormatOption> {
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

        // Cover / Thumbnail Picture option
        if (!thumbnailUrl.isNullOrBlank()) {
            options.add(
                FormatOption(
                    id = "image_thumb",
                    label = "Thumbnail / Cover Picture • JPG",
                    resolutionTag = "JPG",
                    container = "JPG",
                    sizeBytes = 0L,
                    formattedSize = "Original Image",
                    isImage = true,
                    directImageUrl = thumbnailUrl
                )
            )
        }

        return options
    }

    private fun extractFirstUrl(text: String): String? {
        val regex = Regex("""https?://[^\s<>"{}|\\^`\[\]]+""", RegexOption.IGNORE_CASE)
        return regex.find(text)?.value
    }

    private fun isValidUrl(url: String): Boolean {
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
                lower.contains("rate-limit") || lower.contains("authentication") ->
                "Login required or rate-limited. Tap the Key icon in the top bar to sign in to your account, then tap Fetch again."
            lower.contains("unsupported url") || lower.contains("no suitable") ->
                "Unsupported or invalid link. Please check the URL or tap the Update icon to update yt-dlp."
            lower.contains("unavailable") || lower.contains("404") ||
                lower.contains("removed") || lower.contains("not found") ->
                "Media is unavailable or has been removed."
            lower.contains("unable to resolve host") || lower.contains("network") ||
                lower.contains("timed out") ->
                "Network error. Please check your internet connection and try again."
            else -> "Could not fetch media: ${raw.take(160).ifBlank { "Unknown error" }}"
        }
    }
}
