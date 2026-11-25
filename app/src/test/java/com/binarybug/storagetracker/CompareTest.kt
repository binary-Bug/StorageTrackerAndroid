package com.binarybug.storagetracker

import com.binarybug.storagetracker.data.Snapshot
import com.binarybug.storagetracker.data.SnapshotMeta
import com.binarybug.storagetracker.scan.Compare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompareTest {
    private fun snap(id: String, dirs: List<Pair<String, Long>>, files: List<Pair<String, Long>> = emptyList()) =
        Snapshot(SnapshotMeta(id, 0L, 1000L, 500L, 0L, 0L, 0L, 0, 0.0, true, 25), dirs, files, emptyList())

    @Test
    fun ownBytesRollUpToAncestors() {
        val c = Compare.run(null, snap("b", listOf("/" to 10L, "/DCIM" to 100L, "/DCIM/Camera" to 400L)))
        assertEquals(510L, c.root.b)
        assertEquals(500L, c.nodes["/DCIM"]!!.b)
        assertEquals(400L, c.nodes["/DCIM/Camera"]!!.b)
    }

    @Test
    fun directChangeIsNotDoubleCountedButFolderTotalsStillChange() {
        val a = snap("a", listOf("/" to 10L, "/DCIM" to 100L, "/DCIM/Camera" to 400L))
        val b = snap("b", listOf("/" to 10L, "/DCIM" to 100L, "/DCIM/Camera" to 900L))
        val c = Compare.run(a, b)
        assertEquals(1, c.hot.size)
        assertEquals("/DCIM/Camera", c.hot[0].node.path)
        assertEquals(500L, c.hot[0].self)
        assertEquals(500L, c.nodes["/DCIM"]!!.delta)
        assertEquals(500L, c.root.delta)
    }

    @Test
    fun folderMissingFromOlderSnapshotIsNew() {
        val a = snap("a", listOf("/" to 10L))
        val b = snap("b", listOf("/" to 10L, "/Movies" to 700L))
        val n = Compare.run(a, b).nodes["/Movies"]!!
        assertNull(n.a)
        assertEquals(700L, n.b)
    }

    @Test
    fun hotSpotsAreSortedByAbsoluteChange() {
        val a = snap("a", listOf("/" to 0L, "/A" to 300L, "/B" to 300L))
        val b = snap("b", listOf("/" to 0L, "/A" to 350L, "/B" to 100L))
        val paths = Compare.run(a, b).hot.map { it.node.path }
        assertEquals(listOf("/B", "/A"), paths)
    }

    @Test
    fun parentOfHandlesRootAndNesting() {
        assertEquals("/", Compare.parentOf("/DCIM"))
        assertEquals("/DCIM", Compare.parentOf("/DCIM/Camera"))
        assertEquals("/", Compare.parentOf("/"))
    }

    @Test
    fun largeFilesAreMatchedAcrossSnapshots() {
        val a = snap("a", listOf("/" to 0L), listOf("/x.bin" to 100L, "/gone.bin" to 50L))
        val b = snap("b", listOf("/" to 0L), listOf("/x.bin" to 160L, "/new.bin" to 70L))
        val byPath = Compare.run(a, b).files.associateBy { it.path }
        assertEquals(60L, byPath["/x.bin"]!!.delta)
        assertNull(byPath["/new.bin"]!!.a)
        assertNull(byPath["/gone.bin"]!!.b)
    }
}
