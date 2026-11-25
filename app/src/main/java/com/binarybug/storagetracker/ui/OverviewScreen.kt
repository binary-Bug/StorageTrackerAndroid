package com.binarybug.storagetracker.ui

import android.content.Context
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.binarybug.storagetracker.Model
import com.binarybug.storagetracker.formatBytes
import com.binarybug.storagetracker.readPrimaryVolumeUsage
import com.binarybug.storagetracker.scan.Breakdown
import com.binarybug.storagetracker.scan.CategoryDiffs
import com.binarybug.storagetracker.scan.Forecast
import com.binarybug.storagetracker.scan.FreeStability
import com.binarybug.storagetracker.scan.MemInfo
import com.binarybug.storagetracker.scan.TrendRange
import com.binarybug.storagetracker.scan.UnattributedTrend

/** Mobile version of the Windows dashboard's top section: stat cards, trend chart, biggest changes, coverage. */
class OverviewScreen(private val ctx: Context, private val p: Palette, private val a: Actions) : Screen {
    private val scroll = ScrollView(ctx).apply { setBackgroundColor(p.bg) }
    private val col = ctx.vbox().apply { setPadding(0, ctx.dp(4), 0, ctx.dp(20)) }
    override val root: View get() = scroll

    /** Which part of the history the used/free chart shows; kept while the app is open. */
    private var trendRange = TrendRange.ALL

    /** Index into the stability card's periods (1 day, 3 days, 1 week); kept while the app is open. */
    private var stabilityWindow = 1

    init { scroll.addView(col, ViewGroup.LayoutParams(MATCH, WRAP)) }

    override fun refresh() {
        col.removeAllViews()
        storageCard()
        if (!Model.loaded) { messageCard("Loading snapshots…", null); return }
        if (Model.metas.isEmpty()) {
            messageCard("No snapshots yet", "Run a scan to record where your storage is used. After the next scan you can compare the two.") {
                a.requestScan()
            }
            return
        }
        changeCard()
        breakdownCard()
        systemExplainerCard()
        unattributedTrendCard()
        stabilityCard()
        trendCard()
        topChangesCard()
        memoryCard()
        coverageCard()
        col.addView(
            ctx.label("Storage Tracker · binaryBug", 11f, p.muted).apply { gravity = Gravity.CENTER; alpha = 0.7f },
            lp(MATCH, WRAP, t = ctx.dp(8), b = ctx.dp(4)),
        )
    }

    private fun breakdownCard() {
        val snap = Model.comparison?.newer ?: return
        val b = Breakdown.build(snap)
        val c = ctx.card(p).apply { setPadding(0, ctx.dp(14), 0, ctx.dp(8)) }
        c.addView(ctx.heading("Where your storage goes", p), lp(l = ctx.dp(16), r = ctx.dp(16)))
        c.addView(
            ctx.label("From the latest snapshot, ${Fmt.ago(snap.meta.takenMs)}. Percentages are of the ${formatBytes(b.usable)} you can use.", 12f, p.muted),
            lp(l = ctx.dp(16), r = ctx.dp(16), t = ctx.dp(2)),
        )
        if (!b.hasApps) {
            c.addView(ctx.label("Apps, app data and cache are not measured yet. Switch on Usage access to include them.", 13f, p.used, true), lp(l = ctx.dp(16), r = ctx.dp(16), t = ctx.dp(8)))
            c.addView(ctx.pill("Grant Usage access", p) { a.openUsageAccessSettings() }, lp(WRAP, WRAP, l = ctx.dp(16), t = ctx.dp(6)))
        }
        val bar = StackedBarView(ctx, p).apply {
            set(
                b.slices.map { p.cat[it.color] to it.bytes }, b.usable,
                "Storage by type: " + b.slices.joinToString(", ") { "${it.label} ${formatBytes(it.bytes)}" } + ", free ${formatBytes(b.free)}",
            )
        }
        c.addView(bar, lp(MATCH, ctx.dp(18), l = ctx.dp(16), r = ctx.dp(16), t = ctx.dp(10), b = ctx.dp(8)))
        for (s in b.slices) {
            val go: (() -> Unit)? = when {
                s.key.startsWith("cat:") -> ({ a.openCategory(s.key) })
                s.key == "apps" || s.key == "appdata" || s.key == "cache" -> ({ a.goToTab(1) })
                else -> null
            }
            c.addView(ctx.legendRow(p, p.cat[s.color], s.label, s.note, formatBytes(s.bytes), Fmt.pct(s.bytes, b.usable), go != null, go))
        }
        c.addView(ctx.legendRow(p, p.track, "Free", "available to use", formatBytes(b.free), Fmt.pct(b.free, b.usable)))
        col.addCard(ctx, c)
    }

