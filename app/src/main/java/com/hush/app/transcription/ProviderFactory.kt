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
        ProviderConfig.PROVIDER_MOONSHINE -> throw IllegalStateException(
            "Moonshine is a streaming provider — use MoonshineProvider directly, not TranscriptionProvider"
        )
        ProviderConfig.PROVIDER_VOXTRAL_REALTIME -> throw IllegalStateException(
            "Voxtral Realtime is a streaming provider — use VoxtralRealtimeProvider directly, not TranscriptionProvider"
        )
        else -> VoxtralProvider(config as? ProviderConfig.Voxtral ?: ProviderConfig.Voxtral())
    }

    fun isStreaming(context: Context): Boolean {
        val activeId = ProviderRepository.getActiveProviderId(context)
        return activeId == ProviderConfig.PROVIDER_MOONSHINE || activeId == ProviderConfig.PROVIDER_VOXTRAL_REALTIME
    }

    val allProviderIds: List<String> = ProviderRepository.allProviderIds

    fun displayName(providerId: String): String = when (providerId) {
        ProviderConfig.PROVIDER_VOXTRAL -> "Voxtral (Mistral)"
        ProviderConfig.PROVIDER_OPENAI -> "OpenAI Whisper"
        ProviderConfig.PROVIDER_GROQ -> "Groq"
        ProviderConfig.PROVIDER_LOCAL -> "Local (On-Device)"
        ProviderConfig.PROVIDER_MOONSHINE -> "Moonshine (Streaming)"
        ProviderConfig.PROVIDER_VOXTRAL_REALTIME -> "Voxtral Realtime (Streaming)"
        else -> providerId
    }
}
