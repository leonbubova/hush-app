package com.hush.app

import com.hush.app.transcription.OpenAiWhisperProvider
import com.hush.app.transcription.ProviderConfig
import com.hush.app.transcription.TranscribeResult
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class OpenAiWhisperProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var testFile: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val resource = javaClass.classLoader!!.getResource("test_audio.m4a")
        testFile = File(resource.toURI())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun provider(
        apiKey: String = "test-key",
        model: String = "whisper-1",
        language: String = "",
    ) = OpenAiWhisperProvider(
        config = ProviderConfig.OpenAiWhisper(apiKey = apiKey, model = model, language = language),
        endpointOverride = server.url("/").toString(),
    )

    @Test
    fun `provider has correct metadata`() {
        val p = provider()
        assertEquals(ProviderConfig.PROVIDER_OPENAI, p.id)
        assertEquals("OpenAI Whisper", p.displayName)
        assertTrue(p.requiresNetwork)
    }

    @Test
    fun `blank API key returns Error without making request`() = runBlocking {
        val p = provider(apiKey = "")
        val result = p.transcribe(testFile)

        assertTrue(result is TranscribeResult.Error)
        assertEquals("No API key configured", (result as TranscribeResult.Error).message)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `successful transcription returns Success`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"text": "Hello world"}"""))

        val result = provider().transcribe(testFile)

        assertTrue(result is TranscribeResult.Success)
        assertEquals("Hello world", (result as TranscribeResult.Success).text)
    }

    @Test
    fun `401 returns invalid API key error`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(401)
            .setBody("""{"error": "unauthorized"}"""))

        val result = provider().transcribe(testFile)

        assertTrue(result is TranscribeResult.Error)
        val error = result as TranscribeResult.Error
        assertEquals(401, error.code)
        assertEquals("Invalid API key", error.message)
    }

    @Test
    fun `429 returns rate limit error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody(""))

        val result = provider().transcribe(testFile)

        assertTrue(result is TranscribeResult.Error)
        assertTrue((result as TranscribeResult.Error).message.contains("Rate limited"))
    }

    @Test
    fun `request includes correct auth header and model`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"text": "test"}"""))

        provider(apiKey = "sk-openai-123", model = "whisper-1").transcribe(testFile)

        val request = server.takeRequest()
        assertEquals("Bearer sk-openai-123", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("whisper-1"))
    }

    @Test
    fun `language param is included when set`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"text": "Hallo Welt"}"""))

        provider(language = "de").transcribe(testFile)

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("de"))
    }

    @Test
    fun `language param is omitted when blank`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"text": "test"}"""))

        provider(language = "").transcribe(testFile)

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        // Should not contain a "language" form field
        assertFalse(body.contains("name=\"language\""))
    }

    @Test
    fun `malformed JSON returns Error`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("not json"))

        val result = provider().transcribe(testFile)

        assertTrue(result is TranscribeResult.Error)
    }

    @Test
    fun `network error returns Error`() = runBlocking {
        server.shutdown()

        val p = OpenAiWhisperProvider(
            config = ProviderConfig.OpenAiWhisper(apiKey = "key"),
            endpointOverride = "http://localhost:1/v1/audio/transcriptions",
        )
        val result = p.transcribe(testFile)

        assertTrue(result is TranscribeResult.Error)
        assertNull((result as TranscribeResult.Error).code)
    }

    @Test
    fun `real audio file is sent with correct content type`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"text": "test"}"""))

        provider().transcribe(testFile)

        val request = server.takeRequest()
        assertTrue("Request body should contain real audio data", request.bodySize > 1000)
        val body = request.body.readUtf8()
        assertTrue(body.contains("audio/mp4"))
        assertTrue(body.contains("test_audio.m4a"))
    }
}
