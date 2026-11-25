package com.binarybug.storagetracker.ui

import android.content.Context
import android.view.View
import android.widget.ListView
import com.binarybug.storagetracker.Model
import com.binarybug.storagetracker.formatBytes
import com.binarybug.storagetracker.scan.Breakdown
import com.binarybug.storagetracker.scan.Category
import com.binarybug.storagetracker.scan.Compare
import com.binarybug.storagetracker.scan.FileDiff
import com.binarybug.storagetracker.scan.Node

/**
 * Browse the latest snapshot three ways: by folder (drill down), by category (photos, videos, apps, ...), or as the
 * list of largest files. Categories correspond to the colours of "Where your storage goes" on the Overview.
 */
class BrowseScreen(private val ctx: Context, private val p: Palette, private val a: Actions) : Screen {
    private val list = ListView(ctx).apply { setBackgroundColor(p.bg); divider = null }
    private val adapter = ViewListAdapter()
    override val root: View get() = list

    private var mode = 0 // 0 folders, 1 categories, 2 large files
    private var current = "/"
    private var catKey: String? = null
    private val seg = Segmented(ctx, p, listOf("Folders", "Categories", "Large files"), 0) { mode = it; catKey = null; refresh() }
    private val crumb = ctx.label("/", 14f, p.text, true)
    private val upBtn = ctx.pill("↑ Up", p, filled = false) { goUp() }
    private val note = ctx.label("", 12f, p.muted)
    private val crumbRow = ctx.hbox()

    init {
        val h = ctx.vbox().apply { setPadding(ctx.dp(16), ctx.dp(12), ctx.dp(16), ctx.dp(8)) }
        h.addView(seg)
        crumbRow.addView(upBtn, lp(WRAP, WRAP, 0f, r = ctx.dp(12)))
        crumbRow.addView(crumb, lp(0, WRAP, 1f))
        h.addView(crumbRow, lp(t = ctx.dp(8)))
        h.addView(note, lp(t = ctx.dp(6)))
        list.addHeaderView(h, null, false)
        list.adapter = adapter
    }

    fun open(path: String) {
        current = if (Model.comparison?.nodes?.containsKey(path) == true) path else "/"
        mode = 0
        catKey = null
        seg.set(0)
        refresh()
    }

    /** [key] like "cat:IMAGES" opens that category's biggest files; null shows the category list. */
    fun openCategory(key: String?) {
        mode = 1
        catKey = key
        seg.set(1)
        refresh()
    }

    fun canGoUp(): Boolean = (mode == 0 && current != "/") || (mode == 1 && catKey != null)

    fun goUp() {
        if (mode == 1) catKey = null else current = Compare.parentOf(current)
        refresh()
    }

    override fun refresh() {
        val cmp = Model.comparison
        if (cmp == null) {
            crumbRow.visibility = View.GONE
            note.text = if (Model.loaded) "No snapshots yet. Run a scan first." else "Loading…"
            adapter.rows = emptyList()
            return
        }
        when (mode) {
            0 -> folders(cmp.nodes[current] ?: cmp.root, cmp.older != null)
            1 -> categories()
            else -> files(cmp.files, cmp.older != null)
        }
    }

    private fun categories() {
        val snap = Model.comparison?.newer ?: return
        val key = catKey
        if (key != null) { categoryFiles(snap, key); return }
        crumbRow.visibility = View.GONE
        val b = Breakdown.build(snap)
        note.text = "Same groups and colours as the Overview. File types come from the scan of shared storage; apps, app data and cache come from Android's per-app statistics."
        val rows = ArrayList<() -> View>()
        for (s in b.slices) {
            rows.add {
                val go: (() -> Unit)? = when {
                    s.key.startsWith("cat:") -> ({ catKey = s.key; refresh() })
                    s.key == "apps" || s.key == "appdata" || s.key == "cache" -> ({ a.goToTab(1) })
                    else -> null
                }
                ctx.legendRow(p, p.cat[s.color], s.label, s.note, formatBytes(s.bytes), Fmt.pct(s.bytes, b.usable), go != null, go)
            }
        }
        rows.add { ctx.legendRow(p, p.track, "Free", "available to use", formatBytes(b.free), Fmt.pct(b.free, b.usable)) }
        adapter.rows = rows
    }

