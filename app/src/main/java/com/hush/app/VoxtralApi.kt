package com.hush.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import android.util.Log
import java.io.File
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

sealed class TranscribeResult {
    data class Success(val text: String) : TranscribeResult()
    data class Error(val code: Int?, val message: String) : TranscribeResult()
}

object VoxtralApi {

    private const val TAG = "VoxtralApi"
    private const val ENDPOINT = "https://api.mistral.ai/v1/audio/transcriptions"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun transcribe(audioFile: File, apiKey: String, endpoint: String = ENDPOINT): TranscribeResult {
        Log.i(TAG, "transcribe: file=${audioFile.absolutePath} size=${audioFile.length()} bytes, keyLength=${apiKey.length}")

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "voxtral-mini-latest")
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/mp4".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        return try {
            Log.i(TAG, "transcribe: sending request...")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            Log.i(TAG, "transcribe: response code=${response.code}, body=$responseBody")
            if (response.isSuccessful) {
                val json = JSONObject(responseBody ?: return TranscribeResult.Error(null, "Empty response from server"))
                TranscribeResult.Success(json.getString("text"))
            } else {
                val message = when (response.code) {
                    401 -> "Invalid API key"
                    429 -> "Rate limited — try again in a moment"
                    in 500..599 -> "Server error (${response.code})"
                    else -> "API error ${response.code}"
                }
                Log.e(TAG, "transcribe: $message: $responseBody")
                TranscribeResult.Error(response.code, message)
            }
        } catch (e: UnknownHostException) {
            Log.e(TAG, "transcribe: no internet", e)
            TranscribeResult.Error(null, "No internet connection")
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "transcribe: timeout", e)
            TranscribeResult.Error(null, "Request timed out")
        } catch (e: Exception) {
            Log.e(TAG, "transcribe: exception", e)
            TranscribeResult.Error(null, "Network error: ${e.message ?: "unknown"}")
        }
    }
}
