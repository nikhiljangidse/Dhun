package com.nikhil.dhun.playback

import android.content.Context
import com.nikhil.dhun.data.HistoryStore
import com.nikhil.dhun.data.Song

/**
 * Smart shuffle:
 *  1. Songs NOT heard in this source's current cycle come first, in random order.
 *  2. Songs already heard follow, oldest-heard first (so recent ones come last).
 *  3. If every song was already heard, the cycle restarts and everything is fresh again.
 */
object SmartShuffle {
    fun order(context: Context, songs: List<Song>, key: String): List<Song> {
        if (songs.isEmpty()) return songs
        HistoryStore.init(context)
        val start = HistoryStore.cycleStart(key)
        val fresh = songs.filter { HistoryStore.lastHeard(it.id) <= start }
        if (fresh.isEmpty()) {
            HistoryStore.resetCycle(context, key)
            return songs.shuffled()
        }
        val heard = songs.filter { HistoryStore.lastHeard(it.id) > start }
            .sortedBy { HistoryStore.lastHeard(it.id) }
        return fresh.shuffled() + heard
    }
}
