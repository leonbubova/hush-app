package com.flowvoice.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

object VoxtralApi {

    private const val TAG = "VoxtralApi"
    private const val ENDPOINT = "https://api.mistral.ai/v1/audio/transcriptions"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun transcribe(audioFile: File, apiKey: String): String? {
        Log.d(TAG, "transcribe: file=${audioFile.absolutePath} size=${audioFile.length()} bytes, keyLength=${apiKey.length}")

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
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        return try {
            Log.d(TAG, "transcribe: sending request...")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            Log.d(TAG, "transcribe: response code=${response.code}, body=$responseBody")
            if (response.isSuccessful) {
                val json = JSONObject(responseBody ?: return null)
                json.getString("text")
            } else {
                Log.e(TAG, "transcribe: API error ${response.code}: $responseBody")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "transcribe: exception", e)
            null
        }
    }
}
