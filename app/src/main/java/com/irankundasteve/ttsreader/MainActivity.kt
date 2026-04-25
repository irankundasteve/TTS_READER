package com.irankundasteve.ttsreader

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.irankundasteve.ttsreader.ui.theme.DarkSurface
import com.irankundasteve.ttsreader.ui.theme.MintPrimary
import com.irankundasteve.ttsreader.ui.theme.SplashBackground
import com.irankundasteve.ttsreader.ui.theme.TTSReaderTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: LaunchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val playbackState by viewModel.playbackState.collectAsState()
            TTSReaderTheme(darkTheme = uiState.darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LaunchRoute(
                        uiState = uiState,
                        playbackState = playbackState,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun LaunchRoute(
    uiState: LaunchUiState,
    playbackState: PlaybackUiState,
    viewModel: LaunchViewModel,
) {
    AnimatedContent(
        targetState = uiState.isReady,
        transitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = 320)) togetherWith
                fadeOut(animationSpec = tween(durationMillis = 220))
        },
        label = "launch_transition",
    ) { ready ->
        if (ready) {
            HomeScreen(
                uiState = uiState,
                playbackState = playbackState,
                viewModel = viewModel,
            )
        } else {
            SplashScreen()
        }
    }
}

@Composable
private fun SplashScreen() {
    var animateLogo by remember { mutableStateOf(false) }
    val logoScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (animateLogo) 1f else 0.86f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "logo_scale",
    )
    val logoTint by animateColorAsState(
        targetValue = if (animateLogo) Color(0xFF87F0CF) else Color(0xFF5CCAA7),
        animationSpec = tween(durationMillis = 650),
        label = "logo_tint",
    )

    LaunchedEffect(Unit) {
        delay(40)
        animateLogo = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            androidx.compose.material3.Icon(
                painter = painterResource(id = R.drawable.ic_splash_logo),
                contentDescription = null,
                tint = logoTint,
                modifier = Modifier.scale(logoScale),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "TTS Reader",
                style = MaterialTheme.typography.headlineSmall,
                color = Color(0xFFF2F7F5),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    uiState: LaunchUiState,
    playbackState: PlaybackUiState,
    viewModel: LaunchViewModel,
) {
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var selectedLanguage by rememberSaveable(uiState.selectedLanguage) {
        mutableStateOf(uiState.selectedLanguage)
    }
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var sliderIsDragging by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val maxCharacters = 5_000
    val hasText = textFieldValue.text.isNotBlank()
    val playbackVisible = playbackState.hasSession
    val voiceSettings = uiState.voiceSettings

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(playbackState.progressFraction) {
        if (!sliderIsDragging) {
            sliderPosition = playbackState.progressFraction
        }
    }

    LaunchedEffect(
        playbackState.currentRange,
        playbackState.activeText,
        playbackState.status,
    ) {
        if (playbackState.hasSession && playbackState.activeText == textFieldValue.text) {
            playbackState.currentRange?.let { range ->
                textFieldValue = textFieldValue.copy(
                    selection = TextRange(range.first, range.last + 1),
                )
            }
        } else if (!playbackState.hasSession) {
            val cursor = textFieldValue.selection.end.coerceIn(0, textFieldValue.text.length)
            textFieldValue = textFieldValue.copy(selection = TextRange(cursor))
        }
    }

    LaunchedEffect(playbackState.status, playbackState.activeText) {
        if (
            playbackState.status == PlaybackStatus.PREPARING &&
            playbackState.activeText.length > 700
        ) {
            Toast.makeText(context, "Preparing audio...", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(SplashBackground)
                .statusBarsPadding(),
            containerColor = SplashBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "TTS Reader",
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SplashBackground,
                        titleContentColor = Color(0xFFF2F7F5),
                        actionIconContentColor = MintPrimary,
                    ),
                    actions = {
                        IconButton(
                            onClick = {
                                readClipboardText(context)?.let { pasted ->
                                    if (playbackVisible) {
                                        viewModel.stopPlayback()
                                    }
                                    textFieldValue = TextFieldValue(pasted.take(maxCharacters))
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = "Paste text",
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (playbackVisible) {
                        FloatingActionButton(
                            onClick = { viewModel.stopPlayback() },
                            containerColor = Color(0xFF2A3432),
                            contentColor = Color(0xFFF2F7F5),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = "Stop",
                            )
                        }
                    }

                    FloatingActionButton(
                        onClick = {
                            if (!hasText) {
                                Toast.makeText(
                                    context,
                                    "Please enter text",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                return@FloatingActionButton
                            }

                            viewModel.onPrimaryPlaybackAction(
                                text = textFieldValue.text,
                                language = selectedLanguage,
                            )
                        },
                        modifier = Modifier.alpha(if (hasText) 1f else 0.45f),
                        containerColor = if (hasText) MintPrimary else Color(0xFF3B4E49),
                        contentColor = if (hasText) Color(0xFF062C23) else Color(0xFF9AB0AA),
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                    ) {
                        Icon(
                            imageVector = if (
                                playbackState.status == PlaybackStatus.PLAYING ||
                                playbackState.status == PlaybackStatus.PREPARING
                            ) {
                                Icons.Filled.Pause
                            } else {
                                Icons.Filled.PlayArrow
                            },
                            contentDescription = "Toggle playback",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = buildString {
                        append(selectedLanguage.voiceLabel)
                        append(" • ")
                        append(if (uiState.ttsReady) "TTS ready" else "TTS unavailable")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.6f)
                        .background(
                            color = DarkSurface,
                            shape = MaterialTheme.shapes.large,
                        )
                        .border(
                            width = 1.dp,
                            color = Color(0xFF23322E),
                            shape = MaterialTheme.shapes.large,
                        )
                        .padding(16.dp),
                ) {
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = { updated ->
                            val normalized = normalizeTextFieldValue(updated, maxCharacters)
                            if (playbackVisible && normalized.text != textFieldValue.text) {
                                viewModel.stopPlayback()
                            }
                            textFieldValue = normalized
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                        placeholder = {
                            Text(text = "Type or paste text here...")
                        },
                        trailingIcon = {
                            if (textFieldValue.text.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        if (playbackVisible) {
                                            viewModel.stopPlayback()
                                        }
                                        textFieldValue = TextFieldValue("")
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "Clear text",
                                    )
                                }
                            }
                        },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        minLines = 12,
                        maxLines = Int.MAX_VALUE,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    )

                    Text(
                        text = "${textFieldValue.text.length} / $maxCharacters",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(
                                color = DarkSurface.copy(alpha = 0.92f),
                                shape = MaterialTheme.shapes.small,
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                VoiceSelector(
                    selectedLanguage = selectedLanguage,
                    expanded = voiceMenuExpanded,
                    settingsActive = !voiceSettings.isDefault,
                    onExpandedChange = { voiceMenuExpanded = it },
                    onLanguageSelected = {
                        if (playbackVisible) {
                            viewModel.stopPlayback()
                        }
                        selectedLanguage = it
                        voiceMenuExpanded = false
                    },
                    onSettingsClick = { showSettingsSheet = true },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = buildString {
                            append("Storage ")
                            append(if (uiState.permissions.storageGranted) "ready" else "not granted")
                            append(" • Media ")
                            append(if (uiState.permissions.mediaGranted) "ready" else "not granted")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (playbackVisible) {
                    PlaybackSection(
                        playbackState = playbackState,
                        sliderPosition = sliderPosition,
                        sliderIsDragging = sliderIsDragging,
                        onSliderPositionChange = { value ->
                            sliderIsDragging = true
                            sliderPosition = value
                        },
                        onSliderChangeFinished = {
                            sliderIsDragging = false
                            viewModel.seekPlayback(sliderPosition, selectedLanguage)
                        },
                    )
                }
            }
        }

        if (showSettingsSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC060908)),
            )
            VoiceSettingsSheet(
                modifier = Modifier.align(Alignment.BottomCenter),
                voiceSettings = voiceSettings,
                onDismiss = { showSettingsSheet = false },
                onRateChange = viewModel::updateVoiceRate,
                onPitchChange = viewModel::updateVoicePitch,
                onPreview = { viewModel.previewVoice(selectedLanguage) },
                onReset = viewModel::resetVoiceSettings,
            )
        }
    }
}

@Composable
private fun VoiceSettingsSheet(
    modifier: Modifier = Modifier,
    voiceSettings: VoiceSettings,
    onDismiss: () -> Unit,
    onRateChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onPreview: () -> Unit,
    onReset: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.42f),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color(0xFF161D1B),
        tonalElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Voice Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFF2F7F5),
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = "Close settings",
                        tint = Color(0xFFF2F7F5),
                    )
                }
            }

            VoiceSettingsSlider(
                label = "Speed",
                value = voiceSettings.rate,
                valueText = "${formatDecimal(voiceSettings.rate)}x",
                valueRange = 0.5f..2.0f,
                onValueChange = onRateChange,
            )

            VoiceSettingsSlider(
                label = "Pitch",
                value = voiceSettings.pitch,
                valueText = formatDecimal(voiceSettings.pitch),
                valueRange = 0.5f..1.5f,
                onValueChange = onPitchChange,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = onPreview,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(text = "Sample")
                }

                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                    },
                )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(text = "Reset")
                }
            }
        }
    }
}

