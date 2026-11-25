package com.binarybug.storagetracker.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Packs snapshot files into one zip for Export and reads them back for Import. Only well-formed snapshot file names are
 * accepted, so a zip with other content (or names like "../x") can never write anywhere except as a snapshot file.
 */
object SnapshotArchive {
    private val NAME = Regex("^snap_\\d{8}_\\d{6}\\.json$")
    const val MAX_ENTRY_BYTES = 100L * 1024 * 1024
    private const val MAX_ENTRIES = 2000

    fun isSnapshotName(name: String): Boolean = NAME.matches(name)

    /** "snap_20261125_220000.json" -> "20261125_220000" */
    fun idOf(name: String): String = name.removePrefix("snap_").removeSuffix(".json")

    /** Writes the named snapshot files, read through [open], into [out]. Names that are not snapshot names are skipped. Returns how many were written. */
    fun write(names: List<String>, open: (String) -> InputStream, out: OutputStream): Int {
        var written = 0
        ZipOutputStream(out).use { zip ->
            for (name in names) {
                if (!isSnapshotName(name)) continue
                zip.putNextEntry(ZipEntry(name))
                open(name).use { it.copyTo(zip) }
                zip.closeEntry()
                written++
            }
        }
        return written
    }

    /**
     * Calls [onEntry] with each valid snapshot file in the zip. Entries with other names, folders, and entries larger than
     * [maxEntryBytes] are ignored. Returns how many entries were ignored.
     */
    fun read(input: InputStream, maxEntryBytes: Long = MAX_ENTRY_BYTES, onEntry: (String, ByteArray) -> Unit): Int {
        var ignored = 0
        var seen = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++seen > MAX_ENTRIES) { ignored++; break }
                if (entry.isDirectory || !isSnapshotName(entry.name)) { ignored++; continue }
                val data = readLimited(zip, maxEntryBytes)
                if (data == null) ignored++ else onEntry(entry.name, data)
            }
        }
        return ignored
    }

    /** Reads the current entry, or returns null when it is larger than [max]. */
    private fun readLimited(zip: ZipInputStream, max: Long): ByteArray? {
        val buf = ByteArray(8192)
        val out = ByteArrayOutputStream()
        var total = 0L
        while (true) {
            val n = zip.read(buf)
            if (n < 0) break
            total += n
            if (total > max) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
