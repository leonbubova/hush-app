package com.hush.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray

object HistoryRepository {

    private const val PREFS_NAME = "hush_secure"
    private const val KEY_HISTORY = "history"

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun addEntry(context: Context, text: String) {
        if (text.isBlank()) return
        val entries = loadAll(context).toMutableList()
        entries.add(0, text)
        val arr = JSONArray(entries)
        getEncryptedPrefs(context).edit().putString(KEY_HISTORY, arr.toString()).commit()
    }

    fun loadAll(context: Context): List<String> {
        val json = getEncryptedPrefs(context).getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        getEncryptedPrefs(context).edit().remove(KEY_HISTORY).commit()
    }
}
