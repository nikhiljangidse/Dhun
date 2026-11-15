package com.nikhil.dhun.data

/** One song that exists more than once. [copies] is best-quality first; copies[0] is the one to keep. */
data class DuplicateGroup(val copies: List<Song>) {
    val keeper: Song get() = copies.first()
}

/**
 * Finds duplicate songs:
 *  1. Same cleaned-up title (ignoring "(1)", "copy", "[Official Video]", "320kbps", track numbers…)
 *     AND length within 2.5 seconds.
 *  2. Renamed copies: exactly the same file size and length.
 */
object Duplicates {
    private val brackets = Regex("""\(.*?\)|\[.*?]|\{.*?}""")
    private val leadingNumber = Regex("""^\d{1,3}[\s._-]+""")
    private val nonWord = Regex("""[^\p{L}\p{N}]+""")
    private val noise = Regex("""\b(official|video|audio|lyrics|lyric|lyrical|full song|hd|hq|4k|\d{2,3} ?kbps|kbps|mp3|m4a|copy)\b""")
    private val trailingCopyNumber = Regex("""\s\d$""")
    private val spaces = Regex("""\s+""")

    fun normalize(title: String): String {
        var s = title.lowercase()
        s = brackets.replace(s, " ")
        s = leadingNumber.replace(s.trim(), "")
        s = nonWord.replace(s, " ")
        s = noise.replace(s, " ")
        s = spaces.replace(s, " ").trim()
        s = trailingCopyNumber.replace(s, "")
        return s.trim()
    }

    private fun bestFirst(list: List<Song>): List<Song> =
        list.sortedWith(
            compareByDescending<Song> { it.bitrate }
                .thenByDescending { it.sizeBytes }
                .thenBy { it.dateAdded }
        )

    fun find(songs: List<Song>): List<DuplicateGroup> {
        val groups = ArrayList<DuplicateGroup>()
        val used = HashSet<Long>()

        songs.groupBy { normalize(it.title) }.forEach { (key, list) ->
            if (key.isEmpty() || list.size < 2) return@forEach
            val sorted = list.sortedBy { it.durationMs }
            var cluster = mutableListOf(sorted[0])
            for (s in sorted.drop(1)) {
                if (s.durationMs - cluster.first().durationMs <= 2_500L) {
                    cluster.add(s)
                } else {
                    if (cluster.size > 1) {
                        groups += DuplicateGroup(bestFirst(cluster))
                        cluster.forEach { used += it.id }
                    }
                    cluster = mutableListOf(s)
                }
            }
            if (cluster.size > 1) {
                groups += DuplicateGroup(bestFirst(cluster))
                cluster.forEach { used += it.id }
            }
        }

        songs.filter { it.id !in used && it.sizeBytes > 0 }
            .groupBy { it.sizeBytes to it.durationMs / 1000 }
            .values
            .filter { it.size > 1 }
            .forEach { groups += DuplicateGroup(bestFirst(it)) }

        return groups.sortedBy { it.keeper.title.lowercase() }
    }
}

fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        mb >= 1024 -> String.format(java.util.Locale.US, "%.1f GB", mb / 1024)
        mb >= 1 -> String.format(java.util.Locale.US, "%.1f MB", mb)
        else -> "${bytes / 1024} KB"
    }
}
