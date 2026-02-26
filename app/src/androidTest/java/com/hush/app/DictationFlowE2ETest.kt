package com.hush.app

import android.os.Environment
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
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
        // With no API key configured, app launches straight to "Ready".
        ComposeTestHelpers.dismissAnrIfPresent()
        composeRule.waitForIdle()
        Thread.sleep(500)

        // Assert "Ready" state and take screenshot
        ComposeTestHelpers.dismissAnrIfPresent()
        composeRule.onNodeWithTag(TestTags.STATUS_TEXT).assertTextEquals("Ready")
        takeScreenshot("ready")

        // Tap mic button, assert "Listening..."
        composeRule.onNodeWithTag(TestTags.MIC_BUTTON).performClick()
        composeRule.waitForIdle()
        Thread.sleep(500)
        ComposeTestHelpers.dismissAnrIfPresent()
        composeRule.onNodeWithTag(TestTags.STATUS_TEXT).assertTextEquals("Listening...")
        takeScreenshot("listening")

        // Tap mic button again, assert "Error" with "No API key configured"
        composeRule.onNodeWithTag(TestTags.MIC_BUTTON).performClick()
        composeRule.waitForIdle()
        Thread.sleep(2000)
        ComposeTestHelpers.dismissAnrIfPresent()
        composeRule.onNodeWithTag(TestTags.STATUS_TEXT).assertTextEquals("Error")
        composeRule.onNodeWithTag(TestTags.SUBTITLE_TEXT).assertTextContains("API key", substring = true)
        takeScreenshot("error")
    }

    @Test
    fun emptyHistoryShowsPlaceholder() {
        ComposeTestHelpers.dismissAnrIfPresent()
        composeRule.waitForIdle()
        Thread.sleep(500)

        // Fresh install — no history
        composeRule.onNodeWithTag(TestTags.EMPTY_HISTORY_TEXT).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.EMPTY_HISTORY_TEXT).assertTextContains("transcriptions", substring = true)
    }

    @Test
    fun micButtonIsDisplayedAndClickable() {
        ComposeTestHelpers.dismissAnrIfPresent()
        composeRule.waitForIdle()
        Thread.sleep(500)

        composeRule.onNodeWithTag(TestTags.MIC_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.MIC_BUTTON).assertHasClickAction()
    }

    @Test
    fun drawerMenuButtonIsDisplayed() {
        ComposeTestHelpers.dismissAnrIfPresent()
        composeRule.waitForIdle()
        Thread.sleep(500)

        composeRule.onNodeWithTag(TestTags.DRAWER_MENU_BUTTON).assertIsDisplayed()
    }

    @Test
    fun accessibilityBannerIsDisplayed() {
        // On a fresh install, accessibility is not enabled
        ComposeTestHelpers.dismissAnrIfPresent()
        composeRule.waitForIdle()
        Thread.sleep(500)

        composeRule.onNodeWithTag(TestTags.ACCESSIBILITY_BANNER).assertIsDisplayed()
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
