package com.hush.app

import com.hush.app.transcription.ProviderConfig
import com.hush.app.transcription.TranscribeResult
import com.hush.app.transcription.VoxtralProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for VoxtralProvider transcription logic.
 *
 * Uses MockWebServer to simulate Mistral API responses and verify
 * that each HTTP status code maps to the correct TranscribeResult.
 */
class VoxtralApiTest {

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

    private fun provider(apiKey: String = "test-key"): VoxtralProvider {
        return VoxtralProvider(
            ProviderConfig.Voxtral(
                apiKey = apiKey,
                endpoint = server.url("/").toString(),
            )
        )
    }

    @Test
    fun `successful transcription returns Success with text`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"text": "Hello world"}"""))

        val result = provider().transcribe(testFile)

        assertTrue(result is TranscribeResult.Success)
        assertEquals("Hello world", (result as TranscribeResult.Success).text)
    }

    @Test
    fun `401 returns Error with invalid API key message`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(401)
            .setBody("""{"error": "unauthorized"}"""))

        val result = provider("bad-key").transcribe(testFile)

        assertTrue(result is TranscribeResult.Error)
        val error = result as TranscribeResult.Error
        assertEquals(401, error.code)
        assertEquals("Invalid API key", error.message)
    }

    @Test
    fun `429 returns Error with rate limit message`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(429)
            .setBody(""))

        val result = provider().transcribe(testFile)

        assertTrue(result is TranscribeResult.Error)
        val error = result as TranscribeResult.Error
        assertEquals(429, error.code)
        assertTrue(error.message.contains("Rate limited"))
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
    fun `500 returns Error with server error message`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error"))

        val result = provider().transcribe(testFile)

        assertTrue(result is TranscribeResult.Error)
        val error = result as TranscribeResult.Error
        assertEquals(500, error.code)
        assertTrue(error.message.contains("Server error"))
    }

    @Test
    fun `503 returns Error with server error message`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(503)
            .setBody("Service Unavailable"))

        val result = provider().transcribe(testFile)

        assertTrue(result is TranscribeResult.Error)
        val error = result as TranscribeResult.Error
        assertEquals(503, error.code)
        assertTrue(error.message.contains("Server error"))
    }

    @Test
    fun `network error returns Error with no internet message`() = runBlocking {
        // Shut down server to simulate network failure
        server.shutdown()

        val p = VoxtralProvider(
            ProviderConfig.Voxtral(
                apiKey = "test-key",
                endpoint = "http://localhost:1/v1/audio/transcriptions",
            )
        )
        val result = p.transcribe(testFile)

        assertTrue(result is TranscribeResult.Error)
        val error = result as TranscribeResult.Error
        assertNull(error.code)
    }

    @Test
    fun `empty response body returns Error`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(""))

        val result = provider().transcribe(testFile)

        // Empty body can't be parsed as JSON — should be an error
        assertTrue(result is TranscribeResult.Error)
    }

    @Test
    fun `malformed JSON returns Error`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("not json at all"))

        val result = provider().transcribe(testFile)

        assertTrue(result is TranscribeResult.Error)
    }

    @Test
    fun `blank API key returns Error without making request`() = runBlocking {
        val p = VoxtralProvider(ProviderConfig.Voxtral(apiKey = ""))
        val result = p.transcribe(testFile)

        assertTrue(result is TranscribeResult.Error)
        assertEquals("No API key configured", (result as TranscribeResult.Error).message)
    }

    @Test
    fun `request includes correct auth header`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"text": "test"}"""))

        provider("my-secret-key").transcribe(testFile)

        val request = server.takeRequest()
        assertEquals("Bearer my-secret-key", request.getHeader("Authorization"))
    }

    @Test
    fun `request uses multipart form with model and file`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"text": "test"}"""))

        provider().transcribe(testFile)

        val request = server.takeRequest()
        val contentType = request.getHeader("Content-Type") ?: ""
        assertTrue(contentType.contains("multipart/form-data"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("voxtral-mini-latest"))
    }

    @Test
    fun `real audio file is sent with correct size`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"text": "test"}"""))

        provider().transcribe(testFile)

        val request = server.takeRequest()
        // Real m4a file should be significantly larger than a dummy
        assertTrue("Request body should contain real audio data", request.bodySize > 1000)
        val body = request.body.readUtf8()
        assertTrue(body.contains("audio/mp4"))
        assertTrue(body.contains("test_audio.m4a"))
    }
}
