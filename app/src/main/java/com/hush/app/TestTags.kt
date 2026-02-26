package com.hush.app

/**
 * Central registry of Compose testTag constants.
 * Prevents typos and serves as an inventory of testable UI elements.
 */
object TestTags {
    // Home screen
    const val STATUS_TEXT = "status_text"
    const val SUBTITLE_TEXT = "subtitle_text"
    const val MIC_BUTTON = "mic_button"
    const val HISTORY_CLEAR_BUTTON = "history_clear_button"
    const val EMPTY_HISTORY_TEXT = "empty_history_text"
    const val ACCESSIBILITY_BANNER = "accessibility_banner"
    const val DRAWER_MENU_BUTTON = "drawer_menu_button"

    fun historyItem(index: Int) = "history_item_$index"

    // Drawer
    const val DRAWER_HOME = "drawer_home"
    const val DRAWER_USAGE = "drawer_usage"
    const val DRAWER_SETTINGS = "drawer_settings"

    // Settings screen
    const val SETTINGS_SCREEN = "settings_screen"
    const val API_KEY_FIELD = "api_key_field"
    const val SAVE_BUTTON = "save_button"
    const val MODEL_DOWNLOAD_BUTTON = "model_download_button"
    const val MODEL_DELETE_BUTTON = "model_delete_button"

    fun providerOption(id: String) = "provider_option_$id"

    // Usage screen
    const val USAGE_SCREEN = "usage_screen"
}
