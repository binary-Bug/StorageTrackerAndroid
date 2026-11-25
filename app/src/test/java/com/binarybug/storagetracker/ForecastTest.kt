package com.binarybug.storagetracker

import com.binarybug.storagetracker.data.SnapshotMeta
import com.binarybug.storagetracker.scan.Forecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastTest {
    private val day = 86_400_000L
    private val gb = 1_000_000_000L
    private val now = 100 * day

    private fun m(daysAgo: Double, freeGb: Double) =
        SnapshotMeta("s$daysAgo", now - (daysAgo * day).toLong(), 128 * gb, (freeGb * gb).toLong(), 0L, 0L, 0L, 0, 0.0, true, 25)

    @Test
    fun shrinkingFreeSpaceGivesDaysLeft() {
        // loses 1 GB a day, 8 GB free now -> 8 days
        val f = Forecast.of(listOf(m(3.0, 11.0), m(2.0, 10.0), m(1.0, 9.0), m(0.0, 8.0)), now)!!
        assertEquals(-1.0 * gb, f.perDayBytes, 1e6)
        assertEquals(8.0, f.daysLeft!!, 0.01)
        assertEquals(4, f.basedOn)
        assertTrue(f.warns)
    }

    @Test
    fun growingOrFlatFreeSpaceHasNoEstimate() {
        val grow = Forecast.of(listOf(m(2.0, 10.0), m(1.0, 11.0), m(0.0, 12.0)), now)!!
        assertNull(grow.daysLeft)
        assertFalse(grow.warns)
        val flat = Forecast.of(listOf(m(2.0, 10.0), m(1.0, 10.0), m(0.0, 10.0)), now)!!
        assertNull(flat.daysLeft)
    }

    @Test
    fun slowShrinkBeyondTenYearsIsNotReported() {
        // 5 MB a day with 100 GB free is about 55 years
        val f = Forecast.of(listOf(m(2.0, 100.01), m(1.0, 100.005), m(0.0, 100.0)), now)!!
        assertNull(f.daysLeft)
    }

    @Test
    fun farAwayFullDateIsNotAWarning() {
        // 0.1 GB a day with 20 GB free -> 200 days
        val f = Forecast.of(listOf(m(2.0, 20.2), m(1.0, 20.1), m(0.0, 20.0)), now)!!
        assertEquals(200.0, f.daysLeft!!, 0.5)
        assertFalse(f.warns)
    }

    @Test
    fun needsThreeSnapshotsOverHalfADay() {
        assertNull(Forecast.of(listOf(m(1.0, 10.0), m(0.0, 9.0)), now))
        assertNull(Forecast.of(listOf(m(0.3, 10.0), m(0.2, 9.5), m(0.0, 9.0)), now))
        assertNotNull(Forecast.of(listOf(m(1.0, 10.0), m(0.5, 9.5), m(0.0, 9.0)), now))
    }

    @Test
    fun aOneTimeDropIsNotTreatedAsAPace() {
        // flat at 10 GB for 3 days, then a single 2.4 GB drop in the last interval: no pace, but the drop is reported
        val f = Forecast.of(listOf(m(3.0, 10.0), m(2.5, 10.0), m(2.0, 10.0), m(1.0, 10.0), m(0.5, 10.0), m(0.0, 7.6)), now)!!
        assertNull(f.daysLeft)
        assertEquals(1, f.steps.size)
        assertEquals(-2.4 * gb, f.steps[0].bytes.toDouble(), 1e6)
        assertEquals("Ignoring a one-time drop of 2.4 GB (just now).", f.stepNote { "just now" })
    }

    @Test
    fun steadyDriftIsKeptWhenAJumpHappensInTheMiddle() {
        // loses 0.1 GB a day, plus one 3 GB drop on day 3: the pace is still 0.1 GB a day, so 16.5 GB lasts 165 days
        val f = Forecast.of(listOf(m(5.0, 20.0), m(4.0, 19.9), m(3.0, 19.8), m(2.0, 16.7), m(1.0, 16.6), m(0.0, 16.5)), now)!!
        assertEquals(-0.1 * gb, f.perDayBytes, 1e6)
        assertEquals(165.0, f.daysLeft!!, 0.5)
        assertEquals(1, f.steps.size)
    }

    @Test
    fun aRiseFromCleaningUpIsAlsoALeftOutJump() {
        val f = Forecast.of(listOf(m(4.0, 5.0), m(3.0, 4.9), m(2.0, 4.8), m(1.0, 9.0), m(0.0, 8.9)), now)!!
        assertEquals(1, f.steps.size)
        assertTrue(f.steps[0].bytes > 0)
        assertEquals("Ignoring a one-time rise of 4.2 GB (1 d ago).", f.stepNote { "1 d ago" })
        assertEquals(-0.1 * gb, f.perDayBytes, 1e6)
    }

    @Test
    fun severalJumpsAreCountedAndTheLatestIsDescribed() {
        val f = Forecast.of(listOf(m(5.0, 10.0), m(4.0, 8.0), m(3.0, 8.0), m(2.0, 6.5), m(1.0, 6.5), m(0.0, 6.5)), now)!!
        assertEquals(2, f.steps.size)
        assertEquals("Ignoring 2 one-time jumps; the latest was a drop of 1.5 GB (2 d ago).", f.stepNote { "2 d ago" })
    }

    @Test
    fun noJumpsMeansNoNote() {
        val f = Forecast.of(listOf(m(2.0, 10.0), m(1.0, 9.0), m(0.0, 8.0)), now)!!
        assertTrue(f.steps.isEmpty())
        assertNull(f.stepNote { "x" })
    }

    @Test
    fun ignoresSnapshotsOlderThanTwoWeeks() {
        // old readings were rising; only the last three (shrinking) count
        val f = Forecast.of(listOf(m(40.0, 5.0), m(30.0, 20.0), m(2.0, 12.0), m(1.0, 11.0), m(0.0, 10.0)), now)!!
        assertEquals(3, f.basedOn)
        assertEquals(10.0, f.daysLeft!!, 0.01)
    }
}
