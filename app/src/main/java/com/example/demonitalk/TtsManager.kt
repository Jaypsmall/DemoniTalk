package com.example.demonitalk

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TtsManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                try {
                    val result = tts?.setLanguage(Locale.getDefault())
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w("TtsManager", "Idioma TTS no disponible: ${Locale.getDefault()}")
                    }
                } catch (e: Exception) {
                    Log.e("TtsManager", "Error configurando TTS: ${e.message}")
                }
            } else {
                isTtsReady = false
                Log.e("TtsManager", "No se pudo inicializar TextToSpeech")
            }
        }
    }

    fun cleanTextForSpeech(text: String): String {
        return text
            .replace(Regex("""\[CMD:.+?\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[REMEMBER:.+?\]""", RegexOption.IGNORE_CASE), "")
            // Reemplazar toda puntuación por espacio para que TTS JAMÁS pronuncie las palabras "coma" o "punto"
            .replace(Regex("""[,.;:!?¡¿"'\-\(\)\[\]]"""), " ")
            .replace(Regex("""[\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF\u1F300-\u1F9FF]"""), "")
            .replace(Regex("""[🚀😈🤖⚡🔥✨👍]"""), "")
            .replace("*", "")
            .replace("#", "")
            .replace("_", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun speakText(textToSpeak: String, onStartCallback: (() -> Unit)? = null, onDoneCallback: (() -> Unit)? = null) {
        if (!isTtsReady || tts == null) {
            onDoneCallback?.invoke()
            return
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                mainHandler.post { onStartCallback?.invoke() }
            }

            override fun onDone(id: String?) {
                mainHandler.post { onDoneCallback?.invoke() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                mainHandler.post { onDoneCallback?.invoke() }
            }
        })

        try {
            tts?.speak(
                textToSpeak,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "DemoniSpeech_${System.currentTimeMillis()}"
            )
        } catch (e: Exception) {
            Log.e("TtsManager", "Error ejecutando TTS: ${e.message}")
            onDoneCallback?.invoke()
        }
    }

    fun destroy() {
        tts?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.shutdown() } catch (_: Exception) {}
        }
        tts = null
        isTtsReady = false
    }
}
