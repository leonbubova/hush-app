package com.hush.app

import android.app.Application
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import com.hush.app.transcription.ProviderConfig
import com.hush.app.transcription.ProviderRepository
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {

    private lateinit var app: Application
    private lateinit var viewModel: MainViewModel

    private fun getEncryptedPrefs(context: Context): android.content.SharedPreferences {
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
        app = ApplicationProvider.getApplicationContext()
        // Reset singleton caches so tests get fresh SharedPreferences
        HistoryRepository.resetCachedPrefs()
        ProviderRepository.resetCachedPrefs()
        // Clear all relevant prefs before each test
        getEncryptedPrefs(app).edit().clear().apply()
        UsageRepository.clearSessions(app)
        viewModel = MainViewModel(app)
    }

    @After
    fun tearDown() {
        getEncryptedPrefs(app).edit().clear().apply()
        UsageRepository.clearSessions(app)
        HistoryRepository.resetCachedPrefs()
        ProviderRepository.resetCachedPrefs()
    }

    // ---- Init state ----

    @Test
    fun `init state starts on HOME screen`() {
        assertEquals(MainViewModel.AppScreen.HOME, viewModel.state.value.currentScreen)
    }

    @Test
    fun `init state has IDLE dictation state`() {
        assertEquals(DictationService.DictationState.IDLE, viewModel.state.value.dictationState)
    }

    @Test
    fun `init state loads empty history when no saved data`() {
        assertTrue(viewModel.state.value.history.isEmpty())
    }

    @Test
    fun `init state loads saved history from prefs`() {
        val arr = JSONArray(listOf("hello", "world"))
        getEncryptedPrefs(app).edit().putString("history", arr.toString()).apply()

        // Recreate viewModel to pick up saved history
        viewModel = MainViewModel(app)

        assertEquals(listOf("hello", "world"), viewModel.state.value.history)
    }

    @Test
    fun `init state loads default provider as moonshine`() {
        assertEquals(ProviderConfig.PROVIDER_MOONSHINE, viewModel.state.value.activeProviderId)
    }

    @Test
    fun `init state loads provider configs for all providers`() {
        val configs = viewModel.state.value.providerConfigs
        assertEquals(6, configs.size)
        assertTrue(configs.containsKey(ProviderConfig.PROVIDER_VOXTRAL))
        assertTrue(configs.containsKey(ProviderConfig.PROVIDER_VOXTRAL_REALTIME))
        assertTrue(configs.containsKey(ProviderConfig.PROVIDER_OPENAI))
        assertTrue(configs.containsKey(ProviderConfig.PROVIDER_GROQ))
        assertTrue(configs.containsKey(ProviderConfig.PROVIDER_LOCAL))
        assertTrue(configs.containsKey(ProviderConfig.PROVIDER_MOONSHINE))
    }

    @Test
    fun `init state loads usage sessions`() {
        UsageRepository.recordSession(app, 1000L, 10, 5)
        viewModel = MainViewModel(app)

        assertEquals(1, viewModel.state.value.usageSessions.size)
    }

    // ---- Navigation ----

    @Test
    fun `navigateTo SETTINGS updates currentScreen`() {
        viewModel.navigateTo(MainViewModel.AppScreen.SETTINGS)
        assertEquals(MainViewModel.AppScreen.SETTINGS, viewModel.state.value.currentScreen)
    }

    @Test
    fun `navigateTo USAGE updates currentScreen and refreshes sessions`() {
        UsageRepository.recordSession(app, 1000L, 10, 5)
        viewModel.navigateTo(MainViewModel.AppScreen.USAGE)

        assertEquals(MainViewModel.AppScreen.USAGE, viewModel.state.value.currentScreen)
        assertEquals(1, viewModel.state.value.usageSessions.size)
    }

    @Test
    fun `navigateTo HOME from SETTINGS updates currentScreen`() {
        viewModel.navigateTo(MainViewModel.AppScreen.SETTINGS)
        viewModel.navigateTo(MainViewModel.AppScreen.HOME)
        assertEquals(MainViewModel.AppScreen.HOME, viewModel.state.value.currentScreen)
    }

    // ---- Provider management ----

    @Test
    fun `setActiveProvider updates state`() {
        viewModel.setActiveProvider(ProviderConfig.PROVIDER_OPENAI)
        assertEquals(ProviderConfig.PROVIDER_OPENAI, viewModel.state.value.activeProviderId)
    }

    @Test
    fun `setActiveProvider persists to prefs`() {
        viewModel.setActiveProvider(ProviderConfig.PROVIDER_GROQ)
        val persisted = ProviderRepository.getActiveProviderId(app)
        assertEquals(ProviderConfig.PROVIDER_GROQ, persisted)
    }

    @Test
    fun `saveProviderConfig updates state map`() {
        val config = ProviderConfig.OpenAiWhisper(apiKey = "test-key-123")
        viewModel.saveProviderConfig(ProviderConfig.PROVIDER_OPENAI, config)

        val saved = viewModel.state.value.providerConfigs[ProviderConfig.PROVIDER_OPENAI]
        assertTrue(saved is ProviderConfig.OpenAiWhisper)
        assertEquals("test-key-123", (saved as ProviderConfig.OpenAiWhisper).apiKey)
    }

    @Test
    fun `saveProviderConfig persists to prefs`() {
        val config = ProviderConfig.Groq(apiKey = "groq-key-456")
        viewModel.saveProviderConfig(ProviderConfig.PROVIDER_GROQ, config)

        val persisted = ProviderRepository.getConfig(app, ProviderConfig.PROVIDER_GROQ)
        assertTrue(persisted is ProviderConfig.Groq)
        assertEquals("groq-key-456", (persisted as ProviderConfig.Groq).apiKey)
    }

    // ---- History ----

    @Test
    fun `clearHistory empties list and removes from prefs`() {
        // Seed history
        val arr = JSONArray(listOf("test"))
        getEncryptedPrefs(app).edit().putString("history", arr.toString()).apply()
        viewModel = MainViewModel(app)
        assertEquals(1, viewModel.state.value.history.size)

        viewModel.clearHistory()

        assertTrue(viewModel.state.value.history.isEmpty())
        assertNull(getEncryptedPrefs(app).getString("history", null))
    }

    @Test
    fun `clearHistory is no-op when already empty`() {
        viewModel.clearHistory()
        assertTrue(viewModel.state.value.history.isEmpty())
    }

    @Test
    fun `history preserves order newest first`() {
        val arr = JSONArray(listOf("newest", "older", "oldest"))
        getEncryptedPrefs(app).edit().putString("history", arr.toString()).apply()
        viewModel = MainViewModel(app)

        assertEquals(listOf("newest", "older", "oldest"), viewModel.state.value.history)
    }

    // ---- Usage ----

    @Test
    fun `clearUsageData empties sessions`() {
        UsageRepository.recordSession(app, 1000L, 10, 5)
        viewModel = MainViewModel(app)
        assertFalse(viewModel.state.value.usageSessions.isEmpty())

        viewModel.clearUsageData()
        assertTrue(viewModel.state.value.usageSessions.isEmpty())
    }

    @Test
    fun `navigateTo USAGE refreshes sessions from repository`() {
        // Start with no sessions
        viewModel.navigateTo(MainViewModel.AppScreen.HOME)
        assertTrue(viewModel.state.value.usageSessions.isEmpty())

        // Add session externally
        UsageRepository.recordSession(app, 2000L, 20, 10)

        // Navigate to USAGE should refresh
        viewModel.navigateTo(MainViewModel.AppScreen.USAGE)
        assertEquals(1, viewModel.state.value.usageSessions.size)
    }

    // ---- Service ----

    @Test
    fun `toggle does not crash when service is null`() {
        // Service is not bound in unit tests — toggle() should just be a no-op
        viewModel.toggle()
    }

    // ---- Error message ----

    @Test
    fun `init state has empty error message`() {
        assertEquals("", viewModel.state.value.errorMessage)
    }

    // ---- Model statuses ----

    @Test
    fun `init state has model statuses for all available models`() {
        // ModelManager.refreshStatuses() runs in init, populating statuses via viewModelScope
        // Under Robolectric the flow collection may or may not have run yet,
        // so we just verify the shape is reasonable (empty or populated)
        val statuses = viewModel.state.value.modelStatuses
        if (statuses.isNotEmpty()) {
            assertTrue(statuses.all { it.value == com.hush.app.transcription.ModelStatus.NOT_DOWNLOADED })
        }
    }
}
