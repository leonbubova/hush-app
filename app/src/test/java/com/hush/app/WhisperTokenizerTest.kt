package com.hush.app

import com.hush.app.transcription.WhisperTokenizer
import org.junit.Assert.*
import org.junit.Test

class WhisperTokenizerTest {

    private fun tokenizer() = WhisperTokenizer.fromInputStream {
        javaClass.classLoader!!.getResourceAsStream("whisper_vocab.json")!!
    }

    @Test
    fun `decode simple word tokens`() {
        val t = tokenizer()
        // Token 262 = "Ġthe" → "the" after cleanup
        val result = t.decode(listOf(262L))
        assertEquals("the", result)
    }

    @Test
    fun `decode filters EOS token`() {
        val t = tokenizer()
        val result = t.decode(listOf(262L, 50256L, 262L), eosId = 50256L)
        // Should stop at EOS, not include second "the"
        assertEquals("the", result)
    }

    @Test
    fun `decode filters special tokens above 50257`() {
        val t = tokenizer()
        // Special tokens like SOT (50257), language (50259), etc. should be filtered
        val result = t.decode(listOf(50258L, 50259L, 262L, 50256L))
        assertEquals("the", result)
    }

    @Test
    fun `decode empty list returns empty string`() {
        val t = tokenizer()
        val result = t.decode(emptyList())
        assertEquals("", result)
    }

    @Test
    fun `decode list with only special tokens returns empty string`() {
        val t = tokenizer()
        val result = t.decode(listOf(50257L, 50258L, 50259L))
        assertEquals("", result)
    }

    @Test
    fun `decode replaces BPE space marker with space`() {
        val t = tokenizer()
        // "Ġthe" + "Ġcat" → " the cat" → trimmed "the cat"
        val result = t.decode(listOf(262L, 3797L)) // "Ġthe" + "Ġcat"
        assertEquals("the cat", result)
    }

    @Test
    fun `decode known phrase`() {
        val t = tokenizer()
        // Token 220 = "Ġ" (just a space) → trimmed to ""
        val result = t.decode(listOf(220L))
        assertEquals("", result)
    }

    @Test
    fun `constants are correct`() {
        assertEquals(50257, WhisperTokenizer.SPECIAL_TOKEN_START)
        assertEquals(50256L, WhisperTokenizer.END_OF_TEXT)
    }

    @Test
    fun `decode stops at custom EOS`() {
        val t = tokenizer()
        val result = t.decode(listOf(262L, 50257L, 262L), eosId = 50257L)
        assertEquals("the", result)
    }

    @Test
    fun `vocab loads successfully`() {
        val t = tokenizer()
        // Token 0 = "!"
        val result = t.decode(listOf(0L))
        assertEquals("!", result)
    }

    @Test
    fun `punctuation tokens decode correctly`() {
        val t = tokenizer()
        assertEquals("!", t.decode(listOf(0L)))
        assertEquals("#", t.decode(listOf(2L)))
    }

    @Test
    fun `multiple word tokens produce coherent text`() {
        val t = tokenizer()
        // "Ġhello" = 23748, "Ġworld" = 995
        val result = t.decode(listOf(23748L, 995L))
        assertEquals("hello world", result)
    }
}
