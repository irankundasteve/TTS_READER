package com.irankundasteve.ttsreader

import android.app.Application
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsReaderApplication : Application() {

    private var textToSpeech: TextToSpeech? = null

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
                onComplete(applyLocale(engine, locale))
            } else {
                engine?.shutdown()
                onComplete(false)
            }
        }
    }

    private fun applyLocale(engine: TextToSpeech?, locale: Locale): Boolean {
        val result = engine?.setLanguage(locale) ?: TextToSpeech.ERROR
        return result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED &&
            result != TextToSpeech.ERROR
    }

    override fun onTerminate() {
        textToSpeech?.shutdown()
        super.onTerminate()
    }
}
