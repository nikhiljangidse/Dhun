package com.nikhil.dhun.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nikhil.dhun.MainActivity
import com.nikhil.dhun.data.HistoryStore
import com.nikhil.dhun.data.PlaybackPrefs
import com.nikhil.dhun.data.Song
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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

    // ---- Crossfade state ----
    /** Second, silent-to-the-system player that plays the old song's ending while it fades out. */
    private var fader: ExoPlayer? = null
    private var fading = false
    private var fadeStartAt = 0L
    private var fadeDuration = 0L
    private var fadedFrom: String? = null
    private var selfSkip = false

    private val musicAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private val ticker = object : Runnable {
        override fun run() {
            session?.player?.let { checkCrossfade(it) }
            if (tickCount++ % 12 == 0) tick() // heard-tracking + save every ~3 s
            handler.postDelayed(this, 250L)
        }
    }

    private val ramp = object : Runnable {
        override fun run() {
            val p = session?.player
            val f = fader
            if (p == null || f == null || !fading) return
            val t = ((SystemClock.elapsedRealtime() - fadeStartAt).toFloat() / fadeDuration).coerceIn(0f, 1f)
            // Equal-power curves: overall loudness stays even through the blend.
            p.volume = sin(t * PI / 2).toFloat()
            f.volume = cos(t * PI / 2).toFloat()
            if (t >= 1f) endCrossfade() else handler.postDelayed(this, 40L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        HistoryStore.init(this)
        PlaybackPrefs.init(this)

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(musicAttributes, true) // pause for calls / other apps, resume after
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_LOCAL) // keep CPU awake while playing
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                markedId = null
                if (selfSkip) {
                    selfSkip = false
                } else if (fading) {
                    endCrossfade() // user skipped during a fade
                }
                saveProgress()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && fading) endCrossfade() // paused / headphones out
            }

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                if (playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE && fading) endCrossfade()
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
        endCrossfade()
        fader?.release()
        fader = null
        saveQueue()
        saveProgress()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** Starts a crossfade when the current song is within the crossfade window of its end. */
    private fun checkCrossfade(p: Player) {
        val cf = PlaybackPrefs.crossfadeSec * 1000L
        if (cf <= 0L || fading || !p.isPlaying) return
        if (p.repeatMode == Player.REPEAT_MODE_ONE || !p.hasNextMediaItem()) return
        val duration = p.duration
        if (duration == C.TIME_UNSET || duration < cf * 2 + 5_000L) return
        val remaining = duration - p.currentPosition
        if (remaining > cf || remaining < 600L) return
        val item = p.currentMediaItem ?: return
        val tag = "${p.currentMediaItemIndex}:${item.mediaId}"
        if (tag == fadedFrom) return
        fadedFrom = tag
        startCrossfade(p, item, remaining)
    }

    private fun startCrossfade(p: Player, item: MediaItem, remaining: Long) {
        if (item.localConfiguration == null) return
        val f = fader ?: ExoPlayer.Builder(this)
            .setAudioAttributes(musicAttributes, false) // must NOT take audio focus from the main player
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .also { fader = it }
        f.volume = 1f
        f.setMediaItem(item, p.currentPosition + 120L)
        f.prepare()
        f.play()

        fading = true
        fadeDuration = remaining.coerceAtLeast(1_000L)
        fadeStartAt = SystemClock.elapsedRealtime()
        p.volume = 0f
        selfSkip = true
        p.seekToNextMediaItem()
        handler.post(ramp)
    }

    private fun endCrossfade() {
        handler.removeCallbacks(ramp)
        fader?.let {
            it.stop()
            it.clearMediaItems()
        }
        session?.player?.volume = 1f
        fading = false
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
