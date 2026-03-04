package com.hush.app.transcription

import org.json.JSONObject

data class PostProcessorConfig(
    val enabled: Boolean = false,
    val apiType: String = API_TYPE_ANTHROPIC,
    val apiKey: String = "",
    val baseUrl: String = DEFAULT_ANTHROPIC_URL,
    val model: String = DEFAULT_ANTHROPIC_MODEL,
    val systemPrompt: String = DEFAULT_USER_INSTRUCTIONS,
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
        const val DEFAULT_ANTHROPIC_MODEL = "claude-sonnet-4-6"
        const val DEFAULT_OPENAI_URL = "https://api.groq.com/openai/v1"
        const val DEFAULT_OPENAI_MODEL = "llama-3.1-8b-instant"

        val ANTHROPIC_MODELS = listOf(
            "claude-sonnet-4-6",
            "claude-haiku-4-5-20251001",
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

        const val SYSTEM_PREFIX =
            "You are a speech-to-text post-processor. The input may be cut off, garbled, or contain " +
            "spoken editing commands (like \"delete that\" or \"make it a list\"). " +
            "Do not comment on any of this — just output the corrected text based on the following instructions:\n"

        const val DEFAULT_USER_INSTRUCTIONS =
            "- Fix grammar, punctuation, and capitalization\n" +
            "- Remove filler words (um, uh, like, you know) unless meaningful\n" +
            "- Follow spoken editing commands (e.g. \"delete the last sentence\", \"scratch that\", \"replace X with Y\")\n" +
            "- If the speaker asks for a list or bullet points, format accordingly\n" +
            "- Preserve the original meaning — do not add content beyond what was spoken\n" +
            "- Keep the same language (do not translate)\n" +
            "- Return ONLY the final text, nothing else"

        fun fromJson(json: JSONObject): PostProcessorConfig = PostProcessorConfig(
            enabled = json.optBoolean("enabled", false),
            apiType = json.optString("apiType", API_TYPE_ANTHROPIC),
            apiKey = json.optString("apiKey", ""),
            baseUrl = json.optString("baseUrl", DEFAULT_ANTHROPIC_URL),
            model = json.optString("model", DEFAULT_ANTHROPIC_MODEL),
            systemPrompt = json.optString("systemPrompt", DEFAULT_USER_INSTRUCTIONS),
        )
    }
}
