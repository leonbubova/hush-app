package com.hush.app.transcription

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TextPostProcessor(
    private val config: PostProcessorConfig,
    private val endpointOverride: String? = null,
) {
    companion object {
        private const val TAG = "TextPostProcessor"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
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

    private fun callAnthropic(rawText: String): String {
        val baseUrl = (endpointOverride ?: config.baseUrl).trimEnd('/')
        val url = "$baseUrl/messages"

        val body = JSONObject().apply {
            put("model", config.model)
            put("max_tokens", 2048)
            put("temperature", 0.3)
            put("system", config.systemPrompt)
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

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.w(TAG, "Anthropic API error: ${response.code}")
            return rawText
        }

        val responseBody = response.body?.string() ?: return rawText
        val json = JSONObject(responseBody)
        val content = json.getJSONArray("content")
        if (content.length() == 0) return rawText

        val text = content.getJSONObject(0).getString("text")
        return if (text.isNotBlank()) text.trim() else rawText
    }

    private fun callOpenAi(rawText: String): String {
        val baseUrl = (endpointOverride ?: config.baseUrl).trimEnd('/')
        val url = "$baseUrl/chat/completions"

        val body = JSONObject().apply {
            put("model", config.model)
            put("max_tokens", 2048)
            put("temperature", 0.3)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", config.systemPrompt)
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

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.w(TAG, "OpenAI-compatible API error: ${response.code}")
            return rawText
        }

        val responseBody = response.body?.string() ?: return rawText
        val json = JSONObject(responseBody)
        val choices = json.getJSONArray("choices")
        if (choices.length() == 0) return rawText

        val text = choices.getJSONObject(0).getJSONObject("message").getString("content")
        return if (text.isNotBlank()) text.trim() else rawText
    }
}
