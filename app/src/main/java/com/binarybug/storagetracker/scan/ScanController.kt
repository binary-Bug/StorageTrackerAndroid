package com.binarybug.storagetracker.scan

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.binarybug.storagetracker.data.CatFile
import com.binarybug.storagetracker.data.CatTotal
import com.binarybug.storagetracker.data.Prefs
import com.binarybug.storagetracker.data.Snapshot
import com.binarybug.storagetracker.data.SnapshotMeta
import com.binarybug.storagetracker.data.SnapshotStore
import com.binarybug.storagetracker.BYTES_PER_GB
import com.binarybug.storagetracker.formatBytes
import com.binarybug.storagetracker.readPrimaryVolumeUsage
import com.binarybug.storagetracker.work.Notifier
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Runs at most one scan at a time. Both the "Scan now" button and the scheduled job go through here, so they can
 * never overlap. The scan runs on its own thread with a large stack because the folder walk is recursive.
 */
object ScanController {
    fun interface Listener { fun onScanState() }

    private const val MIN_FOLDER_MB = 25
    private const val TOP_FILES = 50
    private const val TOP_PER_CATEGORY = 30
    private val main = Handler(Looper.getMainLooper())
    val listeners = CopyOnWriteArrayList<Listener>()

    @Volatile var running = false
        private set
    @Volatile var progress: ScanProgress? = null
        private set
    @Volatile private var cancelFlag = false

    /** When the running scan started, for a banner clock that keeps ticking even while no progress events arrive. */
    @Volatile var startedMs = 0L
        private set
    @Volatile var measuringApps = false
        private set

    fun cancel() { cancelFlag = true }

    /** Returns false (and does nothing) when a scan is already running. [onDone] is called on the scan thread. */
    fun start(context: Context, trigger: String, onDone: (() -> Unit)? = null): Boolean {
        synchronized(this) {
            if (running) return false
            running = true
            cancelFlag = false
            startedMs = System.currentTimeMillis()
            measuringApps = false
        }
        val app = context.applicationContext
        notifyState()
        Thread(null, { runScan(app, trigger); onDone?.invoke() }, "storage-scan", 64L * 1024 * 1024).start()
        return true
    }

    /** Free-space drop, low-space notice and growth alerts for a scan that was just saved. [before] is the previous snapshot's header. */
    private fun alerts(c: Context, before: SnapshotMeta?, snap: Snapshot) {
        val meta = snap.meta
        val dropLimit = (Prefs.alertDropGb(c) * BYTES_PER_GB).toLong()
        if (before != null && dropLimit > 0 && before.free - meta.free >= dropLimit) Notifier.freeSpaceDrop(c, before.free - meta.free, meta.free)

        val now = System.currentTimeMillis()
        if (Prefs.lowSpaceNotice(c) && AlertRules.shouldWarnLow(meta.total, meta.free, Prefs.lastLowNoticeMs(c), now)) {
            Notifier.lowSpace(c, meta.free, meta.total)
            Prefs.setLastLowNoticeMs(c, now)
        }

        val growthLimit = (Prefs.growthAlertGb(c) * BYTES_PER_GB).toLong()
        if (before != null && growthLimit > 0) {
            val older = SnapshotStore.load(c, before.id)
            if (older != null) Notifier.growth(c, AlertRules.growth(Compare.run(older, snap), growthLimit))
        }
    }

    private fun notifyState() { main.post { listeners.forEach { it.onScanState() } } }

    private fun runScan(c: Context, trigger: String) {
        val startMs = System.currentTimeMillis()
        var status = "ok"
        var summary = ""
        try {
            val root = Environment.getExternalStorageDirectory().path
            val out = Scanner(root, MIN_FOLDER_MB * 1024L * 1024L, TOP_FILES, TOP_PER_CATEGORY, { cancelFlag }) {
                progress = it
                notifyState()
            }.run()
            measuringApps = true
            notifyState()
            val appStats = AppStorage.collect(c) { cancelFlag }
            if (cancelFlag) throw CancellationException() // stop pressed while apps were being measured: save nothing
            val endMs = System.currentTimeMillis()
            val usage = readPrimaryVolumeUsage()
            val meta = SnapshotMeta(
                SnapshotStore.newId(endMs), endMs, usage.totalBytes, usage.freeBytes, out.bytes,
                out.dirsScanned, out.filesScanned, out.skipped, out.elapsedMs / 1000.0,
                Environment.isExternalStorageManager(), MIN_FOLDER_MB,
                if (appStats.available) appStats.apps.sumOf { it.total } else -1L,
            )
            val before = SnapshotStore.listMeta(c).lastOrNull()
            val cats = Category.entries.map { CatTotal(it.name, out.catBytes[it.ordinal], out.catCount[it.ordinal]) }
            val catFiles = out.catTop.map { CatFile(it.first.name, it.second, it.third) }
            val snap = Snapshot(meta, out.dirs, out.files, out.skippedSamples, appStats.apps, cats, catFiles, appStats.sys, appStats.available)
            SnapshotStore.save(c, snap)
            SnapshotStore.prune(c, Prefs.retentionDays(c))
            summary = "${out.filesScanned} files, ${formatBytes(out.bytes)} in ${out.elapsedMs / 1000}s, ${out.skipped} folders skipped, " +
                if (appStats.available) "${appStats.apps.size} apps measured" else "apps not measured (needs Usage access)"
            runCatching { alerts(c, before, snap) } // a notification problem must never turn a good scan into an error

        } catch (e: CancellationException) {
            status = "cancelled"
            summary = "Stopped before finishing; nothing was saved."
        } catch (e: Throwable) {
            status = "error"
            summary = (e.message ?: e.javaClass.simpleName).take(200)
        } finally {
            Prefs.recordRun(c, startMs, System.currentTimeMillis(), trigger, status, summary)
            running = false
            progress = null
            notifyState()
        }
    }
}
