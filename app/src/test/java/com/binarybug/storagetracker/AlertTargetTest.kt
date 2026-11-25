package com.binarybug.storagetracker

import com.binarybug.storagetracker.scan.AlertTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertTargetTest {
    @Test
    fun plainTargetsRoundTrip() {
        for (t in listOf(AlertTarget.Overview, AlertTarget.Changes, AlertTarget.Apps)) assertSame(t, AlertTarget.parse(t.encode()))
        assertEquals("changes", AlertTarget.Changes.encode())
    }

    @Test
    fun aFolderKeepsItsWholePathEvenWithColonsAndSpaces() {
        for (path in listOf("/DCIM/Camera", "/", "/Download/a:b c", "/Android/media/com.whatsapp")) {
            val parsed = AlertTarget.parse(AlertTarget.Folder(path).encode())
            assertTrue(parsed is AlertTarget.Folder)
            assertEquals(path, (parsed as AlertTarget.Folder).path)
        }
    }

    @Test
    fun anythingElseIsNotATarget() {
        assertNull(AlertTarget.parse(null))
        assertNull(AlertTarget.parse(""))
        assertNull(AlertTarget.parse("folder:"))
        assertNull(AlertTarget.parse("Changes"))
        assertNull(AlertTarget.parse("https://example.com"))
    }
}
