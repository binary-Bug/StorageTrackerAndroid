package com.binarybug.storagetracker.scan

import com.binarybug.storagetracker.data.SnapshotMeta

/**
 * Whether free space is going up, going down, or holding steady over a number of days.
 *
 * It compares the typical free space at the start of the period with the typical free space at the end (the median of the
 * first third and of the last third of the snapshots), not single readings. So a quick jump that disappears again, such as
 * clearing a cache that Android refills within hours, leaves the result at "stable": you are back where you were.
 */
class FreeStability(
    val trend: Trend,
    /** Typical free space at the start and at the end of the period. */
    val startFree: Long,
    val endFree: Long,
    val perDayBytes: Double,
    val minFree: Long,
    val maxFree: Long,
    val basedOn: Int,
    val spanDays: Double,
) {
    enum class Trend { UP, DOWN, STABLE }

    val netBytes: Long get() = endFree - startFree

    companion object {
        const val MIN_POINTS = 4
        const val MIN_SPAN_DAYS = 0.5
        private const val MIN_GAP_DAYS = 0.1
        private const val DAY_MS = 86_400_000.0

        /** Stable when the net change is under 300 MB, or under 100 MB a day: neither a gain nor a loss worth noticing. */
        private const val STABLE_NET_BYTES = 300_000_000L
        private const val STABLE_BYTES_PER_DAY = 100_000_000.0

        /** [metas] is sorted oldest first. Null when there are fewer than 4 snapshots, or they cover less than 12 hours. */
        fun of(metas: List<SnapshotMeta>, nowMs: Long, windowDays: Int): FreeStability? {
            val recent = metas.filter { it.takenMs >= nowMs - windowDays * DAY_MS.toLong() }
            if (recent.size < MIN_POINTS) return null
            val span = (recent.last().takenMs - recent.first().takenMs) / DAY_MS
            if (span < MIN_SPAN_DAYS) return null

            val k = maxOf(2, recent.size / 3)
            val head = recent.take(k)
            val tail = recent.takeLast(k)
            val start = median(head.map { it.free })
            val end = median(tail.map { it.free })
            val gapDays = (tail.map { it.takenMs }.average() - head.map { it.takenMs }.average()) / DAY_MS
            if (gapDays < MIN_GAP_DAYS) return null

            val net = end - start
            val perDay = net / gapDays
            val trend = when {
                Math.abs(net) < STABLE_NET_BYTES || Math.abs(perDay) < STABLE_BYTES_PER_DAY -> Trend.STABLE
                net > 0 -> Trend.UP
                else -> Trend.DOWN
            }
            return FreeStability(trend, start, end, perDay, recent.minOf { it.free }, recent.maxOf { it.free }, recent.size, span)
        }

        private fun median(values: List<Long>): Long {
            val s = values.sorted()
            return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
        }
    }
}
