package com.hush.app.transcription

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Computes 80-channel log-mel spectrograms from 16kHz mono PCM audio,
 * matching the preprocessing expected by OpenAI Whisper models.
 *
 * Parameters match whisper's audio.py:
 * - N_FFT = 400 (25ms window at 16kHz)
 * - HOP_LENGTH = 160 (10ms hop at 16kHz)
 * - N_MELS = 80
 * - CHUNK_LENGTH = 30 seconds → 3000 frames
 * - SAMPLE_RATE = 16000
 */
object MelSpectrogram {

    const val SAMPLE_RATE = 16000
    const val N_FFT = 400
    const val HOP_LENGTH = 160
    const val N_MELS = 80
    const val CHUNK_LENGTH = 30 // seconds
    const val N_FRAMES = CHUNK_LENGTH * SAMPLE_RATE / HOP_LENGTH // 3000

    /**
     * Compute log-mel spectrogram from 16kHz mono PCM audio.
     * @param audioData Float array of PCM samples normalized to [-1.0, 1.0]
     * @return Flat float array of shape [1, 80, 3000] (batch, mels, frames)
     */
    fun compute(audioData: FloatArray): FloatArray {
        // Pad or truncate audio to exactly 30 seconds (480000 samples)
        val targetSamples = CHUNK_LENGTH * SAMPLE_RATE
        val padded = if (audioData.size >= targetSamples) {
            audioData.copyOf(targetSamples)
        } else {
            FloatArray(targetSamples).also { buf ->
                audioData.copyInto(buf)
                // Rest is already zero-padded
            }
        }

        // 1. STFT with Hann window
        val magnitudes = stft(padded)

        // 2. Apply mel filterbank
        val melFilters = melFilterbank()
        val melSpec = applyMelFilterbank(magnitudes, melFilters)

        // 3. Log scale (matching whisper's log_mel_spectrogram)
        logScale(melSpec)

        // 4. Return as flat array [1, N_MELS, N_FRAMES]
        return melSpec
    }

    /**
     * Compute STFT magnitude-squared spectrogram.
     * Returns array of shape [n_fft/2 + 1, n_frames] (flattened).
     */
    internal fun stft(audio: FloatArray): FloatArray {
        val window = hannWindow(N_FFT)
        val nFreqs = N_FFT / 2 + 1
        val nFrames = N_FRAMES

        val magnitudes = FloatArray(nFreqs * nFrames)

        // Pre-allocate FFT buffers
        val real = FloatArray(N_FFT)
        val imag = FloatArray(N_FFT)

        for (frame in 0 until nFrames) {
            val start = frame * HOP_LENGTH

            // Apply window and copy to real buffer
            for (i in 0 until N_FFT) {
                val sampleIdx = start + i
                real[i] = if (sampleIdx < audio.size) audio[sampleIdx] * window[i] else 0f
                imag[i] = 0f
            }

            // In-place FFT
            fft(real, imag)

            // Store power spectrum (magnitude squared) for first nFreqs bins
            for (k in 0 until nFreqs) {
                magnitudes[k * nFrames + frame] = real[k] * real[k] + imag[k] * imag[k]
            }
        }

        return magnitudes
    }

