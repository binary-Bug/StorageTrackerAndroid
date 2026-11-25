package com.binarybug.storagetracker

import com.binarybug.storagetracker.data.AppEntry
import com.binarybug.storagetracker.data.CatTotal
import com.binarybug.storagetracker.data.Snapshot
import com.binarybug.storagetracker.data.SnapshotMeta
import com.binarybug.storagetracker.data.SysStats
import com.binarybug.storagetracker.scan.Breakdown
import com.binarybug.storagetracker.scan.Category
import com.binarybug.storagetracker.scan.Compare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BreakdownTest {
    private val meta = SnapshotMeta("x", 0L, 1000L, 200L, 0L, 0L, 0L, 0, 0.0, true, 25)

    @Test
    fun categoryIsDecidedByExtensionIgnoringCase() {
        assertEquals(Category.IMAGES, Category.of("IMG_001.JPG"))
        assertEquals(Category.VIDEOS, Category.of("clip.mp4"))
        assertEquals(Category.APKS, Category.of("app.apk"))
        assertEquals(Category.OTHER, Category.of("README"))
        assertEquals(Category.OTHER, Category.of("trailingdot."))
    }

    @Test
    fun unattributedIsUsedMinusEverythingMeasured() {
        val s = Snapshot(
            meta, emptyList(), emptyList(), emptyList(),
            apps = listOf(AppEntry("a", "A", 100L, 300L, 50L, false)),
            cats = listOf(CatTotal("IMAGES", 50L, 3L)),
            sys = SysStats(1200L, -1, -1, -1, -1, -1, -1, -1, -1, -1),
            appsAvailable = true,
        )
        val b = Breakdown.build(s)
        // used = 800. code 100 + data (300 - 50 cache) 250 + cache 50 + images 50 = 450.
        assertEquals(450L, b.measured)
        assertEquals(350L, b.unattributed)
        assertEquals(200L, b.osPartitions) // advertised 1200 - usable 1000
    }

    @Test
    fun withoutAppsOnlyFileCategoriesAreMeasured() {
        val s = Snapshot(meta, emptyList(), emptyList(), emptyList(), cats = listOf(CatTotal("AUDIO", 120L, 9L)))
        val b = Breakdown.build(s)
        assertEquals(false, b.hasApps)
        assertEquals(120L, b.measured)
        assertEquals(680L, b.unattributed)
    }

    @Test
    fun appGrowthIsComparedByPackage() {
        val a = Snapshot(meta, emptyList(), emptyList(), emptyList(), apps = listOf(AppEntry("p.one", "One", 10L, 90L, 0L, false)))
        val b = Snapshot(
            meta, emptyList(), emptyList(), emptyList(),
            apps = listOf(AppEntry("p.one", "One", 10L, 190L, 0L, false), AppEntry("p.two", "Two", 5L, 5L, 0L, false)),
        )
        val byPkg = Compare.run(a, b).apps.associateBy { it.pkg }
        assertEquals(100L, byPkg["p.one"]!!.delta)
        assertNull(byPkg["p.two"]!!.a)
        assertEquals(10L, byPkg["p.two"]!!.b)
    }
}
