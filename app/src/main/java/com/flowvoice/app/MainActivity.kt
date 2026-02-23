package com.flowvoice.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
                )
            }
        }
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
                        "FlowVoice",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                actions = {
                    TextButton(onClick = onShowApiKeyDialog) {
                        Text("API Key", color = Color.White.copy(alpha = 0.7f))
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Status text
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
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

                Spacer(Modifier.height(8.dp))

                Text(
                    text = when (state.dictationState) {
                        DictationService.DictationState.IDLE -> "Double-tap volume down or tap the mic"
                        DictationService.DictationState.RECORDING -> "Speak now — double-tap volume to stop"
                        DictationService.DictationState.PROCESSING -> "Sending to Voxtral..."
                        DictationService.DictationState.DONE -> "Text copied to clipboard"
                        DictationService.DictationState.ERROR -> "Check API key and try again"
                    },
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                )
            }

            // Mic button
            MicButton(
                isRecording = isRecording,
                isProcessing = isProcessing,
                onClick = onToggle,
            )

            // Last transcription
            if (state.lastTranscription.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.08f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Last transcription",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.lastTranscription,
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 24.sp,
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(1.dp))
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
    var key by remember { mutableStateOf(currentKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Voxtral API Key") },
        text = {
            Column {
                Text(
                    "Enter your Mistral AI API key for transcription.",
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
        },
        confirmButton = {
            TextButton(onClick = { onSave(key) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
