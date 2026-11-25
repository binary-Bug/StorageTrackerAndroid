package com.binarybug.storagetracker

import com.binarybug.storagetracker.data.SnapshotMeta
import com.binarybug.storagetracker.scan.TrendRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendRangeTest {
    private val hour = 3_600_000L
    private val now = 1_000 * hour

    private fun m(hoursAgo: Long) = SnapshotMeta("s$hoursAgo", now - hoursAgo * hour, 100L, 50L, 0L, 0L, 0L, 0, 0.0, true, 25)

    // one snapshot every 2 hours for 20 days, oldest first
    private val metas = (480L downTo 0L step 2).map { m(it) }

    @Test
    fun lastNTakesTheNewestSnapshotsInOrder() {
        val five = TrendRange.LAST_5.select(metas, now)
        assertEquals(5, five.size)
        assertEquals(listOf("s8", "s6", "s4", "s2", "s0"), five.map { it.id })
        assertEquals(10, TrendRange.LAST_10.select(metas, now).size)
        assertEquals(3, TrendRange.LAST_5.select(metas.takeLast(3), now).size) // fewer than asked: all of them
    }

    @Test
    fun timeWindowsKeepOnlyRecentSnapshots() {
        val day = TrendRange.DAY.select(metas, now)
        assertEquals(13, day.size) // 24h back inclusive, every 2h
        assertTrue(day.all { it.takenMs >= now - 24 * hour })
        val week = TrendRange.WEEK.select(metas, now)
        assertEquals(85, week.size)
        assertEquals("s168", week.first().id)
    }

    @Test
    fun allKeepsEverythingAndAWindowNeverEmptiesTheChart() {
        assertEquals(metas.size, TrendRange.ALL.select(metas, now).size)
        // the newest snapshot is 3 days old: the 1 day window falls back to it instead of showing nothing
        val stale = listOf(m(100), m(80), m(72))
        assertEquals(listOf("s72"), TrendRange.DAY.select(stale, now).map { it.id })
        assertTrue(TrendRange.WEEK.select(emptyList(), now).isEmpty())
    }
}
