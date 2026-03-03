package com.hush.app.transcription

import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ErrorMessages {

    fun noApiKey(provider: String): String =
        "$provider API key not configured \u2014 add it in Settings"

    fun forHttpError(code: Int, body: String?, provider: String): String = when (code) {
        401 -> "Invalid API key \u2014 check your $provider key in Settings"
        429 -> parse429Body(body)
        in 500..599 -> "$provider server is down \u2014 try again later"
        else -> "$provider request failed \u2014 try again"
    }

    fun forNetworkException(e: Exception): String = when (e) {
        is UnknownHostException -> "No internet \u2014 check your WiFi or mobile data"
        is SocketTimeoutException -> "Request timed out \u2014 check your connection and try again"
        else -> "Connection problem \u2014 check your internet and try again"
    }

    fun emptyResponse(provider: String): String =
        "$provider returned no text \u2014 try again"

    fun modelNotDownloaded(model: String): String =
        "Model not downloaded \u2014 download '$model' in Settings"

    fun moonshineNotDownloaded(): String =
        "Download the Moonshine model first \u2014 open the menu \u2192 Settings \u2192 Download"

    fun modelLoadFailed(): String =
        "Failed to load model \u2014 try re-downloading in Settings"

    fun audioConversionFailed(): String =
        "Audio could not be processed \u2014 try recording again"

    fun emptyAudio(): String =
        "No audio detected \u2014 speak louder or check your microphone"

    fun localTranscriptionFailed(): String =
        "On-device transcription failed \u2014 try again or switch to a cloud provider"

    fun emptyTranscription(): String =
        "No speech detected \u2014 try speaking closer to the mic"

    fun micUnavailable(): String =
        "Microphone unavailable \u2014 check permissions in phone Settings"

    fun recorderFailed(): String =
        "Recording failed \u2014 restart the app and try again"

    fun unexpectedError(): String =
        "Something went wrong \u2014 try again"

    fun downloadFailed(): String =
        "Download failed \u2014 check your connection and try again"

    fun exportFailed(): String =
        "Export failed \u2014 check storage permissions and try again"

    fun importFailed(): String =
        "Import failed \u2014 make sure the file is a valid Hush backup"

    private fun parse429Body(body: String?): String {
        if (body == null) return "Rate limited \u2014 try again in a moment"
        return try {
            val error = JSONObject(body).optJSONObject("error")
            val type = error?.optString("type", "") ?: ""
            val code = error?.optString("code", "") ?: ""
            if (type.contains("insufficient_quota") || code.contains("insufficient_quota")) {
                "Insufficient quota \u2014 check your billing"
            } else {
                "Rate limited \u2014 try again in a moment"
            }
        } catch (_: Exception) {
            "Rate limited \u2014 try again in a moment"
        }
    }
}
