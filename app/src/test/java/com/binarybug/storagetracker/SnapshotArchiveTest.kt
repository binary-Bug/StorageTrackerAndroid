package com.binarybug.storagetracker

import com.binarybug.storagetracker.data.SnapshotArchive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotArchiveTest {
    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> for ((n, b) in entries) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return out.toByteArray()
    }

    private fun readAll(zip: ByteArray, max: Long = SnapshotArchive.MAX_ENTRY_BYTES): Pair<Map<String, ByteArray>, Int> {
        val got = LinkedHashMap<String, ByteArray>()
        val ignored = SnapshotArchive.read(ByteArrayInputStream(zip), max) { n, b -> got[n] = b }
        return got to ignored
    }

    @Test
    fun namesMustLookLikeSnapshotFiles() {
        assertTrue(SnapshotArchive.isSnapshotName("snap_20261125_220000.json"))
        for (bad in listOf("snap_x.json", "../snap_20261125_220000.json", "dir/snap_20261125_220000.json", "snap_20261125_220000.json.tmp", "notes.txt", ""))
            assertFalse(bad, SnapshotArchive.isSnapshotName(bad))
        assertEquals("20261125_220000", SnapshotArchive.idOf("snap_20261125_220000.json"))
    }

    @Test
    fun writeThenReadReturnsTheSameFiles() {
        val files = mapOf("snap_20261125_220000.json" to "{\"a\":1}".toByteArray(), "snap_20261124_100000.json" to "{\"b\":2}".toByteArray())
        val out = ByteArrayOutputStream()
        val written = SnapshotArchive.write(files.keys.toList(), { ByteArrayInputStream(files.getValue(it)) }, out)
        assertEquals(2, written)

        val (got, ignored) = readAll(out.toByteArray())
        assertEquals(0, ignored)
        assertEquals(files.keys, got.keys)
        for ((n, b) in files) assertArrayEquals(b, got.getValue(n))
    }

    @Test
    fun writeSkipsNamesThatAreNotSnapshots() {
        val out = ByteArrayOutputStream()
        val written = SnapshotArchive.write(listOf("secrets.txt", "snap_20261125_220000.json"), { ByteArrayInputStream(byteArrayOf(1)) }, out)
        assertEquals(1, written)
        assertEquals(listOf("snap_20261125_220000.json"), readAll(out.toByteArray()).first.keys.toList())
    }

    @Test
    fun readIgnoresForeignEntriesAndPathTricks() {
        val zip = zipOf(
            "../evil.json" to byteArrayOf(1),
            "readme.txt" to byteArrayOf(2),
            "sub/snap_20261125_220000.json" to byteArrayOf(3),
            "snap_20261125_220000.json" to byteArrayOf(4),
        )
        val (got, ignored) = readAll(zip)
        assertEquals(listOf("snap_20261125_220000.json"), got.keys.toList())
        assertEquals(3, ignored)
    }

    @Test
    fun readIgnoresEntriesOverTheSizeLimit() {
        val zip = zipOf("snap_20261125_220000.json" to ByteArray(50), "snap_20261124_100000.json" to ByteArray(5))
        val (got, ignored) = readAll(zip, max = 10)
        assertEquals(listOf("snap_20261124_100000.json"), got.keys.toList())
        assertEquals(1, ignored)
    }

    @Test
    fun anEmptyOrNonZipInputGivesNothing() {
        assertEquals(0 to 0, readAll(zipOf()).let { it.first.size to it.second })
        assertEquals(0, readAll("not a zip at all".toByteArray()).first.size)
    }
}
