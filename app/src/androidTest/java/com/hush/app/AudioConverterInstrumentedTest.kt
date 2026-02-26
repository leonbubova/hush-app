package com.hush.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hush.app.transcription.AudioConverter
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented test for AudioConverter using real MediaExtractor/MediaCodec on device.
 * Uses the test_audio.m4a fixture (~37KB, says "Hello this is a test for end to end testing").
 */
@RunWith(AndroidJUnit4::class)
class AudioConverterInstrumentedTest {

    private lateinit var testAudioFile: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val inputStream = context.assets.open("test_audio.m4a")
        testAudioFile = File(context.cacheDir, "test_audio.m4a")
        testAudioFile.outputStream().use { output ->
            inputStream.copyTo(output)
        }
    }

    @Test
    fun decodeProduces16kMonoOutput() {
        val samples = AudioConverter.decodeToFloat16kMono(testAudioFile)

        // Should have a reasonable number of samples for a short audio clip
        // test_audio.m4a is a few seconds long → expect several thousand samples at 16kHz
        assertTrue("Expected at least 16000 samples (1s), got ${samples.size}", samples.size >= 16000)
    }

    @Test
    fun outputValuesInNormalizedRange() {
        val samples = AudioConverter.decodeToFloat16kMono(testAudioFile)

        for (i in samples.indices) {
            assertTrue(
                "Sample at index $i out of range: ${samples[i]}",
                samples[i] >= -1.0f && samples[i] <= 1.0f
            )
        }
    }

    @Test
    fun outputIsNotSilent() {
        val samples = AudioConverter.decodeToFloat16kMono(testAudioFile)

        // Calculate variance — non-silent audio should have meaningful variance
        val mean = samples.average().toFloat()
        val variance = samples.map { (it - mean) * (it - mean) }.average()

        assertTrue("Audio output appears silent (variance=$variance)", variance > 0.0001)
    }

    @Test
    fun sampleCountMatchesExpectedDuration() {
        val samples = AudioConverter.decodeToFloat16kMono(testAudioFile)

        // test_audio.m4a is roughly 2-5 seconds
        val durationSeconds = samples.size / 16000.0
        assertTrue(
            "Duration ${durationSeconds}s seems wrong for test audio",
            durationSeconds in 1.0..10.0
        )
    }
}
