package com.nikhil.dhun.data.sound

/**
 * Finds songs whose fingerprints line up.
 * 1. Index every fingerprint frame by its two 16-bit halves.
 * 2. For each song, look up ~20 s of its frames; hits from another song that agree on the same
 *    time offset are votes. (This copes with different intros / silence at the start.)
 * 3. Candidates with enough votes are checked properly: bit error rate (BER) at that offset.
 *    Same recording: BER ~0.0–0.2. Different songs: ~0.5. We accept below [MAX_BER].
 */
object SoundMatcher {
    const val MAX_BER = 0.30f
    private const val MAX_POSTING = 400
    private const val KEYS = 1 shl 17

    data class Match(val a: Long, val b: Long, val ber: Float)

    fun find(fingerprints: Map<Long, IntArray>, onProgress: (Float) -> Unit = {}): List<Match> {
        val ids = fingerprints.keys.toLongArray()
        val arrs = Array(ids.size) { fingerprints.getValue(ids[it]) }
        if (arrs.size < 2) return emptyList()

        // ---- inverted index (every 2nd frame is enough because queries use every frame)
        val counts = IntArray(KEYS + 1)
        for (s in arrs.indices) {
            val a = arrs[s]
            var f = 0
            while (f < a.size) {
                val v = a[f]
                counts[v and 0xFFFF]++
                counts[0x10000 or (v ushr 16)]++
                f += 2
            }
        }
        val starts = IntArray(KEYS + 1)
        for (k in 0 until KEYS) starts[k + 1] = starts[k] + counts[k]
        val entries = LongArray(starts[KEYS])
        val fill = starts.copyOf()
        for (s in arrs.indices) {
            val a = arrs[s]
            var f = 0
            while (f < a.size) {
                val v = a[f]
                val packed = (s.toLong() shl 32) or f.toLong()
                entries[fill[v and 0xFFFF]++] = packed
                entries[fill[0x10000 or (v ushr 16)]++] = packed
                f += 2
            }
        }

        // ---- query
        val results = ArrayList<Match>()
        val votes = HashMap<Long, Int>()
        val fps = Fingerprinter.FPS
        for (si in arrs.indices) {
            if (si % 20 == 0) onProgress(si.toFloat() / arrs.size)
            val a = arrs[si]
            var q0 = (10 * fps).toInt()
            var q1 = (30 * fps).toInt()
            if (a.size < q1 + 5) {
                q0 = a.size / 4
                q1 = a.size * 3 / 4
            }
            if (q1 - q0 < 40) continue

            votes.clear()
            for (f in q0 until q1) {
                val v = a[f]
                vote(v and 0xFFFF, f, si, starts, entries, votes)
                vote(0x10000 or (v ushr 16), f, si, starts, entries, votes)
            }

            val best = HashMap<Int, Float>()
            votes.entries
                .filter { it.value >= 3 }
                .sortedByDescending { it.value }
                .take(30)
                .forEach { c ->
                    val sj = (c.key ushr 32).toInt()
                    val off = ((c.key and 0xFFFFFFFFL).toInt() - 100_000) * 2
                    val b = arrs[sj]
                    var lowest = 1f
                    for (d in off - 3..off + 3) {
                        val r = ber(a, q0, q1, b, d)
                        if (r < lowest) lowest = r
                    }
                    if (lowest < MAX_BER) {
                        val prev = best[sj]
                        if (prev == null || lowest < prev) best[sj] = lowest
                    }
                }
            best.forEach { (sj, r) -> results += Match(ids[si], ids[sj], r) }
        }
        onProgress(1f)
        return results
    }

    private fun vote(key: Int, f: Int, si: Int, starts: IntArray, entries: LongArray, votes: HashMap<Long, Int>) {
        val from = starts[key]
        val to = starts[key + 1]
        if (to - from > MAX_POSTING) return
        for (p in from until to) {
            val e = entries[p]
            val sj = (e ushr 32).toInt()
            if (sj <= si) continue
            val off = e.toInt() - f
            val vk = (sj.toLong() shl 32) or ((off shr 1) + 100_000).toLong()
            votes[vk] = (votes[vk] ?: 0) + 1
        }
    }

    /** Fraction of differing bits between a[from until to] and b shifted by [off]. */
    fun ber(a: IntArray, from: Int, to: Int, b: IntArray, off: Int): Float {
        var errs = 0L
        var cnt = 0
        for (i in from until to) {
            val j = i + off
            if (j < 0 || j >= b.size) continue
            errs += Integer.bitCount(a[i] xor b[j])
            cnt++
        }
        if (cnt < (to - from) / 2) return 1f
        return errs.toFloat() / (32f * cnt)
    }
}
