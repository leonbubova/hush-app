package com.hush.app

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusPillOverlayTest {

    @Test
    fun `START type has purple border color`() {
        assertEquals(Color.parseColor("#6C63FF"), StatusPillOverlay.PillType.START.borderColor)
    }

    @Test
    fun `DONE type has green border color`() {
        assertEquals(Color.parseColor("#4ECDC4"), StatusPillOverlay.PillType.DONE.borderColor)
    }

    @Test
    fun `ERROR type has red border color`() {
        assertEquals(Color.parseColor("#FF6B6B"), StatusPillOverlay.PillType.ERROR.borderColor)
    }

    @Test
    fun `fromString maps START correctly`() {
        assertEquals(StatusPillOverlay.PillType.START, StatusPillOverlay.PillType.fromString("START"))
    }

    @Test
    fun `fromString maps DONE correctly`() {
        assertEquals(StatusPillOverlay.PillType.DONE, StatusPillOverlay.PillType.fromString("DONE"))
    }

    @Test
    fun `fromString maps ERROR correctly`() {
        assertEquals(StatusPillOverlay.PillType.ERROR, StatusPillOverlay.PillType.fromString("ERROR"))
    }

    @Test
    fun `fromString is case insensitive`() {
        assertEquals(StatusPillOverlay.PillType.START, StatusPillOverlay.PillType.fromString("start"))
        assertEquals(StatusPillOverlay.PillType.DONE, StatusPillOverlay.PillType.fromString("done"))
        assertEquals(StatusPillOverlay.PillType.ERROR, StatusPillOverlay.PillType.fromString("error"))
    }

    @Test
    fun `fromString defaults to START for unknown values`() {
        assertEquals(StatusPillOverlay.PillType.START, StatusPillOverlay.PillType.fromString("unknown"))
    }

    @Test
    fun `ACTION_STATUS_PILL constant is correct`() {
        assertEquals("com.hush.ACTION_STATUS_PILL", DictationService.ACTION_STATUS_PILL)
    }

    @Test
    fun `EXTRA_PILL_TYPE constant is correct`() {
        assertEquals("com.hush.EXTRA_PILL_TYPE", DictationService.EXTRA_PILL_TYPE)
    }

    @Test
    fun `EXTRA_PILL_MESSAGE constant is correct`() {
        assertEquals("com.hush.EXTRA_PILL_MESSAGE", DictationService.EXTRA_PILL_MESSAGE)
    }

    @Test
    fun `EXTRA_WORD_COUNT constant is correct`() {
        assertEquals("com.hush.EXTRA_WORD_COUNT", DictationService.EXTRA_WORD_COUNT)
    }
}