@Composable
private fun VoiceSelector(
    selectedLanguage: SupportedLanguage,
    expanded: Boolean,
    settingsActive: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onLanguageSelected: (SupportedLanguage) -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Voice",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth(0.8f),
            ) {
                OutlinedTextField(
                    value = selectedLanguage.voiceLabel,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.5.dp,
                            color = MintPrimary,
                            shape = MaterialTheme.shapes.medium,
                        ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    trailingIcon = {
                        IconButton(onClick = { onExpandedChange(!expanded) }) {
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Expand voice list",
                            )
                        }
                    },
                )

                androidx.compose.material3.DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpandedChange(false) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface),
                ) {
                    SupportedLanguage.entries.forEach { option ->
                        val isSelected = option == selectedLanguage
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.voiceLabel,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MintPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            leadingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MintPrimary,
                                    )
                                }
                            },
                            onClick = { onLanguageSelected(option) },
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .background(
                    color = if (settingsActive) MintPrimary.copy(alpha = 0.18f) else Color(0xFF1B2422),
                    shape = MaterialTheme.shapes.medium,
                )
                .border(
                    width = 1.dp,
                    color = if (settingsActive) MintPrimary else Color(0xFF2D3C38),
                    shape = MaterialTheme.shapes.medium,
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "Voice settings",
                tint = if (settingsActive) MintPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VoiceSettingsSlider(
    label: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFF2F7F5),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MintPrimary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}

