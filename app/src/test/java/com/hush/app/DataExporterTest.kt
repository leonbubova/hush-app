package com.hush.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataExporterTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        HistoryRepository.clear(context)
        UsageRepository.clearSessions(context)
    }

    @Test
    fun `export produces valid JSON with version, history, and usage`() {
        HistoryRepository.addEntry(context, "hello world")
        HistoryRepository.addEntry(context, "second entry")
        UsageRepository.recordSession(context, 1000L, 30, 10)

        val json = DataExporter.exportJson(context)
        val root = JSONObject(json)

        assertEquals(1, root.getInt("version"))
        assertTrue(root.has("exportedAt"))
        assertEquals(2, root.getJSONArray("history").length())
        // addEntry prepends, so "second entry" is first
        assertEquals("second entry", root.getJSONArray("history").getString(0))
        assertEquals("hello world", root.getJSONArray("history").getString(1))

        val usage = root.getJSONArray("usage")
        assertEquals(1, usage.length())
        assertEquals(1000L, usage.getJSONObject(0).getLong("s"))
        assertEquals(30, usage.getJSONObject(0).getInt("d"))
        assertEquals(10, usage.getJSONObject(0).getInt("w"))
    }

    @Test
    fun `import with valid JSON restores data`() {
        val json = """
        {
            "version": 1,
            "exportedAt": "2026-03-01T14:30:00Z",
            "history": ["first", "second", "third"],
            "usage": [
                {"s": 1000, "d": 30, "w": 10},
                {"s": 2000, "d": 60, "w": 25}
            ]
        }
        """.trimIndent()

        val result = DataExporter.importJson(context, json)

        assertEquals(3, result.historyCount)
        assertEquals(2, result.usageCount)

        val history = HistoryRepository.loadAll(context)
        assertEquals(listOf("first", "second", "third"), history)

        val sessions = UsageRepository.loadSessions(context)
        assertEquals(2, sessions.size)
        assertEquals(1000L, sessions[0].startEpochMs)
        assertEquals(2000L, sessions[1].startEpochMs)
    }

    @Test
    fun `import with empty history and usage works`() {
        val json = """
        {
            "version": 1,
            "exportedAt": "2026-03-01T14:30:00Z",
            "history": [],
            "usage": []
        }
        """.trimIndent()

        val result = DataExporter.importJson(context, json)

        assertEquals(0, result.historyCount)
        assertEquals(0, result.usageCount)
        assertTrue(HistoryRepository.loadAll(context).isEmpty())
        assertTrue(UsageRepository.loadSessions(context).isEmpty())
    }

    @Test
    fun `import with missing history and usage fields works`() {
        val json = """{"version": 1, "exportedAt": "2026-03-01T14:30:00Z"}"""

        val result = DataExporter.importJson(context, json)

        assertEquals(0, result.historyCount)
        assertEquals(0, result.usageCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `import with missing version field fails`() {
        val json = """{"exportedAt": "2026-03-01T14:30:00Z", "history": []}"""
        DataExporter.importJson(context, json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `import with version 0 fails`() {
        val json = """{"version": 0, "history": []}"""
        DataExporter.importJson(context, json)
    }

    @Test(expected = org.json.JSONException::class)
    fun `import with invalid JSON throws`() {
        DataExporter.importJson(context, "not json at all")
    }

    @Test
    fun `round-trip export then import preserves data`() {
        // Set up initial data
        HistoryRepository.addEntry(context, "entry one")
        HistoryRepository.addEntry(context, "entry two")
        UsageRepository.recordSession(context, 5000L, 120, 50)
        UsageRepository.recordSession(context, 6000L, 45, 20)

        // Export
        val json = DataExporter.exportJson(context)

        // Clear everything
        HistoryRepository.clear(context)
        UsageRepository.clearSessions(context)
        assertTrue(HistoryRepository.loadAll(context).isEmpty())
        assertTrue(UsageRepository.loadSessions(context).isEmpty())

        // Import
        val result = DataExporter.importJson(context, json)

        assertEquals(2, result.historyCount)
        assertEquals(2, result.usageCount)

        val history = HistoryRepository.loadAll(context)
        assertEquals("entry two", history[0])
        assertEquals("entry one", history[1])

        val sessions = UsageRepository.loadSessions(context)
        assertEquals(5000L, sessions[0].startEpochMs)
        assertEquals(6000L, sessions[1].startEpochMs)
        assertEquals(120, sessions[0].durationSeconds)
        assertEquals(50, sessions[0].wordCount)
    }

    @Test
    fun `import replaces existing data`() {
        // Pre-existing data
        HistoryRepository.addEntry(context, "old entry")
        UsageRepository.recordSession(context, 999L, 10, 5)

        val json = """
        {
            "version": 1,
            "exportedAt": "2026-03-01T14:30:00Z",
            "history": ["new entry"],
            "usage": [{"s": 8000, "d": 90, "w": 40}]
        }
        """.trimIndent()

        DataExporter.importJson(context, json)

        val history = HistoryRepository.loadAll(context)
        assertEquals(1, history.size)
        assertEquals("new entry", history[0])

        val sessions = UsageRepository.loadSessions(context)
        assertEquals(1, sessions.size)
        assertEquals(8000L, sessions[0].startEpochMs)
    }

    @Test
    fun `import with missing wordCount defaults to 0`() {
        val json = """
        {
            "version": 1,
            "exportedAt": "2026-03-01T14:30:00Z",
            "history": [],
            "usage": [{"s": 1000, "d": 30}]
        }
        """.trimIndent()

        DataExporter.importJson(context, json)

        val sessions = UsageRepository.loadSessions(context)
        assertEquals(1, sessions.size)
        assertEquals(0, sessions[0].wordCount)
    }
}
