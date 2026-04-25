package com.irankundasteve.ttsreader

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Ready to read",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = buildString {
                append(uiState.selectedLanguage.label)
                append(" loaded")
                append(" • ")
                append(if (uiState.ttsReady) "TTS ready" else "TTS unavailable")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyLarge,
            label = {
                Text(text = "Paste or type text")
            },
            placeholder = {
                Text(text = "Your text will appear here")
            },
            minLines = 6,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = buildString {
                append("Storage: ")
                append(if (uiState.permissions.storageGranted) "granted" else "not granted")
                append(" • Media: ")
                append(if (uiState.permissions.mediaGranted) "granted" else "not granted")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )
    }
}
