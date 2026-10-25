package com.squeve.redail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePacksTest {
    @Test
    fun everyStyleHasItsOwnName() {
        assertEquals(PACKS.size, PACKS.map { it.name }.toSet().size)
    }

    @Test
    fun thereAreLightAndDarkStyles() {
        assertTrue(PACKS.any { it.light })
        assertTrue(PACKS.any { !it.light })
    }

    @Test
    fun sceneryIsOneOfTheKnownKinds() {
        assertTrue(PACKS.all { it.scenery in 0..2 })
        assertTrue(PACKS.all { it.bg.size >= 2 })
    }
}
