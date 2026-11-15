package com.nikhil.dhun.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.nikhil.dhun.data.sound.ScanState
import com.nikhil.dhun.data.sound.SoundScanner
import com.nikhil.dhun.data.DuplicateGroup
import com.nikhil.dhun.data.Duplicates
import com.nikhil.dhun.data.HistoryStore
import com.nikhil.dhun.data.MusicLibrary
import com.nikhil.dhun.data.PlaylistStore
import com.nikhil.dhun.data.Song
import com.nikhil.dhun.data.formatBytes
import com.nikhil.dhun.playback.PlayerConnection
import com.nikhil.dhun.ui.theme.DhunColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What is about to be deleted, and which kept song each deleted copy maps to. */
private data class DeletePlan(val ids: List<Long>, val keptFor: Map<Long, Long>, val bytes: Long)

@Composable
fun DuplicatesScreen(songs: List<Song>, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bySound by rememberSaveable { mutableStateOf(false) }
    val scan by SoundScanner.state.collectAsState()
    val nameGroups = remember(songs) { Duplicates.find(songs) }
    val soundGroups = remember(scan, songs) {
        val done = scan as? ScanState.Done
        if (done == null) {
            emptyList()
        } else {
            val present = songs.associateBy { it.id }
            done.groups.mapNotNull { g ->
                val still = g.copies.mapNotNull { present[it.id] }
                if (still.size > 1) g.copy(copies = Duplicates.bestFirst(still)) else null
            }
        }
    }
    val groups = if (bySound) soundGroups else nameGroups
    val scanning = scan is ScanState.Listening || scan is ScanState.Matching

    // Keep the screen awake while listening to songs.
    val activity = context as? Activity
    DisposableEffect(scanning) {
        if (scanning) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    // Ticked = will be deleted. Default: every copy except the best one.
    val selected = remember(groups) {
        mutableStateListOf<Long>().apply { groups.forEach { g -> g.copies.drop(1).forEach { add(it.id) } } }
    }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<DeletePlan?>(null) }
    var confirmLegacy by remember { mutableStateOf<DeletePlan?>(null) }

    val selectedSet = selected.toSet()
    val allCopies = groups.flatMap { it.copies }
    val selectedBytes = allCopies.filter { it.id in selectedSet }.sumOf { it.sizeBytes }
    val extraCopies = groups.sumOf { it.copies.size - 1 }
    val extraBytes = groups.sumOf { g -> g.copies.drop(1).sumOf { it.sizeBytes } }

    fun makePlan(): DeletePlan {
        val keptFor = HashMap<Long, Long>()
        groups.forEach { g ->
            val keeper = g.copies.firstOrNull { it.id !in selectedSet }
            if (keeper != null) g.copies.filter { it.id in selectedSet }.forEach { keptFor[it.id] = keeper.id }
        }
        return DeletePlan(selected.toList(), keptFor, selectedBytes)
    }

    suspend fun afterDelete(plan: DeletePlan) {
        MusicLibrary.refresh(context)
        val present = MusicLibrary.byId().keys
        val gone = plan.ids.filter { it !in present }.toSet()
        if (gone.isEmpty()) {
            toast(context, "Nothing was deleted")
            return
        }
        PlaylistStore.remapSongs(context, gone, plan.keptFor)
        HistoryStore.transfer(context, plan.keptFor.filterKeys { it in gone }, gone)
        PlayerConnection.removeIds(gone)
        toast(context, "Deleted ${songCount(gone.size)} · freed ${formatBytes(plan.bytes)}")
    }

    suspend fun deleteDirectly(plan: DeletePlan) {
        withContext(Dispatchers.IO) {
            plan.ids.forEach { id ->
                try {
                    context.contentResolver.delete(Song.songUri(id), null, null)
                } catch (e: Exception) {
                    // skip files we can't delete
                }
            }
        }
        afterDelete(plan)
    }

    // Android 11+: one system confirmation for all files.
    val systemDelete = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val plan = pending
        pending = null
        if (result.resultCode == Activity.RESULT_OK && plan != null) {
            scope.launch {
                busy = true
                afterDelete(plan)
                busy = false
            }
        } else {
            toast(context, "Nothing deleted")
        }
    }

    // Android 8–10: needs storage write permission.
    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val plan = pending
        pending = null
        if (granted && plan != null) {
            scope.launch {
                busy = true
                deleteDirectly(plan)
                busy = false
            }
        } else {
            toast(context, "Storage permission is needed to delete songs")
        }
    }

    fun runDelete(plan: DeletePlan) {
        if (plan.ids.isEmpty()) return
        pending = plan
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val request = MediaStore.createDeleteRequest(context.contentResolver, plan.ids.map { Song.songUri(it) })
                systemDelete.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } catch (e: Exception) {
                pending = null
                toast(context, "Could not delete: ${e.message}")
            }
        } else if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            pending = null
            scope.launch {
                busy = true
                deleteDirectly(plan)
                busy = false
            }
        } else {
            writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = DhunColors.Text)
            }
            Text("Duplicate songs", color = DhunColors.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }

        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeChip("By name", !bySound) { bySound = false }
            ModeChip("By sound", bySound) { bySound = true }
        }

        if (bySound && scan !is ScanState.Done) {
            SoundScanCard(scan, songs.size, onStart = { SoundScanner.start(context, songs) }, onCancel = { SoundScanner.cancel() })
            return@Column
        }

        if (groups.isEmpty()) {
            if (bySound) {
                SectionEmpty("No songs sound the same", "Every song on your phone sounds different from the others.")
                TextButton(onClick = { SoundScanner.start(context, songs) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Scan again", color = DhunColors.Accent, fontWeight = FontWeight.SemiBold)
                }
            } else {
                SectionEmpty("No duplicates by name", "Try \u201CBy sound\u201D to find the same song saved under different names.")
            }
            return@Column
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "summary") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(DhunColors.Surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "${songCount(groups.size)} have copies",
                        color = DhunColors.Text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "$extraCopies extra copies · ${formatBytes(extraBytes)} can be freed",
                        color = DhunColors.Accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        (if (bySound) "Matched by how they sound — name, quality and format don't matter. " else "") +
                            "Ticked copies will be deleted from your phone. The best-quality copy of each song is kept, " +
                            "and your playlists and shuffle history move to it.",
                        color = DhunColors.Muted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            selected.clear()
                            groups.forEach { g -> g.copies.drop(1).forEach { selected.add(it.id) } }
                        }) { Text("Select all extras", color = DhunColors.Accent, fontWeight = FontWeight.SemiBold) }
                        TextButton(onClick = { selected.clear() }) {
                            Text("Clear", color = DhunColors.Muted)
                        }
                        if (bySound) {
                            TextButton(onClick = { SoundScanner.start(context, songs) }) {
                                Text("Scan again", color = DhunColors.Muted)
                            }
                        }
                    }
                }
            }
            items(groups, key = { "g_${it.keeper.id}" }) { g ->
                GroupCard(
                    group = g,
                    isSelected = { it in selectedSet },
                    onToggle = { id -> if (id in selectedSet) selected.remove(id) else selected.add(id) },
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .background(DhunColors.Bg)
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            Button(
                onClick = {
                    val plan = makePlan()
                    if (Build.VERSION.SDK_INT >= 30) runDelete(plan) else confirmLegacy = plan
                },
                enabled = selected.isNotEmpty() && !busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DhunColors.Accent,
                    contentColor = DhunColors.OnAccent,
                    disabledContainerColor = DhunColors.Surface2,
                    disabledContentColor = DhunColors.Muted,
                ),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = DhunColors.OnAccent, strokeWidth = 2.5.dp)
                } else {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (selected.isEmpty()) "Select copies to delete"
                        else "Delete ${songCount(selected.size)} · ${formatBytes(selectedBytes)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    confirmLegacy?.let { plan ->
        ConfirmDialog(
            title = "Delete ${songCount(plan.ids.size)}?",
            body = "These files will be permanently removed from your phone (${formatBytes(plan.bytes)}).",
            confirmLabel = "Delete",
            onDismiss = { confirmLegacy = null },
            onConfirm = {
                confirmLegacy = null
                runDelete(plan)
            },
        )
    }
}

