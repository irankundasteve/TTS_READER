package com.irankundasteve.ttsreader

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

data class PermissionSnapshot(
    val storageGranted: Boolean,
    val mediaGranted: Boolean,
)

data class LaunchUiState(
    val isReady: Boolean = false,
    val selectedLanguage: SupportedLanguage = SupportedLanguage.ENGLISH,
    val darkTheme: Boolean = true,
    val permissions: PermissionSnapshot = PermissionSnapshot(
        storageGranted = false,
        mediaGranted = false,
    ),
    val ttsReady: Boolean = false,
)

enum class SupportedLanguage(
    val tag: String,
    val label: String,
    val voiceLabel: String,
) {
    ENGLISH("en-US", "English", "English - US"),
    SWAHILI("sw", "Swahili", "Swahili"),
    KIRUNDI("rn", "Kirundi", "Kirundi");

    companion object {
        fun fromTag(tag: String?): SupportedLanguage {
            return entries.firstOrNull { it.tag == tag } ?: ENGLISH
        }
    }
}

class LaunchViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val app = application as TtsReaderApplication
    private val _uiState = MutableStateFlow(LaunchUiState())
    val uiState: StateFlow<LaunchUiState> = _uiState.asStateFlow()
    val playbackState: StateFlow<PlaybackUiState> = app.playbackState

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            val preferences = getApplication<Application>()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

            val language = SupportedLanguage.fromTag(
                preferences.getString(KEY_LANGUAGE, SupportedLanguage.ENGLISH.tag),
            )
            val darkTheme = preferences.getBoolean(KEY_DARK_THEME, true)
            val permissions = readPermissionSnapshot(getApplication())
            val ttsReady = initializeTts(language)

            val elapsed = SystemClock.elapsedRealtime() - startedAt
            if (elapsed < MIN_SPLASH_DURATION_MS) {
                delay(MIN_SPLASH_DURATION_MS - elapsed)
            }

            _uiState.value = LaunchUiState(
                isReady = true,
                selectedLanguage = language,
                darkTheme = darkTheme,
                permissions = permissions,
                ttsReady = ttsReady,
            )
        }
    }

    private suspend fun initializeTts(language: SupportedLanguage): Boolean {
        return suspendCancellableCoroutine { continuation ->
            app.initializeTextToSpeech(Locale.forLanguageTag(language.tag)) { ready ->
                if (continuation.isActive) {
                    continuation.resume(ready)
                }
            }
        }
    }

    private fun readPermissionSnapshot(context: Context): PermissionSnapshot {
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val mediaPermissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        return PermissionSnapshot(
            storageGranted = isGranted(context, storagePermission),
            mediaGranted = mediaPermissions.all { isGranted(context, it) },
        )
    }

    private fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun onPrimaryPlaybackAction(
        text: String,
        language: SupportedLanguage,
    ) {
        if (text.isBlank()) {
            return
        }

        when (playbackState.value.status) {
            PlaybackStatus.PLAYING,
            PlaybackStatus.PREPARING,
            -> app.pausePlayback()

            PlaybackStatus.PAUSED -> {
                val currentPlayback = playbackState.value
                if (
                    currentPlayback.activeText != text ||
                    currentPlayback.selectedLanguage != language
                ) {
                    startPlayback(text, language)
                } else {
                    viewModelScope.launch {
                        if (initializeTts(language)) {
                            app.resumePlayback()
                        }
                    }
                }
            }

            PlaybackStatus.IDLE -> startPlayback(text, language)
        }
    }

    fun seekPlayback(
        progress: Float,
        language: SupportedLanguage,
    ) {
        viewModelScope.launch {
            if (initializeTts(language)) {
                app.seekPlayback(progress)
            }
        }
    }

    fun stopPlayback() {
        app.stopPlayback()
    }

    private fun startPlayback(
        text: String,
        language: SupportedLanguage,
    ) {
        viewModelScope.launch {
            if (initializeTts(language)) {
                app.startPlayback(text, language)
            }
        }
    }

    companion object {
        private const val MIN_SPLASH_DURATION_MS = 1_000L
        private const val PREFERENCES_NAME = "tts_reader_preferences"
        private const val KEY_LANGUAGE = "preferred_language"
        private const val KEY_DARK_THEME = "dark_theme"
    }
}
