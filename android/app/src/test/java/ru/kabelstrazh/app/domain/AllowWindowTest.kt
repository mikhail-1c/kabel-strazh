package ru.kabelstrazh.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowWindowTest {
    @Test
    fun inactiveWhenUnset() {
        assertFalse(AllowWindow().isActive(1_000))
    }

    @Test
    fun remainingNeverNegative() {
        assertEquals(0L, AllowWindow(untilEpochMs = 500).remainingMs(1_000))
    }

    @Test
    fun activeBeforeDeadline() {
        val window = AllowWindow(untilEpochMs = 5_000)
        assertTrue(window.isActive(4_999))
        assertEquals(2_000L, window.remainingMs(3_000))
    }
}
