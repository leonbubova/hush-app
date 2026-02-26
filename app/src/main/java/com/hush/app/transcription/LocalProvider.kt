package com.hush.app.transcription

import android.content.Context
import android.util.Log
import com.hush.app.transcription.ProviderConfig.Companion.PROVIDER_LOCAL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File

class LocalProvider(
    private val config: ProviderConfig.Local,
    private val context: Context,
) : TranscriptionProvider {

    override val displayName = "Local (On-Device)"
    override val id = PROVIDER_LOCAL
    override val requiresNetwork = false

    private val modelManager = ModelManager(context)
    private val moduleMutex = Mutex()
    private var cachedModule: Module? = null
    private var cachedModelPath: String? = null

    companion object {
        private const val TAG = "LocalProvider"
    }

    override suspend fun transcribe(audioFile: File): TranscribeResult = withContext(Dispatchers.Default) {
        try {
            // 1. Check model is downloaded
            val modelPath = modelManager.getModelPath(config.model)
                ?: return@withContext TranscribeResult.Error(
                    null,
                    "Model not downloaded. Please download '${config.model}' in Settings."
                )

            // 2. Convert audio to 16kHz mono float PCM
            val audioData = try {
                AudioConverter.decodeToFloat16kMono(audioFile)
            } catch (e: AudioConversionException) {
                return@withContext TranscribeResult.Error(null, "Audio conversion failed: ${e.message}")
            }

            if (audioData.isEmpty()) {
                return@withContext TranscribeResult.Error(null, "Audio file is empty or could not be decoded")
            }

            // 3. Load model (cached)
            val module = moduleMutex.withLock {
                if (cachedModule == null || cachedModelPath != modelPath) {
                    cachedModule?.destroy()
                    Log.i(TAG, "Loading ExecuTorch model from $modelPath")
                    cachedModule = Module.load(modelPath)
                    cachedModelPath = modelPath
                }
                cachedModule!!
            }

            // 4. Run inference
            val inputTensor = Tensor.fromBlob(audioData, longArrayOf(1, audioData.size.toLong()))
            val result = module.forward(EValue.from(inputTensor))

            // 5. Decode output
            val outputText = decodeOutput(result)
            if (outputText.isBlank()) {
                return@withContext TranscribeResult.Error(null, "Transcription produced empty output")
            }

            TranscribeResult.Success(outputText.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Local transcription failed", e)
            TranscribeResult.Error(null, "Local transcription failed: ${e.message}")
        }
    }

    private fun decodeOutput(result: Array<EValue>): String {
        // ExecuTorch Whisper returns the transcribed text as a string tensor
        // or token IDs depending on the export configuration.
        // For the standard whisper export, the output is a string.
        return try {
            if (result.isNotEmpty()) {
                // Try string output first (preferred export format)
                result[0].toStr()
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode output as string, trying tensor", e)
            try {
                // Fallback: output may be token IDs as int tensor
                val tensor = result[0].toTensor()
                val tokenIds = tensor.dataAsLongArray
                // Token decoding would require a tokenizer — for now return raw IDs
                tokenIds.joinToString(" ")
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to decode output in any format", e2)
                ""
            }
        }
    }

    fun release() {
        cachedModule?.destroy()
        cachedModule = null
        cachedModelPath = null
    }
}
