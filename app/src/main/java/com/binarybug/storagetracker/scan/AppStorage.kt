package com.binarybug.storagetracker.scan

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process
import android.os.storage.StorageManager
import com.binarybug.storagetracker.data.AppEntry
import com.binarybug.storagetracker.data.SysStats
import java.util.concurrent.CancellationException

/** "Usage access" is the special permission Android requires before an app may read other apps' storage sizes. */
object UsageAccess {
    fun granted(c: Context): Boolean {
        val ops = c.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
    }
}

class AppStorageResult(val apps: List<AppEntry>, val sys: SysStats?, val available: Boolean)

/** Per-app sizes (code, data, cache) from Android's own storage statistics, plus device-level totals. */
object AppStorage {
    @Suppress("DEPRECATION")
    fun collect(c: Context, cancelled: () -> Boolean = { false }): AppStorageResult {
        val ssm = c.getSystemService(StorageStatsManager::class.java)
        val uuid = StorageManager.UUID_DEFAULT
        val user = Process.myUserHandle()
        val marketing = runCatching { ssm.getTotalBytes(uuid) }.getOrDefault(-1L)
        if (!UsageAccess.granted(c)) return AppStorageResult(emptyList(), SysStats(marketing, -1, -1, -1, -1, -1, -1, -1, -1, -1), false)

        val pm = c.packageManager
        val apps = ArrayList<AppEntry>()
        for (ai in pm.getInstalledApplications(0)) {
            if (cancelled()) throw CancellationException()
            try {
                val st = ssm.queryStatsForPackage(uuid, ai.packageName, user)
                if (st.appBytes + st.dataBytes <= 0L) continue
                apps.add(
                    AppEntry(
                        ai.packageName, pm.getApplicationLabel(ai).toString(), st.appBytes, st.dataBytes, st.cacheBytes,
                        (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    ),
                )
            } catch (e: Exception) {
                // package vanished or is not measurable: skip it
            }
        }
        val u = runCatching { ssm.queryStatsForUser(uuid, user) }.getOrNull()
        val x = runCatching { ssm.queryExternalStatsForUser(uuid, user) }.getOrNull()
        val sys = SysStats(
            marketing, runCatching { ssm.getFreeBytes(uuid) }.getOrDefault(-1L),
            u?.appBytes ?: -1L, u?.dataBytes ?: -1L, u?.cacheBytes ?: -1L,
            x?.totalBytes ?: -1L, x?.audioBytes ?: -1L, x?.videoBytes ?: -1L, x?.imageBytes ?: -1L, x?.appBytes ?: -1L,
        )
        return AppStorageResult(apps, sys, true)
    }
}
