package com.hush.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.hush.app.ComposeTestHelpers.navigateTo
import com.hush.app.transcription.ProviderConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsFlowE2ETest {

    @get:Rule(order = 0)
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.POST_NOTIFICATIONS,
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun navigateToSettingsShowsProviders() {
        ComposeTestHelpers.dismissAnrIfPresent()
        composeRule.waitForIdle()
        composeRule.navigateTo(MainViewModel.AppScreen.SETTINGS)

        composeRule.onNodeWithTag(TestTags.providerOption(ProviderConfig.PROVIDER_VOXTRAL)).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.providerOption(ProviderConfig.PROVIDER_OPENAI)).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.providerOption(ProviderConfig.PROVIDER_GROQ)).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.providerOption(ProviderConfig.PROVIDER_LOCAL)).assertIsDisplayed()
    }

    @Test
    fun switchProviderToOpenAi() {
        ComposeTestHelpers.dismissAnrIfPresent()
        composeRule.waitForIdle()
        composeRule.navigateTo(MainViewModel.AppScreen.SETTINGS)

        composeRule.onNodeWithTag(TestTags.providerOption(ProviderConfig.PROVIDER_OPENAI)).performClick()
        composeRule.waitForIdle()

        // OpenAI config should show API key field
        composeRule.onNodeWithTag(TestTags.API_KEY_FIELD).assertIsDisplayed()
    }

    @Test
    fun switchProviderToLocalShowsDownloadButton() {
        ComposeTestHelpers.dismissAnrIfPresent()
        composeRule.waitForIdle()
        composeRule.navigateTo(MainViewModel.AppScreen.SETTINGS)

        composeRule.onNodeWithTag(TestTags.providerOption(ProviderConfig.PROVIDER_LOCAL)).performClick()
        composeRule.waitForIdle()

        // Local config should show model download button (model not downloaded on fresh install)
        composeRule.onNodeWithTag(TestTags.MODEL_DOWNLOAD_BUTTON).assertIsDisplayed()
    }

    @Test
    fun navigateAwayAndBackPersistsSelection() {
        ComposeTestHelpers.dismissAnrIfPresent()
        composeRule.waitForIdle()
        composeRule.navigateTo(MainViewModel.AppScreen.SETTINGS)

        // Switch to Groq
        composeRule.onNodeWithTag(TestTags.providerOption(ProviderConfig.PROVIDER_GROQ)).performClick()
        composeRule.waitForIdle()

        // Go home
        composeRule.navigateTo(MainViewModel.AppScreen.HOME)
        composeRule.onNodeWithTag(TestTags.STATUS_TEXT).assertIsDisplayed()

        // Go back to settings
        composeRule.navigateTo(MainViewModel.AppScreen.SETTINGS)

        // Groq should still be the active provider — its API key field should be visible
        composeRule.onNodeWithTag(TestTags.API_KEY_FIELD).assertIsDisplayed()
    }
}
