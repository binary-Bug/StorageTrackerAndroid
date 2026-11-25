package com.binarybug.storagetracker

import com.binarybug.storagetracker.data.AppEntry
import com.binarybug.storagetracker.data.CatFile
import com.binarybug.storagetracker.data.CatTotal
import com.binarybug.storagetracker.data.Snapshot
import com.binarybug.storagetracker.data.SnapshotMeta
import com.binarybug.storagetracker.scan.Category
import com.binarybug.storagetracker.scan.CategoryDiffs
import com.binarybug.storagetracker.scan.Compare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryDiffTest {
    private fun meta(free: Long) = SnapshotMeta("x", 0L, 1000L, free, 0L, 0L, 0L, 0, 0.0, true, 25)

    private fun withApps(free: Long, data: Long, images: Long) = Snapshot(
        meta(free), emptyList(), emptyList(), emptyList(),
        apps = listOf(AppEntry("a", "A", 10L, data, 5L, false)),
        cats = listOf(CatTotal("IMAGES", images, 3L)),
        appsAvailable = true,
    )

    @Test
    fun categoryChangesAddUpToTheUsedSpaceChange() {
        val older = withApps(free = 600L, data = 20L, images = 100L) // used 400
        val newer = withApps(free = 250L, data = 70L, images = 300L) // used 750
        val d = CategoryDiffs.between(older, newer)

        assertEquals(350L, d.usedDelta)
        assertNull(d.note)
        assertEquals(listOf("cat:IMAGES", "system", "appdata"), d.rows.take(3).map { it.key })
        assertEquals(200L, d.rows.first { it.key == "cat:IMAGES" }.delta)
        assertEquals(100L, d.rows.first { it.key == "system" }.delta)
        assertEquals(50L, d.rows.first { it.key == "appdata" }.delta)
        assertEquals(d.usedDelta, d.rows.sumOf { it.delta })
    }

    @Test
    fun appRowsAreLeftOutWhenAppsWereNotMeasuredInBothSnapshots() {
        val noApps = Snapshot(meta(600L), emptyList(), emptyList(), emptyList(), cats = listOf(CatTotal("IMAGES", 100L, 3L)))
        val d = CategoryDiffs.between(noApps, withApps(free = 250L, data = 70L, images = 300L))

        assertNotNull(d.note)
        assertTrue(d.rows.any { it.key == "cat:IMAGES" })
        for (k in listOf("apps", "appdata", "cache", "system")) assertFalse("$k should be omitted", d.rows.any { it.key == k })
    }

    @Test
    fun trackedFilesComeFromBothTheTop50AndTheCategoryLists() {
        val older = Snapshot(
            meta(600L), emptyList(), listOf("/a.mp4" to 100L), emptyList(),
            catFiles = listOf(CatFile("VIDEOS", "/a.mp4", 100L), CatFile("IMAGES", "/p.jpg", 50L)),
        )
        val newer = Snapshot(
            meta(500L), emptyList(), listOf("/a.mp4" to 300L), emptyList(),
            catFiles = listOf(CatFile("IMAGES", "/p.jpg", 50L), CatFile("IMAGES", "/new.jpg", 70L)),
        )
        val byPath = Compare.run(older, newer).allFiles.associateBy { it.path }

        assertEquals(200L, byPath.getValue("/a.mp4").delta)
        assertEquals(Category.VIDEOS, byPath.getValue("/a.mp4").category)
        assertNull(byPath.getValue("/new.jpg").a)
        assertEquals(Category.IMAGES, byPath.getValue("/new.jpg").category)
        assertEquals(0L, byPath.getValue("/p.jpg").delta)
        assertEquals("/a.mp4", Compare.run(older, newer).allFiles.first().path) // biggest change first
    }
}
