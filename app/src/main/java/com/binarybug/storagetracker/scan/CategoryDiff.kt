package com.binarybug.storagetracker.scan

import com.binarybug.storagetracker.data.Snapshot

/** One row of the category-by-category comparison between two snapshots. [color] indexes the palette's category colours. */
class CatDelta(val key: String, val label: String, val color: Int, val before: Long, val after: Long) {
    val delta: Long get() = after - before
}

/**
 * [rows] are sorted by size of change. [usedDelta] is the change of the phone's total used space, so the rows can be read as
 * "which categories added up to that change". [note] explains rows that had to be left out.
 */
class CategoryDiff(val rows: List<CatDelta>, val usedDelta: Long, val note: String?)

object CategoryDiffs {
    private val appKeys = setOf("apps", "appdata", "cache", "system")

    /**
     * Compares the "where your storage goes" slices of two snapshots. App rows and the system row are only comparable when
     * apps were measured in BOTH snapshots; otherwise they are left out (the older one would count as 0 and look like a huge jump).
     */
    fun between(older: Snapshot, newer: Snapshot): CategoryDiff {
        val a = Breakdown.build(older)
        val b = Breakdown.build(newer)
        val comparableApps = a.hasApps && b.hasApps
        val before = a.slices.associateBy { it.key }
        val after = b.slices.associateBy { it.key }
        val rows = ArrayList<CatDelta>()
        for (key in before.keys + after.keys) {
            if (key in appKeys && !comparableApps) continue
            val s = after[key] ?: before.getValue(key)
            rows.add(CatDelta(key, s.label, s.color, before[key]?.bytes ?: 0L, after[key]?.bytes ?: 0L))
        }
        val note = if (comparableApps) null
        else "Apps were not measured in one of the two snapshots, so the app rows and the system row are left out."
        return CategoryDiff(rows.sortedByDescending { Math.abs(it.delta) }, newer.meta.used - older.meta.used, note)
    }
}