@Composable
private fun PlaybackSection(
    playbackState: PlaybackUiState,
    sliderPosition: Float,
    sliderIsDragging: Boolean,
    onSliderPositionChange: (Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
) {
    val sliderValue = if (sliderIsDragging) sliderPosition else playbackState.progressFraction

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (
            playbackState.status == PlaybackStatus.PREPARING &&
            playbackState.activeText.length > 700
        ) {
            Text(
                text = "Preparing audio...",
                style = MaterialTheme.typography.bodySmall,
                color = MintPrimary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(playbackState.elapsedMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatDuration(playbackState.totalDurationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Slider(
            value = sliderValue,
            onValueChange = onSliderPositionChange,
            onValueChangeFinished = onSliderChangeFinished,
        )
    }
}

private fun readClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clipData = clipboard?.primaryClip ?: return null
    if (clipData.itemCount == 0) {
        return null
    }

    return clipData
        .getItemAt(0)
        .coerceToText(context)
        ?.toString()
        ?.takeIf { it.isNotBlank() }
}

private fun normalizeTextFieldValue(
    value: TextFieldValue,
    maxCharacters: Int,
): TextFieldValue {
    val truncatedText = value.text.take(maxCharacters)
    val end = value.selection.end.coerceIn(0, truncatedText.length)
    val start = value.selection.start.coerceIn(0, end)
    return value.copy(
        text = truncatedText,
        selection = TextRange(start, end),
    )
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatDecimal(value: Float): String {
    return "%.1f".format(value)
}
