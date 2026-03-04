package com.hush.app

import com.hush.app.transcription.PostProcessorConfig
import com.hush.app.transcription.TextPostProcessor
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TextPostProcessorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun processor(
        enabled: Boolean = true,
        apiType: String = PostProcessorConfig.API_TYPE_ANTHROPIC,
        apiKey: String = "test-key",
        model: String = "test-model",
        systemPrompt: String = "Fix the text",
    ) = TextPostProcessor(
        config = PostProcessorConfig(
            enabled = enabled,
            apiType = apiType,
            apiKey = apiKey,
            model = model,
            systemPrompt = systemPrompt,
        ),
        endpointOverride = server.url("/v1").toString(),
    )

    // --- Disabled / no key ---

    @Test
    fun `disabled config returns raw text without making request`() = runBlocking {
        val result = processor(enabled = false).process("um hello world")
        assertEquals("um hello world", result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `blank API key returns raw text without making request`() = runBlocking {
        val result = processor(apiKey = "").process("um hello world")
        assertEquals("um hello world", result)
        assertEquals(0, server.requestCount)
    }

    // --- Anthropic API ---

    @Test
    fun `anthropic success returns enhanced text`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"content": [{"type": "text", "text": "Hello, world."}]}"""))

        val result = processor().process("um hello world")

        assertEquals("Hello, world.", result)
    }

    @Test
    fun `anthropic sends correct headers`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"content": [{"type": "text", "text": "test"}]}"""))

        processor(apiKey = "sk-ant-test123").process("hello")

        val request = server.takeRequest()
        assertEquals("sk-ant-test123", request.getHeader("x-api-key"))
        assertEquals("2023-06-01", request.getHeader("anthropic-version"))
        assertTrue(request.path!!.endsWith("/messages"))
    }

    @Test
    fun `anthropic sends correct body with system prompt`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"content": [{"type": "text", "text": "raw text"}]}"""))

        processor(model = "claude-haiku-4-5-20251001", systemPrompt = "Clean up").process("raw text")

        val request = server.takeRequest()
        val body = org.json.JSONObject(request.body.readUtf8())
        assertEquals("claude-haiku-4-5-20251001", body.getString("model"))
        assertEquals(2048, body.getInt("max_tokens"))
        assertEquals(0.0, body.getDouble("temperature"), 0.001)
        assertEquals("Clean up", body.getString("system"))
        val messages = body.getJSONArray("messages")
        assertEquals(1, messages.length())
        assertEquals("user", messages.getJSONObject(0).getString("role"))
        val userContent = messages.getJSONObject(0).getString("content")
        assertTrue("User content should be wrapped in transcription tags", userContent.contains("<transcription>"))
        assertTrue("User content should contain raw text", userContent.contains("raw text"))
        assertTrue("User content should close transcription tags", userContent.contains("</transcription>"))
    }

    @Test
    fun `anthropic HTTP error falls back to raw text`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val result = processor().process("raw text here")

        assertEquals("raw text here", result)
    }

    @Test
    fun `anthropic empty content array falls back to raw text`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"content": []}"""))

        val result = processor().process("raw text")

        assertEquals("raw text", result)
    }

    // --- OpenAI-compatible API ---

    @Test
    fun `openai success returns enhanced text`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"choices": [{"message": {"content": "Hello, world."}}]}"""))

        val result = processor(apiType = PostProcessorConfig.API_TYPE_OPENAI).process("um hello world")

        assertEquals("Hello, world.", result)
    }

    @Test
    fun `openai sends correct headers`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"choices": [{"message": {"content": "test"}}]}"""))

        processor(apiType = PostProcessorConfig.API_TYPE_OPENAI, apiKey = "gsk-test456").process("hello")

        val request = server.takeRequest()
        assertEquals("Bearer gsk-test456", request.getHeader("Authorization"))
        assertTrue(request.path!!.endsWith("/chat/completions"))
    }

    @Test
    fun `openai sends system prompt as message`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"choices": [{"message": {"content": "input"}}]}"""))

        processor(apiType = PostProcessorConfig.API_TYPE_OPENAI, systemPrompt = "Be helpful").process("input")

        val request = server.takeRequest()
        val body = org.json.JSONObject(request.body.readUtf8())
        assertEquals(0.0, body.getDouble("temperature"), 0.001)
        val messages = body.getJSONArray("messages")
        assertEquals(2, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("Be helpful", messages.getJSONObject(0).getString("content"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        val userContent = messages.getJSONObject(1).getString("content")
        assertTrue("User content should be wrapped in transcription tags", userContent.contains("<transcription>"))
        assertTrue("User content should contain raw text", userContent.contains("input"))
    }

    @Test
    fun `openai HTTP error falls back to raw text`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        val result = processor(apiType = PostProcessorConfig.API_TYPE_OPENAI).process("raw text here")

        assertEquals("raw text here", result)
    }

    @Test
    fun `openai empty choices falls back to raw text`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"choices": []}"""))

        val result = processor(apiType = PostProcessorConfig.API_TYPE_OPENAI).process("raw text")

        assertEquals("raw text", result)
    }

    // --- Network errors ---

    @Test
    fun `network error falls back to raw text`() = runBlocking {
        server.shutdown()

        val p = TextPostProcessor(
            config = PostProcessorConfig(
                enabled = true,
                apiKey = "key",
            ),
            endpointOverride = "http://localhost:1/v1",
        )
        val result = p.process("raw text")

        assertEquals("raw text", result)
    }

    // --- Edge cases ---

    @Test
    fun `blank response text falls back to raw text`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"content": [{"type": "text", "text": "   "}]}"""))

        val result = processor().process("raw text")

        assertEquals("raw text", result)
    }

    @Test
    fun `response text is trimmed`() = runBlocking {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"content": [{"type": "text", "text": "  Hello, world.  "}]}"""))

        val result = processor().process("hello world")

        assertEquals("Hello, world.", result)
    }

}
