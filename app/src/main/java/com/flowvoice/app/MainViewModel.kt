package com.flowvoice.app

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val dictationState: DictationService.DictationState = DictationService.DictationState.IDLE,
        val lastTranscription: String = "",
        val apiKey: String = "",
        val showApiKeyDialog: Boolean = false,
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
            service?.onStateChanged = { dictationState ->
                _state.value = _state.value.copy(dictationState = dictationState)
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
            showApiKeyDialog = savedKey.isBlank()
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

    fun updateLastTranscription(text: String) {
        _state.value = _state.value.copy(lastTranscription = text)
    }

    override fun onCleared() {
        if (bound) {
            getApplication<Application>().unbindService(connection)
            bound = false
        }
        super.onCleared()
    }
}
