package com.binarybug.storagetracker.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonWriter
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Header of a snapshot: everything except the (large) folder and file lists. */
class SnapshotMeta(
    val id: String,
    val takenMs: Long,
    val total: Long,
    val free: Long,
    val scanned: Long,
    val dirsScanned: Long,
    val filesScanned: Long,
    val skipped: Int,
    val scanSec: Double,
    val allFiles: Boolean,
    val minFolderMb: Int,
    /** Total size (code + data) of all measured apps at scan time, or -1 when apps were not measured (no Usage access). */
    val appsTotal: Long = -1L,
) {
    val used: Long get() = total - free

    /** Used space that neither the file scan nor the app statistics explain; null when apps were not measured. */
    val unattributed: Long? get() = if (appsTotal >= 0) (used - appsTotal - scanned).coerceAtLeast(0L) else null
}

/** One installed app's storage, from Android's storage statistics. [dataBytes] includes [cacheBytes]. */
class AppEntry(
    val pkg: String,
    val label: String,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val system: Boolean,
) {
    val total: Long get() = appBytes + dataBytes
}

/** Device-level numbers from StorageStatsManager. -1 means "not available". */
class SysStats(
    val marketingTotal: Long,
    val apiFree: Long,
    val userApp: Long,
    val userData: Long,
    val userCache: Long,
    val extTotal: Long,
    val extAudio: Long,
    val extVideo: Long,
    val extImage: Long,
    val extApp: Long,
)

class CatTotal(val name: String, val bytes: Long, val count: Long)
class CatFile(val cat: String, val path: String, val size: Long)

/**
 * One scan result. [dirs] holds (path, own bytes): files directly in the folder plus sub-folders smaller than
 * [SnapshotMeta.minFolderMb], so ancestors are never double counted. Paths are relative to shared storage, "/" is the root.
 */
class Snapshot(
    val meta: SnapshotMeta,
    val dirs: List<Pair<String, Long>>,
    val files: List<Pair<String, Long>>,
    val skippedSamples: List<String>,
    val apps: List<AppEntry> = emptyList(),
    val cats: List<CatTotal> = emptyList(),
    val catFiles: List<CatFile> = emptyList(),
    val sys: SysStats? = null,
    val appsAvailable: Boolean = false,
)

/** Outcome of an import: new snapshots saved, ones already on the phone, and files that were not valid snapshots. */
class ImportResult(val added: Int, val skipped: Int, val invalid: Int)

/** Snapshots are plain JSON files in the app's private storage, one file per scan. */
object SnapshotStore {
    private const val VERSION = 2L
    private val metaCache = HashMap<String, Pair<Long, SnapshotMeta>>()

    private fun dir(c: Context): File = File(c.filesDir, "snapshots").also { it.mkdirs() }

