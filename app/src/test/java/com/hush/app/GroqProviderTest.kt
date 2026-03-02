package com.hush.app

import com.hush.app.transcription.GroqProvider
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

class GroqProviderTest {

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
        model: String = "whisper-large-v3-turbo",
    ) = GroqProvider(
        config = ProviderConfig.Groq(apiKey = apiKey, model = model),
        endpointOverride = server.url("/").toString(),
    )

    @Test
    fun `provider has correct metadata`() {
        val p = provider()
        assertEquals(ProviderConfig.PROVIDER_GROQ, p.id)
        assertEquals("Groq", p.displayName)
        assertTrue(p.requiresNetwork)
    }

    @Test
    fun `blank API key returns Error without making request`() = runBlocking {
        val p = provider(apiKey = "")
        val result = p.transcribe(testFile)

        assertTrue(result is TranscribeResult.Error)
        assertTrue((result as TranscribeResult.Error).message.contains("API key not configured"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `successful transcription returns Success`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"text": "Hello from Groq"}"""))

        val result = provider().transcribe(testFile)

        assertTrue(result is TranscribeResult.Success)
        assertEquals("Hello from Groq", (result as TranscribeResult.Success).text)
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
        assertTrue(error.message.contains("Invalid API key"))
    }

    @Test
    fun `429 returns rate limit error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody(""))

        val result = provider().transcribe(testFile)

        assertTrue(result is TranscribeResult.Error)
        assertTrue((result as TranscribeResult.Error).message.contains("Rate limited"))
    }

    @Test
    fun `429 with insufficient_quota returns billing error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody(
            """{"error": {"message": "You exceeded your current quota", "type": "insufficient_quota", "code": "insufficient_quota"}}"""
        ))

        val result = provider().transcribe(testFile)

        assertTrue(result is TranscribeResult.Error)
        val error = result as TranscribeResult.Error
        assertEquals(429, error.code)
        assertTrue(error.message.contains("billing"))
    }

    @Test
    fun `500 returns server error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val result = provider().transcribe(testFile)

        assertTrue(result is TranscribeResult.Error)
        val error = result as TranscribeResult.Error
        assertEquals(500, error.code)
        assertTrue(error.message.contains("server is down"))
    }

    @Test
    fun `request includes correct auth header and model`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"text": "test"}"""))

        provider(apiKey = "gsk-groq-abc", model = "whisper-large-v3").transcribe(testFile)

        val request = server.takeRequest()
        assertEquals("Bearer gsk-groq-abc", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("whisper-large-v3"))
    }

    @Test
    fun `request is multipart form data`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"text": "test"}"""))

        provider().transcribe(testFile)

        val request = server.takeRequest()
        val contentType = request.getHeader("Content-Type") ?: ""
        assertTrue(contentType.contains("multipart/form-data"))
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

        val p = GroqProvider(
            config = ProviderConfig.Groq(apiKey = "key"),
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
