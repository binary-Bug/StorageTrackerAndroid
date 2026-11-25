package com.binarybug.storagetracker.ui

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import com.binarybug.storagetracker.Model
import com.binarybug.storagetracker.formatBytes
import com.binarybug.storagetracker.scan.AppDiff
import com.binarybug.storagetracker.scan.Breakdown
import com.binarybug.storagetracker.scan.Idle
import com.binarybug.storagetracker.scan.UnusedApps

/** Per-app storage (installed code, data, cache) from Android's storage statistics, with growth since the older snapshot. */
class AppsScreen(private val ctx: Context, private val p: Palette, private val a: Actions) : Screen {
    private val list = ListView(ctx).apply { setBackgroundColor(p.bg); divider = null }
    private val adapter = ViewListAdapter()
    override val root: View get() = list

    private var sort = 0
    private var userOnly = true
    private var query = ""
    private val summary = ctx.label("", 14f, p.text)
    private val notice = ctx.vbox()

    init {
        val h = ctx.vbox().apply { setPadding(ctx.dp(16), ctx.dp(12), ctx.dp(16), ctx.dp(8)) }
        h.addView(ctx.heading("Apps", p))
        h.addView(summary, lp(t = ctx.dp(4)))
        h.addView(notice, lp(t = ctx.dp(4)))
        h.addView(ctx.label("Sort by", 12f, p.muted), lp(t = ctx.dp(10)))
        h.addView(Segmented(ctx, p, listOf("Size", "Data", "Cache", "Growth", "Unused"), 0) { sort = it; refresh() }, lp(t = ctx.dp(4)))
        h.addView(ctx.label("Show", 12f, p.muted), lp(t = ctx.dp(10)))
        h.addView(Segmented(ctx, p, listOf("User apps", "All apps"), 0) { userOnly = it == 0; refresh() }, lp(t = ctx.dp(4)))
        h.addView(
            EditText(ctx).apply {
                hint = "Search apps"
                setSingleLine()
                inputType = InputType.TYPE_CLASS_TEXT
                imeOptions = EditorInfo.IME_ACTION_DONE
                minimumHeight = ctx.dp(48)
                contentDescription = "Search apps by name"
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
                    override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) { query = s?.toString().orEmpty(); refresh() }
                })
            },
            lp(t = ctx.dp(8)),
        )
        list.addHeaderView(h, null, false)
        list.adapter = adapter
    }

    override fun refresh() {
        notice.removeAllViews()
        val cmp = Model.comparison
        if (cmp == null) {
            summary.text = if (Model.loaded) "No snapshots yet. Run a scan first." else "Loading…"
            adapter.rows = emptyList()
            return
        }
        if (!cmp.newer.appsAvailable || cmp.newer.apps.isEmpty()) {
            summary.text = "Per-app sizes are not available in the latest snapshot."
            notice.addView(ctx.label("Android only lets an app read other apps' sizes after you switch on “Usage access” for it. Then run a scan.", 13f, p.muted))
            notice.addView(ctx.pill("Grant Usage access", p) { a.openUsageAccessSettings() }, lp(WRAP, WRAP, t = ctx.dp(8)))
            adapter.rows = emptyList()
            return
        }
        val comparing = cmp.older != null
        val all = cmp.apps.filter { it.newer != null }
        val shown = all.filter { (!userOnly || it.newer?.system == false) && (query.isEmpty() || it.label.contains(query, true) || it.pkg.contains(query, true)) }
        val now = System.currentTimeMillis()
        val idleOf: (String) -> Idle? = if (sort == 4) {
            val used = UnusedApps.lastUsed(ctx)
            val installed = UnusedApps.installedAt(ctx)
            ({ pkg -> UnusedApps.idle(used[pkg] ?: 0L, installed[pkg] ?: 0L, now) })
        } else ({ null })
        val ordered: List<AppDiff> = when (sort) {
            1 -> shown.sortedByDescending { it.newer?.dataBytes ?: 0L }
            2 -> shown.sortedByDescending { it.newer?.cacheBytes ?: 0L }
            3 -> shown.filter { it.a != null && it.delta != 0L }.sortedByDescending { Math.abs(it.delta) }
            4 -> shown.filter { UnusedApps.isUnused(idleOf(it.pkg)) }.sortedByDescending { it.b ?: 0L }
            else -> shown.sortedByDescending { it.b ?: 0L }
        }
        val totalAll = all.sumOf { it.b ?: 0L }
        val totalCache = all.sumOf { it.newer?.cacheBytes ?: 0L }
        summary.text = if (sort == 4) {
            "${ordered.size} apps not opened for ${UnusedApps.THRESHOLD_DAYS}+ days use ${formatBytes(ordered.sumOf { it.b ?: 0L })}. Tap one to open its App info and uninstall or clear it."
        } else {
            "${all.size} apps use ${formatBytes(totalAll)}; ${formatBytes(totalCache)} of that is cache that can be cleared. Tap an app for its App info."
        }
        adapter.rows = ordered.take(300).map { d ->
            val e = d.newer!!
            val row: () -> View = {
                val second = if (sort == 4) UnusedApps.describe(idleOf(e.pkg)) else e.pkg
                val sub = "app ${formatBytes(e.appBytes)} · data ${formatBytes(e.dataBytes - e.cacheBytes)} · cache ${formatBytes(e.cacheBytes)}\n$second" +
                    if (e.system) " · system" else ""
                val change = when {
                    !comparing || sort == 4 -> null
                    d.a == null -> "new"
                    d.delta != 0L -> Fmt.delta(d.delta)
                    else -> null
                }
                ctx.listRow(p, e.label, sub, formatBytes(e.total), p.text, change, chevron = true) { a.openAppInfo(e.pkg) }
            }
            row
        }
        if (ordered.isEmpty()) summary.text = summary.text.toString() + " Nothing matches the filters."
    }
}
