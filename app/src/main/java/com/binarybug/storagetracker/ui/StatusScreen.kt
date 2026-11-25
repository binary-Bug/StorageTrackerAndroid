package com.binarybug.storagetracker.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Environment
import android.os.PowerManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.Switch
import com.binarybug.storagetracker.Model
import com.binarybug.storagetracker.data.Prefs
import com.binarybug.storagetracker.data.SnapshotMeta
import com.binarybug.storagetracker.data.SnapshotStore
import com.binarybug.storagetracker.formatBytes
import com.binarybug.storagetracker.scan.ScanController
import com.binarybug.storagetracker.scan.SnapshotPicker
import com.binarybug.storagetracker.scan.UsageAccess
import com.binarybug.storagetracker.work.Notifier
import com.binarybug.storagetracker.work.Scheduler

/** Job registration, last run, approximate next run, settings, and the permission/battery checks that decide if it works. */
class StatusScreen(private val ctx: Context, private val p: Palette, private val a: Actions) : Screen {
    private val scroll = ScrollView(ctx).apply { setBackgroundColor(p.bg) }
    private val col = ctx.vbox().apply { setPadding(0, ctx.dp(4), 0, ctx.dp(20)) }
    override val root: View get() = scroll

    private val intervalLabels = listOf("15 minutes", "1 hour", "2 hours", "4 hours", "12 hours", "24 hours")
    private val intervalMins = listOf(15, 60, 120, 240, 720, 1440)
    private val retentionDays = listOf(1, 3, 7, 14, 30)
    private val alertGb = listOf(0f, 0.5f, 1f, 2f, 5f, 10f)
    private val growthGb = listOf(0f, 0.2f, 0.5f, 1f, 2f)

    init { scroll.addView(col, ViewGroup.LayoutParams(MATCH, WRAP)) }

    override fun refresh() {
        col.removeAllViews()
        scheduleCard()
        settingsCard()
        permissionsCard()
        snapshotsCard()
        aboutCard()
    }

    /** Developer, version and install details, which Android's own App info page does not show for apps installed outside Play. */
    private fun aboutCard() {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val c = ctx.card(p)
        c.addView(ctx.heading("About", p))
        line(c, "Storage Tracker", p.text, true)
        line(c, "Developer: binaryBug")
        line(c, "Version ${info.versionName} (build ${info.longVersionCode})", p.muted)
        line(c, "Package ${ctx.packageName}", p.muted)
        line(c, "Installed ${Fmt.whenFull(info.firstInstallTime)} · updated ${Fmt.whenFull(info.lastUpdateTime)}", p.muted)
        line(c, "Runs fully offline: no accounts, no network, no tracking. Snapshots stay in this app's private storage.", p.muted)
        line(c, "© binaryBug", p.muted, true)
        col.addCard(ctx, c)
    }

    private fun line(c: android.widget.LinearLayout, s: String, color: Int = p.text, bold: Boolean = false) =
        c.addView(ctx.label(s, 14f, color, bold), lp(t = ctx.dp(6)))

