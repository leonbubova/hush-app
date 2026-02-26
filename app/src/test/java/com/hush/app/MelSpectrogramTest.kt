package com.hush.app

import com.hush.app.transcription.MelSpectrogram
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class MelSpectrogramTest {

    @Test
    fun `output shape is correct for 30 seconds of audio`() {
        val audio = FloatArray(MelSpectrogram.SAMPLE_RATE * 30) // 30s of silence
        val result = MelSpectrogram.compute(audio)
        // Shape should be [1, 80, 3000] = 240000
        assertEquals(MelSpectrogram.N_MELS * MelSpectrogram.N_FRAMES, result.size)
    }

    @Test
    fun `output shape is correct for short audio (padded to 30s)`() {
        val audio = FloatArray(MelSpectrogram.SAMPLE_RATE) // 1 second
        val result = MelSpectrogram.compute(audio)
        assertEquals(MelSpectrogram.N_MELS * MelSpectrogram.N_FRAMES, result.size)
    }

    @Test
    fun `output shape is correct for empty audio`() {
        val audio = FloatArray(0)
        val result = MelSpectrogram.compute(audio)
        assertEquals(MelSpectrogram.N_MELS * MelSpectrogram.N_FRAMES, result.size)
    }

    @Test
    fun `output shape is correct for audio longer than 30s (truncated)`() {
        val audio = FloatArray(MelSpectrogram.SAMPLE_RATE * 60) // 60s, should truncate
        val result = MelSpectrogram.compute(audio)
        assertEquals(MelSpectrogram.N_MELS * MelSpectrogram.N_FRAMES, result.size)
    }

    @Test
    fun `hann window has correct properties`() {
        val window = MelSpectrogram.hannWindow(MelSpectrogram.N_FFT)
        assertEquals(MelSpectrogram.N_FFT, window.size)

        // Hann window is zero at start (periodic window: w[0] = 0)
        assertEquals(0f, window[0], 1e-6f)

        // Hann window peaks at center
        val center = window[MelSpectrogram.N_FFT / 2]
        assertTrue("Window center should be close to 1.0, got $center", center > 0.99f)

        // All values in [0, 1]
        for (i in window.indices) {
            assertTrue("Window value at $i should be >= 0: ${window[i]}", window[i] >= 0f)
            assertTrue("Window value at $i should be <= 1: ${window[i]}", window[i] <= 1f)
        }
    }

    @Test
    fun `mel filterbank has correct shape`() {
        val filters = MelSpectrogram.melFilterbank()
        val nFreqs = MelSpectrogram.N_FFT / 2 + 1
        assertEquals(MelSpectrogram.N_MELS * nFreqs, filters.size)
    }

    @Test
    fun `mel filterbank has no negative values`() {
        val filters = MelSpectrogram.melFilterbank()
        for (i in filters.indices) {
            assertTrue("Filter value at $i should be non-negative: ${filters[i]}", filters[i] >= 0f)
        }
    }

    @Test
    fun `mel filterbank covers frequency range`() {
        val nFreqs = MelSpectrogram.N_FFT / 2 + 1
        val filters = MelSpectrogram.melFilterbank()

        // Each mel band should have at least some non-zero weights
        for (m in 0 until MelSpectrogram.N_MELS) {
            var hasNonZero = false
            for (k in 0 until nFreqs) {
                if (filters[m * nFreqs + k] > 0f) {
                    hasNonZero = true
                    break
                }
            }
            assertTrue("Mel band $m should have non-zero weights", hasNonZero)
        }
    }

    @Test
    fun `hz to mel conversion is correct`() {
        // Known values
        assertEquals(0.0, MelSpectrogram.hzToMel(0.0), 1e-6)
        // 1000 Hz ≈ 1000 mel (by design of the scale)
        val mel1000 = MelSpectrogram.hzToMel(1000.0)
        assertTrue("1000 Hz should be ~1000 mel, got $mel1000", abs(mel1000 - 999.985) < 1.0)
    }

    @Test
    fun `mel to hz roundtrip`() {
        val testFreqs = doubleArrayOf(0.0, 100.0, 500.0, 1000.0, 4000.0, 8000.0)
        for (hz in testFreqs) {
            val roundtrip = MelSpectrogram.melToHz(MelSpectrogram.hzToMel(hz))
            assertEquals("Roundtrip for $hz Hz", hz, roundtrip, 1e-6)
        }
    }

    @Test
    fun `sine wave produces energy in expected mel band`() {
        // Generate a 440Hz sine wave (A4)
        val sampleRate = MelSpectrogram.SAMPLE_RATE
        val freq = 440.0
        val audio = FloatArray(sampleRate * 30) { i ->
            (0.5 * sin(2.0 * PI * freq * i / sampleRate)).toFloat()
        }

        val result = MelSpectrogram.compute(audio)

        // Find the mel band with highest average energy
        var maxBandEnergy = -Float.MAX_VALUE
        var maxBand = -1
        for (m in 0 until MelSpectrogram.N_MELS) {
            var sum = 0f
            for (t in 0 until MelSpectrogram.N_FRAMES) {
                sum += result[m * MelSpectrogram.N_FRAMES + t]
            }
            val avg = sum / MelSpectrogram.N_FRAMES
            if (avg > maxBandEnergy) {
                maxBandEnergy = avg
                maxBand = m
            }
        }

        // 440Hz should fall in a low-to-mid mel band (roughly bands 10-25)
        assertTrue(
            "440Hz sine should peak in mel band ~10-30, got band $maxBand",
            maxBand in 5..35
        )
    }

    @Test
    fun `silence produces uniform low energy`() {
        val audio = FloatArray(MelSpectrogram.SAMPLE_RATE * 30) // all zeros
        val result = MelSpectrogram.compute(audio)

        // All values should be the same (silence = uniform log-mel)
        val firstVal = result[0]
        for (i in result.indices) {
            assertEquals("Silence should produce uniform values", firstVal, result[i], 1e-3f)
        }
    }

    @Test
    fun `output values are in reasonable range`() {
        // Generate some audio with noise
        val audio = FloatArray(MelSpectrogram.SAMPLE_RATE * 30) { i ->
            (0.1 * sin(2.0 * PI * 1000.0 * i / MelSpectrogram.SAMPLE_RATE)).toFloat()
        }
        val result = MelSpectrogram.compute(audio)

        // After normalization, values should be roughly in [-1, 1] range
        for (i in result.indices) {
            assertTrue(
                "Output value at $i should be in reasonable range: ${result[i]}",
                result[i] >= -2.0f && result[i] <= 2.0f
            )
        }
    }

    @Test
    fun `constants are correct for whisper`() {
        assertEquals(16000, MelSpectrogram.SAMPLE_RATE)
        assertEquals(400, MelSpectrogram.N_FFT)
        assertEquals(160, MelSpectrogram.HOP_LENGTH)
        assertEquals(80, MelSpectrogram.N_MELS)
        assertEquals(30, MelSpectrogram.CHUNK_LENGTH)
        assertEquals(3000, MelSpectrogram.N_FRAMES)
    }
}
