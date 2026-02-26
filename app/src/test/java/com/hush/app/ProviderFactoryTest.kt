package com.hush.app

import com.hush.app.transcription.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProviderFactoryTest {

    @Test
    fun `create returns VoxtralProvider for voxtral ID`() {
        val config = ProviderConfig.Voxtral(apiKey = "key")
        val provider = ProviderFactory.create(ProviderConfig.PROVIDER_VOXTRAL, config)

        assertTrue(provider is VoxtralProvider)
        assertEquals(ProviderConfig.PROVIDER_VOXTRAL, provider.id)
    }

    @Test
    fun `create returns OpenAiWhisperProvider for openai ID`() {
        val config = ProviderConfig.OpenAiWhisper(apiKey = "key")
        val provider = ProviderFactory.create(ProviderConfig.PROVIDER_OPENAI, config)

        assertTrue(provider is OpenAiWhisperProvider)
        assertEquals(ProviderConfig.PROVIDER_OPENAI, provider.id)
    }

    @Test
    fun `create returns GroqProvider for groq ID`() {
        val config = ProviderConfig.Groq(apiKey = "key")
        val provider = ProviderFactory.create(ProviderConfig.PROVIDER_GROQ, config)

        assertTrue(provider is GroqProvider)
        assertEquals(ProviderConfig.PROVIDER_GROQ, provider.id)
    }

    @Test
    fun `create falls back to VoxtralProvider for unknown ID`() {
        val config = ProviderConfig.Voxtral()
        val provider = ProviderFactory.create("unknown", config)

        assertTrue(provider is VoxtralProvider)
    }

    @Test
    fun `create returns LocalProvider for local ID with context`() {
        val config = ProviderConfig.Local()
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val provider = ProviderFactory.create(ProviderConfig.PROVIDER_LOCAL, config, context)

        assertTrue(provider is LocalProvider)
        assertEquals(ProviderConfig.PROVIDER_LOCAL, provider.id)
        assertFalse(provider.requiresNetwork)
    }

    @Test
    fun `allProviderIds contains all providers including local`() {
        val ids = ProviderFactory.allProviderIds
        assertTrue(ids.contains(ProviderConfig.PROVIDER_VOXTRAL))
        assertTrue(ids.contains(ProviderConfig.PROVIDER_OPENAI))
        assertTrue(ids.contains(ProviderConfig.PROVIDER_GROQ))
        assertTrue(ids.contains(ProviderConfig.PROVIDER_LOCAL))
    }

    @Test
    fun `displayName returns correct names for all providers`() {
        assertEquals("Voxtral (Mistral)", ProviderFactory.displayName(ProviderConfig.PROVIDER_VOXTRAL))
        assertEquals("OpenAI Whisper", ProviderFactory.displayName(ProviderConfig.PROVIDER_OPENAI))
        assertEquals("Groq", ProviderFactory.displayName(ProviderConfig.PROVIDER_GROQ))
        assertEquals("Local (On-Device)", ProviderFactory.displayName(ProviderConfig.PROVIDER_LOCAL))
    }

    @Test
    fun `displayName returns raw ID for unknown provider`() {
        assertEquals("some_unknown", ProviderFactory.displayName("some_unknown"))
    }

    @Test
    fun `all providers implement TranscriptionProvider`() {
        val configs = mapOf(
            ProviderConfig.PROVIDER_VOXTRAL to ProviderConfig.Voxtral(),
            ProviderConfig.PROVIDER_OPENAI to ProviderConfig.OpenAiWhisper(),
            ProviderConfig.PROVIDER_GROQ to ProviderConfig.Groq(),
        )
        configs.forEach { (id, config) ->
            val provider = ProviderFactory.create(id, config)
            assertTrue("$id should implement TranscriptionProvider", provider is TranscriptionProvider)
            assertTrue("$id should require network", provider.requiresNetwork)
            assertTrue("$id displayName should not be blank", provider.displayName.isNotBlank())
        }
    }
}
