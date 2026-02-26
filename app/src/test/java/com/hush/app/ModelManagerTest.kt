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
import java.security.MessageDigest

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
    fun `cleanOrphanedModels deletes orphaned pte files`() {
        val modelsDir = manager.getModelsDir()
        modelsDir.mkdirs()
        val orphaned = File(modelsDir, "old_model_v1.pte")
        orphaned.writeText("old data")
        assertTrue(orphaned.exists())

        manager.cleanOrphanedModels()

        assertFalse(orphaned.exists())
    }

    @Test
    fun `cleanOrphanedModels keeps valid model files`() {
        val modelsDir = manager.getModelsDir()
        modelsDir.mkdirs()
        val validFile = File(modelsDir, q4FileName)
        validFile.writeText("model data")

        manager.cleanOrphanedModels()

        assertTrue(validFile.exists())
        validFile.delete()
    }

    @Test
    fun `cleanOrphanedModels deletes orphaned tmp files`() {
        val modelsDir = manager.getModelsDir()
        modelsDir.mkdirs()
        val orphanedTmp = File(modelsDir, "old_model_v1.pte.tmp")
        orphanedTmp.writeText("partial download")

        manager.cleanOrphanedModels()

        assertFalse(orphanedTmp.exists())
    }

    @Test
    fun `cleanOrphanedModels keeps valid tmp files for in-progress downloads`() {
        val modelsDir = manager.getModelsDir()
        modelsDir.mkdirs()
        val validTmp = File(modelsDir, "${q4FileName}.tmp")
        validTmp.writeText("downloading")

        manager.cleanOrphanedModels()

        assertTrue(validTmp.exists())
        validTmp.delete()
    }

    @Test
    fun `cleanOrphanedModels leaves non-pte non-tmp files alone`() {
        val modelsDir = manager.getModelsDir()
        modelsDir.mkdirs()
        val unknownFile = File(modelsDir, "readme.txt")
        unknownFile.writeText("notes")

        manager.cleanOrphanedModels()

        assertTrue(unknownFile.exists())
        unknownFile.delete()
    }

    @Test
    fun `cleanOrphanedModels handles missing models directory`() {
        val modelsDir = manager.getModelsDir()
        if (modelsDir.exists()) modelsDir.deleteRecursively()

        // Should not throw
        manager.cleanOrphanedModels()
    }

    @Test
    fun `all AVAILABLE_MODELS have non-empty sha256`() {
        for (model in ModelManager.AVAILABLE_MODELS) {
            assertTrue(
                "Model ${model.id} has empty sha256",
                model.sha256.isNotBlank()
            )
            assertEquals(
                "Model ${model.id} sha256 should be 64 hex chars",
                64, model.sha256.length
            )
            assertTrue(
                "Model ${model.id} sha256 should be lowercase hex",
                model.sha256.matches(Regex("^[0-9a-f]{64}$"))
            )
        }
    }

    @Test
    fun `SHA256 hex formatting matches known value`() {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("test".toByteArray())
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        // SHA-256("test") = 9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08
        assertEquals(
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
            hex
        )
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
