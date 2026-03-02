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
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class ModelInfo(
    val id: String,
    val displayName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
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

data class MoonshineModelInfo(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val components: List<String>,
    val totalSizeBytes: Long,
) {
    val dirName: String get() = id
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
        private const val MOONSHINE_DIR = "moonshine"

        val AVAILABLE_MODELS = listOf(
            ModelInfo(
                id = "whisper-tiny-en-q4",
                displayName = "Whisper tiny.en INT4 (English, 132 MB)",
                downloadUrl = "https://github.com/leonbubova/hush-app-models/releases/download/v0.2.0/whisper_tiny_en_q4.pte",
                sizeBytes = 138_482_176L,
                sha256 = "e70aabd765950263eadced2c1ca3cfc727f766664304dc6dff52509dcdedb9cb",
            ),
            ModelInfo(
                id = "whisper-tiny-en-q8",
                displayName = "Whisper tiny.en INT8 (English, 136 MB)",
                downloadUrl = "https://github.com/leonbubova/hush-app-models/releases/download/v0.2.0/whisper_tiny_en_q8.pte",
                sizeBytes = 142_690_304L,
                sha256 = "2b0de6b24fb9983a9140a6f90d3b8d83107291352d20ad32c9fc08815d65d510",
            ),
            ModelInfo(
                id = "whisper-tiny-en-fp32",
                displayName = "Whisper tiny.en FP32 (English, 220 MB)",
                downloadUrl = "https://github.com/leonbubova/hush-app-models/releases/download/v0.2.0/whisper_tiny_en_fp32.pte",
                sizeBytes = 230_960_384L,
                sha256 = "801d970cfbc4c86be7795618662db1c5f3fb7d5ec48b629d55dc9496d00b56af",
            ),
        )

        val AVAILABLE_MOONSHINE_MODELS = listOf(
            MoonshineModelInfo(
                id = "tiny-streaming-en",
                displayName = "Moonshine tiny (English, ~26 MB)",
                baseUrl = "https://download.moonshine.ai/model/tiny-streaming-en/quantized",
                components = listOf(
                    "adapter.ort",
                    "cross_kv.ort",
                    "decoder_kv.ort",
                    "encoder.ort",
                    "frontend.ort",
                    "streaming_config.json",
                    "tokenizer.bin",
                ),
                totalSizeBytes = 27_262_976L,
            ),
        )

        fun getModelInfo(modelId: String): ModelInfo? =
            AVAILABLE_MODELS.find { it.id == modelId }

        fun getMoonshineModelInfo(modelId: String): MoonshineModelInfo? =
            AVAILABLE_MOONSHINE_MODELS.find { it.id == modelId }
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
        for (model in AVAILABLE_MOONSHINE_MODELS) {
            map[model.id] = getMoonshineModelStatus(model.id)
        }
        _statuses.value = map
    }

    internal fun cleanOrphanedModels() {
        val modelsDir = getModelsDir()
        if (!modelsDir.exists()) return

        val allowedNames = AVAILABLE_MODELS.flatMap { listOf(it.fileName, "${it.fileName}.tmp") }.toSet()

        modelsDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                if (file.name == MOONSHINE_DIR) cleanOrphanedMoonshineModels(file)
                return@forEach
            }
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

    private fun cleanOrphanedMoonshineModels(moonshineDir: File) {
        val knownIds = AVAILABLE_MOONSHINE_MODELS.associateBy { it.dirName }

        moonshineDir.listFiles()?.forEach { dir ->
            if (!dir.isDirectory) {
                dir.delete()
                return@forEach
            }

            val info = knownIds[dir.name]
            if (info == null) {
                Log.i(TAG, "Deleting orphaned moonshine model dir: ${dir.name}")
                dir.deleteRecursively()
                return@forEach
            }

            // Clean .tmp files from interrupted downloads
            dir.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { tmp ->
                Log.i(TAG, "Deleting orphaned moonshine tmp: ${dir.name}/${tmp.name}")
                tmp.delete()
            }

            // Delete entire dir if not all components are present
            val allPresent = info.components.all { File(dir, it).exists() }
            if (!allPresent) {
                Log.i(TAG, "Deleting incomplete moonshine model: ${dir.name}")
                dir.deleteRecursively()
            }
        }
    }

    fun getModelStatus(modelId: String): ModelStatus {
        // Check whisper models
        val info = getModelInfo(modelId)
        if (info != null) {
            return if (getModelFile(info).exists()) ModelStatus.READY else ModelStatus.NOT_DOWNLOADED
        }
        // Check moonshine models
        return getMoonshineModelStatus(modelId)
    }

    fun getMoonshineModelStatus(modelId: String): ModelStatus {
        val info = getMoonshineModelInfo(modelId) ?: return ModelStatus.NOT_DOWNLOADED
        val dir = getMoonshineModelDir(info)
        if (!dir.exists()) return ModelStatus.NOT_DOWNLOADED
        val allPresent = info.components.all { File(dir, it).exists() }
        return if (allPresent) ModelStatus.READY else ModelStatus.NOT_DOWNLOADED
    }

    fun getModelPath(modelId: String): String? {
        val info = getModelInfo(modelId) ?: return null
        val file = getModelFile(info)
        return if (file.exists()) file.absolutePath else null
    }

    fun getMoonshineModelPath(modelId: String): String? {
        val info = getMoonshineModelInfo(modelId) ?: return null
        val dir = getMoonshineModelDir(info)
        val allPresent = info.components.all { File(dir, it).exists() }
        return if (allPresent) dir.absolutePath else null
    }

    suspend fun downloadModel(modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        // Check if it's a moonshine model first
        if (getMoonshineModelInfo(modelId) != null) {
            return@withContext downloadMoonshineModel(modelId)
        }

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

            val digest = MessageDigest.getInstance("SHA-256")

            FileOutputStream(tempFile).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Long = 0

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        bytesRead += read

                        if (contentLength > 0) {
                            updateProgress(modelId, bytesRead.toFloat() / contentLength)
                        }
                    }
                }
            }

            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (actualHash != info.sha256) {
                tempFile.delete()
                updateStatus(modelId, ModelStatus.ERROR)
                Log.e(TAG, "SHA256 mismatch for $modelId: expected=${info.sha256}, actual=$actualHash")
                return@withContext Result.failure(
                    RuntimeException("SHA256 mismatch for $modelId: expected=${info.sha256}, actual=$actualHash")
                )
            }

            tempFile.renameTo(modelFile)
            updateStatus(modelId, ModelStatus.READY)
            updateProgress(modelId, 1f)
            Log.i(TAG, "Model $modelId downloaded and SHA256 verified: $actualHash")
            Result.success(Unit)
        } catch (e: Exception) {
            updateStatus(modelId, ModelStatus.ERROR)
            Log.e(TAG, "Failed to download model $modelId", e)
            Result.failure(e)
        }
    }

    private suspend fun downloadMoonshineModel(modelId: String): Result<Unit> {
        val info = getMoonshineModelInfo(modelId)
            ?: return Result.failure(IllegalArgumentException("Unknown moonshine model: $modelId"))

        updateStatus(modelId, ModelStatus.DOWNLOADING)
        updateProgress(modelId, 0f)

        val dir = getMoonshineModelDir(info)
        dir.mkdirs()

        return try {
            val totalComponents = info.components.size
            for ((index, component) in info.components.withIndex()) {
                val url = "${info.baseUrl}/$component"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    updateStatus(modelId, ModelStatus.ERROR)
                    return Result.failure(
                        RuntimeException("Download failed for $component: HTTP ${response.code}")
                    )
                }

                val body = response.body ?: run {
                    updateStatus(modelId, ModelStatus.ERROR)
                    return Result.failure(RuntimeException("Empty response body for $component"))
                }

                val targetFile = File(dir, component)
                val tempFile = File(dir, "$component.tmp")

                FileOutputStream(tempFile).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                        }
                    }
                }

                tempFile.renameTo(targetFile)
                updateProgress(modelId, (index + 1).toFloat() / totalComponents)
                Log.i(TAG, "Downloaded moonshine component: $component")
            }

            updateStatus(modelId, ModelStatus.READY)
            updateProgress(modelId, 1f)
            Log.i(TAG, "Moonshine model $modelId download complete")
            Result.success(Unit)
        } catch (e: Exception) {
            updateStatus(modelId, ModelStatus.ERROR)
            Log.e(TAG, "Failed to download moonshine model $modelId", e)
            Result.failure(e)
        }
    }

    fun deleteModel(modelId: String) {
        // Check whisper models
        val info = getModelInfo(modelId)
        if (info != null) {
            val file = getModelFile(info)
            if (file.exists()) file.delete()
            updateStatus(modelId, ModelStatus.NOT_DOWNLOADED)
            updateProgress(modelId, 0f)
            return
        }

        // Check moonshine models
        val moonInfo = getMoonshineModelInfo(modelId)
        if (moonInfo != null) {
            val dir = getMoonshineModelDir(moonInfo)
            if (dir.exists()) dir.deleteRecursively()
            updateStatus(modelId, ModelStatus.NOT_DOWNLOADED)
            updateProgress(modelId, 0f)
        }
    }

    private fun getModelFile(info: ModelInfo): File =
        File(context.filesDir, "$MODELS_DIR/${info.fileName}")

    private fun getMoonshineModelDir(info: MoonshineModelInfo): File =
        File(context.filesDir, "$MODELS_DIR/$MOONSHINE_DIR/${info.dirName}")

    internal fun getModelsDir(): File = File(context.filesDir, MODELS_DIR)

    private fun updateStatus(modelId: String, status: ModelStatus) {
        _statuses.value = _statuses.value.toMutableMap().apply { put(modelId, status) }
    }

    private fun updateProgress(modelId: String, progress: Float) {
        _downloadProgress.value = _downloadProgress.value.toMutableMap().apply { put(modelId, progress) }
    }
}
