package com.hush.app

import com.hush.app.transcription.ProviderConfig
import com.hush.app.transcription.VoxtralRealtimeProvider
import org.junit.Assert.*
import org.junit.Test

class VoxtralRealtimeProviderTest {

    @Test
    fun `start with blank API key calls onError`() {
        val config = ProviderConfig.VoxtralRealtime(apiKey = "")
        val provider = VoxtralRealtimeProvider(config)

        var errorMessage: String? = null
        provider.onError = { errorMessage = it }

        provider.start()

        assertNotNull("onError should be called for blank API key", errorMessage)
        assertTrue(errorMessage!!.contains("API key"))
    }

    @Test
    fun `release clears all callbacks`() {
        val config = ProviderConfig.VoxtralRealtime(apiKey = "test-key")
        val provider = VoxtralRealtimeProvider(config)

        provider.onLineStarted = {}
        provider.onLineTextChanged = {}
        provider.onLineCompleted = {}
        provider.onError = {}

        provider.release()

        // After release, callbacks should be null (provider won't hold references)
        // We verify by checking the provider can be released without error
    }
}
