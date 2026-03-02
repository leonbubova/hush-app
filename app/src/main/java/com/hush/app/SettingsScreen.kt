package com.hush.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hush.app.transcription.ErrorMessages
import kotlinx.coroutines.delay
import com.hush.app.transcription.ModelManager
import com.hush.app.transcription.ModelStatus
import com.hush.app.transcription.PostProcessorConfig
import com.hush.app.transcription.ProviderConfig
import com.hush.app.transcription.ProviderFactory
import com.hush.app.ui.theme.HushCardBackground
import com.hush.app.ui.theme.HushCardBorder
import com.hush.app.ui.theme.HushCardShape
import com.hush.app.ui.theme.HushLabelColor

private val PlayfairDisplay = FontFamily(
    Font(R.font.playfair_display_regular, weight = FontWeight.Normal),
    Font(R.font.playfair_display_bold, weight = FontWeight.Bold),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: MainViewModel.UiState,
    onSetActiveProvider: (String) -> Unit,
    onSaveProviderConfig: (String, ProviderConfig) -> Unit,
    onSavePostProcessorConfig: (PostProcessorConfig) -> Unit = {},
    onDownloadModel: (String) -> Unit = {},
    onDeleteModel: (String) -> Unit = {},
    onOpenDrawer: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = Color(0xFF0D0D1A),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.testTag(TestTags.DRAWER_MENU_BUTTON),
                    ) {
                        Text("\u2630", fontSize = 20.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                },
                title = {
                    Text(
                        "Settings",
                        fontFamily = PlayfairDisplay,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .testTag(TestTags.SETTINGS_SCREEN),
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                "Transcription Provider",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = HushLabelColor,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            // Provider selector
            ProviderSelector(
                providers = ProviderFactory.allProviderIds,
                activeId = state.activeProviderId,
                onSelect = onSetActiveProvider,
            )

            Spacer(Modifier.height(24.dp))

            // Per-provider config panel
            val activeConfig = state.providerConfigs[state.activeProviderId]
            if (activeConfig != null) {
                ProviderConfigPanel(
                    providerId = state.activeProviderId,
                    config = activeConfig,
                    onSave = { onSaveProviderConfig(state.activeProviderId, it) },
                    modelStatuses = state.modelStatuses,
                    modelDownloadProgress = state.modelDownloadProgress,
                    onDownloadModel = onDownloadModel,
                    onDeleteModel = onDeleteModel,
                )
            }

            Spacer(Modifier.height(24.dp))

            // Text Enhancement section
            TextEnhancementPanel(
                config = state.postProcessorConfig,
                onSave = onSavePostProcessorConfig,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun providerDescription(providerId: String): String = when (providerId) {
    ProviderConfig.PROVIDER_VOXTRAL -> "Cloud \u00B7 Mistral AI"
    ProviderConfig.PROVIDER_VOXTRAL_REALTIME -> "Cloud \u00B7 Live Streaming"
    ProviderConfig.PROVIDER_OPENAI -> "Cloud \u00B7 OpenAI Whisper"
    ProviderConfig.PROVIDER_GROQ -> "Cloud \u00B7 Fast"
    ProviderConfig.PROVIDER_MOONSHINE -> "On-Device \u00B7 Live Streaming"
    ProviderConfig.PROVIDER_LOCAL -> "On-Device \u00B7 Deprecated"
    else -> ""
}

@Composable
private fun ProviderSelector(
    providers: List<String>,
    activeId: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        providers.forEach { id ->
            val isSelected = id == activeId
            val bgColor = if (isSelected) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.06f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .clickable { onSelect(id) }
                    .testTag(TestTags.providerOption(id))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelect(id) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Color(0xFF6C63FF),
                            unselectedColor = Color.White.copy(alpha = 0.4f),
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            ProviderFactory.displayName(id),
                            fontSize = 16.sp,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Text(
                            providerDescription(id),
                            fontSize = 12.sp,
                            color = HushLabelColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderConfigPanel(
    providerId: String,
    config: ProviderConfig,
    onSave: (ProviderConfig) -> Unit,
    modelStatuses: Map<String, ModelStatus> = emptyMap(),
    modelDownloadProgress: Map<String, Float> = emptyMap(),
    onDownloadModel: (String) -> Unit = {},
    onDeleteModel: (String) -> Unit = {},
) {
    when (config) {
        is ProviderConfig.Voxtral -> VoxtralConfigPanel(config, onSave)
        is ProviderConfig.OpenAiWhisper -> OpenAiConfigPanel(config, onSave)
        is ProviderConfig.Groq -> GroqConfigPanel(config, onSave)
        is ProviderConfig.Local -> LocalConfigPanel(
            config = config,
            onSave = onSave,
            modelStatuses = modelStatuses,
            modelDownloadProgress = modelDownloadProgress,
            onDownloadModel = onDownloadModel,
            onDeleteModel = onDeleteModel,
        )
        is ProviderConfig.VoxtralRealtime -> VoxtralRealtimeConfigPanel(config, onSave)
        is ProviderConfig.Moonshine -> MoonshineConfigPanel(
            config = config,
            onSave = onSave,
            modelStatuses = modelStatuses,
            modelDownloadProgress = modelDownloadProgress,
            onDownloadModel = onDownloadModel,
            onDeleteModel = onDeleteModel,
        )
    }
}

@Composable
private fun VoxtralConfigPanel(
    config: ProviderConfig.Voxtral,
    onSave: (ProviderConfig) -> Unit,
) {
    var apiKey by remember(config) { mutableStateOf(config.apiKey) }
    var model by remember(config) { mutableStateOf(config.model) }

    LaunchedEffect(apiKey) {
        delay(500)
        if (apiKey != config.apiKey && apiKey.isNotBlank()) {
            onSave(config.copy(apiKey = apiKey.trim(), model = model))
        }
    }

    ConfigSection(title = "Voxtral Configuration") {
        ApiKeyField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = "Mistral API Key",
        )

        Spacer(Modifier.height(12.dp))

        ModelDropdown(
            selected = model,
            options = listOf("voxtral-mini-latest", "voxtral-mini-2602", "voxtral-mini-2507"),
            onSelect = { model = it; onSave(config.copy(apiKey = apiKey.trim(), model = it)) },
        )
    }
}

@Composable
private fun VoxtralRealtimeConfigPanel(
    config: ProviderConfig.VoxtralRealtime,
    onSave: (ProviderConfig) -> Unit,
) {
    var apiKey by remember(config) { mutableStateOf(config.apiKey) }
    var model by remember(config) { mutableStateOf(config.model) }

    LaunchedEffect(apiKey) {
        delay(500)
        if (apiKey != config.apiKey && apiKey.isNotBlank()) {
            onSave(config.copy(apiKey = apiKey.trim(), model = model))
        }
    }

    ConfigSection(title = "Voxtral Realtime Configuration") {
        Text(
            "Cloud streaming via WebSocket. Requires Mistral API key.",
            fontSize = 13.sp,
            color = HushLabelColor,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        ApiKeyField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = "Mistral API Key",
        )

        Spacer(Modifier.height(12.dp))

        ModelDropdown(
            selected = model,
            options = listOf("voxtral-mini-transcribe-realtime-2602", "voxtral-mini-transcribe-realtime-latest"),
            onSelect = { model = it; onSave(config.copy(apiKey = apiKey.trim(), model = it)) },
        )
    }
}

@Composable
private fun OpenAiConfigPanel(
    config: ProviderConfig.OpenAiWhisper,
    onSave: (ProviderConfig) -> Unit,
) {
    var apiKey by remember(config) { mutableStateOf(config.apiKey) }
    var model by remember(config) { mutableStateOf(config.model) }
    var language by remember(config) { mutableStateOf(config.language) }

    LaunchedEffect(apiKey) {
        delay(500)
        if (apiKey != config.apiKey && apiKey.isNotBlank()) {
            onSave(config.copy(apiKey = apiKey.trim(), model = model, language = language.trim()))
        }
    }

    LaunchedEffect(language) {
        delay(500)
        if (language != config.language) {
            onSave(config.copy(apiKey = apiKey.trim(), model = model, language = language.trim()))
        }
    }

    ConfigSection(title = "OpenAI Configuration") {
        ApiKeyField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = "OpenAI API Key",
        )

        Spacer(Modifier.height(12.dp))

        ModelDropdown(
            selected = model,
            options = listOf("whisper-1", "gpt-4o-transcribe", "gpt-4o-mini-transcribe"),
            onSelect = { model = it; onSave(config.copy(apiKey = apiKey.trim(), model = it, language = language.trim())) },
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = language,
            onValueChange = { language = it },
            label = { Text("Language (optional, e.g. en, de)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = settingsTextFieldColors(),
        )
    }
}

@Composable
private fun GroqConfigPanel(
    config: ProviderConfig.Groq,
    onSave: (ProviderConfig) -> Unit,
) {
    var apiKey by remember(config) { mutableStateOf(config.apiKey) }
    var model by remember(config) { mutableStateOf(config.model) }

    LaunchedEffect(apiKey) {
        delay(500)
        if (apiKey != config.apiKey && apiKey.isNotBlank()) {
            onSave(config.copy(apiKey = apiKey.trim(), model = model))
        }
    }

    ConfigSection(title = "Groq Configuration") {
        ApiKeyField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = "Groq API Key",
        )

        Spacer(Modifier.height(12.dp))

        ModelDropdown(
            selected = model,
            options = listOf("whisper-large-v3-turbo", "whisper-large-v3"),
            onSelect = { model = it; onSave(config.copy(apiKey = apiKey.trim(), model = it)) },
        )
    }
}

@Composable
private fun LocalConfigPanel(
    config: ProviderConfig.Local,
    onSave: (ProviderConfig) -> Unit,
    modelStatuses: Map<String, ModelStatus>,
    modelDownloadProgress: Map<String, Float>,
    onDownloadModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
) {
    var model by remember(config) { mutableStateOf(config.model) }
    var language by remember(config) { mutableStateOf(config.language) }

    LaunchedEffect(language) {
        delay(500)
        if (language != config.language) {
            onSave(config.copy(model = model, language = language.trim()))
        }
    }

    val selectedModelInfo = ModelManager.getModelInfo(model)
    val modelStatus = modelStatuses[model] ?: ModelStatus.NOT_DOWNLOADED
    val progress = modelDownloadProgress[model] ?: 0f

    ConfigSection(title = "Local Configuration (Deprecated)") {
        Text(
            "Regularly crashes the app. Use Moonshine instead for on-device transcription.",
            fontSize = 13.sp,
            color = Color(0xFFFF6B6B),
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // Model selector
        ModelDropdown(
            selected = model,
            options = ModelManager.AVAILABLE_MODELS.map { it.id },
            onSelect = { model = it; onSave(config.copy(model = it, language = language.trim())) },
        )

        Spacer(Modifier.height(12.dp))

        // Model status + download/delete
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            selectedModelInfo?.displayName ?: model,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                        Text(
                            when (modelStatus) {
                                ModelStatus.NOT_DOWNLOADED -> "Not downloaded (${formatSize(selectedModelInfo?.sizeBytes ?: 0)})"
                                ModelStatus.DOWNLOADING -> "Downloading... ${(progress * 100).toInt()}%"
                                ModelStatus.READY -> "Ready"
                                ModelStatus.ERROR -> ErrorMessages.downloadFailed()
                            },
                            fontSize = 12.sp,
                            color = when (modelStatus) {
                                ModelStatus.READY -> Color(0xFF4CAF50)
                                ModelStatus.ERROR -> Color(0xFFEF5350)
                                ModelStatus.DOWNLOADING -> Color(0xFF6C63FF)
                                else -> HushLabelColor
                            },
                        )
                    }

                    when (modelStatus) {
                        ModelStatus.NOT_DOWNLOADED, ModelStatus.ERROR -> {
                            OutlinedButton(
                                onClick = { onDownloadModel(model) },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                modifier = Modifier.testTag(TestTags.MODEL_DOWNLOAD_BUTTON),
                            ) {
                                Text("Download", fontSize = 13.sp, color = Color.White)
                            }
                        }
                        ModelStatus.DOWNLOADING -> {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(32.dp),
                                color = Color(0xFF6C63FF),
                                trackColor = Color.White.copy(alpha = 0.1f),
                                strokeWidth = 3.dp,
                            )
                        }
                        ModelStatus.READY -> {
                            TextButton(
                                onClick = { onDeleteModel(model) },
                                modifier = Modifier.testTag(TestTags.MODEL_DELETE_BUTTON),
                            ) {
                                Text("Delete", color = HushLabelColor, fontSize = 13.sp)
                            }
                        }
                    }
                }

                if (modelStatus == ModelStatus.DOWNLOADING) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFF6C63FF),
                        trackColor = Color.White.copy(alpha = 0.1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = language,
            onValueChange = { language = it },
            label = { Text("Language (optional, e.g. en, de)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = settingsTextFieldColors(),
        )
    }
}

@Composable
private fun MoonshineConfigPanel(
    config: ProviderConfig.Moonshine,
    onSave: (ProviderConfig) -> Unit,
    modelStatuses: Map<String, ModelStatus>,
    modelDownloadProgress: Map<String, Float>,
    onDownloadModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
) {
    var model by remember(config) { mutableStateOf(config.model) }

    val selectedModelInfo = ModelManager.getMoonshineModelInfo(model)
    val modelStatus = modelStatuses[model] ?: ModelStatus.NOT_DOWNLOADED
    val progress = modelDownloadProgress[model] ?: 0f

    ConfigSection(title = "Moonshine Configuration") {
        Text(
            "No API key needed. Streams on device.",
            fontSize = 13.sp,
            color = HushLabelColor,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // Model selector
        ModelDropdown(
            selected = model,
            options = ModelManager.AVAILABLE_MOONSHINE_MODELS.map { it.id },
            onSelect = { model = it; onSave(config.copy(model = it)) },
        )

        Spacer(Modifier.height(12.dp))

        // Model status + download/delete
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            selectedModelInfo?.displayName ?: model,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                        Text(
                            when (modelStatus) {
                                ModelStatus.NOT_DOWNLOADED -> "Not downloaded (${formatSize(selectedModelInfo?.totalSizeBytes ?: 0)})"
                                ModelStatus.DOWNLOADING -> "Downloading... ${(progress * 100).toInt()}%"
                                ModelStatus.READY -> "Ready"
                                ModelStatus.ERROR -> ErrorMessages.downloadFailed()
                            },
                            fontSize = 12.sp,
                            color = when (modelStatus) {
                                ModelStatus.READY -> Color(0xFF4CAF50)
                                ModelStatus.ERROR -> Color(0xFFEF5350)
                                ModelStatus.DOWNLOADING -> Color(0xFF6C63FF)
                                else -> HushLabelColor
                            },
                        )
                    }

                    when (modelStatus) {
                        ModelStatus.NOT_DOWNLOADED, ModelStatus.ERROR -> {
                            OutlinedButton(
                                onClick = { onDownloadModel(model) },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                modifier = Modifier.testTag(TestTags.MODEL_DOWNLOAD_BUTTON),
                            ) {
                                Text("Download", fontSize = 13.sp, color = Color.White)
                            }
                        }
                        ModelStatus.DOWNLOADING -> {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(32.dp),
                                color = Color(0xFF6C63FF),
                                trackColor = Color.White.copy(alpha = 0.1f),
                                strokeWidth = 3.dp,
                            )
                        }
                        ModelStatus.READY -> {
                            TextButton(
                                onClick = { onDeleteModel(model) },
                                modifier = Modifier.testTag(TestTags.MODEL_DELETE_BUTTON),
                            ) {
                                Text("Delete", color = HushLabelColor, fontSize = 13.sp)
                            }
                        }
                    }
                }

                if (modelStatus == ModelStatus.DOWNLOADING) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFF6C63FF),
                        trackColor = Color.White.copy(alpha = 0.1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextEnhancementPanel(
    config: PostProcessorConfig,
    onSave: (PostProcessorConfig) -> Unit,
) {
    var enabled by remember(config) { mutableStateOf(config.enabled) }
    var apiType by remember(config) { mutableStateOf(config.apiType) }
    var apiKey by remember(config) { mutableStateOf(config.apiKey) }
    var model by remember(config) { mutableStateOf(config.model) }
    var systemPrompt by remember(config) { mutableStateOf(config.systemPrompt) }

    fun currentConfig() = PostProcessorConfig(
        enabled = enabled,
        apiType = apiType,
        apiKey = apiKey.trim(),
        baseUrl = PostProcessorConfig.baseUrlForType(apiType),
        model = model.trim(),
        systemPrompt = systemPrompt,
    )

    // Auto-save with debounce for text fields
    LaunchedEffect(apiKey, model, systemPrompt) {
        delay(500)
        val current = currentConfig()
        if (current != config) {
            onSave(current)
        }
    }

    Text(
        "Text Enhancement",
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = HushLabelColor,
        modifier = Modifier.padding(bottom = 12.dp),
    )

    ConfigSection(title = "LLM Post-Processing") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Clean up transcriptions",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                )
                Text(
                    "Fix grammar, punctuation, and filler words",
                    fontSize = 12.sp,
                    color = HushLabelColor,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    onSave(currentConfig().copy(enabled = it))
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF6C63FF),
                    uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                ),
            )
        }

        if (enabled) {
            Spacer(Modifier.height(16.dp))

            // API type dropdown
            var apiTypeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = apiTypeExpanded,
                onExpandedChange = { apiTypeExpanded = it },
            ) {
                OutlinedTextField(
                    value = when (apiType) {
                        PostProcessorConfig.API_TYPE_ANTHROPIC -> "Anthropic"
                        PostProcessorConfig.API_TYPE_OPENAI -> "OpenAI-compatible"
                        else -> apiType
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("API Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = apiTypeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    colors = settingsTextFieldColors(),
                )
                ExposedDropdownMenu(
                    expanded = apiTypeExpanded,
                    onDismissRequest = { apiTypeExpanded = false },
                    containerColor = Color(0xFF2A2A3E),
                ) {
                    DropdownMenuItem(
                        text = { Text("Anthropic", color = Color.White) },
                        onClick = {
                            apiType = PostProcessorConfig.API_TYPE_ANTHROPIC
                            model = PostProcessorConfig.DEFAULT_ANTHROPIC_MODEL
                            apiTypeExpanded = false
                            onSave(currentConfig())
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("OpenAI-compatible (Groq)", color = Color.White) },
                        onClick = {
                            apiType = PostProcessorConfig.API_TYPE_OPENAI
                            model = PostProcessorConfig.DEFAULT_OPENAI_MODEL
                            apiTypeExpanded = false
                            onSave(currentConfig())
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // API key
            ApiKeyField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = if (apiType == PostProcessorConfig.API_TYPE_ANTHROPIC)
                    "Anthropic API Key" else "API Key",
            )

            Spacer(Modifier.height(12.dp))

            // Model dropdown
            ModelDropdown(
                selected = model,
                options = PostProcessorConfig.modelsForType(apiType),
                onSelect = { model = it; onSave(currentConfig().copy(model = it)) },
            )

            Spacer(Modifier.height(12.dp))

            // System prompt
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("System Prompt") },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
                colors = settingsTextFieldColors(),
            )

            // Reset to default button
            if (systemPrompt != PostProcessorConfig.DEFAULT_SYSTEM_PROMPT) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        systemPrompt = PostProcessorConfig.DEFAULT_SYSTEM_PROMPT
                        onSave(currentConfig().copy(systemPrompt = PostProcessorConfig.DEFAULT_SYSTEM_PROMPT))
                    },
                ) {
                    Text("Reset prompt to default", color = HushLabelColor, fontSize = 13.sp)
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format("%.0f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format("%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}

@Composable
private fun ConfigSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = HushCardShape,
        colors = CardDefaults.cardColors(containerColor = HushCardBackground),
        border = HushCardBorder,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 12.dp),
            )
            content()
        }
    }
}

