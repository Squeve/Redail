package com.squeve.redail.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdaterTest {
    @Test
    fun markdownBecomesPlainLines() {
        val raw = "## 0.15 - Notes\r\n- **Drag** to reorder\n* Swipe `left`\n\n\n\n**Full Changelog**: https://x"
        assertEquals("0.15 - Notes\n• Drag to reorder\n• Swipe left", Updater.cleanNotes(raw))
    }

    @Test
    fun longNotesAreShortened() {
        assertEquals(1201, Updater.cleanNotes("a".repeat(2000)).length)
    }

    @Test
    fun emptyNotesStayEmpty() {
        assertEquals("", Updater.cleanNotes(""))
    }
}
