package com.hush.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.util.Log
import com.hush.app.transcription.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * E2E transcription tests that send real audio to real APIs.
 *
 * Each test is skipped (via assumeTrue) if the corresponding API key
 * is not provided in local.properties. This keeps CI green without keys.
 *
 * Audio: "Hello this is a test for end to end testing"
 *
 * Run:
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.hush.app.TranscriptionE2ETest
 *
 * Local transcription tests require the Q4 model to be downloaded first:
 *   1. Install debug APK on emulator
 *   2. Open Settings → download whisper-tiny-en-q4 model
 *   3. Then run the tests
 */
@RunWith(AndroidJUnit4::class)
class TranscriptionE2ETest {

    private lateinit var context: Context
    private lateinit var audioFile: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext

        // Copy test audio from assets to a temp file (providers need a File)
        val input = InstrumentationRegistry.getInstrumentation().context
            .assets.open("test_audio.m4a")
        audioFile = File(context.cacheDir, "test_audio.m4a")
        audioFile.outputStream().use { out -> input.copyTo(out) }
    }

    private fun getArg(key: String): String? =
        InstrumentationRegistry.getArguments().getString(key)

    @Test
    fun voxtral_transcribes_real_audio() = runBlocking {
        val apiKey = getArg("voxtralKey")
        assumeTrue("TEST_VOXTRAL_KEY not set — skipping", !apiKey.isNullOrBlank())

        val provider = VoxtralProvider(ProviderConfig.Voxtral(apiKey = apiKey!!))
        val result = provider.transcribe(audioFile)

        assertTrue("Expected Success, got: $result", result is TranscribeResult.Success)
        val text = (result as TranscribeResult.Success).text.lowercase()
        assertTrue("Expected 'hello' in transcription, got: $text", text.contains("hello"))
        assertTrue("Expected 'test' in transcription, got: $text", text.contains("test"))
    }

    @Test
    fun openai_transcribes_real_audio() = runBlocking {
        val apiKey = getArg("openaiKey")
        assumeTrue("TEST_OPENAI_KEY not set — skipping", !apiKey.isNullOrBlank())

        val provider = OpenAiWhisperProvider(ProviderConfig.OpenAiWhisper(apiKey = apiKey!!))
        val result = provider.transcribe(audioFile)

        assertTrue("Expected Success, got: $result", result is TranscribeResult.Success)
        val text = (result as TranscribeResult.Success).text.lowercase()
        assertTrue("Expected 'hello' in transcription, got: $text", text.contains("hello"))
        assertTrue("Expected 'test' in transcription, got: $text", text.contains("test"))
    }

    @Test
    fun groq_transcribes_real_audio() = runBlocking {
        val apiKey = getArg("groqKey")
        assumeTrue("TEST_GROQ_KEY not set — skipping", !apiKey.isNullOrBlank())

        val provider = GroqProvider(ProviderConfig.Groq(apiKey = apiKey!!))
        val result = provider.transcribe(audioFile)

        assertTrue("Expected Success, got: $result", result is TranscribeResult.Success)
        val text = (result as TranscribeResult.Success).text.lowercase()
        assertTrue("Expected 'hello' in transcription, got: $text", text.contains("hello"))
        assertTrue("Expected 'test' in transcription, got: $text", text.contains("test"))
    }

    private fun isModelDownloaded(): Boolean {
        val modelManager = ModelManager(context)
        return modelManager.getModelPath("whisper-tiny-en-q4") != null
    }

    private fun copyAssetToFile(assetName: String): File {
        val input = InstrumentationRegistry.getInstrumentation().context
            .assets.open(assetName)
        val file = File(context.cacheDir, assetName)
        file.outputStream().use { out -> input.copyTo(out) }
        return file
    }

    @Test
    fun local_transcribes_short_audio() = runBlocking {
        assumeTrue("Q4 model not downloaded — skipping", isModelDownloaded())

        val config = ProviderConfig.Local(model = "whisper-tiny-en-q4")
        val provider = LocalProvider(config, context)
        try {
            val result = provider.transcribe(audioFile)

            assertTrue("Expected Success, got: $result", result is TranscribeResult.Success)
            val text = (result as TranscribeResult.Success).text.lowercase()
            Log.i("TranscriptionE2E", "Local short transcription: $text")
            assertTrue("Expected 'hello' in transcription, got: $text", text.contains("hello"))
            assertTrue("Expected 'test' in transcription, got: $text", text.contains("test"))
        } finally {
            provider.release()
        }
    }

    @Test
    fun local_transcribes_long_audio() = runBlocking {
        assumeTrue("Q4 model not downloaded — skipping", isModelDownloaded())

        val longAudioFile = copyAssetToFile("test_audio_long.m4a")
        val config = ProviderConfig.Local(model = "whisper-tiny-en-q4")
        val provider = LocalProvider(config, context)
        try {
            val startTime = System.currentTimeMillis()
            val result = provider.transcribe(longAudioFile)
            val elapsed = System.currentTimeMillis() - startTime
            Log.i("TranscriptionE2E", "Local long transcription took ${elapsed}ms")

            assertTrue("Expected Success, got: $result", result is TranscribeResult.Success)
            val text = (result as TranscribeResult.Success).text.lowercase()
            Log.i("TranscriptionE2E", "Local long transcription: $text")
            assertTrue("Transcription should not be empty", text.isNotBlank())
            assertTrue("Expected 'transcription' in result, got: $text", text.contains("transcription"))
            assertTrue("Expected 'recording' in result, got: $text", text.contains("recording"))
        } finally {
            provider.release()
        }
    }
}
