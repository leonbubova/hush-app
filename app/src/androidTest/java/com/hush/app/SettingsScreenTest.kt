package com.hush.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hush.app.transcription.ProviderConfig
import com.hush.app.ui.theme.HushTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests SettingsScreen composable with different state configurations.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun defaultConfigs(): Map<String, ProviderConfig> = mapOf(
        ProviderConfig.PROVIDER_VOXTRAL to ProviderConfig.Voxtral(),
        ProviderConfig.PROVIDER_OPENAI to ProviderConfig.OpenAiWhisper(),
        ProviderConfig.PROVIDER_GROQ to ProviderConfig.Groq(),
        ProviderConfig.PROVIDER_LOCAL to ProviderConfig.Local(),
    )

    private fun setSettings(state: MainViewModel.UiState) {
        composeRule.setContent {
            HushTheme {
                SettingsScreen(
                    state = state,
                    onSetActiveProvider = {},
                    onSaveProviderConfig = { _, _ -> },
                    onOpenDrawer = {},
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun showsAllFourProviderOptions() {
        setSettings(MainViewModel.UiState(providerConfigs = defaultConfigs()))

        composeRule.onNodeWithTag(TestTags.providerOption(ProviderConfig.PROVIDER_VOXTRAL)).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.providerOption(ProviderConfig.PROVIDER_OPENAI)).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.providerOption(ProviderConfig.PROVIDER_GROQ)).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.providerOption(ProviderConfig.PROVIDER_LOCAL)).assertIsDisplayed()
    }

    @Test
    fun defaultSelectionIsVoxtral() {
        setSettings(
            MainViewModel.UiState(
                activeProviderId = ProviderConfig.PROVIDER_VOXTRAL,
                providerConfigs = defaultConfigs(),
            )
        )

        // Voxtral config panel should be visible (it shows the API key field)
        composeRule.onNodeWithTag(TestTags.API_KEY_FIELD).assertIsDisplayed()
    }

    @Test
    fun settingsScreenRootTagExists() {
        setSettings(MainViewModel.UiState(providerConfigs = defaultConfigs()))
        composeRule.onNodeWithTag(TestTags.SETTINGS_SCREEN).assertIsDisplayed()
    }

    @Test
    fun localProviderShowsModelDownloadButton() {
        setSettings(
            MainViewModel.UiState(
                activeProviderId = ProviderConfig.PROVIDER_LOCAL,
                providerConfigs = defaultConfigs(),
            )
        )

        // Local config panel shows download button when model is not downloaded
        composeRule.onNodeWithTag(TestTags.MODEL_DOWNLOAD_BUTTON).assertIsDisplayed()
    }

    @Test
    fun cloudProviderShowsApiKeyField() {
        setSettings(
            MainViewModel.UiState(
                activeProviderId = ProviderConfig.PROVIDER_OPENAI,
                providerConfigs = defaultConfigs(),
            )
        )

        composeRule.onNodeWithTag(TestTags.API_KEY_FIELD).assertIsDisplayed()
    }
}
