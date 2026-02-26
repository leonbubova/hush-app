package com.hush.app

import com.hush.app.transcription.ModelManager
import com.hush.app.transcription.ModelStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ModelManagerTest {

    private lateinit var manager: ModelManager
    private val q4FileName = ModelManager.getModelInfo("whisper-tiny-en-q4")!!.fileName

    @Before
    fun setUp() {
        manager = ModelManager(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `getModelStatus returns NOT_DOWNLOADED for missing model`() {
        val status = manager.getModelStatus("whisper-tiny-en-q4")
        assertEquals(ModelStatus.NOT_DOWNLOADED, status)
    }

    @Test
    fun `getModelStatus returns NOT_DOWNLOADED for unknown model`() {
        val status = manager.getModelStatus("nonexistent-model")
        assertEquals(ModelStatus.NOT_DOWNLOADED, status)
    }

    @Test
    fun `getModelPath returns null for missing model`() {
        assertNull(manager.getModelPath("whisper-tiny-en-q4"))
    }

    @Test
    fun `getModelPath returns path when model file exists`() {
        // Create the model file manually
        val modelsDir = manager.getModelsDir()
        modelsDir.mkdirs()
        val modelFile = File(modelsDir, q4FileName)
        modelFile.writeText("fake model data")

        val path = manager.getModelPath("whisper-tiny-en-q4")
        assertNotNull(path)
        assertTrue(path!!.endsWith(q4FileName))

        modelFile.delete()
    }

    @Test
    fun `deleteModel removes model file`() {
        val modelsDir = manager.getModelsDir()
        modelsDir.mkdirs()
        val modelFile = File(modelsDir, q4FileName)
        modelFile.writeText("fake model data")
        assertTrue(modelFile.exists())

        manager.deleteModel("whisper-tiny-en-q4")

        assertFalse(modelFile.exists())
        assertEquals(ModelStatus.NOT_DOWNLOADED, manager.getModelStatus("whisper-tiny-en-q4"))
    }

    @Test
    fun `deleteModel is no-op for unknown model`() {
        // Should not throw
        manager.deleteModel("nonexistent-model")
    }

    @Test
    fun `AVAILABLE_MODELS is non-empty`() {
        assertTrue(ModelManager.AVAILABLE_MODELS.isNotEmpty())
    }

    @Test
    fun `AVAILABLE_MODELS contains whisper-tiny-en`() {
        val model = ModelManager.getModelInfo("whisper-tiny-en-q4")
        assertNotNull(model)
        assertEquals("whisper-tiny-en-q4", model!!.id)
        assertTrue(model.sizeBytes > 0)
        assertTrue(model.fileName.endsWith(".pte"))
    }

    @Test
    fun `getModelInfo returns null for unknown model`() {
        assertNull(ModelManager.getModelInfo("nonexistent"))
    }

    @Test
    fun `refreshStatuses updates status to READY when file exists`() {
        val modelsDir = manager.getModelsDir()
        modelsDir.mkdirs()
        val modelFile = File(modelsDir, q4FileName)
        modelFile.writeText("fake model data")

        manager.refreshStatuses()

        val statuses = manager.statuses.value
        assertEquals(ModelStatus.READY, statuses["whisper-tiny-en-q4"])

        modelFile.delete()
    }
}
