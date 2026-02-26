package com.hush.app

import com.hush.app.transcription.AudioConverter
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioConverterTest {

    @Test
    fun `pcmBytesToFloat converts mono Int16 to normalized Float32`() {
        // Int16 samples: 0, 16384 (half max), -16384, 32767 (max)
        val shorts = shortArrayOf(0, 16384, -16384, 32767)
        val bytes = shortsToBytes(shorts)

        val result = AudioConverter.pcmBytesToFloat(bytes, channelCount = 1)

        assertEquals(4, result.size)
        assertEquals(0f, result[0], 0.001f)
        assertEquals(0.5f, result[1], 0.001f)
        assertEquals(-0.5f, result[2], 0.001f)
        assertEquals(32767f / 32768f, result[3], 0.001f)
    }

    @Test
    fun `pcmBytesToFloat averages stereo channels to mono`() {
        // Stereo: L=16384, R=-16384 → mono = 0
        //         L=32767, R=32767 → mono ≈ 1.0
        val shorts = shortArrayOf(16384, -16384, 32767, 32767)
        val bytes = shortsToBytes(shorts)

        val result = AudioConverter.pcmBytesToFloat(bytes, channelCount = 2)

        assertEquals(2, result.size)
        assertEquals(0f, result[0], 0.001f) // (0.5 + -0.5) / 2
        assertEquals(32767f / 32768f, result[1], 0.001f)
    }

    @Test
    fun `pcmBytesToFloat output values in range -1 to 1`() {
        val shorts = shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE, 0)
        val bytes = shortsToBytes(shorts)

        val result = AudioConverter.pcmBytesToFloat(bytes, channelCount = 1)

        for (sample in result) {
            assertTrue("Sample $sample out of range", sample >= -1.0f && sample <= 1.0f)
        }
    }

    @Test
    fun `pcmBytesToFloat handles empty input`() {
        val result = AudioConverter.pcmBytesToFloat(ByteArray(0), channelCount = 1)
        assertEquals(0, result.size)
    }

    @Test
    fun `resample returns copy when rates are equal`() {
        val input = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val result = AudioConverter.resample(input, fromRate = 16000, toRate = 16000)

        assertArrayEquals(input, result, 0.0001f)
        assertNotSame(input, result) // should be a copy
    }

    @Test
    fun `resample handles empty input`() {
        val result = AudioConverter.resample(floatArrayOf(), fromRate = 44100, toRate = 16000)
        assertEquals(0, result.size)
    }

    @Test
    fun `resample downsamples 44100 to 16000`() {
        // Create a simple signal: 44100 samples = 1 second
        val input = FloatArray(44100) { (it % 100) / 100f }

        val result = AudioConverter.resample(input, fromRate = 44100, toRate = 16000)

        // Output should be approximately 16000 samples for 1 second
        assertTrue("Expected ~16000 samples, got ${result.size}", result.size in 15999..16001)
    }

    @Test
    fun `resample preserves DC signal`() {
        // Constant signal should remain constant after resampling
        val input = FloatArray(44100) { 0.5f }

        val result = AudioConverter.resample(input, fromRate = 44100, toRate = 16000)

        for (sample in result) {
            assertEquals(0.5f, sample, 0.001f)
        }
    }

    @Test
    fun `resample upsamples 8000 to 16000`() {
        val input = FloatArray(8000) { it.toFloat() / 8000f }

        val result = AudioConverter.resample(input, fromRate = 8000, toRate = 16000)

        // Should approximately double the number of samples
        assertTrue("Expected ~16000 samples, got ${result.size}", result.size in 15998..16001)
    }

    @Test
    fun `resample linear interpolation is correct for known values`() {
        // Simple test: [0, 1] resampled from rate 2 to rate 4 should give [0, 0.5, 1.0, ...]
        val input = floatArrayOf(0f, 1f)
        val result = AudioConverter.resample(input, fromRate = 2, toRate = 4)

        assertTrue(result.size >= 2)
        assertEquals(0f, result[0], 0.01f)
        // Midpoint should be approximately 0.5
        assertEquals(0.5f, result[1], 0.01f)
    }

    private fun shortsToBytes(shorts: ShortArray): ByteArray {
        val buffer = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in shorts) buffer.putShort(s)
        return buffer.array()
    }
}
