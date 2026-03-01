package com.hush.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DataExporter {

    private const val CURRENT_VERSION = 1

    data class ImportResult(val historyCount: Int, val usageCount: Int)

    fun exportJson(context: Context): String {
        val history = HistoryRepository.loadAll(context)
        val sessions = UsageRepository.loadSessions(context)

        val root = JSONObject()
        root.put("version", CURRENT_VERSION)
        root.put("exportedAt", iso8601Now())

        val historyArr = JSONArray()
        history.forEach { historyArr.put(it) }
        root.put("history", historyArr)

        val usageArr = JSONArray()
        sessions.forEach { s ->
            usageArr.put(JSONObject().apply {
                put("s", s.startEpochMs)
                put("d", s.durationSeconds)
                put("w", s.wordCount)
            })
        }
        root.put("usage", usageArr)

        return root.toString(2)
    }

    fun importJson(context: Context, json: String): ImportResult {
        val root = JSONObject(json)

        val version = root.optInt("version", -1)
        if (version < 1) {
            throw IllegalArgumentException("Invalid or missing version field")
        }

        // Parse history
        val historyArr = root.optJSONArray("history") ?: JSONArray()
        val history = (0 until historyArr.length()).map { historyArr.getString(it) }

        // Parse usage
        val usageArr = root.optJSONArray("usage") ?: JSONArray()
        val sessions = (0 until usageArr.length()).map { i ->
            val obj = usageArr.getJSONObject(i)
            RecordingSession(
                startEpochMs = obj.getLong("s"),
                durationSeconds = obj.getInt("d"),
                wordCount = obj.optInt("w", 0),
            )
        }

        // Replace existing data
        HistoryRepository.saveAll(context, history)
        UsageRepository.saveSessions(context, sessions)

        return ImportResult(historyCount = history.size, usageCount = sessions.size)
    }

    private fun iso8601Now(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
