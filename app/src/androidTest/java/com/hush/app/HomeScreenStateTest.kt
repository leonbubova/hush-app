package com.hush.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hush.app.ui.theme.HushTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests HushScreen() directly with different UiState values.
 * No Activity or Service needed — fast composable-level tests.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(state: MainViewModel.UiState) {
        composeRule.setContent {
            HushTheme {
                HushScreen(
                    state = state,
                    onToggle = {},
                    onClearHistory = {},
                )
            }
        }
    }

    @Test
    fun idleStateShowsReady() {
        setScreen(MainViewModel.UiState(dictationState = DictationService.DictationState.IDLE))
        composeRule.onNodeWithTag(TestTags.STATUS_TEXT).assertTextEquals("Ready")
        composeRule.onNodeWithTag(TestTags.SUBTITLE_TEXT).assertTextContains("Double-tap", substring = true)
    }

    @Test
    fun recordingStateShowsListening() {
        setScreen(MainViewModel.UiState(dictationState = DictationService.DictationState.RECORDING))
        composeRule.onNodeWithTag(TestTags.STATUS_TEXT).assertTextEquals("Listening...")
        composeRule.onNodeWithTag(TestTags.SUBTITLE_TEXT).assertTextContains("Speak now", substring = true)
    }

    @Test
    fun processingStateShowsTranscribing() {
        setScreen(MainViewModel.UiState(dictationState = DictationService.DictationState.PROCESSING))
        composeRule.onNodeWithTag(TestTags.STATUS_TEXT).assertTextEquals("Transcribing...")
    }

    @Test
    fun doneStateShowsCopied() {
        setScreen(MainViewModel.UiState(dictationState = DictationService.DictationState.DONE))
        composeRule.onNodeWithTag(TestTags.STATUS_TEXT).assertTextEquals("Copied!")
        composeRule.onNodeWithTag(TestTags.SUBTITLE_TEXT).assertTextContains("clipboard", substring = true)
    }

    @Test
    fun errorStateShowsErrorMessage() {
        setScreen(
            MainViewModel.UiState(
                dictationState = DictationService.DictationState.ERROR,
                errorMessage = "No API key configured",
            )
        )
        composeRule.onNodeWithTag(TestTags.STATUS_TEXT).assertTextEquals("Error")
        composeRule.onNodeWithTag(TestTags.SUBTITLE_TEXT).assertTextContains("No API key", substring = true)
    }

    @Test
    fun emptyHistoryShowsPlaceholder() {
        setScreen(MainViewModel.UiState(history = emptyList()))
        composeRule.onNodeWithTag(TestTags.EMPTY_HISTORY_TEXT).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.EMPTY_HISTORY_TEXT).assertTextContains("transcriptions", substring = true)
    }

    @Test
    fun historyItemsDisplayed() {
        setScreen(MainViewModel.UiState(history = listOf("First transcription", "Second transcription")))
        composeRule.onNodeWithTag(TestTags.historyItem(0)).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.historyItem(1)).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.HISTORY_CLEAR_BUTTON).assertIsDisplayed()
    }

    @Test
    fun accessibilityBannerShownWhenDisabled() {
        setScreen(MainViewModel.UiState(accessibilityEnabled = false))
        composeRule.onNodeWithTag(TestTags.ACCESSIBILITY_BANNER).assertIsDisplayed()
    }

    @Test
    fun accessibilityBannerHiddenWhenEnabled() {
        setScreen(MainViewModel.UiState(accessibilityEnabled = true))
        composeRule.onNodeWithTag(TestTags.ACCESSIBILITY_BANNER).assertDoesNotExist()
    }

    @Test
    fun micButtonIsDisplayed() {
        setScreen(MainViewModel.UiState())
        composeRule.onNodeWithTag(TestTags.MIC_BUTTON).assertIsDisplayed()
    }
}
