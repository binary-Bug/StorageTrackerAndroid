package com.binarybug.storagetracker

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatBytesTest {
    @Test
    fun smallValuesStayInBytes() {
        assertEquals("512 B", formatBytes(512))
    }

    @Test
    fun kilobytesUseDecimalUnits() {
        assertEquals("1.5 KB", formatBytes(1500))
    }

    @Test
    fun gigabytesMatchAndroidSettings() {
        assertEquals("2.0 GB", formatBytes(2_000_000_000L))
        assertEquals("128.0 GB", formatBytes(128_000_000_000L))
    }
}
