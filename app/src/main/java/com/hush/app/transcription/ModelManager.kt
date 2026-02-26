package com.hush.app.transcription

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class ModelInfo(
    val id: String,
    val displayName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
) {
    val fileName: String
        get() {
            val segments = downloadUrl.trimEnd('/').split('/')
            val file = segments.last()
            val version = segments.getOrNull(segments.size - 2)
                ?.takeIf { it.startsWith("v") }
            return if (version != null) "${version}_$file" else file
        }
}

enum class ModelStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    ERROR,
}

class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        private const val MODELS_DIR = "models"

        val AVAILABLE_MODELS = listOf(
            ModelInfo(
                id = "whisper-tiny-en-q4",
                displayName = "Whisper tiny.en INT4 (English, 132 MB)",
                downloadUrl = "https://github.com/leonbubova/hush-app-models/releases/download/v0.2.0/whisper_tiny_en_q4.pte",
                sizeBytes = 138_482_176L,
            ),
            ModelInfo(
                id = "whisper-tiny-en-q8",
                displayName = "Whisper tiny.en INT8 (English, 136 MB)",
                downloadUrl = "https://github.com/leonbubova/hush-app-models/releases/download/v0.2.0/whisper_tiny_en_q8.pte",
                sizeBytes = 142_690_304L,
            ),
            ModelInfo(
                id = "whisper-tiny-en-fp32",
                displayName = "Whisper tiny.en FP32 (English, 220 MB)",
                downloadUrl = "https://github.com/leonbubova/hush-app-models/releases/download/v0.2.0/whisper_tiny_en_fp32.pte",
                sizeBytes = 230_960_384L,
            ),
        )

        fun getModelInfo(modelId: String): ModelInfo? =
            AVAILABLE_MODELS.find { it.id == modelId }
    }

    private val _statuses = MutableStateFlow<Map<String, ModelStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, ModelStatus>> = _statuses.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    init {
        refreshStatuses()
    }

    fun refreshStatuses() {
        cleanOrphanedModels()
        val map = mutableMapOf<String, ModelStatus>()
        for (model in AVAILABLE_MODELS) {
            map[model.id] = if (getModelFile(model).exists()) ModelStatus.READY else ModelStatus.NOT_DOWNLOADED
        }
        _statuses.value = map
    }

    internal fun cleanOrphanedModels() {
        val modelsDir = getModelsDir()
        if (!modelsDir.exists()) return

        val allowedNames = AVAILABLE_MODELS.flatMap { listOf(it.fileName, "${it.fileName}.tmp") }.toSet()

        modelsDir.listFiles()?.forEach { file ->
            if (file.name in allowedNames) return@forEach
            if (file.name.endsWith(".pte") || file.name.endsWith(".tmp")) {
                val sizeMb = file.length() / (1024 * 1024)
                Log.i(TAG, "Deleting orphaned model file: ${file.name} (${sizeMb} MB)")
                file.delete()
            } else {
                Log.i(TAG, "Skipping unexpected file in models dir: ${file.name}")
            }
        }
    }

    fun getModelStatus(modelId: String): ModelStatus {
        val info = getModelInfo(modelId) ?: return ModelStatus.NOT_DOWNLOADED
        return if (getModelFile(info).exists()) ModelStatus.READY else ModelStatus.NOT_DOWNLOADED
    }

    fun getModelPath(modelId: String): String? {
        val info = getModelInfo(modelId) ?: return null
        val file = getModelFile(info)
        return if (file.exists()) file.absolutePath else null
    }

    suspend fun downloadModel(modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val info = getModelInfo(modelId)
            ?: return@withContext Result.failure(IllegalArgumentException("Unknown model: $modelId"))

        if (info.downloadUrl.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Download URL not configured for $modelId"))
        }

        updateStatus(modelId, ModelStatus.DOWNLOADING)
        updateProgress(modelId, 0f)

        try {
            val request = Request.Builder().url(info.downloadUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                updateStatus(modelId, ModelStatus.ERROR)
                return@withContext Result.failure(
                    RuntimeException("Download failed: HTTP ${response.code}")
                )
            }

            val body = response.body
                ?: run {
                    updateStatus(modelId, ModelStatus.ERROR)
                    return@withContext Result.failure(RuntimeException("Empty response body"))
                }

            val contentLength = body.contentLength()
            val modelFile = getModelFile(info)
            modelFile.parentFile?.mkdirs()

            val tempFile = File(modelFile.parent, "${modelFile.name}.tmp")

            FileOutputStream(tempFile).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Long = 0

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesRead += read

                        if (contentLength > 0) {
                            updateProgress(modelId, bytesRead.toFloat() / contentLength)
                        }
                    }
                }
            }

            tempFile.renameTo(modelFile)
            updateStatus(modelId, ModelStatus.READY)
            updateProgress(modelId, 1f)
            Log.i(TAG, "Model $modelId downloaded to ${modelFile.absolutePath}")
            Result.success(Unit)
        } catch (e: Exception) {
            updateStatus(modelId, ModelStatus.ERROR)
            Log.e(TAG, "Failed to download model $modelId", e)
            Result.failure(e)
        }
    }

    fun deleteModel(modelId: String) {
        val info = getModelInfo(modelId) ?: return
        val file = getModelFile(info)
        if (file.exists()) {
            file.delete()
        }
        updateStatus(modelId, ModelStatus.NOT_DOWNLOADED)
        updateProgress(modelId, 0f)
    }

    private fun getModelFile(info: ModelInfo): File =
        File(context.filesDir, "$MODELS_DIR/${info.fileName}")

    internal fun getModelsDir(): File = File(context.filesDir, MODELS_DIR)

    private fun updateStatus(modelId: String, status: ModelStatus) {
        _statuses.value = _statuses.value.toMutableMap().apply { put(modelId, status) }
    }

    private fun updateProgress(modelId: String, progress: Float) {
        _downloadProgress.value = _downloadProgress.value.toMutableMap().apply { put(modelId, progress) }
    }
}
