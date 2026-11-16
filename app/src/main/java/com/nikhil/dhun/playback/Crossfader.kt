package com.nikhil.dhun.playback

import android.os.Handler
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.session.MediaSession
import com.nikhil.dhun.data.PlaybackPrefs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Spotify-style crossfade with two players that take turns ("A/B decks").
 *
 * No song is ever handed from one player to the other, so there is no gap or repeat:
 *  ARMED   ~5 s before the fade the spare player loads the same queue, paused at the next song.
 *  FADING  at the fade point the spare starts the next song (volume 0 -> 1) and becomes the
 *          session's player (notification, controls, app all follow it). The old player keeps
 *          playing the end of the old song (volume 1 -> 0) and then stops.
 *  The old player becomes the spare for the next crossfade.
 */
@OptIn(UnstableApi::class)
class Crossfader(
    private val handler: Handler,
    private val attributes: AudioAttributes,
    private val session: () -> MediaSession?,
    private val newPlayer: () -> ExoPlayer,
) {
    private enum class Phase { IDLE, ARMED, FADING }

    private var phase = Phase.IDLE
    private var spare: ExoPlayer? = null
    private var fadingOut: ExoPlayer? = null
    private var armedTag: String? = null
    private var lastFadedTag: String? = null
    private var fadeStart = 0L
    private var fadeLength = 0L

    companion object {
        /** Accurate seeking for mp3 (VBR) and constant-bitrate files. */
        fun accurateExtractors(): DefaultExtractorsFactory =
            DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
                .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING)

        private const val ARM_AHEAD_MS = 5_000L
    }

    private fun current(): ExoPlayer? = session()?.player as? ExoPlayer

    private fun spare(): ExoPlayer = spare ?: newPlayer().also { spare = it }

    private fun tagOf(p: Player): String? {
        val item = p.currentMediaItem ?: return null
        return "${p.currentMediaItemIndex}:${item.mediaId}"
    }

    /** Called every 250 ms by the service. */
    fun tick() {
        if (phase == Phase.FADING) return
        val p = current() ?: return
        val cf = PlaybackPrefs.crossfadeSec * 1000L
        val duration = p.duration
        val tag = tagOf(p)
        val eligible = cf > 0L && p.isPlaying &&
            p.repeatMode != Player.REPEAT_MODE_ONE && p.hasNextMediaItem() &&
            duration != C.TIME_UNSET && duration >= cf * 2 + 5_000L &&
            tag != null && tag != lastFadedTag
        if (!eligible) {
            if (phase == Phase.ARMED) cancel()
            return
        }
        if (phase == Phase.ARMED && tag != armedTag) cancel()

        val pos = p.currentPosition
        val fadeAt = duration - cf
        if (phase == Phase.IDLE) {
            if (pos >= fadeAt - ARM_AHEAD_MS && duration - pos > 2_500L) arm(p, tag!!)
            return
        }
        // ARMED: start when we reach the fade point (or right away if we jumped past it).
        if (pos >= fadeAt) {
            val s = spare
            if (s != null && s.playbackState == Player.STATE_READY) {
                start(p, s)
            } else if (duration - pos < 1_500L) {
                lastFadedTag = tag
                cancel()
            }
        }
    }

    private fun arm(p: ExoPlayer, tag: String) {
        val s = spare()
        val items = List(p.mediaItemCount) { p.getMediaItemAt(it) }
        s.setAudioAttributes(attributes, false)
        s.playWhenReady = false
        s.volume = 0f
        s.repeatMode = p.repeatMode
        s.shuffleModeEnabled = p.shuffleModeEnabled
        s.setMediaItems(items, p.nextMediaItemIndex, 0L)
        s.prepare()
        phase = Phase.ARMED
        armedTag = tag
    }

    private fun start(old: ExoPlayer, next: ExoPlayer) {
        val sess = session()
        if (sess == null) {
            cancel()
            return
        }
        lastFadedTag = armedTag
        phase = Phase.FADING

        // Audio focus moves to the new player; the old one keeps playing without it.
        old.setAudioAttributes(attributes, false)
        next.setAudioAttributes(attributes, true)
        next.volume = 0f
        next.play()

        // Notification, lock screen and the app now follow the new player.
        sess.player = next

        // The old player only needs to finish the current song.
        val i = old.currentMediaItemIndex
        if (i + 1 < old.mediaItemCount) old.removeMediaItems(i + 1, old.mediaItemCount)
        if (i > 0) old.removeMediaItems(0, i)
        old.repeatMode = Player.REPEAT_MODE_OFF

        fadingOut = old
        spare = old
        fadeLength = (old.duration - old.currentPosition).coerceAtLeast(1_000L)
        fadeStart = SystemClock.elapsedRealtime()
        handler.post(ramp)
    }

    private val ramp = object : Runnable {
        override fun run() {
            if (phase != Phase.FADING) return
            val next = current()
            val old = fadingOut
            if (next == null || old == null) {
                finishFade()
                return
            }
            val t = ((SystemClock.elapsedRealtime() - fadeStart).toFloat() / fadeLength).coerceIn(0f, 1f)
            // Equal-power curves keep the overall loudness steady through the blend.
            next.volume = sin(t * PI / 2).toFloat()
            old.volume = cos(t * PI / 2).toFloat()
            if (t >= 1f || old.playbackState == Player.STATE_ENDED) finishFade() else handler.postDelayed(this, 30L)
        }
    }

    private fun finishFade() {
        handler.removeCallbacks(ramp)
        fadingOut?.let {
            it.volume = 0f
            it.stop()
            it.clearMediaItems()
        }
        fadingOut = null
        current()?.volume = 1f
        phase = Phase.IDLE
        armedTag = null
    }

    /** Stop any crossfade work and return to normal playback. */
    fun cancel() {
        when (phase) {
            Phase.ARMED -> spare?.let {
                it.stop()
                it.clearMediaItems()
            }
            Phase.FADING -> finishFade()
            Phase.IDLE -> Unit
        }
        current()?.volume = 1f
        phase = Phase.IDLE
        armedTag = null
    }

    // ---- events from the session's current player

    fun onDiscontinuity(reason: Int) {
        when (reason) {
            Player.DISCONTINUITY_REASON_SEEK,
            Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> if (phase != Phase.IDLE) cancel()
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> if (phase == Phase.ARMED) cancel()
            else -> Unit
        }
    }

    fun onQueueChanged() {
        if (phase == Phase.ARMED) cancel()
    }

    fun onPausedOrInterrupted() {
        if (phase != Phase.IDLE) cancel()
    }

    /** Release the spare player (the service releases the session's current player). */
    fun release() {
        cancel()
        val s = spare
        if (s != null && s !== current()) s.release()
        spare = null
    }
}
