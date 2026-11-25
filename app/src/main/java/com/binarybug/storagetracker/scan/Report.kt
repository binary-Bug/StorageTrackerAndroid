package com.binarybug.storagetracker.scan

import com.binarybug.storagetracker.formatBytes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Plain-text summary of the current comparison, for the Share button. */
object Report {
    private const val TOP = 5

    private fun signed(v: Long): String = (if (v > 0) "+" else if (v < 0) "−" else "") + formatBytes(Math.abs(v))

    fun build(cmp: Comparison, forecast: Forecast?): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val now = cmp.newer.meta
        val out = StringBuilder()
        out.appendLine("Storage Tracker report")
        out.appendLine("Snapshot ${fmt.format(Date(now.takenMs))}")
        out.appendLine("Used ${formatBytes(now.used)} · free ${formatBytes(now.free)} of ${formatBytes(now.total)}")
        val old = cmp.older
        if (old == null) {
            out.appendLine("Only one snapshot so far, nothing to compare yet.")
        } else {
            out.appendLine()
            out.appendLine("Since ${fmt.format(Date(old.meta.takenMs))}: used ${signed(now.used - old.meta.used)}, free ${signed(now.free - old.meta.free)}")
            val cats = CategoryDiffs.between(old, cmp.newer).rows.filter { it.delta != 0L }.take(TOP)
            if (cats.isNotEmpty()) {
                out.appendLine()
                out.appendLine("By category:")
                for (r in cats) out.appendLine("  ${signed(r.delta)}  ${r.label}")
            }
            val folders = cmp.hot.filter { it.self != 0L }.take(TOP)
            if (folders.isNotEmpty()) {
                out.appendLine()
                out.appendLine("Biggest folder changes:")
                for (h in folders) out.appendLine("  ${signed(h.self)}  ${if (h.node.path == "/") "/ (storage root)" else h.node.path}")
            }
            val apps = cmp.apps.filter { it.a != null && it.b != null && it.delta != 0L }
                .sortedByDescending { Math.abs(it.delta) }.take(TOP)
            if (apps.isNotEmpty()) {
                out.appendLine()
                out.appendLine("Apps that changed:")
                for (d in apps) out.appendLine("  ${signed(d.delta)}  ${d.label}")
            }
        }
        if (forecast?.daysLeft != null) {
            out.appendLine()
            out.appendLine("At the recent pace (${formatBytes((-forecast.perDayBytes).toLong())} a day) free space runs out in about ${Math.round(forecast.daysLeft)} days.")
        }
        return out.toString().trimEnd()
    }
}
