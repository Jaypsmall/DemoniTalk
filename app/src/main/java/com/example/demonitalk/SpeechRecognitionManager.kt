package com.example.demonitalk

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class SpeechRecognitionManager(
    private val context: Context,
    private val onReady: () -> Unit,
    private val onSpeechResults: (String) -> Unit,
    private val onErrorRestart: () -> Unit,
    private val onErrorStopWaiting: () -> Unit,
    private val isModeActive: () -> Boolean
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var originalSystemVolume: Int = -1
    private val mainHandler = Handler(Looper.getMainLooper())

    var isListening = false
        private set

    fun muteAudio(mute: Boolean) {
        try {
            if (mute) {
                if (originalSystemVolume == -1) {
                    originalSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)
                }

                // Silenciar todos los canales por los que la app de Búsqueda de Google o Android emite el pitido de micrófono
                try { audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0) } catch (_: Exception) {}
                try { audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0) } catch (_: Exception) {}
                try { audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_MUTE, 0) } catch (_: Exception) {}
                try { audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0) } catch (_: Exception) {}
            } else {
                try { audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0) } catch (_: Exception) {}
                try { audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0) } catch (_: Exception) {}
                try { audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_UNMUTE, 0) } catch (_: Exception) {}

                if (originalSystemVolume != -1) {
                    val volumeToRestore = originalSystemVolume
                    originalSystemVolume = -1
                    mainHandler.postDelayed({
                        try {
                            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, volumeToRestore, 0)
                        } catch (_: Exception) {}
                    }, 600)
                }
            }
        } catch (e: Exception) {
            Log.d("SpeechRecManager", "Error gestionando audio: ${e.message}")
        }
    }

    private fun setupSpeechRecognizer() {
        if (speechRecognizer != null) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e("SpeechRecManager", "Speech Recognition no disponible en este dispositivo")
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            speechRecognizer?.setRecognitionListener(speechListener)
        } catch (e: Exception) {
            Log.e("SpeechRecManager", "Error creando SpeechRecognizer: ${e.message}", e)
            speechRecognizer = null
        }
    }

    private val speechListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            onReady()
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListening = false
            muteAudio(true)
        }

        @SuppressLint("SwitchIntDef")
        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
                SpeechRecognizer.ERROR_CLIENT -> "Error del cliente"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permisos insuficientes"
                SpeechRecognizer.ERROR_NETWORK -> "Error de red"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tiempo de espera de red agotado"
                SpeechRecognizer.ERROR_NO_MATCH -> "No se encontró coincidencia"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocedor ocupado"
                SpeechRecognizer.ERROR_SERVER -> "Error del servidor"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó voz"
                12 -> "Idioma no soportado (Error 12)"
                else -> "Error desconocido: $error"
            }

            Log.e("SpeechRecManager", "Error de reconocimiento [$error]: $message")
            isListening = false
            muteAudio(false)

            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                error == SpeechRecognizer.ERROR_CLIENT ||
                error == SpeechRecognizer.ERROR_SERVER ||
                error == 12
            ) {
                resetSpeechRecognizer()
            }

            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
            ) {
                if (ShellUtils.isRootAvailable()) {
                    Thread {
                        try {
                            val pkg = context.packageName
                            ShellUtils.executeCommand("appops set $pkg RECORD_AUDIO allow")
                            ShellUtils.executeCommand("appops set $pkg PROJECT_MEDIA allow")
                            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                                ShellUtils.executeCommand("am force-stop com.google.android.googlequicksearchbox")
                            }
                        } catch (e: Exception) {
                            Log.e("SpeechRecManager", "Error recuperación ROOT: ${e.message}")
                        }
                    }.start()
                }
            }

            if (isModeActive()) {
                onErrorRestart()
            } else {
                onErrorStopWaiting()
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            muteAudio(false)

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                onSpeechResults(text)
            } else {
                onErrorRestart()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening() {
        if (isListening) return

        try {
            setupSpeechRecognizer()
            if (speechRecognizer == null) return

            try { speechRecognizer?.cancel() } catch (_: Exception) {}

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es")
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra("android.speech.extra.DICTATION_MODE", true)
            }

            muteAudio(true)
            speechRecognizer?.startListening(intent)
            isListening = true

        } catch (e: Exception) {
            Log.e("SpeechRecManager", "Error al iniciar captura de voz: ${e.message}", e)
            isListening = false
            muteAudio(false)
            if (isModeActive()) {
                onErrorRestart()
            }
        }
    }

    fun stopListening() {
        isListening = false
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
        muteAudio(false)
    }

    fun resetSpeechRecognizer() {
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
        try { speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null
        isListening = false
    }

    fun destroy() {
        stopListening()
        resetSpeechRecognizer()
        mainHandler.removeCallbacksAndMessages(null)
    }
}
