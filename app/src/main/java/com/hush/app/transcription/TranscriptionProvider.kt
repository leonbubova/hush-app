package com.hush.app.transcription

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
