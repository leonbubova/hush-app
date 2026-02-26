package com.hush.app

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertTextEquals
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/**
 * Reusable test helpers for instrumented Compose tests.
 */
object ComposeTestHelpers {

    /**
     * Dismiss "System UI isn't responding" or similar ANR dialogs.
     */
    fun dismissAnrIfPresent() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val wait = device.wait(Until.findObject(By.text("Wait")), 1_000)
        wait?.click()
        device.waitForIdle(500)
    }

    /**
     * Open the navigation drawer by clicking the hamburger menu button.
     */
    fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.openDrawer() {
        dismissAnrIfPresent()
        onNodeWithTag(TestTags.DRAWER_MENU_BUTTON).performClick()
        waitForIdle()
    }

    /**
     * Navigate to a screen via the drawer.
     */
    fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.navigateTo(
        screen: MainViewModel.AppScreen,
    ) {
        openDrawer()
        val tag = when (screen) {
            MainViewModel.AppScreen.HOME -> TestTags.DRAWER_HOME
            MainViewModel.AppScreen.USAGE -> TestTags.DRAWER_USAGE
            MainViewModel.AppScreen.SETTINGS -> TestTags.DRAWER_SETTINGS
        }
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    /**
     * Assert the status text matches the expected value, with ANR dismissal.
     */
    fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.assertStatusText(
        expected: String,
    ) {
        dismissAnrIfPresent()
        onNodeWithTag(TestTags.STATUS_TEXT).assertTextEquals(expected)
    }
}
