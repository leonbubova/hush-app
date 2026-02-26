package com.hush.app

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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hush.app.transcription.ProviderConfig
import com.hush.app.transcription.ProviderFactory

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
    onOpenDrawer: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = Color(0xFF0D0D1A),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
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
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                "Transcription Provider",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.6f),
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
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
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
            val bgColor = if (isSelected) Color(0xFF6C63FF).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)
            val borderColor = if (isSelected) Color(0xFF6C63FF) else Color.Transparent

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .clickable { onSelect(id) }
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
                    Text(
                        ProviderFactory.displayName(id),
                        fontSize = 16.sp,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
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
) {
    when (config) {
        is ProviderConfig.Voxtral -> VoxtralConfigPanel(config, onSave)
        is ProviderConfig.OpenAiWhisper -> OpenAiConfigPanel(config, onSave)
        is ProviderConfig.Groq -> GroqConfigPanel(config, onSave)
        is ProviderConfig.Local -> {} // Phase 2
    }
}

@Composable
private fun VoxtralConfigPanel(
    config: ProviderConfig.Voxtral,
    onSave: (ProviderConfig) -> Unit,
) {
    var apiKey by remember(config) { mutableStateOf(config.apiKey) }
    var model by remember(config) { mutableStateOf(config.model) }

    ConfigSection(title = "Voxtral Configuration") {
        ApiKeyField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = "Mistral API Key",
        )

        Spacer(Modifier.height(12.dp))

        ModelDropdown(
            selected = model,
            options = listOf("voxtral-mini-latest"),
            onSelect = { model = it },
        )

        Spacer(Modifier.height(16.dp))

        SaveButton(
            enabled = apiKey != config.apiKey || model != config.model,
            onClick = { onSave(config.copy(apiKey = apiKey.trim(), model = model)) },
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
            onSelect = { model = it },
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

        Spacer(Modifier.height(16.dp))

        SaveButton(
            enabled = apiKey != config.apiKey || model != config.model || language != config.language,
            onClick = { onSave(config.copy(apiKey = apiKey.trim(), model = model, language = language.trim())) },
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

    ConfigSection(title = "Groq Configuration") {
        ApiKeyField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = "Groq API Key",
        )

        Spacer(Modifier.height(12.dp))

        ModelDropdown(
            selected = model,
            options = listOf("whisper-large-v3-turbo", "whisper-large-v3", "distil-whisper-large-v3-en"),
            onSelect = { model = it },
        )

        Spacer(Modifier.height(16.dp))

        SaveButton(
            enabled = apiKey != config.apiKey || model != config.model,
            onClick = { onSave(config.copy(apiKey = apiKey.trim(), model = model)) },
        )
    }
}

@Composable
private fun ConfigSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
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
    var isEditing by remember(value) { mutableStateOf(!hasKey) }
    var editValue by remember(value) { mutableStateOf("") }

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
                Text("Change", color = Color(0xFF6C63FF), fontSize = 13.sp)
            }
        }
    } else {
        OutlinedTextField(
            value = editValue,
            onValueChange = {
                editValue = it
                onValueChange(it)
            },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
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
            containerColor = Color(0xFF2A2A4A),
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
private fun SaveButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF6C63FF),
            disabledContainerColor = Color(0xFF6C63FF).copy(alpha = 0.3f),
        ),
    ) {
        Text("Save", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun settingsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White.copy(alpha = 0.8f),
    focusedBorderColor = Color(0xFF6C63FF),
    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
    cursorColor = Color(0xFF6C63FF),
    focusedLabelColor = Color(0xFF6C63FF),
    unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
)
