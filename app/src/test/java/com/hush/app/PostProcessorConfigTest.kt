package com.hush.app

import com.hush.app.transcription.PostProcessorConfig
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PostProcessorConfigTest {

    @Test
    fun `default config has expected values`() {
        val config = PostProcessorConfig()
        assertFalse(config.enabled)
        assertEquals(PostProcessorConfig.API_TYPE_ANTHROPIC, config.apiType)
        assertEquals("", config.apiKey)
        assertEquals(PostProcessorConfig.DEFAULT_ANTHROPIC_URL, config.baseUrl)
        assertEquals(PostProcessorConfig.DEFAULT_ANTHROPIC_MODEL, config.model)
        assertEquals(PostProcessorConfig.DEFAULT_USER_INSTRUCTIONS, config.systemPrompt)
    }

    @Test
    fun `JSON round-trip preserves all fields`() {
        val original = PostProcessorConfig(
            enabled = true,
            apiType = PostProcessorConfig.API_TYPE_OPENAI,
            apiKey = "sk-test-key-123",
            baseUrl = "https://custom.api.com/v1",
            model = "gpt-4o-mini",
            systemPrompt = "Custom prompt here",
        )

        val json = original.toJson()
        val restored = PostProcessorConfig.fromJson(json)

        assertEquals(original, restored)
    }

    @Test
    fun `fromJson handles missing fields with defaults`() {
        val json = JSONObject().apply {
            put("enabled", true)
            put("apiKey", "key123")
        }

        val config = PostProcessorConfig.fromJson(json)

        assertTrue(config.enabled)
        assertEquals("key123", config.apiKey)
        assertEquals(PostProcessorConfig.API_TYPE_ANTHROPIC, config.apiType)
        assertEquals(PostProcessorConfig.DEFAULT_ANTHROPIC_URL, config.baseUrl)
        assertEquals(PostProcessorConfig.DEFAULT_ANTHROPIC_MODEL, config.model)
        assertEquals(PostProcessorConfig.DEFAULT_USER_INSTRUCTIONS, config.systemPrompt)
    }

    @Test
    fun `fromJson handles empty JSON object`() {
        val config = PostProcessorConfig.fromJson(JSONObject())

        assertFalse(config.enabled)
        assertEquals("", config.apiKey)
    }

    @Test
    fun `toJson produces valid JSON with all fields`() {
        val config = PostProcessorConfig(
            enabled = true,
            apiType = PostProcessorConfig.API_TYPE_ANTHROPIC,
            apiKey = "test-key",
            baseUrl = "https://api.anthropic.com/v1",
            model = "claude-haiku-4-5-20251001",
            systemPrompt = "Fix text",
        )

        val json = config.toJson()

        assertTrue(json.getBoolean("enabled"))
        assertEquals("anthropic", json.getString("apiType"))
        assertEquals("test-key", json.getString("apiKey"))
        assertEquals("https://api.anthropic.com/v1", json.getString("baseUrl"))
        assertEquals("claude-haiku-4-5-20251001", json.getString("model"))
        assertEquals("Fix text", json.getString("systemPrompt"))
    }
}