    private fun scheduleCard() {
        val c = ctx.card(p)
        val job = Scheduler.pending(ctx)
        val enabled = Prefs.scheduleEnabled(ctx)
        c.addView(ctx.heading("Background scan", p))
        when {
            job != null -> line(c, "● Registered with Android", p.free, true)
            !enabled -> line(c, "○ Turned off", p.muted, true)
            else -> line(c, "○ Not registered yet (opens when the app starts)", p.used, true)
        }
        val mins = Prefs.intervalMin(ctx)
        line(c, "Repeats about every ${intervalLabels.getOrElse(intervalMins.indexOf(mins)) { "$mins minutes" }}")
        line(c, "Survives restart: ${if (job?.isPersisted == true) "yes" else "–"} · runs when the battery is not low")

        val last = Prefs.lastRun(ctx)
        if (last == null) {
            line(c, "Last run: never")
        } else {
            line(c, "Last run: ${Fmt.whenFull(last.endMs)} (${Fmt.ago(last.endMs)})")
            val statusColor = if (last.status == "ok") p.free else p.used
            line(c, "${last.trigger} · ${last.status} · took ${Fmt.dur(last.endMs - last.startMs)}", statusColor, true)
            if (last.summary.isNotEmpty()) line(c, last.summary, p.muted)
        }
        if (job != null) {
            val base = maxOf(Prefs.lastScheduledEndMs(ctx), Prefs.scheduledAtMs(ctx))
            val next = base + mins * 60_000L
            val text = if (next > System.currentTimeMillis()) "about ${Fmt.timeShort(next)}" else "any time now (Android is waiting for good conditions)"
            line(c, "Next run: $text")
            line(c, "Approximate: Android groups background work to save battery, so it can be late.", p.muted)
        }
        if (ScanController.running) line(c, "A scan is running now.", p.primary, true)

        val sw = Switch(ctx).apply {
            text = "Run automatically"
            isChecked = enabled
            minimumHeight = ctx.dp(48)
            setTextColor(p.text)
            setOnCheckedChangeListener { _, on ->
                Prefs.setScheduleEnabled(ctx, on)
                if (on) Scheduler.schedule(ctx) else Scheduler.cancel(ctx)
                refresh()
            }
        }
        c.addView(sw, lp(t = ctx.dp(8)))
        c.addView(ctx.label("Repeat every", 12f, p.muted), lp(t = ctx.dp(8)))
        c.addView(
            Dropdown(ctx) { i ->
                Prefs.setIntervalMin(ctx, intervalMins[i])
                if (Prefs.scheduleEnabled(ctx)) Scheduler.schedule(ctx)
                refresh()
            }.apply { load(intervalLabels, intervalMins.indexOf(mins).coerceAtLeast(0)); contentDescription = "Repeat every" },
            lp(MATCH, ctx.dp(48)),
        )
        val buttons = ctx.hbox()
        buttons.addView(ctx.pill("Scan now", p) { a.requestScan() })
        buttons.addView(ctx.pill("Add Quick Settings tile", p, false) { a.addQuickTile() }, lp(WRAP, WRAP, l = ctx.dp(8)))
        c.addView(buttons, lp(t = ctx.dp(12)))
        col.addCard(ctx, c)
    }

    private fun settingsCard() {
        val c = ctx.card(p)
        c.addView(ctx.heading("Alerts and history", p))
        c.addView(ctx.label("Alert me when free space drops by", 12f, p.muted), lp(t = ctx.dp(8)))
        val cur = alertGb.indexOf(Prefs.alertDropGb(ctx)).let { if (it < 0) 3 else it }
        c.addView(
            Dropdown(ctx) { Prefs.setAlertDropGb(ctx, alertGb[it]) }
                .apply { load(alertGb.map { if (it == 0f) "Never" else "%s GB".format(if (it == it.toInt().toFloat()) it.toInt().toString() else it.toString()) }, cur); contentDescription = "Alert threshold" },
            lp(MATCH, ctx.dp(48)),
        )
        c.addView(ctx.label("Alert me when an app or folder grows by", 12f, p.muted), lp(t = ctx.dp(8)))
        val curGrowth = growthGb.indexOf(Prefs.growthAlertGb(ctx)).let { if (it < 0) 3 else it }
        c.addView(
            Dropdown(ctx) { Prefs.setGrowthAlertGb(ctx, growthGb[it]) }
                .apply { load(growthGb.map { if (it == 0f) "Never" else "%s GB".format(if (it == it.toInt().toFloat()) it.toInt().toString() else it.toString()) }, curGrowth); contentDescription = "Growth alert threshold" },
            lp(MATCH, ctx.dp(48)),
        )
        c.addView(
            Switch(ctx).apply {
                text = "Warn once a day when under 10% is free"
                isChecked = Prefs.lowSpaceNotice(ctx)
                minimumHeight = ctx.dp(48)
                setTextColor(p.text)
                setOnCheckedChangeListener { _, on -> Prefs.setLowSpaceNotice(ctx, on) }
            },
            lp(t = ctx.dp(8)),
        )
        c.addView(ctx.label("Keep snapshots for", 12f, p.muted), lp(t = ctx.dp(8)))
        c.addView(
            Dropdown(ctx) { Prefs.setRetentionDays(ctx, retentionDays[it]) }
                .apply { load(retentionDays.map { "$it days" }, retentionDays.indexOf(Prefs.retentionDays(ctx)).coerceAtLeast(0)); contentDescription = "Keep snapshots for" },
            lp(MATCH, ctx.dp(48)),
        )
        c.addView(ctx.label("The newest two snapshots are always kept so you can compare.", 12f, p.muted), lp(t = ctx.dp(6)))
        col.addCard(ctx, c)
    }

