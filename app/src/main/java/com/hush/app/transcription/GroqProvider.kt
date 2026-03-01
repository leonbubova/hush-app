package com.hush.app.transcription

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class GroqProvider(
    private val config: ProviderConfig.Groq,
    private val endpointOverride: String? = null,
) : TranscriptionProvider {

    override val displayName = "Groq"
    override val id = ProviderConfig.PROVIDER_GROQ
    override val requiresNetwork = true

    override suspend fun transcribe(audioFile: File): TranscribeResult {
        val apiKey = config.apiKey
        if (apiKey.isBlank()) {
            return TranscribeResult.Error(null, "No API key configured")
        }

        Log.i(TAG, "transcribe: file=${audioFile.absolutePath} size=${audioFile.length()} bytes")

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", config.model)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/mp4".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url(endpointOverride ?: ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            Log.i(TAG, "transcribe: response code=${response.code}")
            if (response.isSuccessful) {
                val json = JSONObject(responseBody ?: return TranscribeResult.Error(null, "Empty response from server"))
                TranscribeResult.Success(json.getString("text"))
            } else {
                val message = when (response.code) {
                    401 -> "Invalid API key"
                    429 -> parse429Error(responseBody)
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

    companion object {
        private const val TAG = "GroqProvider"
        private const val ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
        private val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
