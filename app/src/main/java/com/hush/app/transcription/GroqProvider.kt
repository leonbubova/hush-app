package com.hush.app.transcription

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File

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
            return TranscribeResult.Error(null, ErrorMessages.noApiKey(displayName))
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
                val json = JSONObject(responseBody ?: return TranscribeResult.Error(null, ErrorMessages.emptyResponse(displayName)))
                TranscribeResult.Success(json.getString("text"))
            } else {
                val message = ErrorMessages.forHttpError(response.code, responseBody, displayName)
                Log.e(TAG, "transcribe: $message: $responseBody")
                TranscribeResult.Error(response.code, message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "transcribe: exception", e)
            TranscribeResult.Error(null, ErrorMessages.forNetworkException(e))
        }
    }

    companion object {
        private const val TAG = "GroqProvider"
        private const val ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
        private val client = HttpClientFactory.createApiClient()
    }
}
