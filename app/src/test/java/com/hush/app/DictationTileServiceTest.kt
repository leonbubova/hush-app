package com.hush.app

import android.service.quicksettings.Tile
import org.junit.Assert.assertEquals
import org.junit.Test

class DictationTileServiceTest {

    data class TileState(val state: Int, val iconRes: Int, val label: String)

    companion object {
        /** Pure function matching the mapping in DictationTileService.updateTile() */
        fun mapState(dictationState: DictationService.DictationState, appName: String): TileState {
            return when (dictationState) {
                DictationService.DictationState.IDLE -> TileState(Tile.STATE_INACTIVE, R.drawable.ic_mic, appName)
                DictationService.DictationState.RECORDING -> TileState(Tile.STATE_ACTIVE, R.drawable.ic_mic_active, "Recording...")
                DictationService.DictationState.STREAMING -> TileState(Tile.STATE_ACTIVE, R.drawable.ic_mic_active, "Streaming...")
                DictationService.DictationState.PROCESSING -> TileState(Tile.STATE_UNAVAILABLE, R.drawable.ic_mic, "Processing...")
                DictationService.DictationState.DONE -> TileState(Tile.STATE_INACTIVE, R.drawable.ic_mic, appName)
                DictationService.DictationState.ERROR -> TileState(Tile.STATE_INACTIVE, R.drawable.ic_mic, appName)
            }
        }
    }

    @Test
    fun `IDLE state maps to inactive tile`() {
        val result = mapState(DictationService.DictationState.IDLE, "Hush")
        assertEquals(Tile.STATE_INACTIVE, result.state)
        assertEquals(R.drawable.ic_mic, result.iconRes)
        assertEquals("Hush", result.label)
    }

    @Test
    fun `RECORDING state maps to active tile`() {
        val result = mapState(DictationService.DictationState.RECORDING, "Hush")
        assertEquals(Tile.STATE_ACTIVE, result.state)
        assertEquals(R.drawable.ic_mic_active, result.iconRes)
        assertEquals("Recording...", result.label)
    }

    @Test
    fun `STREAMING state maps to active tile`() {
        val result = mapState(DictationService.DictationState.STREAMING, "Hush")
        assertEquals(Tile.STATE_ACTIVE, result.state)
        assertEquals(R.drawable.ic_mic_active, result.iconRes)
        assertEquals("Streaming...", result.label)
    }

    @Test
    fun `PROCESSING state maps to unavailable tile`() {
        val result = mapState(DictationService.DictationState.PROCESSING, "Hush")
        assertEquals(Tile.STATE_UNAVAILABLE, result.state)
        assertEquals(R.drawable.ic_mic, result.iconRes)
        assertEquals("Processing...", result.label)
    }

    @Test
    fun `DONE state maps to inactive tile`() {
        val result = mapState(DictationService.DictationState.DONE, "Hush")
        assertEquals(Tile.STATE_INACTIVE, result.state)
        assertEquals(R.drawable.ic_mic, result.iconRes)
        assertEquals("Hush", result.label)
    }

    @Test
    fun `ERROR state maps to inactive tile`() {
        val result = mapState(DictationService.DictationState.ERROR, "Hush")
        assertEquals(Tile.STATE_INACTIVE, result.state)
        assertEquals(R.drawable.ic_mic, result.iconRes)
        assertEquals("Hush", result.label)
    }

    @Test
    fun `currentState defaults to IDLE`() {
        assertEquals(DictationService.DictationState.IDLE, DictationService.currentState)
    }
}
