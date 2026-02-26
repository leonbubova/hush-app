package com.hush.app.transcription

import android.content.Context

object ProviderFactory {

    fun resolve(context: Context): TranscriptionProvider {
        val activeId = ProviderRepository.getActiveProviderId(context)
        val config = ProviderRepository.getConfig(context, activeId)
        return create(activeId, config, context)
    }

    fun create(providerId: String, config: ProviderConfig, context: Context? = null): TranscriptionProvider = when (providerId) {
        ProviderConfig.PROVIDER_VOXTRAL -> VoxtralProvider(config as ProviderConfig.Voxtral)
        ProviderConfig.PROVIDER_OPENAI -> OpenAiWhisperProvider(config as ProviderConfig.OpenAiWhisper)
        ProviderConfig.PROVIDER_GROQ -> GroqProvider(config as ProviderConfig.Groq)
        ProviderConfig.PROVIDER_LOCAL -> LocalProvider(
            config as ProviderConfig.Local,
            context ?: throw IllegalStateException("Context required for local provider"),
        )
        else -> VoxtralProvider(config as? ProviderConfig.Voxtral ?: ProviderConfig.Voxtral())
    }

    val allProviderIds: List<String> = ProviderRepository.allProviderIds

    fun displayName(providerId: String): String = when (providerId) {
        ProviderConfig.PROVIDER_VOXTRAL -> "Voxtral (Mistral)"
        ProviderConfig.PROVIDER_OPENAI -> "OpenAI Whisper"
        ProviderConfig.PROVIDER_GROQ -> "Groq"
        ProviderConfig.PROVIDER_LOCAL -> "Local (On-Device)"
        else -> providerId
    }
}
