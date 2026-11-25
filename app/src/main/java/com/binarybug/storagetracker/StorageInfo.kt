package com.binarybug.storagetracker

import android.os.Environment
import android.os.StatFs

/** Drive-level numbers for the primary shared storage. These are exact, unlike folder-level scan totals. */
data class VolumeUsage(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes: Long get() = totalBytes - freeBytes
}

fun readPrimaryVolumeUsage(): VolumeUsage {
    val stat = StatFs(Environment.getExternalStorageDirectory().path)
    return VolumeUsage(totalBytes = stat.totalBytes, freeBytes = stat.availableBytes)
}

/** Bytes per "GB" for charts: decimal (1 GB = 1,000,000,000 bytes), the same unit Android Settings and phone makers use. */
const val BYTES_PER_GB = 1_000_000_000.0

/** Formats a byte count with decimal units like Android Settings does, e.g. 1500 -> "1.5 KB", 128e9 -> "128.0 GB". */
fun formatBytes(bytes: Long): String {
    if (bytes < 1000) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1000 && unit < units.lastIndex) {
        value /= 1000
        unit++
    }
    return "%.1f %s".format(value, units[unit])
}