    fun newId(ms: Long): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(ms))

    @Synchronized
    fun save(c: Context, s: Snapshot) {
        val target = File(dir(c), "snap_${s.meta.id}.json")
        val tmp = File(dir(c), "snap_${s.meta.id}.tmp")
        tmp.bufferedWriter(Charsets.UTF_8).use { w ->
            val j = JsonWriter(w)
            val m = s.meta
            j.beginObject()
            j.name("v").value(VERSION)
            j.name("id").value(m.id)
            j.name("takenMs").value(m.takenMs)
            j.name("total").value(m.total)
            j.name("free").value(m.free)
            j.name("scanned").value(m.scanned)
            j.name("dirsScanned").value(m.dirsScanned)
            j.name("filesScanned").value(m.filesScanned)
            j.name("skipped").value(m.skipped.toLong())
            j.name("scanSec").value(m.scanSec)
            j.name("allFiles").value(m.allFiles)
            j.name("minFolderMb").value(m.minFolderMb.toLong())
            j.name("appsTotal").value(m.appsTotal)
            j.name("appsAvailable").value(s.appsAvailable)
            s.sys?.let {
                j.name("sys").beginObject()
                j.name("marketingTotal").value(it.marketingTotal); j.name("apiFree").value(it.apiFree)
                j.name("userApp").value(it.userApp); j.name("userData").value(it.userData); j.name("userCache").value(it.userCache)
                j.name("extTotal").value(it.extTotal); j.name("extAudio").value(it.extAudio)
                j.name("extVideo").value(it.extVideo); j.name("extImage").value(it.extImage); j.name("extApp").value(it.extApp)
                j.endObject()
            }
            j.name("dirs").beginArray()
            for ((p, b) in s.dirs) { j.beginArray(); j.value(p); j.value(b); j.endArray() }
            j.endArray()
            j.name("files").beginArray()
            for ((p, b) in s.files) { j.beginArray(); j.value(p); j.value(b); j.endArray() }
            j.endArray()
            j.name("skippedSamples").beginArray()
            for (x in s.skippedSamples) j.value(x)
            j.endArray()
            j.name("cats").beginArray()
            for (c2 in s.cats) { j.beginArray(); j.value(c2.name); j.value(c2.bytes); j.value(c2.count); j.endArray() }
            j.endArray()
            j.name("catFiles").beginArray()
            for (f in s.catFiles) { j.beginArray(); j.value(f.cat); j.value(f.path); j.value(f.size); j.endArray() }
            j.endArray()
            j.name("apps").beginArray()
            for (a in s.apps) {
                j.beginArray(); j.value(a.pkg); j.value(a.label); j.value(a.appBytes); j.value(a.dataBytes); j.value(a.cacheBytes); j.value(a.system); j.endArray()
            }
            j.endArray()
            j.endObject()
            j.flush()
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        metaCache.remove(target.name)
    }

    /** Removes one snapshot file. Returns false when it did not exist. */
    @Synchronized
    fun delete(c: Context, id: String): Boolean {
        val f = File(dir(c), "snap_$id.json")
        metaCache.remove(f.name)
        return f.delete()
    }

    /** Writes every snapshot into one zip. Returns how many snapshots were written. */
    @Synchronized
    fun exportTo(c: Context, out: OutputStream): Int {
        val names = dir(c).listFiles { f -> SnapshotArchive.isSnapshotName(f.name) }?.map { it.name }?.sorted() ?: emptyList()
        return SnapshotArchive.write(names, { File(dir(c), it).inputStream() }, out)
    }

    /**
     * Adds snapshots from a zip made by [exportTo]. A snapshot that already exists is kept as it is, and a file that is not a
     * complete, readable snapshot of the name it claims is counted as invalid and not saved.
     */
    @Synchronized
    fun importFrom(c: Context, input: InputStream): ImportResult {
        var added = 0
        var skipped = 0
        var invalid = 0
        val ignored = SnapshotArchive.read(input) { name, bytes ->
            val target = File(dir(c), name)
            if (target.exists()) {
                skipped++
            } else {
                val tmp = File(dir(c), "import_$name.tmp")
                tmp.writeBytes(bytes)
                val snap = parse(tmp, full = true)
                if (snap != null && snap.meta.id == SnapshotArchive.idOf(name) && tmp.renameTo(target)) added++
                else { tmp.delete(); invalid++ }
            }
        }
        metaCache.clear()
        return ImportResult(added, skipped, invalid + ignored)
    }

    /** Snapshot headers, oldest first. Only the header of each file is parsed, and results are cached. */
    @Synchronized
    fun listMeta(c: Context): List<SnapshotMeta> {
        val files = dir(c).listFiles { f -> f.name.startsWith("snap_") && f.name.endsWith(".json") } ?: return emptyList()
        val out = ArrayList<SnapshotMeta>()
        for (f in files) {
            val cached = metaCache[f.name]
            val meta = if (cached != null && cached.first == f.lastModified()) cached.second
            else parse(f, full = false)?.meta?.also { metaCache[f.name] = f.lastModified() to it }
            if (meta != null) out.add(meta)
        }
        return out.sortedBy { it.takenMs }
    }

    fun load(c: Context, id: String): Snapshot? = parse(File(dir(c), "snap_$id.json"), full = true)

    /** Deletes snapshots older than [retentionDays], but always keeps the newest two so a comparison is possible. */
    @Synchronized
    fun prune(c: Context, retentionDays: Int): Int {
        val all = listMeta(c)
        val keep = all.takeLast(2).map { it.id }.toSet()
        val cutoff = System.currentTimeMillis() - retentionDays * 86_400_000L
        var deleted = 0
        for (m in all) {
            if (m.takenMs < cutoff && m.id !in keep && File(dir(c), "snap_${m.id}.json").delete()) deleted++
        }
        return deleted
    }

    private fun parse(file: File, full: Boolean): Snapshot? = try {
        JsonReader(file.bufferedReader(Charsets.UTF_8)).use { r ->
            var id = ""; var takenMs = 0L; var total = 0L; var free = 0L; var scanned = 0L
            var dirsScanned = 0L; var filesScanned = 0L; var skipped = 0; var scanSec = 0.0
            var allFiles = false; var minFolderMb = 25; var appsAvailable = false; var appsTotal = -1L
            var sys: SysStats? = null
            val dirs = ArrayList<Pair<String, Long>>()
            val files = ArrayList<Pair<String, Long>>()
            val samples = ArrayList<String>()
            val cats = ArrayList<CatTotal>()
            val catFiles = ArrayList<CatFile>()
            val apps = ArrayList<AppEntry>()
            r.beginObject()
            loop@ while (r.hasNext()) {
                when (r.nextName()) {
                    "id" -> id = r.nextString()
                    "takenMs" -> takenMs = r.nextLong()
                    "total" -> total = r.nextLong()
                    "free" -> free = r.nextLong()
                    "scanned" -> scanned = r.nextLong()
                    "dirsScanned" -> dirsScanned = r.nextLong()
                    "filesScanned" -> filesScanned = r.nextLong()
                    "skipped" -> skipped = r.nextInt()
                    "scanSec" -> scanSec = r.nextDouble()
                    "allFiles" -> allFiles = r.nextBoolean()
                    "minFolderMb" -> minFolderMb = r.nextInt()
                    "appsTotal" -> appsTotal = r.nextLong()
                    "appsAvailable" -> appsAvailable = r.nextBoolean()
                    "sys" -> {
                        val m = HashMap<String, Long>()
                        r.beginObject()
                        while (r.hasNext()) m[r.nextName()] = r.nextLong()
                        r.endObject()
                        fun g(k: String) = m[k] ?: -1L
                        sys = SysStats(g("marketingTotal"), g("apiFree"), g("userApp"), g("userData"), g("userCache"), g("extTotal"), g("extAudio"), g("extVideo"), g("extImage"), g("extApp"))
                    }
                    "dirs" -> {
                        if (!full) break@loop
                        r.beginArray()
                        while (r.hasNext()) { r.beginArray(); val p = r.nextString(); val b = r.nextLong(); r.endArray(); dirs.add(p to b) }
                        r.endArray()
                    }
                    "files" -> {
                        if (!full) break@loop
                        r.beginArray()
                        while (r.hasNext()) { r.beginArray(); val p = r.nextString(); val b = r.nextLong(); r.endArray(); files.add(p to b) }
                        r.endArray()
                    }
                    "skippedSamples" -> {
                        if (!full) break@loop
                        r.beginArray()
                        while (r.hasNext()) samples.add(r.nextString())
                        r.endArray()
                    }
                    "cats" -> {
                        if (!full) break@loop
                        r.beginArray()
                        while (r.hasNext()) { r.beginArray(); cats.add(CatTotal(r.nextString(), r.nextLong(), r.nextLong())); r.endArray() }
                        r.endArray()
                    }
                    "catFiles" -> {
                        if (!full) break@loop
                        r.beginArray()
                        while (r.hasNext()) { r.beginArray(); catFiles.add(CatFile(r.nextString(), r.nextString(), r.nextLong())); r.endArray() }
                        r.endArray()
                    }
                    "apps" -> {
                        if (!full) break@loop
                        r.beginArray()
                        while (r.hasNext()) {
                            r.beginArray()
                            apps.add(AppEntry(r.nextString(), r.nextString(), r.nextLong(), r.nextLong(), r.nextLong(), r.nextBoolean()))
                            r.endArray()
                        }
                        r.endArray()
                    }
                    else -> r.skipValue()
                }
            }
            Snapshot(
                SnapshotMeta(id, takenMs, total, free, scanned, dirsScanned, filesScanned, skipped, scanSec, allFiles, minFolderMb, appsTotal),
                dirs, files, samples, apps, cats, catFiles, sys, appsAvailable,
            )
        }
    } catch (e: Exception) {
        null
    }
}
