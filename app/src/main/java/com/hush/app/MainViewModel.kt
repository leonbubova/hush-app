package com.hush.app

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import androidx.lifecycle.AndroidViewModel
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hush.app.transcription.ModelManager
import com.hush.app.transcription.ModelStatus
import com.hush.app.transcription.ProviderConfig
import com.hush.app.transcription.ProviderFactory
import com.hush.app.transcription.ProviderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import androidx.lifecycle.viewModelScope

class MainViewModel(application: Application) : AndroidViewModel(application) {

    enum class AppScreen { HOME, USAGE, SETTINGS }

    data class UiState(
        val dictationState: DictationService.DictationState = DictationService.DictationState.IDLE,
        val history: List<String> = emptyList(),
        val errorMessage: String = "",
        val streamingText: String = "",
        val activeProviderId: String = ProviderConfig.PROVIDER_VOXTRAL,
        val providerConfigs: Map<String, ProviderConfig> = emptyMap(),
        val accessibilityEnabled: Boolean = false,
        val currentScreen: AppScreen = AppScreen.HOME,
        val usageSessions: List<RecordingSession> = emptyList(),
        val modelStatuses: Map<String, ModelStatus> = emptyMap(),
        val modelDownloadProgress: Map<String, Float> = emptyMap(),
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val modelManager = ModelManager(application)

    private var service: DictationService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as DictationService.LocalBinder
            service = localBinder.getService()
            bound = true
            service?.onStateChanged = { dictationState, text ->
                _state.value = when (dictationState) {
                    DictationService.DictationState.DONE -> {
                        val newText = text ?: ""
                        if (newText.isNotBlank()) {
                            val updated = listOf(newText) + _state.value.history
                            saveHistory(updated)
                            _state.value.copy(
                                dictationState = dictationState,
                                history = updated,
                                errorMessage = "",
                                streamingText = "",
                                usageSessions = UsageRepository.loadSessions(getApplication()),
                            )
                        } else {
                            _state.value.copy(
                                dictationState = dictationState,
                                errorMessage = "",
                                streamingText = "",
                            )
                        }
                    }
                    DictationService.DictationState.ERROR -> _state.value.copy(
                        dictationState = dictationState,
                        errorMessage = text ?: "Something went wrong",
                        streamingText = "",
                    )
                    DictationService.DictationState.STREAMING -> _state.value.copy(
                        dictationState = dictationState,
                        errorMessage = "",
                    )
                    else -> _state.value.copy(
                        dictationState = dictationState,
                        errorMessage = "",
                        streamingText = "",
                    )
                }
            }
            service?.onStreamingTextChanged = { text ->
                _state.value = _state.value.copy(streamingText = text)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

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

    private fun migratePrefsIfNeeded(context: Context, encryptedPrefs: android.content.SharedPreferences) {
        // Migrate from legacy unencrypted prefs
        val oldPrefs = context.getSharedPreferences("flowvoice", Context.MODE_PRIVATE)
        val oldKey = oldPrefs.getString("voxtral_api_key", null)
        if (oldKey != null) {
            encryptedPrefs.edit().putString("voxtral_api_key", oldKey).apply()
            oldPrefs.edit().remove("voxtral_api_key").apply()
        }

        // Migrate from old encrypted prefs name
        try {
            val oldMasterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val oldEncryptedPrefs = EncryptedSharedPreferences.create(
                context,
                "flowvoice_secure",
                oldMasterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            val oldEncKey = oldEncryptedPrefs.getString("voxtral_api_key", null)
            if (oldEncKey != null && encryptedPrefs.getString("voxtral_api_key", null) == null) {
                encryptedPrefs.edit().putString("voxtral_api_key", oldEncKey).apply()
                val oldHistory = oldEncryptedPrefs.getString("history", null)
                if (oldHistory != null) {
                    encryptedPrefs.edit().putString("history", oldHistory).apply()
                }
            }
        } catch (_: Exception) {
            // Old prefs don't exist or can't be read — that's fine
        }
    }

    private var serviceStarted = false

    init {
        val prefs = getEncryptedPrefs(application)
        migratePrefsIfNeeded(application, prefs)
        // ProviderRepository handles migrating voxtral_api_key → provider config
        val activeId = ProviderRepository.getActiveProviderId(application)
        val configs = ProviderRepository.getAllConfigs(application)
        _state.value = _state.value.copy(
            activeProviderId = activeId,
            providerConfigs = configs,
            history = loadHistory(),
            accessibilityEnabled = isAccessibilityEnabled(),
            usageSessions = UsageRepository.loadSessions(application),
        )
        // Observe model download statuses and progress
        viewModelScope.launch {
            modelManager.statuses.collect { statuses ->
                _state.value = _state.value.copy(modelStatuses = statuses)
            }
        }
        viewModelScope.launch {
            modelManager.downloadProgress.collect { progress ->
                _state.value = _state.value.copy(modelDownloadProgress = progress)
            }
        }
    }

    fun startServiceIfNeeded() {
        if (!serviceStarted) {
            startAndBindService()
            serviceStarted = true
        }
    }

    private fun startAndBindService() {
        val context = getApplication<Application>()
        val intent = Intent(context, DictationService::class.java)
        context.startForegroundService(intent)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun toggle() {
        service?.toggle()
    }

    fun setAppForeground(foreground: Boolean) {
        service?.isAppInForeground = foreground
    }

    fun setActiveProvider(id: String) {
        val context = getApplication<Application>()
        ProviderRepository.setActiveProviderId(context, id)
        _state.value = _state.value.copy(activeProviderId = id)
    }

    fun saveProviderConfig(providerId: String, config: ProviderConfig) {
        val context = getApplication<Application>()
        ProviderRepository.saveConfig(context, providerId, config)
        val updated = _state.value.providerConfigs.toMutableMap()
        updated[providerId] = config
        _state.value = _state.value.copy(providerConfigs = updated)
    }

    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            modelManager.downloadModel(modelId)
        }
    }

    fun deleteModel(modelId: String) {
        modelManager.deleteModel(modelId)
    }

    fun clearHistory() {
        val context = getApplication<Application>()
        getEncryptedPrefs(context).edit().remove("history").apply()
        _state.value = _state.value.copy(history = emptyList())
    }

    private fun loadHistory(): List<String> {
        val context = getApplication<Application>()
        val json = getEncryptedPrefs(context).getString("history", null) ?: return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { arr.getString(it) }
    }

    private fun saveHistory(history: List<String>) {
        val context = getApplication<Application>()
        val arr = JSONArray(history)
        getEncryptedPrefs(context).edit().putString("history", arr.toString()).apply()
    }

    fun refreshAccessibilityStatus() {
        _state.value = _state.value.copy(accessibilityEnabled = isAccessibilityEnabled())
    }

    private fun isAccessibilityEnabled(): Boolean {
        val context = getApplication<Application>()
        val serviceName = ComponentName(context, HushAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return TextUtils.SimpleStringSplitter(':').apply { setString(enabledServices) }
            .any { it.equals(serviceName, ignoreCase = true) }
    }

    fun navigateTo(screen: AppScreen) {
        _state.value = _state.value.copy(
            currentScreen = screen,
            usageSessions = if (screen == AppScreen.USAGE)
                UsageRepository.loadSessions(getApplication()) else _state.value.usageSessions,
        )
    }

    fun clearUsageData() {
        UsageRepository.clearSessions(getApplication())
        _state.value = _state.value.copy(usageSessions = emptyList())
    }

    override fun onCleared() {
        if (bound) {
            getApplication<Application>().unbindService(connection)
            bound = false
        }
        super.onCleared()
    }
}
