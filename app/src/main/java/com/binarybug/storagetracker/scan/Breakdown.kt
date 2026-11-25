package com.binarybug.storagetracker.scan

import com.binarybug.storagetracker.data.Snapshot

/** One coloured slice of "where your storage goes". [color] indexes into the palette's category colours. */
class Slice(val key: String, val label: String, val bytes: Long, val color: Int, val note: String? = null)

/**
 * Splits the used space into what can be measured (apps, app data, cache, files by type) and the remainder that Android
 * itself calls "System" but cannot attribute to anything. [osPartitions] is the gap between the advertised capacity
 * (e.g. 128 GB) and the partition apps can use: Android's own system partitions.
 */
class Breakdown(
    val slices: List<Slice>,
    val used: Long,
    val free: Long,
    val usable: Long,
    val marketing: Long,
    val osPartitions: Long,
    val measured: Long,
    val unattributed: Long,
    val hasApps: Boolean,
) {
    companion object {
        const val C_APPS = 0
        const val C_DATA = 1
        const val C_CACHE = 2
        const val C_IMAGES = 3
        const val C_VIDEOS = 4
        const val C_AUDIO = 5
        const val C_DOCS = 6
        const val C_ARCHIVES = 7
        const val C_APKS = 8
        const val C_OTHER = 9
        const val C_SYSTEM = 10

        fun colorOf(c: Category) = when (c) {
            Category.IMAGES -> C_IMAGES
            Category.VIDEOS -> C_VIDEOS
            Category.AUDIO -> C_AUDIO
            Category.DOCUMENTS -> C_DOCS
            Category.ARCHIVES -> C_ARCHIVES
            Category.APKS -> C_APKS
            Category.OTHER -> C_OTHER
        }

        fun build(s: Snapshot): Breakdown {
            val m = s.meta
            val slices = ArrayList<Slice>()
            val hasApps = s.apps.isNotEmpty()
            if (hasApps) {
                val code = s.apps.sumOf { it.appBytes }
                val cache = s.apps.sumOf { it.cacheBytes }
                val data = s.apps.sumOf { (it.dataBytes - it.cacheBytes).coerceAtLeast(0L) }
                slices.add(Slice("apps", "Apps (installed code)", code, C_APPS, "${s.apps.size} apps"))
                slices.add(Slice("appdata", "App data", data, C_DATA, "accounts, saves, downloads inside apps"))
                slices.add(Slice("cache", "App cache", cache, C_CACHE, "can be cleared safely"))
            }
            for (c in Category.entries) {
                val t = s.cats.firstOrNull { it.name == c.name } ?: continue
                if (t.bytes > 0) slices.add(Slice("cat:${c.name}", c.label, t.bytes, colorOf(c), "${"%,d".format(t.count)} files"))
            }
            val measured = slices.sumOf { it.bytes }
            val unattributed = (m.used - measured).coerceAtLeast(0L)
            val marketing = s.sys?.marketingTotal ?: -1L
            val os = if (marketing > m.total) marketing - m.total else 0L
            if (unattributed > 0) {
                slices.add(
                    if (hasApps) Slice("system", "System & unmeasurable", unattributed, C_SYSTEM, "Android cannot attribute this")
                    else Slice("system", "Apps, system & other (not measured yet)", unattributed, C_SYSTEM, "grant Usage access to split this up"),
                )
            }
            return Breakdown(slices, m.used, m.free, m.total, marketing, os, measured, unattributed, hasApps)
        }
    }
}
