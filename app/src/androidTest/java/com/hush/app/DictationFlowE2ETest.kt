package com.hush.app

import android.os.Environment
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DictationFlowE2ETest {

    @get:Rule(order = 0)
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.POST_NOTIFICATIONS,
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val device: UiDevice by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun freshInstall_noApiKey_recordAndStop_showsError() {
        // Permissions are pre-granted by GrantPermissionRule.
        // On first launch with no API key, the API key dialog appears.
        dismissAnrIfPresent()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        Thread.sleep(500)

        // Assert "Ready" state and take screenshot
        dismissAnrIfPresent()
        composeRule.onNodeWithTag("status_text").assertTextEquals("Ready")
        takeScreenshot("ready")

        // Tap mic button, assert "Listening..."
        composeRule.onNodeWithTag("mic_button").performClick()
        composeRule.waitForIdle()
        Thread.sleep(500)
        dismissAnrIfPresent()
        composeRule.onNodeWithTag("status_text").assertTextEquals("Listening...")
        takeScreenshot("listening")

        // Tap mic button again, assert "Error" with "No API key configured"
        composeRule.onNodeWithTag("mic_button").performClick()
        composeRule.waitForIdle()
        Thread.sleep(2000)
        dismissAnrIfPresent()
        composeRule.onNodeWithTag("status_text").assertTextEquals("Error")
        composeRule.onNodeWithTag("subtitle_text").assertTextContains("API key", substring = true)
        takeScreenshot("error")
    }

    private fun dismissAnrIfPresent() {
        // Dismiss "System UI isn't responding" or similar ANR dialogs
        val wait = device.wait(Until.findObject(By.text("Wait")), 1_000)
        wait?.click()
        device.waitForIdle(500)
    }

    private fun takeScreenshot(name: String) {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "hush-tests"
        )
        dir.mkdirs()

        val baselineFile = File(dir, "${name}_baseline.png")
        val targetFile = if (baselineFile.exists()) {
            File(dir, "${name}_latest.png")
        } else {
            baselineFile
        }

        device.takeScreenshot(targetFile)
    }
}
