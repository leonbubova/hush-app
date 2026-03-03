package com.hush.app.transcription

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Centralized OkHttpClient factory with certificate pinning.
 *
 * Pins intermediate CAs (not leaf certs — those rotate every 90 days).
 * Each host has a primary pin + backup pin (root or alternate intermediate).
 *
 * Pin rotation schedule:
 * - Google Trust Services WE1 (OpenAI, Groq, Mistral): rotates ~1-3 years
 * - Let's Encrypt R13 (Moonshine CDN): rotates ~1-3 years
 * - Sectigo DV E36 (GitHub model downloads): rotates ~1-3 years
 *
 * When pins expire: update the hash, release an app update. The backup pin
 * (root CA) provides a grace period if the intermediate rotates unexpectedly.
 */
object HttpClientFactory {

    private val certificatePinner = CertificatePinner.Builder()
        // OpenAI — Google Trust Services WE1 intermediate + GTS Root R4 backup
        .add("api.openai.com", "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=")
        .add("api.openai.com", "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=")
        // Groq — Google Trust Services WE1 intermediate + GTS Root R4 backup
        .add("api.groq.com", "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=")
        .add("api.groq.com", "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=")
        // Mistral (Voxtral) — Google Trust Services WE1 intermediate + GTS Root R4 backup
        .add("api.mistral.ai", "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=")
        .add("api.mistral.ai", "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=")
        // Anthropic (post-processing) — Google Trust Services WE1 intermediate + GTS Root R4 backup
        .add("api.anthropic.com", "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=")
        .add("api.anthropic.com", "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=")
        // Moonshine CDN — Let's Encrypt R13 + ISRG Root X1 backup
        .add("download.moonshine.ai", "sha256/AlSQhgtJirc8ahLyekmtX+Iw+v46yPYRLJt9Cq1GlB0=")
        .add("download.moonshine.ai", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=")
        // GitHub (model downloads) — Sectigo DV E36 + Sectigo Root E46 backup
        .add("github.com", "sha256/ZSagvDzjltLkewXEBuDxIzpW/dpVw1Juvvmd0hhkzdY=")
        .add("github.com", "sha256/sLVjNUaFYfW7n6EtgBeEpjOlcnBdNPMrZDRF36iwBdE=")
        // GitHub releases CDN — Let's Encrypt R12 + ISRG Root X1 backup
        .add("objects.githubusercontent.com", "sha256/kZwN96eHtZftBWrOZUsd6cA4es80n3NzSk/XtYz2EqQ=")
        .add("objects.githubusercontent.com", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=")
        .build()

    /** Standard client for batch API calls (transcription providers). */
    fun createApiClient(): OkHttpClient = OkHttpClient.Builder()
        .certificatePinner(certificatePinner)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Client for WebSocket streaming (no read timeout). */
    fun createStreamingClient(): OkHttpClient = OkHttpClient.Builder()
        .certificatePinner(certificatePinner)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    /** Client for LLM post-processing (shorter timeouts). */
    fun createPostProcessorClient(): OkHttpClient = OkHttpClient.Builder()
        .certificatePinner(certificatePinner)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Client for model downloads (long read timeout). */
    fun createDownloadClient(): OkHttpClient = OkHttpClient.Builder()
        .certificatePinner(certificatePinner)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()
}
