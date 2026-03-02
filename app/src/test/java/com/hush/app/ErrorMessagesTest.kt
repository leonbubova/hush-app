package com.hush.app

import com.hush.app.transcription.ErrorMessages
import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorMessagesTest {

    private val emDash = "\u2014"

    @Test
    fun `noApiKey includes provider name and em dash`() {
        val msg = ErrorMessages.noApiKey("OpenAI")
        assertTrue(msg.contains(emDash))
        assertTrue(msg.contains("OpenAI"))
        assertTrue(msg.contains("Settings"))
    }

    @Test
    fun `forHttpError 401 includes Invalid API key and provider`() {
        val msg = ErrorMessages.forHttpError(401, null, "Groq")
        assertTrue(msg.contains(emDash))
        assertTrue(msg.contains("Invalid API key"))
        assertTrue(msg.contains("Groq"))
    }

    @Test
    fun `forHttpError 429 returns rate limited`() {
        val msg = ErrorMessages.forHttpError(429, null, "OpenAI")
        assertTrue(msg.contains(emDash))
        assertTrue(msg.contains("Rate limited"))
    }

    @Test
    fun `forHttpError 429 with insufficient_quota returns billing`() {
        val body = """{"error": {"type": "insufficient_quota", "code": "insufficient_quota"}}"""
        val msg = ErrorMessages.forHttpError(429, body, "OpenAI")
        assertTrue(msg.contains(emDash))
        assertTrue(msg.contains("billing"))
    }

    @Test
    fun `forHttpError 5xx includes server is down and provider`() {
        for (code in listOf(500, 502, 503)) {
            val msg = ErrorMessages.forHttpError(code, null, "Voxtral")
            assertTrue(msg.contains(emDash))
            assertTrue(msg.contains("Voxtral"))
            assertTrue(msg.contains("server is down"))
        }
    }

    @Test
    fun `forHttpError other code includes provider`() {
        val msg = ErrorMessages.forHttpError(403, null, "Groq")
        assertTrue(msg.contains(emDash))
        assertTrue(msg.contains("Groq"))
        assertTrue(msg.contains("request failed"))
    }

    @Test
    fun `forNetworkException UnknownHostException returns no internet`() {
        val msg = ErrorMessages.forNetworkException(UnknownHostException("test"))
        assertTrue(msg.contains(emDash))
        assertTrue(msg.contains("No internet"))
    }

    @Test
    fun `forNetworkException SocketTimeoutException returns timed out`() {
        val msg = ErrorMessages.forNetworkException(SocketTimeoutException("test"))
        assertTrue(msg.contains(emDash))
        assertTrue(msg.contains("timed out"))
    }

    @Test
    fun `forNetworkException generic exception returns connection problem`() {
        val msg = ErrorMessages.forNetworkException(RuntimeException("test"))
        assertTrue(msg.contains(emDash))
        assertTrue(msg.contains("Connection problem"))
    }

    @Test
    fun `emptyResponse includes provider name`() {
        val msg = ErrorMessages.emptyResponse("Groq")
        assertTrue(msg.contains(emDash))
        assertTrue(msg.contains("Groq"))
    }

    @Test
    fun `modelNotDownloaded includes model name`() {
        val msg = ErrorMessages.modelNotDownloaded("whisper-tiny")
        assertTrue(msg.contains(emDash))
        assertTrue(msg.contains("whisper-tiny"))
        assertTrue(msg.contains("not downloaded"))
    }

    @Test
    fun `all static messages contain em dash`() {
        val messages = listOf(
            ErrorMessages.moonshineNotDownloaded(),
            ErrorMessages.modelLoadFailed(),
            ErrorMessages.audioConversionFailed(),
            ErrorMessages.emptyAudio(),
            ErrorMessages.localTranscriptionFailed(),
            ErrorMessages.emptyTranscription(),
            ErrorMessages.micUnavailable(),
            ErrorMessages.recorderFailed(),
            ErrorMessages.unexpectedError(),
            ErrorMessages.downloadFailed(),
            ErrorMessages.exportFailed(),
            ErrorMessages.importFailed(),
        )
        for (msg in messages) {
            assertTrue("Missing em dash in: $msg", msg.contains(emDash))
        }
    }

    @Test
    fun `micUnavailable mentions permissions`() {
        assertTrue(ErrorMessages.micUnavailable().contains("permissions"))
    }

    @Test
    fun `downloadFailed mentions connection`() {
        assertTrue(ErrorMessages.downloadFailed().contains("connection"))
    }

    @Test
    fun `importFailed mentions Hush backup`() {
        assertTrue(ErrorMessages.importFailed().contains("Hush backup"))
    }

    @Test
    fun `exportFailed mentions storage permissions`() {
        assertTrue(ErrorMessages.exportFailed().contains("storage permissions"))
    }

    @Test
    fun `unexpectedError is user-friendly`() {
        val msg = ErrorMessages.unexpectedError()
        assertTrue(msg.contains("Something went wrong"))
        assertTrue(msg.contains("try again"))
    }
}
