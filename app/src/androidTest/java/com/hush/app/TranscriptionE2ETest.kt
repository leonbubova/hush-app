package com.hush.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
}
