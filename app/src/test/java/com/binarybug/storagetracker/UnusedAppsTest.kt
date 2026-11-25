package com.binarybug.storagetracker

import com.binarybug.storagetracker.scan.UnusedApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnusedAppsTest {
    private val day = 86_400_000L
    private val now = 1_000 * day

    @Test
    fun countsDaysFromTheLastOpen() {
        val i = UnusedApps.idle(now - 45 * day, now - 300 * day, now)!!
        assertEquals(45L, i.days)
        assertTrue(i.everOpened)
        assertTrue(UnusedApps.isUnused(i))
        assertEquals("last opened 45 days ago", UnusedApps.describe(i))
    }

    @Test
    fun aRecentlyInstalledAppIsNeverIdle() {
        // last used long ago, but reinstalled 3 days ago
        val i = UnusedApps.idle(now - 200 * day, now - 3 * day, now)!!
        assertEquals(3L, i.days)
        assertFalse(UnusedApps.isUnused(i))
    }

    @Test
    fun neverOpenedCountsFromInstall() {
        val i = UnusedApps.idle(0L, now - 100 * day, now)!!
        assertEquals(100L, i.days)
        assertFalse(i.everOpened)
        assertTrue(UnusedApps.isUnused(i))
        assertEquals("never opened in the last year (installed 100 days ago)", UnusedApps.describe(i))
    }

    @Test
    fun thresholdIsInclusiveAndUnknownIsNotUnused() {
        assertTrue(UnusedApps.isUnused(UnusedApps.idle(now - 30 * day, 0L, now)))
        assertFalse(UnusedApps.isUnused(UnusedApps.idle(now - 29 * day, 0L, now)))
        assertNull(UnusedApps.idle(0L, 0L, now))
        assertFalse(UnusedApps.isUnused(null))
        assertEquals("no usage record", UnusedApps.describe(null))
    }
}