    /** Why "System" looks huge in Settings: spells out the arithmetic with this phone's real numbers. */
    private fun systemExplainerCard() {
        val snap = Model.comparison?.newer ?: return
        val b = Breakdown.build(snap)
        if (b.marketing <= 0) return
        val c = ctx.card(p)
        c.addView(ctx.heading("Why “System” looks so big", p))
        val lines = listOf(
            "Advertised capacity: ${formatBytes(b.marketing)} (a rounded marketing figure)",
            "Android's own system partitions: ${formatBytes(b.osPartitions)} (not usable by you; the size varies by phone)",
            "Storage you can use: ${formatBytes(b.usable)}",
            "Used: ${formatBytes(b.used)} · free: ${formatBytes(b.free)}",
            "Measured (apps, data, cache, files): ${formatBytes(b.measured)}",
            (if (b.hasApps) "Not attributable by any app: " else "Not measured yet: ") + formatBytes(b.unattributed),
        )
        for (s in lines) c.addView(ctx.label(s, 14f, p.text), lp(t = ctx.dp(6)))
        val why = if (b.hasApps) {
            "Settings shows “System” = advertised capacity − free − measured, so it lumps the real system partitions together with the " +
                "${formatBytes(b.unattributed)} Android cannot attribute (system data, compiled app code, logs, update files, file-system overhead). " +
                "Without root access no app can look inside that part."
        } else {
            "Apps and their data are not measured yet, so most of the used space is still unexplained. Switch on Usage access, then scan again."
        }
        c.addView(ctx.label(why, 13f, p.muted), lp(t = ctx.dp(10)))
        c.addView(ctx.label("All sizes use 1 GB = 1,000,000,000 bytes, like Android Settings.", 12f, p.muted), lp(t = ctx.dp(6)))
        col.addCard(ctx, c)
    }

