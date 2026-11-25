package com.binarybug.storagetracker

import com.binarybug.storagetracker.data.SnapshotMeta
import com.binarybug.storagetracker.scan.UnattributedTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnattributedTrendTest {
    private val gb = 1_000_000_000L

    /** A snapshot with [used] bytes in use, [scanned] bytes of files and [apps] bytes of apps (-1 = not measured). */
    private fun m(t: Long, used: Long, scanned: Long, apps: Long) = SnapshotMeta("s$t", t, 100 * gb, 100 * gb - used, scanned, 0L, 0L, 0, 0.0, true, 25, apps)

    @Test
    fun skipsSnapshotsWhereAppsWereNotMeasured() {
        val t = UnattributedTrend.of(listOf(m(1, 50 * gb, 10 * gb, -1), m(2, 52 * gb, 10 * gb, 20 * gb)))
        assertEquals(1, t.points.size)
        assertEquals(22 * gb, t.points[0].bytes)
        assertNull(t.jumpFrom)
        assertFalse(t.hasJump)
    }

    @Test
    fun reportsTheRiseBetweenTheLastTwoMeasuredSnapshots() {
        val t = UnattributedTrend.of(listOf(m(1, 50 * gb, 10 * gb, 20 * gb), m(2, 51 * gb, 10 * gb, 20 * gb), m(3, 55 * gb, 10 * gb, 20 * gb)))
        assertEquals(3, t.points.size)
        assertEquals(4 * gb, t.jumpBytes)
        assertEquals(2L, t.jumpFrom?.t)
        assertEquals(3L, t.jumpTo?.t)
        assertTrue(t.hasJump)
    }

    @Test
    fun smallRisesAndFallsAreNotWarnings() {
        val rise = UnattributedTrend.of(listOf(m(1, 50 * gb, 10 * gb, 20 * gb), m(2, 50 * gb + 400_000_000L, 10 * gb, 20 * gb)))
        assertFalse(rise.hasJump)
        val fall = UnattributedTrend.of(listOf(m(1, 50 * gb, 10 * gb, 20 * gb), m(2, 45 * gb, 10 * gb, 20 * gb)))
        assertEquals(-5 * gb, fall.jumpBytes)
        assertFalse(fall.hasJump)
    }

    @Test
    fun noSnapshotsGivesAnEmptyTrend() {
        val t = UnattributedTrend.of(emptyList())
        assertTrue(t.points.isEmpty())
        assertFalse(t.hasJump)
    }
}
