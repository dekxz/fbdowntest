package com.fbdownloader.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WinDark = darkColorScheme(
    primary = Color(0xFF0078D4),
    onPrimary = Color.White,
    background = Color(0xFF202020),
    onBackground = Color.White,
    surface = Color(0xFF2D2D2D),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2D2D2D),
    outline = Color(0xFF3A3A3A),
)

@Composable
fun MainScreen(
    vm: MainViewModel,
    onImportCookies: () -> Unit,
    onOpenFolder: () -> Unit,
    onPickHistory: () -> Unit,
) {
    MaterialTheme(colorScheme = WinDark) {
        val url by vm.url.collectAsState()
        val useCookies by vm.useCookies.collectAsState()
        val useAria2 by vm.useAria2.collectAsState()
        val cookieStatus by vm.cookieStatus.collectAsState()
        val format by vm.format.collectAsState()
        val qualities by vm.qualities.collectAsState()
        val qIndex by vm.qualityIndex.collectAsState()
        val qEnabled by vm.qualityEnabled.collectAsState()
        val log by vm.logText.collectAsState()
        val busy by vm.busy.collectAsState()
        val downloading by vm.downloading.collectAsState()
        val message by vm.message.collectAsState()
        val historyLabel by vm.historyLabel.collectAsState()
        val historyPicked by vm.historyPicked.collectAsState()

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(
                Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = vm::setUrl,
                    enabled = !busy,
                    singleLine = true,
                    label = { Text("Video/Post URL") },
                    placeholder = { Text("Paste Facebook video, reel, watch, photo, post or fb.watch URL") },
                    modifier = Modifier.fillMaxWidth(),
                )

                // Cookies (replaces the desktop "Cookies From: Firefox" combo)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = useCookies, onCheckedChange = vm::setUseCookies, enabled = !busy)
                    Column(Modifier.weight(1f)) {
                        Text("Use cookies.txt", fontSize = 14.sp)
                        Text(cookieStatus, fontSize = 11.sp, color = Color(0xFFA0A0A0))
                    }
                    OutlinedButton(onClick = onImportCookies, enabled = !busy) { Text("Import") }
                }

                // aria2c toggle (same option as the desktop checkbox)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = useAria2, onCheckedChange = vm::setUseAria2, enabled = !busy)
                    Column(Modifier.weight(1f)) {
                        Text("Use aria2c", fontSize = 14.sp)
                        Text("Faster multi-connection downloads", fontSize = 11.sp, color = Color(0xFFA0A0A0))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Picker(
                        label = "Download As",
                        items = MainViewModel.Fmt.entries,
                        selected = format.ordinal,
                        enabled = !busy,
                        text = { it.label },
                        onSelect = { vm.setFormat(MainViewModel.Fmt.entries[it]) },
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = vm::fetchFormats, enabled = !busy) { Text("Fetch Formats") }
                }

                Picker(
                    label = "Video Quality",
                    items = qualities,
                    selected = qIndex.coerceIn(0, (qualities.size - 1).coerceAtLeast(0)),
                    enabled = !busy && qEnabled && format == MainViewModel.Fmt.MP4,
                    text = { it.label },
                    onSelect = { vm.qualityIndex.value = it },
                    modifier = Modifier.fillMaxWidth(),
                )

                // Log area
                val scroll = rememberScrollState()
                LaunchedEffect(log) { scroll.scrollTo(scroll.maxValue) }
                Box(
                    Modifier.weight(1f).fillMaxWidth()
                        .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        log,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFE0E0E0),
                        modifier = Modifier.fillMaxSize().verticalScroll(scroll),
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = vm::onDownloadClicked,
                        enabled = downloading || !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (downloading) "Cancel" else "Download") }
                    OutlinedButton(onClick = onOpenFolder, modifier = Modifier.weight(1f)) { Text("Open Folder") }
                }
                Text(historyLabel, fontSize = 11.sp, color = Color(0xFFA0A0A0), maxLines = 2)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onPickHistory, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text("Pick existing history file", fontSize = 12.sp, maxLines = 2)
                    }
                    TextButton(onClick = vm::updateYtDlp, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Update Modules") }
                }
                if (historyPicked) {
                    TextButton(onClick = vm::useDefaultHistory, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Use the app's own history file instead", fontSize = 12.sp)
                    }
                }
            }
        }

        message?.let { m ->
            AlertDialog(
                onDismissRequest = vm::dismissMessage,
                title = { Text(m.title) },
                text = { Text(m.text) },
                confirmButton = { TextButton(onClick = vm::dismissMessage) { Text("OK") } },
                containerColor = Color(0xFF202020),
            )
        }
    }
}

@Composable
private fun <T> Picker(
    label: String,
    items: List<T>,
    selected: Int,
    enabled: Boolean,
    text: (T) -> String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(
                "$label: ${items.getOrNull(selected)?.let(text) ?: ""}",
                maxLines = 1,
                fontSize = 13.sp,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEachIndexed { i, item ->
                DropdownMenuItem(text = { Text(text(item), fontSize = 13.sp) }, onClick = { onSelect(i); open = false })
            }
        }
    }
}