    /** The space Android cannot attribute, over time, with a warning when it jumps (OS update, system data, an unscanned folder). */
    private fun unattributedTrendCard() {
        val t = UnattributedTrend.of(Model.metas)
        val c = ctx.card(p)
        c.addView(ctx.heading("Unattributed space over time (GB)", p))
        if (t.points.size < 2) {
            val why = if (t.points.isEmpty()) "Needs a scan with Usage access on, so apps are measured too." else "One measured snapshot so far; the next scan will draw the line."
            c.addView(ctx.label(why, 14f, p.muted), lp(t = ctx.dp(6)))
            col.addCard(ctx, c)
            return
        }
        val g = com.binarybug.storagetracker.BYTES_PER_GB
        val last = t.points.last()
        c.addView(
            ctx.label("Used space that no app and no scanned file explains. Now ${formatBytes(last.bytes)}.", 12f, p.muted),
            lp(t = ctx.dp(2)),
        )
        if (t.hasJump) {
            c.addView(
                ctx.label("⚠ Up ${formatBytes(t.jumpBytes)} since ${Fmt.ago(t.jumpFrom!!.t)}. Often an OS update or system data; check Changes for new folders.", 14f, p.used, true)
                    .apply { setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(12), ctx.dp(8)); setBackgroundColor(p.warnBg) },
                lp(t = ctx.dp(10)),
            )
        }
        val chart = SingleLineChartView(ctx, p, p.cat[0]).apply {
            set(t.points.map { SingleLineChartView.Pt(it.t, it.bytes / g) }, "Unattributed space over ${t.points.size} snapshots. Latest ${formatBytes(last.bytes)}.")
        }
        c.addView(chart, lp(MATCH, ctx.dp(160), t = ctx.dp(8)))
        col.addCard(ctx, c)
    }

    private fun storageCard() {
        val u = readPrimaryVolumeUsage()
        val c = ctx.card(p)
        c.addView(ctx.label("Phone storage right now", 13f, p.muted))
        c.addView(ctx.label("${formatBytes(u.usedBytes)} used", 30f, p.text, true).apply { isAccessibilityHeading = true })
        val bar = UsageBarView(ctx, p).apply { set(u.usedBytes, u.totalBytes) }
        c.addView(bar, lp(MATCH, ctx.dp(14), t = ctx.dp(8), b = ctx.dp(8)))
        val row = ctx.hbox()
        row.addView(ctx.label("${formatBytes(u.freeBytes)} free", 15f, p.free, true), lp(0, WRAP, 1f))
        row.addView(ctx.label("${Fmt.pct(u.usedBytes, u.totalBytes)} of ${formatBytes(u.totalBytes)}", 15f, p.muted))
        c.addView(row)
        forecastLine(c)
        val shortcuts = ctx.hbox()
        shortcuts.addView(ctx.pill("Free up space", p, false) { a.openStorageSettings() })
        if (Model.metas.isNotEmpty()) shortcuts.addView(ctx.pill("Share report", p, false) { a.shareReport() }, lp(WRAP, WRAP, l = ctx.dp(8)))
        c.addView(shortcuts, lp(t = ctx.dp(12)))
        if (u.totalBytes > 0 && u.freeBytes * 10 < u.totalBytes) {
            c.addView(
                ctx.label("⚠ Low space: less than 10% is free.", 14f, p.used, true)
                    .apply { setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(12), ctx.dp(8)); setBackgroundColor(p.warnBg) },
                lp(t = ctx.dp(10)),
            )
        }
        col.addCard(ctx, c)
    }

    /** "At this pace you run out in about N days", from the last two weeks of snapshots. Silent when the trend is flat or unknown. */
    private fun forecastLine(c: LinearLayout) {
        val f = Forecast.of(Model.metas, System.currentTimeMillis()) ?: return
        val left = f.daysLeft
        val text = when {
            left == null -> "Free space is steady over the last ${Fmt.dur((f.spanDays * 86_400_000).toLong())}."
            left < 1.0 -> "At the steady pace (${formatBytes((-f.perDayBytes).toLong())} a day) free space runs out within a day."
            else -> "At the steady pace (${formatBytes((-f.perDayBytes).toLong())} a day) free space runs out in about ${Math.round(left)} days."
        }
        c.addView(ctx.label(text, 13f, if (f.warns) p.used else p.muted, f.warns), lp(t = ctx.dp(8)))
        f.stepNote { Fmt.ago(it) }?.let { c.addView(ctx.label(it, 12f, p.muted), lp(t = ctx.dp(2))) }
    }

    private fun messageCard(title: String, body: String?, onScan: (() -> Unit)? = null) {
        val c = ctx.card(p)
        c.addView(ctx.heading(title, p))
        if (body != null) c.addView(ctx.label(body, 14f, p.muted), lp(t = ctx.dp(6)))
        if (onScan != null) c.addView(ctx.pill("Scan now", p) { onScan() }, lp(WRAP, WRAP, t = ctx.dp(12)))
        col.addCard(ctx, c)
    }

    private fun tile(title: String, value: String, delta: String, deltaColor: Int): View = ctx.vbox().apply {
        addView(ctx.label(title, 13f, p.muted))
        addView(ctx.label(value, 22f, p.text, true))
        addView(ctx.label(delta, 14f, deltaColor, true))
        contentDescription = "$title $value, $delta"
        isFocusable = true
    }

    private fun changeCard() {
        val cmp = Model.comparison ?: return
        val c = ctx.card(p)
        val old = cmp.older?.meta
        val now = cmp.newer.meta
        if (old == null) {
            c.addView(ctx.heading("Latest snapshot", p))
            c.addView(ctx.label("Taken ${Fmt.whenFull(now.takenMs)}. Only one snapshot exists so far; the next scan will show what changed.", 14f, p.muted), lp(t = ctx.dp(6)))
        } else {
            c.addView(ctx.heading("Change since ${Fmt.ago(old.takenMs)}", p))
            c.addView(ctx.label("${Fmt.whenFull(old.takenMs)} → ${Fmt.whenFull(now.takenMs)} (${Fmt.dur(now.takenMs - old.takenMs)} apart)", 12f, p.muted), lp(t = ctx.dp(2)))
            val du = now.used - old.used
            val df = now.free - old.free
            val row = ctx.hbox().apply { gravity = Gravity.TOP }
            row.addView(tile("Used", formatBytes(now.used), Fmt.delta(du), if (du > 0) p.used else p.free), lp(0, WRAP, 1f, t = ctx.dp(10)))
            row.addView(tile("Free", formatBytes(now.free), Fmt.delta(df), if (df < 0) p.used else p.free), lp(0, WRAP, 1f, t = ctx.dp(10)))
            c.addView(row)
            val cats = CategoryDiffs.between(cmp.older, cmp.newer).rows.filter { it.delta != 0L }.take(3)
            if (cats.isNotEmpty()) {
                c.addView(ctx.label("What changed most", 12f, p.muted), lp(t = ctx.dp(12)))
                for (r in cats) {
                    val line = ctx.hbox()
                    line.addView(ctx.label(r.label, 14f, p.text), lp(0, WRAP, 1f))
                    line.addView(ctx.label(Fmt.delta(r.delta), 14f, if (r.delta > 0) p.used else p.free, true))
                    c.addView(line, lp(t = ctx.dp(4)))
                }
                c.addView(ctx.pill("See all changes", p, filled = false) { a.goToTab(2) }, lp(WRAP, WRAP, t = ctx.dp(8)))
            }
        }
        col.addCard(ctx, c)
    }

    /** An arrow for the direction free space is heading over the chosen period; quick jumps that revert do not count. */
    private fun stabilityCard() {
        val c = ctx.card(p)
        c.addView(ctx.heading("How stable is your free space?", p))
        val windows = listOf("1 day" to 1, "3 days" to 3, "1 week" to 7)
        val arrow = ctx.label("", 40f, p.muted, true).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        val headline = ctx.label("", 18f, p.text, true)
        val detail = ctx.label("", 13f, p.muted)
        val range = ctx.label("", 12f, p.muted)
        val row = ctx.hbox().apply { gravity = Gravity.CENTER_VERTICAL; isFocusable = true }
        val texts = ctx.vbox()
        texts.addView(headline)
        texts.addView(detail, lp(t = ctx.dp(2)))
        row.addView(arrow, lp(WRAP, WRAP, r = ctx.dp(16)))
        row.addView(texts, lp(0, WRAP, 1f))

        // redrawn in place, so changing the period does not rebuild the screen
        fun draw() {
            val s = FreeStability.of(Model.metas, System.currentTimeMillis(), windows[stabilityWindow].second)
            if (s == null) {
                arrow.text = "–"; arrow.setTextColor(p.muted)
                headline.text = "Not enough history yet"
                detail.text = "Needs at least 4 snapshots spread over 12 hours inside this period. Keep the background scan on."
                range.text = ""
            } else {
                val perDay = formatBytes(Math.abs(s.perDayBytes).toLong())
                val path = "${formatBytes(s.startFree)} → ${formatBytes(s.endFree)} over ${Fmt.dur((s.spanDays * 86_400_000).toLong())}"
                when (s.trend) {
                    FreeStability.Trend.UP -> {
                        arrow.text = "↑"; arrow.setTextColor(p.free); headline.text = "Gaining space"
                        detail.text = "About $perDay more free each day ($path)."
                    }
                    FreeStability.Trend.DOWN -> {
                        arrow.text = "↓"; arrow.setTextColor(p.used); headline.text = "Losing space"
                        detail.text = "About $perDay less free each day ($path)."
                    }
                    FreeStability.Trend.STABLE -> {
                        arrow.text = "→"; arrow.setTextColor(p.primary); headline.text = "Stable"
                        detail.text = "Free space is about where it was ($path)."
                    }
                }
                range.text = "It ranged from ${formatBytes(s.minFree)} to ${formatBytes(s.maxFree)} across ${s.basedOn} snapshots."
            }
            row.contentDescription = "${headline.text}. ${detail.text}"
        }

        c.addView(Segmented(ctx, p, windows.map { it.first }, stabilityWindow) { stabilityWindow = it; draw() }, lp(t = ctx.dp(8)))
        c.addView(row, lp(t = ctx.dp(12)))
        c.addView(range, lp(t = ctx.dp(6)))
        c.addView(
            ctx.label(
                "Compares typical free space at the start and the end of the period, so a quick jump that fades again (like clearing a cache that Android refills within hours) does not count as a gain.",
                12f, p.muted,
            ),
            lp(t = ctx.dp(8)),
        )
        draw()
        col.addCard(ctx, c)
    }

    private fun trendCard() {
        val g = com.binarybug.storagetracker.BYTES_PER_GB
        val c = ctx.card(p)
        c.addView(ctx.heading("Used and free over time (GB)", p))
        val chart = TrendChartView(ctx, p)
        val caption = ctx.label("", 12f, p.muted)
        // The chart and caption are redrawn in place, so changing the range does not rebuild the screen or jump the scroll position.
        fun draw() {
            val shown = trendRange.select(Model.metas, System.currentTimeMillis())
            chart.set(shown.map { TrendChartView.Pt(it.takenMs, it.used / g, it.free / g) })
            caption.text = "Showing ${shown.size} of ${Model.metas.size} snapshots · latest ${Fmt.ago(Model.metas.last().takenMs)}"
        }
        val ranges = TrendRange.entries
        c.addView(
            Segmented(ctx, p, ranges.map { it.label }, ranges.indexOf(trendRange)) { trendRange = ranges[it]; draw() },
            lp(t = ctx.dp(8)),
        )
        c.addView(chart, lp(MATCH, ctx.dp(200), t = ctx.dp(8)))
        c.addView(caption, lp(t = ctx.dp(4)))
        draw()
        col.addCard(ctx, c)
    }

    private fun topChangesCard() {
        val cmp = Model.comparison ?: return
        val c = ctx.card(p).apply { setPadding(0, ctx.dp(14), 0, ctx.dp(8)) }
        c.addView(ctx.heading("Biggest changes", p), lp(l = ctx.dp(16), r = ctx.dp(16)))
        val min = 50L * 1024 * 1024
        val top = cmp.hot.filter { Math.abs(it.self) >= min }.take(5)
        if (cmp.older == null) {
            c.addView(ctx.label("Needs two snapshots to compare.", 14f, p.muted), lp(l = ctx.dp(16), r = ctx.dp(16), t = ctx.dp(6)))
        } else if (top.isEmpty()) {
            c.addView(ctx.label("No folder changed by 50 MB or more.", 14f, p.muted), lp(l = ctx.dp(16), r = ctx.dp(16), t = ctx.dp(6)))
        } else {
            for (h in top) {
                val n = h.node
                c.addView(
                    ctx.listRow(
                        p, if (n.path == "/") "/ (storage root)" else n.name, com.binarybug.storagetracker.scan.Compare.parentOf(n.path),
                        Fmt.delta(h.self), if (h.self > 0) p.used else p.free, n.b?.let { "now ${formatBytes(it)}" },
                    ) { a.openInBrowse(n.path) },
                )
            }
        }
        c.addView(ctx.pill("See all changes", p, filled = false) { a.goToTab(2) }, lp(WRAP, WRAP, l = ctx.dp(16), t = ctx.dp(8)))
        col.addCard(ctx, c)
    }

    /** RAM and swap as of the last time this screen was drawn. Not storage, but it explains a phone that reloads apps all the time. */
    private fun memoryCard() {
        val m = MemInfo.read(ctx) ?: return
        val c = ctx.card(p)
        c.addView(ctx.heading("Memory (RAM) right now", p))
        c.addView(
            ctx.label("${formatBytes(m.availableBytes)} available of ${formatBytes(m.totalBytes)}", 14f, p.text),
            lp(t = ctx.dp(6)),
        )
        c.addView(UsageBarView(ctx, p).apply { set(m.usedBytes, m.totalBytes) }, lp(MATCH, ctx.dp(12), t = ctx.dp(6)))
        if (m.hasSwap) {
            c.addView(
                ctx.label("Swap in use: ${formatBytes(m.swapUsedBytes)} of ${formatBytes(m.swapTotalBytes)}", 14f, p.text),
                lp(t = ctx.dp(12)),
            )
            c.addView(UsageBarView(ctx, p).apply { set(m.swapUsedBytes, m.swapTotalBytes) }, lp(MATCH, ctx.dp(12), t = ctx.dp(6)))
        }
        if (m.underPressure) {
            c.addView(
                ctx.label("⚠ Memory is tight: apps are being pushed out and reloaded. Closing apps you do not use helps.", 14f, p.used, true)
                    .apply { setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(12), ctx.dp(8)); setBackgroundColor(p.warnBg) },
                lp(t = ctx.dp(10)),
            )
        }
        c.addView(
            ctx.label(
                "Swap is overflow memory. It is normally compressed RAM, so it uses no storage. If your phone has a “RAM expansion” option and it is on, part of it is kept on your storage instead.",
                12f, p.muted,
            ),
            lp(t = ctx.dp(10)),
        )
        col.addCard(ctx, c)
    }

    private fun coverageCard() {
        val m = Model.comparison?.newer?.meta ?: return
        val c = ctx.card(p)
        c.addView(ctx.heading("Scan coverage", p))
        val unattributed = (m.used - m.scanned).coerceAtLeast(0)
        val lines = listOf(
            "Scanned ${formatBytes(m.scanned)} of ${formatBytes(m.used)} used (${Fmt.pct(m.scanned, m.used)})",
            "${"%,d".format(m.filesScanned)} files in ${"%,d".format(m.dirsScanned)} folders · took ${Fmt.dur((m.scanSec * 1000).toLong())}",
            "${m.skipped} folders could not be read",
            "Not attributed to folders: ${formatBytes(unattributed)} (system files and other apps' private data, which Android hides)",
            if (m.allFiles) "All files access was on for this scan" else "All files access was OFF for this scan, so it is incomplete",
        )
        for (s in lines) c.addView(ctx.label(s, 14f, p.text), lp(t = ctx.dp(6)))
        if (!Environment.isExternalStorageManager()) {
            c.addView(ctx.pill("Grant all files access", p) { a.openAllFilesSettings() }, lp(WRAP, WRAP, t = ctx.dp(12)))
        }
        col.addCard(ctx, c)
    }
}
