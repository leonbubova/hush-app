package com.hush.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import com.hush.app.transcription.ProviderConfig
import com.hush.app.transcription.ProviderRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProviderRepositoryTest {

    private lateinit var context: Context

    private fun getEncryptedPrefs(): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "hush_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        getEncryptedPrefs().edit().clear().apply()
    }

    @After
    fun tearDown() {
        getEncryptedPrefs().edit().clear().apply()
    }

    @Test
    fun `default active provider is voxtral`() {
        val id = ProviderRepository.getActiveProviderId(context)
        assertEquals(ProviderConfig.PROVIDER_VOXTRAL, id)
    }

    @Test
    fun `setActiveProviderId then getActiveProviderId roundtrips`() {
        ProviderRepository.setActiveProviderId(context, ProviderConfig.PROVIDER_OPENAI)
        assertEquals(ProviderConfig.PROVIDER_OPENAI, ProviderRepository.getActiveProviderId(context))
    }

    @Test
    fun `getConfig returns default for unconfigured provider`() {
        val config = ProviderRepository.getConfig(context, ProviderConfig.PROVIDER_GROQ)
        assertTrue(config is ProviderConfig.Groq)
        assertEquals("", (config as ProviderConfig.Groq).apiKey)
        assertEquals("whisper-large-v3-turbo", config.model)
    }

    @Test
    fun `saveConfig then getConfig roundtrips`() {
        val config = ProviderConfig.OpenAiWhisper(apiKey = "sk-test", model = "gpt-4o-transcribe", language = "en")
        ProviderRepository.saveConfig(context, ProviderConfig.PROVIDER_OPENAI, config)

        val loaded = ProviderRepository.getConfig(context, ProviderConfig.PROVIDER_OPENAI)
        assertTrue(loaded is ProviderConfig.OpenAiWhisper)
        val openai = loaded as ProviderConfig.OpenAiWhisper
        assertEquals("sk-test", openai.apiKey)
        assertEquals("gpt-4o-transcribe", openai.model)
        assertEquals("en", openai.language)
    }

    @Test
    fun `getAllConfigs returns configs for all 5 providers`() {
        val configs = ProviderRepository.getAllConfigs(context)
        assertEquals(5, configs.size)
        assertTrue(configs[ProviderConfig.PROVIDER_VOXTRAL] is ProviderConfig.Voxtral)
        assertTrue(configs[ProviderConfig.PROVIDER_OPENAI] is ProviderConfig.OpenAiWhisper)
        assertTrue(configs[ProviderConfig.PROVIDER_GROQ] is ProviderConfig.Groq)
        assertTrue(configs[ProviderConfig.PROVIDER_LOCAL] is ProviderConfig.Local)
        assertTrue(configs[ProviderConfig.PROVIDER_MOONSHINE] is ProviderConfig.Moonshine)
    }

    @Test
    fun `getAllConfigs reflects saved configs`() {
        val config = ProviderConfig.Voxtral(apiKey = "my-key")
        ProviderRepository.saveConfig(context, ProviderConfig.PROVIDER_VOXTRAL, config)

        val all = ProviderRepository.getAllConfigs(context)
        assertEquals("my-key", (all[ProviderConfig.PROVIDER_VOXTRAL] as ProviderConfig.Voxtral).apiKey)
    }

    @Test
    fun `malformed JSON in prefs returns default config`() {
        getEncryptedPrefs().edit()
            .putString("provider_cfg_voxtral", "not valid json!!!")
            .apply()

        val config = ProviderRepository.getConfig(context, ProviderConfig.PROVIDER_VOXTRAL)
        assertTrue(config is ProviderConfig.Voxtral)
        assertEquals("", (config as ProviderConfig.Voxtral).apiKey)
    }

    @Test
    fun `legacy migration moves voxtral_api_key to provider config`() {
        // Simulate legacy state: voxtral_api_key exists but no active_provider
        getEncryptedPrefs().edit()
            .putString("voxtral_api_key", "legacy-key-123")
            .apply()

        val config = ProviderRepository.getConfig(context, ProviderConfig.PROVIDER_VOXTRAL)
        assertTrue(config is ProviderConfig.Voxtral)
        assertEquals("legacy-key-123", (config as ProviderConfig.Voxtral).apiKey)
    }

    @Test
    fun `allProviderIds contains all 5 providers`() {
        val ids = ProviderRepository.allProviderIds
        assertEquals(5, ids.size)
        assertTrue(ids.contains(ProviderConfig.PROVIDER_VOXTRAL))
        assertTrue(ids.contains(ProviderConfig.PROVIDER_OPENAI))
        assertTrue(ids.contains(ProviderConfig.PROVIDER_GROQ))
        assertTrue(ids.contains(ProviderConfig.PROVIDER_LOCAL))
        assertTrue(ids.contains(ProviderConfig.PROVIDER_MOONSHINE))
    }

    @Test
    fun `local provider config roundtrips`() {
        val config = ProviderConfig.Local(model = "whisper-tiny-en-q8", language = "de")
        ProviderRepository.saveConfig(context, ProviderConfig.PROVIDER_LOCAL, config)

        val loaded = ProviderRepository.getConfig(context, ProviderConfig.PROVIDER_LOCAL)
        assertTrue(loaded is ProviderConfig.Local)
        val local = loaded as ProviderConfig.Local
        assertEquals("whisper-tiny-en-q8", local.model)
        assertEquals("de", local.language)
    }
}
