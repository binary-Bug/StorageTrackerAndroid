package com.binarybug.storagetracker

import com.binarybug.storagetracker.data.SnapshotMeta
import com.binarybug.storagetracker.scan.SnapshotPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnapshotPickerTest {
    private fun m(id: String, t: Long) = SnapshotMeta(id, t, 1000L, 200L, 100L, 0L, 0L, 0, 0.0, true, 25)
    private val metas = listOf(m("1", 10L), m("2", 20L), m("3", 30L)) // oldest first

    @Test
    fun previousIsTheSnapshotBefore() {
        assertEquals("2", SnapshotPicker.previousOf(metas, "3")?.id)
        assertNull(SnapshotPicker.previousOf(metas, "1"))
        assertNull(SnapshotPicker.previousOf(metas, "missing"))
    }

    @Test
    fun useAsFromKeepsANewerToOtherwiseTakesTheLatest() {
        assertEquals(Pair<String?, String?>("2", "3"), SnapshotPicker.withFrom(metas, "2", "3"))
        assertEquals(Pair<String?, String?>("1", "2"), SnapshotPicker.withFrom(metas, "1", "2"))
        assertEquals(Pair<String?, String?>("2", "3"), SnapshotPicker.withFrom(metas, "2", null))
        // the chosen snapshot is newer than the current "to": fall back to the latest, which is newer than it
        assertEquals(Pair<String?, String?>("2", "3"), SnapshotPicker.withFrom(metas, "2", "1"))
        // the latest snapshot cannot be "from" of anything newer
        assertEquals(Pair<String?, String?>("3", null), SnapshotPicker.withFrom(metas, "3", "2"))
    }

    @Test
    fun useAsToKeepsAnOlderFromOtherwiseTakesThePreviousSnapshot() {
        assertEquals(Pair<String?, String?>("1", "3"), SnapshotPicker.withTo(metas, "3", "1"))
        assertEquals(Pair<String?, String?>("1", "2"), SnapshotPicker.withTo(metas, "2", null))
        // the current "from" is newer than the chosen "to": use the snapshot before it instead
        assertEquals(Pair<String?, String?>("1", "2"), SnapshotPicker.withTo(metas, "2", "3"))
        // the oldest snapshot has nothing before it
        assertEquals(Pair<String?, String?>(null, "1"), SnapshotPicker.withTo(metas, "1", "2"))
    }

    @Test
    fun unattributedIsUsedMinusAppsMinusScannedFilesAndOnlyWhenAppsWereMeasured() {
        // used = 1000 - 200 = 800, files 100, apps 300 -> 400 unexplained
        assertEquals(400L, SnapshotMeta("x", 0L, 1000L, 200L, 100L, 0L, 0L, 0, 0.0, true, 25, 300L).unattributed)
        assertNull(SnapshotMeta("x", 0L, 1000L, 200L, 100L, 0L, 0L, 0, 0.0, true, 25).unattributed)
        assertEquals(0L, SnapshotMeta("x", 0L, 1000L, 200L, 100L, 0L, 0L, 0, 0.0, true, 25, 900L).unattributed)
    }
}
