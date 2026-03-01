package com.hush.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.annotation.VisibleForTesting
import org.json.JSONArray

object HistoryRepository {

    private const val PREFS_NAME = "hush_secure"
    private const val KEY_HISTORY = "history"
    private const val MAX_ENTRIES = 5000

    @Volatile private var cachedPrefs: SharedPreferences? = null

    @VisibleForTesting
    fun resetCachedPrefs() { cachedPrefs = null }

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        synchronized(this) {
            cachedPrefs?.let { return it }
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            ).also { cachedPrefs = it }
        }
    }

    fun addEntry(context: Context, text: String) {
        if (text.isBlank()) return
        val entries = loadAll(context).toMutableList()
        entries.add(0, text)
        while (entries.size > MAX_ENTRIES) entries.removeAt(entries.lastIndex)
        val arr = JSONArray(entries)
        getEncryptedPrefs(context).edit().putString(KEY_HISTORY, arr.toString()).apply()
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

    fun saveAll(context: Context, entries: List<String>) {
        val arr = JSONArray(entries)
        getEncryptedPrefs(context).edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    fun clear(context: Context) {
        getEncryptedPrefs(context).edit().remove(KEY_HISTORY).apply()
    }
}
