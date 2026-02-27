package com.hush.app.transcription

import org.json.JSONObject

sealed class ProviderConfig {

    abstract fun toJson(): JSONObject

    data class Voxtral(
        val apiKey: String = "",
        val model: String = "voxtral-mini-latest",
        val endpoint: String = "https://api.mistral.ai/v1/audio/transcriptions",
    ) : ProviderConfig() {
        override fun toJson() = JSONObject().apply {
            put("apiKey", apiKey)
            put("model", model)
            put("endpoint", endpoint)
        }
    }

    data class OpenAiWhisper(
        val apiKey: String = "",
        val model: String = "whisper-1",
        val language: String = "",
    ) : ProviderConfig() {
        override fun toJson() = JSONObject().apply {
            put("apiKey", apiKey)
            put("model", model)
            put("language", language)
        }
    }

    data class Groq(
        val apiKey: String = "",
        val model: String = "whisper-large-v3-turbo",
    ) : ProviderConfig() {
        override fun toJson() = JSONObject().apply {
            put("apiKey", apiKey)
            put("model", model)
        }
    }

    data class Local(
        val model: String = "whisper-tiny-en-q4",
        val language: String = "",
    ) : ProviderConfig() {
        override fun toJson() = JSONObject().apply {
            put("model", model)
            put("language", language)
        }
    }

    data class Moonshine(
        val model: String = "tiny-streaming-en",
    ) : ProviderConfig() {
        override fun toJson() = JSONObject().apply {
            put("model", model)
        }
    }

    companion object {
        const val PROVIDER_VOXTRAL = "voxtral"
        const val PROVIDER_OPENAI = "openai"
        const val PROVIDER_GROQ = "groq"
        const val PROVIDER_LOCAL = "local"
        const val PROVIDER_MOONSHINE = "moonshine"

        fun fromJson(providerId: String, json: JSONObject): ProviderConfig = when (providerId) {
            PROVIDER_VOXTRAL -> Voxtral(
                apiKey = json.optString("apiKey", ""),
                model = json.optString("model", "voxtral-mini-latest"),
                endpoint = json.optString("endpoint", "https://api.mistral.ai/v1/audio/transcriptions"),
            )
            PROVIDER_OPENAI -> OpenAiWhisper(
                apiKey = json.optString("apiKey", ""),
                model = json.optString("model", "whisper-1"),
                language = json.optString("language", ""),
            )
            PROVIDER_GROQ -> Groq(
                apiKey = json.optString("apiKey", ""),
                model = json.optString("model", "whisper-large-v3-turbo"),
            )
            PROVIDER_LOCAL -> Local(
                model = json.optString("model", "whisper-tiny-en-q4"),
                language = json.optString("language", ""),
            )
            PROVIDER_MOONSHINE -> Moonshine(
                model = json.optString("model", "tiny-streaming-en"),
            )
            else -> Voxtral()
        }

        fun default(providerId: String): ProviderConfig = when (providerId) {
            PROVIDER_VOXTRAL -> Voxtral()
            PROVIDER_OPENAI -> OpenAiWhisper()
            PROVIDER_GROQ -> Groq()
            PROVIDER_LOCAL -> Local()
            PROVIDER_MOONSHINE -> Moonshine()
            else -> Voxtral()
        }
    }
}
