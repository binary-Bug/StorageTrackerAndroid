package com.binarybug.storagetracker.scan

import android.app.ActivityManager
import android.content.Context
import java.io.File

/** RAM and swap, in bytes. Swap is overflow memory; it is normally compressed RAM, so it uses no storage. */
class MemInfo(val totalBytes: Long, val availableBytes: Long, val swapTotalBytes: Long, val swapFreeBytes: Long) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0L)
    val swapUsedBytes: Long get() = (swapTotalBytes - swapFreeBytes).coerceAtLeast(0L)
    val hasSwap: Boolean get() = swapTotalBytes > 0

    /** True when little RAM is free or most swap is already in use, so apps are likely being reloaded. */
    val underPressure: Boolean
        get() = totalBytes > 0 && (availableBytes * 100 < totalBytes * LOW_AVAILABLE_PERCENT || (hasSwap && swapUsedBytes * 100 > swapTotalBytes * HIGH_SWAP_PERCENT))

    companion object {
        private const val LOW_AVAILABLE_PERCENT = 10
        private const val HIGH_SWAP_PERCENT = 75

        /** Parses the lines of /proc/meminfo (values are in KiB). Null when the total is missing. */
        fun parse(lines: List<String>): MemInfo? {
            val kb = HashMap<String, Long>()
            for (line in lines) {
                val name = line.substringBefore(':', "").trim()
                val value = line.substringAfter(':', "").trim().substringBefore(' ').toLongOrNull() ?: continue
                if (name.isNotEmpty()) kb[name] = value
            }
            val total = kb["MemTotal"] ?: return null
            // MemAvailable is missing on very old kernels; MemFree is a lower bound then
            val available = kb["MemAvailable"] ?: kb["MemFree"] ?: 0L
            return MemInfo(total * 1024, available * 1024, (kb["SwapTotal"] ?: 0L) * 1024, (kb["SwapFree"] ?: 0L) * 1024)
        }

        /** Reads /proc/meminfo, or falls back to ActivityManager (no swap figures) if that file cannot be read. */
        fun read(c: Context): MemInfo? {
            runCatching { parse(File("/proc/meminfo").readLines()) }.getOrNull()?.let { return it }
            val am = c.getSystemService(ActivityManager::class.java) ?: return null
            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            return if (mi.totalMem > 0) MemInfo(mi.totalMem, mi.availMem, 0L, 0L) else null
        }
    }
}
