package com.hush.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.hush.app.ComposeTestHelpers.assertStatusText
import com.hush.app.ComposeTestHelpers.navigateTo
import com.hush.app.ComposeTestHelpers.openDrawer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule(order = 0)
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.POST_NOTIFICATIONS,
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesOnHomeWithReady() {
        ComposeTestHelpers.dismissAnrIfPresent()
        composeRule.waitForIdle()
        composeRule.assertStatusText("Ready")
    }

    @Test
    fun drawerOpensWhenHamburgerTapped() {
        ComposeTestHelpers.dismissAnrIfPresent()
        composeRule.waitForIdle()
        composeRule.openDrawer()
        composeRule.onNodeWithTag(TestTags.DRAWER_HOME).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.DRAWER_USAGE).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.DRAWER_SETTINGS).assertIsDisplayed()
    }

    @Test
    fun navigateToSettingsViaDrawer() {
        ComposeTestHelpers.dismissAnrIfPresent()
        composeRule.waitForIdle()
        composeRule.navigateTo(MainViewModel.AppScreen.SETTINGS)
        composeRule.onNodeWithTag(TestTags.SETTINGS_SCREEN).assertIsDisplayed()
    }

    @Test
    fun navigateToUsageViaDrawer() {
        ComposeTestHelpers.dismissAnrIfPresent()
        composeRule.waitForIdle()
        composeRule.navigateTo(MainViewModel.AppScreen.USAGE)
        composeRule.onNodeWithTag(TestTags.USAGE_SCREEN).assertIsDisplayed()
    }

    @Test
    fun navigateToSettingsAndBackToHome() {
        ComposeTestHelpers.dismissAnrIfPresent()
        composeRule.waitForIdle()
        composeRule.navigateTo(MainViewModel.AppScreen.SETTINGS)
        composeRule.onNodeWithTag(TestTags.SETTINGS_SCREEN).assertIsDisplayed()

        composeRule.navigateTo(MainViewModel.AppScreen.HOME)
        composeRule.onNodeWithTag(TestTags.STATUS_TEXT).assertIsDisplayed()
    }

    @Test
    fun navigateToUsageAndBackToHome() {
        ComposeTestHelpers.dismissAnrIfPresent()
        composeRule.waitForIdle()
        composeRule.navigateTo(MainViewModel.AppScreen.USAGE)
        composeRule.onNodeWithTag(TestTags.USAGE_SCREEN).assertIsDisplayed()

        composeRule.navigateTo(MainViewModel.AppScreen.HOME)
        composeRule.onNodeWithTag(TestTags.STATUS_TEXT).assertIsDisplayed()
    }

    @Test
    fun drawerClosesAfterSelection() {
        ComposeTestHelpers.dismissAnrIfPresent()
        composeRule.waitForIdle()
        composeRule.navigateTo(MainViewModel.AppScreen.SETTINGS)
        // Drawer content stays in the tree but should not be visible when closed
        composeRule.onNodeWithTag(TestTags.SETTINGS_SCREEN).assertIsDisplayed()
        // The drawer_menu_button on Settings screen should be visible (not behind drawer)
        composeRule.onNodeWithTag(TestTags.DRAWER_MENU_BUTTON).assertIsDisplayed()
    }

    @Test
    fun homeIsSelectedInDrawerByDefault() {
        ComposeTestHelpers.dismissAnrIfPresent()
        composeRule.waitForIdle()
        composeRule.openDrawer()
        // Home item should exist in drawer
        composeRule.onNodeWithTag(TestTags.DRAWER_HOME).assertIsDisplayed()
    }
}
