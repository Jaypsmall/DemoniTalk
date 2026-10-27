package com.example.demonitalk

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

class VoskSpeechManager(
    private val context: Context,
    private val onReady: () -> Unit,
    private val onSpeechResults: (String) -> Unit,
    private val onErrorRestart: () -> Unit,
    private val isModeActive: () -> Boolean,
    private val onPartialSpeechResults: ((String) -> Boolean)? = null
) : RecognitionListener {

    companion object {
        private const val TAG = "VoskSpeechManager"
        private const val MODEL_NAME = "model-es"
        private const val SAMPLE_RATE = 16000.0f
    }

    private var voskModel: Model? = null
    private var speechService: SpeechService? = null
    private val gson = Gson()

    @Volatile
    private var isInitializing = false

    @Volatile
    private var hasExecutedPartialInCurrentSession = false

    var isListening = false
        private set

    val isModelReady: Boolean
        get() = voskModel != null

    fun initModel(onComplete: ((Boolean) -> Unit)? = null) {
        if (voskModel != null) {
            onComplete?.invoke(true)
            return
        }
        if (isInitializing) return
        isInitializing = true

        Log.i(TAG, "Cargando modelo Vosk '$MODEL_NAME' para escucha sin internet...")
        StorageService.unpack(context, MODEL_NAME, MODEL_NAME,
            { model ->
                voskModel = model
                isInitializing = false
                Log.i(TAG, "¡Modelo Vosk '$MODEL_NAME' cargado con éxito!")
                onComplete?.invoke(true)
            },
            { exception ->
                isInitializing = false
                Log.e(TAG, "Error cargando modelo Vosk offline: ${exception.message}", exception)
                onComplete?.invoke(false)
            }
        )
    }

    fun startListening() {
        if (isListening) return

        if (voskModel == null) {
            initModel { success ->
                if (success) {
                    startListeningInternal()
                } else {
                    onErrorRestart()
                }
            }
        } else {
            startListeningInternal()
        }
    }

    private fun startListeningInternal() {
        val model = voskModel ?: return
        try {
            stopListening()

            val recognizer = Recognizer(model, SAMPLE_RATE)
            speechService = SpeechService(recognizer, SAMPLE_RATE)
            hasExecutedPartialInCurrentSession = false

            isListening = true
            speechService?.startListening(this)
            onReady()
            Log.d(TAG, "Vosk Speech Service inició la escucha offline")
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar Vosk SpeechService: ${e.message}", e)
            isListening = false
            if (isModeActive()) {
                onErrorRestart()
            }
        }
    }

    override fun onPartialResult(hypothesis: String?) {
        if (hasExecutedPartialInCurrentSession || hypothesis.isNullOrEmpty()) return
        val text = parseJsonText(hypothesis, "partial")
        if (text.isNotEmpty()) {
            if (onPartialSpeechResults?.invoke(text) == true) {
                hasExecutedPartialInCurrentSession = true
                isListening = false
                stopListening()
            }
        }
    }

    override fun onResult(hypothesis: String?) {
        if (hasExecutedPartialInCurrentSession) {
            hasExecutedPartialInCurrentSession = false
            return
        }
        isListening = false
        if (!hypothesis.isNullOrEmpty()) {
            val text = parseJsonText(hypothesis, "text")
            if (text.isNotEmpty()) {
                Log.i(TAG, "Vosk reconoció: '$text'")
                onSpeechResults(text)
                return
            }
        }
        if (isModeActive()) {
            onErrorRestart()
        }
    }

    override fun onFinalResult(hypothesis: String?) {
        if (hasExecutedPartialInCurrentSession) {
            hasExecutedPartialInCurrentSession = false
            return
        }
        isListening = false
        if (!hypothesis.isNullOrEmpty()) {
            val text = parseJsonText(hypothesis, "text")
            if (text.isNotEmpty()) {
                Log.i(TAG, "Resultado final Vosk: '$text'")
                onSpeechResults(text)
                return
            }
        }
        if (isModeActive()) {
            onErrorRestart()
        }
    }

    override fun onError(exception: Exception?) {
        Log.e(TAG, "Error Vosk Speech: ${exception?.message}", exception)
        isListening = false
        if (isModeActive()) {
            onErrorRestart()
        }
    }

    override fun onTimeout() {
        Log.d(TAG, "Vosk Speech timeout")
        isListening = false
        if (isModeActive()) {
            onErrorRestart()
        }
    }

    fun stopListening() {
        isListening = false
        try {
            speechService?.stop()
            speechService?.shutdown()
        } catch (_: Exception) {
        }
        speechService = null
    }

    private fun parseJsonText(json: String?, key: String): String {
        if (json.isNullOrEmpty()) return ""
        return try {
            val jsonObj = gson.fromJson(json, JsonObject::class.java)
            jsonObj?.get(key)?.asString?.trim() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun destroy() {
        stopListening()
        try {
            voskModel?.close()
        } catch (_: Exception) {
        }
        voskModel = null
    }
}