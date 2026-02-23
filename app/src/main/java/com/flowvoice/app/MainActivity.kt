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
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowvoice.app.ui.theme.FlowVoiceTheme
import kotlin.math.cos
import kotlin.math.sin

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Blobs as full-screen background, biased toward center
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedBlobs(isRecording = isRecording)
            }

            // Content on top
            Column(
                modifier = Modifier
                    .fillMaxSize()
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

            Spacer(Modifier.height(24.dp))

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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                MicButton(
                    isRecording = isRecording,
                    isProcessing = isProcessing,
                    onClick = onToggle,
                )
            }

            Spacer(Modifier.height(8.dp))

            // History section
            if (state.history.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "History",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    TextButton(onClick = onClearHistory) {
                        Text(
                            "Clear",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.5f),
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
                                containerColor = Color.White.copy(alpha = 0.12f)
                            )
                        ) {
                            Text(
                                text,
                                fontSize = 15.sp,
                                color = Color.White.copy(alpha = if (index == 0) 0.9f else 0.7f),
                                lineHeight = 22.sp,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                }
            } else {
                Text(
                    "Your transcriptions will appear here",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            }
            } // end Column
        } // end Box
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
fun AnimatedBlobs(isRecording: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "blobs")

    val sizeMultiplier by animateFloatAsState(
        targetValue = if (isRecording) 1.4f else 1f,
        animationSpec = tween(800, easing = EaseInOutSine),
        label = "blobSize"
    )

    // Each blob: core color, edge/halo color, base size, position offsets, speed
    data class BlobSpec(
        val coreColor: Color,
        val edgeColor: Color,
        val baseRadius: Float,
        val xPhase: Float,
        val yPhase: Float,
        val speed: Int,
        val xOffset: Float,  // static offset from center
        val yOffset: Float,
    )

    val blobs = remember {
        listOf(
            // Upper-left dark red blob
            BlobSpec(
                coreColor = Color(0xFF8B1515),
                edgeColor = Color(0xFF2A0845),
                baseRadius = 450f,
                xPhase = 0f, yPhase = 0.5f,
                speed = 5500,
                xOffset = -160f, yOffset = -100f,
            ),
            // Lower-left dark red/maroon blob
            BlobSpec(
                coreColor = Color(0xFF7A1212),
                edgeColor = Color(0xFF1A0A3E),
                baseRadius = 380f,
                xPhase = 1.8f, yPhase = 0f,
                speed = 4800,
                xOffset = -120f, yOffset = 140f,
            ),
            // Right side large dark maroon blob
            BlobSpec(
                coreColor = Color(0xFF5A0E2A),
                edgeColor = Color(0xFF0D1B5E),
                baseRadius = 520f,
                xPhase = 0.6f, yPhase = 1.2f,
                speed = 6200,
                xOffset = 140f, yOffset = 30f,
            ),
            // Center blue-purple blob
            BlobSpec(
                coreColor = Color(0xFF1A0A4E),
                edgeColor = Color(0xFF0A0A2E),
                baseRadius = 350f,
                xPhase = 2.2f, yPhase = 2.8f,
                speed = 7000,
                xOffset = 0f, yOffset = -30f,
            ),
        )
    }

    val animValues = blobs.map { blob ->
        val xAnim by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * Math.PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(blob.speed, easing = LinearEasing),
            ),
            label = "blobX${blob.xPhase}"
        )
        val yAnim by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * Math.PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween((blob.speed * 1.3f).toInt(), easing = LinearEasing),
            ),
            label = "blobY${blob.yPhase}"
        )
        Pair(xAnim, yAnim)
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .blur(8.dp)
    ) {
        val cx = size.width / 2
        val cy = size.height * 0.33f
        val drift = 25f

        blobs.forEachIndexed { i, blob ->
            val (xAnim, yAnim) = animValues[i]
            val driftX = sin(xAnim + blob.xPhase) * drift
            val driftY = cos(yAnim + blob.yPhase) * drift
            val r = blob.baseRadius * sizeMultiplier
            val blobCenter = Offset(
                cx + blob.xOffset + driftX,
                cy + blob.yOffset + driftY,
            )

            // Solid core for defined shape
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to blob.coreColor,
                        0.5f to blob.coreColor.copy(alpha = 0.95f),
                        0.75f to blob.edgeColor.copy(alpha = 0.7f),
                        1f to Color.Transparent,
                    ),
                    center = blobCenter,
                    radius = r,
                ),
                radius = r,
                center = blobCenter,
            )
        }
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
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val ringAlpha by animateFloatAsState(
        targetValue = if (isProcessing) 0.3f else 0.85f,
        animationSpec = tween(300),
        label = "ringAlpha"
    )

    val scale = if (isRecording) pulseScale else 1f

    Box(
        modifier = Modifier
            .size(220.dp)
            .scale(scale)
            .clip(CircleShape)
            .clickable(enabled = !isProcessing) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            // Outer ring
            drawCircle(
                color = Color.White.copy(alpha = ringAlpha),
                radius = size.minDimension / 2 - 6f,
                center = center,
                style = Stroke(width = 1.8f),
            )
            // Inner ring — close to outer, slightly overlapping
            drawCircle(
                color = Color.White.copy(alpha = ringAlpha * 0.5f),
                radius = size.minDimension / 2 - 14f,
                center = center,
                style = Stroke(width = 1.2f),
            )
        }

        // Center icon — only when recording (small x) or processing
        if (isRecording) {
            Text(
                text = "\u00D7", // multiplication sign as thin x
                fontSize = 20.sp,
                color = Color.White.copy(alpha = 0.8f),
            )
        } else if (isProcessing) {
            Text(
                text = "\u2025",
                fontSize = 24.sp,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
        // Idle: no icon, just the rings
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
