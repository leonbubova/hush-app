package com.hush.app.transcription

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class TextPostProcessor(
    private val config: PostProcessorConfig,
    private val endpointOverride: String? = null,
) {
    companion object {
        private const val TAG = "TextPostProcessor"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private val client = HttpClientFactory.createPostProcessorClient()
    }

    suspend fun process(rawText: String): String {
        if (!config.enabled) return rawText
        if (config.apiKey.isBlank()) {
            Log.w(TAG, "Post-processing enabled but no API key configured")
            return rawText
        }

        return try {
            withContext(Dispatchers.IO) {
                when (config.apiType) {
                    PostProcessorConfig.API_TYPE_ANTHROPIC -> callAnthropic(rawText)
                    PostProcessorConfig.API_TYPE_OPENAI -> callOpenAi(rawText)
                    else -> {
                        Log.w(TAG, "Unknown API type: ${config.apiType}")
                        rawText
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Post-processing failed, returning raw text", e)
            rawText
        }
    }

    private fun buildSystemPrompt(): String {
        return PostProcessorConfig.SYSTEM_PREFIX + config.systemPrompt
    }

    private fun callAnthropic(rawText: String): String {
        val baseUrl = (endpointOverride ?: PostProcessorConfig.baseUrlForType(config.apiType)).trimEnd('/')
        val url = "$baseUrl/messages"

        val body = JSONObject().apply {
            put("model", config.model)
            put("max_tokens", 2048)
            put("temperature", 0.3)
            put("system", buildSystemPrompt())
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", rawText)
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .header("x-api-key", config.apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .build()

        Log.i(TAG, "Anthropic request: $url model=${config.model}")
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string()?.take(200) ?: ""
            Log.w(TAG, "Anthropic API error: ${response.code} url=$url body=$errorBody")
            return rawText
        }

        val responseBody = response.body?.string() ?: return rawText
        val json = JSONObject(responseBody)
        val content = json.getJSONArray("content")
        if (content.length() == 0) return rawText

        val text = content.getJSONObject(0).getString("text")
        if (text.isNotBlank()) {
            Log.i(TAG, "Anthropic success: '${rawText.take(50)}' → '${text.trim().take(50)}'")
            return text.trim()
        }
        return rawText
    }

    private fun callOpenAi(rawText: String): String {
        val baseUrl = (endpointOverride ?: PostProcessorConfig.baseUrlForType(config.apiType)).trimEnd('/')
        val url = "$baseUrl/chat/completions"

        val body = JSONObject().apply {
            put("model", config.model)
            put("max_tokens", 2048)
            put("temperature", 0.3)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", buildSystemPrompt())
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", rawText)
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("content-type", "application/json")
            .build()

        Log.i(TAG, "OpenAI request: $url model=${config.model}")
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string()?.take(200) ?: ""
            Log.w(TAG, "OpenAI-compatible API error: ${response.code} url=$url body=$errorBody")
            return rawText
        }

        val responseBody = response.body?.string() ?: return rawText
        val json = JSONObject(responseBody)
        val choices = json.getJSONArray("choices")
        if (choices.length() == 0) return rawText

        val text = choices.getJSONObject(0).getJSONObject("message").getString("content")
        if (text.isNotBlank()) {
            Log.i(TAG, "OpenAI success: '${rawText.take(50)}' → '${text.trim().take(50)}'")
            return text.trim()
        }
        return rawText
    }
}
