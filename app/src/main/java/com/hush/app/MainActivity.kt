package com.hush.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.hush.app.BuildConfig
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hush.app.ui.theme.HushTheme
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val PlayfairDisplay = FontFamily(
    Font(R.font.playfair_display_regular, weight = FontWeight.Normal),
    Font(R.font.playfair_display_bold, weight = FontWeight.Bold),
)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var lastVolumeDownTime = 0L
    private var pendingExportJson: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.RECORD_AUDIO] == true) {
            viewModel.startServiceIfNeeded()
        }
    }

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val json = pendingExportJson ?: return@registerForActivityResult
        pendingExportJson = null
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            android.widget.Toast.makeText(this, "Data exported", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Export failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                ?: throw IllegalStateException("Could not read file")
            val result = viewModel.importFromJson(json)
            android.widget.Toast.makeText(
                this,
                "Imported ${result.historyCount} entries, ${result.usageCount} sessions",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Import failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissions()

        setContent {
            HushTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                BackHandler(enabled = state.currentScreen != MainViewModel.AppScreen.HOME) {
                    viewModel.navigateTo(MainViewModel.AppScreen.HOME)
                }
                BackHandler(enabled = drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        HushDrawerContent(
                            currentScreen = state.currentScreen,
                            onNavigate = { screen ->
                                viewModel.navigateTo(screen)
                                scope.launch { drawerState.close() }
                            },
                            onAccessibilitySettings = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                scope.launch { drawerState.close() }
                            },
                        )
                    },
                ) {
                    when (state.currentScreen) {
                        MainViewModel.AppScreen.HOME -> HushScreen(
                            state = state,
                            onToggle = { viewModel.toggle() },
                            onClearHistory = { viewModel.clearHistory() },
                            onCopyText = { text ->
                                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Transcription", text))
                                android.widget.Toast.makeText(this, "Copied", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onEnableAccessibility = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                        )
                        MainViewModel.AppScreen.USAGE -> HushUsageScaffold(
                            sessions = state.usageSessions,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onBack = { viewModel.navigateTo(MainViewModel.AppScreen.HOME) },
                        )
                        MainViewModel.AppScreen.SETTINGS -> SettingsScreen(
                            state = state,
                            onSetActiveProvider = { viewModel.setActiveProvider(it) },
                            onSaveProviderConfig = { id, config -> viewModel.saveProviderConfig(id, config) },
                            onDownloadModel = { viewModel.downloadModel(it) },
                            onDeleteModel = { viewModel.deleteModel(it) },
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onBack = { viewModel.navigateTo(MainViewModel.AppScreen.HOME) },
                            onExport = {
                                pendingExportJson = viewModel.getExportJson()
                                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                                createDocumentLauncher.launch("hush-backup-$dateStr.json")
                            },
                            onImport = {
                                openDocumentLauncher.launch(arrayOf("application/json"))
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startServiceIfNeeded()
        }
        viewModel.refreshAccessibilityStatus()
        viewModel.refreshHistory()
        viewModel.setAppForeground(true)
    }

    override fun onPause() {
        super.onPause()
        viewModel.setAppForeground(false)
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
fun HushScreen(
    state: MainViewModel.UiState,
    onToggle: () -> Unit,
    onClearHistory: () -> Unit,
    onCopyText: (String) -> Unit = {},
    onEnableAccessibility: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
) {
    val isRecording = state.dictationState == DictationService.DictationState.RECORDING
    val isStreaming = state.dictationState == DictationService.DictationState.STREAMING
    val isActive = isRecording || isStreaming
    val isProcessing = state.dictationState == DictationService.DictationState.PROCESSING

    val bgColor by animateColorAsState(
        targetValue = when {
            isActive -> Color(0xFF1A1A2E)
            else -> Color(0xFF0D0D1A)
        },
        animationSpec = tween(500),
        label = "bg"
    )

    Scaffold(
        containerColor = bgColor,
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Hush!",
                            fontFamily = PlayfairDisplay,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        if (BuildConfig.DEBUG) {
                            Text(
                                "  dev",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }
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
            // Track mic button center for blob alignment
            var micCenterY by remember { mutableFloatStateOf(0f) }

            // Blobs behind everything, centered on mic button
            AnimatedBlobs(isRecording = isActive, centerYPx = micCenterY)

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
                        DictationService.DictationState.STREAMING -> "Streaming..."
                        DictationService.DictationState.PROCESSING -> "Transcribing..."
                        DictationService.DictationState.DONE -> "Copied!"
                        DictationService.DictationState.ERROR -> "Error"
                    },
                    fontSize = 32.sp,
                    fontFamily = PlayfairDisplay,
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                    modifier = Modifier.testTag(TestTags.STATUS_TEXT),
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = when (state.dictationState) {
                        DictationService.DictationState.IDLE -> "Double-tap volume down or tap the circle"
                        DictationService.DictationState.RECORDING -> "Speak now — double-tap volume to stop"
                        DictationService.DictationState.STREAMING -> "Speak now — text appears live"
                        DictationService.DictationState.PROCESSING -> "Decoding your yapping..."
                        DictationService.DictationState.DONE -> "Text copied to clipboard"
                        DictationService.DictationState.ERROR -> state.errorMessage.ifBlank { "Something went wrong" }
                    },
                    fontSize = 15.sp,
                    fontFamily = PlayfairDisplay,
                    color = Color.White.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(TestTags.SUBTITLE_TEXT),
                )

                // Current model indicator
                val activeConfig = state.providerConfigs[state.activeProviderId]
                if (activeConfig != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = activeConfig.displayLabel,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.35f),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Mic button — reports its center so blobs can align
            val hasHistory = state.history.isNotEmpty()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned { coords ->
                        micCenterY = coords.boundsInParent().center.y
                    },
                contentAlignment = Alignment.Center,
            ) {
                MicButton(
                    isRecording = isActive,
                    isProcessing = isProcessing,
                    onClick = onToggle,
                )
            }

            // Live streaming text
            if (isStreaming && state.streamingText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF6C63FF).copy(alpha = 0.15f)
                    ),
                ) {
                    Text(
                        state.streamingText,
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Accessibility setup banner
            if (!state.accessibilityEnabled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.ACCESSIBILITY_BANNER),
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

            // History section
            if (hasHistory) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "History",
                        fontSize = 16.sp,
                        fontFamily = PlayfairDisplay,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    TextButton(
                        onClick = onClearHistory,
                        modifier = Modifier.testTag(TestTags.HISTORY_CLEAR_BUTTON),
                    ) {
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(TestTags.historyItem(index))
                                .clickable { onCopyText(text) },
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
                    modifier = Modifier
                        .padding(vertical = 32.dp)
                        .testTag(TestTags.EMPTY_HISTORY_TEXT),
                )
            }
            } // end Column
        } // end Box
    }

}

@Composable
fun AnimatedBlobs(isRecording: Boolean, centerYPx: Float = 0f) {
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
            .blur(3.dp)
    ) {
        val cx = size.width / 2
        val cy = if (centerYPx > 0f) centerYPx else size.height / 2
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
            .clickable(enabled = !isProcessing) { onClick() }
            .testTag(TestTags.MIC_BUTTON),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HushUsageScaffold(
    sessions: List<RecordingSession>,
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
                        "Usage",
                        fontFamily = PlayfairDisplay,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            UsageScreen(sessions = sessions)
        }
    }
}

