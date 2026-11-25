package com.binarybug.storagetracker.scan

import com.binarybug.storagetracker.data.SnapshotMeta

/**
 * How the space Android cannot attribute (used − apps − scanned files) moves between snapshots. Only snapshots where apps
 * were measured have a value. A sudden jump usually means an OS update, a big cache of system data, or an unscanned folder.
 */
class UnattributedTrend(val points: List<Point>, val jumpBytes: Long, val jumpFrom: Point?, val jumpTo: Point?) {
    class Point(val t: Long, val bytes: Long)

    /** True when the latest value rose by at least [JUMP_BYTES] since the previous measured snapshot. */
    val hasJump: Boolean get() = jumpBytes >= JUMP_BYTES

    companion object {
        const val JUMP_BYTES = 1_000_000_000L

        /** [metas] is sorted oldest first. */
        fun of(metas: List<SnapshotMeta>): UnattributedTrend {
            val pts = metas.mapNotNull { m -> m.unattributed?.let { Point(m.takenMs, it) } }
            val to = pts.lastOrNull()
            val from = if (pts.size >= 2) pts[pts.size - 2] else null
            return UnattributedTrend(pts, if (to != null && from != null) to.bytes - from.bytes else 0L, from, to)
        }
    }
}