    private fun permissionsCard() {
        val c = ctx.card(p)
        c.addView(ctx.heading("Permissions and reliability", p))
        val files = Environment.isExternalStorageManager()
        line(c, if (files) "✓ All files access: granted" else "✗ All files access: not granted (scans are skipped)", if (files) p.free else p.used, true)
        if (!files) c.addView(ctx.pill("Grant all files access", p) { a.openAllFilesSettings() }, lp(WRAP, WRAP, t = ctx.dp(8)))

        val usage = UsageAccess.granted(ctx)
        line(c, if (usage) "✓ Usage access: granted (per-app sizes)" else "✗ Usage access: not granted (Apps tab is empty)", if (usage) p.free else p.used, true)
        if (!usage) c.addView(ctx.pill("Grant Usage access", p) { a.openUsageAccessSettings() }, lp(WRAP, WRAP, t = ctx.dp(8)))

        val notif = Notifier.canNotify(ctx)
        line(c, if (notif) "✓ Notifications: allowed" else "✗ Notifications: blocked (no alerts)", if (notif) p.free else p.used, true)
        if (!notif) c.addView(ctx.pill("Allow notifications", p) { a.requestNotificationPermission() }, lp(WRAP, WRAP, t = ctx.dp(8)))

        val pm = ctx.getSystemService(PowerManager::class.java)
        val unrestricted = pm.isIgnoringBatteryOptimizations(ctx.packageName)
        line(c, if (unrestricted) "✓ Battery: not restricted" else "! Battery: may be restricted", if (unrestricted) p.free else p.used, true)
        line(c, "Some phones (realme, Oppo, Xiaomi) stop background work for apps they think are idle. If scans stop appearing, set this app to Unrestricted or allow background activity.", p.muted)
        val row = ctx.hbox()
        row.addView(ctx.pill("Battery settings", p, false) { a.openBatterySettings() })
        row.addView(ctx.pill("App settings", p, false) { a.openAppSettings() }, lp(WRAP, WRAP, l = ctx.dp(8)))
        c.addView(row, lp(t = ctx.dp(8)))
        col.addCard(ctx, c)
    }

    /** Whether the Snapshots card is open. Collapsed by default; kept while the app is open, so a refresh does not close it. */
    private var snapshotsOpen = false

    /** The snapshot table of the Windows dashboard: when, used/free, change vs the previous one, skipped folders, and what to compare. */
    private fun snapshotsCard() {
        val metas = Model.metas // oldest first
        val c = ctx.card(p).apply { setPadding(0, ctx.dp(14), 0, ctx.dp(8)) }

        // The list grows with every scan, so the card is collapsed by default: a tap on the header opens or closes it.
        // Everything that grows lives in [body]; the header and one summary line stay.
        val body = ctx.vbox()
        val title = "Snapshots (${metas.size})"
        val newest = metas.lastOrNull()
        val summary = ctx.label(
            if (newest == null) "None yet" else "Newest ${Fmt.whenFull(newest.takenMs)} · ${formatBytes(newest.free)} free",
            13f, p.muted,
        )
        val arrow = ctx.label("", 20f, p.muted, true).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        val header = ctx.hbox().apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ctx.dp(48)
            setPadding(ctx.dp(16), 0, ctx.dp(16), 0)
            isClickable = true
            isFocusable = true
        }
        header.addView(ctx.heading(title, p), lp(0, WRAP, 1f))
        header.addView(arrow)
        fun apply() {
            body.visibility = if (snapshotsOpen) View.VISIBLE else View.GONE
            summary.visibility = if (snapshotsOpen) View.GONE else View.VISIBLE
            arrow.text = if (snapshotsOpen) "▾" else "▸"
            header.contentDescription = "$title, ${if (snapshotsOpen) "expanded" else "collapsed"}. Tap to ${if (snapshotsOpen) "collapse" else "expand"}."
        }
        header.setOnClickListener { snapshotsOpen = !snapshotsOpen; apply() }
        c.addView(header)
        c.addView(summary, lp(l = ctx.dp(16), r = ctx.dp(16)))
        c.addView(body)
        apply()

