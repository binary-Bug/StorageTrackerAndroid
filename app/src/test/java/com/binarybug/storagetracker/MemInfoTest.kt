package com.binarybug.storagetracker

import com.binarybug.storagetracker.scan.MemInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemInfoTest {
    // the real values read from the test phone
    private val phone = listOf(
        "MemTotal:        5656644 kB",
        "MemFree:          210000 kB",
        "MemAvailable:    1720576 kB",
        "Buffers:           10000 kB",
        "SwapTotal:       3145724 kB",
        "SwapFree:         550444 kB",
    )

    @Test
    fun parsesKibibyteLinesIntoBytes() {
        val m = MemInfo.parse(phone)!!
        assertEquals(5_656_644L * 1024, m.totalBytes)
        assertEquals(1_720_576L * 1024, m.availableBytes)
        assertEquals((5_656_644L - 1_720_576L) * 1024, m.usedBytes)
        assertEquals((3_145_724L - 550_444L) * 1024, m.swapUsedBytes)
        assertTrue(m.hasSwap)
    }

    @Test
    fun theTestPhoneIsUnderPressureBecauseMostSwapIsInUse() {
        // swap is 83% used even though 30% of RAM is available
        assertTrue(MemInfo.parse(phone)!!.underPressure)
    }

    @Test
    fun plentyOfRamAndLittleSwapIsNotPressure() {
        val m = MemInfo.parse(listOf("MemTotal: 8000000 kB", "MemAvailable: 4000000 kB", "SwapTotal: 2000000 kB", "SwapFree: 1900000 kB"))!!
        assertFalse(m.underPressure)
    }

    @Test
    fun veryLowAvailableRamIsPressureEvenWithoutSwap() {
        val m = MemInfo.parse(listOf("MemTotal: 4000000 kB", "MemAvailable: 300000 kB"))!!
        assertFalse(m.hasSwap)
        assertEquals(0L, m.swapUsedBytes)
        assertTrue(m.underPressure)
    }

    @Test
    fun missingAvailableFallsBackToFreeAndGarbageIsIgnored() {
        val m = MemInfo.parse(listOf("MemTotal: 1000 kB", "MemFree: 400 kB", "nonsense", "Weird: abc kB", ""))!!
        assertEquals(400L * 1024, m.availableBytes)
        assertNull(MemInfo.parse(listOf("MemFree: 400 kB")))
        assertNull(MemInfo.parse(emptyList()))
        assertNotNull(MemInfo.parse(listOf("MemTotal: 1 kB")))
    }
}
