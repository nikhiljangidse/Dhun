package com.nikhil.dhun.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nikhil.dhun.data.Playlist
import com.nikhil.dhun.data.Song
import com.nikhil.dhun.ui.theme.DhunColors

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("e.g. Gym Mix") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DhunColors.Accent,
                    cursorColor = DhunColors.Accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text(confirmLabel, color = if (name.isNotBlank()) DhunColors.Accent else DhunColors.Muted, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = DhunColors.Muted) } },
    )
}

@Composable
fun AddToPlaylistDialog(
    song: Song,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPick: (Playlist) -> Unit,
    onNew: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add “${song.title}” to") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onNew).padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = DhunColors.Accent)
                        Text("New playlist", color = DhunColors.Accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = DhunColors.Line)
                }
                items(playlists, key = { it.id }) { pl ->
                    val already = song.id in pl.songIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !already) { onPick(pl) }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(pl.name, color = DhunColors.Text, fontSize = 16.sp)
                            Text(songCount(pl.songIds.size), color = DhunColors.Muted, fontSize = 13.sp)
                        }
                        if (already) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = "Already added", tint = DhunColors.Muted, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = DhunColors.Muted) } },
    )
}

@Composable
fun ConfirmDialog(title: String, body: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, color = DhunColors.Muted) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = DhunColors.Accent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = DhunColors.Muted) } },
    )
}

private fun ignoringBattery(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
fun SettingsDialog(onDismiss: () -> Unit, onRescan: () -> Unit, onResetHistory: () -> Unit) {
    val context = LocalContext.current
    val unrestricted = remember { ignoringBattery(context) }
    var confirmReset by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Background playback", color = DhunColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    if (unrestricted) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = DhunColors.Accent, modifier = Modifier.size(18.dp))
                            Text("No battery limits — music won't be stopped.", color = DhunColors.Muted, fontSize = 14.sp)
                        }
                    } else {
                        Text(
                            "Your phone may stop music after a while to save battery. Allow Dhun to run without limits.",
                            color = DhunColors.Muted,
                            fontSize = 14.sp,
                        )
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                                    )
                                } catch (e: Exception) {
                                    toast(context, "Open Settings > Apps > Dhun > Battery > Unrestricted")
                                }
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DhunColors.Accent, contentColor = DhunColors.OnAccent),
                        ) { Text("Allow", fontWeight = FontWeight.Bold) }
                        Text(
                            "On Xiaomi / Redmi / POCO also turn on Autostart for Dhun.",
                            color = DhunColors.Muted,
                            fontSize = 12.sp,
                        )
                    }
                }
                HorizontalDivider(color = DhunColors.Line)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Library", color = DhunColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { onRescan(); onDismiss() }, modifier = Modifier.padding(start = 0.dp)) {
                        Text("Rescan music on phone", color = DhunColors.Accent)
                    }
                    TextButton(onClick = { confirmReset = true }) {
                        Text("Reset all shuffle history", color = DhunColors.Accent)
                    }
                }
                Text(
                    "Smart shuffle counts a song as heard after 30 seconds (or half of a short song).",
                    color = DhunColors.Muted,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = DhunColors.Accent) } },
    )

    if (confirmReset) {
        ConfirmDialog(
            title = "Reset shuffle history?",
            body = "Every song will count as not heard again, in all playlists.",
            confirmLabel = "Reset",
            onDismiss = { confirmReset = false },
            onConfirm = {
                confirmReset = false
                onResetHistory()
                onDismiss()
            },
        )
    }
}