@Composable
private fun ApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    val hasKey = value.isNotBlank()
    var isEditing by remember { mutableStateOf(!hasKey) }
    var editValue by remember { mutableStateOf("") }
    var lastEmitted by remember { mutableStateOf(value) }

    // Detect external config changes (e.g., switching provider tabs)
    // but ignore changes we caused via onValueChange
    if (value != lastEmitted) {
        isEditing = !value.isNotBlank()
        editValue = ""
        lastEmitted = value
    }

    if (!isEditing && hasKey) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "\u00B7\u00B7\u00B7\u00B7\u00B7\u00B7\u00B7\u00B7\u00B7\u00B7\u00B7\u00B7${value.takeLast(4)}",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                isEditing = true
                editValue = ""
            }) {
                Text("Change", color = HushLabelColor, fontSize = 13.sp)
            }
        }
    } else {
        OutlinedTextField(
            value = editValue,
            onValueChange = {
                editValue = it
                lastEmitted = it
                onValueChange(it)
            },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Password }
                .testTag(TestTags.API_KEY_FIELD),
            colors = settingsTextFieldColors(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Model") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            colors = settingsTextFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color(0xFF2A2A3E),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = Color.White) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun settingsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White.copy(alpha = 0.8f),
    focusedBorderColor = Color(0xFF6C63FF),
    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
    cursorColor = Color(0xFF6C63FF),
    focusedLabelColor = Color(0xFF6C63FF),
    unfocusedLabelColor = HushLabelColor,
)
