package com.hush.app

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import androidx.lifecycle.AndroidViewModel
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class MainViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val dictationState: DictationService.DictationState = DictationService.DictationState.IDLE,
        val history: List<String> = emptyList(),
        val errorMessage: String = "",
        val apiKey: String = "",
        val showApiKeyDialog: Boolean = false,
        val accessibilityEnabled: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

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
                            )
                        } else {
                            _state.value.copy(dictationState = dictationState, errorMessage = "")
                        }
                    }
                    DictationService.DictationState.ERROR -> _state.value.copy(
                        dictationState = dictationState,
                        errorMessage = text ?: "Something went wrong",
                    )
                    else -> _state.value.copy(
                        dictationState = dictationState,
                        errorMessage = "",
                    )
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
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

    private fun migratePrefsIfNeeded(context: Context, encryptedPrefs: SharedPreferences) {
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
        val savedKey = prefs.getString("voxtral_api_key", "") ?: ""
        _state.value = _state.value.copy(
            apiKey = savedKey,
            history = loadHistory(),
            showApiKeyDialog = savedKey.isBlank(),
            accessibilityEnabled = isAccessibilityEnabled(),
        )
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

    fun saveApiKey(key: String) {
        val context = getApplication<Application>()
        getEncryptedPrefs(context)
            .edit()
            .putString("voxtral_api_key", key.trim())
            .apply()
        _state.value = _state.value.copy(apiKey = key.trim(), showApiKeyDialog = false)
    }

    fun showApiKeyDialog() {
        _state.value = _state.value.copy(showApiKeyDialog = true)
    }

    fun dismissApiKeyDialog() {
        _state.value = _state.value.copy(showApiKeyDialog = false)
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

    override fun onCleared() {
        if (bound) {
            getApplication<Application>().unbindService(connection)
            bound = false
        }
        super.onCleared()
    }
}
