package com.hush.app.transcription

import org.json.JSONObject

data class PostProcessorConfig(
    val enabled: Boolean = false,
    val apiType: String = API_TYPE_ANTHROPIC,
    val apiKey: String = "",
    val baseUrl: String = DEFAULT_ANTHROPIC_URL,
    val model: String = DEFAULT_ANTHROPIC_MODEL,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("apiType", apiType)
        put("apiKey", apiKey)
        put("baseUrl", baseUrl)
        put("model", model)
        put("systemPrompt", systemPrompt)
    }

    companion object {
        const val API_TYPE_ANTHROPIC = "anthropic"
        const val API_TYPE_OPENAI = "openai"

        const val DEFAULT_ANTHROPIC_URL = "https://api.anthropic.com/v1"
        const val DEFAULT_ANTHROPIC_MODEL = "claude-haiku-4-5-20251001"
        const val DEFAULT_OPENAI_URL = "https://api.groq.com/openai/v1"
        const val DEFAULT_OPENAI_MODEL = "llama-3.1-8b-instant"

        val ANTHROPIC_MODELS = listOf(
            "claude-haiku-4-5-20251001",
            "claude-sonnet-4-6",
        )
        val OPENAI_MODELS = listOf(
            "llama-3.1-8b-instant",
            "llama-3.3-70b-versatile",
            "gemma2-9b-it",
        )

        fun baseUrlForType(apiType: String): String = when (apiType) {
            API_TYPE_ANTHROPIC -> DEFAULT_ANTHROPIC_URL
            API_TYPE_OPENAI -> DEFAULT_OPENAI_URL
            else -> DEFAULT_ANTHROPIC_URL
        }

        fun modelsForType(apiType: String): List<String> = when (apiType) {
            API_TYPE_ANTHROPIC -> ANTHROPIC_MODELS
            API_TYPE_OPENAI -> OPENAI_MODELS
            else -> ANTHROPIC_MODELS
        }

        const val DEFAULT_SYSTEM_PROMPT =
            "You are a speech-to-text post-processor. Clean up the raw transcription:\n" +
            "- Fix grammar, punctuation, and capitalization\n" +
            "- Remove filler words (um, uh, like, you know) unless meaningful\n" +
            "- Preserve the original meaning exactly — do not add, remove, or rephrase\n" +
            "- Keep the same language (do not translate)\n" +
            "- Return ONLY the corrected text, nothing else"

        fun fromJson(json: JSONObject): PostProcessorConfig = PostProcessorConfig(
            enabled = json.optBoolean("enabled", false),
            apiType = json.optString("apiType", API_TYPE_ANTHROPIC),
            apiKey = json.optString("apiKey", ""),
            baseUrl = json.optString("baseUrl", DEFAULT_ANTHROPIC_URL),
            model = json.optString("model", DEFAULT_ANTHROPIC_MODEL),
            systemPrompt = json.optString("systemPrompt", DEFAULT_SYSTEM_PROMPT),
        )
    }
}
