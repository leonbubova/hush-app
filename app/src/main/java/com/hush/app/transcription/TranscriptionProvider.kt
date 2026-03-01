package com.hush.app.transcription

import org.json.JSONObject
import java.io.File

interface TranscriptionProvider {
    val displayName: String
    val id: String
    val requiresNetwork: Boolean
    suspend fun transcribe(audioFile: File): TranscribeResult
}

sealed class TranscribeResult {
    data class Success(val text: String) : TranscribeResult()
    data class Error(val code: Int?, val message: String) : TranscribeResult()
}

internal fun parse429Error(responseBody: String?): String {
    if (responseBody == null) return "Rate limited — try again in a moment"
    return try {
        val error = JSONObject(responseBody).optJSONObject("error")
        val type = error?.optString("type", "") ?: ""
        val code = error?.optString("code", "") ?: ""
        if (type.contains("insufficient_quota") || code.contains("insufficient_quota")) {
            "Insufficient quota — check your billing"
        } else {
            "Rate limited — try again in a moment"
        }
    } catch (_: Exception) {
        "Rate limited — try again in a moment"
    }
}
