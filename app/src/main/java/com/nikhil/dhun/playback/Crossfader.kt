package com.nikhil.dhun.playback

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import com.nikhil.dhun.data.PlaybackPrefs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Spotify-style crossfade with two players.
 *
 * The main player (the one in the notification) always plays the *new* song. A second "fader"
 * player plays the *old* song's ending while it fades out.
 *
 *  ARMED    ~5 s before the fade: fader loads the old song, paused exactly at the fade point
 *           (index seeking = sample-accurate even for VBR mp3). Nothing audible changes.
 *  STARTING a few ms before the fade point the ready fader starts, so there is no gap.
 *  FADING   as soon as the fader is really playing, the main player jumps to the next song
 *           at volume 0; old fades out / new fades in on equal-power curves.
 */
@OptIn(UnstableApi::class)
class Crossfader(
    private val context: Context,
    private val handler: Handler,
    private val attributes: AudioAttributes,
    private val mainPlayer: () -> Player?,
) {
    private enum class Phase { IDLE, ARMED, STARTING, FADING }

    private var phase = Phase.IDLE
    private var fader: ExoPlayer? = null
    private var armedTag: String? = null
    private var armedAt = 0L
    private var oldDuration = 0L
    private var fadeStart = 0L
    private var fadeLength = 0L
    private var startDeadline = 0L
    private var lastFadedTag: String? = null
    private var ownSeek = false

    companion object {
        /** Accurate seeking for mp3 (VBR) and constant-bitrate files. */
        fun accurateExtractors(): DefaultExtractorsFactory =
            DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
                .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING)

        private const val ARM_AHEAD_MS = 5_000L
        private const val START_LEAD_MS = 60L
    }

    private fun faderPlayer(): ExoPlayer =
        fader ?: ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context, accurateExtractors()))
            .setAudioAttributes(attributes, false) // never take audio focus from the main player
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .also { fader = it }

    private fun tagOf(p: Player): String? {
        val item = p.currentMediaItem ?: return null
        return "${p.currentMediaItemIndex}:${item.mediaId}"
    }

    /** Called every 250 ms by the service. */
    fun tick() {
        val p = mainPlayer() ?: return
        if (phase == Phase.STARTING || phase == Phase.FADING) return
        val cf = PlaybackPrefs.crossfadeSec * 1000L
        val eligible = cf > 0L && p.isPlaying &&
            p.repeatMode != Player.REPEAT_MODE_ONE && p.hasNextMediaItem()
        val duration = p.duration
        val item = p.currentMediaItem
        val tag = tagOf(p)
        if (!eligible || duration == C.TIME_UNSET || duration < cf * 2 + 5_000L ||
            item == null || item.localConfiguration == null || tag == null || tag == lastFadedTag
        ) {
            if (phase == Phase.ARMED) cancel()
            return
        }
        if (phase == Phase.ARMED) {
            if (tag != armedTag) cancel() else return
        }
        val pos = p.currentPosition
        val fadeAt = duration - cf
        when {
            pos < fadeAt - ARM_AHEAD_MS -> return
            pos <= fadeAt - 300 -> arm(item, tag, fadeAt, duration)
            // Jumped into the last seconds: fade the remaining part (if there is enough left).
            duration - pos > 2_500L -> arm(item, tag, pos + 500, duration)
        }
    }

    private fun arm(item: MediaItem, tag: String, at: Long, duration: Long) {
        val f = faderPlayer()
        f.playWhenReady = false
        f.volume = 1f
        f.setMediaItem(item, at)
        f.prepare()
        phase = Phase.ARMED
        armedTag = tag
        armedAt = at
        oldDuration = duration
        handler.removeCallbacks(watch)
        handler.post(watch)
    }

    private val watch = object : Runnable {
        override fun run() {
            val p = mainPlayer()
            val f = fader
            if (p == null || f == null) {
                cancel()
                return
            }
            when (phase) {
                Phase.ARMED -> {
                    if (tagOf(p) != armedTag) {
                        cancel()
                        return
                    }
                    val toStart = armedAt - p.currentPosition
                    if (p.isPlaying && toStart <= START_LEAD_MS) {
                        if (f.playbackState == Player.STATE_READY) {
                            f.play()
                            phase = Phase.STARTING
                            startDeadline = SystemClock.elapsedRealtime() + 700L
                        } else if (toStart < -1_000L) {
                            // Fader could not get ready in time: skip the fade for this song.
                            lastFadedTag = armedTag
                            cancel()
                            return
                        }
                    }
                    handler.postDelayed(this, if (toStart > 600) 100L else 5L)
                }
                Phase.STARTING -> {
                    val reallyPlaying = f.isPlaying && f.currentPosition > armedAt + 5
                    if (reallyPlaying || SystemClock.elapsedRealtime() > startDeadline) {
                        beginFade(p, f)
                    } else {
                        handler.postDelayed(this, 5L)
                    }
                }
                else -> Unit
            }
        }
    }

    private fun beginFade(p: Player, f: ExoPlayer) {
        lastFadedTag = armedTag
        phase = Phase.FADING
        fadeLength = (oldDuration - f.currentPosition).coerceAtLeast(1_000L)
        fadeStart = SystemClock.elapsedRealtime()
        p.volume = 0f
        ownSeek = true
        p.seekToNextMediaItem()
        handler.post(ramp)
    }

    private val ramp = object : Runnable {
        override fun run() {
            if (phase != Phase.FADING) return
            val p = mainPlayer()
            val f = fader
            if (p == null || f == null) {
                cancel()
                return
            }
            val t = ((SystemClock.elapsedRealtime() - fadeStart).toFloat() / fadeLength).coerceIn(0f, 1f)
            // Equal-power curves keep the overall loudness steady through the blend.
            p.volume = sin(t * PI / 2).toFloat()
            f.volume = cos(t * PI / 2).toFloat()
            if (t >= 1f || f.playbackState == Player.STATE_ENDED) cancel() else handler.postDelayed(this, 30L)
        }
    }

    /** Stop any crossfade work and return to normal playback. */
    fun cancel() {
        handler.removeCallbacks(watch)
        handler.removeCallbacks(ramp)
        fader?.let {
            it.pause()
            it.stop()
            it.clearMediaItems()
        }
        mainPlayer()?.volume = 1f
        phase = Phase.IDLE
        armedTag = null
    }

    // ---- events from the main player

    fun onPositionDiscontinuity(reason: Int) {
        when (reason) {
            Player.DISCONTINUITY_REASON_SEEK -> {
                if (ownSeek) ownSeek = false else if (phase != Phase.IDLE) cancel()
            }
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> {
                if (phase == Phase.ARMED || phase == Phase.STARTING) cancel()
            }
            else -> Unit
        }
    }

    fun onPausedOrInterrupted() {
        if (phase != Phase.IDLE) cancel()
    }

    fun release() {
        cancel()
        fader?.release()
        fader = null
    }
}
