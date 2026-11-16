package com.nikhil.dhun.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nikhil.dhun.data.HistoryStore
import com.nikhil.dhun.data.MusicLibrary
import com.nikhil.dhun.data.PlaybackPrefs
import com.nikhil.dhun.data.Playlist
import com.nikhil.dhun.data.PlaylistStore
import com.nikhil.dhun.data.Song
import com.nikhil.dhun.data.Source
import com.nikhil.dhun.data.heardCountIds
import com.nikhil.dhun.data.songsFor
import com.nikhil.dhun.playback.PlayerConnection
import com.nikhil.dhun.ui.theme.DhunColors
import kotlinx.coroutines.launch

private sealed interface Route {
    data object Library : Route
    data object Search : Route
    data class Collection(val source: Source) : Route
    data class PickSongs(val playlistId: Long) : Route
    data object Duplicates : Route
}

@Composable
fun DhunApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val songs by MusicLibrary.songs.collectAsState()
    val loaded by MusicLibrary.loaded.collectAsState()
    val playlists by PlaylistStore.playlists.collectAsState()
    val heard by HistoryStore.heard.collectAsState()
    val cycles by HistoryStore.cycles.collectAsState()
    val byId = remember(songs) { songs.associateBy { it.id } }

    val stack = remember { mutableStateListOf<Route>(Route.Library) }
    var libTab by rememberSaveable { mutableStateOf(LibTab.Songs) }
    var showPlayer by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var addTarget by remember { mutableStateOf<Song?>(null) }
    var newPlaylist by remember { mutableStateOf(false) }
    var newPlaylistSong by remember { mutableStateOf<Song?>(null) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }

    var crashReport by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        crashReport = com.nikhil.dhun.data.CrashLog.read(context)
        com.nikhil.dhun.data.CrashLog.clear(context)
    }

    LaunchedEffect(PlayerConnection.connected, loaded) {
        if (PlayerConnection.connected && loaded) PlayerConnection.restoreIfIdle(context)
    }

    val current = PlayerConnection.currentId?.let { byId[it] }
    LaunchedEffect(current == null) {
        if (current == null) {
            showPlayer = false
            showQueue = false
        }
    }

    val actions = remember {
        AppActions(
            play = { src, list, start, shuffle -> PlayerConnection.play(context, src, list, start, shuffle) },
            open = { src -> stack.add(Route.Collection(src)) },
            playNext = { s ->
                PlayerConnection.playNext(s)
                toast(context, "Plays next")
            },
            addToQueue = { s ->
                PlayerConnection.addToQueue(s)
                toast(context, "Added to queue")
            },
            addToPlaylist = { s -> addTarget = s },
            createPlaylist = {
                newPlaylistSong = null
                newPlaylist = true
            },
        )
    }

    fun popRoute() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    BackHandler(enabled = showQueue) { showQueue = false }
    BackHandler(enabled = showPlayer && !showQueue) { showPlayer = false }
    BackHandler(enabled = !showPlayer && stack.size > 1) { popRoute() }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listBottom = navBottom + if (current != null) 96.dp else 16.dp

    Box(Modifier.fillMaxSize().background(DhunColors.Bg)) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            when (val route = stack.last()) {
                Route.Library -> LibraryScreen(
                    tab = libTab,
                    onTab = { libTab = it },
                    loaded = loaded,
                    songs = songs,
                    playlists = playlists,
                    byId = byId,
                    heard = heard,
                    cycles = cycles,
                    currentId = current?.id,
                    actions = actions,
                    onSearch = { stack.add(Route.Search) },
                    onSettings = { showSettings = true },
                    bottomPadding = listBottom,
                )

                Route.Search -> SearchScreen(
                    songs = songs,
                    currentId = current?.id,
                    actions = actions,
                    onBack = { popRoute() },
                    bottomPadding = listBottom,
                )

                is Route.Collection -> {
                    val src = route.source
                    val playlistId = if (src.key.startsWith("playlist:")) src.key.removePrefix("playlist:").toLongOrNull() else null
                    val playlist = playlistId?.let { id -> playlists.firstOrNull { it.id == id } }
                    if (playlistId != null && playlist == null) {
                        // Playlist was deleted.
                        LaunchedEffect(Unit) { popRoute() }
                    } else {
                        val shownSource = if (playlist != null) src.copy(title = playlist.name) else src
                        val list = songsFor(shownSource, songs, byId, playlists, PlaybackPrefs.sort)
                        CollectionScreen(
                            source = shownSource,
                            songs = list,
                            isPlaylist = playlist != null,
                            heard = heard,
                            cycles = cycles,
                            currentId = current?.id,
                            actions = actions,
                            onBack = { popRoute() },
                            onStartFresh = {
                                HistoryStore.resetCycle(context, src.key)
                                toast(context, "New shuffle cycle started")
                            },
                            onAddSongs = { playlist?.let { stack.add(Route.PickSongs(it.id)) } },
                            onRename = { renameTarget = playlist },
                            onDelete = { deleteTarget = playlist },
                            onRemoveSong = { s -> playlist?.let { PlaylistStore.removeSong(context, it.id, s.id) } },
                            bottomPadding = listBottom,
                        )
                    }
                }

                Route.Duplicates -> DuplicatesScreen(songs = songs, onBack = { popRoute() })

                is Route.PickSongs -> {
                    val pl = playlists.firstOrNull { it.id == route.playlistId }
                    if (pl == null) {
                        LaunchedEffect(Unit) { popRoute() }
                    } else {
                        PickSongsScreen(
                            playlistName = pl.name,
                            songs = songs,
                            alreadyIn = pl.songIds.toSet(),
                            onBack = { popRoute() },
                            onAdd = { ids ->
                                val n = PlaylistStore.addSongs(context, pl.id, ids)
                                toast(context, "Added ${songCount(n)} to ${pl.name}")
                                popRoute()
                            },
                        )
                    }
                }
            }
        }

        // Screens with their own bottom button (delete / add) hide the mini player.
        val ownBottomBar = stack.last() is Route.Duplicates || stack.last() is Route.PickSongs
        if (current != null) {
            AnimatedVisibility(
                visible = !showPlayer && !ownBottomBar,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                MiniPlayer(
                    song = current,
                    onOpen = { showPlayer = true },
                    modifier = Modifier.navigationBarsPadding().padding(horizontal = 10.dp, vertical = 10.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = showPlayer && current != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            if (current != null) {
                val src = PlaybackPrefs.source
                val shuffleInfo = src?.let { s ->
                    val ids = PlaybackPrefs.sourceSongIds
                    if (ids.isEmpty()) null
                    else {
                        val left = ids.size - heardCountIds(ids, s.key, heard, cycles)
                        "Smart shuffle · $left of ${ids.size} not heard yet"
                    }
                }
                val queue = PlayerConnection.queueIds
                val nextIndex = PlayerConnection.currentIndex + 1
                val upNextId = queue.getOrNull(nextIndex)
                    ?: if (PlayerConnection.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ALL) queue.firstOrNull() else null
                NowPlayingScreen(
                    song = current,
                    sourceLabel = src?.label ?: "LIBRARY",
                    sourceTitle = src?.title ?: "Your music",
                    shuffleOn = PlaybackPrefs.shuffle,
                    shuffleInfo = shuffleInfo,
                    upNext = upNextId?.let { byId[it] },
                    onClose = { showPlayer = false },
                    onToggleShuffle = {
                        PlayerConnection.toggleShuffle(context)
                        toast(context, if (PlaybackPrefs.shuffle) "Smart shuffle on" else "Shuffle off")
                    },
                    onOpenQueue = { showQueue = true },
                    onAddToPlaylist = { addTarget = current },
                )
            }
        }

        AnimatedVisibility(
            visible = showQueue && showPlayer,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            QueueScreen(byId = byId, onClose = { showQueue = false })
        }
    }

    // ---- Dialogs ----
    addTarget?.let { song ->
        AddToPlaylistDialog(
            song = song,
            playlists = playlists,
            onDismiss = { addTarget = null },
            onPick = { pl ->
                PlaylistStore.addSongs(context, pl.id, listOf(song.id))
                toast(context, "Added to ${pl.name}")
                addTarget = null
            },
            onNew = {
                newPlaylistSong = song
                addTarget = null
                newPlaylist = true
            },
        )
    }

    if (newPlaylist) {
        NameDialog(
            title = "New playlist",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { newPlaylist = false },
            onConfirm = { name ->
                val id = PlaylistStore.create(context, name)
                val s = newPlaylistSong
                if (s != null) {
                    PlaylistStore.addSongs(context, id, listOf(s.id))
                    toast(context, "Added to $name")
                } else {
                    stack.add(Route.Collection(Source.playlist(id, name)))
                    stack.add(Route.PickSongs(id))
                }
                newPlaylistSong = null
                newPlaylist = false
            },
        )
    }

    renameTarget?.let { pl ->
        NameDialog(
            title = "Rename playlist",
            initial = pl.name,
            confirmLabel = "Save",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                PlaylistStore.rename(context, pl.id, name)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { pl ->
        ConfirmDialog(
            title = "Delete “${pl.name}”?",
            body = "The playlist is removed. Your songs stay on the phone.",
            confirmLabel = "Delete",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                PlaylistStore.delete(context, pl.id)
                deleteTarget = null
            },
        )
    }

    crashReport?.let { report ->
        CrashDialog(report = report, onDismiss = { crashReport = null })
    }

    if (showSettings) {
        SettingsDialog(
            onDismiss = { showSettings = false },
            onRescan = {
                scope.launch {
                    MusicLibrary.refresh(context)
                    toast(context, "Found ${songCount(MusicLibrary.songs.value.size)}")
                }
            },
            onFindDuplicates = { stack.add(Route.Duplicates) },
            onResetHistory = {
                HistoryStore.clearAll(context)
                toast(context, "Shuffle history cleared")
            },
        )
    }
}

@Composable
fun PermissionScreen(onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DhunColors.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = DhunColors.Accent, modifier = Modifier.size(64.dp))
        Text(
            "Play your own music",
            color = DhunColors.Text,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        Text(
            "Dhun needs access to the audio files on your phone. No ads, no account, nothing leaves your phone.",
            color = DhunColors.Muted,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
        )
        Button(
            onClick = onAllow,
            modifier = Modifier.padding(top = 28.dp).fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(27.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DhunColors.Accent, contentColor = DhunColors.OnAccent),
        ) {
            Text("Allow access to music", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onOpenSettings, modifier = Modifier.padding(top = 8.dp)) {
            Text("Open app settings", color = DhunColors.Muted)
        }
    }
}
