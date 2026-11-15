package com.nikhil.dhun.data.sound

import android.content.Context
import com.nikhil.dhun.data.Song
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Audio fingerprint (Haitsma–Kalker style):
 * every ~46 ms we look at 33 frequency bands between 300 and 2000 Hz and store 32 bits saying
 * whether the energy difference between neighbouring bands went up or down since the last step.
 * The pattern survives different bitrates, formats and sources, so two downloads of the
 * same song give nearly identical bits, while different songs differ in ~50% of bits.
 */
object Fingerprinter {
    const val RATE = 5512
    const val FRAME = 2048
    const val HOP = 256
    private const val BANDS = 33
    private const val LEVELS = 11 // 2^11 = 2048

    /** Frames per second of fingerprint data. */
    val FPS: Double = RATE.toDouble() / HOP

    private val window = FloatArray(FRAME) { (0.5 - 0.5 * cos(2 * PI * it / (FRAME - 1))).toFloat() }
    private val edges = IntArray(BANDS + 1) { k ->
        val f = 300.0 * (2000.0 / 300.0).pow(k.toDouble() / BANDS)
        (f * FRAME / RATE).roundToInt()
    }
    private val rev = IntArray(FRAME) { Integer.reverse(it) ushr (32 - LEVELS) }
    private val cosTable = FloatArray(FRAME / 2) { cos(2 * PI * it / FRAME).toFloat() }
    private val sinTable = FloatArray(FRAME / 2) { sin(2 * PI * it / FRAME).toFloat() }

    /** Fingerprint of ~60 s of the song (from 10 s in, or from the start for short songs). */
    fun fromSong(context: Context, song: Song): IntArray? {
        val dur = song.durationMs
        val startMs = if (dur > 80_000L) 10_000L else 0L
        val lengthMs = minOf(60_000L, dur - startMs)
        if (lengthMs < 10_000L) return null
        val samples = AudioDecoder.decodeMono(context, song.uri, startMs * 1000, lengthMs * 1000, RATE) ?: return null
        val fp = compute(samples)
        return if (fp.size >= 100) fp else null
    }

    fun compute(samples: FloatArray): IntArray {
        val n = (samples.size - FRAME) / HOP + 1
        if (n < 3) return IntArray(0)
        val re = FloatArray(FRAME)
        val im = FloatArray(FRAME)
        var prev = DoubleArray(BANDS)
        var cur = DoubleArray(BANDS)
        val out = IntArray(n - 1)
        for (fIdx in 0 until n) {
            val base = fIdx * HOP
            for (i in 0 until FRAME) {
                re[i] = samples[base + i] * window[i]
                im[i] = 0f
            }
            fft(re, im)
            for (m in 0 until BANDS) {
                var e = 0.0
                for (b in edges[m] until edges[m + 1]) {
                    e += (re[b] * re[b] + im[b] * im[b]).toDouble()
                }
                cur[m] = e
            }
            if (fIdx > 0) {
                var bits = 0
                for (m in 0 until 32) {
                    val d = (cur[m] - cur[m + 1]) - (prev[m] - prev[m + 1])
                    if (d > 0) bits = bits or (1 shl m)
                }
                out[fIdx - 1] = bits
            }
            val t = prev
            prev = cur
            cur = t
        }
        return out
    }

    private fun fft(re: FloatArray, im: FloatArray) {
        val n = FRAME
        for (i in 0 until n) {
            val j = rev[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var i = 0
            while (i < n) {
                var k = 0
                for (j in i until i + half) {
                    val l = j + half
                    val c = cosTable[k]
                    val s = sinTable[k]
                    val tre = re[l] * c + im[l] * s
                    val tim = -re[l] * s + im[l] * c
                    re[l] = re[j] - tre
                    im[l] = im[j] - tim
                    re[j] += tre
                    im[j] += tim
                    k += step
                }
                i += size
            }
            size *= 2
        }
    }
}
