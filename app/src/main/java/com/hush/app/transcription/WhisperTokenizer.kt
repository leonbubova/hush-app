package com.hush.app.transcription

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.InputStream

/**
 * Decodes Whisper BPE token IDs back to text.
 *
 * Loads vocab.json from assets on first use. The vocab maps token strings
 * to integer IDs; we invert it to get ID → string for decoding.
 *
 * Whisper uses byte-level BPE where:
 * - 'Ġ' (U+0120) represents a space character
 * - Special tokens (>= 50257) are filtered during decoding
 * - Token 50256 is <|endoftext|> (EOS)
 */
class WhisperTokenizer private constructor(
    private val vocabLoader: () -> InputStream,
) {

    constructor(context: Context) : this(
        vocabLoader = { context.assets.open(VOCAB_ASSET) }
    )

    companion object {
        private const val TAG = "WhisperTokenizer"
        private const val VOCAB_ASSET = "whisper_vocab.json"

        // Special tokens start at this ID and should be filtered from output
        const val SPECIAL_TOKEN_START = 50257

        // The base vocab <|endoftext|> token
        const val END_OF_TEXT = 50256L

        /**
         * Create a tokenizer from a raw InputStream (for testing).
         */
        internal fun fromInputStream(inputStream: () -> InputStream) =
            WhisperTokenizer(vocabLoader = inputStream)
    }

    // Lazy-loaded reverse vocab: ID → token string
    private val idToToken: Map<Int, String> by lazy { loadVocab() }

    /**
     * Decode a list of token IDs to text.
     * Filters special tokens and converts BPE encoding to readable text.
     *
     * @param tokenIds List of token IDs from the decoder
     * @param eosId End-of-sequence token ID (from model metadata)
     * @return Decoded text string
     */
    fun decode(tokenIds: List<Long>, eosId: Long = END_OF_TEXT): String {
        val sb = StringBuilder()

        for (id in tokenIds) {
            // Stop at EOS
            if (id == eosId) break

            // Skip special tokens (SOT, language, task, timestamps, etc.)
            if (id >= SPECIAL_TOKEN_START) continue

            // Also skip the base EOS token
            if (id == END_OF_TEXT) continue

            val token = idToToken[id.toInt()]
            if (token != null) {
                sb.append(token)
            } else {
                Log.w(TAG, "Unknown token ID: $id")
            }
        }

        // Convert BPE encoding to readable text
        return cleanBpeText(sb.toString())
    }

    /**
     * Clean BPE-encoded text:
     * - Replace 'Ġ' (U+0120) with space
     * - Handle other byte-level BPE artifacts
     */
    private fun cleanBpeText(text: String): String {
        return text
            .replace('\u0120', ' ')  // Ġ → space
            .replace('\u010a', '\n') // Ċ → newline
            .trim()
    }

    private fun loadVocab(): Map<Int, String> {
        Log.i(TAG, "Loading tokenizer vocab...")
        val startTime = System.currentTimeMillis()

        val jsonStr = vocabLoader().bufferedReader().use { it.readText() }
        val jsonObj = JSONObject(jsonStr)

        val reverseMap = HashMap<Int, String>(jsonObj.length())
        val keys = jsonObj.keys()
        while (keys.hasNext()) {
            val token = keys.next()
            val id = jsonObj.getInt(token)
            reverseMap[id] = token
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.i(TAG, "Loaded ${reverseMap.size} tokens in ${elapsed}ms")

        return reverseMap
    }
}
