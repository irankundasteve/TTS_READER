package com.irankundasteve.ttsreader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = MintPrimary,
    secondary = MintSecondary,
    background = DarkBackground,
    surface = DarkSurface,
)

private val LightColors = lightColorScheme(
    primary = MintSecondary,
    secondary = MintPrimary,
    background = LightBackground,
    surface = LightSurface,
)

@Composable
fun TTSReaderTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
