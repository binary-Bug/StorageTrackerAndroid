package com.binarybug.storagetracker

import com.binarybug.storagetracker.data.AppEntry
import com.binarybug.storagetracker.data.Snapshot
import com.binarybug.storagetracker.data.SnapshotMeta
import com.binarybug.storagetracker.scan.AlertRules
import com.binarybug.storagetracker.scan.Compare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertRulesTest {
    private val gb = 1_000_000_000L
    private val hour = 3_600_000L

    @Test
    fun lowSpaceNeedsUnderTenPercentAndOncePerDay() {
        val now = 100 * hour
        assertTrue(AlertRules.shouldWarnLow(100 * gb, 9 * gb, 0L, now))
        assertFalse(AlertRules.shouldWarnLow(100 * gb, 10 * gb, 0L, now)) // exactly 10% is not low
        assertFalse(AlertRules.shouldWarnLow(100 * gb, 9 * gb, now - 2 * hour, now)) // warned 2 hours ago
        assertTrue(AlertRules.shouldWarnLow(100 * gb, 9 * gb, now - 24 * hour, now))
        assertFalse(AlertRules.shouldWarnLow(0L, 0L, 0L, now))
    }

    private fun snap(t: Long, dirs: List<Pair<String, Long>>, apps: List<AppEntry>, measured: Boolean) = Snapshot(
        SnapshotMeta("s$t", t, 128 * gb, 50 * gb, 0L, 0L, 0L, 0, 0.0, true, 25), dirs, emptyList(), emptyList(),
        apps = apps, appsAvailable = measured,
    )

    private fun app(pkg: String, total: Long) = AppEntry(pkg, pkg.uppercase(), 0L, total, 0L, false)

    @Test
    fun reportsTheBiggestGrowersAboveTheLimit() {
        val older = snap(1, listOf("/DCIM" to gb, "/Movies" to gb), listOf(app("a", 5 * gb), app("b", gb)), true)
        val newer = snap(2, listOf("/DCIM" to 3 * gb, "/Movies" to gb + 100_000_000L), listOf(app("a", 6 * gb), app("b", gb + 50_000_000L)), true)
        val g = AlertRules.growth(Compare.run(older, newer), gb)
        assertEquals(listOf("/DCIM", "A"), g.map { it.label })
        assertEquals(listOf(2 * gb, gb), g.map { it.bytes })
        assertEquals(listOf(false, true), g.map { it.isApp })
        // tapping the alert opens the grown folder in Browse, or the Apps tab for a grown app
        assertEquals(listOf("folder:/DCIM", "apps"), g.map { it.target.encode() })
    }

    @Test
    fun offLimitMeansNoAlertAndAnAppNewToTheSnapshotIsNotGrowth() {
        val older = snap(1, listOf("/DCIM" to gb), emptyList(), false)
        val newer = snap(2, listOf("/DCIM" to gb), listOf(app("a", 9 * gb)), true) // apps only just became measurable
        assertTrue(AlertRules.growth(Compare.run(older, newer), gb).isEmpty())
        assertTrue(AlertRules.growth(Compare.run(older, snap(3, listOf("/DCIM" to 5 * gb), emptyList(), false)), 0L).isEmpty())
        assertTrue(AlertRules.growth(Compare.run(null, newer), gb).isEmpty())
    }

    @Test
    fun capsTheListAtThree() {
        val older = snap(1, listOf("/a" to 0L, "/b" to 0L, "/c" to 0L, "/d" to 0L), emptyList(), false)
        val newer = snap(2, listOf("/a" to 2 * gb, "/b" to 3 * gb, "/c" to 4 * gb, "/d" to 5 * gb), emptyList(), false)
        assertEquals(listOf("/d", "/c", "/b"), AlertRules.growth(Compare.run(older, newer), gb).map { it.label })
    }
}
