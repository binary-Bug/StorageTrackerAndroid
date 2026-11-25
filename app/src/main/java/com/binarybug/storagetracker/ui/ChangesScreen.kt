package com.binarybug.storagetracker.ui

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ListView
import com.binarybug.storagetracker.Model
import com.binarybug.storagetracker.data.SnapshotMeta
import com.binarybug.storagetracker.formatBytes
import com.binarybug.storagetracker.scan.Category
import com.binarybug.storagetracker.scan.CategoryDiffs
import com.binarybug.storagetracker.scan.Comparison
import com.binarybug.storagetracker.scan.Compare

/**
 * Pick two snapshots and see what changed, three ways: by folder (the "hot spots" of the Windows dashboard), by file,
 * and by category, so a change of, say, 300 MB can be traced to the categories and then to the exact files behind it.
 */
class ChangesScreen(private val ctx: Context, private val p: Palette, private val a: Actions) : Screen {
    private val list = ListView(ctx).apply { setBackgroundColor(p.bg); divider = null; isVerticalScrollBarEnabled = true }
    private val adapter = ViewListAdapter()
    override val root: View get() = list

    private val mb = 1024L * 1024L
    private val minValues = longArrayOf(1L, 10 * mb, 50 * mb, 100 * mb, 500 * mb, 1024 * mb)
    private val minLabels = listOf("Any change", "10 MB", "50 MB", "100 MB", "500 MB", "1 GB")
    private var direction = 0
    private var minIdx = 2
    private var query = ""
    private var view = 0 // 0 folders, 1 files, 2 categories
    private var categoryFilter: Category? = null
    private var shown: List<SnapshotMeta> = emptyList()

    // Item 0 of "From" is "None", so the older snapshot is optional and a single snapshot is never compared with itself.
    private val fromDd = Dropdown(ctx) { i -> Model.select(ctx, shown.getOrNull(i - 1)?.id, Model.toId) }
    private val toDd = Dropdown(ctx) { i -> if (i in shown.indices) Model.select(ctx, Model.fromId, shown[i].id) }
    private val summary = ctx.label("", 13f, p.muted)
    private val viewSeg = Segmented(ctx, p, listOf("Folders", "Files", "Categories"), 0) { view = it; refresh() }
    private val filterLabel = ctx.label("", 14f, p.text, true)
    private val filterRow = ctx.hbox()

