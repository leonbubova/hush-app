package com.hush.app

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for VoxtralApi transcription logic.
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

        // Create a small temp file to act as the "audio" file
        testFile = File.createTempFile("test_audio", ".m4a")
        testFile.writeBytes(byteArrayOf(0, 1, 2, 3))
    }

    @After
    fun tearDown() {
        server.shutdown()
        testFile.delete()
    }

    @Test
    fun `successful transcription returns Success with text`() {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"text": "Hello world"}"""))

        val result = VoxtralApi.transcribe(testFile, "test-key", server.url("/").toString())

        assertTrue(result is TranscribeResult.Success)
        assertEquals("Hello world", (result as TranscribeResult.Success).text)
    }

    @Test
    fun `401 returns Error with invalid API key message`() {
        server.enqueue(MockResponse()
            .setResponseCode(401)
            .setBody("""{"error": "unauthorized"}"""))

        val result = VoxtralApi.transcribe(testFile, "bad-key", server.url("/").toString())

        assertTrue(result is TranscribeResult.Error)
        val error = result as TranscribeResult.Error
        assertEquals(401, error.code)
        assertEquals("Invalid API key", error.message)
    }

    @Test
    fun `429 returns Error with rate limit message`() {
        server.enqueue(MockResponse()
            .setResponseCode(429)
            .setBody(""))

        val result = VoxtralApi.transcribe(testFile, "test-key", server.url("/").toString())

        assertTrue(result is TranscribeResult.Error)
        val error = result as TranscribeResult.Error
        assertEquals(429, error.code)
        assertTrue(error.message.contains("Rate limited"))
    }

    @Test
    fun `500 returns Error with server error message`() {
        server.enqueue(MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error"))

        val result = VoxtralApi.transcribe(testFile, "test-key", server.url("/").toString())

        assertTrue(result is TranscribeResult.Error)
        val error = result as TranscribeResult.Error
        assertEquals(500, error.code)
        assertTrue(error.message.contains("Server error"))
    }

    @Test
    fun `503 returns Error with server error message`() {
        server.enqueue(MockResponse()
            .setResponseCode(503)
            .setBody("Service Unavailable"))

        val result = VoxtralApi.transcribe(testFile, "test-key", server.url("/").toString())

        assertTrue(result is TranscribeResult.Error)
        val error = result as TranscribeResult.Error
        assertEquals(503, error.code)
        assertTrue(error.message.contains("Server error"))
    }

    @Test
    fun `network error returns Error with no internet message`() {
        // Shut down server to simulate network failure
        server.shutdown()

        val result = VoxtralApi.transcribe(testFile, "test-key", "http://localhost:1/v1/audio/transcriptions")

        assertTrue(result is TranscribeResult.Error)
        val error = result as TranscribeResult.Error
        assertNull(error.code)
    }

    @Test
    fun `empty response body returns Error`() {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(""))

        val result = VoxtralApi.transcribe(testFile, "test-key", server.url("/").toString())

        // Empty body can't be parsed as JSON — should be an error
        assertTrue(result is TranscribeResult.Error)
    }

    @Test
    fun `malformed JSON returns Error`() {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("not json at all"))

        val result = VoxtralApi.transcribe(testFile, "test-key", server.url("/").toString())

        assertTrue(result is TranscribeResult.Error)
    }

    @Test
    fun `request includes correct auth header`() {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"text": "test"}"""))

        VoxtralApi.transcribe(testFile, "my-secret-key", server.url("/").toString())

        val request = server.takeRequest()
        assertEquals("Bearer my-secret-key", request.getHeader("Authorization"))
    }

    @Test
    fun `request uses multipart form with model and file`() {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"text": "test"}"""))

        VoxtralApi.transcribe(testFile, "test-key", server.url("/").toString())

        val request = server.takeRequest()
        val contentType = request.getHeader("Content-Type") ?: ""
        assertTrue(contentType.contains("multipart/form-data"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("voxtral-mini-latest"))
    }
}
