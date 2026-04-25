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
import kotlinx.coroutines.Job
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
    val voiceSettings: VoiceSettings = VoiceSettings(),
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
    private var settingsApplyJob: Job? = null

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
            val voiceSettings = VoiceSettings(
                rate = preferences.getFloat(KEY_VOICE_RATE, 1.0f),
                pitch = preferences.getFloat(KEY_VOICE_PITCH, 1.0f),
            )
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
                voiceSettings = voiceSettings,
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
                app.seekPlayback(progress, _uiState.value.voiceSettings)
            }
        }
    }

    fun stopPlayback() {
        app.stopPlayback()
    }

    fun updateVoiceRate(rate: Float) {
        updateVoiceSettings(_uiState.value.voiceSettings.copy(rate = rate))
    }

    fun updateVoicePitch(pitch: Float) {
        updateVoiceSettings(_uiState.value.voiceSettings.copy(pitch = pitch))
    }

    fun resetVoiceSettings() {
        updateVoiceSettings(VoiceSettings())
    }

    fun previewVoice(language: SupportedLanguage) {
        viewModelScope.launch {
            if (initializeTts(language)) {
                app.previewVoice(language, _uiState.value.voiceSettings)
            }
        }
    }

    private fun startPlayback(
        text: String,
        language: SupportedLanguage,
    ) {
        viewModelScope.launch {
            if (initializeTts(language)) {
                app.startPlayback(text, language, _uiState.value.voiceSettings)
            }
        }
    }

    private fun updateVoiceSettings(voiceSettings: VoiceSettings) {
        val sanitized = VoiceSettings(
            rate = voiceSettings.rate.coerceIn(0.5f, 2.0f),
            pitch = voiceSettings.pitch.coerceIn(0.5f, 1.5f),
        )

        _uiState.value = _uiState.value.copy(voiceSettings = sanitized)
        persistVoiceSettings(sanitized)
        scheduleVoiceSettingsApply()
    }

    private fun persistVoiceSettings(voiceSettings: VoiceSettings) {
        getApplication<Application>()
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_VOICE_RATE, voiceSettings.rate)
            .putFloat(KEY_VOICE_PITCH, voiceSettings.pitch)
            .apply()
    }

    private fun scheduleVoiceSettingsApply() {
        settingsApplyJob?.cancel()
        settingsApplyJob = viewModelScope.launch {
            delay(200L)
            if (playbackState.value.hasSession) {
                app.updateVoiceSettings(_uiState.value.voiceSettings)
            }
        }
    }

    companion object {
        private const val MIN_SPLASH_DURATION_MS = 1_000L
        private const val PREFERENCES_NAME = "tts_reader_preferences"
        private const val KEY_LANGUAGE = "preferred_language"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_VOICE_RATE = "voice_rate"
        private const val KEY_VOICE_PITCH = "voice_pitch"
    }
}
