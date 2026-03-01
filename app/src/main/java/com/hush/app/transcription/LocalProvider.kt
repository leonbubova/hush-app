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

    override val displayName = "Local (Deprecated)"
    override val id = PROVIDER_LOCAL
    override val requiresNetwork = false

    private val modelManager = ModelManager(context)
    private val tokenizer = WhisperTokenizer(context)
    private val moduleMutex = Mutex()
    private var cachedModule: Module? = null
    private var cachedModelPath: String? = null

    // Model metadata (populated on first load)
    private var eosId: Long = DEFAULT_EOS_ID
    private var decoderStartTokenId: Long = DEFAULT_DECODER_START_TOKEN_ID
    private var maxSeqLen: Int = DEFAULT_MAX_SEQ_LEN

    companion object {
        private const val TAG = "LocalProvider"
        private const val MAX_DECODE_TOKENS = 448  // Whisper standard max for 30s audio

        // Whisper defaults (overridden by model metadata if available)
        private const val DEFAULT_EOS_ID = 50256L
        private const val DEFAULT_DECODER_START_TOKEN_ID = 50258L
        private const val DEFAULT_MAX_SEQ_LEN = 448
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
                    loadModelMetadata(cachedModule!!)
                }
                cachedModule!!
            }

            // 4. Compute mel spectrogram [1, 80, 3000]
            val melStartTime = System.currentTimeMillis()
            val melFeatures = MelSpectrogram.compute(audioData)
            val melTensor = Tensor.fromBlob(
                melFeatures,
                longArrayOf(1, MelSpectrogram.N_MELS.toLong(), MelSpectrogram.N_FRAMES.toLong())
            )
            val melElapsed = System.currentTimeMillis() - melStartTime
            Log.i(TAG, "Mel spectrogram computed in ${melElapsed}ms (${audioData.size} samples)")

            // 5. Run encoder
            Log.i(TAG, "Encoder input tensor shape: [1, ${MelSpectrogram.N_MELS}, ${MelSpectrogram.N_FRAMES}], size=${melFeatures.size}")
            val encoderStartTime = System.currentTimeMillis()
            val encoderOutput = try {
                module.execute("encoder", EValue.from(melTensor))
            } catch (e: Exception) {
                Log.e(TAG, "Encoder execution failed", e)
                // Dump ExecuTorch log buffer for more info
                try {
                    val logBuffer = module.readLogBuffer()
                    for (line in logBuffer) {
                        Log.e(TAG, "  [ET] $line")
                    }
                } catch (_: Exception) {}
                throw e
            }
            val encoderElapsed = System.currentTimeMillis() - encoderStartTime
            Log.i(TAG, "Encoder completed in ${encoderElapsed}ms")

            if (encoderOutput.isEmpty()) {
                return@withContext TranscribeResult.Error(null, "Encoder produced no output")
            }

            // 6. Autoregressive decoder loop
            val decoderStartTime = System.currentTimeMillis()
            val tokenIds = decoderLoop(module, encoderOutput[0])
            val decoderElapsed = System.currentTimeMillis() - decoderStartTime
            Log.i(TAG, "Decoder completed in ${decoderElapsed}ms (${tokenIds.size} tokens)")

            // 7. Decode tokens to text
            val outputText = tokenizer.decode(tokenIds, eosId)
            Log.i(TAG, "Transcription result: '$outputText'")
            Log.i(TAG, "Total inference time: ${melElapsed + encoderElapsed + decoderElapsed}ms")

            if (outputText.isBlank()) {
                return@withContext TranscribeResult.Error(null, "Transcription produced empty output")
            }

            TranscribeResult.Success(outputText.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Local transcription failed", e)
            TranscribeResult.Error(null, "Local transcription failed: ${e.message}")
        }
    }

    /**
     * Autoregressive decoder loop: generates tokens one at a time.
     *
     * For each step:
     * 1. Create decoder input tensor from the last generated token
     * 2. Execute text_decoder with (token, encoder_output, cache_position)
     * 3. Argmax on logits to get next token
     * 4. Stop on EOS or max tokens
     */
    private fun decoderLoop(module: Module, encoderOutput: EValue): List<Long> {
        val tokens = mutableListOf<Long>()
        var currentToken = decoderStartTokenId

        val maxTokens = minOf(MAX_DECODE_TOKENS, maxSeqLen)

        for (step in 0 until maxTokens) {
            // Decoder input: current token [1, 1]
            val tokenTensor = Tensor.fromBlob(
                longArrayOf(currentToken),
                longArrayOf(1, 1)
            )

            // Cache position scalar
            val positionTensor = Tensor.fromBlob(
                longArrayOf(step.toLong()),
                longArrayOf(1)
            )

            // Execute decoder — catch per-step failures to return partial results
            val decoderResult = try {
                module.execute(
                    "text_decoder",
                    EValue.from(tokenTensor),
                    encoderOutput,
                    EValue.from(positionTensor)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Decoder step $step failed: ${e.message}", e)
                break
            }

            if (decoderResult.isEmpty()) {
                Log.w(TAG, "Decoder returned empty result at step $step")
                break
            }

            // Get logits and find argmax
            val nextToken = argmax(decoderResult[0])

            if (nextToken == eosId) {
                Log.i(TAG, "EOS token at step $step")
                break
            }

            tokens.add(nextToken)
            currentToken = nextToken
        }

        if (tokens.size >= maxTokens) {
            Log.w(TAG, "Reached max decode tokens ($maxTokens) without EOS — output may be truncated")
        }

        return tokens
    }

    /**
     * Find the token ID with the highest logit value.
     */
    private fun argmax(logitsEValue: EValue): Long {
        val logits = logitsEValue.toTensor().dataAsFloatArray
        var maxIdx = 0
        var maxVal = logits[0]
        for (i in 1 until logits.size) {
            if (logits[i] > maxVal) {
                maxVal = logits[i]
                maxIdx = i
            }
        }
        return maxIdx.toLong()
    }

    /**
     * Load metadata and preload inference methods.
     */
    private fun loadModelMetadata(module: Module) {
        Log.i(TAG, "=== Loading model metadata ===")

        eosId = queryMetadataLong(module, "get_eos_id") ?: DEFAULT_EOS_ID
        Log.i(TAG, "  eos_id = $eosId")

        decoderStartTokenId = queryMetadataLong(module, "decoder_start_token_id")
            ?: DEFAULT_DECODER_START_TOKEN_ID
        Log.i(TAG, "  decoder_start_token_id = $decoderStartTokenId")

        maxSeqLen = queryMetadataLong(module, "get_max_seq_len")?.toInt() ?: DEFAULT_MAX_SEQ_LEN
        Log.i(TAG, "  max_seq_len = $maxSeqLen")

        val bosId = queryMetadataLong(module, "get_bos_id")
        Log.i(TAG, "  bos_id = $bosId")

        val vocabSize = queryMetadataLong(module, "get_vocab_size")
        Log.i(TAG, "  vocab_size = $vocabSize")

        // Preload inference methods and log their status
        for (method in listOf("encoder", "text_decoder")) {
            try {
                val status = module.loadMethod(method)
                Log.i(TAG, "  loadMethod('$method') → $status (0=OK)")
            } catch (e: Exception) {
                Log.e(TAG, "  loadMethod('$method') failed: ${e.message}")
            }
        }

        // Dump ExecuTorch internal log buffer for debugging
        try {
            val logBuffer = module.readLogBuffer()
            if (logBuffer.isNotEmpty()) {
                for (line in logBuffer) {
                    Log.i(TAG, "  [ET] $line")
                }
            }
        } catch (_: Exception) {}

        Log.i(TAG, "=== End metadata ===")
    }

    /**
     * Call a no-arg metadata method and extract the Long value.
     */
    private fun queryMetadataLong(module: Module, methodName: String): Long? {
        return try {
            module.loadMethod(methodName)
            val result = module.execute(methodName)
            if (result.isNotEmpty()) {
                val ev = result[0]
                when {
                    ev.isInt() -> ev.toInt()
                    ev.isTensor() -> ev.toTensor().dataAsLongArray.firstOrNull()
                    else -> {
                        Log.w(TAG, "$methodName returned unexpected type")
                        null
                    }
                }
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "$methodName query failed: ${e.message}")
            null
        }
    }

    fun release() {
        cachedModule?.destroy()
        cachedModule = null
        cachedModelPath = null
    }
}
