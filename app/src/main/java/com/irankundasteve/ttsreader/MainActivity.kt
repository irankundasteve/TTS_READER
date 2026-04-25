package com.irankundasteve.ttsreader

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
            TTSReaderTheme(darkTheme = uiState.darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LaunchRoute(uiState = uiState)
                }
            }
        }
    }
}

@Composable
private fun LaunchRoute(uiState: LaunchUiState) {
    AnimatedContent(
        targetState = uiState.isReady,
        transitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = 320)) togetherWith
                fadeOut(animationSpec = tween(durationMillis = 220))
        },
        label = "launch_transition",
    ) { ready ->
        if (ready) {
            HomeScreen(uiState = uiState)
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

@Composable
private fun HomeScreen(uiState: LaunchUiState) {
    var text by rememberSaveable { mutableStateOf("") }
    var selectedLanguage by rememberSaveable(uiState.selectedLanguage) {
        mutableStateOf(uiState.selectedLanguage)
    }
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val maxCharacters = 5_000
    val hasText = text.isNotBlank()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

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
                                text = pasted.take(maxCharacters)
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentPaste,
                            contentDescription = "Paste text",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = if (hasText) ({}) else ({}),
                modifier = Modifier.alpha(if (hasText) 1f else 0.45f),
                containerColor = if (hasText) MintPrimary else Color(0xFF3B4E49),
                contentColor = if (hasText) Color(0xFF062C23) else Color(0xFF9AB0AA),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier.size(28.dp),
                )
            }
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
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
                        value = text,
                        onValueChange = { updated ->
                            text = updated.take(maxCharacters)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                        placeholder = {
                            Text(text = "Type or paste text here...")
                        },
                        trailingIcon = {
                            if (text.isNotEmpty()) {
                                IconButton(onClick = { text = "" }) {
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
                        text = "${text.length} / $maxCharacters",
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
                    onExpandedChange = { voiceMenuExpanded = it },
                    onLanguageSelected = {
                        selectedLanguage = it
                        voiceMenuExpanded = false
                    },
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
            }
        }
    }
}

@Composable
private fun VoiceSelector(
    selectedLanguage: SupportedLanguage,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onLanguageSelected: (SupportedLanguage) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
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
