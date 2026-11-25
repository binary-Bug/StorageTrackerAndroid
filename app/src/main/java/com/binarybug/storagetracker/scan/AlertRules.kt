package com.binarybug.storagetracker.scan

/** Decides when a scan result is worth a notification. Pure functions, so the rules can be tested without Android. */
object AlertRules {
    private const val LOW_FRACTION = 0.10
    const val NOTICE_GAP_MS = 24 * 3_600_000L
    private const val MAX_ITEMS = 3

    /** Low-space notice: less than 10% free, and not already warned in the last 24 hours. */
    fun shouldWarnLow(total: Long, free: Long, lastNoticeMs: Long, nowMs: Long): Boolean =
        total > 0 && free < total * LOW_FRACTION && nowMs - lastNoticeMs >= NOTICE_GAP_MS

    /** An app or folder that grew by at least the alert limit since the previous snapshot. */
    class Growth(val label: String, val bytes: Long, val isApp: Boolean, val target: AlertTarget)

    /**
     * Biggest growers, largest first (at most 3). Apps count only when measured in both snapshots, so an app that merely
     * appeared because Usage access was switched on is not reported as growth. [limit] <= 0 turns the alert off.
     */
    fun growth(cmp: Comparison, limit: Long): List<Growth> {
        if (limit <= 0L || cmp.older == null) return emptyList()
        val out = ArrayList<Growth>()
        if (cmp.older.appsAvailable && cmp.newer.appsAvailable) {
            for (d in cmp.apps) if (d.a != null && d.b != null && d.delta >= limit) out.add(Growth(d.label, d.delta, true, AlertTarget.Apps))
        }
        for (h in cmp.hot) if (h.self >= limit) out.add(Growth(if (h.node.path == "/") "Storage root" else h.node.path, h.self, false, AlertTarget.Folder(h.node.path)))
        return out.sortedByDescending { it.bytes }.take(MAX_ITEMS)
    }
}
