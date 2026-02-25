package com.hush.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class RecordingSession(
    val startEpochMs: Long,
    val durationSeconds: Int,
    val wordCount: Int,
)

object UsageRepository {

    private const val PREFS_NAME = "hush_usage"
    private const val KEY_SESSIONS = "sessions"
    private const val MAX_SESSIONS = 500

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordSession(context: Context, startEpochMs: Long, durationSeconds: Int, wordCount: Int = 0) {
        val sessions = loadSessions(context).toMutableList()
        sessions.add(RecordingSession(startEpochMs, durationSeconds, wordCount))
        // Cap at MAX_SESSIONS, dropping oldest
        while (sessions.size > MAX_SESSIONS) sessions.removeAt(0)
        saveSessions(context, sessions)
    }

    fun loadSessions(context: Context): List<RecordingSession> {
        val json = prefs(context).getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                RecordingSession(
                    startEpochMs = obj.getLong("s"),
                    durationSeconds = obj.getInt("d"),
                    wordCount = obj.optInt("w", 0),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearSessions(context: Context) {
        prefs(context).edit().remove(KEY_SESSIONS).apply()
    }

    private fun saveSessions(context: Context, sessions: List<RecordingSession>) {
        val arr = JSONArray()
        sessions.forEach { s ->
            arr.put(JSONObject().apply {
                put("s", s.startEpochMs)
                put("d", s.durationSeconds)
                put("w", s.wordCount)
            })
        }
        prefs(context).edit().putString(KEY_SESSIONS, arr.toString()).apply()
    }
}