@Composable
private fun GroupCard(group: DuplicateGroup, isSelected: (Long) -> Boolean, onToggle: (Long) -> Unit) {
    val keeper = group.keeper
    val allTicked = group.copies.all { isSelected(it.id) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(DhunColors.Surface)
            .padding(vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SongArt(keeper, Modifier.size(46.dp), corner = 12.dp)
            Column(Modifier.weight(1f)) {
                Text(keeper.title, color = DhunColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${keeper.artist} · ${formatDuration(keeper.durationMs)}",
                    color = DhunColors.Muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${group.copies.size} copies", color = DhunColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                group.matchPercent?.let {
                    Text("$it% same", color = DhunColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        group.copies.forEachIndexed { index, s ->
            val ticked = isSelected(s.id)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(s.id) }
                    .padding(start = 4.dp, end = 14.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = ticked,
                    onCheckedChange = { onToggle(s.id) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = DhunColors.Accent,
                        checkmarkColor = DhunColors.OnAccent,
                        uncheckedColor = DhunColors.Muted,
                    ),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        s.fileName.ifBlank { s.title },
                        color = if (ticked) DhunColors.Muted else DhunColors.Text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val quality = buildList {
                        add(formatBytes(s.sizeBytes))
                        if (s.bitrate > 0) add("${s.bitrate / 1000} kbps")
                        add(s.folderName)
                    }.joinToString(" · ")
                    Text(quality, color = DhunColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (index == 0) {
                    Text(
                        "Best",
                        color = DhunColors.OnAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DhunColors.Accent)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        if (allTicked) {
            Text(
                "All copies ticked — this song will be gone completely.",
                color = DhunColors.Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ModeChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(if (on) DhunColors.Accent else DhunColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (on) DhunColors.OnAccent else DhunColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SoundScanCard(scan: ScanState, songTotal: Int, onStart: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(DhunColors.Surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = DhunColors.Accent, modifier = Modifier.size(34.dp))
        when (scan) {
            is ScanState.Listening -> {
                Text("Listening to your songs…", color = DhunColors.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                LinearProgressIndicator(
                    progress = { if (scan.total > 0) scan.done.toFloat() / scan.total else 0f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = DhunColors.Accent,
                    trackColor = DhunColors.Chip,
                )
                Text("${scan.done} of ${scan.total} songs", color = DhunColors.Muted, fontSize = 13.sp)
                Text(
                    "You can leave this screen — the scan keeps going. Keep the app open.",
                    color = DhunColors.Muted,
                    fontSize = 12.sp,
                )
                TextButton(onClick = onCancel) { Text("Stop", color = DhunColors.Muted) }
            }
            is ScanState.Matching -> {
                Text("Comparing songs…", color = DhunColors.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                LinearProgressIndicator(
                    progress = { scan.progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = DhunColors.Accent,
                    trackColor = DhunColors.Chip,
                )
            }
            else -> {
                Text("Find songs that sound the same", color = DhunColors.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Dhun listens to about a minute of every song and makes a sound fingerprint, like Shazam. " +
                        "Songs downloaded from different sites, with different names, quality or format are matched " +
                        "if they are the same recording.",
                    color = DhunColors.Muted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Text(
                    "First scan of $songTotal songs takes a few minutes. Later scans only check new songs.",
                    color = DhunColors.Muted,
                    fontSize = 12.sp,
                )
                Button(
                    onClick = onStart,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DhunColors.Accent, contentColor = DhunColors.OnAccent),
                ) { Text("Start sound scan", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