    init {
        val h = ctx.vbox().apply { setPadding(ctx.dp(16), ctx.dp(12), ctx.dp(16), ctx.dp(8)) }
        h.addView(ctx.heading("Compare two snapshots", p))
        h.addView(ctx.label("From (older)", 12f, p.muted), lp(t = ctx.dp(8)))
        fromDd.contentDescription = "Compare from older snapshot"
        h.addView(fromDd, lp(MATCH, ctx.dp(48)))
        h.addView(ctx.label("To (newer)", 12f, p.muted), lp(t = ctx.dp(4)))
        toDd.contentDescription = "Compare to newer snapshot"
        h.addView(toDd, lp(MATCH, ctx.dp(48)))

        val quick = ctx.hbox()
        quick.addView(ctx.pill("Previous", p, false) { quickPick(0) }, lp(0, WRAP, 1f))
        quick.addView(ctx.pill("24 h ago", p, false) { quickPick(1) }, lp(0, WRAP, 1f, l = ctx.dp(8)))
        quick.addView(ctx.pill("Oldest", p, false) { quickPick(2) }, lp(0, WRAP, 1f, l = ctx.dp(8)))
        h.addView(quick, lp(t = ctx.dp(8)))

        h.addView(ctx.label("View", 12f, p.muted), lp(t = ctx.dp(12)))
        h.addView(viewSeg, lp(t = ctx.dp(4)))
        h.addView(ctx.label("Show", 12f, p.muted), lp(t = ctx.dp(8)))
        h.addView(Segmented(ctx, p, listOf("All", "Grew", "Shrank"), 0) { direction = it; refresh() }, lp(t = ctx.dp(4)))
        h.addView(ctx.label("Minimum change", 12f, p.muted), lp(t = ctx.dp(8)))
        h.addView(Dropdown(ctx) { minIdx = it; refresh() }.apply { load(minLabels, minIdx); contentDescription = "Minimum change" }, lp(MATCH, ctx.dp(48)))
        val search = EditText(ctx).apply {
            hint = "Filter by name"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            minimumHeight = ctx.dp(48)
            contentDescription = "Filter by folder or file name"
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { query = s?.toString().orEmpty(); refresh() }
            })
        }
        h.addView(search, lp(t = ctx.dp(8)))

        filterRow.visibility = View.GONE
        filterRow.addView(filterLabel, lp(0, WRAP, 1f))
        filterRow.addView(ctx.pill("Show all types", p, false) { categoryFilter = null; refresh() }, lp(WRAP, WRAP, l = ctx.dp(8)))
        h.addView(filterRow, lp(t = ctx.dp(8)))

        h.addView(summary, lp(t = ctx.dp(10)))
        list.addHeaderView(h, null, false)
        list.adapter = adapter
    }

    private fun quickPick(kind: Int) {
        val m = Model.metas
        if (m.isEmpty()) return
        val latest = m.last()
        val from = when (kind) {
            0 -> m.getOrNull(m.size - 2)
            1 -> m.lastOrNull { it.takenMs <= latest.takenMs - 86_400_000L } ?: m.first()
            else -> m.first()
        }
        Model.select(ctx, if (from === latest) null else from?.id, latest.id)
    }

    private fun dirOk(d: Long) = direction == 0 || (direction == 1 && d > 0) || (direction == 2 && d < 0)
    private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

    private fun minText() = if (minIdx == 0) "any amount" else "${minLabels[minIdx]} or more"

    override fun refresh() {
        shown = Model.metas.reversed()
        val labels = shown.map { "${Fmt.whenFull(it.takenMs)} · ${formatBytes(it.used)} used" }
        fromDd.load(listOf("None (no earlier snapshot)") + labels, shown.indexOfFirst { it.id == Model.fromId } + 1)
        toDd.load(labels, shown.indexOfFirst { it.id == Model.toId }.coerceAtLeast(0))

        val cat = categoryFilter
        filterRow.visibility = if (cat != null && view == 1) View.VISIBLE else View.GONE
        if (cat != null) filterLabel.text = "Only: ${cat.label}"

        val cmp = Model.comparison
        if (cmp == null || cmp.older == null) {
            summary.text = if (Model.loaded && shown.isNotEmpty()) "Pick an older snapshot in “From” to see what changed." else "No snapshots yet. Run a scan first."
            adapter.rows = emptyList()
            return
        }
        when (view) {
            0 -> folderRows(cmp)
            1 -> fileRows(cmp)
            else -> categoryRows(cmp)
        }
    }

    private fun folderRows(cmp: Comparison) {
        val min = minValues[minIdx]
        val all = cmp.hot.filter {
            Math.abs(it.self) >= min && dirOk(it.self) && (query.isEmpty() || it.node.path.contains(query, ignoreCase = true))
        }
        val rows = all.take(200)
        summary.text = "${plural(all.size, "folder", "folders")} changed by ${minText()}" + if (all.size > rows.size) " (showing the biggest ${rows.size})" else ""
        adapter.rows = rows.map { h ->
            val n = h.node
            val badge = when { n.a == null -> " · new or now over 25 MB"; n.b == null -> " · gone or now under 25 MB"; else -> "" }
            val row: () -> View = {
                ctx.listRow(
                    p, if (n.path == "/") "/ (storage root)" else n.name, Compare.parentOf(n.path) + badge,
                    Fmt.delta(h.self), if (h.self > 0) p.used else p.free, n.b?.let { "folder now ${formatBytes(it)}" },
                    chevron = true,
                ) { a.openInBrowse(n.path) }
            }
            row
        }
    }

    private fun fileRows(cmp: Comparison) {
        val min = minValues[minIdx]
        val cat = categoryFilter
        val all = cmp.allFiles.filter { f ->
            val d = f.delta
            d != 0L && Math.abs(d) >= min && dirOk(d) && (cat == null || f.category == cat) &&
                (query.isEmpty() || f.path.contains(query, ignoreCase = true))
        }
        val rows = all.take(200)
        summary.text = "${plural(all.size, "file", "files")} changed by ${minText()}" + (if (all.size > rows.size) " (showing the biggest ${rows.size})" else "") +
            ". Only the biggest files are tracked (the 50 largest plus the 30 largest of each type), so a file can appear or vanish here just because it moved in or out of those lists."
        adapter.rows = rows.map { f ->
            val status = when { f.a == null -> "new in the tracked files"; f.b == null -> "no longer in the tracked files"; else -> "size changed" }
            val row: () -> View = {
                ctx.listRow(
                    p, f.name, "${f.parent} · ${f.category.label} · $status",
                    Fmt.delta(f.delta), if (f.delta > 0) p.used else p.free, f.b?.let { "now ${formatBytes(it)}" },
                )
            }
            row
        }
    }

    private fun categoryRows(cmp: Comparison) {
        val older = cmp.older ?: return
        val diff = CategoryDiffs.between(older, cmp.newer)
        val min = minValues[minIdx]
        val rows = diff.rows.filter { it.delta != 0L && Math.abs(it.delta) >= min && dirOk(it.delta) }
        summary.text = "Used space changed by ${Fmt.delta(diff.usedDelta)} in total. ${plural(rows.size, "category", "categories")} changed by ${minText()}. " +
            "Tap a file type to see the exact files, or an app row to open the Apps tab." + (diff.note?.let { " $it" } ?: "")
        val out = ArrayList<() -> View>()
        out.add { ctx.listRow(p, "Total used space", "newer snapshot minus older", Fmt.delta(diff.usedDelta), if (diff.usedDelta > 0) p.used else p.free, "now ${formatBytes(cmp.newer.meta.used)}") }
        for (r in rows) {
            val share = if (diff.usedDelta != 0L && (r.delta > 0) == (diff.usedDelta > 0)) "${Fmt.pct(Math.abs(r.delta), Math.abs(diff.usedDelta))} of the change" else ""
            val go: (() -> Unit)? = when {
                r.key.startsWith("cat:") -> ({ categoryFilter = Category.fromName(r.key.removePrefix("cat:")); view = 1; viewSeg.set(1); refresh() })
                r.key == "apps" || r.key == "appdata" || r.key == "cache" -> ({ a.goToTab(1) })
                else -> null
            }
            out.add { ctx.legendRow(p, p.cat[r.color], r.label, "${formatBytes(r.before)} → ${formatBytes(r.after)}", Fmt.delta(r.delta), share, go != null, go) }
        }
        adapter.rows = out
    }
}