    private fun categoryFiles(snap: com.binarybug.storagetracker.data.Snapshot, key: String) {
        val cat = Category.fromName(key.removePrefix("cat:"))
        crumbRow.visibility = View.VISIBLE
        upBtn.visibility = View.VISIBLE
        crumb.text = cat?.label ?: key
        val total = snap.cats.firstOrNull { it.name == cat?.name }
        note.text = "The biggest ${cat?.label?.lowercase() ?: "files"} found" +
            (if (total != null) " (${"%,d".format(total.count)} files, ${formatBytes(total.bytes)} in all)." else ".")
        val rows = snap.catFiles.filter { it.cat == cat?.name }.sortedByDescending { it.size }.map { f ->
            val row: () -> View = { ctx.listRow(p, f.path.substringAfterLast('/'), Compare.parentOf(f.path), formatBytes(f.size), p.text, null) }
            row
        }
        adapter.rows = if (rows.isEmpty()) listOf { ctx.listRow(p, "No files over 1 MB in this category", null, null, p.text, null) } else rows
    }

    private fun folders(node: Node, comparing: Boolean) {
        current = node.path
        crumbRow.visibility = View.VISIBLE
        upBtn.visibility = if (current == "/") View.GONE else View.VISIBLE
        crumb.text = if (current == "/") "Internal storage" else current
        crumb.contentDescription = "Folder $current"
        val pruneMb = Model.comparison?.newer?.meta?.minFolderMb ?: 25
        note.text = "Sorted by size of change. Folders under $pruneMb MB are not listed on their own; they are counted in their parent."
        val kids = node.kids.sortedWith(
            compareByDescending<Node> { if (comparing) Math.abs(it.delta) else it.b ?: 0L }.thenByDescending { it.b ?: 0L },
        )
        val rows = ArrayList<() -> View>()
        if (node.selfB > 0 || node.selfA > 0) {
            rows.add {
                ctx.listRow(
                    p, "Files here + small folders", "directly in this folder",
                    formatBytes(node.selfB), p.text, if (comparing) Fmt.delta(node.selfDelta) else null,
                )
            }
        }
        for (k in kids) {
            rows.add {
                val tag = when { !comparing -> ""; k.a == null -> "NEW · "; k.b == null -> "GONE · "; else -> "" }
                val change = if (comparing) Fmt.delta(k.delta) else null
                ctx.listRow(
                    p, k.name,
                    tag + (if (k.kids.isEmpty()) "no sub-folders listed" else if (k.kids.size == 1) "1 sub-folder" else "${k.kids.size} sub-folders"),
                    k.b?.let { formatBytes(it) } ?: "—", p.text, change,
                    chevron = k.kids.isNotEmpty(),
                    onClick = if (k.kids.isNotEmpty()) ({ current = k.path; refresh() }) else null,
                )
            }
        }
        adapter.rows = rows
    }

    private fun files(all: List<FileDiff>, comparing: Boolean) {
        crumbRow.visibility = View.GONE
        note.text = "The 50 largest files in the latest snapshot. A file missing from one list may just have moved in or out of the top 50."
        adapter.rows = all.filter { it.b != null }.map { f ->
            val row: () -> View = {
                val status = when {
                    !comparing -> null
                    f.a == null -> "new in top 50"
                    f.delta != 0L -> Fmt.delta(f.delta)
                    else -> "unchanged"
                }
                ctx.listRow(p, f.name, f.parent, formatBytes(f.b ?: 0L), p.text, status)
            }
            row
        }
    }
}
