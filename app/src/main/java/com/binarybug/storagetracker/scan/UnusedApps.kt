package com.binarybug.storagetracker.scan

import android.app.usage.UsageStatsManager
import android.content.Context

/** How long an app has sat idle. [everOpened] is false when Android has no record of the app being opened at all. */
class Idle(val days: Long, val everOpened: Boolean)

object UnusedApps {
    const val THRESHOLD_DAYS = 30L
    private const val DAY_MS = 86_400_000L
    private const val HISTORY_DAYS = 365L

    /**
     * Days since the app was last opened. An app installed after its last recorded use counts from the install time, so a
     * brand-new app is never reported as idle. Returns null when nothing is known about the app.
     */
    fun idle(lastUsedMs: Long, installedMs: Long, nowMs: Long): Idle? {
        val since = maxOf(lastUsedMs, installedMs)
        if (since <= 0L) return null
        return Idle(((nowMs - since) / DAY_MS).coerceAtLeast(0L), lastUsedMs > 0L)
    }

    fun isUnused(idle: Idle?): Boolean = idle != null && idle.days >= THRESHOLD_DAYS

    fun describe(idle: Idle?): String = when {
        idle == null -> "no usage record"
        idle.everOpened -> "last opened ${idle.days} days ago"
        else -> "never opened in the last year (installed ${idle.days} days ago)"
    }

    /** Last-opened time per package over the past year (Usage access required); empty when it is not granted. */
    fun lastUsed(c: Context): Map<String, Long> {
        if (!UsageAccess.granted(c)) return emptyMap()
        val usm = c.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val stats = runCatching { usm.queryAndAggregateUsageStats(now - HISTORY_DAYS * DAY_MS, now) }.getOrNull() ?: return emptyMap()
        return stats.mapValues { it.value.lastTimeUsed }
    }

    /** Install time per package. */
    fun installedAt(c: Context): Map<String, Long> =
        c.packageManager.getInstalledPackages(0).associate { it.packageName to it.firstInstallTime }
}
