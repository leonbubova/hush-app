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

    data class VoxtralRealtime(
        val apiKey: String = "",
        val model: String = "voxtral-mini-transcribe-realtime-2602",
        val endpoint: String = "wss://api.mistral.ai/v1/audio/transcriptions/realtime",
    ) : ProviderConfig() {
        override fun toJson() = JSONObject().apply {
            put("apiKey", apiKey)
            put("model", model)
            put("endpoint", endpoint)
        }
    }

    /** Short human-readable label for the active provider + model, e.g. "Moonshine tiny" */
    val displayLabel: String get() = when (this) {
        is Moonshine -> ModelManager.getMoonshineModelInfo(model)
            ?.displayName?.substringBefore(" (") ?: "Moonshine $model"
        is Local -> ModelManager.getModelInfo(model)
            ?.displayName?.substringBefore(" (") ?: "Local $model"
        is Voxtral -> "Voxtral · $model"
        is OpenAiWhisper -> "OpenAI · $model"
        is Groq -> "Groq · $model"
        is VoxtralRealtime -> "Voxtral RT · $model"
    }

    companion object {
        const val PROVIDER_VOXTRAL = "voxtral"
        const val PROVIDER_OPENAI = "openai"
        const val PROVIDER_GROQ = "groq"
        const val PROVIDER_LOCAL = "local"
        const val PROVIDER_MOONSHINE = "moonshine"
        const val PROVIDER_VOXTRAL_REALTIME = "voxtral_realtime"

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
            PROVIDER_VOXTRAL_REALTIME -> {
                val storedEndpoint = json.optString("endpoint", "")
                val endpoint = if (storedEndpoint.isEmpty() || storedEndpoint == "wss://api.mistral.ai/v1/realtime")
                    "wss://api.mistral.ai/v1/audio/transcriptions/realtime" else storedEndpoint
                VoxtralRealtime(
                    apiKey = json.optString("apiKey", ""),
                    model = json.optString("model", "voxtral-mini-transcribe-realtime-2602"),
                    endpoint = endpoint,
                )
            }
            else -> Voxtral()
        }

        fun default(providerId: String): ProviderConfig = when (providerId) {
            PROVIDER_VOXTRAL -> Voxtral()
            PROVIDER_OPENAI -> OpenAiWhisper()
            PROVIDER_GROQ -> Groq()
            PROVIDER_LOCAL -> Local()
            PROVIDER_MOONSHINE -> Moonshine()
            PROVIDER_VOXTRAL_REALTIME -> VoxtralRealtime()
            else -> Voxtral()
        }
    }
}
