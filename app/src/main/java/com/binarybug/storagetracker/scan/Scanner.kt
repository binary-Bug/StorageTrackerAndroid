package com.binarybug.storagetracker.scan

import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.util.PriorityQueue
import java.util.concurrent.CancellationException

class ScanProgress(val dirs: Long, val files: Long, val bytes: Long, val skipped: Int, val elapsedMs: Long)

class ScanOutput(
    val dirs: List<Pair<String, Long>>,
    val files: List<Pair<String, Long>>,
    val catBytes: LongArray,
    val catCount: LongArray,
    val catTop: List<Triple<Category, String, Long>>,
    val dirsScanned: Long,
    val filesScanned: Long,
    val bytes: Long,
    val skipped: Int,
    val skippedSamples: List<String>,
    val elapsedMs: Long,
)

/**
 * Walks shared storage once and records, like the Windows scanner, a pruned folder tree plus the largest files,
 * and additionally totals every file by type (photos, videos, audio, ...) with the biggest files of each type.
 * Uses lstat so symbolic links are never followed, and counts folders it cannot read instead of failing.
 */
class Scanner(
    private val rootPath: String,
    private val minFolderBytes: Long,
    private val topFiles: Int,
    private val topPerCategory: Int,
    private val cancelled: () -> Boolean,
    private val onProgress: (ScanProgress) -> Unit,
) {
    private val minFileBytes = 1L shl 20
    private val dirs = ArrayList<Pair<String, Long>>()
    private val heap = PriorityQueue<Pair<Long, String>>(compareBy { it.first })
    private val catHeaps = Array(Category.entries.size) { PriorityQueue<Pair<Long, String>>(compareBy { it.first }) }
    private val catBytes = LongArray(Category.entries.size)
    private val catCount = LongArray(Category.entries.size)
    private val skippedSamples = ArrayList<String>()
    private var dirsCount = 0L
    private var filesCount = 0L
    private var bytesCount = 0L
    private var skipped = 0
    private var lastProgress = 0L
    private val started = SystemClock.elapsedRealtime()

    fun run(): ScanOutput {
        scan(rootPath, "")
        val files = heap.sortedByDescending { it.first }.map { it.second to it.first }
        val top = ArrayList<Triple<Category, String, Long>>()
        for (c in Category.entries) catHeaps[c.ordinal].sortedByDescending { it.first }.forEach { top.add(Triple(c, it.second, it.first)) }
        return ScanOutput(
            dirs, files, catBytes, catCount, top, dirsCount, filesCount, bytesCount, skipped, skippedSamples,
            SystemClock.elapsedRealtime() - started,
        )
    }

    /** Returns the total bytes under [abs]. A folder is recorded when it is at least [minFolderBytes] (or is the root). */
    private fun scan(abs: String, rel: String): Long {
        if (cancelled()) throw CancellationException()
        val names = File(abs).list()
        if (names == null) {
            skip(rel.ifEmpty { "/" }, "cannot be read")
            return 0L
        }
        dirsCount++
        tick()
        var total = 0L
        var keptChildren = 0L
        for (name in names) {
            val childAbs = "$abs/$name"
            val st = try {
                Os.lstat(childAbs)
            } catch (e: ErrnoException) {
                continue
            }
            val mode = st.st_mode
            if (OsConstants.S_ISLNK(mode)) continue
            if (OsConstants.S_ISDIR(mode)) {
                val childTotal = scan(childAbs, "$rel/$name")
                total += childTotal
                if (childTotal >= minFolderBytes) keptChildren += childTotal
            } else if (OsConstants.S_ISREG(mode)) {
                val size = st.st_size
                filesCount++
                total += size
                bytesCount += size
                val cat = Category.of(name)
                catBytes[cat.ordinal] += size
                catCount[cat.ordinal]++
                if (size >= minFileBytes) {
                    val path = "$rel/$name"
                    consider(heap, topFiles, size, path)
                    consider(catHeaps[cat.ordinal], topPerCategory, size, path)
                }
            }
        }
        if (total >= minFolderBytes || rel.isEmpty()) dirs.add((rel.ifEmpty { "/" }) to (total - keptChildren))
        return total
    }

    private fun consider(h: PriorityQueue<Pair<Long, String>>, limit: Int, size: Long, path: String) {
        if (h.size < limit) h.add(size to path)
        else if (size > h.peek()!!.first) { h.poll(); h.add(size to path) }
    }

    private fun skip(path: String, why: String) {
        skipped++
        if (skippedSamples.size < 50) skippedSamples.add("$path: $why")
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastProgress >= 400) {
            lastProgress = now
            onProgress(ScanProgress(dirsCount, filesCount, bytesCount, skipped, now - started))
        }
    }
}