        val backup = ctx.hbox()
        backup.addView(ctx.pill("Export", p, false) { a.exportSnapshots() })
        backup.addView(ctx.pill("Import", p, false) { a.importSnapshots() }, lp(WRAP, WRAP, l = ctx.dp(8)))
        body.addView(backup, lp(l = ctx.dp(16), r = ctx.dp(16), t = ctx.dp(4)))
        body.addView(
            ctx.label("Export saves all snapshots into one zip file so you can keep your history; Import adds them back.", 12f, p.muted),
            lp(l = ctx.dp(16), r = ctx.dp(16), t = ctx.dp(6)),
        )
        if (metas.isEmpty()) {
            body.addView(ctx.label("None yet. Tap the play button at the top right to take the first one.", 14f, p.muted), lp(l = ctx.dp(16), r = ctx.dp(16), t = ctx.dp(6)))
        } else {
            body.addView(
                ctx.label("Tap a snapshot to compare it. “from” and “to” mark the pair currently shown in Changes.", 12f, p.muted),
                lp(l = ctx.dp(16), r = ctx.dp(16), t = ctx.dp(2)),
            )
        }
        val newestFirst = metas.indices.reversed().take(30)
        for (i in newestFirst) {
            val m = metas[i]
            val prev = metas.getOrNull(i - 1)
            val marks = listOfNotNull(if (m.id == Model.fromId) "from" else null, if (m.id == Model.toId) "to" else null)
            val title = Fmt.whenFull(m.takenMs) + if (marks.isEmpty()) "" else "   [${marks.joinToString(" · ")}]"
            val coverage = (if (m.appsTotal >= 0) "apps measured" else "apps not measured") + if (m.allFiles) "" else " · no all-files access"
            val sub = "${formatBytes(m.free)} free · ${"%,d".format(m.filesScanned)} files · scan ${Fmt.dur((m.scanSec * 1000).toLong())} · ${m.skipped} skipped · $coverage"
            val change = prev?.let { Fmt.delta(m.used - it.used) + " vs previous" }
            val delta = prev?.let { m.used - it.used } ?: 0L
            body.addView(
                ctx.listRow(p, title, sub, formatBytes(m.used) + " used", if (delta > 0) p.used else p.text, change, chevron = true) { snapshotActions(m) },
            )
        }
        if (metas.size > newestFirst.size) {
            body.addView(ctx.label("Showing the newest ${newestFirst.size} of ${metas.size}.", 12f, p.muted), lp(l = ctx.dp(16), r = ctx.dp(16), t = ctx.dp(8)))
        }
        col.addCard(ctx, c)
    }

    private fun confirmDelete(m: SnapshotMeta) {
        AlertDialog.Builder(ctx)
            .setTitle("Delete this snapshot?")
            .setMessage("${Fmt.whenFull(m.takenMs)} will be removed from this phone. Changes will go back to comparing the latest two snapshots.")
            .setPositiveButton("Delete") { _, _ ->
                SnapshotStore.delete(ctx, m.id)
                Model.reload(ctx)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun snapshotActions(m: SnapshotMeta) {
        val metas = Model.metas
        val prev = SnapshotPicker.previousOf(metas, m.id)
        val items = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        fun open(pair: Pair<String?, String?>) { Model.select(ctx, pair.first, pair.second); a.goToTab(2) }
        if (prev != null) {
            items.add("Compare with the previous snapshot")
            actions.add { open(Pair(prev.id, m.id)) }
        }
        items.add("Use as “From” (older) in Changes")
        actions.add { open(SnapshotPicker.withFrom(metas, m.id, Model.toId)) }
        items.add("Use as “To” (newer) in Changes")
        actions.add { open(SnapshotPicker.withTo(metas, m.id, Model.fromId)) }
        items.add("Delete this snapshot")
        actions.add { confirmDelete(m) }
        AlertDialog.Builder(ctx)
            .setTitle(Fmt.whenFull(m.takenMs))
            .setItems(items.toTypedArray()) { _, i -> actions[i]() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
