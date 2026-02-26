package com.hush.app.transcription

import android.content.Context

object ProviderFactory {

    fun resolve(context: Context): TranscriptionProvider {
        val activeId = ProviderRepository.getActiveProviderId(context)
        val config = ProviderRepository.getConfig(context, activeId)
        return create(activeId, config)
    }

    fun create(providerId: String, config: ProviderConfig): TranscriptionProvider = when (providerId) {
        ProviderConfig.PROVIDER_VOXTRAL -> VoxtralProvider(config as ProviderConfig.Voxtral)
        ProviderConfig.PROVIDER_OPENAI -> OpenAiWhisperProvider(config as ProviderConfig.OpenAiWhisper)
        ProviderConfig.PROVIDER_GROQ -> GroqProvider(config as ProviderConfig.Groq)
        else -> VoxtralProvider(config as? ProviderConfig.Voxtral ?: ProviderConfig.Voxtral())
    }

    val allProviderIds: List<String> = ProviderRepository.allProviderIds

    fun displayName(providerId: String): String = when (providerId) {
        ProviderConfig.PROVIDER_VOXTRAL -> "Voxtral (Mistral)"
        ProviderConfig.PROVIDER_OPENAI -> "OpenAI Whisper"
        ProviderConfig.PROVIDER_GROQ -> "Groq"
        else -> providerId
    }
}
