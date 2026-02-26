package com.hush.app

import com.hush.app.transcription.LocalProvider
import com.hush.app.transcription.ProviderConfig
import com.hush.app.transcription.TranscribeResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LocalProviderTest {

    private fun provider(model: String = "whisper-tiny-en-q4") = LocalProvider(
        config = ProviderConfig.Local(model = model),
        context = RuntimeEnvironment.getApplication(),
    )

    @Test
    fun `provider has correct metadata`() {
        val p = provider()
        assertEquals(ProviderConfig.PROVIDER_LOCAL, p.id)
        assertEquals("Local (On-Device)", p.displayName)
        assertFalse(p.requiresNetwork)
    }

    @Test
    fun `transcribe returns error when model not downloaded`() = runBlocking {
        val p = provider()
        val tempFile = java.io.File.createTempFile("test", ".m4a").apply {
            writeBytes(byteArrayOf(0, 1, 2, 3))
            deleteOnExit()
        }

        val result = p.transcribe(tempFile)

        assertTrue("Expected Error, got $result", result is TranscribeResult.Error)
        val error = result as TranscribeResult.Error
        assertTrue(error.message.contains("not downloaded"))
    }

    @Test
    fun `transcribe returns error for unknown model`() = runBlocking {
        val p = provider(model = "nonexistent-model")
        val tempFile = java.io.File.createTempFile("test", ".m4a").apply {
            writeBytes(byteArrayOf(0, 1, 2, 3))
            deleteOnExit()
        }

        val result = p.transcribe(tempFile)

        assertTrue("Expected Error, got $result", result is TranscribeResult.Error)
        val error = result as TranscribeResult.Error
        assertTrue(error.message.contains("not downloaded"))
    }

    @Test
    fun `id is PROVIDER_LOCAL constant`() {
        assertEquals("local", provider().id)
    }

    @Test
    fun `displayName is correct`() {
        assertEquals("Local (On-Device)", provider().displayName)
    }

    @Test
    fun `requiresNetwork is false`() {
        assertFalse(provider().requiresNetwork)
    }
}