    /**
     * Hann window of given size.
     */
    internal fun hannWindow(size: Int): FloatArray {
        return FloatArray(size) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / size))).toFloat()
        }
    }

    /**
     * In-place Cooley-Tukey FFT. Input arrays must have power-of-2 length.
     * Since N_FFT=400 is not a power of 2, we use Bluestein's algorithm
     * by zero-padding to the next power of 2 (512).
     */
    internal fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        if (n and (n - 1) == 0) {
            // Already power of 2
            fftRadix2(real, imag, n)
        } else {
            // Zero-pad to next power of 2
            val m = nextPowerOf2(n)
            val rPad = FloatArray(m)
            val iPad = FloatArray(m)
            real.copyInto(rPad, 0, 0, n)
            fftRadix2(rPad, iPad, m)
            // Copy back the bins we need
            rPad.copyInto(real, 0, 0, n)
            iPad.copyInto(imag, 0, 0, n)
        }
    }

    private fun nextPowerOf2(n: Int): Int {
        var v = n - 1
        v = v or (v shr 1)
        v = v or (v shr 2)
        v = v or (v shr 4)
        v = v or (v shr 8)
        v = v or (v shr 16)
        return v + 1
    }

    /**
     * Standard radix-2 Cooley-Tukey FFT (in-place, decimation-in-time).
     */
    private fun fftRadix2(real: FloatArray, imag: FloatArray, n: Int) {
        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                var tmp = real[i]; real[i] = real[j]; real[j] = tmp
                tmp = imag[i]; imag[i] = imag[j]; imag[j] = tmp
            }
            var m = n shr 1
            while (m >= 1 && j >= m) {
                j -= m
                m = m shr 1
            }
            j += m
        }

        // Butterfly operations
        var step = 2
        while (step <= n) {
            val halfStep = step shr 1
            val angleStep = -2.0 * PI / step
            for (k in 0 until halfStep) {
                val angle = k * angleStep
                val wr = cos(angle).toFloat()
                val wi = kotlin.math.sin(angle).toFloat()
                var i = k
                while (i < n) {
                    val jj = i + halfStep
                    val tr = wr * real[jj] - wi * imag[jj]
                    val ti = wr * imag[jj] + wi * real[jj]
                    real[jj] = real[i] - tr
                    imag[jj] = imag[i] - ti
                    real[i] += tr
                    imag[i] += ti
                    i += step
                }
            }
            step = step shl 1
        }
    }

    /**
     * Create an 80-channel mel filterbank for the given FFT size and sample rate.
     * Returns array of shape [N_MELS, N_FFT/2 + 1] (flattened).
     */
    internal fun melFilterbank(): FloatArray {
        val nFreqs = N_FFT / 2 + 1
        val filters = FloatArray(N_MELS * nFreqs)

        val fMin = 0.0
        val fMax = SAMPLE_RATE / 2.0

        val melMin = hzToMel(fMin)
        val melMax = hzToMel(fMax)

        // N_MELS + 2 equally spaced points in mel space
        val melPoints = DoubleArray(N_MELS + 2) { i ->
            melMin + i * (melMax - melMin) / (N_MELS + 1)
        }
        val hzPoints = DoubleArray(melPoints.size) { melToHz(melPoints[it]) }

        // FFT bin frequencies
        val fftFreqs = DoubleArray(nFreqs) { it * SAMPLE_RATE.toDouble() / N_FFT }

        for (m in 0 until N_MELS) {
            val fLeft = hzPoints[m]
            val fCenter = hzPoints[m + 1]
            val fRight = hzPoints[m + 2]

            for (k in 0 until nFreqs) {
                val freq = fftFreqs[k]
                val weight = when {
                    freq < fLeft -> 0.0
                    freq <= fCenter -> (freq - fLeft) / (fCenter - fLeft)
                    freq <= fRight -> (fRight - freq) / (fRight - fCenter)
                    else -> 0.0
                }
                filters[m * nFreqs + k] = weight.toFloat()
            }

            // Slaney-style normalization (area normalization)
            val enorm = 2.0 / (hzPoints[m + 2] - hzPoints[m])
            for (k in 0 until nFreqs) {
                filters[m * nFreqs + k] *= enorm.toFloat()
            }
        }

        return filters
    }

    /**
     * Apply mel filterbank to power spectrogram.
     * magnitudes: [nFreqs, nFrames], filters: [nMels, nFreqs]
     * Returns: [nMels, nFrames] flattened (= nMels * nFrames)
     */
    internal fun applyMelFilterbank(magnitudes: FloatArray, filters: FloatArray): FloatArray {
        val nFreqs = N_FFT / 2 + 1
        val nFrames = N_FRAMES
        val melSpec = FloatArray(N_MELS * nFrames)

        for (m in 0 until N_MELS) {
            val filterOffset = m * nFreqs
            for (t in 0 until nFrames) {
                var sum = 0f
                for (k in 0 until nFreqs) {
                    sum += filters[filterOffset + k] * magnitudes[k * nFrames + t]
                }
                melSpec[m * nFrames + t] = max(sum, 1e-10f)
            }
        }

        return melSpec
    }

    /**
     * Apply log scaling matching Whisper's implementation:
     * 1. Take log10 of mel spectrogram
     * 2. Clamp to max(log10(mel)) - 8.0
     * 3. Normalize to range [0, 1] by: (mel + 4.0) / 4.0
     */
    internal fun logScale(melSpec: FloatArray) {
        // Convert to log10 scale
        for (i in melSpec.indices) {
            melSpec[i] = log10(melSpec[i])
        }

        // Find max value
        var maxVal = -Float.MAX_VALUE
        for (i in melSpec.indices) {
            if (melSpec[i] > maxVal) maxVal = melSpec[i]
        }

        // Clamp to max - 8.0
        val minVal = maxVal - 8.0f
        for (i in melSpec.indices) {
            melSpec[i] = max(melSpec[i], minVal)
        }

        // Normalize: (x + 4.0) / 4.0
        for (i in melSpec.indices) {
            melSpec[i] = (melSpec[i] + 4.0f) / 4.0f
        }
    }

    // Mel scale conversions (HTK formula used by Whisper)
    internal fun hzToMel(hz: Double): Double = 2595.0 * log10(1.0 + hz / 700.0)
    internal fun melToHz(mel: Double): Double = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)
}
