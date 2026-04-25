package com.irankundasteve.ttsreader

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class PlaybackStatus {
    IDLE,
    PREPARING,
    PLAYING,
    PAUSED,
}

data class PlaybackUiState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val activeText: String = "",
    val selectedLanguage: SupportedLanguage = SupportedLanguage.ENGLISH,
    val currentRange: IntRange? = null,
    val currentWordIndex: Int = 0,
    val totalWords: Int = 0,
    val elapsedMs: Long = 0L,
    val totalDurationMs: Long = 0L,
) {
    val hasSession: Boolean
        get() = status != PlaybackStatus.IDLE

    val progressFraction: Float
        get() = if (totalWords > 1) {
            currentWordIndex.toFloat() / (totalWords - 1).toFloat()
        } else {
            0f
        }
}

private data class WordBoundary(
    val start: Int,
    val endExclusive: Int,
)

private data class PlaybackSession(
    val utteranceId: String,
    val text: String,
    val language: SupportedLanguage,
    val words: List<WordBoundary>,
    val startWordIndex: Int,
    val startOffset: Int,
    val totalDurationMs: Long,
)

class TtsReaderApplication : Application() {

    private var textToSpeech: TextToSpeech? = null
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var playbackSession: PlaybackSession? = null
    private var pausedWordIndex = 0
    private var currentWordIndex = 0
    private var pauseRequested = false

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (
            focusChange == AudioManager.AUDIOFOCUS_LOSS ||
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            pausePlayback()
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    fun initializeTextToSpeech(locale: Locale, onComplete: (Boolean) -> Unit) {
        val existing = textToSpeech
        if (existing != null) {
            onComplete(applyLocale(existing, locale))
            return
        }

        var engine: TextToSpeech? = null
        engine = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech = engine
                configureEngine(engine)
                onComplete(applyLocale(engine, locale))
            } else {
                engine?.shutdown()
                onComplete(false)
            }
        }
    }

    fun startPlayback(
        text: String,
        language: SupportedLanguage,
        startFromProgress: Float = 0f,
    ): Boolean {
        val engine = textToSpeech ?: return false
        val words = tokenizeWords(text)
        if (words.isEmpty()) {
            return false
        }

        if (!requestAudioFocus()) {
            return false
        }

        if (!applyLocale(engine, Locale.forLanguageTag(language.tag))) {
            abandonAudioFocus()
            return false
        }

        val startWordIndex = progressToWordIndex(startFromProgress, words.lastIndex)
        val startOffset = words[startWordIndex].start
        val session = PlaybackSession(
            utteranceId = "utterance-${SystemClock.elapsedRealtimeNanos()}",
            text = text,
            language = language,
            words = words,
            startWordIndex = startWordIndex,
            startOffset = startOffset,
            totalDurationMs = estimateDurationMs(words.size),
        )

        playbackSession = session
        pausedWordIndex = startWordIndex
        currentWordIndex = startWordIndex
        pauseRequested = false
        _playbackState.value = buildPlaybackState(
            session = session,
            status = PlaybackStatus.PREPARING,
            wordIndex = startWordIndex,
            currentRange = session.wordRange(startWordIndex),
        )

        val result = engine.speak(
            text.substring(startOffset),
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            session.utteranceId,
        )

        if (result == TextToSpeech.ERROR) {
            abandonAudioFocus()
            playbackSession = null
            _playbackState.value = PlaybackUiState()
            return false
        }

        return true
    }

    fun pausePlayback() {
        val session = playbackSession ?: return
        if (_playbackState.value.status == PlaybackStatus.PAUSED) {
            return
        }

        pauseRequested = true
        pausedWordIndex = currentWordIndex.coerceIn(0, session.words.lastIndex)
        textToSpeech?.stop()
        abandonAudioFocus()
        _playbackState.value = buildPlaybackState(
            session = session,
            status = PlaybackStatus.PAUSED,
            wordIndex = pausedWordIndex,
            currentRange = session.wordRange(pausedWordIndex),
        )
    }

    fun resumePlayback(): Boolean {
        val state = _playbackState.value
        if (state.status != PlaybackStatus.PAUSED) {
            return false
        }

        return startPlayback(
            text = state.activeText,
            language = state.selectedLanguage,
            startFromProgress = state.progressFraction,
        )
    }

    fun seekPlayback(progress: Float): Boolean {
        val session = playbackSession ?: return false
        val targetIndex = progressToWordIndex(progress, session.words.lastIndex)
        pausedWordIndex = targetIndex
        currentWordIndex = targetIndex
        val range = session.wordRange(targetIndex)

        return when (_playbackState.value.status) {
            PlaybackStatus.PLAYING,
            PlaybackStatus.PREPARING,
            -> startPlayback(
                text = session.text,
                language = session.language,
                startFromProgress = progress,
            )

            PlaybackStatus.PAUSED -> {
                _playbackState.value = buildPlaybackState(
                    session = session,
                    status = PlaybackStatus.PAUSED,
                    wordIndex = targetIndex,
                    currentRange = range,
                )
                true
            }

            PlaybackStatus.IDLE -> false
        }
    }

    fun stopPlayback() {
        textToSpeech?.stop()
        abandonAudioFocus()
        playbackSession = null
        pausedWordIndex = 0
        currentWordIndex = 0
        pauseRequested = false
        _playbackState.value = PlaybackUiState()
    }

    private fun configureEngine(engine: TextToSpeech?) {
        engine?.setAudioAttributes(audioAttributes)
        engine?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    val session = playbackSession ?: return
                    if (utteranceId != session.utteranceId) {
                        return
                    }

                    _playbackState.value = buildPlaybackState(
                        session = session,
                        status = PlaybackStatus.PLAYING,
                        wordIndex = currentWordIndex,
                        currentRange = session.wordRange(currentWordIndex),
                    )
                }

                override fun onRangeStart(
                    utteranceId: String?,
                    start: Int,
                    end: Int,
                    frame: Int,
                ) {
                    val session = playbackSession ?: return
                    if (utteranceId != session.utteranceId) {
                        return
                    }

                    val fullStart = session.startOffset + start
                    val fullEnd = session.startOffset + end
                    val spokenWordIndex = session.words.indexOfFirst { word ->
                        fullStart >= word.start && fullStart < word.endExclusive
                    }.takeIf { it >= 0 } ?: currentWordIndex

                    currentWordIndex = spokenWordIndex
                    pausedWordIndex = spokenWordIndex
                    _playbackState.value = buildPlaybackState(
                        session = session,
                        status = PlaybackStatus.PLAYING,
                        wordIndex = spokenWordIndex,
                        currentRange = fullStart until fullEnd.coerceAtLeast(fullStart + 1),
                    )
                }

                override fun onDone(utteranceId: String?) {
                    val session = playbackSession ?: return
                    if (utteranceId != session.utteranceId) {
                        return
                    }

                    abandonAudioFocus()
                    if (pauseRequested) {
                        pauseRequested = false
                        return
                    }

                    playbackSession = null
                    pausedWordIndex = 0
                    currentWordIndex = 0
                    _playbackState.value = PlaybackUiState()
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    if (!pauseRequested) {
                        return
                    }

                    pauseRequested = false
                }

                override fun onError(utteranceId: String?) {
                    playbackSession = null
                    pausedWordIndex = 0
                    currentWordIndex = 0
                    pauseRequested = false
                    abandonAudioFocus()
                    _playbackState.value = PlaybackUiState()
                }
            },
        )
    }

    private fun requestAudioFocus(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()

        audioFocusRequest = request
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
        audioFocusRequest = null
    }

    private fun applyLocale(engine: TextToSpeech?, locale: Locale): Boolean {
        val result = engine?.setLanguage(locale) ?: TextToSpeech.ERROR
        return result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED &&
            result != TextToSpeech.ERROR
    }

    private fun buildPlaybackState(
        session: PlaybackSession,
        status: PlaybackStatus,
        wordIndex: Int,
        currentRange: IntRange?,
    ): PlaybackUiState {
        val boundedWordIndex = wordIndex.coerceIn(0, session.words.lastIndex)
        return PlaybackUiState(
            status = status,
            activeText = session.text,
            selectedLanguage = session.language,
            currentRange = currentRange,
            currentWordIndex = boundedWordIndex,
            totalWords = session.words.size,
            elapsedMs = estimateElapsedMs(
                wordIndex = boundedWordIndex,
                totalWords = session.words.size,
                totalDurationMs = session.totalDurationMs,
            ),
            totalDurationMs = session.totalDurationMs,
        )
    }

    private fun tokenizeWords(text: String): List<WordBoundary> {
        return Regex("\\S+").findAll(text).map { match ->
            WordBoundary(
                start = match.range.first,
                endExclusive = match.range.last + 1,
            )
        }.toList()
    }

    private fun estimateDurationMs(wordCount: Int): Long {
        val wordsPerMinute = 165.0
        return ((wordCount / wordsPerMinute) * 60_000.0).roundToLong().coerceAtLeast(1_000L)
    }

    private fun estimateElapsedMs(
        wordIndex: Int,
        totalWords: Int,
        totalDurationMs: Long,
    ): Long {
        if (totalWords <= 1) {
            return 0L
        }

        return (totalDurationMs * (wordIndex.toDouble() / totalWords.toDouble())).roundToLong()
    }

    private fun progressToWordIndex(progress: Float, maxIndex: Int): Int {
        return (progress.coerceIn(0f, 1f) * maxIndex.toFloat()).roundToInt()
            .coerceIn(0, maxIndex)
    }

    private fun PlaybackSession.wordRange(index: Int): IntRange? {
        val word = words.getOrNull(index) ?: return null
        return word.start until word.endExclusive
    }

    override fun onTerminate() {
        textToSpeech?.shutdown()
        super.onTerminate()
    }
}
