package com.binarybug.storagetracker

import com.binarybug.storagetracker.data.AppEntry
import com.binarybug.storagetracker.data.CatTotal
import com.binarybug.storagetracker.data.Snapshot
import com.binarybug.storagetracker.data.SnapshotMeta
import com.binarybug.storagetracker.scan.Compare
import com.binarybug.storagetracker.scan.Forecast
import com.binarybug.storagetracker.scan.Report
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportTest {
    private val gb = 1_000_000_000L
    private fun meta(t: Long, free: Long) = SnapshotMeta("s$t", t, 128 * gb, free, 0L, 0L, 0L, 0, 0.0, true, 25)

    private fun snap(t: Long, free: Long, dirs: List<Pair<String, Long>>, whatsapp: Long) = Snapshot(
        meta(t, free), dirs, emptyList(), emptyList(),
        apps = listOf(AppEntry("com.whatsapp", "WhatsApp", 100_000_000L, whatsapp, 0L, false)),
        cats = listOf(CatTotal("VIDEOS", dirs.sumOf { it.second }, 1L)),
        appsAvailable = true,
    )

    @Test
    fun singleSnapshotSaysThereIsNothingToCompare() {
        val s = snap(1_000L, 50 * gb, listOf("/DCIM" to gb), 200_000_000L)
        val text = Report.build(Compare.run(null, s), null)
        assertTrue(text.startsWith("Storage Tracker report"))
        assertTrue(text.contains("Only one snapshot"))
        assertFalse(text.contains("By category"))
    }

    @Test
    fun comparisonListsCategoriesFoldersAndAppsWithSigns() {
        val older = snap(1_000L, 50 * gb, listOf("/DCIM" to gb, "/Movies" to 2 * gb), 200_000_000L)
        val newer = snap(2_000L, 49 * gb, listOf("/DCIM" to 2 * gb, "/Movies" to gb), 500_000_000L)
        val text = Report.build(Compare.run(older, newer), null)
        assertTrue(text, text.contains("used +${formatBytes(gb)}, free −${formatBytes(gb)}"))
        assertTrue(text, text.contains("Biggest folder changes:"))
        assertTrue(text, text.contains("+${formatBytes(gb)}  /DCIM"))
        assertTrue(text, text.contains("−${formatBytes(gb)}  /Movies"))
        assertTrue(text, text.contains("+${formatBytes(300_000_000L)}  WhatsApp"))
    }

    @Test
    fun forecastLineAppearsOnlyWhenThereIsAnEstimate() {
        val older = snap(1_000L, 50 * gb, listOf("/DCIM" to gb), 0L)
        val newer = snap(2_000L, 49 * gb, listOf("/DCIM" to 2 * gb), 0L)
        val day = 86_400_000L
        val now = 10 * day
        val metas = listOf(
            SnapshotMeta("a", now - 3 * day, 128 * gb, 11 * gb, 0L, 0L, 0L, 0, 0.0, true, 25),
            SnapshotMeta("b", now - 2 * day, 128 * gb, 10 * gb, 0L, 0L, 0L, 0, 0.0, true, 25),
            SnapshotMeta("c", now - day, 128 * gb, 9 * gb, 0L, 0L, 0L, 0, 0.0, true, 25),
        )
        val f = Forecast.of(metas, now)
        assertEquals(true, f?.daysLeft != null)
        assertTrue(Report.build(Compare.run(older, newer), f).contains("free space runs out in about 9 days"))
        assertFalse(Report.build(Compare.run(older, newer), null).contains("runs out"))
    }
}
