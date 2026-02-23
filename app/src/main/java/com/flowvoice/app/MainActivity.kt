package com.flowvoice.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowvoice.app.ui.theme.FlowVoiceTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var lastVolumeDownTime = 0L

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted or denied, service will handle */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissions()

        setContent {
            FlowVoiceTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                FlowVoiceScreen(
                    state = state,
                    onToggle = { viewModel.toggle() },
                    onSaveApiKey = { viewModel.saveApiKey(it) },
                    onShowApiKeyDialog = { viewModel.showApiKeyDialog() },
                    onDismissApiKeyDialog = { viewModel.dismissApiKeyDialog() },
                    onClearHistory = { viewModel.clearHistory() },
                    onCopyText = { text ->
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Transcription", text))
                        android.widget.Toast.makeText(this, "Copied", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onEnableAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAccessibilityStatus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val now = System.currentTimeMillis()
            if (now - lastVolumeDownTime < 400) {
                lastVolumeDownTime = 0
                viewModel.toggle()
                return true
            }
            lastVolumeDownTime = now
            // Let the first press through after a short delay
            return super.onKeyDown(keyCode, event)
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun requestPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowVoiceScreen(
    state: MainViewModel.UiState,
    onToggle: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onShowApiKeyDialog: () -> Unit,
    onDismissApiKeyDialog: () -> Unit,
    onClearHistory: () -> Unit,
    onCopyText: (String) -> Unit = {},
    onEnableAccessibility: () -> Unit = {},
) {
    val isRecording = state.dictationState == DictationService.DictationState.RECORDING
    val isProcessing = state.dictationState == DictationService.DictationState.PROCESSING

    val bgColor by animateColorAsState(
        targetValue = when {
            isRecording -> Color(0xFF1A1A2E)
            else -> Color(0xFF0D0D1A)
        },
        animationSpec = tween(500),
        label = "bg"
    )

    Scaffold(
        containerColor = bgColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Hush",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                actions = {
                    IconButton(onClick = onShowApiKeyDialog) {
                        Text("\u2699", fontSize = 20.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Status text
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = when (state.dictationState) {
                        DictationService.DictationState.IDLE -> "Ready"
                        DictationService.DictationState.RECORDING -> "Listening..."
                        DictationService.DictationState.PROCESSING -> "Transcribing..."
                        DictationService.DictationState.DONE -> "Copied!"
                        DictationService.DictationState.ERROR -> "Error"
                    },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when (state.dictationState) {
                        DictationService.DictationState.RECORDING -> Color(0xFF6C63FF)
                        DictationService.DictationState.DONE -> Color(0xFF4CAF50)
                        DictationService.DictationState.ERROR -> Color(0xFFEF5350)
                        else -> Color.White
                    }
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = when (state.dictationState) {
                        DictationService.DictationState.IDLE -> "Double-tap volume down or tap the mic"
                        DictationService.DictationState.RECORDING -> "Speak now — double-tap volume to stop"
                        DictationService.DictationState.PROCESSING -> "Sending to Voxtral..."
                        DictationService.DictationState.DONE -> "Text copied to clipboard"
                        DictationService.DictationState.ERROR -> state.errorMessage.ifBlank { "Something went wrong" }
                    },
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Accessibility setup banner
            if (!state.accessibilityEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2A2A4A)
                    ),
                    onClick = onEnableAccessibility,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Enable background shortcut",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Turn on accessibility to use volume double-tap from any app",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }
                        Text(
                            "\u2192",
                            fontSize = 20.sp,
                            color = Color(0xFF6C63FF),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Mic button
            MicButton(
                isRecording = isRecording,
                isProcessing = isProcessing,
                onClick = onToggle,
            )

            Spacer(Modifier.height(20.dp))

            // History section
            if (state.history.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "History",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                    TextButton(onClick = onClearHistory) {
                        Text(
                            "Clear",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.4f),
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    itemsIndexed(state.history) { index, text ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onCopyText(text) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (index == 0)
                                    Color.White.copy(alpha = 0.10f)
                                else
                                    Color.White.copy(alpha = 0.05f)
                            )
                        ) {
                            Text(
                                text,
                                fontSize = 15.sp,
                                color = Color.White.copy(alpha = if (index == 0) 0.9f else 0.6f),
                                lineHeight = 22.sp,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
                Text(
                    "Your transcriptions will appear here",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.3f),
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }

    // API Key Dialog
    if (state.showApiKeyDialog) {
        ApiKeyDialog(
            currentKey = state.apiKey,
            onSave = onSaveApiKey,
            onDismiss = onDismissApiKeyDialog,
        )
    }
}

@Composable
fun MicButton(
    isRecording: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val buttonColor by animateColorAsState(
        targetValue = when {
            isRecording -> Color(0xFF6C63FF)
            isProcessing -> Color(0xFF9E9E9E)
            else -> Color(0xFF2A2A4A)
        },
        animationSpec = tween(300),
        label = "btnColor"
    )

    val scale = if (isRecording) pulseScale else 1f

    Box(
        modifier = Modifier
            .size(140.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(buttonColor)
            .clickable(enabled = !isProcessing) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when {
                isRecording -> "◼"
                isProcessing -> "..."
                else -> "🎙"
            },
            fontSize = 48.sp,
        )
    }
}

@Composable
fun ApiKeyDialog(
    currentKey: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val hasKey = currentKey.isNotBlank()
    var isEditing by remember { mutableStateOf(!hasKey) }
    var key by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API Key") },
        text = {
            Column {
                if (!isEditing && hasKey) {
                    Text(
                        "Your Mistral API key is set.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(
                            "············${currentKey.takeLast(4)}",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { isEditing = true }) {
                        Text("Change key")
                    }
                } else {
                    Text(
                        if (hasKey) "Enter your new API key." else "Enter your Mistral AI API key for transcription.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            if (isEditing) {
                TextButton(
                    onClick = { onSave(key) },
                    enabled = key.isNotBlank(),
                ) {
                    Text("Save")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (isEditing && hasKey) {
                    isEditing = false
                    key = ""
                } else {
                    onDismiss()
                }
            }) {
                Text("Cancel")
            }
        },
    )
}
