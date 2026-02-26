package com.hush.app.transcription

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Converts M4A/AAC audio files to 16kHz mono Float32 PCM arrays
 * suitable for Whisper model input via ExecuTorch.
 *
 * Pipeline: demux M4A → decode AAC → stereo→mono → resample 44100→16000 → normalize to [-1.0, 1.0]
 */
object AudioConverter {

    private const val TARGET_SAMPLE_RATE = 16000
    private const val TIMEOUT_US = 10_000L

    /**
     * Decode an audio file to 16kHz mono Float32 PCM.
     * @return FloatArray of samples normalized to [-1.0, 1.0]
     * @throws AudioConversionException on failure
     */
    fun decodeToFloat16kMono(audioFile: File): FloatArray {
        if (!audioFile.exists()) {
            throw AudioConversionException("Audio file not found: ${audioFile.absolutePath}")
        }

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(audioFile.absolutePath)

            val audioTrackIndex = findAudioTrack(extractor)
                ?: throw AudioConversionException("No audio track found in file")

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw AudioConversionException("No MIME type in audio track")

            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0)
                codec.start()

                val rawPcm = decodeAllFrames(extractor, codec)

                val sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

                val monoSamples = pcmBytesToFloat(rawPcm, channelCount)
                return resample(monoSamples, sourceSampleRate, TARGET_SAMPLE_RATE)
            } finally {
                codec.stop()
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    private fun decodeAllFrames(extractor: MediaExtractor, codec: MediaCodec): ByteArray {
        val outputChunks = mutableListOf<ByteArray>()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false

        while (true) {
            // Feed input
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            // Drain output
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outputIndex >= 0) {
                if (bufferInfo.size > 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                    val chunk = ByteArray(bufferInfo.size)
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.get(chunk)
                    outputChunks.add(chunk)
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Format changed, continue
            }
        }

        val totalSize = outputChunks.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0
        for (chunk in outputChunks) {
            System.arraycopy(chunk, 0, result, offset, chunk.size)
            offset += chunk.size
        }
        return result
    }

    /**
     * Convert raw PCM Int16 bytes to mono Float32 normalized to [-1.0, 1.0].
     * Exposed as internal for unit testing of the math.
     */
    internal fun pcmBytesToFloat(pcmBytes: ByteArray, channelCount: Int): FloatArray {
        val shortBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val totalSamples = shortBuffer.remaining()
        val monoSamples = totalSamples / channelCount

        val result = FloatArray(monoSamples)
        for (i in 0 until monoSamples) {
            if (channelCount == 1) {
                result[i] = shortBuffer.get() / 32768f
            } else {
                var sum = 0f
                for (ch in 0 until channelCount) {
                    sum += shortBuffer.get() / 32768f
                }
                result[i] = sum / channelCount
            }
        }
        return result
    }

    /**
     * Resample audio using linear interpolation.
     * Exposed as internal for unit testing.
     */
    internal fun resample(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate) return input.copyOf()
        if (input.isEmpty()) return floatArrayOf()

        val ratio = fromRate.toDouble() / toRate.toDouble()
        val outputLength = ((input.size - 1) / ratio).toInt() + 1
        val output = FloatArray(outputLength)

        for (i in 0 until outputLength) {
            val srcPos = i * ratio
            val srcIndex = srcPos.toInt()
            val fraction = (srcPos - srcIndex).toFloat()

            output[i] = if (srcIndex + 1 < input.size) {
                input[srcIndex] * (1f - fraction) + input[srcIndex + 1] * fraction
            } else {
                input[srcIndex]
            }
        }
        return output
    }
}

class AudioConversionException(message: String, cause: Throwable? = null) : Exception(message, cause)
