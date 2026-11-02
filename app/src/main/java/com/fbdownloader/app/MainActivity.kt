package com.fbdownloader.app

import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private var lastClipboard: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShare(intent)

        setContent {
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let { vm.importCookies(it) }
            }
            val historyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let { vm.useHistoryFile(it) }
            }
            MainScreen(
                vm = vm,
                onImportCookies = { picker.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) },
                onOpenFolder = ::openDownloads,
                onPickHistory = { historyPicker.launch(arrayOf("*/*")) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Link shared from Facebook ("Share to > Facebook Downloader"). */
    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            Utils.findFbUrl(intent.getStringExtra(Intent.EXTRA_TEXT))?.let { vm.setUrl(it) }
        }
    }

    /**
     * Android equivalent of the desktop clipboard monitor. Android only lets the focused app read
     * the clipboard, so we check whenever the window gains focus.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text != null && text != lastClipboard) {
            lastClipboard = text
            Utils.findFbUrl(text)?.let { vm.setUrl(it) }
        }
    }

    private fun openDownloads() {
        try {
            startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            vm.message.value = MainViewModel.Msg("Error", "Could not open folder: ${e.message}\nFiles are in Downloads/${MediaExporter.FOLDER}.")
        }
    }
}
