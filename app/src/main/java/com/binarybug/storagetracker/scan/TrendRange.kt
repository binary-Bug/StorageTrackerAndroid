package com.binarybug.storagetracker.scan

import com.binarybug.storagetracker.data.SnapshotMeta

/** How much history the "Used and free over time" chart shows. */
enum class TrendRange(val label: String) {
    LAST_5("Last 5"),
    LAST_10("Last 10"),
    DAY("1 day"),
    WEEK("1 week"),
    ALL("All");

    /**
     * The snapshots to plot, oldest first ([metas] is sorted oldest first). A time window always keeps at least the newest
     * snapshot, so the chart is never empty when snapshots exist.
     */
    fun select(metas: List<SnapshotMeta>, nowMs: Long): List<SnapshotMeta> = when (this) {
        LAST_5 -> metas.takeLast(5)
        LAST_10 -> metas.takeLast(10)
        DAY -> within(metas, nowMs, 24 * HOUR_MS)
        WEEK -> within(metas, nowMs, 7 * 24 * HOUR_MS)
        ALL -> metas
    }

    private fun within(metas: List<SnapshotMeta>, nowMs: Long, windowMs: Long): List<SnapshotMeta> {
        val inside = metas.filter { it.takenMs >= nowMs - windowMs }
        return if (inside.isEmpty()) metas.takeLast(1) else inside
    }

    private companion object {
        const val HOUR_MS = 3_600_000L
    }
}
