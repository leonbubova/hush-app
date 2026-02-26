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
class UsageRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        UsageRepository.clearSessions(context)
    }

    @Test
    fun `loadSessions returns empty list when no data`() {
        val sessions = UsageRepository.loadSessions(context)
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `recordSession then loadSessions returns the session`() {
        UsageRepository.recordSession(context, 1000L, 10, 5)

        val sessions = UsageRepository.loadSessions(context)
        assertEquals(1, sessions.size)
        assertEquals(1000L, sessions[0].startEpochMs)
        assertEquals(10, sessions[0].durationSeconds)
        assertEquals(5, sessions[0].wordCount)
    }

    @Test
    fun `multiple sessions are stored in order`() {
        UsageRepository.recordSession(context, 1000L, 10, 5)
        UsageRepository.recordSession(context, 2000L, 20, 10)
        UsageRepository.recordSession(context, 3000L, 30, 15)

        val sessions = UsageRepository.loadSessions(context)
        assertEquals(3, sessions.size)
        assertEquals(1000L, sessions[0].startEpochMs)
        assertEquals(2000L, sessions[1].startEpochMs)
        assertEquals(3000L, sessions[2].startEpochMs)
    }

    @Test
    fun `clearSessions removes all data`() {
        UsageRepository.recordSession(context, 1000L, 10, 5)
        UsageRepository.recordSession(context, 2000L, 20, 10)

        UsageRepository.clearSessions(context)

        val sessions = UsageRepository.loadSessions(context)
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `wordCount defaults to 0 when not provided`() {
        UsageRepository.recordSession(context, 1000L, 10)

        val sessions = UsageRepository.loadSessions(context)
        assertEquals(1, sessions.size)
        assertEquals(0, sessions[0].wordCount)
    }

    @Test
    fun `sessions are capped at MAX_SESSIONS`() {
        // Record 505 sessions — should keep only last 500
        for (i in 1..505) {
            UsageRepository.recordSession(context, i.toLong(), 1, 1)
        }

        val sessions = UsageRepository.loadSessions(context)
        assertEquals(500, sessions.size)
        // Oldest should be session 6 (first 5 were dropped)
        assertEquals(6L, sessions[0].startEpochMs)
        assertEquals(505L, sessions[499].startEpochMs)
    }

    @Test
    fun `malformed JSON in prefs returns empty list`() {
        // Write garbage directly to prefs
        val prefs = context.getSharedPreferences("hush_usage", Context.MODE_PRIVATE)
        prefs.edit().putString("sessions", "not valid json").apply()

        val sessions = UsageRepository.loadSessions(context)
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `RecordingSession data class fields are accessible`() {
        val session = RecordingSession(
            startEpochMs = 123456L,
            durationSeconds = 42,
            wordCount = 100,
        )
        assertEquals(123456L, session.startEpochMs)
        assertEquals(42, session.durationSeconds)
        assertEquals(100, session.wordCount)
    }
}
