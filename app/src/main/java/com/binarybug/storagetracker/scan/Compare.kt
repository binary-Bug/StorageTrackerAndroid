package com.binarybug.storagetracker.scan

import com.binarybug.storagetracker.data.Snapshot

/** A folder in the comparison tree. [a]/[b] are folder totals before/now (null = not tracked in that snapshot). */
class Node(val path: String) {
    val name: String = if (path == "/") "/" else path.substringAfterLast('/')
    var a: Long? = null
    var b: Long? = null
    var selfA = 0L
    var selfB = 0L
    val kids = ArrayList<Node>()
    val delta: Long get() = (b ?: 0L) - (a ?: 0L)
    val selfDelta: Long get() = selfB - selfA
}

/** "Direct change" of one folder: files directly in it plus sub-folders too small to track, so ancestors are not double counted. */
class HotSpot(val node: Node, val self: Long)

class FileDiff(val path: String, val a: Long?, val b: Long?) {
    val delta: Long get() = (b ?: 0L) - (a ?: 0L)
    val name: String get() = path.substringAfterLast('/')
    val parent: String get() = Compare.parentOf(path)
    val category: Category get() = Category.of(name)
}

/** An app's total size (code + data) in the older/newer snapshot; null = the app was not present or not measured. */
class AppDiff(val pkg: String, val label: String, val a: Long?, val b: Long?, val newer: com.binarybug.storagetracker.data.AppEntry?) {
    val delta: Long get() = (b ?: 0L) - (a ?: 0L)
}

class Comparison(
    val older: Snapshot?,
    val newer: Snapshot,
    val nodes: Map<String, Node>,
    val root: Node,
    val hot: List<HotSpot>,
    val files: List<FileDiff>,
    /** Every tracked file: the 50 largest overall plus the 30 largest of each type, from both snapshots. */
    val allFiles: List<FileDiff>,
    val apps: List<AppDiff>,
)

object Compare {
    fun parentOf(path: String): String {
        val i = path.lastIndexOf('/')
        return if (i <= 0) "/" else path.substring(0, i)
    }

    fun run(older: Snapshot?, newer: Snapshot): Comparison {
        val nodes = HashMap<String, Node>()
        fun node(path: String): Node = nodes.getOrPut(path) { Node(path) }

        fun accumulate(dirs: List<Pair<String, Long>>, isNewer: Boolean) {
            for ((path, bytes) in dirs) {
                var cur = path
                while (true) {
                    val n = node(cur)
                    if (isNewer) n.b = (n.b ?: 0L) + bytes else n.a = (n.a ?: 0L) + bytes
                    if (cur == "/") break
                    cur = parentOf(cur)
                }
                val self = node(path)
                if (isNewer) self.selfB = bytes else self.selfA = bytes
            }
        }
        if (older != null) accumulate(older.dirs, false)
        accumulate(newer.dirs, true)
        node("/")
        for (n in nodes.values.toList()) if (n.path != "/") node(parentOf(n.path)).kids.add(n)

        val hot = nodes.values.filter { it.selfDelta != 0L }
            .sortedByDescending { Math.abs(it.selfDelta) }
            .map { HotSpot(it, it.selfDelta) }

        val fa = HashMap<String, Long>(); older?.files?.forEach { fa[it.first] = it.second }
        val fb = HashMap<String, Long>(); newer.files.forEach { fb[it.first] = it.second }
        val files = (fa.keys + fb.keys).map { FileDiff(it, fa[it], fb[it]) }
            .sortedWith(compareByDescending<FileDiff> { it.b ?: 0L }.thenByDescending { Math.abs(it.delta) })

        val ta = HashMap<String, Long>()
        older?.let { o -> o.files.forEach { ta[it.first] = it.second }; o.catFiles.forEach { ta[it.path] = it.size } }
        val tb = HashMap<String, Long>()
        newer.files.forEach { tb[it.first] = it.second }
        newer.catFiles.forEach { tb[it.path] = it.size }
        val allFiles = (ta.keys + tb.keys).map { FileDiff(it, ta[it], tb[it]) }.sortedByDescending { Math.abs(it.delta) }

        val aa = older?.apps?.associateBy { it.pkg } ?: emptyMap()
        val ab = newer.apps.associateBy { it.pkg }
        val apps = (aa.keys + ab.keys).map { pkg ->
            AppDiff(pkg, (ab[pkg] ?: aa[pkg])!!.label, aa[pkg]?.total, ab[pkg]?.total, ab[pkg])
        }.sortedByDescending { it.b ?: 0L }

        return Comparison(older, newer, nodes, node("/"), hot, files, allFiles, apps)
    }
}
