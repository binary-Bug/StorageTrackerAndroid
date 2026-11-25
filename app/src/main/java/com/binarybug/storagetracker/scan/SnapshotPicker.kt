package com.binarybug.storagetracker.scan

import com.binarybug.storagetracker.data.SnapshotMeta

/**
 * Chooses the (from, to) pair for the Changes screen when the user taps a snapshot in the snapshot table, so that "from" is
 * always older than "to" (the Windows dashboard's "Set From" / "Set To" buttons, without ever producing a backwards pair).
 */
object SnapshotPicker {
    /** [metas] is sorted oldest first. */
    fun previousOf(metas: List<SnapshotMeta>, id: String): SnapshotMeta? {
        val i = metas.indexOfFirst { it.id == id }
        return if (i > 0) metas[i - 1] else null
    }

    /** "Use as From": keep the current "to" only if it is newer than the chosen snapshot, otherwise use the latest snapshot. */
    fun withFrom(metas: List<SnapshotMeta>, fromId: String, currentToId: String?): Pair<String?, String?> {
        val from = metas.firstOrNull { it.id == fromId } ?: return Pair(null, currentToId)
        val keep = metas.firstOrNull { it.id == currentToId }?.takeIf { it.takenMs > from.takenMs }
        val to = keep ?: metas.lastOrNull()?.takeIf { it.takenMs > from.takenMs }
        return Pair(from.id, to?.id)
    }

    /** "Use as To": keep the current "from" only if it is older than the chosen snapshot, otherwise compare with the one before it. */
    fun withTo(metas: List<SnapshotMeta>, toId: String, currentFromId: String?): Pair<String?, String?> {
        val to = metas.firstOrNull { it.id == toId } ?: return Pair(currentFromId, null)
        val keep = metas.firstOrNull { it.id == currentFromId }?.takeIf { it.takenMs < to.takenMs }
        val from = keep ?: previousOf(metas, to.id)
        return Pair(from?.id, to.id)
    }
}
