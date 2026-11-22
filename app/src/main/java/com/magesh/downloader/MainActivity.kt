package com.magesh.downloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.magesh.downloader.model.FormatOption
import com.magesh.downloader.ui.DownloaderScreen
import com.magesh.downloader.ui.theme.MageshDownloaderTheme
import com.magesh.downloader.viewmodel.DownloaderViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DownloaderViewModel by viewModels()
    private var pendingDownloadOption: FormatOption? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        pendingDownloadOption?.let { option ->
            pendingDownloadOption = null
            viewModel.startDownload(option)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestStartupPermissionsIfNeeded()
        handleIncomingIntent(intent)

        setContent {
            MageshDownloaderTheme {
                val uiState by viewModel.uiState.collectAsState()
                val downloadState by viewModel.downloadState.collectAsState()
                val engineInitState by viewModel.engineInitState.collectAsState()

                DownloaderScreen(
                    uiState = uiState,
                    downloadState = downloadState,
                    engineInitState = engineInitState,
                    onUrlChange = viewModel::onUrlChange,
                    onClearUrl = viewModel::clearUrl,
                    onFetchClick = viewModel::fetchFormats,
                    onDownloadClick = { option -> ensurePermissionsAndDownload(option) },
                    onCancelDownload = viewModel::cancelDownload
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            viewModel.handleSharedText(sharedText)
        }
    }

    private fun requestStartupPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun ensurePermissionsAndDownload(option: FormatOption) {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isNotEmpty()) {
            pendingDownloadOption = option
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            viewModel.startDownload(option)
        }
    }
}
