package com.hush.app.transcription

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

object ProviderRepository {

    private const val TAG = "ProviderRepository"
    private const val PREFS_NAME = "hush_secure"
    private const val KEY_ACTIVE_PROVIDER = "active_provider"
    private const val KEY_PROVIDER_CFG_PREFIX = "provider_cfg_"
    private const val LEGACY_API_KEY = "voxtral_api_key"

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

    fun getActiveProviderId(context: Context): String {
        val prefs = getEncryptedPrefs(context)
        migrateIfNeeded(prefs)
        return prefs.getString(KEY_ACTIVE_PROVIDER, ProviderConfig.PROVIDER_VOXTRAL)
            ?: ProviderConfig.PROVIDER_VOXTRAL
    }

    fun setActiveProviderId(context: Context, providerId: String) {
        getEncryptedPrefs(context).edit()
            .putString(KEY_ACTIVE_PROVIDER, providerId)
            .apply()
    }

    fun getConfig(context: Context, providerId: String): ProviderConfig {
        val prefs = getEncryptedPrefs(context)
        migrateIfNeeded(prefs)
        val json = prefs.getString(KEY_PROVIDER_CFG_PREFIX + providerId, null)
            ?: return ProviderConfig.default(providerId)
        return try {
            ProviderConfig.fromJson(providerId, JSONObject(json))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse config for $providerId", e)
            ProviderConfig.default(providerId)
        }
    }

    fun saveConfig(context: Context, providerId: String, config: ProviderConfig) {
        getEncryptedPrefs(context).edit()
            .putString(KEY_PROVIDER_CFG_PREFIX + providerId, config.toJson().toString())
            .apply()
    }

    fun getAllConfigs(context: Context): Map<String, ProviderConfig> {
        val prefs = getEncryptedPrefs(context)
        migrateIfNeeded(prefs)
        return allProviderIds.associateWith { id ->
            val json = prefs.getString(KEY_PROVIDER_CFG_PREFIX + id, null)
            if (json != null) {
                try {
                    ProviderConfig.fromJson(id, JSONObject(json))
                } catch (e: Exception) {
                    ProviderConfig.default(id)
                }
            } else {
                ProviderConfig.default(id)
            }
        }
    }

    val allProviderIds = listOf(
        ProviderConfig.PROVIDER_VOXTRAL,
        ProviderConfig.PROVIDER_OPENAI,
        ProviderConfig.PROVIDER_GROQ,
    )

    private fun migrateIfNeeded(prefs: SharedPreferences) {
        if (prefs.contains(KEY_ACTIVE_PROVIDER)) return

        val legacyKey = prefs.getString(LEGACY_API_KEY, null)
        if (legacyKey != null && legacyKey.isNotBlank()) {
            Log.i(TAG, "Migrating legacy voxtral_api_key to provider config")
            val config = ProviderConfig.Voxtral(apiKey = legacyKey)
            prefs.edit()
                .putString(KEY_ACTIVE_PROVIDER, ProviderConfig.PROVIDER_VOXTRAL)
                .putString(KEY_PROVIDER_CFG_PREFIX + ProviderConfig.PROVIDER_VOXTRAL, config.toJson().toString())
                .apply()
        }
    }
}
