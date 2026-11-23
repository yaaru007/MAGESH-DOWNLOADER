package com.magesh.downloader.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.magesh.downloader.EngineInitState
import com.magesh.downloader.R
import com.magesh.downloader.model.DownloadProgressState
import com.magesh.downloader.model.FormatOption
import com.magesh.downloader.model.formatDuration
import com.magesh.downloader.ui.theme.BrandAmber
import com.magesh.downloader.ui.theme.BrandCrimson
import com.magesh.downloader.ui.theme.BrandEmerald
import com.magesh.downloader.ui.theme.BrandPinkGlow
import com.magesh.downloader.ui.theme.BrandViolet
import com.magesh.downloader.viewmodel.UiState
import kotlin.math.roundToInt

@Composable
fun DownloaderScreen(
    uiState: UiState,
    downloadState: DownloadProgressState,
    engineInitState: EngineInitState,
    onUrlChange: (String) -> Unit,
    onClearUrl: () -> Unit,
    onFetchClick: () -> Unit,
    onDownloadClick: (FormatOption) -> Unit,
    onCancelDownload: () -> Unit,
    onUpdateEngineClick: () -> Unit,
    onCookiesSaved: (Boolean) -> Unit,
    onCookiesCleared: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showVideoPlayer by remember(uiState.fetchedMedia?.sourceUrl) { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }

    if (showLoginDialog) {
        LoginWebViewDialog(
            onDismiss = { showLoginDialog = false },
            onCookiesSaved = { saved ->
                showLoginDialog = false
                onCookiesSaved(saved)
            },
            onCookiesCleared = {
                showLoginDialog = false
                onCookiesCleared()
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Header Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF230A22),
                                    Color(0xFF171126),
                                    Color(0xFF120E1D)
                                )
                            )
                        )
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo),
                                contentDescription = "MAGESH DOWNLOADER",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "MAGESH DOWNLOADER",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (uiState.hasCookies) BrandEmerald else BrandAmber
                                            )
                                    )
                                    Text(
                                        text = if (uiState.hasCookies) {
                                            "Signed In • Private & Public"
                                        } else {
                                            "YouTube • Instagram • Facebook"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (uiState.hasCookies) {
                                            BrandEmerald
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalIconButton(
                                onClick = { showLoginDialog = true },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (uiState.hasCookies) {
                                        BrandEmerald.copy(alpha = 0.18f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VpnKey,
                                    contentDescription = "Account Login / Cookies",
                                    tint = if (uiState.hasCookies) BrandEmerald else BrandAmber
                                )
                            }

                            FilledTonalIconButton(
                                onClick = onUpdateEngineClick,
                                enabled = !uiState.isUpdatingEngine,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                if (uiState.isUpdatingEngine) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.SystemUpdateAlt,
                                        contentDescription = "Update yt-dlp",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (engineInitState is EngineInitState.Initializing) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = BrandPinkGlow
                        )
                        Text(
                            text = "Initializing yt-dlp & FFmpeg engine...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            uiState.statusBanner?.let { banner ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BrandEmerald.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, BrandEmerald.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = banner,
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandEmerald,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // URL Input + Paste + Fetch Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.urlInput,
                        onValueChange = onUrlChange,
                        label = { Text("Paste video, reel, or picture link") },
                        placeholder = { Text("https://youtube.com/... or instagram.com/...") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandPinkGlow,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = BrandPinkGlow
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (uiState.urlInput.isNotEmpty()) {
                                IconButton(onClick = onClearUrl) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = "Clear URL"
                                    )
                                }
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as? ClipboardManager
                                val clipText = clipboard?.primaryClip
                                    ?.getItemAt(0)
                                    ?.coerceToText(context)
                                    ?.toString()
                                    .orEmpty()
                                if (clipText.isNotBlank()) {
                                    onUrlChange(clipText.trim())
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste", fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = onFetchClick,
                            enabled = !uiState.isFetching,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandCrimson,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            modifier = Modifier.weight(1.4f)
                        ) {
                            if (uiState.isFetching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Fetching...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Fetch Formats", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Error Banner
            uiState.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (error.contains("Sign in", ignoreCase = true) ||
                            error.contains("Login", ignoreCase = true)
                        ) {
                            Button(
                                onClick = { showLoginDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandAmber,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VpnKey,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sign in with Account", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Active or Completed Download Progress Card
            if (downloadState.isDownloading ||
                downloadState.completedFilePath != null ||
                downloadState.errorMessage != null
            ) {
                DownloadStatusCard(
                    downloadState = downloadState,
                    onCancelClick = onCancelDownload
                )
            }

            // Fetched Media Preview & Formats List
            uiState.fetchedMedia?.let { media ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (showVideoPlayer && !media.previewStreamUrl.isNullOrBlank()) {
                            VideoPreviewPlayer(streamUrl = media.previewStreamUrl)
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = media.thumbnailUrl,
                                    contentDescription = media.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (!media.previewStreamUrl.isNullOrBlank()) {
                                    Button(
                                        onClick = { showVideoPlayer = true },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Black.copy(alpha = 0.72f),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Preview Video"
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Preview", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BadgePill(text = media.platform, color = BrandPinkGlow)
                            val durationText = formatDuration(media.durationSeconds)
                            if (durationText.isNotEmpty()) {
                                BadgePill(text = durationText, color = BrandViolet)
                            }
                            if (showVideoPlayer) {
                                Surface(
                                    onClick = { showVideoPlayer = false },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "Show Thumbnail",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = media.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        media.uploader?.let { uploader ->
                            Text(
                                text = uploader,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Available Downloads",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${media.formats.size} options",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                media.formats.forEach { option ->
                    FormatRowCard(
                        option = option,
                        isDownloadBusy = downloadState.isDownloading,
                        onDownloadClick = { onDownloadClick(option) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BadgePill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun FormatRowCard(
    option: FormatOption,
    isDownloadBusy: Boolean,
    onDownloadClick: () -> Unit
) {
    val accentColor = when {
        option.isImage -> BrandAmber
        option.isAudioOnly -> BrandViolet
        else -> BrandPinkGlow
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            option.isImage -> Icons.Rounded.Image
                            option.isAudioOnly -> Icons.Rounded.AudioFile
                            else -> Icons.Rounded.Movie
                        },
                        contentDescription = null,
                        tint = accentColor
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = option.formattedSize,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onDownloadClick,
                enabled = !isDownloadBusy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = if (option.isImage) Color.Black else Color.White
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Download ${option.resolutionTag}",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DownloadStatusCard(
    downloadState: DownloadProgressState,
    onCancelClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            1.dp,
            if (downloadState.completedFilePath != null) {
                BrandEmerald.copy(alpha = 0.5f)
            } else {
                BrandPinkGlow.copy(alpha = 0.4f)
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = downloadState.title.ifBlank { "Download" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = downloadState.formatLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (downloadState.isDownloading) {
                    OutlinedButton(
                        onClick = onCancelClick,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cancel,
                            contentDescription = "Cancel",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }
                } else if (downloadState.completedFilePath != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Done",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandEmerald
                        )
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Completed",
                            tint = BrandEmerald
                        )
                    }
                }
            }

            if (downloadState.isDownloading) {
                if (downloadState.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { (downloadState.progress / 100f).coerceIn(0f, 1f) },
                        color = BrandPinkGlow,
                        trackColor = MaterialTheme.colorScheme.background,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                } else {
                    LinearProgressIndicator(
                        color = BrandPinkGlow,
                        trackColor = MaterialTheme.colorScheme.background,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                }
            }

            val message = downloadState.errorMessage ?: downloadState.statusText
            if (message.isNotBlank()) {
                val pctPrefix = if (downloadState.isDownloading && downloadState.progress > 0f) {
                    "${downloadState.progress.roundToInt()}% • "
                } else {
                    ""
                }
                Text(
                    text = if (downloadState.errorMessage != null) message else "$pctPrefix$message",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (downloadState.errorMessage != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}
