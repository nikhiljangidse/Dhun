package com.nikhil.dhun.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nikhil.dhun.MainActivity
import com.nikhil.dhun.data.HistoryStore
import com.nikhil.dhun.data.PlaybackPrefs
import com.nikhil.dhun.data.Song

/**
 * Plays music in the background as a foreground service with a media notification,
 * so Android does not stop it midway. Also marks songs as "heard" for smart shuffle
 * and saves the queue so the app resumes where you left off.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var markedId: String? = null
    private var tickCount = 0

    private val musicAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private lateinit var crossfader: Crossfader

    private val ticker = object : Runnable {
        override fun run() {
            crossfader.tick()
            if (tickCount++ % 12 == 0) tick() // heard-tracking + save every ~3 s
            handler.postDelayed(this, 250L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        com.nikhil.dhun.data.CrashLog.install(this)
        HistoryStore.init(this)
        PlaybackPrefs.init(this)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this, Crossfader.accurateExtractors()))
            .setAudioAttributes(musicAttributes, true) // pause for calls / other apps, resume after
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_LOCAL) // keep CPU awake while playing
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                markedId = null
                saveProgress()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                crossfader.onPositionDiscontinuity(reason)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) crossfader.onPausedOrInterrupted() // paused / headphones out
            }

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                if (playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) crossfader.onPausedOrInterrupted()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                saveQueue()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) saveProgress()
            }
        })

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        crossfader = Crossfader(this, handler, musicAttributes) { session?.player }
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(SessionCallback())
            .build()
        handler.post(ticker)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Keep playing when the app is swiped away from recents; stop only if idle. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        crossfader.release()
        saveQueue()
        saveProgress()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun tick() {
        val p = session?.player ?: return
        if (!p.isPlaying) return
        val id = p.currentMediaItem?.mediaId
        if (id != null && id != markedId) {
            val duration = p.duration
            val threshold = if (duration > 0) minOf(30_000L, duration / 2) else 30_000L
            if (p.currentPosition >= threshold) {
                id.toLongOrNull()?.let { HistoryStore.markHeard(this, it) }
                markedId = id
            }
        }
        saveProgress()
    }

    private fun saveQueue() {
        val p = session?.player ?: return
        val ids = (0 until p.mediaItemCount).mapNotNull { p.getMediaItemAt(it).mediaId.toLongOrNull() }
        if (ids.isNotEmpty()) PlaybackPrefs.saveQueue(this, ids)
    }

    private fun saveProgress() {
        val p = session?.player ?: return
        if (p.mediaItemCount == 0) return
        PlaybackPrefs.saveProgress(this, p.currentMediaItemIndex, p.currentPosition)
    }

    /** Items from the UI carry only an id; turn each into a playable content:// uri. */
    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                if (item.localConfiguration != null) {
                    item
                } else {
                    val id = item.mediaId.toLongOrNull()
                    if (id == null) item else item.buildUpon().setUri(Song.songUri(id)).build()
                }
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }
}
