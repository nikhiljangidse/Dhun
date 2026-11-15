package com.nikhil.dhun.data.sound

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/** Decodes part of an audio file to mono PCM at a low sample rate (enough for fingerprinting). */
object AudioDecoder {

    fun decodeMono(context: Context, uri: Uri, startUs: Long, lengthUs: Long, targetRate: Int): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    track = i
                    format = f
                    break
                }
            }
            if (track < 0 || format == null) return null
            extractor.selectTrack(track)
            if (startUs > 0) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(format, null, null, 0)
            c.start()

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            var isFloat = false
            var ratio = sampleRate.toDouble() / targetRate

            val wanted = (lengthUs / 1_000_000.0 * targetRate).toInt()
            val out = FloatArray(wanted)
            var outCount = 0
            var acc = 0.0
            var accN = 0
            var phase = 0.0

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val stopFeedingAtUs = startUs + lengthUs + 1_000_000L
            var spins = 0

            while (!outputDone && outCount < wanted && spins < 100_000) {
                spins++
                if (!inputDone) {
                    val inIdx = c.dequeueInputBuffer(5_000)
                    if (inIdx >= 0) {
                        val buf = c.getInputBuffer(inIdx)
                        val size = if (buf != null) extractor.readSampleData(buf, 0) else -1
                        val time = extractor.sampleTime
                        if (size < 0 || time > stopFeedingAtUs) {
                            c.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(inIdx, 0, size, time, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = c.dequeueOutputBuffer(info, 5_000)
                if (outIdx >= 0) {
                    val buf = c.getOutputBuffer(outIdx)
                    if (buf != null && info.size > 0) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val ordered = buf.slice().order(ByteOrder.nativeOrder())
                        if (isFloat) {
                            val fb = ordered.asFloatBuffer()
                            val frames = fb.remaining() / channels
                            for (fr in 0 until frames) {
                                var sum = 0f
                                for (ch in 0 until channels) sum += fb.get()
                                acc += sum / channels
                                accN++
                                phase += 1.0
                                if (phase >= ratio) {
                                    if (outCount < wanted) out[outCount++] = (acc / accN).toFloat()
                                    acc = 0.0
                                    accN = 0
                                    phase -= ratio
                                }
                            }
                        } else {
                            val sb = ordered.asShortBuffer()
                            val frames = sb.remaining() / channels
                            for (fr in 0 until frames) {
                                var sum = 0f
                                for (ch in 0 until channels) sum += sb.get() / 32768f
                                acc += sum / channels
                                accN++
                                phase += 1.0
                                if (phase >= ratio) {
                                    if (outCount < wanted) out[outCount++] = (acc / accN).toFloat()
                                    acc = 0.0
                                    accN = 0
                                    phase -= ratio
                                }
                            }
                        }
                    }
                    c.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = c.outputFormat
                    sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                    ratio = sampleRate.toDouble() / targetRate
                    isFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                }
            }
            return if (outCount > targetRate * 8) out.copyOf(outCount) else null
        } catch (e: Exception) {
            return null
        } finally {
            try {
                codec?.stop()
            } catch (e: Exception) {
                // ignore
            }
            try {
                codec?.release()
            } catch (e: Exception) {
                // ignore
            }
            extractor.release()
        }
    }
}
