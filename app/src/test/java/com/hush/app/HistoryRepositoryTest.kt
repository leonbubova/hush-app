package com.hush.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        HistoryRepository.resetCachedPrefs()
    }

    @After
    fun tearDown() {
        HistoryRepository.clear(context)
        HistoryRepository.resetCachedPrefs()
    }

    @Test
    fun `loadAll returns empty list when no history`() {
        val entries = HistoryRepository.loadAll(context)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `addEntry prepends new entry`() {
        HistoryRepository.addEntry(context, "first")
        HistoryRepository.addEntry(context, "second")

        val entries = HistoryRepository.loadAll(context)
        assertEquals(2, entries.size)
        assertEquals("second", entries[0])
        assertEquals("first", entries[1])
    }

    @Test
    fun `addEntry with blank text is no-op`() {
        HistoryRepository.addEntry(context, "real entry")
        HistoryRepository.addEntry(context, "")
        HistoryRepository.addEntry(context, "   ")

        val entries = HistoryRepository.loadAll(context)
        assertEquals(1, entries.size)
        assertEquals("real entry", entries[0])
    }

    @Test
    fun `loadAll returns entries in order newest first`() {
        HistoryRepository.addEntry(context, "alpha")
        HistoryRepository.addEntry(context, "beta")
        HistoryRepository.addEntry(context, "gamma")

        val entries = HistoryRepository.loadAll(context)
        assertEquals(listOf("gamma", "beta", "alpha"), entries)
    }

    @Test
    fun `clear removes all entries`() {
        HistoryRepository.addEntry(context, "one")
        HistoryRepository.addEntry(context, "two")

        HistoryRepository.clear(context)

        val entries = HistoryRepository.loadAll(context)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `multiple addEntry calls accumulate correctly`() {
        for (i in 1..10) {
            HistoryRepository.addEntry(context, "entry $i")
        }

        val entries = HistoryRepository.loadAll(context)
        assertEquals(10, entries.size)
        assertEquals("entry 10", entries[0])
        assertEquals("entry 1", entries[9])
    }
}
