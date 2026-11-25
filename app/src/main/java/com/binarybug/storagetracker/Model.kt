package com.binarybug.storagetracker

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.binarybug.storagetracker.data.SnapshotMeta
import com.binarybug.storagetracker.data.SnapshotStore
import com.binarybug.storagetracker.scan.Compare
import com.binarybug.storagetracker.scan.Comparison
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/** Screen-independent state: the snapshot list and the comparison between the selected pair. Survives rotation. */
object Model {
    var metas: List<SnapshotMeta> = emptyList()
        private set
    var fromId: String? = null
        private set
    var toId: String? = null
        private set
    var comparison: Comparison? = null
        private set
    var loaded = false
        private set
    var loading = false
        private set

    val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private fun fire() { listeners.forEach { it() } }

    /** Re-reads snapshots from disk and selects previous -> latest. */
    fun reload(c: Context) {
        val app = c.applicationContext
        loading = true
        fire()
        worker.execute {
            val list = SnapshotStore.listMeta(app)
            val to = list.lastOrNull()?.id
            val from = if (list.size >= 2) list[list.size - 2].id else null
            val cmp = build(app, from, to)
            main.post { metas = list; fromId = from; toId = to; comparison = cmp; loaded = true; loading = false; fire() }
        }
    }

    /** Compare two chosen snapshots. */
    fun select(c: Context, from: String?, to: String?) {
        val app = c.applicationContext
        loading = true
        fire()
        worker.execute {
            val cmp = build(app, from, to)
            main.post { fromId = from; toId = to; comparison = cmp; loading = false; fire() }
        }
    }

    private fun build(c: Context, from: String?, to: String?): Comparison? {
        val newer = to?.let { SnapshotStore.load(c, it) } ?: return null
        val older = from?.let { SnapshotStore.load(c, it) }
        return Compare.run(older, newer)
    }
}
