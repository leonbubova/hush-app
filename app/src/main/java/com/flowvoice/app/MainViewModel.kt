package com.flowvoice.app

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val dictationState: DictationService.DictationState = DictationService.DictationState.IDLE,
        val lastTranscription: String = "",
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
                    DictationService.DictationState.DONE -> _state.value.copy(
                        dictationState = dictationState,
                        lastTranscription = text ?: _state.value.lastTranscription,
                        errorMessage = "",
                    )
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

    init {
        val prefs = application.getSharedPreferences("flowvoice", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("voxtral_api_key", "") ?: ""
        _state.value = _state.value.copy(
            apiKey = savedKey,
            showApiKeyDialog = savedKey.isBlank(),
            accessibilityEnabled = isAccessibilityEnabled(),
        )
        startAndBindService()
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
        context.getSharedPreferences("flowvoice", Context.MODE_PRIVATE)
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

    fun refreshAccessibilityStatus() {
        _state.value = _state.value.copy(accessibilityEnabled = isAccessibilityEnabled())
    }

    private fun isAccessibilityEnabled(): Boolean {
        val context = getApplication<Application>()
        val serviceName = ComponentName(context, FlowVoiceAccessibilityService::class.java).flattenToString()
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
