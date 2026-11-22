package com.magesh.downloader.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val processId = intent?.getStringExtra(DownloadService.EXTRA_PROCESS_ID)
        DownloadService.cancelActiveDownload(context, processId)
    }
}
