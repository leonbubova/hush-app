package com.hush.app

import com.hush.app.transcription.ProviderConfig
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProviderConfigTest {

    // --- Voxtral round-trip ---

    @Test
    fun `Voxtral config round-trips through JSON`() {
        val original = ProviderConfig.Voxtral(
            apiKey = "sk-test-123",
            model = "voxtral-mini-latest",
            endpoint = "https://custom.endpoint/v1",
        )
        val json = original.toJson()
        val restored = ProviderConfig.fromJson(ProviderConfig.PROVIDER_VOXTRAL, json)

        assertEquals(original, restored)
    }

    @Test
    fun `Voxtral default has sensible values`() {
        val config = ProviderConfig.default(ProviderConfig.PROVIDER_VOXTRAL) as ProviderConfig.Voxtral
        assertEquals("", config.apiKey)
        assertEquals("voxtral-mini-latest", config.model)
        assertTrue(config.endpoint.contains("mistral.ai"))
    }

    @Test
    fun `Voxtral toJson includes all fields`() {
        val config = ProviderConfig.Voxtral(apiKey = "key", model = "m", endpoint = "https://e")
        val json = config.toJson()
        assertEquals("key", json.getString("apiKey"))
        assertEquals("m", json.getString("model"))
        assertEquals("https://e", json.getString("endpoint"))
    }

    // --- OpenAI round-trip ---

    @Test
    fun `OpenAI config round-trips through JSON`() {
        val original = ProviderConfig.OpenAiWhisper(
            apiKey = "sk-openai-456",
            model = "whisper-1",
            language = "de",
        )
        val json = original.toJson()
        val restored = ProviderConfig.fromJson(ProviderConfig.PROVIDER_OPENAI, json)

        assertEquals(original, restored)
    }

    @Test
    fun `OpenAI default has sensible values`() {
        val config = ProviderConfig.default(ProviderConfig.PROVIDER_OPENAI) as ProviderConfig.OpenAiWhisper
        assertEquals("", config.apiKey)
        assertEquals("whisper-1", config.model)
        assertEquals("", config.language)
    }

    // --- Groq round-trip ---

    @Test
    fun `Groq config round-trips through JSON`() {
        val original = ProviderConfig.Groq(
            apiKey = "gsk-groq-789",
            model = "whisper-large-v3",
        )
        val json = original.toJson()
        val restored = ProviderConfig.fromJson(ProviderConfig.PROVIDER_GROQ, json)

        assertEquals(original, restored)
    }

    @Test
    fun `Groq default has sensible values`() {
        val config = ProviderConfig.default(ProviderConfig.PROVIDER_GROQ) as ProviderConfig.Groq
        assertEquals("", config.apiKey)
        assertEquals("whisper-large-v3-turbo", config.model)
    }

    // --- Local round-trip ---

    @Test
    fun `Local config round-trips through JSON`() {
        val original = ProviderConfig.Local(
            model = "whisper-tiny-en",
            language = "en",
        )
        val json = original.toJson()
        val restored = ProviderConfig.fromJson(ProviderConfig.PROVIDER_LOCAL, json)

        assertEquals(original, restored)
    }

    @Test
    fun `Local default has sensible values`() {
        val config = ProviderConfig.default(ProviderConfig.PROVIDER_LOCAL) as ProviderConfig.Local
        assertEquals("whisper-tiny-en-q4", config.model)
        assertEquals("", config.language)
    }

    // --- Moonshine round-trip ---

    @Test
    fun `Moonshine config round-trips through JSON`() {
        val original = ProviderConfig.Moonshine(
            model = "tiny-streaming-en",
        )
        val json = original.toJson()
        val restored = ProviderConfig.fromJson(ProviderConfig.PROVIDER_MOONSHINE, json)

        assertEquals(original, restored)
    }

    @Test
    fun `Moonshine default has sensible values`() {
        val config = ProviderConfig.default(ProviderConfig.PROVIDER_MOONSHINE) as ProviderConfig.Moonshine
        assertEquals("tiny-streaming-en", config.model)
    }

    @Test
    fun `Moonshine toJson includes model`() {
        val config = ProviderConfig.Moonshine(model = "tiny-streaming-en")
        val json = config.toJson()
        assertEquals("tiny-streaming-en", json.getString("model"))
    }

    // --- VoxtralRealtime round-trip ---

    @Test
    fun `VoxtralRealtime config round-trips through JSON`() {
        val original = ProviderConfig.VoxtralRealtime(
            apiKey = "sk-realtime-test",
            model = "voxtral-mini-transcribe-realtime-2602",
            endpoint = "wss://custom.endpoint/v1/realtime",
        )
        val json = original.toJson()
        val restored = ProviderConfig.fromJson(ProviderConfig.PROVIDER_VOXTRAL_REALTIME, json)

        assertEquals(original, restored)
    }

    @Test
    fun `VoxtralRealtime default has sensible values`() {
        val config = ProviderConfig.default(ProviderConfig.PROVIDER_VOXTRAL_REALTIME) as ProviderConfig.VoxtralRealtime
        assertEquals("", config.apiKey)
        assertEquals("voxtral-mini-transcribe-realtime-2602", config.model)
        assertTrue(config.endpoint.contains("mistral.ai"))
        assertTrue(config.endpoint.contains("/audio/transcriptions/realtime"))
    }

    @Test
    fun `VoxtralRealtime toJson includes all fields`() {
        val config = ProviderConfig.VoxtralRealtime(apiKey = "key", model = "m", endpoint = "wss://e")
        val json = config.toJson()
        assertEquals("key", json.getString("apiKey"))
        assertEquals("m", json.getString("model"))
        assertEquals("wss://e", json.getString("endpoint"))
    }

    // --- Edge cases ---

    @Test
    fun `fromJson with missing fields uses defaults`() {
        val emptyJson = JSONObject()
        val config = ProviderConfig.fromJson(ProviderConfig.PROVIDER_VOXTRAL, emptyJson) as ProviderConfig.Voxtral

        assertEquals("", config.apiKey)
        assertEquals("voxtral-mini-latest", config.model)
        assertTrue(config.endpoint.contains("mistral.ai"))
    }

    @Test
    fun `fromJson with unknown provider ID returns default Voxtral`() {
        val json = JSONObject()
        val config = ProviderConfig.fromJson("unknown_provider", json)

        assertTrue(config is ProviderConfig.Voxtral)
    }

    @Test
    fun `default with unknown provider ID returns Voxtral`() {
        val config = ProviderConfig.default("nonexistent")
        assertTrue(config is ProviderConfig.Voxtral)
    }

    @Test
    fun `provider ID constants are distinct`() {
        val ids = setOf(
            ProviderConfig.PROVIDER_VOXTRAL,
            ProviderConfig.PROVIDER_OPENAI,
            ProviderConfig.PROVIDER_GROQ,
            ProviderConfig.PROVIDER_LOCAL,
            ProviderConfig.PROVIDER_MOONSHINE,
            ProviderConfig.PROVIDER_VOXTRAL_REALTIME,
        )
        assertEquals(6, ids.size)
    }

    @Test
    fun `OpenAI fromJson with blank language preserves empty string`() {
        val json = JSONObject().apply {
            put("apiKey", "k")
            put("model", "whisper-1")
            put("language", "")
        }
        val config = ProviderConfig.fromJson(ProviderConfig.PROVIDER_OPENAI, json) as ProviderConfig.OpenAiWhisper
        assertEquals("", config.language)
    }
}
