package com.example.demonitalk

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TtsManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val pendingSpeechQueue = mutableListOf<Triple<String, (() -> Unit)?, (() -> Unit)?>>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        createTtsInstance()
    }

    private fun createTtsInstance() {
        if (tts != null) return
        try {
            tts = TextToSpeech(context) { status ->
                mainHandler.post {
                    if (status == TextToSpeech.SUCCESS) {
                        try {
                            var result = tts?.setLanguage(Locale.forLanguageTag("es-ES"))
                            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                result = tts?.setLanguage(Locale.forLanguageTag("es"))
                            }
                            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                tts?.setLanguage(Locale.getDefault())
                            }

                            val audioAttributes = AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                            tts?.setAudioAttributes(audioAttributes)

                            isTtsReady = true
                            Log.i("TtsManager", "TTS inicializado correctamente en español")
                            flushPendingQueue()
                        } catch (e: Exception) {
                            isTtsReady = false
                            Log.e("TtsManager", "Error configurando TTS: ${e.message}")
                        }
                    } else {
                        isTtsReady = false
                        Log.e("TtsManager", "No se pudo inicializar TextToSpeech (status=$status)")
                    }
                }
            }
        } catch (e: Exception) {
            isTtsReady = false
            Log.e("TtsManager", "Error creando instancia TextToSpeech: ${e.message}")
        }
    }

    private fun flushPendingQueue() {
        if (pendingSpeechQueue.isEmpty()) return
        val queueCopy = ArrayList(pendingSpeechQueue)
        pendingSpeechQueue.clear()
        for ((text, startCb, doneCb) in queueCopy) {
            speakText(text, startCb, doneCb)
        }
    }

    fun cleanTextForSpeech(text: String): String {
        return text
            .replace(Regex("""\[CMD:.+?\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[REMEMBER:.+?\]""", RegexOption.IGNORE_CASE), "")
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
            Log.w("TtsManager", "TTS aún no está listo. Encolando texto: '$textToSpeak'")
            pendingSpeechQueue.add(Triple(textToSpeak, onStartCallback, onDoneCallback))
            createTtsInstance()
            return
        }

        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = true
        } catch (_: Exception) {}

        Log.d("TtsManager", "Hablando en voz alta por altavoz: '$textToSpeak'")

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
            val utteranceId = "DemoniSpeech_${System.currentTimeMillis()}"
            val result = tts?.speak(
                textToSpeak,
                TextToSpeech.QUEUE_FLUSH,
                null,
                utteranceId
            )
            if (result == TextToSpeech.ERROR) {
                Log.e("TtsManager", "Error en tts.speak para: '$textToSpeak'")
                onDoneCallback?.invoke()
            }
        } catch (e: Exception) {
            Log.e("TtsManager", "Error ejecutando TTS: ${e.message}")
            onDoneCallback?.invoke()
        }
    }

    fun destroy() {
        isTtsReady = false
        pendingSpeechQueue.clear()
        tts?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.shutdown() } catch (_: Exception) {}
        }
        tts = null
    }
}