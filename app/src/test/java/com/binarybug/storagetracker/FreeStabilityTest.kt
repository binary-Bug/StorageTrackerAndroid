package com.binarybug.storagetracker

import com.binarybug.storagetracker.data.SnapshotMeta
import com.binarybug.storagetracker.scan.FreeStability
import com.binarybug.storagetracker.scan.FreeStability.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FreeStabilityTest {
    private val hour = 3_600_000L
    private val gb = 1_000_000_000L
    private val now = 1_000 * hour

    private fun m(hoursAgo: Int, freeGb: Double) =
        SnapshotMeta("s$hoursAgo", now - hoursAgo * hour, 128 * gb, (freeGb * gb).toLong(), 0L, 0L, 0L, 0, 0.0, true, 25)

    /** One snapshot every 6 hours for [days] days, free space given by [freeAt] (hours ago -> GB). */
    private fun series(days: Int, freeAt: (Int) -> Double) = (days * 24 downTo 0 step 6).map { m(it, freeAt(it)) }

    @Test
    fun smallWobbleIsStable() {
        val s = FreeStability.of(series(3) { 8.0 + if (it % 12 == 0) 0.05 else -0.05 }, now, 3)!!
        assertEquals(Trend.STABLE, s.trend)
    }

    @Test
    fun aCacheClearThatAndroidRefillsWithinHoursIsStable() {
        // free is 5.0 GB all week; one day a cache clear lifts it to 6.5 GB for a few readings, then it falls back to 5.0 GB
        val s = FreeStability.of(series(7) { h -> if (h in 60..78) 6.5 else 5.0 }, now, 7)!!
        assertEquals(Trend.STABLE, s.trend)
        assertEquals(6.5 * gb, s.maxFree.toDouble(), 1.0) // the spike is still reported as the range, just not as a trend
        assertEquals(5.0 * gb, s.endFree.toDouble(), 1.0)
    }

    @Test
    fun losingHalfAGigabyteADayIsDown() {
        // 3 days, 0.5 GB lost a day: 9.0 GB three days ago, 7.5 GB now
        val s = FreeStability.of(series(3) { 7.5 + 0.5 * it / 24.0 }, now, 3)!!
        assertEquals(Trend.DOWN, s.trend)
        assertEquals(-0.5 * gb, s.perDayBytes, 0.15 * gb)
    }

    @Test
    fun gainingSpaceStaysUpIsUp() {
        val s = FreeStability.of(series(3) { 7.5 - 0.5 * it / 24.0 + 0.0 }, now, 3)!!
        assertEquals(Trend.UP, s.trend)
    }

    @Test
    fun aCleanUpThatSticksIsUp() {
        // free was 4 GB, a big clean-up two days ago lifted it to 8 GB and it stayed there
        val s = FreeStability.of(series(4) { h -> if (h > 48) 4.0 else 8.0 }, now, 4)!!
        assertEquals(Trend.UP, s.trend)
        assertEquals(4.0 * gb, s.netBytes.toDouble(), 0.01 * gb)
    }

    @Test
    fun aStepDownThatSticksIsDown() {
        val s = FreeStability.of(series(4) { h -> if (h > 48) 9.0 else 6.0 }, now, 4)!!
        assertEquals(Trend.DOWN, s.trend)
    }

    @Test
    fun notEnoughHistoryGivesNothing() {
        assertNull(FreeStability.of(listOf(m(10, 5.0), m(5, 5.0), m(0, 5.0)), now, 3)) // 3 snapshots
        assertNull(FreeStability.of(listOf(m(5, 5.0), m(4, 5.0), m(3, 5.0), m(2, 5.0), m(0, 5.0)), now, 3)) // 5 hours only
        assertNotNull(FreeStability.of(series(1) { 5.0 }, now, 3))
        assertNull(FreeStability.of(emptyList(), now, 3))
    }

    @Test
    fun onlySnapshotsInsideTheWindowCount() {
        // before the 1-day window free space fell a lot; inside it everything is flat
        val old = listOf(m(120, 12.0), m(100, 9.0), m(80, 6.0), m(60, 5.0))
        val s = FreeStability.of(old + series(1) { 5.0 }, now, 1)!!
        assertEquals(Trend.STABLE, s.trend)
        assertEquals(5, s.basedOn)
    }
}