@Composable
fun HushDrawerContent(
    currentScreen: MainViewModel.AppScreen,
    onNavigate: (MainViewModel.AppScreen) -> Unit,
    onAccessibilitySettings: () -> Unit = {},
) {
    ModalDrawerSheet(
        drawerContainerColor = Color(0xFF1A1A2E),
    ) {
        Spacer(Modifier.height(32.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                "Hush",
                fontSize = 28.sp,
                fontFamily = PlayfairDisplay,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            if (BuildConfig.DEBUG) {
                Text(
                    "  dev",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        DrawerItem(
            label = "Home",
            selected = currentScreen == MainViewModel.AppScreen.HOME,
            onClick = { onNavigate(MainViewModel.AppScreen.HOME) },
            testTag = TestTags.DRAWER_HOME,
        )
        DrawerItem(
            label = "Usage",
            selected = currentScreen == MainViewModel.AppScreen.USAGE,
            onClick = { onNavigate(MainViewModel.AppScreen.USAGE) },
            testTag = TestTags.DRAWER_USAGE,
        )
        DrawerItem(
            label = "Settings",
            selected = currentScreen == MainViewModel.AppScreen.SETTINGS,
            onClick = { onNavigate(MainViewModel.AppScreen.SETTINGS) },
            testTag = TestTags.DRAWER_SETTINGS,
        )
        Spacer(Modifier.weight(1f))
        DrawerItem(
            label = "Accessibility Settings",
            selected = false,
            onClick = onAccessibilitySettings,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DrawerItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String = "",
) {
    val bgColor = if (selected) Color.White.copy(alpha = 0.08f) else Color.Transparent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            label,
            fontSize = 16.sp,
            fontFamily = PlayfairDisplay,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
        )
    }
}
